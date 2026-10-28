package com.backend.controllers

import com.backend.models.dtos.HealthResponse
import com.backend.services.HealthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Health", description = "Application health check used by uptime monitoring")
@RestController
@RequestMapping("/api/v1/health")
class HealthController(
    private val healthService: HealthService,
) {

    @Operation(summary = "Health check", description = "Checks that the application is running and the database is reachable. Public endpoint, no authentication required.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Application and database are up",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = HealthResponse::class),
                    examples = [ExampleObject(
                        name = "UpExample",
                        summary = "Healthy example",
                        value = "{\"status\":\"UP\",\"database\":\"UP\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "503", description = "Service Unavailable - Database is unreachable",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = HealthResponse::class),
                    examples = [ExampleObject(
                        name = "DownExample",
                        summary = "Database down example",
                        value = "{\"status\":\"DOWN\",\"database\":\"DOWN\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping
    fun checkHealth(): ResponseEntity<HealthResponse> {
        val health = healthService.checkHealth()
        val status = if(health.status == "UP") HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE

        return ResponseEntity.status(status).body(health)
    }
}