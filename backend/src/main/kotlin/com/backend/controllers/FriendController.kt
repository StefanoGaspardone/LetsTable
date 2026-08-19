package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.FriendRequestDTO
import com.backend.models.dtos.SendFriendRequestRequest
import com.backend.models.dtos.UserDTO
import com.backend.security.CurrentUser
import com.backend.services.FriendService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Friends", description = "Send, accept, reject friend requests and manage the friends list")
@RestController
@RequestMapping("/api/v1/friends")
@PreAuthorize("hasRole('USER')")
class FriendController(
    private val friendService: FriendService,
) {

    @Operation(summary = "Send friend request", description = "Send a friend request to another user. If the other user had already sent a pending request, the friendship is accepted automatically instead of creating a duplicate.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Friend request sent (or friendship auto-accepted)",
                content = [Content(schema = Schema(implementation = FriendRequestDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Cannot send a friend request to yourself",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "CannotFriendSelfExample",
                        summary = "Cannot friend self example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Cannot send a friend request to yourself\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Receiver user does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UserNotFoundExample",
                        summary = "User not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found for identifier: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Already friends, or a pending request already exists",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AlreadyFriendsExample",
                        summary = "Already friends example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Already friends with user: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    ), ExampleObject(
                        name = "RequestAlreadyExistsExample",
                        summary = "Duplicate pending request example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"A pending friend request already exists with user: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/requests")
    fun sendRequest(@Valid @RequestBody request: SendFriendRequestRequest): ResponseEntity<FriendRequestDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(friendService.sendRequest(CurrentUser.id(), request))

    @Operation(summary = "Accept friend request", description = "Accept a pending friend request. Only the receiver of the request can accept it.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Friend request accepted",
                content = [Content(schema = Schema(implementation = FriendRequestDTO::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the receiver can accept this request",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotReceiverExample",
                        summary = "Not the receiver example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the receiver can respond to this friend request\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Friend request does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Friend request not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Friend request not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/requests/{requestId}/accept")
    fun acceptRequest(@PathVariable requestId: UUID): ResponseEntity<FriendRequestDTO> =
        ResponseEntity.ok(friendService.acceptRequest(CurrentUser.id(), requestId))

    @Operation(summary = "Reject friend request", description = "Reject a pending friend request. Only the receiver can reject it. The request is deleted, not just marked as rejected.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Friend request rejected",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the receiver can reject this request",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotReceiverExample",
                        summary = "Not the receiver example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the receiver can respond to this friend request\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Friend request does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Friend request not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Friend request not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/requests/{requestId}/reject")
    fun rejectRequest(@PathVariable requestId: UUID): ResponseEntity<Void> {
        friendService.rejectRequest(CurrentUser.id(), requestId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Cancel a sent friend request", description = "Cancel a friend request you previously sent. Only the sender can cancel it.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Friend request cancelled",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the sender can cancel this request",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotSenderExample",
                        summary = "Not the sender example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the sender can cancel this friend request\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Friend request does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Friend request not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Friend request not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/requests/{requestId}")
    fun cancelRequest(@PathVariable requestId: UUID): ResponseEntity<Void> {
        friendService.cancelRequest(CurrentUser.id(), requestId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Remove a friend", description = "Remove an existing friendship with another user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Friendship removed",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - No friendship exists with this user",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Friendship not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"No friendship found with user: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/{friendUserId}")
    fun removeFriend(@PathVariable friendUserId: UUID): ResponseEntity<Void> {
        friendService.removeFriend(CurrentUser.id(), friendUserId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List friends", description = "List all accepted friends of the current user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of friends",
                content = [Content(schema = Schema(implementation = UserDTO::class))]
            ),
        ]
    )
    @GetMapping
    fun listFriends(): List<UserDTO> = friendService.listFriends(CurrentUser.id())

    @Operation(summary = "List pending friend requests received", description = "List all friend requests received by the current user that are still pending.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of pending received requests",
                content = [Content(schema = Schema(implementation = FriendRequestDTO::class))]
            ),
        ]
    )
    @GetMapping("/requests/received")
    fun listPendingReceived(): List<FriendRequestDTO> = friendService.listPendingReceived(CurrentUser.id())

    @Operation(summary = "List pending friend requests sent", description = "List all friend requests sent by the current user that are still pending.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of pending sent requests",
                content = [Content(schema = Schema(implementation = FriendRequestDTO::class))]
            ),
        ]
    )
    @GetMapping("/requests/sent")
    fun listPendingSent(): List<FriendRequestDTO> = friendService.listPendingSent(CurrentUser.id())
}