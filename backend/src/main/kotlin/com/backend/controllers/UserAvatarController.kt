package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.UploadedFileDTO
import com.backend.models.enums.FileOwnerType
import com.backend.security.CurrentUser
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

private val ALLOWED_AVATAR_FILE_TYPES = setOf("image/png", "image/jpeg", "image/webp")

@Tag(name = "User Avatar", description = "Upload, retrieve, and delete the current user's avatar image")
@RestController
class UserAvatarController(
    private val uploadedFileService: UploadedFileService,
) {

    @Operation(
        summary = "Upload an avatar",
        description = "Uploads an image to use as the current user's avatar. Returns the uploaded file's id, which must then be passed to PATCH /users/me to actually set it as the active avatar."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Avatar file uploaded successfully"),
            ApiResponse(
                responseCode = "400",
                description = "Invalid file type",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidFileType",
                        value = """{"timestamp":"2026-08-25T12:00:00Z","status":400,"error":"Bad Request","message":"Unsupported file type: application/pdf"}"""
                    )]
                )]
            ),
        ]
    )
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/api/v1/users/me/avatar", consumes = ["multipart/form-data"])
    fun uploadAvatar(@Parameter(description = "Image file to upload") @RequestParam("file") file: MultipartFile): ResponseEntity<UploadedFileDTO> =
        ResponseEntity.ok(uploadedFileService.uploadFile(FileOwnerType.USER_AVATAR, CurrentUser.id(), file, ALLOWED_AVATAR_FILE_TYPES))

    @Operation(
        summary = "Get a user's avatar",
        description = "Streams the raw image content of a user's uploaded avatar file"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Avatar streamed successfully"),
            ApiResponse(
                responseCode = "404",
                description = "Avatar file not found",
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
    @GetMapping("/api/v1/avatars/{fileId}")
    fun getAvatar(@Parameter(description = "ID of the uploaded avatar file") @PathVariable fileId: UUID): ResponseEntity<Resource> {
        val (resource, entity) = uploadedFileService.loadFileResource(FileOwnerType.USER_AVATAR, fileId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(entity.contentType))
            .body(resource)
    }

    @Operation(
        summary = "Delete an avatar file",
        description = "Deletes an uploaded avatar file. Does not update the user's avatarId; call PATCH /users/me with removeAvatar=true afterwards to detach it."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Avatar file deleted successfully"),
            ApiResponse(
                responseCode = "404",
                description = "Avatar file not found",
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
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/api/v1/users/me/avatar/{fileId}")
    fun deleteAvatar(@Parameter(description = "ID of the uploaded avatar file") @PathVariable fileId: UUID): ResponseEntity<Unit> {
        uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, CurrentUser.id(), fileId)
        return ResponseEntity.noContent().build()
    }
}