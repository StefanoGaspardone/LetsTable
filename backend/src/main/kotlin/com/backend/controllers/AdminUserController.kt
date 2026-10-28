package com.backend.controllers

import com.backend.exceptions.ErrorResponse
import com.backend.models.dtos.*
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.security.CurrentUser
import com.backend.services.AdminUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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

@Tag(name = "Admin - Users", description = "Administrative endpoints for managing user accounts")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(
    private val adminUserService: AdminUserService,
) {

    @Operation(summary = "List users", description = "Lists users with optional filters by role, account status, and search term (matches username or email).")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - Paginated list of users",
                content = [Content(schema = Schema(implementation = PageDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid sort field or filter value",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidSortExample",
                            summary = "Invalid sort field example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid sort field: email\"}"
                        ),
                        ExampleObject(
                            name = "InvalidFilterExample",
                            summary = "Invalid enum filter example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'status'\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping
    fun listUsers(
        @Parameter(description = "Filter by user role") @RequestParam(required = false) role: UserRole?,
        @Parameter(description = "Filter by account status") @RequestParam(required = false) status: AccountStatus?,
        @Parameter(description = "Search by username or email") @RequestParam(required = false) search: String?,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Sort field and direction, e.g. 'username-asc', 'createdAt-desc'") @RequestParam(required = false) sort: String?,
    ): ResponseEntity<PageDTO<AdminUserDTO>> =
        ResponseEntity.ok(adminUserService.listUsers(role, status, search, page, size, sort))

    @Operation(summary = "Get user detail", description = "Returns detailed information about a user, including activity stats and recent matches.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - User detail",
                content = [Content(schema = Schema(implementation = AdminUserDetailDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid user id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidIdExample",
                        summary = "Invalid path parameter example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'userId'\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - User does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UserNotFoundExample",
                        summary = "User not found example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
        ]
    )
    @GetMapping("/{userId}")
    fun getUserDetail(
        @Parameter(description = "ID of the user") @PathVariable userId: UUID,
    ): ResponseEntity<AdminUserDetailDTO> =
        ResponseEntity.ok(adminUserService.getUserDetail(userId))

    @Operation(summary = "Suspend a user", description = "Suspends an active user's account. Admins cannot suspend their own account.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - User suspended",
                content = [Content(schema = Schema(implementation = AdminUserDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Cannot suspend own account, or invalid user id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "CannotSuspendSelfExample",
                            summary = "Attempted to suspend own account",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"You cannot suspend your own account\"}"
                        ),
                        ExampleObject(
                            name = "InvalidIdExample",
                            summary = "Invalid path parameter example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'userId'\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - User does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UserNotFoundExample",
                        summary = "User not found example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Account is not currently active",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidTransitionExample",
                        summary = "Account is not active",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Only active accounts can be suspended\"}"
                    )]
                )]
            ),
        ]
    )
    @PatchMapping("/{userId}/suspend")
    fun suspendUser(
        @Parameter(description = "ID of the user to suspend") @PathVariable userId: UUID,
    ): ResponseEntity<AdminUserDTO> =
        ResponseEntity.ok(adminUserService.suspendUser(CurrentUser.id(), userId))

    @Operation(summary = "Reactivate a user", description = "Reactivates a suspended user's account.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Ok - User reactivated",
                content = [Content(schema = Schema(implementation = AdminUserDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid user id",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidIdExample",
                        summary = "Invalid path parameter example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid value for parameter 'userId'\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "404", description = "Not Found - User does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UserNotFoundExample",
                        summary = "User not found example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"User not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Account is not currently suspended",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "InvalidTransitionExample",
                        summary = "Account is not suspended",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Only suspended accounts can be reactivated\"}"
                    )]
                )]
            ),
        ]
    )
    @PatchMapping("/{userId}/reactivate")
    fun reactivateUser(
        @Parameter(description = "ID of the user to reactivate") @PathVariable userId: UUID,
    ): ResponseEntity<AdminUserDTO> =
        ResponseEntity.ok(adminUserService.reactivateUser(userId))

    @Operation(summary = "Create an admin account", description = "Creates a new administrator account, active immediately (no email verification required).")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created - Admin account created",
                content = [Content(schema = Schema(implementation = AdminUserDTO::class))]
            ),
            ApiResponse(
                responseCode = "400", description = "Bad Request - Invalid or malformed request data",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "InvalidDataExample",
                            summary = "Validation error example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"email: must be a well-formed email address\"}"
                        ),
                        ExampleObject(
                            name = "MalformedBodyExample",
                            summary = "Malformed JSON example",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Malformed request body\"}"
                        ),
                    ]
                )]
            ),
            ApiResponse(
                responseCode = "401", description = "Unauthorized - Missing or invalid access token",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "UnauthorizedExample",
                        summary = "Authentication required example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "403", description = "Forbidden - User is not an admin",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ForbiddenExample",
                        summary = "Access denied example",
                        value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409", description = "Conflict - Email or username already taken",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [
                        ExampleObject(
                            name = "EmailTakenExample",
                            summary = "Email already taken",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Email already taken: newadmin@example.com\"}"
                        ),
                        ExampleObject(
                            name = "UsernameTakenExample",
                            summary = "Username already taken",
                            value = "{\"timestamp\":\"2026-09-12T12:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Username already taken: newadmin\"}"
                        ),
                    ]
                )]
            ),
        ]
    )
    @PostMapping("/admin")
    fun createAdmin(@Valid @RequestBody request: CreateAdminRequest): ResponseEntity<AdminUserDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.createAdmin(request))
}