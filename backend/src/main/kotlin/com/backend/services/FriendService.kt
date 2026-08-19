package com.backend.services

import com.backend.exceptions.AlreadyFriendsException
import com.backend.exceptions.CannotFriendSelfException
import com.backend.exceptions.FriendRequestAlreadyExistsException
import com.backend.exceptions.FriendRequestNotFoundException
import com.backend.exceptions.FriendshipNotFoundException
import com.backend.exceptions.NotFriendRequestReceiverException
import com.backend.exceptions.NotFriendRequestSenderException
import com.backend.exceptions.UserNotFoundByIdentifierException
import com.backend.models.dtos.FriendRequestDTO
import com.backend.models.dtos.SendFriendRequestRequest
import com.backend.models.dtos.UserDTO
import com.backend.models.entities.FriendRequest
import com.backend.models.entities.User
import com.backend.models.enums.FriendRequestStatus
import com.backend.repositories.FriendRequestRepository
import com.backend.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FriendService(
    private val friendRequestRepository: FriendRequestRepository,
    private val userRepository: UserRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun sendRequest(senderId: UUID, request: SendFriendRequestRequest): FriendRequestDTO {
        logger.debug("\n\t[DEBUG] [friend_service][send_request] User {} sending friend request to {}", senderId, request.receiverId)

        try {
            if(senderId == request.receiverId) {
                throw CannotFriendSelfException()
            }

            val sender = userRepository.findById(senderId)
                .orElseThrow { UserNotFoundByIdentifierException(senderId.toString()) }
            val receiver = userRepository.findById(request.receiverId)
                .orElseThrow { UserNotFoundByIdentifierException(request.receiverId.toString()) }

            friendRequestRepository.findFriendshipBetween(senderId, request.receiverId).ifPresent {
                throw AlreadyFriendsException(request.receiverId)
            }

            friendRequestRepository.findBySenderIdAndReceiverId(senderId, request.receiverId).ifPresent {
                throw FriendRequestAlreadyExistsException(request.receiverId)
            }

            val reverseRequest = friendRequestRepository.findBySenderIdAndReceiverId(request.receiverId, senderId)
            if(reverseRequest.isPresent) {
                val existing = reverseRequest.get()
                existing.status = FriendRequestStatus.ACCEPTED
                val saved = friendRequestRepository.save(existing)

                logger.info("\n\t[INFO] [friend_service][send_request] Reverse request found, auto-accepted friendship between {} and {}", senderId, request.receiverId)
                return FriendRequestDTO.from(saved)
            }

            val newRequest = FriendRequest(sender = sender, receiver = receiver, status = FriendRequestStatus.PENDING)
            val saved = friendRequestRepository.save(newRequest)

            logger.info("\n\t[INFO] [friend_service][send_request] Friend request sent from {} to {}", senderId, request.receiverId)
            return FriendRequestDTO.from(saved)
        } catch(e: CannotFriendSelfException) {
            logger.warn("\n\t[WARN] [friend_service][send_request] User {} tried to friend themselves", senderId)
            throw e
        } catch(e: AlreadyFriendsException) {
            logger.warn("\n\t[WARN] [friend_service][send_request] User {} already friends with {}", senderId, request.receiverId)
            throw e
        } catch(e: FriendRequestAlreadyExistsException) {
            logger.warn("\n\t[WARN] [friend_service][send_request] Duplicate request from {} to {}", senderId, request.receiverId)
            throw e
        } catch(e: UserNotFoundByIdentifierException) {
            logger.warn("\n\t[WARN] [friend_service][send_request] User not found while sending request from {} to {}", senderId, request.receiverId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_service][send_request] Error sending friend request from {} to {}: {}", senderId, request.receiverId, e.message)
            throw e
        }
    }

    @Transactional
    fun acceptRequest(currentUserId: UUID, requestId: UUID): FriendRequestDTO {
        logger.debug("\n\t[DEBUG] [friend_service][accept_request] User {} accepting request {}", currentUserId, requestId)

        try {
            val request = friendRequestRepository.findById(requestId)
                .orElseThrow { FriendRequestNotFoundException(requestId) }

            if(request.receiver.id != currentUserId) {
                throw NotFriendRequestReceiverException()
            }

            request.status = FriendRequestStatus.ACCEPTED
            val saved = friendRequestRepository.save(request)

            logger.info("\n\t[INFO] [friend_service][accept_request] Request {} accepted by {}", requestId, currentUserId)
            return FriendRequestDTO.from(saved)
        } catch(e: FriendRequestNotFoundException) {
            logger.warn("\n\t[WARN] [friend_service][accept_request] Request {} not found", requestId)
            throw e
        } catch(e: NotFriendRequestReceiverException) {
            logger.warn("\n\t[WARN] [friend_service][accept_request] User {} is not the receiver of request {}", currentUserId, requestId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_service][accept_request] Error accepting request {}: {}", requestId, e.message)
            throw e
        }
    }

    @Transactional
    fun rejectRequest(currentUserId: UUID, requestId: UUID) {
        logger.debug("\n\t[DEBUG] [friend_service][reject_request] User {} rejecting request {}", currentUserId, requestId)

        try {
            val request = friendRequestRepository.findById(requestId)
                .orElseThrow { FriendRequestNotFoundException(requestId) }

            if (request.receiver.id != currentUserId) {
                throw NotFriendRequestReceiverException()
            }

            friendRequestRepository.delete(request)

            logger.info("\n\t[INFO] [friend_service][reject_request] Request {} rejected by {}", requestId, currentUserId)
        } catch(e: FriendRequestNotFoundException) {
            logger.warn("\n\t[WARN] [friend_service][reject_request] Request {} not found", requestId)
            throw e
        } catch(e: NotFriendRequestReceiverException) {
            logger.warn("\n\t[WARN] [friend_service][reject_request] User {} is not the receiver of request {}", currentUserId, requestId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_service][reject_request] Error rejecting request {}: {}", requestId, e.message)
            throw e
        }
    }

    @Transactional
    fun cancelRequest(currentUserId: UUID, requestId: UUID) {
        logger.debug("\n\t[DEBUG] [friend_service][cancel_request] User {} cancelling request {}", currentUserId, requestId)

        try {
            val request = friendRequestRepository.findById(requestId)
                .orElseThrow { FriendRequestNotFoundException(requestId) }

            if(request.sender.id != currentUserId) {
                throw NotFriendRequestSenderException()
            }

            friendRequestRepository.delete(request)

            logger.info("\n\t[INFO] [friend_service][cancel_request] Request {} cancelled by {}", requestId, currentUserId)
        } catch(e: FriendRequestNotFoundException) {
            logger.warn("\n\t[WARN] [friend_service][cancel_request] Request {} not found", requestId)
            throw e
        } catch(e: NotFriendRequestSenderException) {
            logger.warn("\n\t[WARN] [friend_service][cancel_request] User {} is not the sender of request {}", currentUserId, requestId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_service][cancel_request] Error cancelling request {}: {}", requestId, e.message)
            throw e
        }
    }

    @Transactional
    fun removeFriend(currentUserId: UUID, friendUserId: UUID) {
        logger.debug("\n\t[DEBUG] [friend_service][remove_friend] User {} removing friend {}", currentUserId, friendUserId)

        try {
            val friendship = friendRequestRepository.findFriendshipBetween(currentUserId, friendUserId)
                .orElseThrow { FriendshipNotFoundException(friendUserId) }

            friendRequestRepository.delete(friendship)

            logger.info("\n\t[INFO] [friend_service][remove_friend] Friendship between {} and {} removed", currentUserId, friendUserId)
        } catch(e: FriendshipNotFoundException) {
            logger.warn("\n\t[WARN] [friend_service][remove_friend] No friendship found between {} and {}", currentUserId, friendUserId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_service][remove_friend] Error removing friendship between {} and {}: {}", currentUserId, friendUserId, e.message)
            throw e
        }
    }

    fun listFriends(userId: UUID): List<UserDTO> {
        logger.debug("\n\t[DEBUG] [friend_service][list_friends] Listing friends for user {}", userId)

        try {
            val friendships = friendRequestRepository.findAllFriendshipsForUser(userId)
            val friends = friendships.map { friendship ->
                val otherUser: User = if(friendship.sender.id == userId) friendship.receiver else friendship.sender
                UserDTO.from(otherUser)
            }

            logger.info("\n\t[INFO] [friend_service][list_friends] Found {} friends for user {}", friends.size, userId)
            return friends
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_service][list_friends] Error listing friends for user {}: {}", userId, e.message)
            throw e
        }
    }

    fun listPendingReceived(userId: UUID): List<FriendRequestDTO> {
        logger.debug("\n\t[DEBUG] [friend_service][list_pending_received] Listing pending requests received by user {}", userId)

        try {
            val requests = friendRequestRepository.findByReceiverIdAndStatus(userId, FriendRequestStatus.PENDING)
                .map { FriendRequestDTO.from(it) }

            logger.info("\n\t[INFO] [friend_service][list_pending_received] Found {} pending requests for user {}", requests.size, userId)
            return requests
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_service][list_pending_received] Error listing pending requests for user {}: {}", userId, e.message)
            throw e
        }
    }

    fun listPendingSent(userId: UUID): List<FriendRequestDTO> {
        logger.debug("\n\t[DEBUG] [friend_service][list_pending_sent] Listing pending requests sent by user {}", userId)

        try {
            val requests = friendRequestRepository.findBySenderIdAndStatus(userId, FriendRequestStatus.PENDING)
                .map { FriendRequestDTO.from(it) }

            logger.info("\n\t[INFO] [friend_service][list_pending_sent] Found {} pending requests sent by user {}", requests.size, userId)
            return requests
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [friend_service][list_pending_sent] Error listing pending requests sent by user {}: {}", userId, e.message)
            throw e
        }
    }
}