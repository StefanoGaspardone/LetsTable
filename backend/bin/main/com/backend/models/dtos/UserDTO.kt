package com.backend.models.dtos

import com.backend.models.entities.User
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Public representation of a user account")
data class UserDTO(
    @field:Schema(description = "Unique identifier of the user")
    val id: UUID,

    @field:Schema(description = "Username")
    val username: String,

    @field:Schema(description = "Email address")
    val email: String,

    @field:Schema(description = "Role assigned to the user", example = "USER")
    val role: String,

    @field:Schema(description = "Avatar URL for the user profile picture", example = "https://example.com/avatar.png")
    val avatarUrl: String,
) {
    companion object {
        fun from(user: User) = UserDTO(
            id = user.id!!,
            username = user.username,
            email = user.email,
            role = user.role.name,
            avatarUrl = "https://api.dicebear.com/9.x/initials/svg?seed=${user.username}"
        )
    }
}

@Schema(description = "Confirmation that an account deletion was processed")
data class DeleteAccountDTO(
    @field:Schema(description = "Human-readable confirmation message")
    val message: String,
)