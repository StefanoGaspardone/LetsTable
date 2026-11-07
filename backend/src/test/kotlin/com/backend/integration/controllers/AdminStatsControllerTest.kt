package com.backend.integration.controllers

import com.backend.models.entities.CollectionItem
import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistRepository
import com.backend.services.JwtService
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class AdminStatsControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @Autowired
    private lateinit var collectionItemRepository: CollectionItemRepository

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    @Autowired
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @Autowired
    private lateinit var jwtService: JwtService

    private var nextBggId = 1L

    companion object {
        private const val URL = "/api/v1/admin/stats"
    }

    @AfterEach
    fun cleanUp() {
        wishlistItemRepository.deleteAll()
        wishlistRepository.deleteAll()
        collectionItemRepository.deleteAll()
        matchRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(
        username: String = "user-${UUID.randomUUID()}",
        role: UserRole = UserRole.USER,
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
    ): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = role,
                accountStatus = accountStatus,
            )
        )

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistGame(name: String = "Test Game"): Game =
        gameRepository.saveAndFlush(Game(bggId = nextBggId++, name = name, lastSyncedAt = Instant.now()))

    private fun persistMatch(game: Game, createdBy: User, durationMinutes: Int? = 30): Match =
        matchRepository.saveAndFlush(Match(game = game, createdBy = createdBy, playedAt = Instant.now(), durationMinutes = durationMinutes))

    private fun persistCollectionItem(user: User, game: Game): CollectionItem =
        collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = game))

    private fun persistWishlist(owner: User, isDefault: Boolean = true, name: String = "La mia wishlist"): Wishlist =
        wishlistRepository.saveAndFlush(Wishlist(owner = owner, name = name, isShared = false, isDefault = isDefault))

    private fun persistWishlistItem(wishlist: Wishlist, game: Game): WishlistItem =
        wishlistItemRepository.saveAndFlush(
            WishlistItem(
                wishlist = wishlist,
                game = game,
                addedBy = wishlist.owner,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        )

    private fun getStats(user: User? = persistUser(role = UserRole.ADMIN)): ResultActions =
        mockMvc.perform(get(URL).apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    @Nested
    @DisplayName("users")
    inner class UserStatsTests {

        @Test
        fun `should return total and active user counts`() {
            persistUser(accountStatus = AccountStatus.ACTIVE)
            persistUser(accountStatus = AccountStatus.INACTIVE)

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.activeUsers").value(2))
        }

        @Test
        fun `should not count deleted accounts`() {
            persistUser(accountStatus = AccountStatus.DELETED)
            persistUser(accountStatus = AccountStatus.DELETED)

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalUsers").value(1))
                .andExpect(jsonPath("$.activeUsers").value(1))
                .andExpect(jsonPath("$.activationRate").value(1.0))
        }

        @Test
        fun `should count suspended accounts in the total but not in the activation rate`() {
            persistUser(accountStatus = AccountStatus.SUSPENDED)
            persistUser(accountStatus = AccountStatus.INACTIVE)

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.activeUsers").value(1))
                .andExpect(jsonPath("$.activationRate").value(0.5))
        }

        @Test
        fun `should compute the activation rate from active and inactive users only`() {
            persistUser(accountStatus = AccountStatus.ACTIVE)
            persistUser(accountStatus = AccountStatus.INACTIVE)

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.activationRate").value(2.0 / 3.0))
        }
    }

    @Nested
    @DisplayName("matches")
    inner class MatchStatsTests {

        @Test
        fun `should return the total match count`() {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user)
            persistMatch(game, user)

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(2))
        }

        @Test
        fun `should rank the most played games by match count across all users`() {
            val userA = persistUser()
            val userB = persistUser()
            val frequent = persistGame(name = "Frequent Game")
            val rare = persistGame(name = "Rare Game")

            persistMatch(frequent, userA)
            persistMatch(frequent, userB)
            persistMatch(frequent, userB)
            persistMatch(rare, userA)

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mostPlayedGames[*].gameName").value(contains("Frequent Game", "Rare Game")))
                .andExpect(jsonPath("$.mostPlayedGames[*].count").value(contains(3, 1)))
        }
    }

    @Nested
    @DisplayName("rankings")
    inner class RankingTests {

        @Test
        fun `should rank the most owned games by collection count`() {
            val userA = persistUser()
            val userB = persistUser()
            val popular = persistGame(name = "Popular Game")
            val niche = persistGame(name = "Niche Game")

            persistCollectionItem(userA, niche)
            persistCollectionItem(userA, popular)
            persistCollectionItem(userB, popular)

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mostOwnedGames[*].gameName").value(contains("Popular Game", "Niche Game")))
                .andExpect(jsonPath("$.mostOwnedGames[*].count").value(contains(2, 1)))
        }

        @Test
        fun `should rank the most wished games by wishlist item count`() {
            val desired = persistGame(name = "Desired Game")
            val other = persistGame(name = "Other Game")
            val wishlistA = persistWishlist(persistUser())
            val wishlistB = persistWishlist(persistUser())

            persistWishlistItem(wishlistA, desired)
            persistWishlistItem(wishlistB, desired)
            persistWishlistItem(wishlistB, other)

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mostWishedGames[*].gameName").value(contains("Desired Game", "Other Game")))
                .andExpect(jsonPath("$.mostWishedGames[*].count").value(contains(2, 1)))
        }

        @Test
        fun `should keep only the top ten games`() {
            val users = (1..2).map { persistUser() }
            val games = (1..12).map { persistGame(name = "Game $it") }

            games.forEach { persistCollectionItem(users[0], it) }
            persistCollectionItem(users[1], games[11])

            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mostOwnedGames.length()").value(10))
                .andExpect(jsonPath("$.mostOwnedGames[0].gameName").value("Game 12"))
                .andExpect(jsonPath("$.mostOwnedGames[0].count").value(2))
        }

        @Test
        fun `should return empty rankings when there is no data`() {
            getStats()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.mostOwnedGames.length()").value(0))
                .andExpect(jsonPath("$.mostPlayedGames.length()").value(0))
                .andExpect(jsonPath("$.mostWishedGames.length()").value(0))
        }
    }

    @Nested
    @DisplayName("security")
    inner class SecurityTests {

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            getStats(user = persistUser())
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            getStats(user = null)
                .andExpect(status().isForbidden)
        }
    }
}