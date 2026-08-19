package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AddWishlistItemRequest
import com.backend.models.dtos.AddWishlistMemberRequest
import com.backend.models.dtos.CreateWishlistRequest
import com.backend.models.dtos.WishlistDTO
import com.backend.models.dtos.WishlistItemDTO
import com.backend.models.dtos.WishlistMemberDTO
import com.backend.security.CurrentUser
import com.backend.services.WishlistService
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

@Tag(name = "Wishlists", description = "Create and manage personal or shared game wishlists")
@RestController
@RequestMapping("/api/v1/wishlists")
@PreAuthorize("hasRole('USER')")
class WishlistController(
    private val wishlistService: WishlistService,
) {

    @Operation(summary = "Create wishlist", description = "Create a new personal or shared wishlist.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Wishlist created",
                content = [Content(schema = Schema(implementation = WishlistDTO::class))]
            ),
        ]
    )
    @PostMapping
    fun createWishlist(@Valid @RequestBody request: CreateWishlistRequest): ResponseEntity<WishlistDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(wishlistService.createWishlist(CurrentUser.id(), request))

    @Operation(summary = "List accessible wishlists", description = "List all wishlists owned by, or shared with, the current user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of wishlists",
                content = [Content(schema = Schema(implementation = WishlistDTO::class))]
            ),
        ]
    )
    @GetMapping
    fun listAccessibleWishlists(): List<WishlistDTO> = wishlistService.listAccessibleWishlists(CurrentUser.id())

    @Operation(summary = "Delete wishlist", description = "Delete a wishlist. Only the owner can delete it.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Wishlist deleted",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the owner can delete this wishlist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotOwnerExample",
                        summary = "Not the owner example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the wishlist owner can perform this action\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Wishlist does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Wishlist not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/{wishlistId}")
    fun deleteWishlist(@PathVariable wishlistId: UUID): ResponseEntity<Void> {
        wishlistService.deleteWishlist(CurrentUser.id(), wishlistId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Add member", description = "Add a member to a shared wishlist. Only the owner can add members, and only on shared wishlists.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Member added",
                content = [Content(schema = Schema(implementation = WishlistMemberDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Wishlist is not shared",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotSharedExample",
                        summary = "Wishlist not shared example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Wishlist is not shared, cannot manage members: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the owner can add members",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotOwnerExample",
                        summary = "Not the owner example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the wishlist owner can perform this action\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Wishlist or user does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Wishlist not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - User is already a member",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AlreadyMemberExample",
                        summary = "Already a member example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"User is already a member of this wishlist: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/{wishlistId}/members")
    fun addMember(
        @PathVariable wishlistId: UUID,
        @Valid @RequestBody request: AddWishlistMemberRequest,
    ): ResponseEntity<WishlistMemberDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(wishlistService.addMember(CurrentUser.id(), wishlistId, request))

    @Operation(summary = "Remove member", description = "Remove a member from a shared wishlist. Only the owner can remove members.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Member removed",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the owner can remove members",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotOwnerExample",
                        summary = "Not the owner example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Only the wishlist owner can perform this action\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Wishlist or membership does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "MemberNotFoundExample",
                        summary = "Member not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User is not a member of this wishlist: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/{wishlistId}/members/{memberUserId}")
    fun removeMember(@PathVariable wishlistId: UUID, @PathVariable memberUserId: UUID): ResponseEntity<Void> {
        wishlistService.removeMember(CurrentUser.id(), wishlistId, memberUserId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Leave wishlist", description = "Leave a shared wishlist you are a member of.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Left the wishlist",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Not a member of this wishlist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotMemberExample",
                        summary = "Not a member example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User is not a member of this wishlist: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/{wishlistId}/leave")
    fun leaveWishlist(@PathVariable wishlistId: UUID): ResponseEntity<Void> {
        wishlistService.leaveWishlist(CurrentUser.id(), wishlistId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List members", description = "List all members of a wishlist. Requires owner or member access.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of members",
                content = [Content(schema = Schema(implementation = WishlistMemberDTO::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - No access to this wishlist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NoAccessExample",
                        summary = "No access example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"You do not have access to this wishlist\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{wishlistId}/members")
    fun listMembers(@PathVariable wishlistId: UUID): List<WishlistMemberDTO> =
        wishlistService.listMembers(wishlistId)

    @Operation(summary = "Add game to wishlist", description = "Add a game to a wishlist. Owner and members (if shared) can add games.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Game added",
                content = [Content(schema = Schema(implementation = WishlistItemDTO::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - No access to this wishlist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NoAccessExample",
                        summary = "No access example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"You do not have access to this wishlist\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Wishlist or game does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "GameNotFoundExample",
                        summary = "Game not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Game already in wishlist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AlreadyInWishlistExample",
                        summary = "Already in wishlist example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Game already in wishlist: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @PostMapping("/{wishlistId}/items")
    fun addItem(
        @PathVariable wishlistId: UUID,
        @Valid @RequestBody request: AddWishlistItemRequest,
    ): ResponseEntity<WishlistItemDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(wishlistService.addItem(CurrentUser.id(), wishlistId, request))

    @Operation(summary = "Remove game from wishlist", description = "Remove a game from a wishlist. Owner and members (if shared) can remove any game.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Game removed",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - No access to this wishlist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NoAccessExample",
                        summary = "No access example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"You do not have access to this wishlist\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Wishlist item does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Wishlist item not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist item not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @DeleteMapping("/{wishlistId}/items/{itemId}")
    fun removeItem(@PathVariable wishlistId: UUID, @PathVariable itemId: UUID): ResponseEntity<Void> {
        wishlistService.removeItem(CurrentUser.id(), wishlistId, itemId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List wishlist items", description = "List all games in a wishlist. Requires owner or member access.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of games in the wishlist",
                content = [Content(schema = Schema(implementation = WishlistItemDTO::class))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - No access to this wishlist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NoAccessExample",
                        summary = "No access example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"You do not have access to this wishlist\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{wishlistId}/items")
    fun listItems(@PathVariable wishlistId: UUID): List<WishlistItemDTO> =
        wishlistService.listItems(wishlistId)

    @Operation(summary = "Get wishlist", description = "Get details of any wishlist by id. Wishlists are publicly viewable by any authenticated user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Wishlist details",
                content = [Content(schema = Schema(implementation = WishlistDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Wishlist does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Wishlist not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{wishlistId}")
    fun getWishlist(@PathVariable wishlistId: UUID): WishlistDTO = wishlistService.getWishlist(wishlistId)
}