package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.PageDTO
import com.backend.services.GameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Games", description = "Search and browse games from BoardGameGeek")
@RestController
@RequestMapping("/api/v1/games")
class GameController(
    private val gameService: GameService,
) {

    @Operation(summary = "Search games", description = "Live search against BoardGameGeek. Results of the requested page are enriched with full details, cached for 7 days.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of search results",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Missing query parameter",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "MissingParameterExample",
                        summary = "Missing query example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Missing required parameter 'query'\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "502", description = "Bad Gateway - BoardGameGeek request failed",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "BggFailedExample",
                        summary = "BoardGameGeek unavailable example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":502,\"error\":\"Bad Gateway\",\"message\":\"Failed to reach BoardGameGeek API\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/search")
    fun search(
        @Parameter(description = "Text to search on BoardGameGeek", example = "Ark Nova") @RequestParam query: String,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageDTO<GameDTO>> =
        ResponseEntity.ok(gameService.search(query, page, size))

    @Operation(summary = "Hot games", description = "Trending games on BoardGameGeek, cached and refreshed periodically by a scheduled job.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of hot games ordered by trending rank",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
        ]
    )
    @GetMapping("/hot")
    fun hot(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageDTO<GameDTO>> =
        ResponseEntity.ok(gameService.getHotGames(page, size))

    @Operation(summary = "Overall ranking", description = "Games ranked by BoardGameGeek's overall rank, based on the manually uploaded rank index.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of games ordered by overall rank",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
        ]
    )
    @GetMapping("/overall")
    fun overall(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-50)") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageDTO<GameDTO>> =
        ResponseEntity.ok(gameService.getOverallGames(page, size))

    @Operation(summary = "Get game details", description = "Full details of a game, synced from BoardGameGeek if not cached or older than 7 days. Includes sleeves, collection status and, for expansions, the base game.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Game details",
                content = [Content(schema = Schema(implementation = GameDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid BoardGameGeek id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidIdExample",
                        summary = "Invalid path parameter example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'bggId'\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game does not exist on BoardGameGeek",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Game not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found on BoardGameGeek with id: 999999999\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "502", description = "Bad Gateway - BoardGameGeek request failed",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "BggFailedExample",
                        summary = "BoardGameGeek unavailable example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":502,\"error\":\"Bad Gateway\",\"message\":\"Failed to reach BoardGameGeek API\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{bggId}")
    fun getGame(
        @Parameter(description = "BoardGameGeek id of the game", example = "342942") @PathVariable bggId: Long,
    ): ResponseEntity<GameDTO> =
        ResponseEntity.ok(gameService.getOrSyncGame(bggId))

    @Operation(summary = "Get game expansions", description = "Expansions of a game already synced via GET /games/{bggId}, ordered by publication year and name. Expansions are synced from BoardGameGeek in batch.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of expansions",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game not synced or does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Game not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found on BoardGameGeek with id: 316554\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{bggId}/expansions")
    fun getExpansions(
        @Parameter(description = "BoardGameGeek id of the game", example = "316554") @PathVariable bggId: Long,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-50)") @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<PageDTO<GameDTO>> =
        ResponseEntity.ok(gameService.getExpansions(bggId, page, size))
}