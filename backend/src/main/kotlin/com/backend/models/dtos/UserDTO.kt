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

    @field:Schema(description = "ID of the uploaded avatar file, if the user has one. Null if no custom avatar was uploaded.")
    val avatarId: UUID?,

    @field:Schema(description = "Whether the user has push/email notifications enabled")
    val notificationsEnabled: Boolean,
) {
    companion object {
        fun from(user: User) = UserDTO(
            id = user.id!!,
            username = user.username,
            email = user.email,
            role = user.role.name,
            avatarId = user.avatarId,
            notificationsEnabled = user.notificationsEnabled,
        )
    }
}

@Schema(description = "Confirmation that an account deletion was processed")
data class DeleteAccountDTO(
    @field:Schema(description = "Human-readable confirmation message")
    val message: String,
)

@Schema(description = "Payload to update the current user's profile. Fields left null are not modified.")
data class UpdateUserRequest(
    @field:Schema(description = "New username, if changing it", example = "marco2")
    val username: String? = null,

    @field:Schema(description = "New notifications-enabled preference, if changing it", example = "false")
    val notificationsEnabled: Boolean? = null,

    @field:Schema(description = "ID of a previously uploaded file to use as the new avatar. Ignored if removeAvatar is true.")
    val avatarId: UUID? = null,

    @field:Schema(description = "If true, removes the current avatar and reverts to the default. Takes precedence over avatarId.")
    val removeAvatar: Boolean? = null,
)