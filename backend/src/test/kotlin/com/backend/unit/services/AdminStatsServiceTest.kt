package com.backend.unit.services

import com.backend.models.enums.AccountStatus
import com.backend.models.projections.GamePopularityProjection
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.services.AdminStatsService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Pageable
import java.util.UUID

@ExtendWith(MockKExtension::class)
class AdminStatsServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var matchRepository: MatchRepository

    @MockK
    private lateinit var collectionItemRepository: CollectionItemRepository

    @MockK
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @InjectMockKs
    private lateinit var adminStatsService: AdminStatsService

    private fun buildProjection(gameId: UUID = UUID.randomUUID(), gameName: String = "Test Game", count: Long = 5L): GamePopularityProjection =
        object : GamePopularityProjection {
            override val gameId = gameId
            override val gameName = gameName
            override val gameThumbnailUrl = "https://example.com/thumbnail.jpg"
            override val count = count
        }

    private fun stubStats(
        nonDeletedUsers: Long = 10L,
        activeUsers: Long = 10L,
        inactiveUsers: Long = 0L,
        totalMatches: Long = 0L,
        mostOwned: List<GamePopularityProjection> = emptyList(),
        mostPlayed: List<GamePopularityProjection> = emptyList(),
        mostWished: List<GamePopularityProjection> = emptyList(),
    ) {
        every { userRepository.countByAccountStatusNot(AccountStatus.DELETED) } returns nonDeletedUsers
        every { userRepository.countByAccountStatus(AccountStatus.ACTIVE) } returns activeUsers
        every { userRepository.countByAccountStatus(AccountStatus.INACTIVE) } returns inactiveUsers
        every { matchRepository.count() } returns totalMatches
        every { collectionItemRepository.findMostOwnedGames(any()) } returns mostOwned
        every { matchRepository.findMostPlayedGames(any()) } returns mostPlayed
        every { wishlistItemRepository.findMostWishedGames(any()) } returns mostWished
    }

    @Nested
    @DisplayName("users")
    inner class UserTests {

        @Test
        fun `should return total and active user counts`() {
            stubStats(nonDeletedUsers = 100L, activeUsers = 80L, inactiveUsers = 20L)

            val result = adminStatsService.getStats()

            assertThat(result.totalUsers).isEqualTo(100L)
            assertThat(result.activeUsers).isEqualTo(80L)
        }

        @Test
        fun `should not count deleted accounts in the total`() {
            stubStats(nonDeletedUsers = 95L, activeUsers = 80L, inactiveUsers = 15L)

            adminStatsService.getStats()

            verify(exactly = 1) { userRepository.countByAccountStatusNot(AccountStatus.DELETED) }
            verify(exactly = 0) { userRepository.count() }
        }
    }

    @Nested
    @DisplayName("activation rate")
    inner class ActivationRateTests {

        @Test
        fun `should compute activation rate correctly`() {
            stubStats(nonDeletedUsers = 100L, activeUsers = 75L, inactiveUsers = 25L)

            assertThat(adminStatsService.getStats().activationRate).isEqualTo(0.75)
        }

        @Test
        fun `should return activation rate of zero when there are no active or inactive users`() {
            stubStats(nonDeletedUsers = 0L, activeUsers = 0L, inactiveUsers = 0L)

            assertThat(adminStatsService.getStats().activationRate).isEqualTo(0.0)
        }

        @Test
        fun `should return activation rate of one when every user is active`() {
            stubStats(nonDeletedUsers = 12L, activeUsers = 12L, inactiveUsers = 0L)

            assertThat(adminStatsService.getStats().activationRate).isEqualTo(1.0)
        }

        @Test
        fun `should return activation rate of zero when nobody has activated yet`() {
            stubStats(nonDeletedUsers = 3L, activeUsers = 0L, inactiveUsers = 3L)

            assertThat(adminStatsService.getStats().activationRate).isEqualTo(0.0)
        }

        @Test
        fun `should only use active and inactive users in the denominator`() {
            stubStats(nonDeletedUsers = 120L, activeUsers = 50L, inactiveUsers = 50L)

            assertThat(adminStatsService.getStats().activationRate).isEqualTo(0.5)
        }
    }

    @Nested
    @DisplayName("matches and rankings")
    inner class RankingTests {

        @Test
        fun `should return total match count`() {
            stubStats(totalMatches = 42L)

            assertThat(adminStatsService.getStats().totalMatches).isEqualTo(42L)
        }

        @Test
        fun `should map the most owned games ranking`() {
            val gameId = UUID.randomUUID()
            stubStats(mostOwned = listOf(buildProjection(gameId = gameId, gameName = "Catan", count = 15L)))

            val ranking = adminStatsService.getStats().mostOwnedGames

            assertThat(ranking).hasSize(1)
            assertThat(ranking[0].gameId).isEqualTo(gameId)
            assertThat(ranking[0].gameName).isEqualTo("Catan")
            assertThat(ranking[0].count).isEqualTo(15L)
        }

        @Test
        fun `should map the most played games ranking`() {
            stubStats(totalMatches = 30L, mostPlayed = listOf(buildProjection(gameName = "Ark Nova", count = 30L)))

            val ranking = adminStatsService.getStats().mostPlayedGames

            assertThat(ranking.map { it.gameName to it.count }).containsExactly("Ark Nova" to 30L)
        }

        @Test
        fun `should map the most wished games ranking`() {
            stubStats(mostWished = listOf(buildProjection(gameName = "Twilight Imperium", count = 8L)))

            val ranking = adminStatsService.getStats().mostWishedGames

            assertThat(ranking.map { it.gameName to it.count }).containsExactly("Twilight Imperium" to 8L)
        }

        @Test
        fun `should keep the order returned by the repository`() {
            stubStats(
                mostOwned = listOf(
                    buildProjection(gameName = "Catan", count = 15L),
                    buildProjection(gameName = "Azul", count = 9L),
                    buildProjection(gameName = "Carcassonne", count = 4L),
                )
            )

            assertThat(adminStatsService.getStats().mostOwnedGames.map { it.gameName }).containsExactly("Catan", "Azul", "Carcassonne")
        }

        @Test
        fun `should keep the three rankings separate`() {
            stubStats(
                mostOwned = listOf(buildProjection(gameName = "Owned")),
                mostPlayed = listOf(buildProjection(gameName = "Played")),
                mostWished = listOf(buildProjection(gameName = "Wished")),
            )

            val result = adminStatsService.getStats()

            assertThat(result.mostOwnedGames.map { it.gameName }).containsExactly("Owned")
            assertThat(result.mostPlayedGames.map { it.gameName }).containsExactly("Played")
            assertThat(result.mostWishedGames.map { it.gameName }).containsExactly("Wished")
        }

        @Test
        fun `should ask every ranking for the first ten games`() {
            val ownedPageable = slot<Pageable>()
            val playedPageable = slot<Pageable>()
            val wishedPageable = slot<Pageable>()
            stubStats()
            every { collectionItemRepository.findMostOwnedGames(capture(ownedPageable)) } returns emptyList()
            every { matchRepository.findMostPlayedGames(capture(playedPageable)) } returns emptyList()
            every { wishlistItemRepository.findMostWishedGames(capture(wishedPageable)) } returns emptyList()

            adminStatsService.getStats()

            listOf(ownedPageable, playedPageable, wishedPageable).forEach { pageable ->
                assertThat(pageable.captured.pageNumber).isEqualTo(0)
                assertThat(pageable.captured.pageSize).isEqualTo(10)
            }
        }
    }

    @Nested
    @DisplayName("errors")
    inner class ErrorTests {

        @Test
        fun `should rethrow exception when the user count fails`() {
            every { userRepository.countByAccountStatusNot(AccountStatus.DELETED) } throws RuntimeException("DB error")

            assertThatThrownBy { adminStatsService.getStats() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }

        @Test
        fun `should rethrow exception when a ranking query fails`() {
            stubStats()
            every { wishlistItemRepository.findMostWishedGames(any()) } throws RuntimeException("Ranking query failed")

            assertThatThrownBy { adminStatsService.getStats() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Ranking query failed")
        }
    }
}