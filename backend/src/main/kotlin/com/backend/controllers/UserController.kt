package com.backend.controllers

import com.backend.exceptions.CannotAccessOwnResourceViaPublicEndpointException
import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.DeleteAccountDTO
import com.backend.models.dtos.MatchDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.dtos.UpdateUserRequest
import com.backend.models.dtos.UserAchievementDTO
import com.backend.models.dtos.UserDTO
import com.backend.models.dtos.UserProfileDTO
import com.backend.models.dtos.WishlistItemDTO
import com.backend.security.CurrentUser
import com.backend.services.FriendService
import com.backend.services.MatchService
import com.backend.services.UserAchievementService
import com.backend.services.UserService
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
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Users", description = "Search users, view public profiles and manage the current user's account")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('USER')")
class UserController(
    private val userService: UserService,
    private val matchService: MatchService,
    private val friendService: FriendService,
    private val wishlistService: WishlistService,
    private val userAchievementService: UserAchievementService,
) {

    @Operation(summary = "Search users", description = "Search active users by username (at least 2 characters, max 20 results), e.g. to add a player to a match. The current user is excluded.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Matching users",
                content = [Content(array = ArraySchema(schema = Schema(implementation = UserDTO::class)))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Missing query parameter",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "MissingParameterExample",
                        summary = "Missing query example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Missing required parameter 'query'\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/search")
    fun searchByUsername(@Parameter(description = "Part of the username to search", example = "mar") @RequestParam query: String): ResponseEntity<List<UserDTO>> =
        ResponseEntity.ok(userService.searchByUsername(CurrentUser.id(), query))

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
                responseCode = "400", description = "Bad Request - Invalid request data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ValidationExample",
                        summary = "Invalid username example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"username: size must be between 3 and 50\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - The specified avatarId does not exist or is not yours",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "AvatarNotFoundExample",
                        summary = "Avatar file not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"File not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
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

    @Operation(summary = "Delete my account", description = "Permanently anonymize the current user's account. Solo matches are deleted; matches involving other players are kept with this user shown as deleted. Sessions, push tokens, friendships, collection, owned wishlists, wishlist memberships and avatar files are removed.")
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
    fun getUser(@Parameter(description = "User id") @PathVariable userId: UUID): ResponseEntity<UserDTO> =
        ResponseEntity.ok(userService.getUserById(userId))

    @Operation(summary = "Get a user's public profile", description = "Account info, completed matches and wins of a user, plus the friendship status with the current user.")
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
    fun getPublicProfile(@Parameter(description = "User id") @PathVariable userId: UUID): ResponseEntity<UserProfileDTO> =
        ResponseEntity.ok(userService.getUserProfile(CurrentUser.id(), userId))

    @Operation(summary = "Get a user's matches", description = "Paginated list of completed matches created by, or involving as a player, the specified user. Cannot be used for your own id, use GET /matches instead.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of matches",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Own id used or invalid sort field",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "OwnResourceExample",
                            summary = "Own id example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Cannot access your own resource via this endpoint, use the dedicated endpoint instead\"}"
                        ),
                        ExampleObject(
                            name = "InvalidSortExample",
                            summary = "Invalid sort field example",
                            value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field: unknownField\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @GetMapping("/{userId}/matches")
    fun getUserMatches(
        @Parameter(description = "User id") @PathVariable userId: UUID,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Sort field and direction: playedAt, createdAt or durationMinutes, followed by -asc or -desc") @RequestParam(required = false) sort: String?,
    ): ResponseEntity<PageDTO<MatchDTO>> {
        requireOtherUser(userId)
        return ResponseEntity.ok(matchService.listMyMatches(userId, page, size, null, null, null, sort, onlyCompleted = true))
    }

    @Operation(summary = "Get a user's friends", description = "List all accepted friends of the specified user. Cannot be used for your own id, use GET /friends instead.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - List of friends",
                content = [Content(array = ArraySchema(schema = Schema(implementation = UserDTO::class)))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Own id used",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "OwnResourceExample",
                        summary = "Own id example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Cannot access your own resource via this endpoint, use the dedicated endpoint instead\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{userId}/friends")
    fun getUserFriends(@Parameter(description = "User id") @PathVariable userId: UUID): ResponseEntity<List<UserDTO>> {
        requireOtherUser(userId)
        return ResponseEntity.ok(friendService.listFriends(userId))
    }

    @Operation(summary = "Get a user's default wishlist", description = "Paginated list of games in the specified user's default wishlist, which is always visible. Cannot be used for your own id, use GET /wishlists instead.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Page of games in the default wishlist",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Own id used",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "OwnResourceExample",
                        summary = "Own id example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Cannot access your own resource via this endpoint, use the dedicated endpoint instead\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - Default wishlist not found",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "NotFoundExample",
                        summary = "Default wishlist not found example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Default wishlist not found for user: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{userId}/wishlist")
    fun getUserDefaultWishlist(
        @Parameter(description = "User id") @PathVariable userId: UUID,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageDTO<WishlistItemDTO>> {
        requireOtherUser(userId)
        return ResponseEntity.ok(wishlistService.getDefaultWishlistForUser(userId, page, size))
    }

    @Operation(summary = "Get a user's achievements", description = "Every achievement in the catalog with the specified user's progress and unlock status. Cannot be used for your own id, use GET /achievements/me instead.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Achievements with progress",
                content = [Content(array = ArraySchema(schema = Schema(implementation = UserAchievementDTO::class)))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Own id used",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "OwnResourceExample",
                        summary = "Own id example",
                        value = "{\"timestamp\":\"2026-08-19T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Cannot access your own resource via this endpoint, use the dedicated endpoint instead\"}"
                    )]
                )]
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
    @GetMapping("/{userId}/achievements")
    fun getUserAchievements(@Parameter(description = "User id") @PathVariable userId: UUID): ResponseEntity<List<UserAchievementDTO>> {
        requireOtherUser(userId)
        return ResponseEntity.ok(userAchievementService.getUserAchievements(userId))
    }

    private fun requireOtherUser(userId: UUID) {
        if(userId == CurrentUser.id()) {
            throw CannotAccessOwnResourceViaPublicEndpointException()
        }
    }
}