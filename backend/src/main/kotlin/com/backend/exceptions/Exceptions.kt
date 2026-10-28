package com.backend.exceptions

import java.util.UUID

// ============================================================
// 400 BAD REQUEST
// ============================================================

class InvalidOtpException: RuntimeException("Invalid OTP code")

class OtpExpiredException: RuntimeException("OTP code expired")

class CannotFriendSelfException: RuntimeException("Cannot send a friend request to yourself")

class CannotSuspendSelfException: RuntimeException("You cannot suspend your own account")

class WishlistNotSharedException(wishlistId: UUID): RuntimeException("Wishlist is not shared, cannot manage members: $wishlistId")

class CannotAddOwnerAsMemberException: RuntimeException("The wishlist owner is already implicitly a member")

class InvalidMatchPlayerIdentityException: RuntimeException("Each player must have exactly one of userId or guestName")

class InvalidMatchTeamsException(message: String): RuntimeException(message)

class InvalidMatchPlayersException(message: String): RuntimeException(message)

class InvalidExpansionForGameException(expansionId: UUID): RuntimeException("Expansion $expansionId does not belong to the selected game")

class InvalidSortException(field: String): RuntimeException("Invalid sort field: $field")

class InvalidFileTypeException(message: String): RuntimeException(message)

class InvalidRankIndexFileException(message: String): RuntimeException(message)

class CannotAccessOwnResourceViaPublicEndpointException: RuntimeException("Cannot access your own resource via this endpoint, use the dedicated endpoint instead")

// ============================================================
// 401 UNAUTHORIZED
// ============================================================

class InvalidCredentialsException: RuntimeException("Invalid credentials")

class RefreshTokenNotFoundException: RuntimeException("Refresh token not found")

class RefreshTokenExpiredOrRevokedException: RuntimeException("Refresh token expired or revoked")

class InvalidSchedulerTokenException: RuntimeException("Invalid scheduler token")

// ============================================================
// 403 FORBIDDEN
// ============================================================

class AccountNotActivatedException: RuntimeException("Account is not activated")

class NotFriendRequestReceiverException: RuntimeException("Only the receiver can respond to this friend request")

class NotFriendRequestSenderException: RuntimeException("Only the sender can cancel this friend request")

class NotCollectionItemOwnerException: RuntimeException("You do not own this collection item")

class NotWishlistOwnerException: RuntimeException("Only the wishlist owner can perform this action")

class NotWishlistOwnerOrMemberException: RuntimeException("You do not have access to this wishlist")

class NotMatchCreatorException: RuntimeException("Only the match creator can perform this action")

// ============================================================
// 404 NOT FOUND
// ============================================================

class UserNotFoundException(userId: UUID): RuntimeException("User not found: $userId")

class UserNotFoundByIdentifierException(identifier: String): RuntimeException("User not found for identifier: $identifier")

class EmailVerificationNotFoundException: RuntimeException("No pending email verification")

class PasswordResetNotFoundException: RuntimeException("No pending password reset")

class GameNotFoundException(gameId: UUID): RuntimeException("Game not found: $gameId")

class GameNotFoundOnBggException(bggId: Long): RuntimeException("Game not found on BoardGameGeek with id: $bggId")

class FriendRequestNotFoundException(requestId: UUID): RuntimeException("Friend request not found: $requestId")

class FriendshipNotFoundException(userId: UUID): RuntimeException("No friendship found with user: $userId")

class CollectionItemNotFoundException(itemId: UUID): RuntimeException("Collection item not found: $itemId")

class WishlistNotFoundException(wishlistId: UUID): RuntimeException("Wishlist not found: $wishlistId")

class DefaultWishlistNotFoundException(userId: UUID): RuntimeException("Default wishlist not found for user: $userId")

class WishlistItemNotFoundException(itemId: UUID): RuntimeException("Wishlist item not found: $itemId")

class UserNotWishlistMemberException(userId: UUID): RuntimeException("User is not a member of this wishlist: $userId")

class MatchNotFoundException(matchId: UUID): RuntimeException("Match not found: $matchId")

class UploadedFileNotFoundException(id: UUID): RuntimeException("File not found: $id")

class StorageNotFoundException(objectKey: String): RuntimeException("Object not found in storage: $objectKey")

// ============================================================
// 409 CONFLICT
// ============================================================

class EmailAlreadyTakenException(email: String): RuntimeException("Email already taken: $email")

class UsernameAlreadyTakenException(username: String): RuntimeException("Username already taken: $username")

class FriendRequestAlreadyExistsException(receiverId: UUID): RuntimeException("A pending friend request already exists with user: $receiverId")

class AlreadyFriendsException(userId: UUID): RuntimeException("Already friends with user: $userId")

class GameAlreadyInCollectionException(gameId: UUID): RuntimeException("Game already in collection: $gameId")

class GameAlreadyInWishlistException(gameId: UUID): RuntimeException("Game already in wishlist: $gameId")

class UserAlreadyWishlistMemberException(userId: UUID): RuntimeException("User is already a member of this wishlist: $userId")

class CannotModifyDefaultWishlistException: RuntimeException("The default wishlist cannot be deleted or shared")

class InvalidAccountStatusTransitionException(message: String): RuntimeException(message)

// ============================================================
// 429 TOO MANY REQUESTS
// ============================================================

class OtpMaxAttemptsExceededException: RuntimeException("Maximum OTP attempts exceeded")

class OtpResendCooldownException(secondsRemaining: Long): RuntimeException("OTP resend cooldown active, try again in $secondsRemaining seconds")

// ============================================================
// 502 BAD GATEWAY
// ============================================================

class BggRequestFailedException(cause: Throwable): RuntimeException("Failed to reach BoardGameGeek API", cause)

class PushNotificationSendException(cause: Throwable): RuntimeException("Failed to send push notification", cause)

class StorageWriteException(objectKey: String): RuntimeException("Failed to write object to storage: $objectKey")