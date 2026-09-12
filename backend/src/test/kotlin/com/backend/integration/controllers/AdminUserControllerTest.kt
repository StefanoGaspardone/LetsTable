package com.backend.integration.controllers

import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchRepository
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class AdminUserControllerTest : AbstractIntegrationTest() {

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

    private fun persistGame(bggId: Long = (1..1_000_000).random().toLong()): Game =
        gameRepository.saveAndFlush(Game(bggId = bggId, name = "Test Game", lastSyncedAt = Instant.now()))

    private fun persistMatch(game: Game, createdBy: User): Match =
        matchRepository.saveAndFlush(
            Match(
                game = game,
                createdBy = createdBy,
                playedAt = Instant.now(),
                durationMinutes = 30
            )
        )

    @AfterEach
    fun cleanUp() {
        matchRepository.deleteAll()
        collectionItemRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/admin/users
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/admin/users")
    inner class ListUsersTests {

        @Test
        fun `should return a paginated list of users to an admin`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            persistUser(username = "regular1")
            persistUser(username = "regular2")

            mockMvc.perform(
                get("/api/v1/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(3))
        }

        @Test
        fun `should filter by role`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            persistUser(username = "regular1")

            mockMvc.perform(
                get("/api/v1/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
                    .param("role", "ADMIN")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
        }

        @Test
        fun `should filter by account status`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            persistUser(username = "suspended1", accountStatus = AccountStatus.SUSPENDED)

            mockMvc.perform(
                get("/api/v1/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
                    .param("status", "SUSPENDED")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].accountStatus").value("SUSPENDED"))
        }

        @Test
        fun `should filter by search term matching username or email`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            persistUser(username = "marco")
            persistUser(username = "anna")

            mockMvc.perform(
                get("/api/v1/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
                    .param("search", "marco")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("marco"))
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser(username = "regular1")

            mockMvc.perform(
                get("/api/v1/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/admin/users/{userId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/admin/users/{userId}")
    inner class GetUserDetailTests {

        @Test
        fun `should return user detail with stats`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            val target = persistUser(username = "target1")
            val game = persistGame()
            persistMatch(game, target)

            mockMvc.perform(
                get("/api/v1/admin/users/${target.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.user.id").value(target.id.toString()))
                .andExpect(jsonPath("$.totalMatches").value(1))
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)

            mockMvc.perform(
                get("/api/v1/admin/users/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser(username = "regular1")
            val target = persistUser(username = "target1")

            mockMvc.perform(
                get("/api/v1/admin/users/${target.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }
    }

// ---------------------------------------------------------------------
// PATCH /api/v1/admin/users/{userId}/suspend
// ---------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/v1/admin/users/{userId}/suspend")
    inner class SuspendUserTests {

        @Test
        fun `should suspend an active user`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            val target = persistUser(username = "target1", accountStatus = AccountStatus.ACTIVE)

            mockMvc.perform(
                patch("/api/v1/admin/users/${target.id}/suspend")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))

            val updated = userRepository.findById(target.id!!).orElseThrow()
            assertThat(updated.accountStatus).isEqualTo(AccountStatus.SUSPENDED)
        }

        @Test
        fun `should return 400 when an admin tries to suspend their own account`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)

            mockMvc.perform(
                patch("/api/v1/admin/users/${admin.id}/suspend")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isConflict)
        }

        @Test
        fun `should return 400 when the account is not currently active`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            val target = persistUser(username = "target1", accountStatus = AccountStatus.SUSPENDED)

            mockMvc.perform(
                patch("/api/v1/admin/users/${target.id}/suspend")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)

            mockMvc.perform(
                patch("/api/v1/admin/users/${UUID.randomUUID()}/suspend")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser(username = "regular1")
            val target = persistUser(username = "target1")

            mockMvc.perform(
                patch("/api/v1/admin/users/${target.id}/suspend")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }
    }

// ---------------------------------------------------------------------
// PATCH /api/v1/admin/users/{userId}/reactivate
// ---------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/v1/admin/users/{userId}/reactivate")
    inner class ReactivateUserTests {

        @Test
        fun `should reactivate a suspended user`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            val target = persistUser(username = "target1", accountStatus = AccountStatus.SUSPENDED)

            mockMvc.perform(
                patch("/api/v1/admin/users/${target.id}/reactivate")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))

            val updated = userRepository.findById(target.id!!).orElseThrow()
            assertThat(updated.accountStatus).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        fun `should return 400 when the account is not currently suspended`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            val target = persistUser(username = "target1", accountStatus = AccountStatus.ACTIVE)

            mockMvc.perform(
                patch("/api/v1/admin/users/${target.id}/reactivate")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)

            mockMvc.perform(
                patch("/api/v1/admin/users/${UUID.randomUUID()}/reactivate")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser(username = "regular1")
            val target = persistUser(username = "target1", accountStatus = AccountStatus.SUSPENDED)

            mockMvc.perform(
                patch("/api/v1/admin/users/${target.id}/reactivate")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }
    }

// ---------------------------------------------------------------------
// POST /api/v1/admin/users/admin
// ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/admin/users/admin")
    inner class CreateAdminTests {

        @Test
        fun `should create a new admin account`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            val payload = """
            {
                "username": "newadmin",
                "email": "newadmin@example.com",
                "password": "password123"
            }
        """.trimIndent()

            mockMvc.perform(
                post("/api/v1/admin/users/admin")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))

            val created = userRepository.findByUsernameIgnoreCase("newadmin").orElseThrow()
            assertThat(created.role).isEqualTo(UserRole.ADMIN)
            assertThat(created.accountStatus).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        fun `should return 409 when the email is already taken`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            persistUser(username = "existing", role = UserRole.USER)

            val payload = """
            {
                "username": "newadmin",
                "email": "existing@example.com",
                "password": "password123"
            }
        """.trimIndent()

            mockMvc.perform(
                post("/api/v1/admin/users/admin")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when the username is already taken`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            persistUser(username = "taken", role = UserRole.USER)

            val payload = """
            {
                "username": "taken",
                "email": "newadmin@example.com",
                "password": "password123"
            }
        """.trimIndent()

            mockMvc.perform(
                post("/api/v1/admin/users/admin")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isConflict)
        }

        @Test
        fun `should return 400 when the payload is invalid`() {
            val admin = persistUser(username = "admin1", role = UserRole.ADMIN)
            val payload = """
            {
                "username": "ab",
                "email": "not-an-email",
                "password": "short"
            }
        """.trimIndent()

            mockMvc.perform(
                post("/api/v1/admin/users/admin")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser(username = "regular1")
            val payload = """
            {
                "username": "newadmin",
                "email": "newadmin@example.com",
                "password": "password123"
            }
        """.trimIndent()

            mockMvc.perform(
                post("/api/v1/admin/users/admin")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isForbidden)
        }
    }
}