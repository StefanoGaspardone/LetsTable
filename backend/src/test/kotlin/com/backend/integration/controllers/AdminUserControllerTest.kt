package com.backend.integration.controllers

import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.RefreshToken
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistRepository
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
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
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    @Autowired
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private var nextBggId = 1L

    companion object {
        private const val BASE_URL = "/api/v1/admin/users"
    }

    @AfterEach
    fun cleanUp() {
        matchRepository.deleteAll()
        collectionItemRepository.deleteAll()
        wishlistItemRepository.deleteAll()
        wishlistRepository.deleteAll()
        refreshTokenRepository.deleteAll()
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

    private fun admin(): User = persistUser(username = "admin-${UUID.randomUUID()}", role = UserRole.ADMIN)

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistGame(): Game =
        gameRepository.saveAndFlush(Game(bggId = nextBggId++, name = "Test Game", lastSyncedAt = Instant.now()))

    private fun persistMatch(game: Game, createdBy: User): Match =
        matchRepository.saveAndFlush(Match(game = game, createdBy = createdBy, playedAt = Instant.now(), durationMinutes = 30))

    private fun persistRefreshToken(user: User): RefreshToken =
        refreshTokenRepository.saveAndFlush(
            RefreshToken(
                user = user,
                tokenHash = "hash-${UUID.randomUUID()}",
                expiresAt = Instant.now().plusSeconds(3600),
                revoked = false,
            )
        )

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User? = admin()): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    private fun adminPayload(username: String = "newadmin", email: String = "newadmin@example.com", password: String = "password123") = """
        {
            "username": "$username",
            "email": "$email",
            "password": "$password"
        }
    """.trimIndent()

    private fun createAdmin(payload: String, user: User? = admin()) =
        perform(post("$BASE_URL/admin").contentType(MediaType.APPLICATION_JSON).content(payload), user)

    @Nested
    @DisplayName("GET /api/v1/admin/users")
    inner class ListUsersTests {

        @Test
        fun `should return a paginated list of users to an admin`() {
            persistUser(username = "regular1")
            persistUser(username = "regular2")

            perform(get(BASE_URL))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should filter by role`() {
            persistUser(username = "regular1")

            perform(get(BASE_URL).param("role", "ADMIN"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
        }

        @Test
        fun `should filter by account status`() {
            persistUser(username = "suspended1", accountStatus = AccountStatus.SUSPENDED)
            persistUser(username = "inactive1", accountStatus = AccountStatus.INACTIVE)

            perform(get(BASE_URL).param("status", "SUSPENDED"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].username").value(containsInAnyOrder("suspended1")))
                .andExpect(jsonPath("$.content[*].accountStatus").value(everyItem(`is`("SUSPENDED"))))
        }

        @Test
        fun `should combine role and status filters`() {
            persistUser(username = "suspended-admin", role = UserRole.ADMIN, accountStatus = AccountStatus.SUSPENDED)
            persistUser(username = "suspended-user", accountStatus = AccountStatus.SUSPENDED)

            perform(get(BASE_URL).param("role", "ADMIN").param("status", "SUSPENDED"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].username").value(containsInAnyOrder("suspended-admin")))
        }

        @Test
        fun `should search by username`() {
            persistUser(username = "marco")
            persistUser(username = "anna")

            perform(get(BASE_URL).param("search", "marco"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].username").value(containsInAnyOrder("marco")))
        }

        @Test
        fun `should search by email`() {
            userRepository.saveAndFlush(User(username = "luca", email = "luca.rossi@club.it", passwordHash = "hash", role = UserRole.USER, accountStatus = AccountStatus.ACTIVE))
            persistUser(username = "anna")

            perform(get(BASE_URL).param("search", "club.it"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].username").value(containsInAnyOrder("luca")))
        }

        @Test
        fun `should paginate the results`() {
            (1..4).forEach { persistUser(username = "user$it") }

            perform(get(BASE_URL).param("page", "1").param("size", "2"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
        }

        @Test
        fun `should return 400 when the status is not a valid value`() {
            perform(get(BASE_URL).param("status", "BANNED"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the sort field is not allowed`() {
            perform(get(BASE_URL).param("sort", "passwordHash-asc"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            perform(get(BASE_URL), user = persistUser(username = "regular1"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get(BASE_URL), user = null)
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users/{userId}")
    inner class GetUserDetailTests {

        @Test
        fun `should return user detail with stats`() {
            val target = persistUser(username = "target1")
            val game = persistGame()
            persistMatch(game, target)

            perform(get("$BASE_URL/${target.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.user.id").value(target.id.toString()))
                .andExpect(jsonPath("$.user.username").value("target1"))
                .andExpect(jsonPath("$.totalMatches").value(1))
        }

        @Test
        fun `should return zero matches for a user who never played`() {
            val target = persistUser(username = "target1")

            perform(get("$BASE_URL/${target.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(0))
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            perform(get("$BASE_URL/${UUID.randomUUID()}"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the user id is not a UUID`() {
            perform(get("$BASE_URL/not-a-uuid"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val target = persistUser(username = "target1")

            perform(get("$BASE_URL/${target.id}"), user = persistUser(username = "regular1"))
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/users/{userId}/suspend")
    inner class SuspendUserTests {

        @Test
        fun `should suspend an active user`() {
            val target = persistUser(username = "target1")

            perform(patch("$BASE_URL/${target.id}/suspend"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))

            assertThat(userRepository.findById(target.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.SUSPENDED)
        }

        @Test
        fun `should revoke every session of the suspended user`() {
            val target = persistUser(username = "target1")
            val first = persistRefreshToken(target)
            val second = persistRefreshToken(target)

            perform(patch("$BASE_URL/${target.id}/suspend"))
                .andExpect(status().isOk)

            assertThat(refreshTokenRepository.findById(first.id!!).orElseThrow().revoked).isTrue()
            assertThat(refreshTokenRepository.findById(second.id!!).orElseThrow().revoked).isTrue()
        }

        @Test
        fun `should not touch the sessions of other users`() {
            val target = persistUser(username = "target1")
            val bystander = persistUser(username = "bystander")
            val token = persistRefreshToken(bystander)

            perform(patch("$BASE_URL/${target.id}/suspend"))
                .andExpect(status().isOk)

            assertThat(refreshTokenRepository.findById(token.id!!).orElseThrow().revoked).isFalse()
        }

        @Test
        fun `should return 400 when an admin tries to suspend their own account`() {
            val self = admin()

            perform(patch("$BASE_URL/${self.id}/suspend"), user = self)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("You cannot suspend your own account"))

            assertThat(userRepository.findById(self.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.ACTIVE)
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["ACTIVE"], mode = EnumSource.Mode.EXCLUDE)
        fun `should return 409 when the account is not currently active`(accountStatus: AccountStatus) {
            val target = persistUser(username = "target1", accountStatus = accountStatus)

            perform(patch("$BASE_URL/${target.id}/suspend"))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.message").value("Only active accounts can be suspended"))

            assertThat(userRepository.findById(target.id!!).orElseThrow().accountStatus).isEqualTo(accountStatus)
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            perform(patch("$BASE_URL/${UUID.randomUUID()}/suspend"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val target = persistUser(username = "target1")

            perform(patch("$BASE_URL/${target.id}/suspend"), user = persistUser(username = "regular1"))
                .andExpect(status().isForbidden)

            assertThat(userRepository.findById(target.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.ACTIVE)
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/users/{userId}/reactivate")
    inner class ReactivateUserTests {

        @Test
        fun `should reactivate a suspended user`() {
            val target = persistUser(username = "target1", accountStatus = AccountStatus.SUSPENDED)

            perform(patch("$BASE_URL/${target.id}/reactivate"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))

            assertThat(userRepository.findById(target.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.ACTIVE)
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["SUSPENDED"], mode = EnumSource.Mode.EXCLUDE)
        fun `should return 409 when the account is not currently suspended`(accountStatus: AccountStatus) {
            val target = persistUser(username = "target1", accountStatus = accountStatus)

            perform(patch("$BASE_URL/${target.id}/reactivate"))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.message").value("Only suspended accounts can be reactivated"))

            assertThat(userRepository.findById(target.id!!).orElseThrow().accountStatus).isEqualTo(accountStatus)
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            perform(patch("$BASE_URL/${UUID.randomUUID()}/reactivate"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val target = persistUser(username = "target1", accountStatus = AccountStatus.SUSPENDED)

            perform(patch("$BASE_URL/${target.id}/reactivate"), user = persistUser(username = "regular1"))
                .andExpect(status().isForbidden)

            assertThat(userRepository.findById(target.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.SUSPENDED)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/users/admin")
    inner class CreateAdminTests {

        @Test
        fun `should create an active admin account`() {
            createAdmin(adminPayload())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.username").value("newadmin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))

            val created = userRepository.findByUsernameIgnoreCase("newadmin").orElseThrow()
            assertThat(created.role).isEqualTo(UserRole.ADMIN)
            assertThat(created.accountStatus).isEqualTo(AccountStatus.ACTIVE)
            assertThat(created.email).isEqualTo("newadmin@example.com")
        }

        @Test
        fun `should store the password hashed`() {
            createAdmin(adminPayload(password = "password123"))
                .andExpect(status().isCreated)

            val created = userRepository.findByUsernameIgnoreCase("newadmin").orElseThrow()
            assertThat(created.passwordHash).isNotEqualTo("password123")
            assertThat(passwordEncoder.matches("password123", created.passwordHash)).isTrue()
        }

        @Test
        fun `should not expose the password in the response`() {
            createAdmin(adminPayload())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
        }

        @Test
        fun `should return 409 when the email is already taken`() {
            persistUser(username = "existing")

            createAdmin(adminPayload(email = "existing@example.com"))
                .andExpect(status().isConflict)

            assertThat(userRepository.findByUsernameIgnoreCase("newadmin")).isEmpty
        }

        @Test
        fun `should return 409 when the email is taken with a different casing`() {
            persistUser(username = "existing")

            createAdmin(adminPayload(email = "EXISTING@Example.com"))
                .andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when the username is already taken`() {
            persistUser(username = "taken")

            createAdmin(adminPayload(username = "taken"))
                .andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when the username is taken with a different casing`() {
            persistUser(username = "taken")

            createAdmin(adminPayload(username = "TAKEN"))
                .andExpect(status().isConflict)
        }

        @Test
        fun `should return 400 when the payload is invalid`() {
            createAdmin(adminPayload(username = "ab", email = "not-an-email", password = "short"))
                .andExpect(status().isBadRequest)

            assertThat(userRepository.findByUsernameIgnoreCase("ab")).isEmpty
        }

        @Test
        fun `should return 400 when the body is malformed`() {
            createAdmin("{ not json")
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            createAdmin(adminPayload(), user = persistUser(username = "regular1"))
                .andExpect(status().isForbidden)

            assertThat(userRepository.findByUsernameIgnoreCase("newadmin")).isEmpty
        }
    }
}