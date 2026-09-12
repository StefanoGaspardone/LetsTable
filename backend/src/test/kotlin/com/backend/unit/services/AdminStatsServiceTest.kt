package com.backend.unit.services

import com.backend.models.enums.AccountStatus
import com.backend.models.projections.GamePopularityProjection
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.services.AdminStatsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class AdminStatsServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var matchRepository: MatchRepository

    @Mock
    private lateinit var collectionItemRepository: CollectionItemRepository

    @Mock
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @InjectMocks
    private lateinit var adminStatsService: AdminStatsService

    private fun buildProjection(gameId: UUID = UUID.randomUUID(), gameName: String = "Test Game", count: Long = 5L): GamePopularityProjection =
        object : GamePopularityProjection {
            override val gameId = gameId
            override val gameName = gameName
            override val count = count
        }

    @Nested
    @DisplayName("getStats")
    inner class GetStats {

        @Test
        fun `should return total and active user counts`() {
            whenever(userRepository.count()).thenReturn(100L)
            whenever(userRepository.countByAccountStatus(AccountStatus.ACTIVE)).thenReturn(80L)
            whenever(userRepository.countByAccountStatus(AccountStatus.INACTIVE)).thenReturn(20L)
            whenever(matchRepository.count()).thenReturn(50L)
            whenever(collectionItemRepository.findMostOwnedGames(any())).thenReturn(emptyList())
            whenever(matchRepository.findMostPlayedGames(any())).thenReturn(emptyList())
            whenever(wishlistItemRepository.findMostWishedGames(any())).thenReturn(emptyList())

            val result = adminStatsService.getStats()

            assertThat(result.totalUsers).isEqualTo(100L)
            assertThat(result.activeUsers).isEqualTo(80L)
        }

        @Test
        fun `should compute activation rate correctly`() {
            whenever(userRepository.count()).thenReturn(100L)
            whenever(userRepository.countByAccountStatus(AccountStatus.ACTIVE)).thenReturn(75L)
            whenever(userRepository.countByAccountStatus(AccountStatus.INACTIVE)).thenReturn(25L)
            whenever(matchRepository.count()).thenReturn(0L)
            whenever(collectionItemRepository.findMostOwnedGames(any())).thenReturn(emptyList())
            whenever(matchRepository.findMostPlayedGames(any())).thenReturn(emptyList())
            whenever(wishlistItemRepository.findMostWishedGames(any())).thenReturn(emptyList())

            val result = adminStatsService.getStats()

            assertThat(result.activationRate).isEqualTo(0.75)
        }

        @Test
        fun `should return activation rate of zero when there are no active or inactive users`() {
            whenever(userRepository.count()).thenReturn(0L)
            whenever(userRepository.countByAccountStatus(AccountStatus.ACTIVE)).thenReturn(0L)
            whenever(userRepository.countByAccountStatus(AccountStatus.INACTIVE)).thenReturn(0L)
            whenever(matchRepository.count()).thenReturn(0L)
            whenever(collectionItemRepository.findMostOwnedGames(any())).thenReturn(emptyList())
            whenever(matchRepository.findMostPlayedGames(any())).thenReturn(emptyList())
            whenever(wishlistItemRepository.findMostWishedGames(any())).thenReturn(emptyList())

            val result = adminStatsService.getStats()

            assertThat(result.activationRate).isEqualTo(0.0)
        }

        @Test
        fun `should not count deleted or suspended users in the activation rate denominator`() {
            whenever(userRepository.count()).thenReturn(120L)
            whenever(userRepository.countByAccountStatus(AccountStatus.ACTIVE)).thenReturn(50L)
            whenever(userRepository.countByAccountStatus(AccountStatus.INACTIVE)).thenReturn(50L)
            whenever(matchRepository.count()).thenReturn(0L)
            whenever(collectionItemRepository.findMostOwnedGames(any())).thenReturn(emptyList())
            whenever(matchRepository.findMostPlayedGames(any())).thenReturn(emptyList())
            whenever(wishlistItemRepository.findMostWishedGames(any())).thenReturn(emptyList())

            val result = adminStatsService.getStats()

            assertThat(result.activationRate).isEqualTo(0.5)
        }

        @Test
        fun `should return total match count`() {
            whenever(userRepository.count()).thenReturn(10L)
            whenever(userRepository.countByAccountStatus(AccountStatus.ACTIVE)).thenReturn(10L)
            whenever(userRepository.countByAccountStatus(AccountStatus.INACTIVE)).thenReturn(0L)
            whenever(matchRepository.count()).thenReturn(42L)
            whenever(collectionItemRepository.findMostOwnedGames(any())).thenReturn(emptyList())
            whenever(matchRepository.findMostPlayedGames(any())).thenReturn(emptyList())
            whenever(wishlistItemRepository.findMostWishedGames(any())).thenReturn(emptyList())

            val result = adminStatsService.getStats()

            assertThat(result.totalMatches).isEqualTo(42L)
        }

        @Test
        fun `should map the most owned games ranking`() {
            val projection = buildProjection(gameName = "Catan", count = 15L)

            whenever(userRepository.count()).thenReturn(10L)
            whenever(userRepository.countByAccountStatus(AccountStatus.ACTIVE)).thenReturn(10L)
            whenever(userRepository.countByAccountStatus(AccountStatus.INACTIVE)).thenReturn(0L)
            whenever(matchRepository.count()).thenReturn(0L)
            whenever(collectionItemRepository.findMostOwnedGames(any())).thenReturn(listOf(projection))
            whenever(matchRepository.findMostPlayedGames(any())).thenReturn(emptyList())
            whenever(wishlistItemRepository.findMostWishedGames(any())).thenReturn(emptyList())

            val result = adminStatsService.getStats()

            assertThat(result.mostOwnedGames).hasSize(1)
            assertThat(result.mostOwnedGames[0].gameName).isEqualTo("Catan")
            assertThat(result.mostOwnedGames[0].count).isEqualTo(15L)
        }

        @Test
        fun `should map the most played games ranking`() {
            val projection = buildProjection(gameName = "Ark Nova", count = 30L)

            whenever(userRepository.count()).thenReturn(10L)
            whenever(userRepository.countByAccountStatus(AccountStatus.ACTIVE)).thenReturn(10L)
            whenever(userRepository.countByAccountStatus(AccountStatus.INACTIVE)).thenReturn(0L)
            whenever(matchRepository.count()).thenReturn(30L)
            whenever(collectionItemRepository.findMostOwnedGames(any())).thenReturn(emptyList())
            whenever(matchRepository.findMostPlayedGames(any())).thenReturn(listOf(projection))
            whenever(wishlistItemRepository.findMostWishedGames(any())).thenReturn(emptyList())

            val result = adminStatsService.getStats()

            assertThat(result.mostPlayedGames).hasSize(1)
            assertThat(result.mostPlayedGames[0].gameName).isEqualTo("Ark Nova")
            assertThat(result.mostPlayedGames[0].count).isEqualTo(30L)
        }

        @Test
        fun `should map the most wished games ranking`() {
            val projection = buildProjection(gameName = "Twilight Imperium", count = 8L)

            whenever(userRepository.count()).thenReturn(10L)
            whenever(userRepository.countByAccountStatus(AccountStatus.ACTIVE)).thenReturn(10L)
            whenever(userRepository.countByAccountStatus(AccountStatus.INACTIVE)).thenReturn(0L)
            whenever(matchRepository.count()).thenReturn(0L)
            whenever(collectionItemRepository.findMostOwnedGames(any())).thenReturn(emptyList())
            whenever(matchRepository.findMostPlayedGames(any())).thenReturn(emptyList())
            whenever(wishlistItemRepository.findMostWishedGames(any())).thenReturn(listOf(projection))

            val result = adminStatsService.getStats()

            assertThat(result.mostWishedGames).hasSize(1)
            assertThat(result.mostWishedGames[0].gameName).isEqualTo("Twilight Imperium")
            assertThat(result.mostWishedGames[0].count).isEqualTo(8L)
        }

        @Test
        fun `should rethrow exception when a repository call fails`() {
            whenever(userRepository.count()).thenThrow(RuntimeException("DB error"))

            org.assertj.core.api.Assertions.assertThatThrownBy {
                adminStatsService.getStats()
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB error")
        }
    }
}