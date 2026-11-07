package com.backend.integration.controllers

import com.backend.clients.BggClient
import com.backend.models.dtos.*
import com.backend.models.entities.EmailVerification
import com.backend.models.entities.Game
import com.backend.models.entities.RefreshToken
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistActivity
import com.backend.models.entities.WishlistMember
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.NotificationType
import com.backend.models.enums.UserRole
import com.backend.models.enums.WishlistActivityAction
import com.backend.repositories.AppReleaseRepository
import com.backend.repositories.EmailVerificationRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistActivityRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistMemberRepository
import com.backend.repositories.WishlistRepository
import com.backend.services.JwtService
import com.backend.services.PushNotificationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@AutoConfigureMockMvc
@TestPropertySource(properties = ["job.token=${InternalJobControllerTest.JOB_TOKEN}"])
class InternalJobControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var emailVerificationRepository: EmailVerificationRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var appReleaseRepository: AppReleaseRepository

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    @Autowired
    private lateinit var wishlistMemberRepository: WishlistMemberRepository

    @Autowired
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @Autowired
    private lateinit var wishlistActivityRepository: WishlistActivityRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    private lateinit var bggClient: BggClient

    @MockitoBean
    private lateinit var pushNotificationService: PushNotificationService

    companion object {
        const val JOB_TOKEN = "test-job-token-0123456789"
        private const val BASE_URL = "/api/v1/internal/jobs"
        private const val TOKEN_HEADER = "X-Scheduler-Token"
    }

    @BeforeEach
    fun setUp() {
        appReleaseRepository.deleteAll()
    }

    @AfterEach
    fun cleanUp() {
        wishlistActivityRepository.deleteAll()
        wishlistItemRepository.deleteAll()
        wishlistMemberRepository.deleteAll()
        wishlistRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        emailVerificationRepository.deleteAll()
        appReleaseRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(username: String = "user-${UUID.randomUUID()}", accountStatus: AccountStatus = AccountStatus.ACTIVE): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = UserRole.USER,
                accountStatus = accountStatus,
            )
        )

    private fun createdDaysAgo(user: User, days: Long) {
        jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS)), user.id)
    }

    private fun job(path: String, token: String? = JOB_TOKEN, body: String? = null): ResultActions =
        mockMvc.perform(post("$BASE_URL/$path").apply {
            token?.let { header(TOKEN_HEADER, it) }
            body?.let { contentType(MediaType.APPLICATION_JSON).content(it) }
        })

    private fun hotItem(id: Long, rank: Int, name: String) = BggHotItemXml(id = id, rank = rank, name = BggValueXml(name))

    private fun thingItem(id: Long, name: String) = BggThingItemXml(
        id = id,
        thumbnail = "https://example.com/thumb.jpg",
        image = "https://example.com/image.jpg",
        names = listOf(BggThingNameXml(type = "primary", value = name)),
        description = "A description",
        yearPublished = BggValueXml(value = "2024"),
        minPlayers = BggValueXml(value = "1"),
        maxPlayers = BggValueXml(value = "4"),
        playingTime = BggValueXml(value = "90"),
        type = "boardgame",
    )

    @Nested
    @DisplayName("scheduler token")
    inner class TokenTests {

        @ParameterizedTest
        @ValueSource(strings = ["refresh-hot-games", "user-cleanup", "wishlist-notifications"])
        fun `should return 401 without the token`(path: String) {
            job(path, token = null)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.message").value("Invalid scheduler token"))
        }

        @ParameterizedTest
        @ValueSource(strings = ["wrong-token", "test-job-token-012345678", "test-job-token-01234567890", "TEST-JOB-TOKEN-0123456789", " ", ""])
        fun `should return 401 with a wrong token`(token: String) {
            job("user-cleanup", token = token).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should not accept a user access token instead of the scheduler token`() {
            val admin = userRepository.saveAndFlush(User(username = "admin", email = "admin@example.com", passwordHash = "hash", role = UserRole.ADMIN, accountStatus = AccountStatus.ACTIVE))

            mockMvc.perform(post("$BASE_URL/user-cleanup").header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtService.generateAccessToken(admin.id!!, admin.role.name)}"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should not run the job when the token is wrong`() {
            val stale = persistUser(accountStatus = AccountStatus.INACTIVE)
            createdDaysAgo(stale, 30)

            job("user-cleanup", token = "wrong-token").andExpect(status().isUnauthorized)
            job("refresh-hot-games", token = "wrong-token").andExpect(status().isUnauthorized)

            assertThat(userRepository.findById(stale.id!!)).isPresent
            verify(bggClient, never()).getHotGames()
        }
    }

    @Nested
    @DisplayName("POST /api/v1/internal/jobs/refresh-hot-games")
    inner class RefreshHotGamesTests {

        @Test
        fun `should refresh the hot games and return 200`() {
            given(bggClient.getHotGames()).willReturn(BggHotResponseXml(items = listOf(hotItem(10L, 1, "Hot Game"), hotItem(11L, 2, "Warm Game"))))
            given(bggClient.getGameDetailsBatch(listOf(10L, 11L))).willReturn(BggThingResponseXml(items = listOf(thingItem(10L, "Hot Game"), thingItem(11L, "Warm Game"))))

            job("refresh-hot-games")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.job").value("refresh-hot-games"))

            assertThat(gameRepository.findByBggId(10L).orElseThrow().rank).isEqualTo(1)
            assertThat(gameRepository.findByBggId(11L).orElseThrow().rank).isEqualTo(2)
        }

        @Test
        fun `should remove the rank of games no longer hot`() {
            gameRepository.saveAndFlush(Game(bggId = 20L, name = "Old Hot Game", lastSyncedAt = Instant.now()).apply { rank = 1 })
            given(bggClient.getHotGames()).willReturn(BggHotResponseXml(items = listOf(hotItem(21L, 1, "New Hot Game"))))
            given(bggClient.getGameDetailsBatch(listOf(21L))).willReturn(BggThingResponseXml(items = listOf(thingItem(21L, "New Hot Game"))))

            job("refresh-hot-games").andExpect(status().isOk)

            assertThat(gameRepository.findByBggId(20L).orElseThrow().rank).isNull()
            assertThat(gameRepository.findByBggId(21L).orElseThrow().rank).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/internal/jobs/user-cleanup")
    inner class UserCleanupTests {

        @Test
        fun `should delete the accounts never activated after seven days and return how many`() {
            val stale = persistUser(accountStatus = AccountStatus.INACTIVE)
            createdDaysAgo(stale, 8)

            job("user-cleanup")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.job").value("user-cleanup"))
                .andExpect(jsonPath("$.affected").value(1))

            assertThat(userRepository.findById(stale.id!!)).isEmpty
        }

        @Test
        fun `should delete a stale account together with its pending email verification`() {
            val stale = persistUser(accountStatus = AccountStatus.INACTIVE)
            emailVerificationRepository.saveAndFlush(
                EmailVerification(user = stale, otpHash = "hash", expiresAt = Instant.now().minus(7, ChronoUnit.DAYS), attempts = 0, lastSentAt = Instant.now().minus(8, ChronoUnit.DAYS))
            )
            createdDaysAgo(stale, 8)

            job("user-cleanup")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.affected").value(1))

            assertThat(userRepository.findById(stale.id!!)).isEmpty
            assertThat(emailVerificationRepository.findByUserId(stale.id!!)).isEmpty
        }

        @Test
        fun `should keep recent inactive accounts and every other account`() {
            val recent = persistUser(accountStatus = AccountStatus.INACTIVE)
            createdDaysAgo(recent, 3)
            val oldActive = persistUser(accountStatus = AccountStatus.ACTIVE)
            createdDaysAgo(oldActive, 100)
            val oldSuspended = persistUser(accountStatus = AccountStatus.SUSPENDED)
            createdDaysAgo(oldSuspended, 100)
            val oldDeleted = persistUser(accountStatus = AccountStatus.DELETED)
            createdDaysAgo(oldDeleted, 100)

            job("user-cleanup")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.affected").value(0))

            assertThat(userRepository.findAllById(listOf(recent.id!!, oldActive.id!!, oldSuspended.id!!, oldDeleted.id!!))).hasSize(4)
        }

        @Test
        fun `should purge revoked and expired refresh tokens but keep the valid ones`() {
            val user = persistUser()
            val valid = refreshTokenRepository.saveAndFlush(RefreshToken(user = user, tokenHash = "valid", expiresAt = Instant.now().plus(7, ChronoUnit.DAYS), revoked = false))
            refreshTokenRepository.saveAndFlush(RefreshToken(user = user, tokenHash = "revoked", expiresAt = Instant.now().plus(7, ChronoUnit.DAYS), revoked = true))
            refreshTokenRepository.saveAndFlush(RefreshToken(user = user, tokenHash = "expired", expiresAt = Instant.now().minus(1, ChronoUnit.DAYS), revoked = false))

            job("user-cleanup").andExpect(status().isOk)

            assertThat(refreshTokenRepository.findAll().map { it.id }).containsExactly(valid.id)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/internal/jobs/wishlist-notifications")
    inner class WishlistNotificationsTests {

        private fun persistActivity(wishlist: Wishlist, actor: User, game: Game, minutesAgo: Long) {
            val activity = wishlistActivityRepository.saveAndFlush(WishlistActivity(wishlist = wishlist, actor = actor, game = game, action = WishlistActivityAction.ADDED))
            jdbcTemplate.update("UPDATE wishlist_activities SET created_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES)), activity.id)
        }

        @Test
        fun `should return zero when nothing is pending`() {
            job("wishlist-notifications")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.job").value("wishlist-notifications"))
                .andExpect(jsonPath("$.affected").value(0))

            verify(pushNotificationService, never()).sendToUsers(any(), eq(NotificationType.WISHLIST_UPDATED), anyString(), anyString(), anyMap())
        }

        @Test
        fun `should notify the members of a shared wishlist once the author is idle`() {
            val owner = persistUser(username = "owner")
            val member = persistUser(username = "member")
            val wishlist = wishlistRepository.saveAndFlush(Wishlist(name = "Natale", owner = owner, isShared = true))
            wishlistMemberRepository.saveAndFlush(WishlistMember(wishlist = wishlist, user = member))
            val game = gameRepository.saveAndFlush(Game(bggId = 30L, name = "Catan", lastSyncedAt = Instant.now()))
            persistActivity(wishlist, owner, game, minutesAgo = 5)

            job("wishlist-notifications")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.affected").value(1))

            verify(pushNotificationService).sendToUsers(argThat { toSet() == setOf(member.id!!) }, eq(NotificationType.WISHLIST_UPDATED), eq("Natale"), eq("owner ha aggiunto Catan"), anyMap())

            job("wishlist-notifications")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.affected").value(0))
        }

        @Test
        fun `should wait while the author is still editing`() {
            val owner = persistUser(username = "owner")
            val wishlist = wishlistRepository.saveAndFlush(Wishlist(name = "Natale", owner = owner, isShared = true))
            wishlistMemberRepository.saveAndFlush(WishlistMember(wishlist = wishlist, user = persistUser(username = "member")))
            persistActivity(wishlist, owner, gameRepository.saveAndFlush(Game(bggId = 31L, name = "Azul", lastSyncedAt = Instant.now())), minutesAgo = 0)

            job("wishlist-notifications")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.affected").value(0))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/internal/jobs/app-release")
    inner class AppReleaseTests {

        private val body = """{"version":"2.4.0","minSupportedVersion":"2.3.0","downloadUrl":null,"releaseNotes":"## New Features"}"""

        @Test
        fun `should publish the release and return 201`() {
            job("app-release", body = body)
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.latestVersion").value("2.4.0"))
                .andExpect(jsonPath("$.minSupportedVersion").value("2.3.0"))

            assertThat(appReleaseRepository.findAll().map { it.version }).containsExactly("2.4.0")
        }

        @Test
        fun `should return 401 and publish nothing with a wrong token`() {
            job("app-release", token = "wrong-token", body = body).andExpect(status().isUnauthorized)

            assertThat(appReleaseRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 409 when the GitHub workflow runs twice for the same release`() {
            job("app-release", body = body).andExpect(status().isCreated)

            job("app-release", body = body).andExpect(status().isConflict)

            assertThat(appReleaseRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should return 400 when the minimum is greater than the version`() {
            job("app-release", body = """{"version":"2.4.0","minSupportedVersion":"2.5.0"}""")
                .andExpect(status().isBadRequest)

            assertThat(appReleaseRepository.findAll()).isEmpty()
        }
    }
}