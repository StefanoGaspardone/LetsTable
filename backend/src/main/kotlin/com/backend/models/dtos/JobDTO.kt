package com.backend.models.dtos

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Result of an internal scheduled job execution")
data class JobResultResponse(
    @field:Schema(description = "Job identifier", example = "user-cleanup")
    val job: String,

    @field:Schema(description = "Number of affected records, if applicable", example = "3", nullable = true)
    val affected: Long? = null,
)