package com.backend.controllers

import com.backend.exceptions.CannotAccessOwnResourceViaPublicEndpointException
import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.*
import com.backend.security.CurrentUser
import com.backend.services.FriendService
import com.backend.services.MatchService
import com.backend.services.UserService
import com.backend.services.WishlistService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@Tag(name = "Users", description = "Search users and manage the current user's account")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('USER')")
class UserController(
    private val userService: UserService,
    private val matchService: MatchService,
    private val friendService: FriendService,
    private val wishlistService: WishlistService,
) {

    @Operation(summary = "Search users", description = "Search active users by username, e.g. to add a player to a match.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Matching users",
                content = [Content(schema = Schema(implementation = UserDTO::class))]
            ),
        ]
    )
    @GetMapping("/search")
    fun searchByUsername(@RequestParam query: String): ResponseEntity<List<UserDTO>> =
        ResponseEntity.ok(userService.searchByUsername(CurrentUser.id(), query))

    @Operation(summary = "Get user", description = "Get the public account information of any user by id.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - User account information",
                content = [Content(schema = Schema(implementation = UserDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - User does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "User not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: UUID): ResponseEntity<UserDTO> =
        ResponseEntity.ok(userService.getUserById(userId))

    @Operation(summary = "Get a user's public profile", description = "Get a user's public profile: their account info, win/loss stats, and recent matches. Visible to any authenticated user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Public profile",
                content = [Content(schema = Schema(implementation = UserProfileDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - User does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "User not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{userId}/profile")
    fun getPublicProfile(@PathVariable userId: UUID): ResponseEntity<UserProfileDTO> =
        ResponseEntity.ok(userService.getUserProfile(CurrentUser.id(), userId))

    @Operation(summary = "Delete my account", description = "Permanently anonymize the current user's account. Solo matches are deleted; matches involving other players are kept with this user shown as deleted.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Account deleted",
                content = [Content(schema = Schema(implementation = DeleteAccountDTO::class))]
            ),
        ]
    )
    @DeleteMapping("/me")
    fun deleteMyAccount(): ResponseEntity<DeleteAccountDTO> =
        ResponseEntity.ok(userService.deleteAccount(CurrentUser.id()))

    @Operation(summary = "Get my profile", description = "Get the account information of the currently authenticated user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Your account information",
                content = [Content(schema = Schema(implementation = UserDTO::class))]
            ),
        ]
    )
    @GetMapping("/me")
    fun getMyProfile(): ResponseEntity<UserDTO> =
        ResponseEntity.ok(userService.getUserById(CurrentUser.id()))

    @Operation(summary = "Update my profile", description = "Update the current user's username, notifications preference, and/or avatar. Fields left null in the request are not modified.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Updated profile",
                content = [Content(schema = Schema(implementation = UserDTO::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - The specified avatarId does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AvatarNotFoundExample",
                        summary = "Avatar file not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"File not found\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Username already taken",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UsernameTakenExample",
                        summary = "Username already taken example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Username already taken: marco2\"}"
                    )]
                )]
            ),
        ]
    )
    @PatchMapping("/me")
    fun updateMyProfile(@Valid @RequestBody request: UpdateUserRequest): ResponseEntity<UserDTO> =
        ResponseEntity.ok(userService.updateProfile(CurrentUser.id(), request))

    @Operation(summary = "Get a user's matches", description = "Paginated list of matches created by, or involving as a player, the specified user. Cannot be used for your own id, use GET /matches instead.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of matches",
                content = [Content(schema = Schema(implementation = MatchDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Cannot query your own matches via this endpoint",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )

    @GetMapping("/{userId}/matches")
    fun getUserMatches(@PathVariable userId: UUID, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int, @RequestParam(required = false) sort: String?): PageDTO<MatchDTO> {
        if(userId == CurrentUser.id()) {
            throw CannotAccessOwnResourceViaPublicEndpointException()
        }

        return matchService.listMyMatches(userId, page, size, null, null, null, sort, true)
    }

    @Operation(summary = "Get a user's friends", description = "List all accepted friends of the specified user. Cannot be used for your own id, use GET /friends instead.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of friends",
                content = [Content(schema = Schema(implementation = UserDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Cannot query your own friends via this endpoint",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )
    @GetMapping("/{userId}/friends")
    fun getUserFriends(@PathVariable userId: UUID): List<UserDTO> {
        if(userId == CurrentUser.id()) {
            throw CannotAccessOwnResourceViaPublicEndpointException()
        }

        return friendService.listFriends(userId)
    }

    @Operation(summary = "Get a user's default wishlist", description = "Paginated list of games in the specified user's default wishlist, which is always publicly visible. Cannot be used for your own id, use GET /wishlists instead.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of games in the default wishlist",
                content = [Content(schema = Schema(implementation = WishlistItemDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Cannot query your own wishlist via this endpoint",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Default wishlist not found",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ]
    )
    @GetMapping("/{userId}/wishlist")
    fun getUserDefaultWishlist(
        @PathVariable userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageDTO<WishlistItemDTO> {
        if(userId == CurrentUser.id()) {
            throw CannotAccessOwnResourceViaPublicEndpointException()
        }

        return wishlistService.getDefaultWishlistForUser(userId, page, size)
    }
}