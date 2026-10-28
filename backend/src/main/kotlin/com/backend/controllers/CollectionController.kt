package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AddToCollectionRequest
import com.backend.models.dtos.CollectionItemDTO
import com.backend.models.dtos.CollectionStatusDTO
import com.backend.models.dtos.PageDTO
import com.backend.security.CurrentUser
import com.backend.services.CollectionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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

@Tag(name = "Collection", description = "Manage the current user's owned games collection")
@RestController
@RequestMapping("/api/v1/collection")
@PreAuthorize("hasRole('USER')")
class CollectionController(
    private val collectionService: CollectionService,
) {

    @Operation(summary = "Add game to collection", description = "Add a game (already synced via GET /games/{bggId}) to the current user's owned games collection.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Game added to collection",
                content = [Content(schema = Schema(implementation = CollectionItemDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid or malformed request data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidDataExample",
                            summary = "Validation error example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"gameId: must not be null\"}"
                        ),
                        ExampleObject(
                            name = "MalformedBodyExample",
                            summary = "Malformed JSON example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body\"}"
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
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User does not have the USER role",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
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
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Game already in collection",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AlreadyInCollectionExample",
                        summary = "Already in collection example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Game already in collection: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping
    fun addToCollection(@Valid @RequestBody request: AddToCollectionRequest): ResponseEntity<CollectionItemDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(collectionService.addToCollection(CurrentUser.id(), request))

    @Operation(summary = "Remove game from collection", description = "Remove a game from the current user's collection. Only the owner can remove it.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Game removed from collection",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid collection item id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidIdExample",
                        summary = "Invalid path parameter example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'itemId'\"}"
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
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - You do not own this collection item, or user does not have the USER role",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "NotOwnerExample",
                            summary = "Not the owner example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"You do not own this collection item\"}"
                        ),
                        ExampleObject(
                            name = "ForbiddenExample",
                            summary = "Access denied example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Collection item does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Collection item not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Collection item not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/{itemId}")
    fun removeFromCollection(
        @Parameter(description = "ID of the collection item") @PathVariable itemId: UUID,
    ): ResponseEntity<Unit> {
        collectionService.removeFromCollection(CurrentUser.id(), itemId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List collection", description = "List the games in the current user's collection, with optional filters by name, played status and expansion, and sorting.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Paginated list of owned games",
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
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field: rating\"}"
                        ),
                        ExampleObject(
                            name = "InvalidParameterExample",
                            summary = "Invalid query parameter example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'played'\"}"
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
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User does not have the USER role",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping
    fun listCollection(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Search by game name") @RequestParam(required = false) gameName: String?,
        @Parameter(description = "True for played games only, false for never played, omitted for both") @RequestParam(required = false) played: Boolean?,
        @Parameter(description = "True for expansions only, false for base games only, omitted for both") @RequestParam(required = false) isExpansion: Boolean?,
        @Parameter(description = "Sort field and direction, e.g. 'game.name-asc', 'createdAt-desc'") @RequestParam(required = false) sort: String?,
    ): ResponseEntity<PageDTO<CollectionItemDTO>> =
        ResponseEntity.ok(collectionService.listCollection(CurrentUser.id(), page, size, gameName, played, isExpansion, sort))

    @Operation(summary = "Check collection status", description = "Check whether a game is in the current user's collection.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Collection status",
                content = [Content(schema = Schema(implementation = CollectionStatusDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Missing or invalid game id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "MissingParameterExample",
                            summary = "Missing game id example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Missing required parameter 'gameId'\"}"
                        ),
                        ExampleObject(
                            name = "InvalidIdExample",
                            summary = "Invalid game id example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'gameId'\"}"
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
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User does not have the USER role",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/status")
    fun getStatus(
        @Parameter(description = "Internal Let's Table id of the game") @RequestParam gameId: UUID,
    ): ResponseEntity<CollectionStatusDTO> =
        ResponseEntity.ok(collectionService.getStatus(CurrentUser.id(), gameId))
}