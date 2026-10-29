package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.MarkAchievementsSeenRequest
import com.backend.models.dtos.UserAchievementDTO
import com.backend.security.CurrentUser
import com.backend.services.UserAchievementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "User Achievements", description = "Achievements unlocked by playing, winning, collecting and making friends")
@RestController
@RequestMapping("/api/v1/achievements")
@PreAuthorize("hasRole('USER')")
class UserAchievementController(
    private val userAchievementService: UserAchievementService,
) {

    @Operation(summary = "Get my achievements", description = "Every achievement in the catalog with the current user's progress and unlock status. Achievements reached but not yet unlocked are unlocked before answering.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Achievements with progress",
                content = [Content(array = ArraySchema(schema = Schema(implementation = UserAchievementDTO::class)))]
            ),
        ]
    )
    @GetMapping("/me")
    fun getMyAchievements(): ResponseEntity<List<UserAchievementDTO>> =
        ResponseEntity.ok(userAchievementService.getUserAchievements(CurrentUser.id(), evaluate = true))

    @Operation(summary = "Get unseen achievements", description = "The current user's achievements that are unlocked but not yet acknowledged, oldest first. Achievements reached but not yet unlocked are unlocked before answering. The app calls it on launch to show the celebration screen, then confirms with the seen endpoint.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Unseen achievements, empty if there is none",
                content = [Content(array = ArraySchema(schema = Schema(implementation = UserAchievementDTO::class)))]
            ),
        ]
    )
    @GetMapping("/unseen")
    fun getUnseenAchievements(): ResponseEntity<List<UserAchievementDTO>> =
        ResponseEntity.ok(userAchievementService.getUnseenAchievements(CurrentUser.id()))

    @Operation(summary = "Mark achievements as seen", description = "Marks the given achievements as acknowledged for the current user. Unknown or already seen codes are ignored.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Achievements marked as seen",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Empty list of codes or malformed body",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "ValidationExample",
                            summary = "Empty codes example",
                            value = "{\"timestamp\":\"2026-10-06T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"codes: must not be empty\"}"
                        ),
                        ExampleObject(
                            name = "MalformedBodyExample",
                            summary = "Malformed JSON example",
                            value = "{\"timestamp\":\"2026-10-06T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @PostMapping("/seen")
    fun markSeen(@Valid @RequestBody request: MarkAchievementsSeenRequest): ResponseEntity<Unit> {
        userAchievementService.markSeen(CurrentUser.id(), request.codes)
        return ResponseEntity.noContent().build()
    }
}