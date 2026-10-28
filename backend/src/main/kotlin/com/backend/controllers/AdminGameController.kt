package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AdminGameDTO
import com.backend.models.dtos.AdminUploadedFileDTO
import com.backend.models.dtos.BggRankIndexDTO
import com.backend.models.dtos.PageDTO
import com.backend.services.AdminGameService
import com.backend.services.BggRankIndexService
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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "Admin - Games", description = "Administrative endpoints for managing the BGG game cache")
@RestController
@RequestMapping("/api/v1/admin/games")
@PreAuthorize("hasRole('ADMIN')")
class AdminGameController(
    private val adminGameService: AdminGameService,
    private val bggRankIndexService: BggRankIndexService
) {

    @Operation(summary = "Force refresh a game", description = "Forces a fresh sync of a single game from BoardGameGeek, bypassing the normal staleness check.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Game refreshed",
                content = [Content(schema = Schema(implementation = AdminGameDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid BGG id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidIdExample",
                        summary = "Invalid path parameter example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'bggId'\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game does not exist on BoardGameGeek",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "GameNotFoundOnBggExample",
                        summary = "Game not found on BGG example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found on BoardGameGeek with id: 999999999\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "502", description = "Bad Gateway - BoardGameGeek API unreachable",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "BggUnreachableExample",
                        summary = "BGG request failed example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":502,\"error\":\"Bad Gateway\",\"message\":\"Failed to reach BoardGameGeek API\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/{bggId}/refresh")
    fun forceRefreshGame(@Parameter(description = "BoardGameGeek internal id") @PathVariable bggId: Long): ResponseEntity<AdminGameDTO> =
        ResponseEntity.ok(adminGameService.forceRefreshGame(bggId))

    @Operation(summary = "Force refresh hot games cache", description = "Forces an immediate refresh of the trending games cache from BoardGameGeek, instead of waiting for the scheduled job.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Hot games cache refreshed successfully",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "502", description = "Bad Gateway - BoardGameGeek API unreachable",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "BggUnreachableExample",
                        summary = "BGG request failed example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":502,\"error\":\"Bad Gateway\",\"message\":\"Failed to reach BoardGameGeek API\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/hot-games/refresh")
    fun forceRefreshHotGames(): ResponseEntity<Unit> {
        adminGameService.forceRefreshHotGames()
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List rule files for a game", description = "Lists all rulebook PDFs uploaded for a specific game, for moderation purposes.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of rule files",
                content = [Content(array = ArraySchema(schema = Schema(implementation = AdminUploadedFileDTO::class)))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid game id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidIdExample",
                        summary = "Invalid path parameter example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'gameId'\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "GameNotFoundExample",
                        summary = "Game not found example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{gameId}/rules")
    fun listRuleFiles(@Parameter(description = "Internal Let's Table id of the game") @PathVariable gameId: UUID): ResponseEntity<List<AdminUploadedFileDTO>> =
        ResponseEntity.ok(adminGameService.listRuleFiles(gameId))

    @Operation(summary = "Delete a rule file", description = "Deletes an uploaded rulebook PDF for a game, both from storage and from the database.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Rule file deleted successfully",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid game or file id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidIdExample",
                        summary = "Invalid path parameter example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'fileId'\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game or file does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "GameNotFoundExample",
                            summary = "Game not found example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                        ExampleObject(
                            name = "FileNotFoundExample",
                            summary = "File not found example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"File not found: 7c9e6679-7425-40de-944b-e07fc1f90ae7\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @DeleteMapping("/{gameId}/rules/{fileId}")
    fun deleteRuleFile(
        @Parameter(description = "Internal Let's Table id of the game") @PathVariable gameId: UUID,
        @Parameter(description = "ID of the uploaded file") @PathVariable fileId: UUID,
    ): ResponseEntity<Unit> {
        adminGameService.deleteRuleFile(gameId, fileId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List cached games", description = "Returns a paginated list of games stored in the local BGG cache, with optional name search, expansion filter and sorting.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of cached games",
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
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field: popularity\"}"
                        ),
                        ExampleObject(
                            name = "InvalidParameterExample",
                            summary = "Invalid query parameter example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'isExpansion'\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping
    fun listGames(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Search by game name") @RequestParam(required = false) search: String?,
        @Parameter(description = "True for expansions only, false for base games only, omitted for both") @RequestParam(required = false) isExpansion: Boolean?,
        @Parameter(description = "Sort field and direction, e.g. 'name-asc', 'rank-desc', 'lastSyncedAt-desc', 'isExpansion-asc'") @RequestParam(required = false) sort: String?,
    ): ResponseEntity<PageDTO<AdminGameDTO>> =
        ResponseEntity.ok(adminGameService.listGames(page, size, search, isExpansion, sort))

    @Operation(summary = "Upload BGG rank index", description = "Uploads the official BoardGameGeek rank export CSV file (downloaded and extracted manually from boardgamegeek.com/data_dumps/bg_ranks) to refresh the internal rank index used for the 'Overall' ranking.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Rank index refreshed",
                content = [Content(schema = Schema(implementation = BggRankIndexDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Missing, invalid or unparseable file",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidRankIndexFileExample",
                            summary = "Unparseable CSV example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Missing required column: rank\"}"
                        ),
                        ExampleObject(
                            name = "InvalidFileTypeExample",
                            summary = "Wrong file type example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Only CSV files are allowed\"}"
                        ),
                        ExampleObject(
                            name = "MissingFileExample",
                            summary = "Missing file example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Required part 'file' is not present.\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "413", description = "Payload Too Large - File exceeds the maximum upload size",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "PayloadTooLargeExample",
                        summary = "File too large example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":413,\"error\":\"Payload Too Large\",\"message\":\"Uploaded file exceeds the maximum allowed size\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/rank-index/upload", consumes = ["multipart/form-data"])
    fun uploadRankIndex(
        @Parameter(description = "BGG rank export CSV file") @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<BggRankIndexDTO> =
        ResponseEntity.ok(bggRankIndexService.uploadRankIndex(file))

    @Operation(summary = "Get game details", description = "Retrieves the full administrative details of a single cached game by its internal id.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Game details",
                content = [Content(schema = Schema(implementation = AdminGameDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid game id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidIdExample",
                        summary = "Invalid path parameter example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'gameId'\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "GameNotFoundExample",
                        summary = "Game not found example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{gameId}")
    fun getGame(@Parameter(description = "Internal Let's Table id of the game") @PathVariable gameId: UUID): ResponseEntity<AdminGameDTO> =
        ResponseEntity.ok(adminGameService.getGame(gameId))
}