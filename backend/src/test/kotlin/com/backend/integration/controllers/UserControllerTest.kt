package com.backend.integration.controllers

import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.RefreshToken
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@AutoConfigureMockMvc
class UserControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @Autowired
    private lateinit var matchPlayerRepository: MatchPlayerRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var jwtService: JwtService

    private fun persistUser(username: String = "stefano"): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = UserRole.USER,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistGame(bggId: Long = (1..1_000_000).random().toLong()): Game =
        gameRepository.saveAndFlush(Game(bggId = bggId, name = "Test Game", lastSyncedAt = Instant.now()))

    private fun persistMatch(game: Game, createdBy: User): Match =
        matchRepository.saveAndFlush(Match(game = game, createdBy = createdBy, playedAt = LocalDate.now()))

    private fun persistPlayer(match: Match, user: User? = null, guestName: String? = null): MatchPlayer =
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, user = user, guestName = guestName))

    private fun persistRefreshToken(user: User, revoked: Boolean = false): RefreshToken =
        refreshTokenRepository.saveAndFlush(
            RefreshToken(
                user = user,
                tokenHash = "hash-${UUID.randomUUID()}",
                expiresAt = Instant.now().plusSeconds(3600),
                revoked = revoked,
            )
        )

    @AfterEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        matchPlayerRepository.deleteAll()
        matchRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/search
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/search")
    inner class SearchByUsernameTests {

        @Test
        fun `should return matching active users excluding the current user`() {
            val user = persistUser(username = "marco-self")
            val otherUser = persistUser(username = "marco-other")

            mockMvc.perform(
                get("/api/v1/users/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "marco")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherUser.id.toString()))
        }

        @Test
        fun `should return an empty list when no users match`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/users/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "nonexistent-name")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not return inactive users`() {
            val user = persistUser(username = "searcher")
            userRepository.saveAndFlush(
                User(
                    username = "inactive-marco",
                    email = "inactive@example.com",
                    passwordHash = "hash",
                    role = UserRole.USER,
                    accountStatus = AccountStatus.INACTIVE,
                )
            )

            mockMvc.perform(
                get("/api/v1/users/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "marco")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/{userId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{userId}")
    inner class GetUserTests {

        @Test
        fun `should return the public profile of any user`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")

            mockMvc.perform(
                get("/api/v1/users/${target.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("target"))
        }

        @Test
        fun `should return 404 when user does not exist`() {
            val requester = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val target = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${target.id}")
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/me
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/me")
    inner class GetMyProfileTests {

        @Test
        fun `should return the authenticated user's own profile`() {
            val user = persistUser(username = "myself")

            mockMvc.perform(
                get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(user.id.toString()))
                .andExpect(jsonPath("$.username").value("myself"))
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/users/me
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/users/me")
    inner class DeleteMyAccountTests {

        @Test
        fun `should anonymize the account and return 200`() {
            val user = persistUser(username = "to-delete")

            mockMvc.perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isOk)

            val updated = userRepository.findById(user.id!!).orElseThrow()
            assertThat(updated.username).isEqualTo("deleted-user-${user.id}")
            assertThat(updated.email).isEqualTo("deleted-${user.id}@letstable.invalid")
            assertThat(updated.passwordHash).isEmpty()
            assertThat(updated.accountStatus).isEqualTo(AccountStatus.DELETED)
        }

        @Test
        fun `should delete solo matches but keep matches shared with others`() {
            val user = persistUser(username = "to-delete")
            val otherUser = persistUser(username = "stays")
            val game = persistGame()

            val soloMatch = persistMatch(game, user)
            persistPlayer(soloMatch, user = user)

            val sharedMatch = persistMatch(game, user)
            persistPlayer(sharedMatch, user = user)
            persistPlayer(sharedMatch, user = otherUser)

            mockMvc.perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isOk)

            assertThat(matchRepository.findById(soloMatch.id!!)).isEmpty()
            assertThat(matchRepository.findById(sharedMatch.id!!)).isPresent
        }

        @Test
        fun `should revoke all active refresh tokens`() {
            val user = persistUser()
            val token = persistRefreshToken(user, revoked = false)

            mockMvc.perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isOk)

            val updated = refreshTokenRepository.findById(token.id!!).orElseThrow()
            assertThat(updated.revoked).isTrue()
        }

        @Test
        fun `should return a success message`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.message").value("Your account has been deleted"))
        }
    }
}