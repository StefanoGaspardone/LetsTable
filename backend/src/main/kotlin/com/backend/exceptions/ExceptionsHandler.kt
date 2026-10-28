package com.backend.exceptions

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.time.Instant
import org.springframework.web.ErrorResponse as SpringErrorResponse

@Schema(description = "Standard error response")
data class ErrorResponse(
    @field:Schema(description = "Timestamp when the error occurred")
    val timestamp: Instant = Instant.now(),

    @field:Schema(description = "HTTP status code")
    val status: Int,

    @field:Schema(description = "HTTP reason phrase")
    val error: String,

    @field:Schema(description = "Human-readable error message")
    val message: String,
)

@RestControllerAdvice
class ExceptionsHandler {

    private fun buildResponse(status: HttpStatus, message: String): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
            ),
            status,
        )

    private fun buildResponse(status: HttpStatus, e: Exception): ResponseEntity<ErrorResponse> {
        val fallbackMessage = e::class.simpleName
            ?.removeSuffix("Exception")
            ?.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            ?.replaceFirstChar { it.uppercase() }
            ?: status.reasonPhrase

        val finalMessage = e.message.takeIf { !it.isNullOrBlank() } ?: fallbackMessage
        return buildResponse(status, finalMessage)
    }

    // ============================================================
    // 400 BAD REQUEST
    // ============================================================

    @ExceptionHandler(
        InvalidOtpException::class,
        OtpExpiredException::class,
        CannotFriendSelfException::class,
        CannotSuspendSelfException::class,
        WishlistNotSharedException::class,
        CannotAddOwnerAsMemberException::class,
        InvalidMatchPlayerIdentityException::class,
        InvalidMatchTeamsException::class,
        InvalidMatchPlayersException::class,
        InvalidExpansionForGameException::class,
        InvalidSortException::class,
        InvalidFileTypeException::class,
        InvalidRankIndexFileException::class,
        CannotAccessOwnResourceViaPublicEndpointException::class,
    )
    fun handleBadRequest(ex: RuntimeException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Invalid request data" }

        return buildResponse(HttpStatus.BAD_REQUEST, details)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedBody(ex: HttpMessageNotReadableException) =
        buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException) =
        buildResponse(HttpStatus.BAD_REQUEST, "Invalid value for parameter '${ex.name}'")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(ex: MissingServletRequestParameterException) =
        buildResponse(HttpStatus.BAD_REQUEST, "Missing required parameter '${ex.parameterName}'")

    // ============================================================
    // 401 UNAUTHORIZED
    // ============================================================

    @ExceptionHandler(
        InvalidCredentialsException::class,
        RefreshTokenNotFoundException::class,
        RefreshTokenExpiredOrRevokedException::class,
        InvalidSchedulerTokenException::class,
    )
    fun handleUnauthorized(ex: RuntimeException) =
        buildResponse(HttpStatus.UNAUTHORIZED, ex)

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException) =
        buildResponse(HttpStatus.UNAUTHORIZED, "Authentication required")

    // ============================================================
    // 403 FORBIDDEN
    // ============================================================

    @ExceptionHandler(
        AccountNotActivatedException::class,
        NotFriendRequestReceiverException::class,
        NotFriendRequestSenderException::class,
        NotCollectionItemOwnerException::class,
        NotWishlistOwnerException::class,
        NotWishlistOwnerOrMemberException::class,
        NotMatchCreatorException::class,
    )
    fun handleForbidden(ex: RuntimeException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException) =
        buildResponse(HttpStatus.FORBIDDEN, "Access denied")

    // ============================================================
    // 404 NOT FOUND
    // ============================================================

    @ExceptionHandler(
        UserNotFoundException::class,
        UserNotFoundByIdentifierException::class,
        EmailVerificationNotFoundException::class,
        PasswordResetNotFoundException::class,
        GameNotFoundException::class,
        GameNotFoundOnBggException::class,
        FriendRequestNotFoundException::class,
        FriendshipNotFoundException::class,
        CollectionItemNotFoundException::class,
        WishlistNotFoundException::class,
        DefaultWishlistNotFoundException::class,
        WishlistItemNotFoundException::class,
        UserNotWishlistMemberException::class,
        MatchNotFoundException::class,
        UploadedFileNotFoundException::class,
        StorageNotFoundException::class,
    )
    fun handleNotFound(ex: RuntimeException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    // ============================================================
    // 409 CONFLICT
    // ============================================================

    @ExceptionHandler(
        EmailAlreadyTakenException::class,
        UsernameAlreadyTakenException::class,
        FriendRequestAlreadyExistsException::class,
        AlreadyFriendsException::class,
        GameAlreadyInCollectionException::class,
        GameAlreadyInWishlistException::class,
        UserAlreadyWishlistMemberException::class,
        CannotModifyDefaultWishlistException::class,
        InvalidAccountStatusTransitionException::class,
    )
    fun handleConflict(ex: RuntimeException) =
        buildResponse(HttpStatus.CONFLICT, ex)

    // ============================================================
    // 413 PAYLOAD TOO LARGE
    // ============================================================

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(ex: MaxUploadSizeExceededException) =
        buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size")

    // ============================================================
    // 429 TOO MANY REQUESTS
    // ============================================================

    @ExceptionHandler(
        OtpMaxAttemptsExceededException::class,
        OtpResendCooldownException::class,
    )
    fun handleTooManyRequests(ex: RuntimeException) =
        buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex)

    // ============================================================
    // 502 BAD GATEWAY
    // ============================================================

    @ExceptionHandler(
        BggRequestFailedException::class,
        PushNotificationSendException::class,
        StorageWriteException::class,
    )
    fun handleBadGateway(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.BAD_GATEWAY, ex)

    // ============================================================
    // 500 INTERNAL SERVER ERROR
    // ============================================================

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        if(ex is SpringErrorResponse) {
            val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
            return buildResponse(status, ex.body.detail ?: status.reasonPhrase)
        }

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
    }
}