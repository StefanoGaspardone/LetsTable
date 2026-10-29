package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.UploadedFileDTO
import com.backend.models.enums.FileOwnerType
import com.backend.security.CurrentUser
import com.backend.services.UploadedFileService
import com.backend.services.UserService
import com.backend.utils.FileTypeValidator
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.util.UUID

private val isAvatarFileTypeAllowed = FileTypeValidator.exactTypesOrPrefixes(
    exactTypes = emptySet(),
    prefixes = setOf("image/"),
)

@Tag(name = "User Avatar", description = "Upload, retrieve, and delete the current user's avatar image")
@RestController
class UserAvatarController(
    private val uploadedFileService: UploadedFileService,
    private val userService: UserService,
) {

    companion object {
        private val AVATAR_CACHE_DURATION: Duration = Duration.ofDays(7)
    }

    @Operation(summary = "Upload an avatar", description = "Uploads an image (SVG not allowed) to use as the current user's avatar. Returns the uploaded file's id, which must then be passed to PATCH /users/me to set it as the active avatar.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Avatar file uploaded",
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
                            value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Unsupported file type: application/pdf\"}"
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
                        value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":502,\"error\":\"Bad Gateway\",\"message\":\"Failed to write object to storage: user_avatar/3fa85f64-5717-4562-b3fc-2c963f66afa6/7c9e6679-7425-40de-944b-e07fc1f90ae7.png\"}"
                    )]
                )]
            ),
        ]
    )
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/api/v1/users/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAvatar(@Parameter(description = "Image file to upload") @RequestParam("file") file: MultipartFile): ResponseEntity<UploadedFileDTO> =
        ResponseEntity.ok(uploadedFileService.uploadFile(FileOwnerType.USER_AVATAR, CurrentUser.id(), file, isAvatarFileTypeAllowed))

    @Operation(summary = "Get a user's avatar", description = "Streams the raw image content of an uploaded avatar file. Avatar files are immutable, so the response is cacheable.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ok - Avatar image streamed"),
            ApiResponse(
                responseCode = "404", description = "Not Found - Avatar file does not exist",
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
                        value = "{\"timestamp\":\"2026-08-25T12:00:00Z\",\"status\":502,\"error\":\"Bad Gateway\",\"message\":\"Failed to read object from storage: user_avatar/3fa85f64-5717-4562-b3fc-2c963f66afa6/7c9e6679-7425-40de-944b-e07fc1f90ae7.png\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/api/v1/avatars/{fileId}")
    fun getAvatar(@Parameter(description = "ID of the uploaded avatar file") @PathVariable fileId: UUID): ResponseEntity<Resource> {
        val (resource, file) = uploadedFileService.loadFileResource(FileOwnerType.USER_AVATAR, fileId)

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .contentLength(file.size)
            .cacheControl(CacheControl.maxAge(AVATAR_CACHE_DURATION).cachePrivate())
            .body(resource)
    }

    @Operation(summary = "Delete an avatar file", description = "Deletes one of the current user's uploaded avatar files. If it is the active avatar, the user's avatarId is cleared as well.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Avatar file deleted",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Avatar file does not exist or is not yours",
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
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/api/v1/users/me/avatar/{fileId}")
    fun deleteAvatar(@Parameter(description = "ID of the uploaded avatar file") @PathVariable fileId: UUID): ResponseEntity<Unit> {
        userService.deleteAvatar(CurrentUser.id(), fileId)
        return ResponseEntity.noContent().build()
    }
}