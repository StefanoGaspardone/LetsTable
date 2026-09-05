package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.*
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
import com.backend.services.AuthService
import com.backend.services.JwtService
import com.backend.services.MailService
import com.backend.services.WishlistService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class AuthServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK
    private lateinit var emailVerificationRepository: EmailVerificationRepository

    @MockK
    private lateinit var passwordResetRepository: PasswordResetRepository

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var jwtService: JwtService

    @MockK
    private lateinit var mailService: MailService

    @MockK
    private lateinit var wishlistService: WishlistService

    @InjectMockKs
    private lateinit var authService: AuthService

    private val refreshTokenTtlDays: Long = 30
    private val otpTtlMinutes: Long = 10
    private val maxAttempts: Int = 3
    private val resendCooldownSeconds: Long = 60

    @BeforeEach
    fun setUp() {
        authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            emailVerificationRepository = emailVerificationRepository,
            passwordResetRepository = passwordResetRepository,
            passwordEncoder = passwordEncoder,
            jwtService = jwtService,
            mailService = mailService,
            wishlistService = wishlistService,
            refreshTokenTtlDays = refreshTokenTtlDays,
            otpTtlMinutes = otpTtlMinutes,
            maxAttempts = maxAttempts,
            resendCooldownSeconds = resendCooldownSeconds
        )
    }

    private fun sha256(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun createTestUser(
        id: UUID = UUID.randomUUID(),
        username: String = "testuser",
        email: String = "test@example.com",
        passwordHash: String = "hashed_password",
        status: AccountStatus = AccountStatus.ACTIVE,
        role: UserRole = UserRole.USER
    ) = User(
        id = id,
        username = username,
        email = email,
        passwordHash = passwordHash,
        accountStatus = status,
        role = role
    )

    // =========================================================================
    // SIGNUP TESTS
    // =========================================================================
    @Nested
    @DisplayName("signup")
    inner class SignupTests {

        @Test
        fun `should successfully signup user, create wishlist, save verification and send mail`() {
            // Arrange
            val request = RegisterRequest(
                username = " TestUser ",
                email = " TEST@example.com ",
                password = "password123"
            )
            val userId = UUID.randomUUID()
            val savedUser = createTestUser(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                status = AccountStatus.INACTIVE
            )
            val mockVerification = EmailVerification(
                user = savedUser,
                otpHash = "dummy_hash",
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 0,
                lastSentAt = Instant.now()
            )

            val userSlot = slot<User>()

            every { userRepository.existsByEmailIgnoreCase(any()) } returns false
            every { userRepository.existsByUsernameIgnoreCase(any()) } returns false
            every { passwordEncoder.encode(any()) } returns "hashed_password123"
            every { userRepository.saveAndFlush(any()) } returns savedUser
            every { wishlistService.createDefaultWishlistForUser(any()) } just Runs
            every { emailVerificationRepository.saveAndFlush(any()) } returns mockVerification
            every { mailService.sendActivationOtp(any(), any(), any()) } just Runs

            // Act
            val response = authService.signup(request)

            // Assert
            assertThat(response).isNotNull
            assertThat(response.email.trim()).isEqualToIgnoringCase("test@example.com")

            // Verify
            verify(exactly = 1) { userRepository.saveAndFlush(any()) }
            verify(exactly = 1) { wishlistService.createDefaultWishlistForUser(capture(userSlot)) }
            verify(exactly = 1) { emailVerificationRepository.saveAndFlush(any()) }
            verify(exactly = 1) { mailService.sendActivationOtp(any(), any(), any()) }

            // Assert sull'utente passato a wishlistService
            assertThat(userSlot.captured.email).isEqualTo("test@example.com")
            assertThat(userSlot.captured.username).isEqualTo("testuser")
        }

        @Test
        fun `should throw EmailAlreadyTakenException when email exists`() {
            val request = RegisterRequest(username = "user", email = "existing@example.com", password = "pass")
            every { userRepository.existsByEmailIgnoreCase("existing@example.com") } returns true

            assertThatThrownBy { authService.signup(request) }
                .isInstanceOf(EmailAlreadyTakenException::class.java)

            verify(exactly = 0) { userRepository.saveAndFlush(any()) }
        }

        @Test
        fun `should throw UsernameAlreadyTakenException when username exists`() {
            val request = RegisterRequest(username = "existingUser", email = "new@example.com", password = "pass")
            every { userRepository.existsByEmailIgnoreCase("new@example.com") } returns false
            every { userRepository.existsByUsernameIgnoreCase("existinguser") } returns true

            assertThatThrownBy { authService.signup(request) }
                .isInstanceOf(UsernameAlreadyTakenException::class.java)

            verify(exactly = 0) { userRepository.saveAndFlush(any()) }
        }
    }

    // =========================================================================
    // ACTIVATE ACCOUNT TESTS
    // =========================================================================
    @Nested
    @DisplayName("activateAccount")
    inner class ActivateAccountTests {

        @Test
        fun `should activate account successfully when OTP is valid`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId, email = "user@example.com", status = AccountStatus.INACTIVE)
            val rawOtp = "123456"
            val verification = EmailVerification(
                user = user,
                otpHash = sha256(rawOtp),
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 0,
                lastSentAt = Instant.now()
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { emailVerificationRepository.findByUserId(userId) } returns Optional.of(verification)
            every { userRepository.save(user) } returns user
            every { emailVerificationRepository.delete(verification) } just Runs

            authService.activateAccount(ActivateAccountRequest(identifier = "user@example.com", otpCode = rawOtp))

            assertThat(user.accountStatus).isEqualTo(AccountStatus.ACTIVE)
            verify {
                userRepository.save(user)
                emailVerificationRepository.delete(verification)
            }
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when identifier does not match any user`() {
            every { userRepository.findByEmailIgnoreCase("nonexistent@example.com") } returns Optional.empty()

            assertThatThrownBy {
                authService.activateAccount(ActivateAccountRequest(identifier = "nonexistent@example.com", otpCode = "123456"))
            }.isInstanceOf(UserNotFoundByIdentifierException::class.java)
        }

        @Test
        fun `should throw EmailVerificationNotFoundException when no pending verification exists`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { emailVerificationRepository.findByUserId(userId) } returns Optional.empty()

            assertThatThrownBy {
                authService.activateAccount(ActivateAccountRequest(identifier = "user@example.com", otpCode = "123456"))
            }.isInstanceOf(EmailVerificationNotFoundException::class.java)
        }

        @Test
        fun `should throw OtpExpiredException when OTP has expired`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            val verification = EmailVerification(
                user = user,
                otpHash = sha256("123456"),
                expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES),
                attempts = 0,
                lastSentAt = Instant.now().minus(11, ChronoUnit.MINUTES)
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { emailVerificationRepository.findByUserId(userId) } returns Optional.of(verification)

            assertThatThrownBy {
                authService.activateAccount(ActivateAccountRequest(identifier = "user@example.com", otpCode = "123456"))
            }.isInstanceOf(OtpExpiredException::class.java)
        }

        @Test
        fun `should throw OtpMaxAttemptsExceededException when attempts limit reached`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            val verification = EmailVerification(
                user = user,
                otpHash = sha256("123456"),
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 3,
                lastSentAt = Instant.now()
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { emailVerificationRepository.findByUserId(userId) } returns Optional.of(verification)

            assertThatThrownBy {
                authService.activateAccount(ActivateAccountRequest(identifier = "user@example.com", otpCode = "123456"))
            }.isInstanceOf(OtpMaxAttemptsExceededException::class.java)
        }

        @Test
        fun `should increment attempts, save and throw InvalidOtpException when OTP is incorrect`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            val verification = EmailVerification(
                user = user,
                otpHash = sha256("123456"),
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 1,
                lastSentAt = Instant.now()
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { emailVerificationRepository.findByUserId(userId) } returns Optional.of(verification)
            every { emailVerificationRepository.save(verification) } returns verification

            assertThatThrownBy {
                authService.activateAccount(ActivateAccountRequest(identifier = "user@example.com", otpCode = "999999"))
            }.isInstanceOf(InvalidOtpException::class.java)

            assertThat(verification.attempts).isEqualTo(2)
            verify { emailVerificationRepository.save(verification) }
        }
    }

    // =========================================================================
    // RESEND ACTIVATION OTP TESTS
    // =========================================================================
    @Nested
    @DisplayName("resendActivationOtp")
    inner class ResendActivationOtpTests {

        @Test
        fun `should resend activation OTP successfully when cooldown has passed`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId, email = "user@example.com")
            val verification = EmailVerification(
                user = user,
                otpHash = "old_hash",
                expiresAt = Instant.now(),
                attempts = 2,
                lastSentAt = Instant.now().minus(65, ChronoUnit.SECONDS)
            )

            every { userRepository.findByUsernameIgnoreCase("user") } returns Optional.of(user)
            every { emailVerificationRepository.findByUserId(userId) } returns Optional.of(verification)
            every { emailVerificationRepository.saveAndFlush(verification) } returns verification
            every { mailService.sendActivationOtp("user@example.com", any(), otpTtlMinutes) } just Runs

            authService.resendActivationOtp(ResendOtpRequest(identifier = "user"))

            assertThat(verification.attempts).isEqualTo(0)
            verify {
                emailVerificationRepository.saveAndFlush(verification)
                mailService.sendActivationOtp("user@example.com", any(), otpTtlMinutes)
            }
        }

        @Test
        fun `should throw OtpResendCooldownException when cooldown is active`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            val verification = EmailVerification(
                user = user,
                otpHash = "hash",
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 0,
                lastSentAt = Instant.now().minus(20, ChronoUnit.SECONDS)
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { emailVerificationRepository.findByUserId(userId) } returns Optional.of(verification)

            assertThatThrownBy {
                authService.resendActivationOtp(ResendOtpRequest(identifier = "user@example.com"))
            }.isInstanceOf(OtpResendCooldownException::class.java)
        }
    }

    // =========================================================================
    // LOGIN TESTS
    // =========================================================================
    @Nested
    @DisplayName("login")
    inner class LoginTests {

        @Test
        fun `should login successfully by email`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId, email = "user@example.com", passwordHash = "encoded_pass")
            val request = LoginRequest(identifier = "USER@example.com", password = "password")

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordEncoder.matches("password", "encoded_pass") } returns true
            every { jwtService.generateAccessToken(userId, "USER") } returns "mock_access_token"
            every { refreshTokenRepository.save(any()) } returns mockk()

            val response = authService.login(request)

            assertThat(response.accessToken).isEqualTo("mock_access_token")
            assertThat(response.refreshToken).isNotEmpty()
            assertThat(response.user.id).isEqualTo(userId)
        }

        @Test
        fun `should login successfully by username`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId, username = "myusername", passwordHash = "encoded_pass")
            val request = LoginRequest(identifier = "myusername", password = "password")

            every { userRepository.findByUsernameIgnoreCase("myusername") } returns Optional.of(user)
            every { passwordEncoder.matches("password", "encoded_pass") } returns true
            every { jwtService.generateAccessToken(userId, "USER") } returns "mock_access_token"
            every { refreshTokenRepository.save(any()) } returns mockk()

            val response = authService.login(request)

            assertThat(response.accessToken).isEqualTo("mock_access_token")
        }

        @Test
        fun `should run dummy password match to mitigate timing attacks and throw InvalidCredentialsException when user not found`() {
            val request = LoginRequest(identifier = "unknown@example.com", password = "password")

            every { userRepository.findByEmailIgnoreCase("unknown@example.com") } returns Optional.empty()
            every { passwordEncoder.matches("password", "\$2a\$10\$7EqJtq98hPqEX7fNZaFWoOhi5uac.Hs2HL8YXtxjKmlXXNzDmSw6C") } returns false

            assertThatThrownBy { authService.login(request) }
                .isInstanceOf(InvalidCredentialsException::class.java)

            verify { passwordEncoder.matches("password", "\$2a\$10\$7EqJtq98hPqEX7fNZaFWoOhi5uac.Hs2HL8YXtxjKmlXXNzDmSw6C") }
        }

        @Test
        fun `should throw InvalidCredentialsException when password does not match`() {
            val user = createTestUser(email = "user@example.com", passwordHash = "encoded_pass")
            val request = LoginRequest(identifier = "user@example.com", password = "wrong_password")

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordEncoder.matches("wrong_password", "encoded_pass") } returns false

            assertThatThrownBy { authService.login(request) }
                .isInstanceOf(InvalidCredentialsException::class.java)
        }

        @Test
        fun `should throw AccountNotActivatedException when user status is INACTIVE`() {
            val user = createTestUser(email = "user@example.com", status = AccountStatus.INACTIVE)
            val request = LoginRequest(identifier = "user@example.com", password = "password")

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordEncoder.matches("password", user.passwordHash) } returns true

            assertThatThrownBy { authService.login(request) }
                .isInstanceOf(AccountNotActivatedException::class.java)
        }
    }

    // =========================================================================
    // REFRESH TESTS
    // =========================================================================
    @Nested
    @DisplayName("refresh")
    inner class RefreshTests {

        @Test
        fun `should refresh tokens successfully and revoke old refresh token`() {
            val rawRefreshToken = "valid_raw_token"
            val tokenHash = sha256(rawRefreshToken)
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId, status = AccountStatus.ACTIVE)

            val storedToken = RefreshToken(
                id = UUID.randomUUID(),
                user = user,
                tokenHash = tokenHash,
                expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
                revoked = false
            )

            every { refreshTokenRepository.findByTokenHash(tokenHash) } returns Optional.of(storedToken)
            every { refreshTokenRepository.save(storedToken) } returns storedToken
            every { jwtService.generateAccessToken(userId, "USER") } returns "new_access_token"
            every { refreshTokenRepository.save(match { it != storedToken }) } returns mockk()

            val response = authService.refresh(rawRefreshToken)

            assertThat(response.accessToken).isEqualTo("new_access_token")
            assertThat(response.refreshToken).isNotNull()
            assertThat(storedToken.revoked).isTrue()
        }

        @Test
        fun `should throw RefreshTokenNotFoundException when token hash is not in db`() {
            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.empty()

            assertThatThrownBy { authService.refresh("unknown_token") }
                .isInstanceOf(RefreshTokenNotFoundException::class.java)
        }

        @Test
        fun `should throw RefreshTokenExpiredOrRevokedException if token is revoked`() {
            val user = createTestUser()
            val storedToken = RefreshToken(
                user = user,
                tokenHash = "hash",
                expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
                revoked = true
            )

            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.of(storedToken)

            assertThatThrownBy { authService.refresh("token") }
                .isInstanceOf(RefreshTokenExpiredOrRevokedException::class.java)
        }

        @Test
        fun `should throw RefreshTokenExpiredOrRevokedException if token is expired`() {
            val user = createTestUser()
            val storedToken = RefreshToken(
                user = user,
                tokenHash = "hash",
                expiresAt = Instant.now().minus(1, ChronoUnit.SECONDS),
                revoked = false
            )

            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.of(storedToken)

            assertThatThrownBy { authService.refresh("token") }
                .isInstanceOf(RefreshTokenExpiredOrRevokedException::class.java)
        }

        @Test
        fun `should throw AccountNotActivatedException if user is not ACTIVE`() {
            val user = createTestUser(status = AccountStatus.INACTIVE)
            val storedToken = RefreshToken(
                user = user,
                tokenHash = "hash",
                expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
                revoked = false
            )

            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.of(storedToken)

            assertThatThrownBy { authService.refresh("token") }
                .isInstanceOf(AccountNotActivatedException::class.java)
        }
    }

    // =========================================================================
    // LOGOUT TESTS
    // =========================================================================
    @Nested
    @DisplayName("logout")
    inner class LogoutTests {

        @Test
        fun `should revoke refresh token if found`() {
            val rawToken = "raw_token"
            val hash = sha256(rawToken)
            val storedToken = RefreshToken(user = createTestUser(), tokenHash = hash, expiresAt = Instant.now(), revoked = false)

            every { refreshTokenRepository.findByTokenHash(hash) } returns Optional.of(storedToken)
            every { refreshTokenRepository.save(storedToken) } returns storedToken

            authService.logout(rawToken)

            assertThat(storedToken.revoked).isTrue()
            verify { refreshTokenRepository.save(storedToken) }
        }

        @Test
        fun `should do nothing gracefully if refresh token is not found`() {
            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.empty()

            authService.logout("nonexistent_token")

            verify(exactly = 0) { refreshTokenRepository.save(any()) }
        }
    }

    // =========================================================================
    // FORGOT PASSWORD TESTS
    // =========================================================================
    @Nested
    @DisplayName("forgotPassword")
    inner class ForgotPasswordTests {

        @Test
        fun `should return silently when user does not exist to prevent user enumeration`() {
            every { userRepository.findByEmailIgnoreCase("ghost@example.com") } returns Optional.empty()

            authService.forgotPassword(ForgotPasswordRequest(identifier = "ghost@example.com"))

            verify(exactly = 0) { passwordResetRepository.saveAndFlush(any()) }
            verify(exactly = 0) { mailService.sendPasswordResetOtp(any(), any(), any()) }
        }

        @Test
        fun `should create new PasswordReset entity when no previous request exists`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId, email = "user@example.com")

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordResetRepository.findByUserId(userId) } returns Optional.empty()
            every { passwordResetRepository.saveAndFlush(any()) } returns mockk()
            every { mailService.sendPasswordResetOtp("user@example.com", any(), otpTtlMinutes) } just Runs

            authService.forgotPassword(ForgotPasswordRequest(identifier = "user@example.com"))

            verify {
                passwordResetRepository.saveAndFlush(any())
                mailService.sendPasswordResetOtp("user@example.com", any(), otpTtlMinutes)
            }
        }

        @Test
        fun `should update existing PasswordReset entity when cooldown has elapsed`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId, email = "user@example.com")
            val existingReset = PasswordReset(
                user = user,
                otpHash = "old_hash",
                expiresAt = Instant.now(),
                attempts = 2,
                lastSentAt = Instant.now().minus(70, ChronoUnit.SECONDS)
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordResetRepository.findByUserId(userId) } returns Optional.of(existingReset)
            every { passwordResetRepository.saveAndFlush(existingReset) } returns existingReset
            every { mailService.sendPasswordResetOtp("user@example.com", any(), otpTtlMinutes) } just Runs

            authService.forgotPassword(ForgotPasswordRequest(identifier = "user@example.com"))

            assertThat(existingReset.attempts).isEqualTo(0)
            verify {
                passwordResetRepository.saveAndFlush(existingReset)
                mailService.sendPasswordResetOtp("user@example.com", any(), otpTtlMinutes)
            }
        }

        @Test
        fun `should throw OtpResendCooldownException when previous reset request is too recent`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            val existingReset = PasswordReset(
                user = user,
                otpHash = "hash",
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 0,
                lastSentAt = Instant.now().minus(10, ChronoUnit.SECONDS)
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordResetRepository.findByUserId(userId) } returns Optional.of(existingReset)

            assertThatThrownBy {
                authService.forgotPassword(ForgotPasswordRequest(identifier = "user@example.com"))
            }.isInstanceOf(OtpResendCooldownException::class.java)
        }
    }

    // =========================================================================
    // RESET PASSWORD TESTS
    // =========================================================================
    @Nested
    @DisplayName("resetPassword")
    inner class ResetPasswordTests {

        @Test
        fun `should reset password successfully when OTP is valid`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId, passwordHash = "old_hash")
            val rawOtp = "654321"
            val reset = PasswordReset(
                user = user,
                otpHash = sha256(rawOtp),
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 0,
                lastSentAt = Instant.now()
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordResetRepository.findByUserId(userId) } returns Optional.of(reset)
            every { passwordEncoder.encode("new_password") } returns "encoded_new_password"
            every { userRepository.save(user) } returns user
            every { passwordResetRepository.delete(reset) } just Runs

            authService.resetPassword(ResetPasswordRequest(identifier = "user@example.com", otpCode = rawOtp, newPassword = "new_password"))

            assertThat(user.passwordHash).isEqualTo("encoded_new_password")
            verify {
                userRepository.save(user)
                passwordResetRepository.delete(reset)
            }
        }

        @Test
        fun `should throw PasswordResetNotFoundException when no pending reset request exists`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordResetRepository.findByUserId(userId) } returns Optional.empty()

            assertThatThrownBy {
                authService.resetPassword(ResetPasswordRequest(identifier = "user@example.com", otpCode = "123456", newPassword = "pass"))
            }.isInstanceOf(PasswordResetNotFoundException::class.java)
        }

        @Test
        fun `should throw OtpExpiredException when reset OTP has expired`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            val reset = PasswordReset(
                user = user,
                otpHash = sha256("123456"),
                expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES),
                attempts = 0,
                lastSentAt = Instant.now().minus(11, ChronoUnit.MINUTES)
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordResetRepository.findByUserId(userId) } returns Optional.of(reset)

            assertThatThrownBy {
                authService.resetPassword(ResetPasswordRequest(identifier = "user@example.com", otpCode = "123456", newPassword = "pass"))
            }.isInstanceOf(OtpExpiredException::class.java)
        }

        @Test
        fun `should throw OtpMaxAttemptsExceededException when attempts exceed limit`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            val reset = PasswordReset(
                user = user,
                otpHash = sha256("123456"),
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 3,
                lastSentAt = Instant.now()
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordResetRepository.findByUserId(userId) } returns Optional.of(reset)

            assertThatThrownBy {
                authService.resetPassword(ResetPasswordRequest(identifier = "user@example.com", otpCode = "123456", newPassword = "pass"))
            }.isInstanceOf(OtpMaxAttemptsExceededException::class.java)
        }

        @Test
        fun `should increment attempts, save reset entity and throw InvalidOtpException when OTP is incorrect`() {
            val userId = UUID.randomUUID()
            val user = createTestUser(id = userId)
            val reset = PasswordReset(
                user = user,
                otpHash = sha256("123456"),
                expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES),
                attempts = 1,
                lastSentAt = Instant.now()
            )

            every { userRepository.findByEmailIgnoreCase("user@example.com") } returns Optional.of(user)
            every { passwordResetRepository.findByUserId(userId) } returns Optional.of(reset)
            every { passwordResetRepository.save(reset) } returns reset

            assertThatThrownBy {
                authService.resetPassword(ResetPasswordRequest(identifier = "user@example.com", otpCode = "000000", newPassword = "pass"))
            }.isInstanceOf(InvalidOtpException::class.java)

            assertThat(reset.attempts).isEqualTo(2)
            verify { passwordResetRepository.save(reset) }
        }
    }
}