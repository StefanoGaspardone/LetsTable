package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.UploadedFileDTO
import com.backend.models.enums.FileOwnerType
import com.backend.services.UploadedFileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

private val ALLOWED_RULE_FILE_TYPES = setOf("application/pdf")

@Tag(name = "Game Rule Files", description = "Community-uploaded rulebook PDFs for games")
@RestController
@RequestMapping("/api/v1/games/{gameId}/rules")
@PreAuthorize("hasRole('USER')")
class GameRuleFileController(
    private val uploadedFileService: UploadedFileService,
) {

    @Operation(summary = "List rule files", description = "Returns all rulebook PDFs uploaded for a game")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Rule files retrieved successfully"
            )
        ]
    )
    @GetMapping
    fun listRuleFiles(@Parameter(description = "Internal ID of the game") @PathVariable gameId: UUID): List<UploadedFileDTO> =
        uploadedFileService.listFiles(FileOwnerType.GAME_RULE, gameId)

    @Operation(summary = "Upload a rule file", description = "Uploads a PDF rulebook for a game. Any authenticated user can upload.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Rule file uploaded successfully"),
            ApiResponse(
                responseCode = "400",
                description = "Invalid file type",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidFileType",
                        value = """{"timestamp":"2026-08-25T12:00:00Z","status":400,"error":"Bad Request","message":"Unsupported file type: image/png"}"""
                    )]
                )]
            ),
        ]
    )
    @PostMapping(consumes = ["multipart/form-data"])
    fun uploadRuleFile(@Parameter(description = "Internal ID of the game") @PathVariable gameId: UUID, @Parameter(description = "PDF file to upload") @RequestParam("file") file: MultipartFile): UploadedFileDTO =
        uploadedFileService.uploadFile(FileOwnerType.GAME_RULE, gameId, file, ALLOWED_RULE_FILE_TYPES)

    @Operation(summary = "Download a rule file", description = "Streams the raw PDF content of an uploaded rule file")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "File streamed successfully"),
            ApiResponse(
                responseCode = "404",
                description = "Rule file not found",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "FileNotFound",
                        value = """{"timestamp":"2026-08-25T12:00:00Z","status":404,"error":"Not Found","message":"File not found"}"""
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{fileId}/download")
    fun downloadRuleFile(@Parameter(description = "Internal ID of the game") @PathVariable gameId: UUID, @Parameter(description = "ID of the uploaded file") @PathVariable fileId: UUID): ResponseEntity<Resource> {
        val (resource, entity) = uploadedFileService.loadFileResource(FileOwnerType.GAME_RULE, gameId, fileId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${entity.fileName}\"")
            .contentType(MediaType.parseMediaType(entity.contentType))
            .body(resource)
    }

    @Operation(summary = "Delete a rule file", description = "Deletes an uploaded rulebook PDF")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Rule file deleted successfully"),
            ApiResponse(
                responseCode = "404",
                description = "Rule file not found",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "FileNotFound",
                        value = """{"timestamp":"2026-08-25T12:00:00Z","status":404,"error":"Not Found","message":"File not found"}"""
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/{fileId}")
    fun deleteRuleFile(@Parameter(description = "Internal ID of the game") @PathVariable gameId: UUID, @Parameter(description = "ID of the uploaded file") @PathVariable fileId: UUID): ResponseEntity<Void> {
        uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId)
        return ResponseEntity.noContent().build()
    }
}