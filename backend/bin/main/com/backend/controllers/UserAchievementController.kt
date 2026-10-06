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
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Achievements", description = "Achievements unlocked by playing, winning, collecting and making friends")
@RestController
@RequestMapping("/api/v1/achievements")
@PreAuthorize("hasRole('USER')")
class AchievementController(
    private val userAchievementService: UserAchievementService,
) {

    @Operation(
        summary = "Get my achievements",
        description = "Returns every achievement in the catalog with the authenticated user's progress and whether it is unlocked."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Achievements with progress",
                content = [Content(array = ArraySchema(schema = Schema(implementation = UserAchievementDTO::class)))]
            ),
        ]
    )
    @GetMapping("/me")
    fun getMyAchievements(): List<UserAchievementDTO> =
        userAchievementService.getUserAchievements(CurrentUser.id())

    @Operation(
        summary = "Get unseen achievements",
        description = "Returns the authenticated user's achievements that are unlocked but not yet acknowledged, oldest first. " +
                "Any achievement reached but not yet received is unlocked before answering. " +
                "The app calls it on launch to show the celebration screen, then confirms with the seen endpoint."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Unseen achievements, empty if there is none",
                content = [Content(array = ArraySchema(schema = Schema(implementation = UserAchievementDTO::class)))]
            ),
        ]
    )
    @GetMapping("/unseen")
    fun getUnseenAchievements(): List<UserAchievementDTO> =
        userAchievementService.getUnseenAchievements()

    @Operation(
        summary = "Mark achievements as seen",
        description = "Marks the given achievements as acknowledged for the authenticated user. Unknown or already seen codes are ignored."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "No Content - Achievements marked as seen"),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Empty list of codes",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ValidationError",
                        summary = "Validation error example",
                        value = "{\"timestamp\":\"2026-10-06T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"codes: must not be empty\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markSeen(@Valid @RequestBody request: MarkAchievementsSeenRequest) =
        userAchievementService.markSeen(request.codes)
}