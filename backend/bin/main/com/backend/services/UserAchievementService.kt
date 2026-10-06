package com.backend.services

import com.backend.exceptions.UserNotFoundException
import com.backend.models.dtos.UserAchievementDTO
import com.backend.models.enums.AchievementMetric
import com.backend.models.enums.AchievementType
import com.backend.models.enums.FriendRequestStatus
import com.backend.repositories.UserAchievementRepository
import com.backend.repositories.UserRepository
import com.backend.security.CurrentUser
import com.backend.models.enums.AccountStatus
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.*

@Service
class UserAchievementService(
    private val userAchievementRepository: UserAchievementRepository,
    private val userRepository: UserRepository,
    private val transactionManager: PlatformTransactionManager
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Europe/Rome")
        private val TYPES_BY_CODE: Map<String, AchievementType> = AchievementType.entries.associateBy { it.name }
    }

    @Transactional
    fun getUserAchievements(userId: UUID): List<UserAchievementDTO> {
        logger.debug("\n\t[DEBUG] [achievement_service][get_user_achievements] Retrieving achievements\n\tuserId={}", userId)

        try {
            if(!userRepository.existsById(userId)) {
                throw UserNotFoundException(userId)
            }

            val metrics = computeMetrics(userId)

            if(userId == CurrentUser.id()) {
                evaluateWithMetrics(userId, metrics)
            }

            val unlockedByCode = userAchievementRepository
                .findAllByUserId(userId)
                .associateBy { it.achievementCode }

            val result = AchievementType.entries.map { type ->
                UserAchievementDTO.from(
                    type,
                    metrics[type.metric] ?: 0L,
                    unlockedByCode[type.name]
                )
            }

            logger.info("\n\t[INFO] [achievement_service][get_user_achievements] Retrieved {} achievements\n\tuserId={}\n\tunlocked={}", result.size, userId, unlockedByCode.size)

            return result
        } catch(e: UserNotFoundException) {
            logger.warn("\n\t[WARN] [achievement_service][get_user_achievements] User not found\n\tuserId={}", userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [achievement_service][get_user_achievements] Error retrieving achievements\n\tuserId={}\n\treason={}", userId, e.message, e)
            throw e
        }
    }

    @Transactional
    fun getUnseenAchievements(): List<UserAchievementDTO> {
        val userId = CurrentUser.id()
        logger.debug("\n\t[DEBUG] [achievement_service][get_unseen_achievements] Retrieving unseen achievements\n\tuserId={}", userId)

        try {
            evaluateWithMetrics(userId, computeMetrics(userId))

            val result = userAchievementRepository
                .findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId)
                .mapNotNull { row ->
                    val type = TYPES_BY_CODE[row.achievementCode]

                    if(type == null) {
                        logger.warn("\n\t[WARN] [achievement_service][get_unseen_achievements] Unknown achievement code in database, skipping\n\tuserId={}\n\tcode={}", userId, row.achievementCode)
                        return@mapNotNull null
                    }

                    UserAchievementDTO.from(type, type.target, row)
                }

            logger.info("\n\t[INFO] [achievement_service][get_unseen_achievements] Retrieved {} unseen achievements\n\tuserId={}", result.size, userId)

            return result
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [achievement_service][get_unseen_achievements] Error retrieving unseen achievements\n\tuserId={}\n\treason={}", userId, e.message, e)
            throw e
        }
    }

    @Transactional
    fun markSeen(codes: List<String>) {
        val userId = CurrentUser.id()
        logger.debug("\n\t[DEBUG] [achievement_service][mark_seen] Marking achievements as seen\n\tuserId={}\n\tcodes={}", userId, codes)

        if(codes.isEmpty()) {
            return
        }

        try {
            val updated = userAchievementRepository.markSeen(userId, codes, Instant.now())

            logger.info("\n\t[INFO] [achievement_service][mark_seen] Marked {} achievements as seen\n\tuserId={}", updated, userId)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [achievement_service][mark_seen] Error marking achievements as seen\n\tuserId={}\n\treason={}", userId, e.message, e)
            throw e
        }
    }

    fun evaluateAfterCommit(userIds: Collection<UUID>) {
        if(userIds.isEmpty()) {
            return
        }

        logger.debug("\n\t[DEBUG] [achievement_service][evaluate_after_commit] Scheduling evaluation\n\tuserIds={}", userIds)

        if(!TransactionSynchronizationManager.isSynchronizationActive()) {
            evaluateInNewTransaction(userIds)
            return
        }

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                evaluateInNewTransaction(userIds)
            }
        })
    }

    private fun evaluateInNewTransaction(userIds: Collection<UUID>) {
        userIds.forEach { userId ->
            try {
                newTransaction.execute { evaluateWithMetrics(userId, computeMetrics(userId)) }
            } catch(e: Exception) {
                logger.error("\n\t[ERROR] [achievement_service][evaluate_after_commit] Error evaluating achievements, will be retried on next app launch\n\tuserId={}\n\treason={}", userId, e.message, e)
            }
        }
    }

    private fun evaluateWithMetrics(userId: UUID, metrics: Map<AchievementMetric, Long>): List<AchievementType> {
        val alreadyUnlocked = userAchievementRepository.findCodesByUserId(userId)
        val now = Instant.now()

        val newlyUnlocked = AchievementType.entries
            .filter { it.name !in alreadyUnlocked }
            .filter { (metrics[it.metric] ?: 0L) >= it.target }
            .filter { userAchievementRepository.insertIfAbsent(userId, it.name, now) == 1 }

        if(newlyUnlocked.isNotEmpty()) {
            logger.info("\n\t[INFO] [achievement_service][evaluate] Unlocked {} achievements\n\tuserId={}\n\tcodes={}", newlyUnlocked.size, userId, newlyUnlocked.map { it.name })
        }

        return newlyUnlocked
    }

    private fun computeMetrics(userId: UUID): Map<AchievementMetric, Long> {
        val playedDates = userAchievementRepository.findFinishedMatchDates(userId)
        val winsInOrder = userAchievementRepository.findFinishedMatchWins(userId)
        val won = winsInOrder.count { it }.toLong()

        return mapOf(
            AchievementMetric.MATCHES_PLAYED to playedDates.size.toLong(),
            AchievementMetric.MATCHES_WON to won,
            AchievementMetric.MATCHES_LOST to playedDates.size - won,
            AchievementMetric.DISTINCT_GAMES_PLAYED to userAchievementRepository.countDistinctGamesPlayed(userId),
            AchievementMetric.WIN_STREAK to longestWinStreak(winsInOrder),
            AchievementMetric.WEEKLY_PLAY_STREAK to longestWeeklyStreak(playedDates),
            AchievementMetric.COLLECTION_SIZE to userAchievementRepository.countCollectionItems(userId),
            AchievementMetric.FRIENDS_COUNT to userAchievementRepository.countFriends(userId, FriendRequestStatus.ACCEPTED),
        )
    }

    private fun longestWinStreak(winsInOrder: List<Boolean>): Long {
        var best = 0L
        var current = 0L

        winsInOrder.forEach { won ->
            current = if(won) current + 1 else 0

            if(current > best) {
                best = current
            }
        }

        return best
    }

    private fun longestWeeklyStreak(playedAt: Collection<Instant>): Long {
        val weeks = playedAt
            .map {
                it.atZone(ZONE)
                    .toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            .distinct()
            .sorted()

        if(weeks.isEmpty()) {
            return 0L
        }

        var best = 1L
        var current = 1L

        for(i in 1 until weeks.size) {
            current = if(weeks[i - 1].plusWeeks(1) == weeks[i]) current + 1 else 1

            if(current > best) {
                best = current
            }
        }

        return best
    }

    private val newTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @EventListener(ApplicationReadyEvent::class)
    fun backfillOnStartup() {
        logger.info("\n\t[INFO] [achievement_service][backfill_on_startup] Starting achievements backfill")

        try {
            val userIds = userRepository.findAll()
                .filter { it.accountStatus == AccountStatus.ACTIVE }
                .mapNotNull { it.id }

            evaluateInNewTransaction(userIds)

            logger.info("\n\t[INFO] [achievement_service][backfill_on_startup] Backfill completed\n\tusers={}", userIds.size)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [achievement_service][backfill_on_startup] Backfill failed\n\treason={}", e.message, e)
        }
    }
}