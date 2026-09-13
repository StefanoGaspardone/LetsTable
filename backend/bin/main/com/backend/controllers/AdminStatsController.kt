package com.backend.controllers

import com.backend.models.dtos.AdminStatsDTO
import com.backend.services.AdminStatsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
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
        ]
    )
    @GetMapping
    fun getStats(): ResponseEntity<AdminStatsDTO> =
        ResponseEntity.ok(adminStatsService.getStats())
}