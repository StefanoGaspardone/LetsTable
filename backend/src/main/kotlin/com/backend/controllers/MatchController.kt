package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.CreateMatchRequest
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.GameMatchStatsDTO
import com.backend.models.dtos.MatchDTO
import com.backend.models.dtos.MatchDayCountDTO
import com.backend.models.dtos.MatchWinStatsDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.dtos.UpdateMatchRequest
import com.backend.security.CurrentUser
import com.backend.services.MatchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@Tag(name = "Matches", description = "Log and manage board game matches, individual or team-based")
@RestController
@RequestMapping("/api/v1/matches")
@PreAuthorize("hasRole('USER')")
class MatchController(
    private val matchService: MatchService,
) {

    @Operation(summary = "Create match", description = "Log a new match. Provide either 'teams' (if isTeamBased is true) or 'players' (if false), never both. Each registered user can appear only once.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Match logged",
                content = [Content(schema = Schema(implementation = MatchDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid teams/players payload, invalid expansion or invalid request data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidTeamsExample",
                            summary = "Missing teams example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"At least one team is required when isTeamBased is true\"}"
                        ),
                        ExampleObject(
                            name = "InvalidIdentityExample",
                            summary = "Invalid player identity example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Each player must have exactly one of userId or guestName\"}"
                        ),
                        ExampleObject(
                            name = "DuplicateUserExample",
                            summary = "Duplicate user example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Each registered user can appear only once in a match\"}"
                        ),
                        ExampleObject(
                            name = "InvalidExpansionExample",
                            summary = "Expansion of another game example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Expansion 3fa85f64-5717-4562-b3fc-2c963f66afa6 does not belong to the selected game\"}"
                        ),
                        ExampleObject(
                            name = "ValidationExample",
                            summary = "Validation error example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"durationMinutes: must be greater than or equal to 0\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game, expansion or referenced user does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "GameNotFoundExample",
                            summary = "Game not found example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                        ExampleObject(
                            name = "UserNotFoundExample",
                            summary = "User not found example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found for identifier: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @PostMapping
    fun createMatch(@Valid @RequestBody request: CreateMatchRequest): ResponseEntity<MatchDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(matchService.createMatch(CurrentUser.id(), request))

    @Operation(summary = "Update match", description = "Fully replace a match's data, including its teams/players. Only the creator can update it. If the match was in progress and no duration is given, the elapsed time since creation is used.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Match updated",
                content = [Content(schema = Schema(implementation = MatchDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid teams/players payload, invalid expansion or invalid request data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidTeamsExample",
                            summary = "Missing teams example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"At least one team is required when isTeamBased is true\"}"
                        ),
                        ExampleObject(
                            name = "DuplicateUserExample",
                            summary = "Duplicate user example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Each registered user can appear only once in a match\"}"
                        ),
                    ]
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
                        value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the match creator can perform this action\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Match, game, expansion or referenced user does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "MatchNotFoundExample",
                        summary = "Match not found example",
                        value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Match not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PutMapping("/{matchId}")
    fun updateMatch(
        @Parameter(description = "Match id") @PathVariable matchId: UUID,
        @Valid @RequestBody request: UpdateMatchRequest,
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
                        value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the match creator can perform this action\"}"
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
                        value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Match not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/{matchId}")
    fun deleteMatch(@Parameter(description = "Match id") @PathVariable matchId: UUID): ResponseEntity<Unit> {
        matchService.deleteMatch(CurrentUser.id(), matchId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Get match", description = "Get details of any match by id. Matches are viewable by any authenticated user.")
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
                        value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Match not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{matchId}")
    fun getMatch(@Parameter(description = "Match id") @PathVariable matchId: UUID): ResponseEntity<MatchDTO> =
        ResponseEntity.ok(matchService.getMatch(matchId))

    @Operation(summary = "List my matches", description = "Paginated list of matches created by, or involving as a player, the current user. Supports filtering by game and date range, e.g. to power a calendar day view.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of matches",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid sort field or query parameter",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidSortExample",
                            summary = "Invalid sort field example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field: unknownField\"}"
                        ),
                        ExampleObject(
                            name = "InvalidDateExample",
                            summary = "Invalid date example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'fromDate'\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @GetMapping
    fun listMyMatches(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Filter by a specific game") @RequestParam(required = false) gameId: UUID?,
        @Parameter(description = "Only matches played on or after this date (yyyy-MM-dd)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: LocalDate?,
        @Parameter(description = "Only matches played on or before this date (yyyy-MM-dd)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: LocalDate?,
        @Parameter(description = "Sort field and direction: playedAt, createdAt or durationMinutes, followed by -asc or -desc, e.g. 'durationMinutes-desc'. In-progress matches (no duration) are always listed last when sorting by duration.") @RequestParam(required = false) sort: String?,
    ): ResponseEntity<PageDTO<MatchDTO>> =
        ResponseEntity.ok(matchService.listMyMatches(CurrentUser.id(), page, size, gameId, fromDate, toDate, sort))

    @Operation(summary = "Match calendar", description = "Number of matches played per day in a given month, for the current user (created or played). Use this to render a calendar view, then call GET /matches with matching fromDate/toDate to list the matches of a specific day.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Match counts per day",
                content = [Content(array = ArraySchema(schema = Schema(implementation = MatchDayCountDTO::class)))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Missing or invalid year/month",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidMonthExample",
                            summary = "Month out of range example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"month: must be less than or equal to 12\"}"
                        ),
                        ExampleObject(
                            name = "MissingParameterExample",
                            summary = "Missing month example",
                            value = "{\"timestamp\":\"2026-08-20T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Missing required parameter 'month'\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @GetMapping("/calendar")
    fun getMatchCalendar(
        @Parameter(description = "Calendar year", example = "2026") @RequestParam year: Int,
        @Parameter(description = "Calendar month, 1-12", example = "8") @RequestParam @Min(1) @Max(12) month: Int,
    ): ResponseEntity<List<MatchDayCountDTO>> =
        ResponseEntity.ok(matchService.getMatchCalendar(CurrentUser.id(), year, month))

    @Operation(summary = "Recent games", description = "Distinct games played in the user's last 10 matches (created or participated), most recent first. Not paginated.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of recent games",
                content = [Content(array = ArraySchema(schema = Schema(implementation = GameDTO::class)))]
            ),
        ]
    )
    @GetMapping("/recent-games")
    fun getRecentGames(): ResponseEntity<List<GameDTO>> =
        ResponseEntity.ok(matchService.getRecentGames(CurrentUser.id()))

    @Operation(summary = "Win statistics", description = "Total completed matches and wins for the current user, across individual and team matches.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Win statistics",
                content = [Content(schema = Schema(implementation = MatchWinStatsDTO::class))]
            ),
        ]
    )
    @GetMapping("/win-stats")
    fun getWinStats(): ResponseEntity<MatchWinStatsDTO> =
        ResponseEntity.ok(matchService.getWinStats(CurrentUser.id()))

    @Operation(summary = "Game statistics", description = "Paginated per-game statistics for the current user (created or played), counting completed matches only: number of matches, total and average time played, wins, win rate and last played date.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of per-game statistics",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid sort field",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidSortExample",
                        summary = "Invalid sort field example",
                        value = "{\"timestamp\":\"2026-10-03T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field: unknownField\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/game-stats")
    fun listGameStats(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Sort field and direction: matchCount, totalMinutes, avgMinutes, winRate, lastPlayedAt or gameName, followed by -asc or -desc. Default is matchCount-desc.") @RequestParam(required = false) sort: String?,
    ): ResponseEntity<PageDTO<GameMatchStatsDTO>> =
        ResponseEntity.ok(matchService.listGameStats(CurrentUser.id(), page, size, sort))
}