package com.backend.exceptions

import java.util.UUID

class EmailAlreadyTakenException(email: String): RuntimeException("Email already taken: $email")

class UsernameAlreadyTakenException(username: String): RuntimeException("Username already taken: $username")

class InvalidCredentialsException: RuntimeException("Invalid credentials")

class AccountNotActivatedException: RuntimeException("Account is not activated")

class UserNotFoundByIdentifierException(identifier: String): RuntimeException("User not found for identifier: $identifier")

class EmailVerificationNotFoundException(userId: UUID) : RuntimeException("Email verification not found for user: $userId")

class PasswordResetNotFoundException(userId: UUID): RuntimeException("Password reset not found for user: $userId")

class InvalidOtpException: RuntimeException("Invalid OTP code")

class OtpExpiredException: RuntimeException("OTP code expired")

class OtpMaxAttemptsExceededException: RuntimeException("Maximum OTP attempts exceeded")

class OtpResendCooldownException(secondsRemaining: Long): RuntimeException("OTP resend cooldown active, try again in $secondsRemaining seconds")

class RefreshTokenNotFoundException: RuntimeException("Refresh token not found")

class RefreshTokenExpiredOrRevokedException: RuntimeException("Refresh token expired or revoked")

class BggRequestFailedException(cause: Throwable) : RuntimeException("Failed to reach BoardGameGeek API", cause)

class GameNotFoundOnBggException(bggId: Long) : RuntimeException("Game not found on BoardGameGeek with id: $bggId")

class CannotFriendSelfException: RuntimeException("Cannot send a friend request to yourself")

class FriendRequestAlreadyExistsException(receiverId: UUID): RuntimeException("A pending friend request already exists with user: $receiverId")

class AlreadyFriendsException(userId: UUID): RuntimeException("Already friends with user: $userId")

class FriendRequestNotFoundException(requestId: UUID): RuntimeException("Friend request not found: $requestId")

class NotFriendRequestReceiverException: RuntimeException("Only the receiver can respond to this friend request")

class NotFriendRequestSenderException: RuntimeException("Only the sender can cancel this friend request")

class FriendshipNotFoundException(userId: UUID): RuntimeException("No friendship found with user: $userId")

class GameAlreadyInCollectionException(gameId: UUID): RuntimeException("Game already in collection: $gameId")

class CollectionItemNotFoundException(itemId: UUID): RuntimeException("Collection item not found: $itemId")

class GameNotFoundException(gameId: UUID): RuntimeException("Game not found: $gameId")

class NotCollectionItemOwnerException: RuntimeException("You do not own this collection item")

class WishlistNotFoundException(wishlistId: UUID): RuntimeException("Wishlist not found: $wishlistId")

class NotWishlistOwnerException: RuntimeException("Only the wishlist owner can perform this action")

class NotWishlistOwnerOrMemberException: RuntimeException("You do not have access to this wishlist")

class WishlistNotSharedException(wishlistId: UUID): RuntimeException("Wishlist is not shared, cannot manage members: $wishlistId")

class CannotAddOwnerAsMemberException: RuntimeException("The wishlist owner is already implicitly a member")

class UserAlreadyWishlistMemberException(userId: UUID): RuntimeException("User is already a member of this wishlist: $userId")

class UserNotWishlistMemberException(userId: UUID): RuntimeException("User is not a member of this wishlist: $userId")

class GameAlreadyInWishlistException(gameId: UUID): RuntimeException("Game already in wishlist: $gameId")

class WishlistItemNotFoundException(itemId: UUID): RuntimeException("Wishlist item not found: $itemId")

class MatchNotFoundException(matchId: UUID): RuntimeException("Match not found: $matchId")

class NotMatchCreatorException: RuntimeException("Only the match creator can perform this action")

class InvalidMatchPlayerIdentityException: RuntimeException("Each player must have exactly one of userId or guestName")

class InvalidMatchTeamsException(message: String): RuntimeException(message)

class InvalidMatchPlayersException(message: String): RuntimeException(message)

class UserNotFoundException(userId: UUID): RuntimeException("User not found: $userId")

class CannotModifyDefaultWishlistException: RuntimeException("The default wishlist cannot be deleted or shared")

class PushNotificationSendException(cause: Throwable): RuntimeException("Failed to send push notification", cause)

class InvalidSortException(field: String): RuntimeException("Invalid sort field: $field")

class UploadedFileNotFoundException(id: UUID) : RuntimeException("File not found: $id")

class InvalidFileTypeException(message: String) : RuntimeException(message)

class StorageWriteException(objectKey: String) : RuntimeException("Failed to write object to storage: $objectKey")

class StorageNotFoundException(objectKey: String) : RuntimeException("Object not found in storage: $objectKey")