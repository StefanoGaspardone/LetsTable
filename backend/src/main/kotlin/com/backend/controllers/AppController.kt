package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AppVersionDTO
import com.backend.services.AppReleaseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "App", description = "Public information about the mobile app, used to check for updates")
@RestController
@RequestMapping("/api/v1/app")
class AppController(
    private val appReleaseService: AppReleaseService,
) {

    @Operation(summary = "Latest app version", description = "Latest published version of the mobile app, the minimum supported version, the APK download URL and the release notes. Public endpoint, no authentication required.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Latest app version",
                content = [Content(schema = Schema(implementation = AppVersionDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - No version published yet",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NoReleaseExample",
                        summary = "No release published example",
                        value = "{\"timestamp\":\"2026-11-03T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"No app release published yet\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/version")
    fun getLatestVersion(): ResponseEntity<AppVersionDTO> =
        ResponseEntity.ok(appReleaseService.getLatestRelease())
}