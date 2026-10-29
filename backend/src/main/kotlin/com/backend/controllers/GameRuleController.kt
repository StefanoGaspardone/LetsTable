package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.UploadedFileDTO
import com.backend.models.enums.FileOwnerType
import com.backend.services.UploadedFileService
import com.backend.utils.FileTypeValidator
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.UUID

private val isRuleFileTypeAllowed = FileTypeValidator.exactTypesOrPrefixes(
    exactTypes = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ),
    prefixes = setOf("image/", "video/"),
)

@Tag(name = "Game Rule Files", description = "Community-uploaded rulebooks and reference material for games")
@RestController
@RequestMapping("/api/v1/games/{gameId}/rules")
@PreAuthorize("hasRole('USER')")
class GameRuleFileController(
    private val uploadedFileService: UploadedFileService,
) {

    @Operation(summary = "List rule files", description = "All rule files uploaded for a game, newest first.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of rule files",
                content = [Content(array = ArraySchema(schema = Schema(implementation = UploadedFileDTO::class)))]
            ),
        ]
    )
    @GetMapping
    fun listRuleFiles(@Parameter(description = "Internal id of the game") @PathVariable gameId: UUID): ResponseEntity<List<UploadedFileDTO>> =
        ResponseEntity.ok(uploadedFileService.listFiles(FileOwnerType.GAME_RULE, gameId))

    @Operation(summary = "Upload a rule file", description = "Uploads a rule file for a game: PDF, Office documents, images (SVG not allowed) or videos. Any authenticated user can upload.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Rule file uploaded",
                content = [Content(schema = Schema(implementation = UploadedFileDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Empty file or unsupported file type",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidFileTypeExample",
                            summary = "Unsupported file type example",
                            value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Unsupported file type: application/zip\"}"
                        ),
                        ExampleObject(
                            name = "EmptyFileExample",
                            summary = "Empty file example",
                            value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Uploaded file is empty\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "413", description = "Payload Too Large - File exceeds the maximum allowed size",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "TooLargeExample",
                        summary = "File too large example",
                        value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":413,\"error\":\"Payload Too Large\",\"message\":\"Uploaded file exceeds the maximum allowed size\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "502", description = "Bad Gateway - Storage write failed",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "StorageWriteExample",
                        summary = "Storage write failed example",
                        value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":502,\"error\":\"Bad Gateway\",\"message\":\"Failed to write object to storage: game_rule/3fa85f64-5717-4562-b3fc-2c963f66afa6/7c9e6679-7425-40de-944b-e07fc1f90ae7.pdf\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadRuleFile(
        @Parameter(description = "Internal id of the game") @PathVariable gameId: UUID,
        @Parameter(description = "File to upload") @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<UploadedFileDTO> =
        ResponseEntity.ok(uploadedFileService.uploadFile(FileOwnerType.GAME_RULE, gameId, file, isRuleFileTypeAllowed))

    @Operation(summary = "Download a rule file", description = "Streams the raw content of a rule file of the given game as an attachment.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ok - File streamed"),
            ApiResponse(
                responseCode = "404", description = "Not Found - File does not exist for this game",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "FileNotFoundExample",
                        summary = "File not found example",
                        value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"File not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "502", description = "Bad Gateway - Storage read failed",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "StorageReadExample",
                        summary = "Storage read failed example",
                        value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":502,\"error\":\"Bad Gateway\",\"message\":\"Failed to read object from storage: game_rule/3fa85f64-5717-4562-b3fc-2c963f66afa6/7c9e6679-7425-40de-944b-e07fc1f90ae7.pdf\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{fileId}/download")
    fun downloadRuleFile(
        @Parameter(description = "Internal id of the game") @PathVariable gameId: UUID,
        @Parameter(description = "Id of the uploaded file") @PathVariable fileId: UUID,
    ): ResponseEntity<Resource> {
        val (resource, file) = uploadedFileService.loadFileResource(FileOwnerType.GAME_RULE, fileId, gameId)
        val contentDisposition = ContentDisposition.attachment().filename(file.fileName, StandardCharsets.UTF_8).build()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .contentType(MediaType.parseMediaType(file.contentType))
            .contentLength(file.size)
            .body(resource)
    }

    @Operation(summary = "Delete a rule file", description = "Deletes a rule file of the given game. Admin only.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Rule file deleted",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only admins can delete rule files",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - File does not exist for this game",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "FileNotFoundExample",
                        summary = "File not found example",
                        value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"File not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{fileId}")
    fun deleteRuleFile(
        @Parameter(description = "Internal id of the game") @PathVariable gameId: UUID,
        @Parameter(description = "Id of the uploaded file") @PathVariable fileId: UUID,
    ): ResponseEntity<Unit> {
        uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId)
        return ResponseEntity.noContent().build()
    }
}