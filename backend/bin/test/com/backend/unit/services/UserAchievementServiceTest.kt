package com.backend.unit.services

import com.backend.exceptions.UserNotFoundException
import com.backend.models.entities.User
import com.backend.models.entities.UserAchievement
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.AchievementType
import com.backend.models.enums.FriendRequestStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.UserAchievementRepository
import com.backend.repositories.UserRepository
import com.backend.security.CurrentUser
import com.backend.services.UserAchievementService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@ExtendWith(MockKExtension::class)
class UserAchievementServiceTest {

    @MockK
    private lateinit var userAchievementRepository: UserAchievementRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @RelaxedMockK
    private lateinit var transactionManager: PlatformTransactionManager

    @InjectMockKs
    private lateinit var userAchievementService: UserAchievementService

    private val userId: UUID = UUID.randomUUID()
    private val otherUserId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mockkObject(CurrentUser)
        every { CurrentUser.id() } returns userId
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CurrentUser)

        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    // ---------- helpers ----------

    private fun at(date: String): Instant =
        LocalDate.parse(date).atTime(20, 0).atZone(ZoneId.of("Europe/Rome")).toInstant()

    /** N date molto distanziate (30 giorni), cosi' non formano mai una serie settimanale per caso. */
    private fun spreadDates(count: Int): List<Instant> =
        (1..count).map { Instant.parse("2026-01-01T10:00:00Z").plus(Duration.ofDays(it * 30L)) }

    private fun stubMetrics(
        uid: UUID = userId,
        dates: List<Instant> = emptyList(),
        wins: List<Boolean> = List(dates.size) { false },
        distinctGames: Long = 0L,
        collection: Long = 0L,
        friends: Long = 0L,
        alreadyUnlocked: Set<String> = emptySet(),
    ) {
        every { userAchievementRepository.findFinishedMatchDates(uid) } returns dates
        every { userAchievementRepository.findFinishedMatchWins(uid) } returns wins
        every { userAchievementRepository.countDistinctGamesPlayed(uid) } returns distinctGames
        every { userAchievementRepository.countCollectionItems(uid) } returns collection
        every { userAchievementRepository.countFriends(uid, FriendRequestStatus.ACCEPTED) } returns friends
        every { userAchievementRepository.findCodesByUserId(uid) } returns alreadyUnlocked
    }

    private fun captureUnlockedCodes(uid: UUID = userId): MutableList<String> {
        val codes = mutableListOf<String>()
        every { userAchievementRepository.insertIfAbsent(uid, capture(codes), any()) } returns 1
        return codes
    }

    private fun row(code: String, seen: Boolean = false) = UserAchievement(
        id = UUID.randomUUID(),
        userId = userId,
        achievementCode = code,
        unlockedAt = Instant.parse("2026-10-01T10:00:00Z"),
        seenAt = if(seen) Instant.parse("2026-10-02T10:00:00Z") else null,
    )

    private fun user(id: UUID?, status: AccountStatus) = User(
        id = id,
        username = "user-${id ?: "null"}",
        email = "${id ?: "null"}@test.com",
        passwordHash = "hash",
        role = UserRole.USER,
        accountStatus = status,
    )

    // ---------- getUserAchievements ----------

    @Nested
    @DisplayName("getUserAchievements")
    inner class GetUserAchievements {

        @Test
        fun `should throw UserNotFoundException when user does not exist`() {
            every { userRepository.existsById(otherUserId) } returns false

            assertThatThrownBy { userAchievementService.getUserAchievements(otherUserId) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { userAchievementRepository.findAllByUserId(any()) }
            verify(exactly = 0) { userAchievementRepository.insertIfAbsent(any(), any(), any()) }
        }

        @Test
        fun `should return the whole catalog with progress capped at the target`() {
            every { userRepository.existsById(otherUserId) } returns true
            stubMetrics(
                uid = otherUserId,
                dates = spreadDates(3),
                wins = listOf(true, false, false),
                collection = 12,
            )
            every { userAchievementRepository.findAllByUserId(otherUserId) } returns listOf(
                UserAchievement(
                    id = UUID.randomUUID(),
                    userId = otherUserId,
                    achievementCode = "FIRST_MATCH",
                    unlockedAt = Instant.parse("2026-10-01T10:00:00Z"),
                )
            )

            val result = userAchievementService.getUserAchievements(otherUserId)

            assertThat(result).hasSize(AchievementType.entries.size)

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
        fun `should not write anything when viewing another user's achievements`() {
            every { userRepository.existsById(otherUserId) } returns true
            stubMetrics(uid = otherUserId, dates = spreadDates(1))
            every { userAchievementRepository.findAllByUserId(otherUserId) } returns emptyList()

            userAchievementService.getUserAchievements(otherUserId)

            verify(exactly = 0) { userAchievementRepository.insertIfAbsent(any(), any(), any()) }
        }

        @Test
        fun `should unlock reached achievements when viewing your own profile`() {
            every { userRepository.existsById(userId) } returns true
            stubMetrics(dates = spreadDates(1))
            val codes = captureUnlockedCodes()
            every { userAchievementRepository.findAllByUserId(userId) } returns listOf(row("FIRST_MATCH"))

            val result = userAchievementService.getUserAchievements(userId)

            assertThat(codes).containsExactly("FIRST_MATCH")
            assertThat(result.first { it.code == "FIRST_MATCH" }.unlocked).isTrue()
        }

        @Test
        fun `should rethrow generic exception`() {
            every { userRepository.existsById(userId) } throws RuntimeException("DB error")

            assertThatThrownBy { userAchievementService.getUserAchievements(userId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }

    // ---------- getUnseenAchievements ----------

    @Nested
    @DisplayName("getUnseenAchievements")
    inner class GetUnseenAchievements {

        @Test
        fun `should evaluate first and then return the unseen achievements oldest first`() {
            stubMetrics(dates = spreadDates(1))
            captureUnlockedCodes()
            every {
                userAchievementRepository.findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId)
            } returns listOf(row("FIRST_MATCH"), row("FIRST_WIN"))

            val result = userAchievementService.getUnseenAchievements()

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
            every {
                userAchievementRepository.findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId)
            } returns emptyList()

            val result = userAchievementService.getUnseenAchievements()

            assertThat(result).isEmpty()
        }

        @Test
        fun `should skip codes that are no longer in the catalog`() {
            stubMetrics()
            every {
                userAchievementRepository.findAllByUserIdAndSeenAtIsNullOrderByUnlockedAtAsc(userId)
            } returns listOf(row("REMOVED_ACHIEVEMENT"), row("FIRST_WIN"))

            val result = userAchievementService.getUnseenAchievements()

            assertThat(result.map { it.code }).containsExactly("FIRST_WIN")
        }

        @Test
        fun `should rethrow generic exception`() {
            every { userAchievementRepository.findFinishedMatchDates(userId) } throws RuntimeException("DB error")

            assertThatThrownBy { userAchievementService.getUnseenAchievements() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }

    // ---------- markSeen ----------

    @Nested
    @DisplayName("markSeen")
    inner class MarkSeen {

        @Test
        fun `should mark the given codes as seen for the current user`() {
            val codes = listOf("FIRST_MATCH", "FIRST_WIN")
            every { userAchievementRepository.markSeen(userId, codes, any()) } returns 2

            userAchievementService.markSeen(codes)

            verify(exactly = 1) { userAchievementRepository.markSeen(userId, codes, any()) }
        }

        @Test
        fun `should do nothing when the list of codes is empty`() {
            userAchievementService.markSeen(emptyList())

            verify { userAchievementRepository wasNot Called }
        }

        @Test
        fun `should rethrow generic exception`() {
            every { userAchievementRepository.markSeen(any(), any(), any()) } throws RuntimeException("DB error")

            assertThatThrownBy { userAchievementService.markSeen(listOf("FIRST_MATCH")) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }

    // ---------- evaluateAfterCommit ----------

    @Nested
    @DisplayName("evaluateAfterCommit")
    inner class EvaluateAfterCommit {

        @Test
        fun `should do nothing when there are no users`() {
            userAchievementService.evaluateAfterCommit(emptySet())

            verify { userAchievementRepository wasNot Called }
        }

        @Test
        fun `should evaluate immediately when there is no active transaction`() {
            stubMetrics(dates = spreadDates(1))
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).containsExactly("FIRST_MATCH")
        }

        @Test
        fun `should wait for the commit when a transaction is active`() {
            stubMetrics(dates = spreadDates(1))
            val codes = captureUnlockedCodes()

            TransactionSynchronizationManager.initSynchronization()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            // prima del commit non deve essere stato toccato niente
            verify { userAchievementRepository wasNot Called }
            assertThat(codes).isEmpty()

            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

            assertThat(codes).containsExactly("FIRST_MATCH")
        }

        @Test
        fun `should not evaluate anything when the transaction never commits`() {
            TransactionSynchronizationManager.initSynchronization()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            // simula il rollback: afterCommit non viene mai chiamato
            verify { userAchievementRepository wasNot Called }
        }

        @Test
        fun `should evaluate every user passed`() {
            stubMetrics(uid = userId, dates = spreadDates(1))
            stubMetrics(uid = otherUserId, friends = 1)
            val codesUser = captureUnlockedCodes(userId)
            val codesOther = captureUnlockedCodes(otherUserId)

            userAchievementService.evaluateAfterCommit(listOf(userId, otherUserId))

            assertThat(codesUser).containsExactly("FIRST_MATCH")
            assertThat(codesOther).containsExactly("FIRST_FRIEND")
        }

        @Test
        fun `should keep evaluating the other users when one of them fails`() {
            every { userAchievementRepository.findFinishedMatchDates(userId) } throws RuntimeException("DB error")
            stubMetrics(uid = otherUserId, dates = spreadDates(1))
            val codesOther = captureUnlockedCodes(otherUserId)

            assertThatCode { userAchievementService.evaluateAfterCommit(listOf(userId, otherUserId)) }
                .doesNotThrowAnyException()

            assertThat(codesOther).containsExactly("FIRST_MATCH")
        }

        @Test
        fun `should not insert achievements that are already unlocked`() {
            stubMetrics(dates = spreadDates(1), alreadyUnlocked = setOf("FIRST_MATCH"))

            userAchievementService.evaluateAfterCommit(setOf(userId))

            verify(exactly = 0) { userAchievementRepository.insertIfAbsent(any(), any(), any()) }
        }

        @Test
        fun `should not fail when the insert reports the achievement already exists`() {
            stubMetrics(dates = spreadDates(1))
            every { userAchievementRepository.insertIfAbsent(userId, any(), any()) } returns 0

            assertThatCode { userAchievementService.evaluateAfterCommit(setOf(userId)) }
                .doesNotThrowAnyException()

            verify(exactly = 1) { userAchievementRepository.insertIfAbsent(userId, "FIRST_MATCH", any()) }
        }

        @Test
        fun `should not unlock anything for a user without activity`() {
            stubMetrics()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            verify(exactly = 0) { userAchievementRepository.insertIfAbsent(any(), any(), any()) }
        }
    }

    // ---------- metriche e soglie (testate tramite evaluateAfterCommit) ----------

    @Nested
    @DisplayName("metrics and thresholds")
    inner class Metrics {

        @Test
        fun `should unlock match and win achievements by count`() {
            stubMetrics(
                dates = spreadDates(10),
                wins = List(10) { it < 2 },
            )
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("FIRST_MATCH", "MATCHES_10", "FIRST_WIN")
            assertThat(codes).doesNotContain("MATCHES_50", "WINS_10", "LOSSES_10")
        }

        @Test
        fun `should unlock the losses achievement after 10 losses`() {
            stubMetrics(dates = spreadDates(10), wins = List(10) { false })
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("LOSSES_10")
            assertThat(codes).doesNotContain("FIRST_WIN")
        }

        @Test
        fun `should unlock the explorer achievement with 10 distinct games`() {
            stubMetrics(distinctGames = 10)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).containsExactly("DISTINCT_GAMES_10")
        }

        @Test
        fun `should unlock collection achievements by size`() {
            stubMetrics(collection = 10)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).containsExactly("COLLECTION_10")
        }

        @Test
        fun `should unlock friends achievements by count`() {
            stubMetrics(friends = 5)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).containsExactlyInAnyOrder("FIRST_FRIEND", "FRIENDS_5")
        }
    }

    // ---------- streak di vittorie ----------

    @Nested
    @DisplayName("win streak")
    inner class WinStreak {

        @Test
        fun `should unlock the 3 wins streak but not the 5 wins one`() {
            stubMetrics(
                dates = spreadDates(5),
                wins = listOf(true, true, true, false, true),
            )
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("WIN_STREAK_3")
            assertThat(codes).doesNotContain("WIN_STREAK_5")
        }

        @Test
        fun `should reset the streak after a loss`() {
            stubMetrics(
                dates = spreadDates(5),
                wins = listOf(true, true, false, true, true),
            )
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).doesNotContain("WIN_STREAK_3", "WIN_STREAK_5")
        }

        @Test
        fun `should unlock both streak achievements with 5 consecutive wins`() {
            stubMetrics(dates = spreadDates(5), wins = List(5) { true })
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("WIN_STREAK_3", "WIN_STREAK_5")
        }

        @Test
        fun `should use the record, not the current streak`() {
            stubMetrics(
                dates = spreadDates(6),
                wins = listOf(true, true, true, false, false, false),
            )
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("WIN_STREAK_3")
        }
    }

    // ---------- streak settimanale ----------

    @Nested
    @DisplayName("weekly play streak")
    inner class WeeklyStreak {

        @Test
        fun `should unlock the 4 weeks streak with 4 consecutive weeks`() {
            val dates = listOf(at("2026-09-22"), at("2026-09-29"), at("2026-10-06"), at("2026-10-13"))
            stubMetrics(dates = dates)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("WEEK_STREAK_4")
            assertThat(codes).doesNotContain("WEEK_STREAK_8")
        }

        @Test
        fun `should unlock the 8 weeks streak with 8 consecutive weeks`() {
            val start = LocalDate.parse("2026-09-01")
            val dates = (0 until 8).map { at(start.plusWeeks(it.toLong()).toString()) }
            stubMetrics(dates = dates)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("WEEK_STREAK_4", "WEEK_STREAK_8")
        }

        @Test
        fun `should break the streak when a week is skipped`() {
            val dates = listOf(at("2026-09-22"), at("2026-09-29"), at("2026-10-13"), at("2026-10-20"))
            stubMetrics(dates = dates)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).doesNotContain("WEEK_STREAK_4")
        }

        @Test
        fun `should count several matches in the same week once`() {
            val dates = listOf(
                at("2026-09-22"), at("2026-09-23"), at("2026-09-24"),
                at("2026-09-29"), at("2026-09-30"),
                at("2026-10-06"),
            )
            stubMetrics(dates = dates)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            // 3 settimane distinte, non 6 partite = 6 settimane
            assertThat(codes).doesNotContain("WEEK_STREAK_4")
        }

        @Test
        fun `should build the streak across the year boundary`() {
            val dates = listOf(at("2026-12-15"), at("2026-12-22"), at("2026-12-29"), at("2027-01-05"))
            stubMetrics(dates = dates)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("WEEK_STREAK_4")
        }

        @Test
        fun `should assign a late sunday night UTC match to the following week in Rome`() {
            // domenica 4 ottobre 23:30 UTC = lunedi' 5 ottobre 01:30 a Roma (settimana successiva).
            // Se il fuso fosse ignorato le settimane sarebbero 21/9, 28/9, 12/10 e la serie si fermerebbe a 2.
            val dates = listOf(
                Instant.parse("2026-09-21T18:00:00Z"),
                Instant.parse("2026-09-28T18:00:00Z"),
                Instant.parse("2026-10-04T23:30:00Z"),
                Instant.parse("2026-10-12T18:00:00Z"),
            )
            stubMetrics(dates = dates)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("WEEK_STREAK_4")
        }

        @Test
        fun `should not depend on the order of the dates`() {
            val dates = listOf(at("2026-10-13"), at("2026-09-22"), at("2026-10-06"), at("2026-09-29"))
            stubMetrics(dates = dates)
            val codes = captureUnlockedCodes()

            userAchievementService.evaluateAfterCommit(setOf(userId))

            assertThat(codes).contains("WEEK_STREAK_4")
        }
    }

    // ---------- backfill ----------

    @Nested
    @DisplayName("backfillOnStartup")
    inner class BackfillOnStartup {

        @Test
        fun `should evaluate only active users`() {
            val active1 = UUID.randomUUID()
            val active2 = UUID.randomUUID()
            val inactive = UUID.randomUUID()
            val suspended = UUID.randomUUID()

            every { userRepository.findAll() } returns listOf(
                user(active1, AccountStatus.ACTIVE),
                user(inactive, AccountStatus.INACTIVE),
                user(active2, AccountStatus.ACTIVE),
                user(suspended, AccountStatus.SUSPENDED),
            )
            stubMetrics(uid = active1, dates = spreadDates(1))
            stubMetrics(uid = active2)
            val codes1 = captureUnlockedCodes(active1)

            userAchievementService.backfillOnStartup()

            assertThat(codes1).containsExactly("FIRST_MATCH")
            verify(exactly = 1) { userAchievementRepository.findFinishedMatchDates(active1) }
            verify(exactly = 1) { userAchievementRepository.findFinishedMatchDates(active2) }
            verify(exactly = 0) { userAchievementRepository.findFinishedMatchDates(inactive) }
            verify(exactly = 0) { userAchievementRepository.findFinishedMatchDates(suspended) }
        }

        @Test
        fun `should skip users without an id`() {
            val active = UUID.randomUUID()

            every { userRepository.findAll() } returns listOf(
                user(null, AccountStatus.ACTIVE),
                user(active, AccountStatus.ACTIVE),
            )
            stubMetrics(uid = active)

            assertThatCode { userAchievementService.backfillOnStartup() }.doesNotThrowAnyException()

            verify(exactly = 1) { userAchievementRepository.findFinishedMatchDates(active) }
        }

        @Test
        fun `should not do anything when there are no users`() {
            every { userRepository.findAll() } returns emptyList()

            userAchievementService.backfillOnStartup()

            verify { userAchievementRepository wasNot Called }
        }

        @Test
        fun `should keep going when one user fails`() {
            val failing = UUID.randomUUID()
            val working = UUID.randomUUID()

            every { userRepository.findAll() } returns listOf(
                user(failing, AccountStatus.ACTIVE),
                user(working, AccountStatus.ACTIVE),
            )
            every { userAchievementRepository.findFinishedMatchDates(failing) } throws RuntimeException("DB error")
            stubMetrics(uid = working, dates = spreadDates(1))
            val codes = captureUnlockedCodes(working)

            assertThatCode { userAchievementService.backfillOnStartup() }.doesNotThrowAnyException()

            assertThat(codes).containsExactly("FIRST_MATCH")
        }

        @Test
        fun `should not propagate the exception when loading the users fails`() {
            every { userRepository.findAll() } throws RuntimeException("DB error")

            assertThatCode { userAchievementService.backfillOnStartup() }.doesNotThrowAnyException()

            verify { userAchievementRepository wasNot Called }
        }
    }
}