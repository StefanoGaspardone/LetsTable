package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.ActivateAccountRequest
import com.backend.models.dtos.AuthResponse
import com.backend.models.dtos.ForgotPasswordRequest
import com.backend.models.dtos.LoginRequest
import com.backend.models.dtos.RefreshResponse
import com.backend.models.dtos.RefreshTokenRequest
import com.backend.models.dtos.RegisterRequest
import com.backend.models.dtos.ResendOtpRequest
import com.backend.models.dtos.ResetPasswordRequest
import com.backend.models.dtos.SignupResponse
import com.backend.services.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "REST API for authentication: signup, activation, login, token refresh and password reset")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

    @Operation(summary = "Signup", description = "Create a new account and send a verification code via email. The account must be activated before logging in.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Account created, verification code sent",
                content = [Content(schema = Schema(implementation = SignupResponse::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid signup data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidDataExample",
                        summary = "Validation error example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid request data\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Email or username already taken",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ConflictExample",
                        summary = "Email already taken example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Email already taken: mario.rossi@example.com\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: RegisterRequest): ResponseEntity<SignupResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request))

    @Operation(summary = "Activate account", description = "Verify the OTP sent via email and activate the account. Does not log the user in — call /login afterwards.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Account successfully activated",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - OTP is invalid or expired",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidOtpExample",
                        summary = "Invalid OTP example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid OTP code\"}"
                    ), ExampleObject(
                        name = "ExpiredOtpExample",
                        summary = "Expired OTP example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"OTP code expired\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - No pending verification for this account",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "No pending verification example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Email verification not found for user: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "429", description = "Too Many Requests - Maximum verification attempts exceeded",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "MaxAttemptsExample",
                        summary = "Max attempts exceeded example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Maximum OTP attempts exceeded\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/activate")
    fun activate(@Valid @RequestBody request: ActivateAccountRequest): ResponseEntity<Void> {
        authService.activateAccount(request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Resend OTP", description = "Request a new verification code. Subject to a cooldown between requests.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - New verification code sent",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - No pending verification for this account",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "No pending verification example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Email verification not found for user: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "429", description = "Too Many Requests - Resend cooldown still active",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "CooldownExample",
                        summary = "Resend cooldown example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"OTP resend cooldown active, try again in 42 seconds\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/activate/resend")
    fun resendActivationOtp(@Valid @RequestBody request: ResendOtpRequest): ResponseEntity<Void> {
        authService.resendActivationOtp(request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Login", description = "Authenticate with email or username and password, returns access and refresh tokens.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Successfully authenticated",
                content = [Content(schema = Schema(implementation = AuthResponse::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Invalid credentials",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidCredentialsExample",
                        summary = "Invalid credentials example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid credentials\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Account is not activated",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AccountNotActivatedExample",
                        summary = "Account not activated example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Account is not activated\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.login(request))

    @Operation(summary = "Refresh access token", description = "Exchange a valid refresh token for a new access token and a rotated refresh token.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Successfully refreshed",
                content = [Content(schema = Schema(implementation = RefreshResponse::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Refresh token is invalid, expired or revoked",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidRefreshTokenExample",
                        summary = "Invalid refresh token example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Refresh token expired or revoked\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Account is not activated",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AccountNotActivatedExample",
                        summary = "Account not activated example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Account is not activated\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<RefreshResponse> =
        ResponseEntity.ok(authService.refresh(request.refreshToken))

    @Operation(summary = "Logout", description = "Revoke the given refresh token.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Successfully logged out",
                content = [Content(schema = Schema(hidden = true))]
            ),
        ]
    )
    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<Void> {
        authService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Forgot password", description = "Request a password reset code. Always returns 204 regardless of whether the account exists, to avoid revealing registered emails.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - If an account exists, a reset code has been sent",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "429", description = "Too Many Requests - Resend cooldown still active",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "CooldownExample",
                        summary = "Resend cooldown example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"OTP resend cooldown active, try again in 42 seconds\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/password/forgot")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<Void> {
        authService.forgotPassword(request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Reset password", description = "Verify the OTP and set a new password.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Password successfully reset",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - OTP is invalid or expired",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidOtpExample",
                        summary = "Invalid OTP example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid OTP code\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - No pending reset for this account",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "No pending reset example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Password reset not found for user: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "429", description = "Too Many Requests - Maximum verification attempts exceeded",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "MaxAttemptsExample",
                        summary = "Max attempts exceeded example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Maximum OTP attempts exceeded\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/password/reset")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Void> {
        authService.resetPassword(request)
        return ResponseEntity.noContent().build()
    }
}