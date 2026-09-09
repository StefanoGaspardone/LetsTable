package com.backend.controllers

import com.backend.models.dtos.DeleteAccountDTO
import com.backend.models.dtos.UserDTO
import com.backend.exceptions.ErrorResponse
import com.backend.security.CurrentUser
import com.backend.services.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Users", description = "Search users and manage the current user's account")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('USER')")
class UserController(
    private val userService: UserService,
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
    fun searchByUsername(@RequestParam query: String): List<UserDTO> =
        userService.searchByUsername(CurrentUser.id(), query)

    @Operation(summary = "Get user", description = "Get the public profile of any user by id.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - User profile",
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
    fun getUser(@PathVariable userId: UUID): UserDTO = userService.getUserById(userId)

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
    fun deleteMyAccount(): DeleteAccountDTO = userService.deleteAccount(CurrentUser.id())

    @Operation(summary = "Get my profile", description = "Get the public profile of the currently authenticated user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Your profile",
                content = [Content(schema = Schema(implementation = UserDTO::class))]
            ),
        ]
    )
    @GetMapping("/me")
    fun getMyProfile(): UserDTO = userService.getUserById(CurrentUser.id())
}