package com.backend.models.dtos

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Payload to register a device for push notifications")
data class RegisterPushTokenRequest(
    @field:Schema(description = "Expo push token for this device", example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]")
    @field:NotBlank
    val token: String,

    @field:Schema(description = "Optional human-readable device name", example = "Mario's Pixel 8")
    val deviceName: String? = null,
)