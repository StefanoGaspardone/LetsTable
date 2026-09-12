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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
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

    private fun persistGame(bggId: Long = (1..1_000_000).random().toLong(), name: String = "Test Game"): Game =
        gameRepository.saveAndFlush(Game(bggId = bggId, name = name, lastSyncedAt = Instant.now()))

    private fun persistMatch(game: Game, createdBy: User): Match =
        matchRepository.saveAndFlush(Match(game = game, createdBy = createdBy, playedAt = Instant.now(), durationMinutes = 30))

    private fun persistCollectionItem(user: User, game: Game): CollectionItem =
        collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = game))

    private fun persistWishlistWithItem(owner: User, game: Game): WishlistItem {
        val wishlist = wishlistRepository.saveAndFlush(Wishlist(owner = owner, name = "My Wishlist", isShared = false, isDefault = true))
        return wishlistItemRepository.saveAndFlush(WishlistItem(
            wishlist = wishlist,
            game = game,
            addedBy = owner,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        ))
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

    @Nested
    @DisplayName("GET /api/v1/admin/stats")
    inner class GetStatsTests {

        @Test
        fun `should return total and active user counts`() {
            val admin = persistUser(role = UserRole.ADMIN)
            persistUser(accountStatus = AccountStatus.ACTIVE)
            persistUser(accountStatus = AccountStatus.INACTIVE)

            mockMvc.perform(
                get("/api/v1/admin/stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.activeUsers").value(2))
        }

        @Test
        fun `should compute activation rate from active and inactive users only`() {
            val admin = persistUser(role = UserRole.ADMIN, accountStatus = AccountStatus.ACTIVE)
            persistUser(accountStatus = AccountStatus.ACTIVE)
            persistUser(accountStatus = AccountStatus.INACTIVE)

            mockMvc.perform(
                get("/api/v1/admin/stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.activationRate").value(2.0 / 3.0))
        }

        @Test
        fun `should return total match count`() {
            val admin = persistUser(role = UserRole.ADMIN)
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user)
            persistMatch(game, user)

            mockMvc.perform(
                get("/api/v1/admin/stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(2))
        }

        @Test
        fun `should rank the most owned games by collection count`() {
            val admin = persistUser(role = UserRole.ADMIN)
            val userA = persistUser()
            val userB = persistUser()
            val popularGame = persistGame(name = "Popular Game")
            val nichGame = persistGame(name = "Niche Game")

            persistCollectionItem(userA, popularGame)
            persistCollectionItem(userB, popularGame)
            persistCollectionItem(userA, nichGame)

            mockMvc.perform(
                get("/api/v1/admin/stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mostOwnedGames[0].gameName").value("Popular Game"))
                .andExpect(jsonPath("$.mostOwnedGames[0].count").value(2))
                .andExpect(jsonPath("$.mostOwnedGames[1].gameName").value("Niche Game"))
                .andExpect(jsonPath("$.mostOwnedGames[1].count").value(1))
        }

        @Test
        fun `should rank the most played games by match count`() {
            val admin = persistUser(role = UserRole.ADMIN)
            val user = persistUser()
            val frequentGame = persistGame(name = "Frequent Game")
            val rareGame = persistGame(name = "Rare Game")

            persistMatch(frequentGame, user)
            persistMatch(frequentGame, user)
            persistMatch(frequentGame, user)
            persistMatch(rareGame, user)

            mockMvc.perform(
                get("/api/v1/admin/stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mostPlayedGames[0].gameName").value("Frequent Game"))
                .andExpect(jsonPath("$.mostPlayedGames[0].count").value(3))
        }

        @Test
        fun `should rank the most wished games by wishlist item count`() {
            val admin = persistUser(role = UserRole.ADMIN)
            val userA = persistUser()
            val userB = persistUser()
            val desiredGame = persistGame(name = "Desired Game")

            persistWishlistWithItem(userA, desiredGame)
            persistWishlistWithItem(userB, desiredGame)

            mockMvc.perform(
                get("/api/v1/admin/stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mostWishedGames[0].gameName").value("Desired Game"))
                .andExpect(jsonPath("$.mostWishedGames[0].count").value(2))
        }

        @Test
        fun `should return empty rankings when there is no data`() {
            val admin = persistUser(role = UserRole.ADMIN)

            mockMvc.perform(
                get("/api/v1/admin/stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mostOwnedGames.length()").value(0))
                .andExpect(jsonPath("$.mostPlayedGames.length()").value(0))
                .andExpect(jsonPath("$.mostWishedGames.length()").value(0))
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/admin/stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(get("/api/v1/admin/stats")).andExpect(status().isForbidden)
        }
    }
}