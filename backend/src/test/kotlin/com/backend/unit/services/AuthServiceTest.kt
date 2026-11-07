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
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.MessageDigest
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class AuthServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK(relaxed = true)
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK(relaxUnitFun = true)
    private lateinit var emailVerificationRepository: EmailVerificationRepository

    @MockK(relaxUnitFun = true)
    private lateinit var passwordResetRepository: PasswordResetRepository

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var jwtService: JwtService

    @MockK(relaxUnitFun = true)
    private lateinit var mailService: MailService

    @MockK(relaxUnitFun = true)
    private lateinit var wishlistService: WishlistService

    private lateinit var authService: AuthService

    companion object {
        private const val REFRESH_TOKEN_TTL_DAYS = 30L
        private const val OTP_TTL_MINUTES = 10L
        private const val MAX_ATTEMPTS = 3
        private const val RESEND_COOLDOWN_SECONDS = 60L
        private const val DUMMY_PASSWORD_HASH = $$"$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5uac.Hs2HL8YXtxjKmlXXNzDmSw6C"
        private val OTP_REGEX = Regex("""^\d{6}$""")
    }

    @BeforeEach
    fun setUp() {
        TransactionSynchronizationManager.initSynchronization()

        authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            emailVerificationRepository = emailVerificationRepository,
            passwordResetRepository = passwordResetRepository,
            passwordEncoder = passwordEncoder,
            jwtService = jwtService,
            mailService = mailService,
            wishlistService = wishlistService,
            refreshTokenTtlDays = REFRESH_TOKEN_TTL_DAYS,
            otpTtlMinutes = OTP_TTL_MINUTES,
            maxAttempts = MAX_ATTEMPTS,
            resendCooldownSeconds = RESEND_COOLDOWN_SECONDS,
        )

        every { passwordEncoder.encode(any()) } answers { "encoded:${firstArg<String>()}" }
        every { passwordEncoder.matches(any(), any()) } answers { secondArg<String>() == "encoded:${firstArg<String>()}" }
        every { jwtService.generateAccessToken(any(), any()) } answers { "access-token-for-${firstArg<UUID>()}" }
    }

    @AfterEach
    fun tearDown() {
        TransactionSynchronizationManager.clearSynchronization()
    }

    private fun commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
    }

    private fun sha256(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun user(
        id: UUID = UUID.randomUUID(),
        username: String = "marco",
        email: String = "marco@example.com",
        password: String = "secret123",
        status: AccountStatus = AccountStatus.ACTIVE,
        role: UserRole = UserRole.USER,
    ) = User(id = id, username = username, email = email, passwordHash = "encoded:$password", accountStatus = status, role = role)

    private fun verification(
        user: User,
        rawOtp: String = "123456",
        expiresIn: Duration = Duration.ofMinutes(10),
        attempts: Int = 0,
        sentAgo: Duration = Duration.ofMinutes(5),
    ) = EmailVerification(
        user = user,
        otpHash = sha256(rawOtp),
        expiresAt = Instant.now().plus(expiresIn),
        attempts = attempts,
        lastSentAt = Instant.now().minus(sentAgo),
    )

    private fun passwordReset(
        user: User,
        rawOtp: String = "654321",
        expiresIn: Duration = Duration.ofMinutes(10),
        attempts: Int = 0,
        sentAgo: Duration = Duration.ofMinutes(5),
    ) = PasswordReset(
        user = user,
        otpHash = sha256(rawOtp),
        expiresAt = Instant.now().plus(expiresIn),
        attempts = attempts,
        lastSentAt = Instant.now().minus(sentAgo),
    )

    private fun stubUserByEmail(email: String, user: User?) {
        every { userRepository.findByEmailIgnoreCase(email) } returns Optional.ofNullable(user)
    }

    private fun stubUserByUsername(username: String, user: User?) {
        every { userRepository.findByUsernameIgnoreCase(username) } returns Optional.ofNullable(user)
    }

    @Nested
    @DisplayName("signup")
    inner class SignupTests {

        private val savedUser = slot<User>()
        private val savedVerification = slot<EmailVerification>()
        private val mailedOtp = slot<String>()

        private fun stubSignup() {
            every { userRepository.existsByEmailIgnoreCase(any()) } returns false
            every { userRepository.existsByUsernameIgnoreCase(any()) } returns false
            every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured.apply { id = UUID.randomUUID() } }
            every { emailVerificationRepository.save(capture(savedVerification)) } answers { savedVerification.captured }
            every { mailService.sendActivationOtp(any(), capture(mailedOtp), any()) } just Runs
        }

        private val request = RegisterRequest(username = " Marco ", email = " Marco@Example.com ", password = " secret123 ")

        @Test
        fun `should create an inactive user with normalized email, trimmed username and encoded password`() {
            stubSignup()

            authService.signup(request)

            assertThat(savedUser.captured.email).isEqualTo("marco@example.com")
            assertThat(savedUser.captured.username).isEqualTo("Marco")
            assertThat(savedUser.captured.passwordHash).isEqualTo("encoded:secret123")
            assertThat(savedUser.captured.accountStatus).isEqualTo(AccountStatus.INACTIVE)
            assertThat(savedUser.captured.role).isEqualTo(UserRole.USER)
        }

        @Test
        fun `should check email and username case-insensitively`() {
            stubSignup()

            authService.signup(request)

            verify { userRepository.existsByEmailIgnoreCase("marco@example.com") }
            verify { userRepository.existsByUsernameIgnoreCase("marco") }
        }

        @Test
        fun `should create the default wishlist for the saved user`() {
            stubSignup()

            authService.signup(request)

            verify(exactly = 1) { wishlistService.createDefaultWishlistForUser(savedUser.captured) }
        }

        @Test
        fun `should store the hash of the OTP that is mailed`() {
            stubSignup()
            val before = Instant.now()

            authService.signup(request)
            commit()

            assertThat(mailedOtp.captured).matches(OTP_REGEX.pattern)
            assertThat(savedVerification.captured.otpHash).isEqualTo(sha256(mailedOtp.captured))
            assertThat(savedVerification.captured.otpHash).isNotEqualTo(mailedOtp.captured)
            assertThat(savedVerification.captured.attempts).isEqualTo(0)
            assertThat(savedVerification.captured.expiresAt).isAfterOrEqualTo(before.plus(Duration.ofMinutes(OTP_TTL_MINUTES)))
            assertThat(savedVerification.captured.user).isSameAs(savedUser.captured)
        }

        @Test
        fun `should send the activation email only after the transaction commits`() {
            stubSignup()

            val response = authService.signup(request)

            verify(exactly = 0) { mailService.sendActivationOtp(any(), any(), any()) }

            commit()

            verify(exactly = 1) { mailService.sendActivationOtp("marco@example.com", any(), OTP_TTL_MINUTES) }
            assertThat(response.email).isEqualTo("marco@example.com")
        }

        @Test
        fun `should not fail when the activation email cannot be sent`() {
            stubSignup()
            every { mailService.sendActivationOtp(any(), any(), any()) } throws RuntimeException("Brevo 401")

            authService.signup(request)

            commit()
        }

        @Test
        fun `should throw EmailAlreadyTakenException without saving when email exists`() {
            every { userRepository.existsByEmailIgnoreCase("marco@example.com") } returns true

            assertThatThrownBy { authService.signup(request) }
                .isInstanceOf(EmailAlreadyTakenException::class.java)

            verify(exactly = 0) { userRepository.saveAndFlush(any()) }
            verify(exactly = 0) { passwordEncoder.encode(any()) }
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
        }

        @Test
        fun `should throw UsernameAlreadyTakenException without saving when username exists`() {
            every { userRepository.existsByEmailIgnoreCase("marco@example.com") } returns false
            every { userRepository.existsByUsernameIgnoreCase("marco") } returns true

            assertThatThrownBy { authService.signup(request) }
                .isInstanceOf(UsernameAlreadyTakenException::class.java)

            verify(exactly = 0) { userRepository.saveAndFlush(any()) }
        }

        @Test
        fun `should map a concurrent duplicate email to EmailAlreadyTakenException`() {
            stubSignup()
            every { userRepository.saveAndFlush(any()) } throws DataIntegrityViolationException(
                "could not execute statement",
                SQLException("duplicate key value violates unique constraint \"users_email_key\""),
            )

            assertThatThrownBy { authService.signup(request) }
                .isInstanceOf(EmailAlreadyTakenException::class.java)

            verify(exactly = 0) { wishlistService.createDefaultWishlistForUser(any()) }
            verify(exactly = 0) { emailVerificationRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("activateAccount")
    inner class ActivateAccountTests {

        @Test
        fun `should activate the account and delete the verification when the OTP is valid`() {
            val user = user(status = AccountStatus.INACTIVE)
            val pending = verification(user, rawOtp = "123456")
            stubUserByEmail("marco@example.com", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.of(pending)

            authService.activateAccount(ActivateAccountRequest(identifier = " Marco@Example.com ", otpCode = "123456"))

            assertThat(user.accountStatus).isEqualTo(AccountStatus.ACTIVE)
            verify(exactly = 1) { emailVerificationRepository.delete(pending) }
        }

        @Test
        fun `should find the user by username too`() {
            val user = user(status = AccountStatus.INACTIVE)
            stubUserByUsername("marco", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.of(verification(user))

            authService.activateAccount(ActivateAccountRequest(identifier = "Marco", otpCode = "123456"))

            assertThat(user.accountStatus).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        fun `should not reveal whether an unknown identifier exists`() {
            stubUserByEmail("ghost@example.com", null)

            assertThatThrownBy { authService.activateAccount(ActivateAccountRequest(identifier = "ghost@example.com", otpCode = "123456")) }
                .isInstanceOf(EmailVerificationNotFoundException::class.java)
        }

        @Test
        fun `should throw EmailVerificationNotFoundException when there is no pending verification`() {
            val user = user()
            stubUserByEmail("marco@example.com", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.empty()

            assertThatThrownBy { authService.activateAccount(ActivateAccountRequest(identifier = "marco@example.com", otpCode = "123456")) }
                .isInstanceOf(EmailVerificationNotFoundException::class.java)
        }

        @Test
        fun `should reject an expired OTP even when it is correct`() {
            val user = user(status = AccountStatus.INACTIVE)
            val pending = verification(user, expiresIn = Duration.ofMinutes(-1))
            stubUserByEmail("marco@example.com", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.of(pending)

            assertThatThrownBy { authService.activateAccount(ActivateAccountRequest(identifier = "marco@example.com", otpCode = "123456")) }
                .isInstanceOf(OtpExpiredException::class.java)

            assertThat(user.accountStatus).isEqualTo(AccountStatus.INACTIVE)
            assertThat(pending.attempts).isEqualTo(0)
            verify(exactly = 0) { emailVerificationRepository.delete(any()) }
        }

        @Test
        fun `should reject even a correct OTP once the attempts are exhausted`() {
            val user = user(status = AccountStatus.INACTIVE)
            stubUserByEmail("marco@example.com", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.of(verification(user, attempts = MAX_ATTEMPTS))

            assertThatThrownBy { authService.activateAccount(ActivateAccountRequest(identifier = "marco@example.com", otpCode = "123456")) }
                .isInstanceOf(OtpMaxAttemptsExceededException::class.java)

            assertThat(user.accountStatus).isEqualTo(AccountStatus.INACTIVE)
        }

        @Test
        fun `should count a wrong OTP as an attempt without activating`() {
            val user = user(status = AccountStatus.INACTIVE)
            val pending = verification(user, attempts = 1)
            stubUserByEmail("marco@example.com", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.of(pending)

            assertThatThrownBy { authService.activateAccount(ActivateAccountRequest(identifier = "marco@example.com", otpCode = "999999")) }
                .isInstanceOf(InvalidOtpException::class.java)

            assertThat(pending.attempts).isEqualTo(2)
            assertThat(user.accountStatus).isEqualTo(AccountStatus.INACTIVE)
            verify(exactly = 0) { emailVerificationRepository.delete(any()) }
        }
    }

    @Nested
    @DisplayName("resendActivationOtp")
    inner class ResendActivationOtpTests {

        @Test
        fun `should issue a new OTP, reset the attempts and mail it after commit`() {
            val user = user(status = AccountStatus.INACTIVE)
            val pending = verification(user, rawOtp = "111111", attempts = 2, expiresIn = Duration.ZERO, sentAgo = Duration.ofSeconds(RESEND_COOLDOWN_SECONDS + 5))
            val mailedOtp = slot<String>()
            val before = Instant.now()
            stubUserByUsername("marco", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.of(pending)
            every { mailService.sendActivationOtp(any(), capture(mailedOtp), any()) } just Runs

            authService.resendActivationOtp(ResendOtpRequest(identifier = "marco"))

            verify(exactly = 0) { mailService.sendActivationOtp(any(), any(), any()) }
            commit()

            verify(exactly = 1) { mailService.sendActivationOtp("marco@example.com", any(), OTP_TTL_MINUTES) }
            assertThat(pending.otpHash).isEqualTo(sha256(mailedOtp.captured))
            assertThat(pending.attempts).isEqualTo(0)
            assertThat(pending.lastSentAt).isAfterOrEqualTo(before)
            assertThat(pending.expiresAt).isAfterOrEqualTo(before.plus(Duration.ofMinutes(OTP_TTL_MINUTES)))
        }

        @Test
        fun `should refuse to resend during the cooldown`() {
            val user = user(status = AccountStatus.INACTIVE)
            val pending = verification(user, sentAgo = Duration.ofSeconds(20))
            val originalHash = pending.otpHash
            stubUserByEmail("marco@example.com", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.of(pending)

            assertThatThrownBy { authService.resendActivationOtp(ResendOtpRequest(identifier = "marco@example.com")) }
                .isInstanceOf(OtpResendCooldownException::class.java)

            assertThat(pending.otpHash).isEqualTo(originalHash)
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
        }

        @Test
        fun `should throw EmailVerificationNotFoundException for an unknown identifier`() {
            stubUserByEmail("ghost@example.com", null)

            assertThatThrownBy { authService.resendActivationOtp(ResendOtpRequest(identifier = "ghost@example.com")) }
                .isInstanceOf(EmailVerificationNotFoundException::class.java)
        }

        @Test
        fun `should throw EmailVerificationNotFoundException for an already active account`() {
            val user = user()
            stubUserByEmail("marco@example.com", user)
            every { emailVerificationRepository.findByUserId(user.id!!) } returns Optional.empty()

            assertThatThrownBy { authService.resendActivationOtp(ResendOtpRequest(identifier = "marco@example.com")) }
                .isInstanceOf(EmailVerificationNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("login")
    inner class LoginTests {

        private val savedTokens = mutableListOf<RefreshToken>()

        @BeforeEach
        fun stubTokens() {
            every { refreshTokenRepository.save(capture(savedTokens)) } answers { firstArg() }
        }

        @Test
        fun `should log in by email and issue an access and a refresh token`() {
            val user = user()
            stubUserByEmail("marco@example.com", user)
            val before = Instant.now()

            val response = authService.login(LoginRequest(identifier = " MARCO@example.com ", password = "secret123"))

            assertThat(response.accessToken).isEqualTo("access-token-for-${user.id}")
            assertThat(response.user.id).isEqualTo(user.id)
            assertThat(savedTokens).hasSize(1)
            assertThat(savedTokens[0].tokenHash).isEqualTo(sha256(response.refreshToken))
            assertThat(savedTokens[0].user).isSameAs(user)
            assertThat(savedTokens[0].expiresAt).isAfterOrEqualTo(before.plus(Duration.ofDays(REFRESH_TOKEN_TTL_DAYS)))
            verify { jwtService.generateAccessToken(user.id!!, "USER") }
        }

        @Test
        fun `should log in by username`() {
            val user = user()
            stubUserByUsername("marco", user)

            val response = authService.login(LoginRequest(identifier = "Marco", password = "secret123"))

            assertThat(response.user.id).isEqualTo(user.id)
        }

        @Test
        fun `should put the role in the access token`() {
            val admin = user(role = UserRole.ADMIN)
            stubUserByEmail("marco@example.com", admin)

            authService.login(LoginRequest(identifier = "marco@example.com", password = "secret123"))

            verify { jwtService.generateAccessToken(admin.id!!, "ADMIN") }
        }

        @Test
        fun `should ignore spaces around the password`() {
            stubUserByEmail("marco@example.com", user())

            authService.login(LoginRequest(identifier = "marco@example.com", password = "  secret123  "))
        }

        @Test
        fun `should issue a different refresh token on every login`() {
            stubUserByEmail("marco@example.com", user())

            val first = authService.login(LoginRequest(identifier = "marco@example.com", password = "secret123"))
            val second = authService.login(LoginRequest(identifier = "marco@example.com", password = "secret123"))

            assertThat(first.refreshToken).isNotEqualTo(second.refreshToken)
            assertThat(first.refreshToken).hasSizeGreaterThanOrEqualTo(43)
        }

        @Test
        fun `should check a dummy hash for unknown users to keep the timing constant`() {
            stubUserByEmail("ghost@example.com", null)

            assertThatThrownBy { authService.login(LoginRequest(identifier = "ghost@example.com", password = "secret123")) }
                .isInstanceOf(InvalidCredentialsException::class.java)

            verify(exactly = 1) { passwordEncoder.matches("secret123", DUMMY_PASSWORD_HASH) }
            assertThat(savedTokens).isEmpty()
        }

        @Test
        fun `should throw InvalidCredentialsException for a wrong password`() {
            stubUserByEmail("marco@example.com", user())

            assertThatThrownBy { authService.login(LoginRequest(identifier = "marco@example.com", password = "wrong")) }
                .isInstanceOf(InvalidCredentialsException::class.java)

            assertThat(savedTokens).isEmpty()
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["INACTIVE", "SUSPENDED"])
        fun `should refuse to log in an account that is not active`(status: AccountStatus) {
            stubUserByEmail("marco@example.com", user(status = status))

            assertThatThrownBy { authService.login(LoginRequest(identifier = "marco@example.com", password = "secret123")) }
                .isInstanceOf(AccountNotActivatedException::class.java)

            assertThat(savedTokens).isEmpty()
        }

        @Test
        fun `should not reveal the account status when the password is wrong`() {
            stubUserByEmail("marco@example.com", user(status = AccountStatus.INACTIVE))

            assertThatThrownBy { authService.login(LoginRequest(identifier = "marco@example.com", password = "wrong")) }
                .isInstanceOf(InvalidCredentialsException::class.java)
        }

        @Test
        fun `should treat a deleted account as unknown`() {
            val deleted = user(email = "deleted-1@letstable.invalid", status = AccountStatus.DELETED).apply { passwordHash = "" }
            stubUserByEmail("deleted-1@letstable.invalid", deleted)

            assertThatThrownBy { authService.login(LoginRequest(identifier = "deleted-1@letstable.invalid", password = "")) }
                .isInstanceOf(InvalidCredentialsException::class.java)

            verify(exactly = 1) { passwordEncoder.matches("", DUMMY_PASSWORD_HASH) }
        }
    }

    @Nested
    @DisplayName("refresh")
    inner class RefreshTests {

        private val savedTokens = mutableListOf<RefreshToken>()

        @BeforeEach
        fun stubTokens() {
            every { refreshTokenRepository.save(capture(savedTokens)) } answers { firstArg() }
        }

        private fun storedToken(user: User = user(), raw: String = "raw-token", expiresIn: Duration = Duration.ofDays(1), revoked: Boolean = false) =
            RefreshToken(user = user, tokenHash = sha256(raw), expiresAt = Instant.now().plus(expiresIn), revoked = revoked)

        @Test
        fun `should rotate the refresh token`() {
            val user = user()
            val current = storedToken(user)
            every { refreshTokenRepository.findByTokenHash(sha256("raw-token")) } returns Optional.of(current)

            val response = authService.refresh("raw-token")

            assertThat(current.revoked).isTrue()
            assertThat(response.accessToken).isEqualTo("access-token-for-${user.id}")
            assertThat(response.refreshToken).isNotEqualTo("raw-token")
            assertThat(savedTokens.single().tokenHash).isEqualTo(sha256(response.refreshToken))
            assertThat(savedTokens.single().revoked).isFalse()
        }

        @Test
        fun `should throw RefreshTokenNotFoundException for an unknown token`() {
            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.empty()

            assertThatThrownBy { authService.refresh("unknown") }
                .isInstanceOf(RefreshTokenNotFoundException::class.java)
        }

        @Test
        fun `should refuse a revoked token`() {
            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.of(storedToken(revoked = true))

            assertThatThrownBy { authService.refresh("raw-token") }
                .isInstanceOf(RefreshTokenExpiredOrRevokedException::class.java)

            assertThat(savedTokens).isEmpty()
        }

        @Test
        fun `should refuse an expired token`() {
            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.of(storedToken(expiresIn = Duration.ofSeconds(-1)))

            assertThatThrownBy { authService.refresh("raw-token") }
                .isInstanceOf(RefreshTokenExpiredOrRevokedException::class.java)

            assertThat(savedTokens).isEmpty()
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["ACTIVE"], mode = EnumSource.Mode.EXCLUDE)
        fun `should refuse to refresh for an account that is no longer active`(status: AccountStatus) {
            val current = storedToken(user(status = status))
            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.of(current)

            assertThatThrownBy { authService.refresh("raw-token") }
                .isInstanceOf(AccountNotActivatedException::class.java)

            assertThat(savedTokens).isEmpty()
            assertThat(current.revoked).isFalse()
        }
    }

    @Nested
    @DisplayName("logout")
    inner class LogoutTests {

        @Test
        fun `should revoke the refresh token`() {
            val token = RefreshToken(user = user(), tokenHash = sha256("raw-token"), expiresAt = Instant.now().plus(Duration.ofDays(1)), revoked = false)
            every { refreshTokenRepository.findByTokenHash(sha256("raw-token")) } returns Optional.of(token)

            authService.logout("raw-token")

            assertThat(token.revoked).isTrue()
        }

        @Test
        fun `should do nothing for an unknown token`() {
            every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.empty()

            authService.logout("unknown")
        }
    }

    @Nested
    @DisplayName("forgotPassword")
    inner class ForgotPasswordTests {

        @Test
        fun `should return silently for an unknown identifier`() {
            stubUserByEmail("ghost@example.com", null)

            authService.forgotPassword(ForgotPasswordRequest(identifier = "ghost@example.com"))
            commit()

            verify(exactly = 0) { passwordResetRepository.save(any()) }
            verify(exactly = 0) { mailService.sendPasswordResetOtp(any(), any(), any()) }
        }

        @Test
        fun `should return silently for a deleted account`() {
            stubUserByEmail("deleted-1@letstable.invalid", user(email = "deleted-1@letstable.invalid", status = AccountStatus.DELETED))

            authService.forgotPassword(ForgotPasswordRequest(identifier = "deleted-1@letstable.invalid"))
            commit()

            verify(exactly = 0) { passwordResetRepository.save(any()) }
            verify(exactly = 0) { mailService.sendPasswordResetOtp(any(), any(), any()) }
        }

        @Test
        fun `should create a reset request and mail its OTP after commit`() {
            val user = user()
            val savedReset = slot<PasswordReset>()
            val mailedOtp = slot<String>()
            stubUserByUsername("marco", user)
            every { passwordResetRepository.findByUserId(user.id!!) } returns Optional.empty()
            every { passwordResetRepository.save(capture(savedReset)) } answers { savedReset.captured }
            every { mailService.sendPasswordResetOtp(any(), capture(mailedOtp), any()) } just Runs

            authService.forgotPassword(ForgotPasswordRequest(identifier = "marco"))

            verify(exactly = 0) { mailService.sendPasswordResetOtp(any(), any(), any()) }
            commit()

            verify(exactly = 1) { mailService.sendPasswordResetOtp("marco@example.com", any(), OTP_TTL_MINUTES) }
            assertThat(mailedOtp.captured).matches(OTP_REGEX.pattern)
            assertThat(savedReset.captured.otpHash).isEqualTo(sha256(mailedOtp.captured))
            assertThat(savedReset.captured.attempts).isEqualTo(0)
        }

        @Test
        fun `should reuse the existing reset request once the cooldown has elapsed`() {
            val user = user()
            val existing = passwordReset(user, attempts = 2, sentAgo = Duration.ofSeconds(RESEND_COOLDOWN_SECONDS + 10))
            val mailedOtp = slot<String>()
            stubUserByEmail("marco@example.com", user)
            every { passwordResetRepository.findByUserId(user.id!!) } returns Optional.of(existing)
            every { mailService.sendPasswordResetOtp(any(), capture(mailedOtp), any()) } just Runs

            authService.forgotPassword(ForgotPasswordRequest(identifier = "marco@example.com"))
            commit()

            assertThat(existing.attempts).isEqualTo(0)
            assertThat(existing.otpHash).isEqualTo(sha256(mailedOtp.captured))
            verify(exactly = 0) { passwordResetRepository.save(any()) }
        }

        @Test
        fun `should refuse a new request during the cooldown`() {
            val user = user()
            val existing = passwordReset(user, sentAgo = Duration.ofSeconds(10))
            val originalHash = existing.otpHash
            stubUserByEmail("marco@example.com", user)
            every { passwordResetRepository.findByUserId(user.id!!) } returns Optional.of(existing)

            assertThatThrownBy { authService.forgotPassword(ForgotPasswordRequest(identifier = "marco@example.com")) }
                .isInstanceOf(OtpResendCooldownException::class.java)

            assertThat(existing.otpHash).isEqualTo(originalHash)
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
        }
    }

    @Nested
    @DisplayName("resetPassword")
    inner class ResetPasswordTests {

        @Test
        fun `should change the password, delete the request and revoke every session`() {
            val user = user()
            val reset = passwordReset(user, rawOtp = "654321")
            stubUserByEmail("marco@example.com", user)
            every { passwordResetRepository.findByUserId(user.id!!) } returns Optional.of(reset)

            authService.resetPassword(ResetPasswordRequest(identifier = "marco@example.com", otpCode = "654321", newPassword = " newSecret456 "))

            assertThat(user.passwordHash).isEqualTo("encoded:newSecret456")
            verify(exactly = 1) { passwordResetRepository.delete(reset) }
            verify(exactly = 1) { refreshTokenRepository.revokeAllByUserId(user.id!!) }
        }

        @Test
        fun `should throw PasswordResetNotFoundException for an unknown identifier`() {
            stubUserByEmail("ghost@example.com", null)

            assertThatThrownBy { authService.resetPassword(ResetPasswordRequest(identifier = "ghost@example.com", otpCode = "654321", newPassword = "pass")) }
                .isInstanceOf(PasswordResetNotFoundException::class.java)
        }

        @Test
        fun `should throw PasswordResetNotFoundException when no reset was requested`() {
            val user = user()
            stubUserByEmail("marco@example.com", user)
            every { passwordResetRepository.findByUserId(user.id!!) } returns Optional.empty()

            assertThatThrownBy { authService.resetPassword(ResetPasswordRequest(identifier = "marco@example.com", otpCode = "654321", newPassword = "pass")) }
                .isInstanceOf(PasswordResetNotFoundException::class.java)
        }

        @Test
        fun `should reject an expired OTP without changing anything`() {
            val user = user()
            stubUserByEmail("marco@example.com", user)
            every { passwordResetRepository.findByUserId(user.id!!) } returns Optional.of(passwordReset(user, expiresIn = Duration.ofMinutes(-1)))

            assertThatThrownBy { authService.resetPassword(ResetPasswordRequest(identifier = "marco@example.com", otpCode = "654321", newPassword = "pass")) }
                .isInstanceOf(OtpExpiredException::class.java)

            assertThat(user.passwordHash).isEqualTo("encoded:secret123")
            verify(exactly = 0) { refreshTokenRepository.revokeAllByUserId(any()) }
        }

        @Test
        fun `should reject even a correct OTP once the attempts are exhausted`() {
            val user = user()
            stubUserByEmail("marco@example.com", user)
            every { passwordResetRepository.findByUserId(user.id!!) } returns Optional.of(passwordReset(user, attempts = MAX_ATTEMPTS))

            assertThatThrownBy { authService.resetPassword(ResetPasswordRequest(identifier = "marco@example.com", otpCode = "654321", newPassword = "pass")) }
                .isInstanceOf(OtpMaxAttemptsExceededException::class.java)

            assertThat(user.passwordHash).isEqualTo("encoded:secret123")
        }

        @Test
        fun `should count a wrong OTP as an attempt without changing the password`() {
            val user = user()
            val reset = passwordReset(user, attempts = 1)
            stubUserByEmail("marco@example.com", user)
            every { passwordResetRepository.findByUserId(user.id!!) } returns Optional.of(reset)

            assertThatThrownBy { authService.resetPassword(ResetPasswordRequest(identifier = "marco@example.com", otpCode = "000000", newPassword = "pass")) }
                .isInstanceOf(InvalidOtpException::class.java)

            assertThat(reset.attempts).isEqualTo(2)
            assertThat(user.passwordHash).isEqualTo("encoded:secret123")
            verify(exactly = 0) { passwordResetRepository.delete(any()) }
            verify(exactly = 0) { refreshTokenRepository.revokeAllByUserId(any()) }
        }
    }
}