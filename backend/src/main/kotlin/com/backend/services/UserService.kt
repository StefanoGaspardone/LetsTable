package com.backend.services

import com.backend.exceptions.UploadedFileNotFoundException
import com.backend.exceptions.UserNotFoundException
import com.backend.exceptions.UsernameAlreadyTakenException
import com.backend.models.dtos.DeleteAccountDTO
import com.backend.models.dtos.UpdateUserRequest
import com.backend.models.dtos.UserDTO
import com.backend.models.dtos.UserProfileDTO
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FileOwnerType
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.FriendRequestRepository
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.MatchTeamRepository
import com.backend.repositories.PushTokenRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistMemberRepository
import com.backend.repositories.WishlistRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val matchPlayerRepository: MatchPlayerRepository,
    private val matchTeamRepository: MatchTeamRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val friendRequestRepository: FriendRequestRepository,
    private val wishlistMemberRepository: WishlistMemberRepository,
    private val uploadedFileRepository: UploadedFileRepository,
    private val collectionItemRepository: CollectionItemRepository,
    private val wishlistRepository: WishlistRepository,
    private val wishlistItemRepository: WishlistItemRepository,
    private val uploadedFileService: UploadedFileService,
    private val friendService: FriendService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MIN_SEARCH_LENGTH = 2
        private const val MAX_SEARCH_RESULTS = 20
    }

    @Transactional(readOnly = true)
    fun searchByUsername(userId: UUID, query: String): List<UserDTO> {
        logger.debug("\n\t[DEBUG] [user_service][search_by_username] User {} searching for username {}", userId, query)

        try {
            val normalizedQuery = query.trim()

            if(normalizedQuery.length < MIN_SEARCH_LENGTH) {
                return emptyList()
            }

            val results = userRepository.searchActiveByUsername(escapeLike(normalizedQuery), userId, PageRequest.of(0, MAX_SEARCH_RESULTS))
                .map { UserDTO.from(it) }

            logger.info("\n\t[INFO] [user_service][search_by_username] Found {} users matching {}", results.size, normalizedQuery)
            return results
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_service][search_by_username] Error searching for username {}: {}", query, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getUserById(userId: UUID): UserDTO {
        logger.debug("\n\t[DEBUG] [user_service][get_user_by_id] Retrieving user {}", userId)

        try {
            val user = findUser(userId)

            logger.info("\n\t[INFO] [user_service][get_user_by_id] Retrieved user {}", userId)
            return UserDTO.from(user)
        } catch(e: UserNotFoundException) {
            logger.warn("\n\t[WARN] [user_service][get_user_by_id] User {} not found", userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_service][get_user_by_id] Error retrieving user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getUserProfile(currentUserId: UUID, targetUserId: UUID): UserProfileDTO {
        logger.debug("\n\t[DEBUG] [user_service][get_user_profile] Retrieving profile for user {}", targetUserId)

        try {
            val response = UserProfileDTO(
                user = UserDTO.from(findUser(targetUserId)),
                friendshipStatus = friendService.getFriendshipStatus(currentUserId, targetUserId).name,
                totalMatches = matchRepository.countCompletedMatchesForUser(targetUserId),
                totalWins = matchRepository.countWonMatchesForUser(targetUserId),
            )

            logger.info("\n\t[INFO] [user_service][get_user_profile] Retrieved profile for user {}", targetUserId)
            return response
        } catch(e: UserNotFoundException) {
            logger.warn("\n\t[WARN] [user_service][get_user_profile] User {} not found", targetUserId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_service][get_user_profile] Error retrieving profile for user {}: {}", targetUserId, e.message)
            throw e
        }
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateUserRequest): UserDTO {
        logger.debug("\n\t[DEBUG] [user_service][update_profile] Updating profile for user {}", userId)

        try {
            val user = findUser(userId)

            request.username?.trim()?.let { newUsername ->
                if(!newUsername.equals(user.username, ignoreCase = true) && userRepository.existsByUsernameIgnoreCase(newUsername)) {
                    throw UsernameAlreadyTakenException(newUsername.lowercase())
                }

                user.username = newUsername
            }

            request.notificationsEnabled?.let { user.notificationsEnabled = it }

            if(request.removeAvatar == true) {
                user.avatarId = null
            } else {
                request.avatarId?.let { avatarId ->
                    uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(avatarId, FileOwnerType.USER_AVATAR, userId)
                        .orElseThrow { UploadedFileNotFoundException(avatarId) }

                    user.avatarId = avatarId
                }
            }

            val saved = try {
                userRepository.saveAndFlush(user)
            } catch(e: DataIntegrityViolationException) {
                throw UsernameAlreadyTakenException(user.username.lowercase())
            }

            logger.info("\n\t[INFO] [user_service][update_profile] Profile updated for user {}", userId)
            return UserDTO.from(saved)
        } catch(e: UsernameAlreadyTakenException) {
            logger.warn("\n\t[WARN] [user_service][update_profile] Username already taken for user {}: {}", userId, request.username)
            throw e
        } catch(e: UploadedFileNotFoundException) {
            logger.warn("\n\t[WARN] [user_service][update_profile] Avatar file {} not found for user {}", request.avatarId, userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_service][update_profile] Error updating profile for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun deleteAvatar(userId: UUID, fileId: UUID) {
        logger.debug("\n\t[DEBUG] [user_service][delete_avatar] User {} deleting avatar file {}", userId, fileId)

        try {
            uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, fileId)

            val user = findUser(userId)

            if(user.avatarId == fileId) {
                user.avatarId = null
            }

            logger.info("\n\t[INFO] [user_service][delete_avatar] Avatar file {} deleted by user {}", fileId, userId)
        } catch(e: UploadedFileNotFoundException) {
            logger.warn("\n\t[WARN] [user_service][delete_avatar] Avatar file {} not found for user {}", fileId, userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_service][delete_avatar] Error deleting avatar file {} for user {}: {}", fileId, userId, e.message)
            throw e
        }
    }

    @Transactional
    fun deleteAccount(userId: UUID): DeleteAccountDTO {
        logger.debug("\n\t[DEBUG] [user_service][delete_account] Deleting account for user {}", userId)

        try {
            val user = findUser(userId)

            val deletedSoloMatches = deleteSoloMatches(userId)

            refreshTokenRepository.revokeAllByUserId(userId)
            pushTokenRepository.deleteAllByUserId(userId)
            friendRequestRepository.deleteAllInvolvingUser(userId)
            collectionItemRepository.deleteAllByUserId(userId)
            wishlistItemRepository.deleteAllByWishlistOwnerId(userId)
            wishlistMemberRepository.deleteAllByWishlistOwnerId(userId)
            wishlistMemberRepository.deleteAllByUserId(userId)
            wishlistRepository.deleteAllByOwnerId(userId)

            uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.USER_AVATAR, userId)
                .forEach { uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, it.id!!) }

            user.username = "deleted-user-${user.id}"
            user.email = "deleted-${user.id}@letstable.invalid"
            user.passwordHash = ""
            user.avatarId = null
            user.notificationsEnabled = false
            user.accountStatus = AccountStatus.DELETED

            logger.info("\n\t[INFO] [user_service][delete_account] Account {} deleted, {} solo matches removed", userId, deletedSoloMatches)
            return DeleteAccountDTO(message = "Your account has been deleted")
        } catch(e: UserNotFoundException) {
            logger.warn("\n\t[WARN] [user_service][delete_account] User {} not found", userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_service][delete_account] Error deleting account {}: {}", userId, e.message)
            throw e
        }
    }

    private fun findUser(userId: UUID): User =
        userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

    private fun deleteSoloMatches(userId: UUID): Int {
        val matches = matchRepository.findAllForUser(userId)

        if(matches.isEmpty()) {
            return 0
        }

        val playersByMatchId = matchPlayerRepository.findAllByMatchIdIn(matches.map { it.id!! }).groupBy { it.match.id }

        val soloMatches = matches.filter { match ->
            val players = playersByMatchId[match.id].orEmpty()
            players.size == 1 && players.first().user?.id == userId
        }

        if(soloMatches.isEmpty()) {
            return 0
        }

        val soloMatchIds = soloMatches.map { it.id!! }

        matchPlayerRepository.deleteAll(soloMatchIds.flatMap { playersByMatchId[it].orEmpty() })
        matchTeamRepository.deleteAll(matchTeamRepository.findAllByMatchIdIn(soloMatchIds))
        matchRepository.deleteAll(soloMatches)

        return soloMatches.size
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}