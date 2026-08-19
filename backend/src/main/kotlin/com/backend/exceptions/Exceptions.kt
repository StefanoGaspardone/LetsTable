package com.backend.exceptions

import java.util.UUID

class EmailAlreadyTakenException(email: String): RuntimeException("Email already taken: $email")

class UsernameAlreadyTakenException(username: String): RuntimeException("Username already taken: $username")

class InvalidCredentialsException: RuntimeException("Invalid credentials")

class AccountNotActivatedException: RuntimeException("Account is not activated")

class UserNotFoundByIdentifierException(identifier: String): RuntimeException("User not found for identifier: $identifier")

class EmailVerificationNotFoundException(userId: UUID) : RuntimeException("Email verification not found for user: $userId")

class PasswordResetNotFoundException(userId: UUID): RuntimeException("Password reset not found for user: $userId")

class InvalidOtpException: RuntimeException("Invalid OTP code")

class OtpExpiredException: RuntimeException("OTP code expired")

class OtpMaxAttemptsExceededException: RuntimeException("Maximum OTP attempts exceeded")

class OtpResendCooldownException(secondsRemaining: Long): RuntimeException("OTP resend cooldown active, try again in $secondsRemaining seconds")

class RefreshTokenNotFoundException: RuntimeException("Refresh token not found")

class RefreshTokenExpiredOrRevokedException: RuntimeException("Refresh token expired or revoked")

class BggRequestFailedException(cause: Throwable) : RuntimeException("Failed to reach BoardGameGeek API", cause)

class GameNotFoundOnBggException(bggId: Long) : RuntimeException("Game not found on BoardGameGeek with id: $bggId")