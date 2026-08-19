package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.CreateMatchRequest
import com.backend.models.dtos.MatchDTO
import com.backend.security.CurrentUser
import com.backend.services.MatchService
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
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Matches", description = "Log and manage board game matches, individual or team-based")
@RestController
@RequestMapping("/api/v1/matches")
@PreAuthorize("hasRole('USER')")
class MatchController(
    private val matchService: MatchService,
) {

    @Operation(summary = "Create match", description = "Log a new match. Provide either 'teams' (if isTeamBased is true) or 'players' (if false), never both.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Match logged",
                content = [Content(schema = Schema(implementation = MatchDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid teams/players payload",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidTeamsExample",
                        summary = "Missing teams example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"At least one team is required when isTeamBased is true\"}"
                    ), ExampleObject(
                        name = "InvalidIdentityExample",
                        summary = "Invalid player identity example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Each player must have exactly one of userId or guestName\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game or referenced user does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "GameNotFoundExample",
                        summary = "Game not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping
    fun createMatch(@Valid @RequestBody request: CreateMatchRequest): ResponseEntity<MatchDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(matchService.createMatch(CurrentUser.id(), request))

    @Operation(summary = "Update match", description = "Fully replace a match's data, including its teams/players. Only the creator can update it.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Match updated",
                content = [Content(schema = Schema(implementation = MatchDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid teams/players payload",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidTeamsExample",
                        summary = "Missing teams example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"At least one team is required when isTeamBased is true\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the creator can update this match",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotCreatorExample",
                        summary = "Not the creator example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the match creator can perform this action\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Match, game or referenced user does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "MatchNotFoundExample",
                        summary = "Match not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Match not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PutMapping("/{matchId}")
    fun updateMatch(
        @PathVariable matchId: UUID,
        @Valid @RequestBody request: CreateMatchRequest,
    ): ResponseEntity<MatchDTO> =
        ResponseEntity.ok(matchService.updateMatch(CurrentUser.id(), matchId, request))

    @Operation(summary = "Delete match", description = "Delete a match. Only the creator can delete it.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Match deleted",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the creator can delete this match",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotCreatorExample",
                        summary = "Not the creator example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the match creator can perform this action\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Match does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Match not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Match not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/{matchId}")
    fun deleteMatch(@PathVariable matchId: UUID): ResponseEntity<Void> {
        matchService.deleteMatch(CurrentUser.id(), matchId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Get match", description = "Get details of any match by id. Matches are publicly viewable by any authenticated user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Match details",
                content = [Content(schema = Schema(implementation = MatchDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Match does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Match not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Match not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{matchId}")
    fun getMatch(@PathVariable matchId: UUID): MatchDTO = matchService.getMatch(matchId)

    @Operation(summary = "List my matches", description = "List all matches created by, or involving as a player, the current user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of matches",
                content = [Content(schema = Schema(implementation = MatchDTO::class))]
            ),
        ]
    )
    @GetMapping
    fun listMyMatches(): List<MatchDTO> = matchService.listMyMatches(CurrentUser.id())
}