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
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistRepository
import com.backend.services.MailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
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

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    @Autowired
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean
    private lateinit var mailService: MailService

    private val jsonMapper = JsonMapper.builder().build()

    companion object {
        private const val BASE_URL = "/api/v1/auth"
        private const val RAW_PASSWORD = "correct-password-123"
        private const val MAX_ATTEMPTS_PROBE = 20
    }

    @AfterEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll()
        emailVerificationRepository.deleteAll()
        passwordResetRepository.deleteAll()
        wishlistItemRepository.deleteAll()
        wishlistRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun hashValue(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun persistUser(
        username: String = "stefano",
        email: String = "stefano@example.com",
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
    ): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = email,
                passwordHash = passwordEncoder.encode(RAW_PASSWORD)!!,
                role = UserRole.USER,
                accountStatus = accountStatus,
            )
        )

    private fun persistInactiveUser(): User = persistUser(username = "pending", email = "pending@example.com", accountStatus = AccountStatus.INACTIVE)

    private fun expiry(expired: Boolean): Instant =
        if(expired) Instant.now().minus(1, ChronoUnit.MINUTES) else Instant.now().plus(10, ChronoUnit.MINUTES)

    private fun persistEmailVerification(user: User, otp: String = "123456", expired: Boolean = false, attempts: Int = 0, sentAgo: Long = 5): EmailVerification =
        emailVerificationRepository.saveAndFlush(
            EmailVerification(
                user = user,
                otpHash = hashValue(otp),
                expiresAt = expiry(expired),
                attempts = attempts,
                lastSentAt = Instant.now().minusSeconds(sentAgo),
            )
        )

    private fun persistPasswordReset(user: User, otp: String = "654321", expired: Boolean = false, attempts: Int = 0, sentAgo: Long = 5): PasswordReset =
        passwordResetRepository.saveAndFlush(
            PasswordReset(
                user = user,
                otpHash = hashValue(otp),
                expiresAt = expiry(expired),
                attempts = attempts,
                lastSentAt = Instant.now().minusSeconds(sentAgo),
            )
        )

    private fun persistRefreshToken(user: User, rawToken: String, revoked: Boolean = false, expired: Boolean = false): RefreshToken =
        refreshTokenRepository.saveAndFlush(
            RefreshToken(
                user = user,
                tokenHash = hashValue(rawToken),
                expiresAt = if(expired) Instant.now().minus(1, ChronoUnit.MINUTES) else Instant.now().plus(7, ChronoUnit.DAYS),
                revoked = revoked,
            )
        )

    private fun postJson(path: String, body: String): ResultActions =
        mockMvc.perform(post("$BASE_URL$path").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun signup(username: String = "newuser", email: String = "newuser@example.com", password: String = "password123") =
        postJson("/signup", """{"username":"$username","email":"$email","password":"$password"}""")

    private fun activate(identifier: String, otp: String) =
        postJson("/activate", """{"identifier":"$identifier","otpCode":"$otp"}""")

    private fun login(identifier: String, password: String = RAW_PASSWORD) =
        postJson("/login", """{"identifier":"$identifier","password":"$password"}""")

    private fun refresh(token: String) = postJson("/refresh", """{"refreshToken":"$token"}""")

    private fun resetPassword(identifier: String, otp: String, newPassword: String = "newpassword123") =
        postJson("/password/reset", """{"identifier":"$identifier","otpCode":"$otp","newPassword":"$newPassword"}""")

    private fun tokensOf(result: ResultActions): Pair<String, String> {
        val body = jsonMapper.readTree(result.andReturn().response.contentAsString)
        return body.get("accessToken").asString() to body.get("refreshToken").asString()
    }

    private fun sentActivationOtp(email: String): String {
        val otp = argumentCaptor<String>()
        verify(mailService).sendActivationOtp(eq(email), otp.capture(), anyLong())
        return otp.lastValue
    }

    private fun sentResetOtp(email: String): String {
        val otp = argumentCaptor<String>()
        verify(mailService).sendPasswordResetOtp(eq(email), otp.capture(), anyLong())
        return otp.lastValue
    }

    @Nested
    @DisplayName("POST /api/v1/auth/signup")
    inner class SignupTests {

        @Test
        fun `should create an inactive user with a hashed password and an email verification`() {
            signup()
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.email").value("newuser@example.com"))

            val saved = userRepository.findByEmailIgnoreCase("newuser@example.com").orElseThrow()
            assertThat(saved.accountStatus).isEqualTo(AccountStatus.INACTIVE)
            assertThat(saved.role).isEqualTo(UserRole.USER)
            assertThat(passwordEncoder.matches("password123", saved.passwordHash)).isTrue()
            assertThat(emailVerificationRepository.findByUserId(saved.id!!)).isPresent
        }

        @Test
        fun `should email the verification code and store only its hash`() {
            signup().andExpect(status().isCreated)

            val otp = sentActivationOtp("newuser@example.com")
            val saved = userRepository.findByEmailIgnoreCase("newuser@example.com").orElseThrow()
            val verification = emailVerificationRepository.findByUserId(saved.id!!).orElseThrow()

            assertThat(otp).matches("\\d{6}")
            assertThat(verification.otpHash).isEqualTo(hashValue(otp)).isNotEqualTo(otp)
        }

        @Test
        fun `should let the user sign up, activate with the emailed code and log in`() {
            signup().andExpect(status().isCreated)

            activate("newuser", sentActivationOtp("newuser@example.com")).andExpect(status().isNoContent)

            login("newuser", "password123")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.user.username").value("newuser"))
        }

        @Test
        fun `should return 409 when email is already taken`() {
            persistUser(email = "taken@example.com")

            signup(email = "taken@example.com").andExpect(status().isConflict)

            verify(mailService, never()).sendActivationOtp(anyString(), anyString(), anyLong())
        }

        @Test
        fun `should return 409 when email is taken with a different casing`() {
            persistUser(email = "taken@example.com")

            signup(email = "TAKEN@Example.com").andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when username is already taken`() {
            persistUser(username = "takenname")

            signup(username = "takenname").andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when username is taken with a different casing`() {
            persistUser(username = "takenname")

            signup(username = "TakenName").andExpect(status().isConflict)
        }

        @Test
        fun `should return 400 when payload is invalid`() {
            signup(username = "ab", email = "not-an-email", password = "short").andExpect(status().isBadRequest)

            assertThat(userRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 when the body is malformed`() {
            postJson("/signup", "{ not json").andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/activate")
    inner class ActivateTests {

        @Test
        fun `should activate account with valid OTP and return 204`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222")

            activate("pending", "111222").andExpect(status().isNoContent)

            assertThat(userRepository.findById(user.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.ACTIVE)
            assertThat(emailVerificationRepository.findByUserId(user.id!!)).isEmpty()
        }

        @Test
        fun `should accept the email as identifier`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222")

            activate("pending@example.com", "111222").andExpect(status().isNoContent)
        }

        @Test
        fun `should count a wrong OTP as a failed attempt`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222")

            activate("pending", "999999").andExpect(status().isBadRequest)

            assertThat(emailVerificationRepository.findByUserId(user.id!!).orElseThrow().attempts).isEqualTo(1)
            assertThat(userRepository.findById(user.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.INACTIVE)
        }

        @Test
        fun `should lock the code after too many wrong attempts, even for the right one`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222")

            val lockedAfter = (1..MAX_ATTEMPTS_PROBE).firstOrNull {
                activate("pending", "999999").andReturn().response.status == 429
            }

            assertThat(lockedAfter).isNotNull()
            activate("pending", "111222").andExpect(status().isTooManyRequests)
            assertThat(userRepository.findById(user.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.INACTIVE)
        }

        @Test
        fun `should return 400 when OTP is expired`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222", expired = true)

            activate("pending", "111222").andExpect(status().isBadRequest)

            assertThat(userRepository.findById(user.id!!).orElseThrow().accountStatus).isEqualTo(AccountStatus.INACTIVE)
        }

        @Test
        fun `should return 429 when max attempts exceeded`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222", attempts = 5)

            activate("pending", "111222").andExpect(status().isTooManyRequests)
        }

        @Test
        fun `should return 404 when no verification exists for the identifier`() {
            persistInactiveUser()

            activate("pending", "111222").andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 for an unknown identifier`() {
            activate("ghost", "111222").andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the code is blank`() {
            persistInactiveUser()

            activate("pending", "").andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/activate/resend")
    inner class ResendActivationOtpTests {

        private fun resend(identifier: String = "pending") = postJson("/activate/resend", """{"identifier":"$identifier"}""")

        @Test
        fun `should send a new code and reset the attempts when cooldown has passed`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222", attempts = 3, sentAgo = 120)

            resend().andExpect(status().isNoContent)

            val newOtp = sentActivationOtp("pending@example.com")
            val updated = emailVerificationRepository.findByUserId(user.id!!).orElseThrow()
            assertThat(updated.attempts).isZero()
            assertThat(updated.otpHash).isEqualTo(hashValue(newOtp)).isNotEqualTo(hashValue("111222"))
        }

        @Test
        fun `should invalidate the previous code`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222", sentAgo = 120)

            resend().andExpect(status().isNoContent)

            activate("pending", "111222").andExpect(status().isBadRequest)
            activate("pending", sentActivationOtp("pending@example.com")).andExpect(status().isNoContent)
        }

        @Test
        fun `should return 429 when cooldown is still active`() {
            val user = persistInactiveUser()
            persistEmailVerification(user, otp = "111222")

            resend().andExpect(status().isTooManyRequests)

            verify(mailService, never()).sendActivationOtp(anyString(), anyString(), anyLong())
            assertThat(emailVerificationRepository.findByUserId(user.id!!).orElseThrow().otpHash).isEqualTo(hashValue("111222"))
        }

        @Test
        fun `should return 404 when no verification exists`() {
            persistInactiveUser()

            resend().andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when the account is already active`() {
            persistUser()

            resend("stefano").andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    inner class LoginTests {

        @Test
        fun `should login with correct credentials and persist a hashed refresh token`() {
            val user = persistUser()

            val result = login("stefano")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)
                .andExpect(jsonPath("$.user.username").value("stefano"))

            val (_, refreshToken) = tokensOf(result)
            val stored = refreshTokenRepository.findAll().filter { it.user.id == user.id }
            assertThat(stored).hasSize(1)
            assertThat(stored.single().tokenHash).isEqualTo(hashValue(refreshToken)).isNotEqualTo(refreshToken)
        }

        @Test
        fun `should login using the email as identifier`() {
            persistUser()

            login("stefano@example.com").andExpect(status().isOk)
        }

        @Test
        fun `should ignore the casing of the identifier`() {
            persistUser()

            login("STEFANO").andExpect(status().isOk)
            login("Stefano@Example.com").andExpect(status().isOk)
        }

        @Test
        fun `should create a separate session for every login`() {
            val user = persistUser()

            login("stefano").andExpect(status().isOk)
            login("stefano").andExpect(status().isOk)

            assertThat(refreshTokenRepository.findAll().filter { it.user.id == user.id }).hasSize(2)
        }

        @Test
        fun `should return 401 when password is wrong`() {
            persistUser()

            login("stefano", "wrong-password")
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.message").value("Invalid credentials"))

            assertThat(refreshTokenRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return the same error when the identifier does not exist`() {
            login("nonexistent", "whatever123")
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
        }

        @Test
        fun `should return 401 for a deleted account`() {
            persistUser(accountStatus = AccountStatus.DELETED)

            login("stefano").andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when account is not activated`() {
            persistInactiveUser()

            login("pending").andExpect(status().isForbidden)

            assertThat(refreshTokenRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 403 when account is suspended`() {
            persistUser(accountStatus = AccountStatus.SUSPENDED)

            login("stefano").andExpect(status().isForbidden)
        }

        @Test
        fun `should return 400 when the password is blank`() {
            login("stefano", "").andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    inner class RefreshTests {

        @Test
        fun `should issue a new token pair and revoke the old refresh token`() {
            val user = persistUser()
            val stored = persistRefreshToken(user, "raw-refresh-token-value")

            val result = refresh("raw-refresh-token-value")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)

            val (_, rotated) = tokensOf(result)
            assertThat(rotated).isNotEqualTo("raw-refresh-token-value")
            assertThat(refreshTokenRepository.findById(stored.id!!).orElseThrow().revoked).isTrue()
        }

        @Test
        fun `should accept the rotated token and refuse the old one`() {
            val user = persistUser()
            persistRefreshToken(user, "first-token")

            val (_, rotated) = tokensOf(refresh("first-token").andExpect(status().isOk))

            refresh("first-token").andExpect(status().isUnauthorized)
            refresh(rotated).andExpect(status().isOk)
        }

        @Test
        fun `should return 401 when refresh token does not exist`() {
            refresh("nonexistent-token")
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.message").value("Refresh token not found"))
        }

        @Test
        fun `should return 401 when refresh token is already revoked`() {
            persistRefreshToken(persistUser(), "already-revoked-token", revoked = true)

            refresh("already-revoked-token").andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 401 when refresh token is expired`() {
            persistRefreshToken(persistUser(), "expired-token", expired = true)

            refresh("expired-token").andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when the account is not activated`() {
            persistRefreshToken(persistInactiveUser(), "token-for-inactive-user")

            refresh("token-for-inactive-user").andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 when the account was suspended`() {
            persistRefreshToken(persistUser(accountStatus = AccountStatus.SUSPENDED), "token-for-suspended-user")

            refresh("token-for-suspended-user").andExpect(status().isForbidden)
        }

        @Test
        fun `should return 400 when the token is blank`() {
            refresh("").andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    inner class LogoutTests {

        private fun logout(token: String) = postJson("/logout", """{"refreshToken":"$token"}""")

        @Test
        fun `should revoke the refresh token and return 204`() {
            val stored = persistRefreshToken(persistUser(), "token-to-logout")

            logout("token-to-logout").andExpect(status().isNoContent)

            assertThat(refreshTokenRepository.findById(stored.id!!).orElseThrow().revoked).isTrue()
        }

        @Test
        fun `should make the token unusable for a refresh`() {
            persistRefreshToken(persistUser(), "token-to-logout")

            logout("token-to-logout").andExpect(status().isNoContent)

            refresh("token-to-logout").andExpect(status().isUnauthorized)
        }

        @Test
        fun `should keep the other sessions of the user`() {
            val user = persistUser()
            persistRefreshToken(user, "phone-token")
            val tablet = persistRefreshToken(user, "tablet-token")

            logout("phone-token").andExpect(status().isNoContent)

            assertThat(refreshTokenRepository.findById(tablet.id!!).orElseThrow().revoked).isFalse()
        }

        @Test
        fun `should return 204 even when the refresh token does not exist`() {
            logout("nonexistent-token").andExpect(status().isNoContent)
        }

        @Test
        fun `should return 400 when the token is blank`() {
            logout("").andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/password/forgot")
    inner class ForgotPasswordTests {

        private fun forgot(identifier: String) = postJson("/password/forgot", """{"identifier":"$identifier"}""")

        @Test
        fun `should create a password reset and email the code`() {
            val user = persistUser()

            forgot("stefano").andExpect(status().isNoContent)

            val otp = sentResetOtp("stefano@example.com")
            val reset = passwordResetRepository.findByUserId(user.id!!).orElseThrow()
            assertThat(reset.otpHash).isEqualTo(hashValue(otp))
        }

        @Test
        fun `should accept the email as identifier`() {
            persistUser()

            forgot("stefano@example.com").andExpect(status().isNoContent)

            sentResetOtp("stefano@example.com")
        }

        @Test
        fun `should return 204 without creating a reset or sending an email when the account does not exist`() {
            forgot("nonexistent").andExpect(status().isNoContent)

            assertThat(passwordResetRepository.findAll()).isEmpty()
            verify(mailService, never()).sendPasswordResetOtp(anyString(), anyString(), anyLong())
        }

        @Test
        fun `should not send anything for a deleted account`() {
            persistUser(accountStatus = AccountStatus.DELETED)

            forgot("stefano").andExpect(status().isNoContent)

            verify(mailService, never()).sendPasswordResetOtp(anyString(), anyString(), anyLong())
        }

        @Test
        fun `should return 429 when a reset was already requested within the cooldown`() {
            persistPasswordReset(persistUser())

            forgot("stefano").andExpect(status().isTooManyRequests)

            verify(mailService, never()).sendPasswordResetOtp(anyString(), anyString(), anyLong())
        }

        @Test
        fun `should issue a new code and reset the attempts when the previous cooldown has passed`() {
            val user = persistUser()
            persistPasswordReset(user, otp = "654321", attempts = 3, sentAgo = 120)

            forgot("stefano").andExpect(status().isNoContent)

            val updated = passwordResetRepository.findByUserId(user.id!!).orElseThrow()
            assertThat(updated.attempts).isZero()
            assertThat(updated.otpHash).isEqualTo(hashValue(sentResetOtp("stefano@example.com")))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/password/reset")
    inner class ResetPasswordTests {

        @Test
        fun `should reset the password with a valid OTP`() {
            val user = persistUser()
            persistPasswordReset(user, otp = "555444")

            resetPassword("stefano", "555444").andExpect(status().isNoContent)

            assertThat(passwordEncoder.matches("newpassword123", userRepository.findById(user.id!!).orElseThrow().passwordHash)).isTrue()
            assertThat(passwordResetRepository.findByUserId(user.id!!)).isEmpty()
        }

        @Test
        fun `should let the user log in only with the new password`() {
            persistPasswordReset(persistUser(), otp = "555444")

            resetPassword("stefano", "555444").andExpect(status().isNoContent)

            login("stefano", RAW_PASSWORD).andExpect(status().isUnauthorized)
            login("stefano", "newpassword123").andExpect(status().isOk)
        }

        @Test
        fun `should revoke every active session of the user`() {
            val user = persistUser()
            val phone = persistRefreshToken(user, "phone-token")
            val tablet = persistRefreshToken(user, "tablet-token")
            persistPasswordReset(user, otp = "555444")

            resetPassword("stefano", "555444").andExpect(status().isNoContent)

            assertThat(refreshTokenRepository.findById(phone.id!!).orElseThrow().revoked).isTrue()
            assertThat(refreshTokenRepository.findById(tablet.id!!).orElseThrow().revoked).isTrue()
            refresh("phone-token").andExpect(status().isUnauthorized)
        }

        @Test
        fun `should work with the code received by email`() {
            persistUser()
            postJson("/password/forgot", """{"identifier":"stefano"}""").andExpect(status().isNoContent)

            resetPassword("stefano", sentResetOtp("stefano@example.com")).andExpect(status().isNoContent)
        }

        @Test
        fun `should count a wrong OTP as a failed attempt and keep the old password`() {
            val user = persistUser()
            persistPasswordReset(user, otp = "555444")

            resetPassword("stefano", "000000").andExpect(status().isBadRequest)

            assertThat(passwordResetRepository.findByUserId(user.id!!).orElseThrow().attempts).isEqualTo(1)
            assertThat(passwordEncoder.matches(RAW_PASSWORD, userRepository.findById(user.id!!).orElseThrow().passwordHash)).isTrue()
        }

        @Test
        fun `should lock the code after too many wrong attempts, even for the right one`() {
            val user = persistUser()
            persistPasswordReset(user, otp = "555444")

            val lockedAfter = (1..MAX_ATTEMPTS_PROBE).firstOrNull {
                resetPassword("stefano", "000000").andReturn().response.status == 429
            }

            assertThat(lockedAfter).isNotNull()
            resetPassword("stefano", "555444").andExpect(status().isTooManyRequests)
            assertThat(passwordEncoder.matches(RAW_PASSWORD, userRepository.findById(user.id!!).orElseThrow().passwordHash)).isTrue()
        }

        @Test
        fun `should return 400 when OTP is expired`() {
            persistPasswordReset(persistUser(), otp = "555444", expired = true)

            resetPassword("stefano", "555444").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 429 when max attempts exceeded`() {
            persistPasswordReset(persistUser(), otp = "555444", attempts = 5)

            resetPassword("stefano", "555444").andExpect(status().isTooManyRequests)
        }

        @Test
        fun `should return 404 when no reset exists for the identifier`() {
            persistUser()

            resetPassword("stefano", "555444").andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the new password is too short`() {
            val user = persistUser()
            persistPasswordReset(user, otp = "555444")

            resetPassword("stefano", "555444", newPassword = "short").andExpect(status().isBadRequest)

            assertThat(passwordResetRepository.findByUserId(user.id!!)).isPresent
        }
    }
}