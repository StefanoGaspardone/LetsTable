package com.backend.models.dtos

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Application health status")
data class HealthResponse(
    @field:Schema(description = "Overall application status", example = "UP", allowableValues = ["UP", "DOWN"])
    val status: String,

    @field:Schema(description = "Database connectivity status", example = "UP", allowableValues = ["UP", "DOWN"])
    val database: String,
)