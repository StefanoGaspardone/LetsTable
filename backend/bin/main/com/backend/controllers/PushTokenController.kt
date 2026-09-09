package com.backend.controllers

import com.backend.models.dtos.RegisterPushTokenRequest
import com.backend.exceptions.ErrorResponse
import com.backend.security.CurrentUser
import com.backend.services.PushTokenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Push Tokens", description = "Register and unregister devices for push notifications")
@RestController
@RequestMapping("/api/v1/push-tokens")
@PreAuthorize("hasRole('USER')")
class PushTokenController(
    private val pushTokenService: PushTokenService,
) {

    @Operation(
        summary = "Register push token",
        description = "Register or update the Expo push token for the current device. If the token already exists (e.g. previously registered by another user on a shared or reset device), it is reassigned to the current user."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Push token registered",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid or missing token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidDataExample",
                        summary = "Validation error example",
                        value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid request data\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Current user does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UserNotFoundExample",
                        summary = "User not found example",
                        value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping
    fun register(@Valid @RequestBody request: RegisterPushTokenRequest): ResponseEntity<Void> {
        pushTokenService.register(CurrentUser.id(), request)
        return ResponseEntity.status(201).build()
    }

    @Operation(
        summary = "Unregister push token",
        description = "Remove a push token, typically called on logout so the device stops receiving notifications for this account."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Push token unregistered (or did not exist)",
                content = [Content(schema = Schema(hidden = true))]
            ),
        ]
    )
    @DeleteMapping
    fun unregister(@RequestParam @NotBlank token: String): ResponseEntity<Void> {
        pushTokenService.unregister(token)
        return ResponseEntity.noContent().build()
    }
}