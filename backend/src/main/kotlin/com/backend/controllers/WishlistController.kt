package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.AddWishlistItemRequest
import com.backend.models.dtos.AddWishlistMemberRequest
import com.backend.models.dtos.CreateWishlistRequest
import com.backend.models.dtos.PageDTO
import com.backend.models.dtos.UpdateWishlistRequest
import com.backend.models.dtos.WishlistDTO
import com.backend.models.dtos.WishlistItemDTO
import com.backend.models.dtos.WishlistItemStatusDTO
import com.backend.models.dtos.WishlistMemberDTO
import com.backend.models.specifications.WishlistFilterType
import com.backend.security.CurrentUser
import com.backend.services.WishlistService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
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

@Tag(name = "Wishlists", description = "Create and manage personal or shared game wishlists. A wishlist is viewable by its owner, by its members if shared, and by anyone if it is a user's default wishlist.")
@RestController
@RequestMapping("/api/v1/wishlists")
@PreAuthorize("hasRole('USER')")
class WishlistController(
    private val wishlistService: WishlistService,
) {

    @Operation(summary = "Create wishlist", description = "Create a new personal or shared wishlist owned by the current user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Wishlist created",
                content = [Content(schema = Schema(implementation = WishlistDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid or malformed request data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "ValidationExample",
                            summary = "Blank name example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"name: must not be blank\"}"
                        ),
                        ExampleObject(
                            name = "MalformedBodyExample",
                            summary = "Malformed JSON example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @PostMapping
    fun createWishlist(@Valid @RequestBody request: CreateWishlistRequest): ResponseEntity<WishlistDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(wishlistService.createWishlist(CurrentUser.id(), request))

    @Operation(summary = "List accessible wishlists", description = "Paginated list of wishlists owned by or shared with the current user, including their default one. Optionally filtered by type.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of wishlists",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid sort field or query parameter",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidSortExample",
                            summary = "Invalid sort field example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field: rating\"}"
                        ),
                        ExampleObject(
                            name = "InvalidParameterExample",
                            summary = "Invalid type example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'type'\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @GetMapping
    fun listAccessibleWishlists(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "ALL, SHARED or PRIVATE (private excludes the default wishlist), omitted for all") @RequestParam(required = false) type: WishlistFilterType?,
        @Parameter(description = "Sort field and direction, e.g. 'name-asc', 'updatedAt-desc'") @RequestParam(required = false) sort: String?,
    ): ResponseEntity<PageDTO<WishlistDTO>> =
        ResponseEntity.ok(wishlistService.listAccessibleWishlists(CurrentUser.id(), page, size, type, sort))

    @Operation(summary = "Get wishlist", description = "Get details of a wishlist. Allowed if it is your own, a user's default wishlist, or a shared wishlist you are a member of.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Wishlist details",
                content = [Content(schema = Schema(implementation = WishlistDTO::class))]
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
    fun getWishlist(@Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID): ResponseEntity<WishlistDTO> =
        ResponseEntity.ok(wishlistService.getWishlist(CurrentUser.id(), wishlistId))

    @Operation(summary = "Rename wishlist", description = "Update the name of a wishlist. Only the owner can rename it, and the default wishlist cannot be renamed.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Wishlist updated",
                content = [Content(schema = Schema(implementation = WishlistDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid name",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ValidationExample",
                        summary = "Blank name example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"name: must not be blank\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - The default wishlist cannot be renamed",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "DefaultWishlistExample",
                        summary = "Default wishlist example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"The default wishlist cannot be renamed or deleted\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - Only the owner can rename this wishlist",
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
    @PatchMapping("/{wishlistId}")
    fun updateWishlist(
        @Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID,
        @Valid @RequestBody request: UpdateWishlistRequest,
    ): ResponseEntity<WishlistDTO> =
        ResponseEntity.ok(wishlistService.updateWishlist(CurrentUser.id(), wishlistId, request))

    @Operation(summary = "Delete wishlist", description = "Delete a wishlist. Only the owner can delete it, and the default wishlist cannot be deleted.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Wishlist deleted",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - The default wishlist cannot be deleted",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "DefaultWishlistExample",
                        summary = "Default wishlist example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"The default wishlist cannot be renamed or deleted\"}"
                    )]
                )]
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
    fun deleteWishlist(@Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID): ResponseEntity<Unit> {
        wishlistService.deleteWishlist(CurrentUser.id(), wishlistId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Add member", description = "Add an active user as member of a shared wishlist. Only the owner can add members, and the owner cannot add themselves.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Member added",
                content = [Content(schema = Schema(implementation = WishlistMemberDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Wishlist is not shared, owner cannot be added, or invalid request data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "NotSharedExample",
                            summary = "Wishlist not shared example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Wishlist is not shared, cannot manage members: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                        ExampleObject(
                            name = "MalformedBodyExample",
                            summary = "Missing or malformed userId example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body\"}"
                        ),
                    ]
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
                responseCode = "404", description = "Not Found - Wishlist does not exist, or user does not exist or is not active",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "WishlistNotFoundExample",
                            summary = "Wishlist not found example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                        ExampleObject(
                            name = "UserNotFoundExample",
                            summary = "User not found example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found for identifier: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                    ]
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
        @Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID,
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
                    examples = [
                        ExampleObject(
                            name = "WishlistNotFoundExample",
                            summary = "Wishlist not found example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                        ExampleObject(
                            name = "MemberNotFoundExample",
                            summary = "Member not found example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User is not a member of this wishlist: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @DeleteMapping("/{wishlistId}/members/{memberUserId}")
    fun removeMember(
        @Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID,
        @Parameter(description = "Id of the member user to remove") @PathVariable memberUserId: UUID,
    ): ResponseEntity<Unit> {
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
    fun leaveWishlist(@Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID): ResponseEntity<Unit> {
        wishlistService.leaveWishlist(CurrentUser.id(), wishlistId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List members", description = "List all members of a wishlist. Allowed if it is your own, a user's default wishlist, or a shared wishlist you are a member of.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of members",
                content = [Content(array = ArraySchema(schema = Schema(implementation = WishlistMemberDTO::class)))]
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
    @GetMapping("/{wishlistId}/members")
    fun listMembers(@Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID): ResponseEntity<List<WishlistMemberDTO>> =
        ResponseEntity.ok(wishlistService.listMembers(CurrentUser.id(), wishlistId))

    @Operation(summary = "Add game to wishlist", description = "Add a game to a wishlist. Allowed for the owner and, if shared, for its members.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Game added",
                content = [Content(schema = Schema(implementation = WishlistItemDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid or malformed request data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "MalformedBodyExample",
                        summary = "Missing or malformed gameId example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - No write access to this wishlist",
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
                    examples = [
                        ExampleObject(
                            name = "WishlistNotFoundExample",
                            summary = "Wishlist not found example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                        ExampleObject(
                            name = "GameNotFoundExample",
                            summary = "Game not found example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Game not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                    ]
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
        @Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID,
        @Valid @RequestBody request: AddWishlistItemRequest,
    ): ResponseEntity<WishlistItemDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(wishlistService.addItem(CurrentUser.id(), wishlistId, request))

    @Operation(summary = "Remove game from wishlist", description = "Remove a game from a wishlist. Allowed for the owner and, if shared, for its members.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204", description = "No Content - Game removed",
                content = [Content(schema = Schema(hidden = true))]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - No write access to this wishlist",
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
                responseCode = "404", description = "Not Found - Wishlist or wishlist item does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "WishlistNotFoundExample",
                            summary = "Wishlist not found example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                        ExampleObject(
                            name = "ItemNotFoundExample",
                            summary = "Wishlist item not found example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Wishlist item not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @DeleteMapping("/{wishlistId}/items/{itemId}")
    fun removeItem(
        @Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID,
        @Parameter(description = "Wishlist item id") @PathVariable itemId: UUID,
    ): ResponseEntity<Unit> {
        wishlistService.removeItem(CurrentUser.id(), wishlistId, itemId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "List wishlist items", description = "Paginated list of games in a wishlist, optionally filtered by name. Allowed if it is your own, a user's default wishlist, or a shared wishlist you are a member of.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of games in the wishlist",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid sort field",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidSortExample",
                        summary = "Invalid sort field example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field: rating\"}"
                    )]
                )]
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
    @GetMapping("/{wishlistId}/items")
    fun listItems(
        @Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Search by game name") @RequestParam(required = false) gameName: String?,
        @Parameter(description = "Sort field and direction, e.g. 'game.name-asc', 'createdAt-desc'") @RequestParam(required = false) sort: String?,
    ): ResponseEntity<PageDTO<WishlistItemDTO>> =
        ResponseEntity.ok(wishlistService.listItems(CurrentUser.id(), wishlistId, page, size, gameName, sort))

    @Operation(summary = "Check wishlist item status", description = "Check whether a game is in a specific wishlist. Allowed if it is your own, a user's default wishlist, or a shared wishlist you are a member of.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Wishlist item status",
                content = [Content(schema = Schema(implementation = WishlistItemStatusDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Missing or invalid game id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "MissingParameterExample",
                            summary = "Missing game id example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Missing required parameter 'gameId'\"}"
                        ),
                        ExampleObject(
                            name = "InvalidIdExample",
                            summary = "Invalid game id example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'gameId'\"}"
                        ),
                    ]
                )]
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
    @GetMapping("/{wishlistId}/items/status")
    fun getItemStatus(
        @Parameter(description = "Wishlist id") @PathVariable wishlistId: UUID,
        @Parameter(description = "Internal Let's Table id of the game") @RequestParam gameId: UUID,
    ): ResponseEntity<WishlistItemStatusDTO> =
        ResponseEntity.ok(wishlistService.getItemStatus(CurrentUser.id(), wishlistId, gameId))

    @Operation(summary = "Get my default wishlist", description = "Returns the default wishlist of the current user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Default wishlist",
                content = [Content(schema = Schema(implementation = WishlistDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Default wishlist does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "DefaultWishlistNotFoundExample",
                        summary = "Default wishlist not found example",
                        value = "{\"timestamp\":\"2026-10-30T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Default wishlist not found for user: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/default")
    fun getMyDefaultWishlist(): ResponseEntity<WishlistDTO> =
        ResponseEntity.ok(wishlistService.getMyDefaultWishlist(CurrentUser.id()))
}