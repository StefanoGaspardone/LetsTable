package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AddToCollectionRequest
import com.backend.models.dtos.CollectionItemDTO
import com.backend.models.dtos.PageDTO
import com.backend.security.CurrentUser
import com.backend.services.CollectionService
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
                responseCode = "403", description = "Forbidden - You do not own this collection item",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotOwnerExample",
                        summary = "Not the owner example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"You do not own this collection item\"}"
                    )]
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
    fun removeFromCollection(@PathVariable itemId: UUID): ResponseEntity<Void> {
        collectionService.removeFromCollection(CurrentUser.id(), itemId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List collection", description = "List all games in the current user's collection.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of owned games",
                content = [Content(schema = Schema(implementation = CollectionItemDTO::class))]
            ),
        ]
    )
    @GetMapping
    fun listCollection(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int, @RequestParam(required = false) gameName: String?, @RequestParam(required = false) sort: String?): PageDTO<CollectionItemDTO> =
        collectionService.listCollection(CurrentUser.id(), page, size, gameName, sort)
}