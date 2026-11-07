package com.backend.unit.services

import com.backend.exceptions.UserNotFoundException
import com.backend.models.entities.UserAchievement
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.AchievementType
import com.backend.models.enums.FriendRequestStatus
import com.backend.models.enums.NotificationType
import com.backend.models.projections.FinishedMatchProjection
import com.backend.repositories.UserAchievementRepository
import com.backend.repositories.UserRepository
import com.backend.security.CurrentUser
import com.backend.services.PushNotificationService
import com.backend.services.UserAchievementService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class UserAchievementServiceTest {

    private lateinit var userAchievementRepository: UserAchievementRepository
    private lateinit var userRepository: UserRepository
    private lateinit var pushNotificationService: PushNotificationService
    private lateinit var transactionManager: PlatformTransactionManager
    private lateinit var service: UserAchievementService

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val defaultGameId = UUID.randomUUID()
    private val definitions = mutableListOf<TransactionDefinition>()

    @BeforeEach
    fun setUp() {
        userAchievementRepository = mockk()
        userRepository = mockk()
        pushNotificationService = mockk(relaxUnitFun = true)
        transactionManager = mockk(relaxed = true)

        definitions.clear()
        every { transactionManager.getTransaction(any()) } answers { definitions += firstArg<TransactionDefinition>(); SimpleTransactionStatus() }

        mockkObject(CurrentUser)
        every { CurrentUser.id() } returns userId

        service = UserAchievementService(userAchievementRepository, userRepository, pushNotificationService, transactionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CurrentUser)
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun at(date: String): Instant = LocalDate.parse(date).atTime(20, 0).atZone(ZoneId.of("Europe/Rome")).toInstant()

    private fun match(at: Instant, isWin: Boolean = false, game: UUID = defaultGameId): FinishedMatchProjection = mockk {
        every { playedAt } returns at
        every { won } returns isWin
        every { gameId } returns game
    }

    private fun spreadMatches(count: Int, wins: List<Boolean> = List(count) { false }, games: List<UUID> = List(count) { defaultGameId }): List<FinishedMatchProjection> =
        (0 until count).map { match(Instant.parse("2026-01-01T10:00:00Z").plus(Duration.ofDays(it * 30L)), wins[it], games[it]) }

    private fun weeklyMatches(vararg dates: Instant) = dates.map { match(it) }

    private fun stubMetrics(
        uid: UUID = userId,
        matches: List<FinishedMatchProjection> = emptyList(),
        collection: Long = 0L,
        friends: Long = 0L,
        alreadyUnlocked: Set<String> = emptySet(),
    ): MutableList<String> {
        val codes = mutableListOf<String>()
        every { userAchievementRepository.findFinishedMatches(uid) } returns matches
        every { userAchievementRepository.countCollectionItems(uid) } returns collection
        every { userAchievementRepository.countFriends(uid, FriendRequestStatus.ACCEPTED) } returns friends
        every { userAchievementRepository.findCodesByUserId(uid) } returns alreadyUnlocked
        every { userAchievementRepository.insertIfAbsent(uid, capture(codes), any()) } returns 1
        return codes
    }

    private fun row(code: String, uid: UUID = userId) = UserAchievement(
        id = UUID.randomUUID(),
        userId = uid,
        achievementCode = code,
        unlockedAt = Instant.parse("2026-10-01T10:00:00Z"),
    )

    private fun evaluate(vararg userIds: UUID) = service.evaluateAfterCommit(userIds.toList())

    private fun synchronizations(): List<TransactionSynchronization> = TransactionSynchronizationManager.getSynchronizations()

    @Nested
    @DisplayName("getUserAchievements")
    inner class GetUserAchievementsTests {

        @Test
        fun `should throw UserNotFoundException without computing anything`() {
            every { userRepository.existsById(otherUserId) } returns false

            assertThatThrownBy { service.getUserAchievements(otherUserId) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { userAchievementRepository.findFinishedMatches(any()) }
        }

        @Test
        fun `should return the whole catalog with the progress of each achievement`() {
            every { userRepository.existsById(otherUserId) } returns true
            stubMetrics(uid = otherUserId, matches = spreadMatches(3, wins = listOf(true, false, false)), collection = 12)
            every { userAchievementRepository.findAllByUserId(otherUserId) } returns listOf(row("FIRST_MATCH", otherUserId))

            val result = service.getUserAchievements(otherUserId)

            assertThat(result.map { it.code }).containsExactlyElementsOf(AchievementType.entries.map { it.name })

            val firstMatch = result.first { it.code == "FIRST_MATCH" }
            assertThat(firstMatch.unlocked).isTrue()
            assertThat(firstMatch.progress).isEqualTo(firstMatch.target)

            val matches10 = result.first { it.code == "MATCHES_10" }
            assertThat(matches10.unlocked).isFalse()
            assertThat(matches10.progress).isEqualTo(3L)
            assertThat(matches10.target).isEqualTo(10L)

            val collection10 = result.first { it.code == "COLLECTION_10" }
            assertThat(collection10.unlocked).isFalse()
            assertThat(collection10.progress).isEqualTo(10L)
        }

        @Test
        fun `should not write anything without evaluate`() {
            every { userRepository.existsById(otherUserId) } returns true
            stubMetrics(uid = otherUserId, matches = spreadMatches(1))
            every { userAchievementRepository.findAllByUserId(otherUserId) } returns emptyList()

            service.getUserAchievements(otherUserId)

            verify(exactly = 0) { userAchievementRepository.insertIfAbsent(any(), any(), any()) }
        }

        @Test
        fun `should unlock reached achievements before reading them with evaluate`() {
            every { userRepository.existsById(userId) } returns true
            val codes = stubMetrics(matches = spreadMatches(1))
            every { userAchievementRepository.findAllByUserId(userId) } returns listOf(row("FIRST_MATCH"))

            val result = service.getUserAchievements(userId, evaluate = true)

            assertThat(codes).containsExactly("FIRST_MATCH")
            assertThat(result.first { it.code == "FIRST_MATCH" }.unlocked).isTrue()
            verifyOrder {
                userAchievementRepository.insertIfAbsent(userId, "FIRST_MATCH", any())
                userAchievementRepository.findAllByUserId(userId)
            }
        }

        @Test
        fun `should not notify when unlocking from the profile`() {
            every { userRepository.existsById(userId) } returns true
            stubMetrics(matches = spreadMatches(1))
            every { userAchievementRepository.findAllByUserId(userId) } returns emptyList()

            service.getUserAchievements(userId, evaluate = true)

            verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should propagate a repository failure`() {
            every { userRepository.existsById(userId) } throws RuntimeException("DB error")

            assertThatThrownBy { service.getUserAchievements(userId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }

    @Nested
    @DisplayName("getUnseenAchievements")
    inner class GetUnseenAchievementsTests {

        @Test
        fun `should evaluate first and then return the unseen achievements oldest first`() {
            stubMetrics(matches = spreadMatches(1))
            every { userAchievementRepository.findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId) } returns listOf(row("FIRST_MATCH"), row("FIRST_WIN"))

            val result = service.getUnseenAchievements(userId)

            assertThat(result.map { it.code }).containsExactly("FIRST_MATCH", "FIRST_WIN")
            assertThat(result).allMatch { it.unlocked && it.progress == it.target }
            verifyOrder {
                userAchievementRepository.insertIfAbsent(userId, "FIRST_MATCH", any())
                userAchievementRepository.findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId)
            }
        }

        @Test
        fun `should return an empty list when there is nothing unseen`() {
            stubMetrics()
            every { userAchievementRepository.findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId) } returns emptyList()

            assertThat(service.getUnseenAchievements(userId)).isEmpty()
        }

        @Test
        fun `should skip codes that are no longer in the catalog`() {
            stubMetrics()
            every { userAchievementRepository.findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId) } returns listOf(row("REMOVED_ACHIEVEMENT"), row("FIRST_WIN"))

            assertThat(service.getUnseenAchievements(userId).map { it.code }).containsExactly("FIRST_WIN")
        }

        @Test
        fun `should propagate a repository failure`() {
            every { userAchievementRepository.findFinishedMatches(userId) } throws RuntimeException("DB error")

            assertThatThrownBy { service.getUnseenAchievements(userId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }

    @Nested
    @DisplayName("markSeen")
    inner class MarkSeenTests {

        @Test
        fun `should mark the given codes as seen`() {
            val codes = listOf("FIRST_MATCH", "FIRST_WIN")
            every { userAchievementRepository.markSeen(userId, codes, any()) } returns 2

            service.markSeen(userId, codes)

            verify(exactly = 1) { userAchievementRepository.markSeen(userId, codes, any()) }
        }

        @Test
        fun `should do nothing when the list of codes is empty`() {
            service.markSeen(userId, emptyList())

            verify(exactly = 0) { userAchievementRepository.markSeen(any(), any(), any()) }
        }

        @Test
        fun `should propagate a repository failure`() {
            every { userAchievementRepository.markSeen(any(), any(), any()) } throws RuntimeException("DB error")

            assertThatThrownBy { service.markSeen(userId, listOf("FIRST_MATCH")) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }

    @Nested
    @DisplayName("evaluateAfterCommit")
    inner class EvaluateAfterCommitTests {

        @Test
        fun `should do nothing when there are no users`() {
            service.evaluateAfterCommit(emptySet())

            verify(exactly = 0) { userAchievementRepository.findFinishedMatches(any()) }
            verify(exactly = 0) { transactionManager.getTransaction(any()) }
        }

        @Test
        fun `should evaluate immediately in a new transaction when there is no active transaction`() {
            val codes = stubMetrics(matches = spreadMatches(1))

            evaluate(userId)

            assertThat(codes).containsExactly("FIRST_MATCH")
            assertThat(definitions.single().propagationBehavior).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        }

        @Test
        fun `should wait for the commit when a transaction is active`() {
            val codes = stubMetrics(matches = spreadMatches(1))
            TransactionSynchronizationManager.initSynchronization()

            evaluate(userId)

            verify(exactly = 0) { userAchievementRepository.findFinishedMatches(any()) }
            assertThat(synchronizations()).hasSize(1)

            synchronizations().forEach { it.afterCommit() }

            assertThat(codes).containsExactly("FIRST_MATCH")
        }

        @Test
        fun `should not evaluate anything when the transaction rolls back`() {
            TransactionSynchronizationManager.initSynchronization()

            evaluate(userId)
            synchronizations().forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 0) { userAchievementRepository.findFinishedMatches(any()) }
        }

        @Test
        fun `should evaluate every user in its own transaction`() {
            val codesUser = stubMetrics(uid = userId, matches = spreadMatches(1))
            val codesOther = stubMetrics(uid = otherUserId, friends = 1)

            evaluate(userId, otherUserId)

            assertThat(codesUser).containsExactly("FIRST_MATCH")
            assertThat(codesOther).containsExactly("FIRST_FRIEND")
            assertThat(definitions).hasSize(2)
        }

        @Test
        fun `should keep evaluating the other users when one of them fails`() {
            stubMetrics(uid = userId)
            every { userAchievementRepository.findFinishedMatches(userId) } throws RuntimeException("DB error")
            val codesOther = stubMetrics(uid = otherUserId, matches = spreadMatches(1))

            assertThatCode { evaluate(userId, otherUserId) }.doesNotThrowAnyException()

            assertThat(codesOther).containsExactly("FIRST_MATCH")
            verify(exactly = 1) { transactionManager.rollback(any()) }
        }

        @Test
        fun `should not insert achievements that are already unlocked`() {
            stubMetrics(matches = spreadMatches(1), alreadyUnlocked = setOf("FIRST_MATCH"))

            evaluate(userId)

            verify(exactly = 0) { userAchievementRepository.insertIfAbsent(any(), any(), any()) }
        }

        @Test
        fun `should not count an achievement inserted concurrently by someone else`() {
            stubMetrics(uid = otherUserId, matches = spreadMatches(1))
            every { userAchievementRepository.insertIfAbsent(otherUserId, any(), any()) } returns 0

            assertThatCode { evaluate(otherUserId) }.doesNotThrowAnyException()

            verify(exactly = 1) { userAchievementRepository.insertIfAbsent(otherUserId, "FIRST_MATCH", any()) }
            verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should not unlock anything for a user without activity`() {
            stubMetrics()

            evaluate(userId)

            verify(exactly = 0) { userAchievementRepository.insertIfAbsent(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("notifications")
    inner class NotificationTests {

        private val titleSlot = slot<String>()
        private val bodySlot = slot<String>()
        private val dataSlot = slot<Map<String, Any>>()

        private fun captureNotification() {
            every { pushNotificationService.sendToUser(otherUserId, NotificationType.ACHIEVEMENT_UNLOCKED, capture(titleSlot), capture(bodySlot), capture(dataSlot)) } returns Unit
        }

        @Test
        fun `should notify a single unlocked achievement`() {
            stubMetrics(uid = otherUserId, matches = spreadMatches(1))
            captureNotification()

            evaluate(otherUserId)

            assertThat(titleSlot.captured).isEqualTo("Obiettivo sbloccato")
            assertThat(bodySlot.captured).isEqualTo("Hai sbloccato «${AchievementType.FIRST_MATCH.title}»")
            assertThat(dataSlot.captured).isEqualTo(mapOf("codes" to listOf("FIRST_MATCH")))
        }

        @Test
        fun `should notify two unlocked achievements by name`() {
            val codes = stubMetrics(uid = otherUserId, matches = spreadMatches(1, wins = listOf(true)))
            captureNotification()

            evaluate(otherUserId)

            val (first, second) = codes.map { AchievementType.valueOf(it) }
            assertThat(codes).containsExactlyInAnyOrder("FIRST_MATCH", "FIRST_WIN")
            assertThat(titleSlot.captured).isEqualTo("Obiettivi sbloccati")
            assertThat(bodySlot.captured).isEqualTo("Hai sbloccato «${first.title}» e «${second.title}»")
            assertThat(dataSlot.captured).isEqualTo(mapOf("codes" to codes))
        }

        @Test
        fun `should summarize three or more unlocked achievements`() {
            val codes = stubMetrics(uid = otherUserId, matches = spreadMatches(1, wins = listOf(true)), friends = 1)
            captureNotification()

            evaluate(otherUserId)

            assertThat(codes).hasSizeGreaterThanOrEqualTo(3)
            val first = AchievementType.valueOf(codes.first())
            assertThat(titleSlot.captured).isEqualTo("Obiettivi sbloccati")
            assertThat(bodySlot.captured).isEqualTo("Hai sbloccato «${first.title}» e altri ${codes.size - 1} obiettivi")
        }

        @Test
        fun `should not notify the user who triggered the evaluation`() {
            stubMetrics(uid = userId, matches = spreadMatches(1))
            stubMetrics(uid = otherUserId, matches = spreadMatches(1))

            evaluate(userId, otherUserId)

            verify(exactly = 0) { pushNotificationService.sendToUser(userId, any(), any(), any(), any()) }
            verify(exactly = 1) { pushNotificationService.sendToUser(otherUserId, NotificationType.ACHIEVEMENT_UNLOCKED, any(), any(), any()) }
        }

        @Test
        fun `should remember the actor when the evaluation runs after the commit`() {
            stubMetrics(uid = userId, matches = spreadMatches(1))
            TransactionSynchronizationManager.initSynchronization()

            evaluate(userId)
            every { CurrentUser.id() } throws IllegalStateException("No authentication")
            synchronizations().forEach { it.afterCommit() }

            verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should notify everyone when there is no authenticated user`() {
            every { CurrentUser.id() } throws IllegalStateException("No authentication")
            stubMetrics(uid = userId, matches = spreadMatches(1))

            evaluate(userId)

            verify(exactly = 1) { pushNotificationService.sendToUser(userId, NotificationType.ACHIEVEMENT_UNLOCKED, any(), any(), any()) }
        }

        @Test
        fun `should not notify when nothing new is unlocked`() {
            stubMetrics(uid = otherUserId, matches = spreadMatches(1), alreadyUnlocked = setOf("FIRST_MATCH"))

            evaluate(otherUserId)

            verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should send the notification inside the new transaction`() {
            stubMetrics(uid = otherUserId, matches = spreadMatches(1))

            evaluate(otherUserId)

            verifyOrder {
                transactionManager.getTransaction(any())
                userAchievementRepository.insertIfAbsent(otherUserId, "FIRST_MATCH", any())
                pushNotificationService.sendToUser(otherUserId, NotificationType.ACHIEVEMENT_UNLOCKED, any(), any(), any())
                transactionManager.commit(any())
            }
        }
    }

    @Nested
    @DisplayName("metrics and thresholds")
    inner class MetricsTests {

        @Test
        fun `should unlock match and win achievements by count`() {
            val codes = stubMetrics(matches = spreadMatches(10, wins = List(10) { it < 2 }))

            evaluate(userId)

            assertThat(codes).contains("FIRST_MATCH", "MATCHES_10", "FIRST_WIN")
            assertThat(codes).doesNotContain("MATCHES_50", "WINS_10", "LOSSES_10")
        }

        @Test
        fun `should unlock the losses achievement after 10 losses`() {
            val codes = stubMetrics(matches = spreadMatches(10))

            evaluate(userId)

            assertThat(codes).contains("LOSSES_10")
            assertThat(codes).doesNotContain("FIRST_WIN")
        }

        @Test
        fun `should unlock the explorer achievement with 10 distinct games`() {
            val codes = stubMetrics(matches = spreadMatches(10, games = List(10) { UUID.randomUUID() }))

            evaluate(userId)

            assertThat(codes).contains("DISTINCT_GAMES_10")
        }

        @Test
        fun `should not count the same game twice for the explorer achievement`() {
            val codes = stubMetrics(matches = spreadMatches(10))

            evaluate(userId)

            assertThat(codes).doesNotContain("DISTINCT_GAMES_10")
        }

        @Test
        fun `should unlock collection achievements by size`() {
            val codes = stubMetrics(collection = 10)

            evaluate(userId)

            assertThat(codes).containsExactly("COLLECTION_10")
        }

        @Test
        fun `should unlock friends achievements by count`() {
            val codes = stubMetrics(friends = 5)

            evaluate(userId)

            assertThat(codes).containsExactlyInAnyOrder("FIRST_FRIEND", "FRIENDS_5")
        }

        @Test
        fun `should unlock everything reachable in catalog order`() {
            val codes = stubMetrics(matches = spreadMatches(1, wins = listOf(true)), friends = 1)

            evaluate(userId)

            assertThat(codes).isEqualTo(AchievementType.entries.map { it.name }.filter { it in codes })
        }
    }

    @Nested
    @DisplayName("win streak")
    inner class WinStreakTests {

        @Test
        fun `should unlock the 3 wins streak but not the 5 wins one`() {
            val codes = stubMetrics(matches = spreadMatches(5, wins = listOf(true, true, true, false, true)))

            evaluate(userId)

            assertThat(codes).contains("WIN_STREAK_3")
            assertThat(codes).doesNotContain("WIN_STREAK_5")
        }

        @Test
        fun `should reset the streak after a loss`() {
            val codes = stubMetrics(matches = spreadMatches(5, wins = listOf(true, true, false, true, true)))

            evaluate(userId)

            assertThat(codes).doesNotContain("WIN_STREAK_3", "WIN_STREAK_5")
        }

        @Test
        fun `should unlock both streak achievements with 5 consecutive wins`() {
            val codes = stubMetrics(matches = spreadMatches(5, wins = List(5) { true }))

            evaluate(userId)

            assertThat(codes).contains("WIN_STREAK_3", "WIN_STREAK_5")
        }

        @Test
        fun `should use the record, not the current streak`() {
            val codes = stubMetrics(matches = spreadMatches(6, wins = listOf(true, true, true, false, false, false)))

            evaluate(userId)

            assertThat(codes).contains("WIN_STREAK_3")
        }
    }

    @Nested
    @DisplayName("weekly play streak")
    inner class WeeklyStreakTests {

        @Test
        fun `should unlock the 4 weeks streak with 4 consecutive weeks`() {
            val codes = stubMetrics(matches = weeklyMatches(at("2026-09-22"), at("2026-09-29"), at("2026-10-06"), at("2026-10-13")))

            evaluate(userId)

            assertThat(codes).contains("WEEK_STREAK_4")
            assertThat(codes).doesNotContain("WEEK_STREAK_8")
        }

        @Test
        fun `should unlock the 8 weeks streak with 8 consecutive weeks`() {
            val start = LocalDate.parse("2026-09-01")
            val codes = stubMetrics(matches = (0 until 8).map { match(at(start.plusWeeks(it.toLong()).toString())) })

            evaluate(userId)

            assertThat(codes).contains("WEEK_STREAK_4", "WEEK_STREAK_8")
        }

        @Test
        fun `should break the streak when a week is skipped`() {
            val codes = stubMetrics(matches = weeklyMatches(at("2026-09-22"), at("2026-09-29"), at("2026-10-13"), at("2026-10-20")))

            evaluate(userId)

            assertThat(codes).doesNotContain("WEEK_STREAK_4")
        }

        @Test
        fun `should keep the longest streak even when a later one is shorter`() {
            val codes = stubMetrics(matches = weeklyMatches(
                at("2026-06-01"), at("2026-06-08"), at("2026-06-15"), at("2026-06-22"),
                at("2026-09-01"), at("2026-09-08"),
            ))

            evaluate(userId)

            assertThat(codes).contains("WEEK_STREAK_4")
        }

        @Test
        fun `should count several matches in the same week once`() {
            val codes = stubMetrics(matches = weeklyMatches(
                at("2026-09-22"), at("2026-09-23"), at("2026-09-24"),
                at("2026-09-29"), at("2026-09-30"),
                at("2026-10-06"),
            ))

            evaluate(userId)

            assertThat(codes).doesNotContain("WEEK_STREAK_4")
        }

        @Test
        fun `should build the streak across the year boundary`() {
            val codes = stubMetrics(matches = weeklyMatches(at("2026-12-15"), at("2026-12-22"), at("2026-12-29"), at("2027-01-05")))

            evaluate(userId)

            assertThat(codes).contains("WEEK_STREAK_4")
        }

        @Test
        fun `should assign a late sunday night UTC match to the following week in Rome`() {
            val codes = stubMetrics(matches = weeklyMatches(
                Instant.parse("2026-09-21T18:00:00Z"),
                Instant.parse("2026-09-28T18:00:00Z"),
                Instant.parse("2026-10-04T23:30:00Z"),
                Instant.parse("2026-10-12T18:00:00Z"),
            ))

            evaluate(userId)

            assertThat(codes).contains("WEEK_STREAK_4")
        }

        @Test
        fun `should not depend on the order of the dates`() {
            val codes = stubMetrics(matches = weeklyMatches(at("2026-10-13"), at("2026-09-22"), at("2026-10-06"), at("2026-09-29")))

            evaluate(userId)

            assertThat(codes).contains("WEEK_STREAK_4")
        }
    }

    @Nested
    @DisplayName("backfillOnStartup")
    inner class BackfillOnStartupTests {

        @Test
        fun `should evaluate only the active users`() {
            val active1 = UUID.randomUUID()
            val active2 = UUID.randomUUID()
            every { userRepository.findIdsByAccountStatus(AccountStatus.ACTIVE) } returns listOf(active1, active2)
            val codes1 = stubMetrics(uid = active1, matches = spreadMatches(1))
            stubMetrics(uid = active2)

            service.backfillOnStartup()

            assertThat(codes1).containsExactly("FIRST_MATCH")
            verify(exactly = 1) { userAchievementRepository.findFinishedMatches(active2) }
            verify(exactly = 1) { userRepository.findIdsByAccountStatus(AccountStatus.ACTIVE) }
        }

        @Test
        fun `should never notify during the backfill`() {
            every { CurrentUser.id() } throws IllegalStateException("No authentication")
            every { userRepository.findIdsByAccountStatus(AccountStatus.ACTIVE) } returns listOf(otherUserId)
            stubMetrics(uid = otherUserId, matches = spreadMatches(1))

            service.backfillOnStartup()

            verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should do nothing when there are no active users`() {
            every { userRepository.findIdsByAccountStatus(AccountStatus.ACTIVE) } returns emptyList()

            service.backfillOnStartup()

            verify(exactly = 0) { userAchievementRepository.findFinishedMatches(any()) }
        }

        @Test
        fun `should keep going when one user fails`() {
            val failing = UUID.randomUUID()
            val working = UUID.randomUUID()
            every { userRepository.findIdsByAccountStatus(AccountStatus.ACTIVE) } returns listOf(failing, working)
            every { userAchievementRepository.findFinishedMatches(failing) } throws RuntimeException("DB error")
            val codes = stubMetrics(uid = working, matches = spreadMatches(1))

            assertThatCode { service.backfillOnStartup() }.doesNotThrowAnyException()

            assertThat(codes).containsExactly("FIRST_MATCH")
        }

        @Test
        fun `should not propagate a failure while loading the users`() {
            every { userRepository.findIdsByAccountStatus(AccountStatus.ACTIVE) } throws RuntimeException("DB error")

            assertThatCode { service.backfillOnStartup() }.doesNotThrowAnyException()

            verify(exactly = 0) { userAchievementRepository.findFinishedMatches(any()) }
        }
    }
}