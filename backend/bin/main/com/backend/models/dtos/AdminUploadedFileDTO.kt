package com.backend.models.dtos

import com.backend.models.entities.UploadedFile
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Administrative representation of an uploaded file")
data class AdminUploadedFileDTO(
    @field:Schema(description = "Unique identifier of the file")
    val id: UUID,

    @field:Schema(description = "Type of entity this file belongs to", example = "USER_AVATAR")
    val ownerType: String,

    @field:Schema(description = "ID of the entity that owns this file")
    val ownerId: UUID,

    @field:Schema(description = "Original file name")
    val fileName: String,

    @field:Schema(description = "MIME type of the file", example = "image/png")
    val contentType: String,

    @field:Schema(description = "File size in bytes")
    val size: Long,

    @field:Schema(description = "Username of the user who uploaded the file, null if unknown")
    val uploadedByUsername: String?,

    @field:Schema(description = "Date and time the file was uploaded")
    val createdAt: Instant,
) {
    companion object {
        fun from(file: UploadedFile) = AdminUploadedFileDTO(
            id = file.id!!,
            ownerType = file.ownerType.name,
            ownerId = file.ownerId,
            fileName = file.fileName,
            contentType = file.contentType,
            size = file.size,
            uploadedByUsername = file.uploadedBy?.username,
            createdAt = file.createdAt,
        )
    }
}