package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.GameTermDTO
import com.backend.models.dtos.PageDTO
import com.backend.services.GameService
import com.backend.services.GameTermService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Games", description = "Search and browse games from BoardGameGeek")
@RestController
@RequestMapping("/api/v1/games")
class GameController(
    private val gameService: GameService,
    private val gameTermService: GameTermService
) {

    @Operation(summary = "Search games", description = "Without filters and with a query, runs a live search against BoardGameGeek. With an empty query or any filter/sort, searches the local catalog of synced games, alphabetically by default.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of games",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid sort field",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidSortExample",
                        summary = "Invalid sort example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field 'price'\"}"
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
        @Parameter(description = "Text to search, optional", example = "Ark Nova") @RequestParam(required = false) query: String?,
        @Parameter(description = "Filter by mechanic id") @RequestParam(required = false) mechanicId: UUID?,
        @Parameter(description = "Filter by category id") @RequestParam(required = false) categoryId: UUID?,
        @Parameter(description = "Filter expansions (true) or base games (false)") @RequestParam(required = false) isExpansion: Boolean?,
        @Parameter(description = "Sort as field-direction: name, yearPublished, bggRank, avgRating", example = "name-asc") @RequestParam(required = false) sort: String?,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageDTO<GameDTO>> =
        ResponseEntity.ok(gameService.search(query, mechanicId, categoryId, isExpansion, sort, page, size))

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

    @Operation(summary = "List mechanics", description = "All BoardGameGeek mechanics found on synced games, sorted by display name. Use the id to filter /search.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of mechanics",
                content = [Content(array = ArraySchema(schema = Schema(implementation = GameTermDTO::class)))]
            ),
        ]
    )
    @GetMapping("/mechanics")
    fun getMechanics(): ResponseEntity<List<GameTermDTO>> =
        ResponseEntity.ok(gameTermService.getMechanics())

    @Operation(summary = "List categories", description = "All BoardGameGeek categories found on synced games, sorted by display name. Use the id to filter /search.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of categories",
                content = [Content(array = ArraySchema(schema = Schema(implementation = GameTermDTO::class)))]
            ),
        ]
    )
    @GetMapping("/categories")
    fun getCategories(): ResponseEntity<List<GameTermDTO>> =
        ResponseEntity.ok(gameTermService.getCategories())
}