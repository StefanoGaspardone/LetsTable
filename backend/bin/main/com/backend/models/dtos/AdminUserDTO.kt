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
    val id: UUID,
    val username: String,
    val email: String,
    val role: String,
    val accountStatus: String,
    val avatarId: UUID?,
    val notificationsEnabled: Boolean,
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

@Schema(description = "Administrative detail view of a user, including activity stats")
data class AdminUserDetailDTO(
    val user: AdminUserDTO,
    val totalMatches: Long,
    val totalWins: Long,
    val collectionCount: Long,
    val recentMatches: List<MatchDTO>,
)

@Schema(description = "Payload to create a new admin account. The account is active immediately, no email verification required.")
data class CreateAdminRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 50)
    val username: String,

    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,
)