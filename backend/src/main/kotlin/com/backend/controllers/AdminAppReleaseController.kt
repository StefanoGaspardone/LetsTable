package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AppVersionDTO
import com.backend.models.dtos.CreateAppReleaseRequest
import com.backend.services.AppReleaseService
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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin App Releases", description = "Publish new versions of the mobile app. Admin only.")
@RestController
@RequestMapping("/api/v1/admin/app-releases")
@PreAuthorize("hasRole('ADMIN')")
class AdminAppReleaseController(
    private val appReleaseService: AppReleaseService,
) {

    @Operation(summary = "Publish app release", description = "Publish a new version of the mobile app. Clients older than minSupportedVersion are forced to update, older than version are invited to update.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Release published",
                content = [Content(schema = Schema(implementation = AppVersionDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid version format, URL or minSupportedVersion greater than version",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidMinVersionExample",
                            summary = "Min version greater than version example",
                            value = "{\"timestamp\":\"2026-11-03T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"minSupportedVersion cannot be greater than version\"}"
                        ),
                        ExampleObject(
                            name = "ValidationExample",
                            summary = "Invalid version format example",
                            value = "{\"timestamp\":\"2026-11-03T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"version: must be in MAJOR.MINOR.PATCH format\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Admin role required",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Not an admin example",
                        value = "{\"timestamp\":\"2026-11-03T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Version already published",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AlreadyExistsExample",
                        summary = "Duplicate version example",
                        value = "{\"timestamp\":\"2026-11-03T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"App release 2.3.0 already exists\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping
    fun createRelease(@Valid @RequestBody request: CreateAppReleaseRequest): ResponseEntity<AppVersionDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(appReleaseService.createRelease(request))
}