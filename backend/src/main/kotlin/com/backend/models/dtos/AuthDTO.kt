package com.backend.models.dtos

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Payload for registering a new user account")
data class RegisterRequest(
    @field:Schema(description = "Unique username", example = "stivigas")
    @field:NotBlank
    @field:Size(min = 3, max = 50)
    val username: String,

    @field:Schema(description = "User email address", example = "user@example.com")
    @field:NotBlank
    @field:Email
    val email: String,

    @field:Schema(description = "Plain text password, will be hashed before storage")
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,
)

@Schema(description = "Payload to log in with email or username")
data class LoginRequest(
    @field:Schema(description = "Email or username", example = "user@example.com")
    @field:NotBlank
    val identifier: String,

    @field:Schema(description = "Plain text password")
    @field:NotBlank
    val password: String,
)

@Schema(description = "Payload to activate an account with an OTP code")
data class ActivateAccountRequest(
    @field:Schema(description = "Email or username of the account", example = "user@example.com")
    @field:NotBlank
    val identifier: String,

    @field:Schema(description = "6-digit verification code sent via email", example = "482913")
    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]{6}$")
    val otpCode: String,
)

@Schema(description = "Payload to resend an OTP code")
data class ResendOtpRequest(
    @field:Schema(description = "Email or username of the account", example = "user@example.com")
    @field:NotBlank
    val identifier: String,
)

@Schema(description = "Payload to refresh or revoke a session")
data class RefreshTokenRequest(
    @field:Schema(description = "Opaque refresh token previously issued at login")
    @field:NotBlank
    val refreshToken: String,
)

@Schema(description = "Payload to start a password reset")
data class ForgotPasswordRequest(
    @field:Schema(description = "Email or username of the account", example = "user@example.com")
    @field:NotBlank
    val identifier: String,
)

@Schema(description = "Payload to complete a password reset")
data class ResetPasswordRequest(
    @field:Schema(description = "Email or username of the account", example = "user@example.com")
    @field:NotBlank
    val identifier: String,

    @field:Schema(description = "6-digit verification code sent via email", example = "482913")
    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]{6}$")
    val otpCode: String,

    @field:Schema(description = "New plain text password")
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val newPassword: String,
)

@Schema(description = "Access and refresh token pair returned after successful authentication")
data class AuthResponse(
    @field:Schema(description = "Short-lived JWT used to authenticate API requests")
    val accessToken: String,

    @field:Schema(description = "Long-lived opaque token used to obtain a new access token")
    val refreshToken: String,

    @field:Schema(description = "Authenticated user's public profile")
    val user: UserDTO,
)

@Schema(description = "New token pair returned after a successful refresh")
data class RefreshResponse(
    @field:Schema(description = "New short-lived JWT")
    val accessToken: String,

    @field:Schema(description = "New long-lived opaque token, replaces the one used in the request")
    val refreshToken: String,
)

@Schema(description = "Response returned after signup, before the account is activated")
data class SignupResponse(
    @field:Schema(description = "Email address the verification code was sent to")
    val email: String,

    @field:Schema(description = "Human-readable instructions for the client")
    val message: String,
)