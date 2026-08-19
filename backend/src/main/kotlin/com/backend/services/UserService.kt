package com.backend.services

import com.backend.exceptions.*
import com.backend.models.dtos.DeleteAccountDTO
import com.backend.models.dtos.UserDTO
import com.backend.models.enums.AccountStatus
import com.backend.repositories.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val matchPlayerRepository: MatchPlayerRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun searchByUsername(userId: UUID, query: String): List<UserDTO> {
        logger.debug("\n\t[DEBUG] [user_service][search_by_username] User {} searching for username {}", userId, query)

        try {
            val results = userRepository.searchActiveByUsername(query)
                .filter { it.id != userId }
                .map { UserDTO.from(it) }

            logger.info("\n\t[INFO] [user_service][search_by_username] Found {} users matching {}", results.size, query)
            return results
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [user_service][search_by_username] Error searching for username {}: {}", query, e.message)
            throw e
        }
    }

    fun getUserById(userId: UUID): UserDTO {
        logger.debug("\n\t[DEBUG] [user_service][get_user_by_id] Retrieving user {}", userId)

        try {
            val user = userRepository.findById(userId)
                .orElseThrow { UserNotFoundException(userId) }

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

    @Transactional
    fun deleteAccount(userId: UUID): DeleteAccountDTO {
        logger.debug("\n\t[DEBUG] [user_service][delete_account] Deleting account for user {}", userId)

        try {
            val user = userRepository.findById(userId)
                .orElseThrow { UserNotFoundException(userId) }

            val userMatches = matchRepository.findAllForUser(userId)
            var deletedSoloMatches = 0

            userMatches.forEach { match ->
                val playersInMatch = matchPlayerRepository.findAllByMatchId(match.id!!)
                val onlyPlayerIsThisUser = playersInMatch.size == 1 && playersInMatch.first().user?.id == userId

                if(onlyPlayerIsThisUser) {
                    matchRepository.delete(match)
                    deletedSoloMatches++
                }
            }

            refreshTokenRepository.findAll()
                .filter { it.user.id == userId && !it.revoked }
                .forEach {
                    it.revoked = true
                    refreshTokenRepository.save(it)
                }

            user.username = "deleted-user-${user.id}"
            user.email = "deleted-${user.id}@letstable.invalid"
            user.passwordHash = ""
            user.accountStatus = AccountStatus.DELETED
            userRepository.save(user)

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
}