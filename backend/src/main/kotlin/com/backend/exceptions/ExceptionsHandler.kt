package com.backend.exceptions

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

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

    private fun buildResponse(status: HttpStatus, e: Exception): ResponseEntity<ErrorResponse> {
        val fallbackMessage = e::class.simpleName
            ?.removeSuffix("Exception")
            ?.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            ?.replaceFirstChar { it.uppercase() }
            ?: status.reasonPhrase

        val finalMessage = e.message.takeIf { !it.isNullOrBlank() } ?: fallbackMessage

        val errorResponse = ErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = finalMessage
        )
        return ResponseEntity(errorResponse, status)
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException) =
        buildResponse(HttpStatus.UNAUTHORIZED, ex)

    @ExceptionHandler(AccountNotActivatedException::class)
    fun handleAccountNotActivated(ex: AccountNotActivatedException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(EmailAlreadyTakenException::class)
    fun handleEmailAlreadyTaken(ex: EmailAlreadyTakenException) =
        buildResponse(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(UsernameAlreadyTakenException::class)
    fun handleUsernameAlreadyTaken(ex: UsernameAlreadyTakenException) =
        buildResponse(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(UserNotFoundByIdentifierException::class)
    fun handleUserNotFoundByIdentifier(ex: UserNotFoundByIdentifierException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(EmailVerificationNotFoundException::class)
    fun handleEmailVerificationNotFound(ex: EmailVerificationNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(PasswordResetNotFoundException::class)
    fun handlePasswordResetNotFound(ex: PasswordResetNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(RefreshTokenNotFoundException::class)
    fun handleRefreshTokenNotFound(ex: RefreshTokenNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(RefreshTokenExpiredOrRevokedException::class)
    fun handleRefreshTokenExpiredOrRevoked(ex: RefreshTokenExpiredOrRevokedException) =
        buildResponse(HttpStatus.UNAUTHORIZED, ex)

    @ExceptionHandler(InvalidOtpException::class)
    fun handleInvalidOtp(ex: InvalidOtpException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(OtpExpiredException::class)
    fun handleOtpExpired(ex: OtpExpiredException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(OtpMaxAttemptsExceededException::class)
    fun handleOtpMaxAttemptsExceeded(ex: OtpMaxAttemptsExceededException) =
        buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex)

    @ExceptionHandler(OtpResendCooldownException::class)
    fun handleOtpResendCooldown(ex: OtpResendCooldownException) =
        buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex)

    @ExceptionHandler(GameNotFoundOnBggException::class)
    fun handleGameNotFoundOnBgg(ex: GameNotFoundOnBggException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(BggRequestFailedException::class)
    fun handleBggRequestFailed(ex: BggRequestFailedException) =
        buildResponse(HttpStatus.BAD_GATEWAY, ex)

    @ExceptionHandler(CannotFriendSelfException::class)
    fun handleCannotFriendSelf(ex: CannotFriendSelfException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(FriendRequestAlreadyExistsException::class)
    fun handleFriendRequestAlreadyExists(ex: FriendRequestAlreadyExistsException) =
        buildResponse(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(AlreadyFriendsException::class)
    fun handleAlreadyFriends(ex: AlreadyFriendsException) =
        buildResponse(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(FriendRequestNotFoundException::class)
    fun handleFriendRequestNotFound(ex: FriendRequestNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(FriendshipNotFoundException::class)
    fun handleFriendshipNotFound(ex: FriendshipNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(NotFriendRequestReceiverException::class)
    fun handleNotFriendRequestReceiver(ex: NotFriendRequestReceiverException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(NotFriendRequestSenderException::class)
    fun handleNotFriendRequestSender(ex: NotFriendRequestSenderException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(GameAlreadyInCollectionException::class)
    fun handleGameAlreadyInCollection(ex: GameAlreadyInCollectionException) =
        buildResponse(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(CollectionItemNotFoundException::class)
    fun handleCollectionItemNotFound(ex: CollectionItemNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(GameNotFoundException::class)
    fun handleGameNotFound(ex: GameNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(WishlistNotFoundException::class)
    fun handleWishlistNotFound(ex: WishlistNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(NotWishlistOwnerException::class)
    fun handleNotWishlistOwner(ex: NotWishlistOwnerException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(NotWishlistOwnerOrMemberException::class)
    fun handleNotWishlistOwnerOrMember(ex: NotWishlistOwnerOrMemberException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(WishlistNotSharedException::class)
    fun handleWishlistNotShared(ex: WishlistNotSharedException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(CannotAddOwnerAsMemberException::class)
    fun handleCannotAddOwnerAsMember(ex: CannotAddOwnerAsMemberException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(UserAlreadyWishlistMemberException::class)
    fun handleUserAlreadyWishlistMember(ex: UserAlreadyWishlistMemberException) =
        buildResponse(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(NotCollectionItemOwnerException::class)
    fun handleNotCollectionItemOwner(ex: NotCollectionItemOwnerException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(UserNotWishlistMemberException::class)
    fun handleUserNotWishlistMember(ex: UserNotWishlistMemberException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(GameAlreadyInWishlistException::class)
    fun handleGameAlreadyInWishlist(ex: GameAlreadyInWishlistException) =
        buildResponse(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(WishlistItemNotFoundException::class)
    fun handleWishlistItemNotFound(ex: WishlistItemNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(MatchNotFoundException::class)
    fun handleMatchNotFound(ex: MatchNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(NotMatchCreatorException::class)
    fun handleNotMatchCreator(ex: NotMatchCreatorException) =
        buildResponse(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(InvalidMatchPlayerIdentityException::class)
    fun handleInvalidMatchPlayerIdentity(ex: InvalidMatchPlayerIdentityException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(InvalidMatchTeamsException::class)
    fun handleInvalidMatchTeams(ex: InvalidMatchTeamsException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(InvalidMatchPlayersException::class)
    fun handleInvalidMatchPlayers(ex: InvalidMatchPlayersException) =
        buildResponse(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(CannotModifyDefaultWishlistException::class)
    fun handleCannotModifyDefaultWishlist(ex: CannotModifyDefaultWishlistException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(PushNotificationSendException::class)
    fun handlePushNotificationSend(ex: PushNotificationSendException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(InvalidSortException::class)
    fun handleInvalidSort(ex: InvalidSortException) =
        buildResponse(HttpStatus.NOT_FOUND, ex)
}