package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.exceptions.InvalidSchedulerTokenException
import com.backend.models.dtos.AppVersionDTO
import com.backend.models.dtos.CreateAppReleaseRequest
import com.backend.models.dtos.JobResultResponse
import com.backend.schedulers.HotGamesScheduler
import com.backend.schedulers.UserCleanupScheduler
import com.backend.schedulers.WishlistNotificationScheduler
import com.backend.services.AppReleaseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

@Tag(name = "Internal Jobs", description = "REST APIs triggered by Cloud Scheduler to run periodic maintenance jobs. Protected by a shared scheduler token.")
@RestController
@RequestMapping("/api/v1/internal/jobs")
class InternalJobController(
    private val hotGamesScheduler: HotGamesScheduler,
    private val userCleanupScheduler: UserCleanupScheduler,
    private val wishlistNotificationScheduler: WishlistNotificationScheduler,
    private val appReleaseService: AppReleaseService,
    @Value($$"${job.token:}") private val jobToken: String,
) {

    @Operation(summary = "Refresh hot games", description = "Fetch the current hot games list from BoardGameGeek and update the local cache. Triggered every 6 hours.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Hot games successfully refreshed",
                content = [Content(schema = Schema(implementation = JobResultResponse::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid scheduler token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidTokenExample",
                        summary = "Invalid scheduler token example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid scheduler token\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "500", description = "Internal Server Error - Refresh failed",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "RefreshFailedExample",
                        summary = "Refresh failed example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"An unexpected error occurred\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/refresh-hot-games")
    fun refreshHotGames(
        @Parameter(description = "Shared secret configured in Cloud Scheduler", required = true)
        @RequestHeader("X-Scheduler-Token", required = false) token: String?,
    ): ResponseEntity<JobResultResponse> {
        checkToken(token)
        hotGamesScheduler.refreshHotGames()
        return ResponseEntity.ok(JobResultResponse(job = "refresh-hot-games"))
    }

    @Operation(summary = "Cleanup inactive accounts", description = "Delete accounts still not activated more than 7 days after signup. Triggered every Sunday at 04:00.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Cleanup completed, returns the number of deleted accounts",
                content = [Content(schema = Schema(implementation = JobResultResponse::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid scheduler token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidTokenExample",
                        summary = "Invalid scheduler token example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid scheduler token\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "500", description = "Internal Server Error - Cleanup failed",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "CleanupFailedExample",
                        summary = "Cleanup failed example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"An unexpected error occurred\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/user-cleanup")
    fun userCleanup(
        @Parameter(description = "Shared secret configured in Cloud Scheduler", required = true)
        @RequestHeader("X-Scheduler-Token", required = false) token: String?,
    ): ResponseEntity<JobResultResponse> {
        checkToken(token)

        val deleted = userCleanupScheduler.deleteStaleInactiveAccounts()
        return ResponseEntity.ok(JobResultResponse(job = "user-cleanup", affected = deleted))
    }

    @Operation(summary = "Send wishlist notifications", description = "Group pending changes on shared wishlists and notify the other participants once the author has been idle for 2 minutes. Triggered every minute.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Pending activities processed, returns the number of notifications sent",
                content = [Content(schema = Schema(implementation = JobResultResponse::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid scheduler token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidTokenExample",
                        summary = "Invalid scheduler token example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid scheduler token\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "500", description = "Internal Server Error - Flush failed",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "FlushFailedExample",
                        summary = "Flush failed example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"An unexpected error occurred\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/wishlist-notifications")
    fun wishlistNotifications(
        @Parameter(description = "Shared secret configured in Cloud Scheduler", required = true)
        @RequestHeader("X-Scheduler-Token", required = false) token: String?,
    ): ResponseEntity<JobResultResponse> {
        checkToken(token)

        val sent = wishlistNotificationScheduler.flushWishlistNotifications()
        return ResponseEntity.ok(JobResultResponse(job = "wishlist-notifications", affected = sent.toLong()))
    }

    private fun checkToken(token: String?) {
        val valid = jobToken.isNotBlank() && token != null &&
                MessageDigest.isEqual(token.toByteArray(), jobToken.toByteArray())

        if(!valid) throw InvalidSchedulerTokenException()
    }

    @Operation(summary = "Publish app release", description = "Publish a new version of the mobile app. Triggered by the GitHub workflow when a release is published.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Release published",
                content = [Content(schema = Schema(implementation = AppVersionDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid version format or minSupportedVersion greater than version",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidMinVersionExample",
                        summary = "Min version greater than version example",
                        value = "{\"timestamp\":\"2026-11-03T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"minSupportedVersion cannot be greater than version\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid scheduler token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidTokenExample",
                        summary = "Invalid scheduler token example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid scheduler token\"}"
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
    @PostMapping("/app-release")
    fun publishAppRelease(
        @Parameter(description = "Shared secret configured in the GitHub workflow", required = true)
        @RequestHeader("X-Scheduler-Token", required = false) token: String?,
        @Valid @RequestBody request: CreateAppReleaseRequest,
    ): ResponseEntity<AppVersionDTO> {
        checkToken(token)
        return ResponseEntity.status(HttpStatus.CREATED).body(appReleaseService.createRelease(request))
    }
}