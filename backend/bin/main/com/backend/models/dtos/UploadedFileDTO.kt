package com.backend.models.dtos

import com.backend.models.entities.UploadedFile
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "A file uploaded and attached to an entity such as a game or user")
data class UploadedFileDTO(
    @field:Schema(description = "Unique identifier of the uploaded file")
    val id: UUID,

    @field:Schema(description = "Original file name", example = "Dune-Imperium-Rulebook.pdf")
    val fileName: String,

    @field:Schema(description = "Username of the user who uploaded the file", example = "marco")
    val uploadedByUsername: String?,

    @field:Schema(description = "When the file was uploaded")
    val createdAt: Instant,
) {
    companion object {
        fun from(entity: UploadedFile) = UploadedFileDTO(
            id = entity.id!!,
            fileName = entity.fileName,
            uploadedByUsername = entity.uploadedBy?.username,
            createdAt = entity.createdAt,
        )
    }
}