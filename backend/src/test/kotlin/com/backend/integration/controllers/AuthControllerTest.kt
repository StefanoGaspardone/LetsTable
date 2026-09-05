package com.backend.integration.controllers

import com.backend.models.entities.EmailVerification
import com.backend.models.entities.PasswordReset
import com.backend.models.entities.RefreshToken
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.EmailVerificationRepository
import com.backend.repositories.PasswordResetRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UserRepository
import com.backend.services.MailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureMockMvc
class AuthControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var emailVerificationRepository: EmailVerificationRepository

    @Autowired
    private lateinit var passwordResetRepository: PasswordResetRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockitoBean
    private lateinit var mailService: MailService

    private val passwordEncoder = BCryptPasswordEncoder()
    private val rawPassword = "correct-password-123"

    private fun hashValue(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun persistActiveUser(username: String = "stefano", email: String = "stefano@example.com"): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = email,
                passwordHash = passwordEncoder.encode(rawPassword)!!,
                role = UserRole.USER,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    private fun persistInactiveUser(username: String = "pending", email: String = "pending@example.com"): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = email,
                passwordHash = passwordEncoder.encode(rawPassword)!!,
                role = UserRole.USER,
                accountStatus = AccountStatus.INACTIVE,
            )
        )

    private fun persistEmailVerification(user: User, otp: String = "123456", expired: Boolean = false, attempts: Int = 0): EmailVerification =
        emailVerificationRepository.saveAndFlush(
            EmailVerification(
                user = user,
                otpHash = hashValue(otp),
                expiresAt = if (expired) Instant.now().minus(1, ChronoUnit.MINUTES) else Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = attempts,
                lastSentAt = Instant.now().minus(5, ChronoUnit.SECONDS),
            )
        )

    private fun persistPasswordReset(user: User, otp: String = "654321", expired: Boolean = false, attempts: Int = 0): PasswordReset =
        passwordResetRepository.saveAndFlush(
            PasswordReset(
                user = user,
                otpHash = hashValue(otp),
                expiresAt = if (expired) Instant.now().minus(1, ChronoUnit.MINUTES) else Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = attempts,
                lastSentAt = Instant.now().minus(5, ChronoUnit.SECONDS),
            )
        )

    private fun persistRefreshToken(user: User, rawToken: String, revoked: Boolean = false, expired: Boolean = false): RefreshToken =
        refreshTokenRepository.saveAndFlush(
            RefreshToken(
                user = user,
                tokenHash = hashValue(rawToken),
                expiresAt = if (expired) Instant.now().minus(1, ChronoUnit.MINUTES) else Instant.now().plus(7, ChronoUnit.DAYS),
                revoked = revoked,
            )
        )

    @BeforeEach
    fun stubMailService() {
        doNothing().whenever(mailService).sendActivationOtp(any(), any(), any())
        doNothing().whenever(mailService).sendPasswordResetOtp(any(), any(), any())
    }

    @AfterEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        emailVerificationRepository.deleteAll()
        passwordResetRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // signup
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/auth/signup")
    inner class SignupTests {

        @Test
        fun `should create an inactive user, an email verification and return 201`() {
            val payload = """{"username":"newuser","email":"newuser@example.com","password":"password123"}"""

            mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.email").value("newuser@example.com"))

            val savedUser = userRepository.findByEmailIgnoreCase("newuser@example.com").orElseThrow()
            assertThat(savedUser.accountStatus).isEqualTo(AccountStatus.INACTIVE)

            val verification = emailVerificationRepository.findByUserId(savedUser.id!!)
            assertThat(verification).isPresent
        }

        @Test
        fun `should return 409 when email is already taken`() {
            persistActiveUser(email = "taken@example.com")
            val payload = """{"username":"anotheruser","email":"taken@example.com","password":"password123"}"""

            mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when username is already taken`() {
            persistActiveUser(username = "takenname")
            val payload = """{"username":"takenname","email":"unique@example.com","password":"password123"}"""

            mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isConflict)
        }

        @Test
        fun `should return 400 when payload is invalid`() {
            val payload = """{"username":"ab","email":"not-an-email","password":"short"}"""

            mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isBadRequest)
        }
    }

    // ---------------------------------------------------------------------
    // activate
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/auth/activate")
    inner class ActivateTests {

        @Test
        fun `should activate account with valid OTP and return 204`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222")

            val payload = """{"identifier":"pending","otpCode":"111222"}"""

            mockMvc.perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNoContent)

            val updatedUser = userRepository.findById(user.id!!).orElseThrow()
            assertThat(updatedUser.accountStatus).isEqualTo(AccountStatus.ACTIVE)
            assertThat(emailVerificationRepository.findByUserId(user.id!!)).isEmpty()
        }

        @Test
        fun `should return 400 on wrong OTP without persisting the attempt due to transaction rollback`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222")

            val payload = """{"identifier":"pending","otpCode":"999999"}"""

            mockMvc.perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)

            val verification = emailVerificationRepository.findByUserId(user.id!!).orElseThrow()
            assertThat(verification.attempts).isEqualTo(0)
        }

        @Test
        fun `should return 400 when OTP is expired`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222", expired = true)

            val payload = """{"identifier":"pending","otpCode":"111222"}"""

            mockMvc.perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 429 when max attempts exceeded`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222", attempts = 5)

            val payload = """{"identifier":"pending","otpCode":"111222"}"""

            mockMvc.perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isTooManyRequests)
        }

        @Test
        fun `should return 404 when no verification exists for the identifier`() {
            persistInactiveUser()
            val payload = """{"identifier":"pending","otpCode":"111222"}"""

            mockMvc.perform(
                post("/api/v1/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // activate/resend
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/auth/activate/resend")
    inner class ResendActivationOtpTests {

        @Test
        fun `should resend OTP and reset attempts when cooldown has passed`() {
            val user = persistInactiveUser()
            val verification = persistEmailVerification(user, otp = "111222", attempts = 3)
            verification.lastSentAt = Instant.now().minus(2, ChronoUnit.MINUTES)
            emailVerificationRepository.saveAndFlush(verification)

            val payload = """{"identifier":"pending"}"""

            mockMvc.perform(
                post("/api/v1/auth/activate/resend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNoContent)

            val updated = emailVerificationRepository.findByUserId(user.id!!).orElseThrow()
            assertThat(updated.attempts).isEqualTo(0)
            assertThat(updated.otpHash).isNotEqualTo(hashValue("111222"))
        }

        @Test
        fun `should return 429 when cooldown is still active`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222")

            val payload = """{"identifier":"pending"}"""

            mockMvc.perform(
                post("/api/v1/auth/activate/resend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isTooManyRequests)
        }

        @Test
        fun `should return 404 when no verification exists`() {
            persistInactiveUser()
            val payload = """{"identifier":"pending"}"""

            mockMvc.perform(
                post("/api/v1/auth/activate/resend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // login
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    inner class LoginTests {

        @Test
        fun `should login successfully with correct credentials and persist a refresh token`() {
            val user = persistActiveUser()
            val payload = """{"identifier":"stefano","password":"$rawPassword"}"""

            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)
                .andExpect(jsonPath("$.user.username").value("stefano"))

            val tokens = refreshTokenRepository.findAll().filter { it.user.id == user.id }
            assertThat(tokens).hasSize(1)
        }

        @Test
        fun `should login successfully using email as identifier`() {
            persistActiveUser(email = "stefano@example.com")
            val payload = """{"identifier":"stefano@example.com","password":"$rawPassword"}"""

            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isOk)
        }

        @Test
        fun `should return 401 when password is wrong`() {
            persistActiveUser()
            val payload = """{"identifier":"stefano","password":"wrong-password"}"""

            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 401 when identifier does not exist`() {
            val payload = """{"identifier":"nonexistent","password":"whatever123"}"""

            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when account is not activated`() {
            persistInactiveUser()
            val payload = """{"identifier":"pending","password":"$rawPassword"}"""

            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // refresh
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    inner class RefreshTests {

        @Test
        fun `should issue a new token pair and revoke the old refresh token`() {
            val user = persistActiveUser()
            val rawToken = "raw-refresh-token-value"
            val storedToken = persistRefreshToken(user, rawToken)

            val payload = """{"refreshToken":"$rawToken"}"""

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)

            val revoked = refreshTokenRepository.findById(storedToken.id!!).orElseThrow()
            assertThat(revoked.revoked).isTrue()
        }

        @Test
        fun `should return 404 when refresh token does not exist`() {
            val payload = """{"refreshToken":"nonexistent-token"}"""

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 401 when refresh token is already revoked`() {
            val user = persistActiveUser()
            val rawToken = "already-revoked-token"
            persistRefreshToken(user, rawToken, revoked = true)

            val payload = """{"refreshToken":"$rawToken"}"""

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 401 when refresh token is expired`() {
            val user = persistActiveUser()
            val rawToken = "expired-token"
            persistRefreshToken(user, rawToken, expired = true)

            val payload = """{"refreshToken":"$rawToken"}"""

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when account is no longer active`() {
            val user = persistInactiveUser()
            val rawToken = "token-for-inactive-user"
            persistRefreshToken(user, rawToken)

            val payload = """{"refreshToken":"$rawToken"}"""

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // logout
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    inner class LogoutTests {

        @Test
        fun `should revoke the refresh token and return 204`() {
            val user = persistActiveUser()
            val rawToken = "token-to-logout"
            val storedToken = persistRefreshToken(user, rawToken)

            val payload = """{"refreshToken":"$rawToken"}"""

            mockMvc.perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNoContent)

            val revoked = refreshTokenRepository.findById(storedToken.id!!).orElseThrow()
            assertThat(revoked.revoked).isTrue()
        }

        @Test
        fun `should return 204 even when the refresh token does not exist`() {
            val payload = """{"refreshToken":"nonexistent-token"}"""

            mockMvc.perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNoContent)
        }
    }

    // ---------------------------------------------------------------------
    // password/forgot
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/auth/password/forgot")
    inner class ForgotPasswordTests {

        @Test
        fun `should create a password reset entry and return 204 when account exists`() {
            val user = persistActiveUser()
            val payload = """{"identifier":"stefano"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/forgot")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNoContent)

            assertThat(passwordResetRepository.findByUserId(user.id!!)).isPresent
        }

        @Test
        fun `should return 204 without creating a reset entry when account does not exist`() {
            val payload = """{"identifier":"nonexistent"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/forgot")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNoContent)

            assertThat(passwordResetRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 429 when a reset was already requested within the cooldown`() {
            val user = persistActiveUser()
            persistPasswordReset(user)

            val payload = """{"identifier":"stefano"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/forgot")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isTooManyRequests)
        }

        @Test
        fun `should issue a new reset when the previous cooldown has passed`() {
            val user = persistActiveUser()
            val existing = persistPasswordReset(user)
            existing.lastSentAt = Instant.now().minus(2, ChronoUnit.MINUTES)
            passwordResetRepository.saveAndFlush(existing)

            val payload = """{"identifier":"stefano"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/forgot")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNoContent)

            val updated = passwordResetRepository.findByUserId(user.id!!).orElseThrow()
            assertThat(updated.attempts).isEqualTo(0)
        }
    }

    // ---------------------------------------------------------------------
    // password/reset
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/auth/password/reset")
    inner class ResetPasswordTests {

        @Test
        fun `should reset password with valid OTP and return 204`() {
            val user = persistActiveUser()
            persistPasswordReset(user, otp = "555444")

            val payload = """{"identifier":"stefano","otpCode":"555444","newPassword":"newpassword123"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNoContent)

            val updatedUser = userRepository.findById(user.id!!).orElseThrow()
            assertThat(passwordEncoder.matches("newpassword123", updatedUser.passwordHash)).isTrue()
            assertThat(passwordResetRepository.findByUserId(user.id!!)).isEmpty()
        }

        @Test
        fun `should return 400 on wrong OTP without persisting the attempt due to transaction rollback`() {
            val user = persistActiveUser()
            persistPasswordReset(user, otp = "555444")

            val payload = """{"identifier":"stefano","otpCode":"000000","newPassword":"newpassword123"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)

            val reset = passwordResetRepository.findByUserId(user.id!!).orElseThrow()
            assertThat(reset.attempts).isEqualTo(0)
        }

        @Test
        fun `should return 400 when OTP is expired`() {
            val user = persistActiveUser()
            persistPasswordReset(user, otp = "555444", expired = true)

            val payload = """{"identifier":"stefano","otpCode":"555444","newPassword":"newpassword123"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 429 when max attempts exceeded`() {
            val user = persistActiveUser()
            persistPasswordReset(user, otp = "555444", attempts = 5)

            val payload = """{"identifier":"stefano","otpCode":"555444","newPassword":"newpassword123"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isTooManyRequests)
        }

        @Test
        fun `should return 404 when no reset exists for the identifier`() {
            persistActiveUser()
            val payload = """{"identifier":"stefano","otpCode":"555444","newPassword":"newpassword123"}"""

            mockMvc.perform(
                post("/api/v1/auth/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }
    }
}