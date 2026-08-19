package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.GameSearchResultResponse
import com.backend.models.dtos.HotGameResponse
import com.backend.services.GameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@Tag(name = "Games", description = "Search and browse games from BoardGameGeek")
@RestController
@RequestMapping("/api/v1/games")
class GameController(
    private val gameService: GameService,
) {

    @Operation(summary = "Search games", description = "Live search against BoardGameGeek, always up to date.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Search results",
                content = [Content(schema = Schema(implementation = GameSearchResultResponse::class))]
            ),
        ]
    )
    @GetMapping("/search")
    fun search(@RequestParam query: String): List<GameSearchResultResponse> = gameService.search(query)

    @Operation(summary = "Hot games", description = "Trending games on BoardGameGeek, cached and refreshed every 6 hours.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Hot games ranking",
                content = [Content(schema = Schema(implementation = HotGameResponse::class))]
            ),
        ]
    )
    @GetMapping("/hot")
    fun hot(): List<HotGameResponse> = gameService.getHotGames()

    @Operation(summary = "Get game details", description = "Fetches full details for a game, syncing from BoardGameGeek if not cached or stale (older than 7 days).")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Game details",
                content = [Content(schema = Schema(implementation = GameSearchResultResponse::class))]
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
        ]
    )
    @GetMapping("/{bggId}")
    fun getGame(@PathVariable bggId: Long): GameDTO = gameService.getOrSyncGame(bggId)
}