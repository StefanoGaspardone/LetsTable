package com.backend.models.dtos

import com.backend.models.entities.User
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Schema(description = "Administrative representation of a user account")
data class AdminUserDTO(
    @field:Schema(description = "Unique identifier of the user")
    val id: UUID,

    @field:Schema(description = "Username")
    val username: String,

    @field:Schema(description = "Email address")
    val email: String,

    @field:Schema(description = "Role assigned to the user", example = "USER")
    val role: String,

    @field:Schema(description = "Current account status", example = "ACTIVE")
    val accountStatus: String,

    @field:Schema(description = "ID of the uploaded avatar file, if the user has one. Null if no custom avatar was uploaded.")
    val avatarId: UUID?,

    @field:Schema(description = "Whether the user has push/email notifications enabled")
    val notificationsEnabled: Boolean,

    @field:Schema(description = "Date and time the account was created")
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User) = AdminUserDTO(
            id = user.id!!,
            username = user.username,
            email = user.email,
            role = user.role.name,
            accountStatus = user.accountStatus.name,
            avatarId = user.avatarId,
            notificationsEnabled = user.notificationsEnabled,
            createdAt = user.createdAt,
        )
    }
}

@Schema(description = "Administrative detail view of a user, including activity stats and recent matches")
data class AdminUserDetailDTO(
    @field:Schema(description = "The user's account information")
    val user: AdminUserDTO,

    @field:Schema(description = "Total number of completed matches the user has participated in")
    val totalMatches: Long,

    @field:Schema(description = "Total number of matches the user has won")
    val totalWins: Long,

    @field:Schema(description = "Number of games in the user's collection")
    val collectionCount: Long,

    @field:Schema(description = "The user's 10 most recent matches")
    val recentMatches: List<MatchDTO>,
)

@Schema(description = "Payload to create a new admin account. The account is active immediately, no email verification required.")
data class CreateAdminRequest(
    @field:Schema(description = "Username for the new admin", example = "admin2")
    @field:NotBlank
    @field:Size(min = 3, max = 50)
    val username: String,

    @field:Schema(description = "Email address for the new admin", example = "admin2@letstable.app")
    @field:NotBlank
    @field:Email
    val email: String,

    @field:Schema(description = "Password for the new admin", example = "securePassword123")
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,
)