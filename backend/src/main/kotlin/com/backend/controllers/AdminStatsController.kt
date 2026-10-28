package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AdminStatsDTO
import com.backend.services.AdminStatsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Stats", description = "Administrative endpoint for aggregated application statistics")
@RestController
@RequestMapping("/api/v1/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
class AdminStatsController(
    private val adminStatsService: AdminStatsService,
) {

    @Operation(summary = "Get aggregated statistics", description = "Returns aggregate counts and rankings about users, matches, and games across the application.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Aggregated statistics",
                content = [Content(schema = Schema(implementation = AdminStatsDTO::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping
    fun getStats(): ResponseEntity<AdminStatsDTO> =
        ResponseEntity.ok(adminStatsService.getStats())
}