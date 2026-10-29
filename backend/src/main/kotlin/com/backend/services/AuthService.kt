package com.backend.services

import com.backend.exceptions.*
import com.backend.models.dtos.*
import com.backend.models.entities.EmailVerification
import com.backend.models.entities.PasswordReset
import com.backend.models.entities.RefreshToken
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.repositories.EmailVerificationRepository
import com.backend.repositories.PasswordResetRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UserRepository
import com.backend.utils.duplicateUserException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val passwordResetRepository: PasswordResetRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val mailService: MailService,
    private val wishlistService: WishlistService,
    @Value($$"${jwt.refresh-token-ttl-days}") private val refreshTokenTtlDays: Long,
    @Value($$"${otp.ttl-minutes}") private val otpTtlMinutes: Long,
    @Value($$"${otp.max-attempts}") private val maxAttempts: Int,
    @Value($$"${otp.resend-cooldown-seconds}") private val resendCooldownSeconds: Long,
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        private const val DUMMY_PASSWORD_HASH = $$"$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5uac.Hs2HL8YXtxjKmlXXNzDmSw6C"
    }

    @Transactional
    fun signup(request: RegisterRequest): SignupResponse {
        logger.debug("\n\t[DEBUG] [auth_service][signup] Signup attempt for email {}", request.email)

        try {
            val normalizedEmail = request.email.trim().lowercase()
            val trimmedUsername = request.username.trim()
            val normalizedUsername = trimmedUsername.lowercase()

            if(userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw EmailAlreadyTakenException(normalizedEmail)
            }

            if(userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
                throw UsernameAlreadyTakenException(normalizedUsername)
            }

            val saved = try {
                userRepository.saveAndFlush(
                    User(
                        username = trimmedUsername,
                        email = normalizedEmail,
                        passwordHash = passwordEncoder.encode(request.password.trim())!!,
                        accountStatus = AccountStatus.INACTIVE,
                    )
                )
            } catch(e: DataIntegrityViolationException) {
                throw duplicateUserException(e, normalizedEmail, normalizedUsername)
            }

            wishlistService.createDefaultWishlistForUser(saved)

            val now = Instant.now()
            val rawOtp = generateOtp()

            emailVerificationRepository.save(
                EmailVerification(
                    user = saved,
                    otpHash = hashValue(rawOtp),
                    expiresAt = otpExpiry(now),
                    attempts = 0,
                    lastSentAt = now,
                )
            )

            sendMailAfterCommit("signup") { mailService.sendActivationOtp(saved.email, rawOtp, otpTtlMinutes) }

            logger.info("\n\t[INFO] [auth_service][signup] User {} signed up, OTP issued", saved.id)
            return SignupResponse(email = saved.email, message = "Please check your email for a verification code")
        } catch(e: EmailAlreadyTakenException) {
            logger.warn("\n\t[WARN] [auth_service][signup] Email already taken: {}", request.email)
            throw e
        } catch(e: UsernameAlreadyTakenException) {
            logger.warn("\n\t[WARN] [auth_service][signup] Username already taken: {}", request.username)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [auth_service][signup] Error signing up with email {}: {}", request.email, e.message)
            throw e
        }
    }

    @Transactional(noRollbackFor = [InvalidOtpException::class])
    fun activateAccount(request: ActivateAccountRequest) {
        logger.debug("\n\t[DEBUG] [auth_service][activate_account] Activating account for identifier {}", request.identifier)

        try {
            val user = findByIdentifierOrNull(request.identifier) ?: throw EmailVerificationNotFoundException()
            val verification = emailVerificationRepository.findByUserId(user.id!!)
                .orElseThrow { EmailVerificationNotFoundException() }

            verifyOtp(
                expired = verification.isExpired(),
                attemptsExceeded = verification.hasExceededMaxAttempts(maxAttempts),
                storedHash = verification.otpHash,
                rawOtp = request.otpCode,
            ) { verification.attempts += 1 }

            user.accountStatus = AccountStatus.ACTIVE
            emailVerificationRepository.delete(verification)

            logger.info("\n\t[INFO] [auth_service][activate_account] Account activated for user {}", user.id)
        } catch(e: EmailVerificationNotFoundException) {
            logger.warn("\n\t[WARN] [auth_service][activate_account] No pending verification for identifier {}", request.identifier)
            throw e
        } catch(e: InvalidOtpException) {
            logger.warn("\n\t[WARN] [auth_service][activate_account] Invalid OTP for identifier {}", request.identifier)
            throw e
        } catch(e: OtpExpiredException) {
            logger.warn("\n\t[WARN] [auth_service][activate_account] Expired OTP for identifier {}", request.identifier)
            throw e
        } catch(e: OtpMaxAttemptsExceededException) {
            logger.warn("\n\t[WARN] [auth_service][activate_account] Max attempts exceeded for identifier {}", request.identifier)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [auth_service][activate_account] Error activating account for identifier {}: {}", request.identifier, e.message)
            throw e
        }
    }

    @Transactional
    fun resendActivationOtp(request: ResendOtpRequest) {
        logger.debug("\n\t[DEBUG] [auth_service][resend_activation_otp] Resending activation OTP for identifier {}", request.identifier)

        try {
            val user = findByIdentifierOrNull(request.identifier) ?: throw EmailVerificationNotFoundException()
            val verification = emailVerificationRepository.findByUserId(user.id!!)
                .orElseThrow { EmailVerificationNotFoundException() }

            val now = Instant.now()
            ensureCooldownElapsed(verification.lastSentAt, now)

            val rawOtp = generateOtp()
            verification.otpHash = hashValue(rawOtp)
            verification.expiresAt = otpExpiry(now)
            verification.attempts = 0
            verification.lastSentAt = now

            sendMailAfterCommit("resend_activation_otp") { mailService.sendActivationOtp(user.email, rawOtp, otpTtlMinutes) }

            logger.info("\n\t[INFO] [auth_service][resend_activation_otp] Activation OTP resent for user {}", user.id)
        } catch(e: EmailVerificationNotFoundException) {
            logger.warn("\n\t[WARN] [auth_service][resend_activation_otp] No pending verification for identifier {}", request.identifier)
            throw e
        } catch(e: OtpResendCooldownException) {
            logger.warn("\n\t[WARN] [auth_service][resend_activation_otp] Resend cooldown active for identifier {}", request.identifier)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [auth_service][resend_activation_otp] Error resending OTP for identifier {}: {}", request.identifier, e.message)
            throw e
        }
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        logger.debug("\n\t[DEBUG] [auth_service][login] Login attempt for identifier {}", request.identifier)

        try {
            val user = findByIdentifierOrNull(request.identifier)

            if(user == null) {
                passwordEncoder.matches(request.password.trim(), DUMMY_PASSWORD_HASH)
                throw InvalidCredentialsException()
            }

            if(!passwordEncoder.matches(request.password.trim(), user.passwordHash)) {
                throw InvalidCredentialsException()
            }

            if(user.accountStatus != AccountStatus.ACTIVE) {
                throw AccountNotActivatedException()
            }

            val (accessToken, refreshToken) = issueTokens(user)

            logger.info("\n\t[INFO] [auth_service][login] User {} logged in successfully", user.id)
            return AuthResponse(accessToken = accessToken, refreshToken = refreshToken, user = UserDTO.from(user))
        } catch(e: InvalidCredentialsException) {
            logger.warn("\n\t[WARN] [auth_service][login] Invalid credentials for identifier {}", request.identifier)
            throw e
        } catch(e: AccountNotActivatedException) {
            logger.warn("\n\t[WARN] [auth_service][login] Account not activated for identifier {}", request.identifier)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [auth_service][login] Error logging in with identifier {}: {}", request.identifier, e.message)
            throw e
        }
    }

    @Transactional
    fun refresh(rawRefreshToken: String): RefreshResponse {
        logger.debug("\n\t[DEBUG] [auth_service][refresh] Refresh attempt")

        try {
            val storedToken = refreshTokenRepository.findByTokenHash(hashValue(rawRefreshToken))
                .orElseThrow { RefreshTokenNotFoundException() }

            if(storedToken.revoked || storedToken.expiresAt.isBefore(Instant.now())) {
                throw RefreshTokenExpiredOrRevokedException()
            }

            val user = storedToken.user

            if(user.accountStatus != AccountStatus.ACTIVE) {
                throw AccountNotActivatedException()
            }

            storedToken.revoked = true

            val (accessToken, refreshToken) = issueTokens(user)

            logger.info("\n\t[INFO] [auth_service][refresh] Access token refreshed for user {}", user.id)
            return RefreshResponse(accessToken = accessToken, refreshToken = refreshToken)
        } catch(e: RefreshTokenNotFoundException) {
            logger.warn("\n\t[WARN] [auth_service][refresh] Refresh token not found")
            throw e
        } catch(e: RefreshTokenExpiredOrRevokedException) {
            logger.warn("\n\t[WARN] [auth_service][refresh] Refresh token expired or revoked")
            throw e
        } catch(e: AccountNotActivatedException) {
            logger.warn("\n\t[WARN] [auth_service][refresh] Account not active")
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [auth_service][refresh] Error refreshing session: {}", e.message)
            throw e
        }
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        logger.debug("\n\t[DEBUG] [auth_service][logout] Logout attempt")

        try {
            refreshTokenRepository.findByTokenHash(hashValue(rawRefreshToken)).ifPresent { it.revoked = true }

            logger.info("\n\t[INFO] [auth_service][logout] Refresh token revoked")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [auth_service][logout] Error logging out: {}", e.message)
            throw e
        }
    }

    @Transactional
    fun forgotPassword(request: ForgotPasswordRequest) {
        logger.debug("\n\t[DEBUG] [auth_service][forgot_password] Password reset requested for identifier {}", request.identifier)

        try {
            val user = findByIdentifierOrNull(request.identifier)

            if(user == null) {
                logger.info("\n\t[INFO] [auth_service][forgot_password] No account found for identifier {}, responding generically", request.identifier)
                return
            }

            val now = Instant.now()
            val rawOtp = generateOtp()
            val existing = passwordResetRepository.findByUserId(user.id!!).orElse(null)

            if(existing != null) {
                ensureCooldownElapsed(existing.lastSentAt, now)

                existing.otpHash = hashValue(rawOtp)
                existing.expiresAt = otpExpiry(now)
                existing.attempts = 0
                existing.lastSentAt = now
            } else {
                passwordResetRepository.save(
                    PasswordReset(
                        user = user,
                        otpHash = hashValue(rawOtp),
                        expiresAt = otpExpiry(now),
                        attempts = 0,
                        lastSentAt = now,
                    )
                )
            }

            sendMailAfterCommit("forgot_password") { mailService.sendPasswordResetOtp(user.email, rawOtp, otpTtlMinutes) }

            logger.info("\n\t[INFO] [auth_service][forgot_password] Password reset OTP issued for user {}", user.id)
        } catch(e: OtpResendCooldownException) {
            logger.warn("\n\t[WARN] [auth_service][forgot_password] Resend cooldown active for identifier {}", request.identifier)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [auth_service][forgot_password] Error requesting password reset for identifier {}: {}", request.identifier, e.message)
            throw e
        }
    }

    @Transactional(noRollbackFor = [InvalidOtpException::class])
    fun resetPassword(request: ResetPasswordRequest) {
        logger.debug("\n\t[DEBUG] [auth_service][reset_password] Completing password reset for identifier {}", request.identifier)

        try {
            val user = findByIdentifierOrNull(request.identifier) ?: throw PasswordResetNotFoundException()
            val reset = passwordResetRepository.findByUserId(user.id!!)
                .orElseThrow { PasswordResetNotFoundException() }

            verifyOtp(
                expired = reset.isExpired(),
                attemptsExceeded = reset.hasExceededMaxAttempts(maxAttempts),
                storedHash = reset.otpHash,
                rawOtp = request.otpCode,
            ) { reset.attempts += 1 }

            user.passwordHash = passwordEncoder.encode(request.newPassword.trim())!!
            passwordResetRepository.delete(reset)
            refreshTokenRepository.revokeAllByUserId(user.id!!)

            logger.info("\n\t[INFO] [auth_service][reset_password] Password reset for user {}, all sessions revoked", user.id)
        } catch(e: PasswordResetNotFoundException) {
            logger.warn("\n\t[WARN] [auth_service][reset_password] No pending reset for identifier {}", request.identifier)
            throw e
        } catch(e: InvalidOtpException) {
            logger.warn("\n\t[WARN] [auth_service][reset_password] Invalid OTP for identifier {}", request.identifier)
            throw e
        } catch(e: OtpExpiredException) {
            logger.warn("\n\t[WARN] [auth_service][reset_password] Expired OTP for identifier {}", request.identifier)
            throw e
        } catch(e: OtpMaxAttemptsExceededException) {
            logger.warn("\n\t[WARN] [auth_service][reset_password] Max attempts exceeded for identifier {}", request.identifier)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [auth_service][reset_password] Error resetting password for identifier {}: {}", request.identifier, e.message)
            throw e
        }
    }

    private fun issueTokens(user: User): Pair<String, String> {
        val accessToken = jwtService.generateAccessToken(user.id!!, user.role.name)
        val rawRefreshToken = generateOpaqueToken()

        refreshTokenRepository.save(
            RefreshToken(
                user = user,
                tokenHash = hashValue(rawRefreshToken),
                expiresAt = Instant.now().plus(refreshTokenTtlDays, ChronoUnit.DAYS),
            )
        )

        return accessToken to rawRefreshToken
    }

    private fun findByIdentifierOrNull(identifier: String): User? {
        val normalized = identifier.trim().lowercase()

        val result = if(normalized.contains("@")) {
            userRepository.findByEmailIgnoreCase(normalized)
        } else {
            userRepository.findByUsernameIgnoreCase(normalized)
        }

        return result.orElse(null)
    }

    private fun verifyOtp(expired: Boolean, attemptsExceeded: Boolean, storedHash: String, rawOtp: String, onMismatch: () -> Unit) {
        if(expired) {
            throw OtpExpiredException()
        }

        if(attemptsExceeded) {
            throw OtpMaxAttemptsExceededException()
        }

        if(!hashMatches(storedHash, rawOtp)) {
            onMismatch()
            throw InvalidOtpException()
        }
    }

    private fun ensureCooldownElapsed(lastSentAt: Instant, now: Instant) {
        val elapsedSeconds = Duration.between(lastSentAt, now).seconds

        if(elapsedSeconds < resendCooldownSeconds) {
            throw OtpResendCooldownException(resendCooldownSeconds - elapsedSeconds)
        }
    }

    private fun sendMailAfterCommit(action: String, send: () -> Unit) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                try {
                    send()
                } catch(e: Exception) {
                    logger.error("\n\t[ERROR] [auth_service][{}] Error sending email after commit: {}", action, e.message)
                }
            }
        })
    }

    private fun otpExpiry(now: Instant): Instant =
        now.plus(otpTtlMinutes, ChronoUnit.MINUTES)

    private fun generateOtp(): String =
        secureRandom.nextInt(100000, 1000000).toString()

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashMatches(storedHash: String, raw: String): Boolean =
        MessageDigest.isEqual(storedHash.toByteArray(Charsets.UTF_8), hashValue(raw).toByteArray(Charsets.UTF_8))

    private fun hashValue(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}