package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AdminGameDTO
import com.backend.models.dtos.AdminUploadedFileDTO
import com.backend.services.AdminGameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Admin - Games", description = "Administrative endpoints for managing the BGG game cache")
@RestController
@RequestMapping("/api/v1/admin/games")
@PreAuthorize("hasRole('ADMIN')")
class AdminGameController(
    private val adminGameService: AdminGameService,
) {

    @Operation(summary = "Force refresh a game", description = "Forces a fresh sync of a single game from BoardGameGeek, bypassing the normal staleness check.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Game refreshed",
                content = [Content(schema = Schema(implementation = AdminGameDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Game does not exist on BoardGameGeek",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "GameNotFoundExample",
                        summary = "Game not found on BGG example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found on BoardGameGeek: 999999999\"}"
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
            ApiResponse(responseCode = "204", description = "No Content - Hot games cache refreshed successfully"),
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
                content = [Content(schema = Schema(implementation = AdminUploadedFileDTO::class))]
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
            ApiResponse(responseCode = "204", description = "No Content - Rule file deleted successfully"),
            ApiResponse(
                responseCode = "404", description = "Not Found - File does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "FileNotFoundExample",
                        summary = "File not found example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"File not found\"}"
                    )]
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
}