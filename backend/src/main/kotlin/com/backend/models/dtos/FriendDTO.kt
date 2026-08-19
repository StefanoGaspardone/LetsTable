package com.backend.models.dtos

import com.backend.models.entities.FriendRequest
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

@Schema(description = "Payload to send a friend request")
data class SendFriendRequestRequest(

    @field:Schema(description = "Id of the user to send the friend request to")
    @field:NotNull
    val receiverId: UUID,
)

@Schema(description = "A pending or accepted friend request")
data class FriendRequestDTO(

    @field:Schema(description = "Friend request id")
    val id: UUID,

    @field:Schema(description = "Public profile of the user who sent the request")
    val sender: UserDTO,

    @field:Schema(description = "Public profile of the user who received the request")
    val receiver: UserDTO,

    @field:Schema(description = "Current status of the request", example = "PENDING")
    val status: String,

    @field:Schema(description = "When the request was created")
    val createdAt: Instant,
) {
    companion object {
        fun from(request: FriendRequest) = FriendRequestDTO(
            id = request.id!!,
            sender = UserDTO.from(request.sender),
            receiver = UserDTO.from(request.receiver),
            status = request.status.name,
            createdAt = request.createdAt,
        )
    }
}