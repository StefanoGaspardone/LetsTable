package com.backend.services

import com.backend.exceptions.CannotSuspendSelfException
import com.backend.exceptions.EmailAlreadyTakenException
import com.backend.exceptions.InvalidAccountStatusTransitionException
import com.backend.exceptions.InvalidSortException
import com.backend.exceptions.UserNotFoundException
import com.backend.exceptions.UsernameAlreadyTakenException
import com.backend.models.dtos.AdminUserDTO
import com.backend.models.dtos.AdminUserDetailDTO
import com.backend.models.dtos.CreateAdminRequest
import com.backend.models.dtos.PageDTO
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.models.mappers.toPageDTO
import com.backend.models.specifications.UserSpecification
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UserRepository
import com.backend.utils.duplicateUserException
import com.backend.utils.resolveSort
import com.backend.utils.safePageRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val collectionItemRepository: CollectionItemRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val matchService: MatchService,
    private val passwordEncoder: PasswordEncoder,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SORT_FIELDS = setOf("username", "createdAt")
        private const val DEFAULT_SORT_FIELD = "createdAt"
        private const val RECENT_MATCHES_LIMIT = 10
    }

    @Transactional(readOnly = true)
    fun listUsers(role: UserRole?, status: AccountStatus?, search: String?, page: Int, size: Int, sort: String?): PageDTO<AdminUserDTO> {
        logger.debug("\n\t[DEBUG] [admin_user_service][list_users] Listing users\n\trole={}\n\tstatus={}\n\tsearch={}\n\tpage={}\n\tsize={}\n\tsort={}", role, status, search, page, size, sort)

        try {
            val pageable = safePageRequest(page, size, resolveSort(sort, SORT_FIELDS, DEFAULT_SORT_FIELD))
            val result = userRepository.findAll(UserSpecification.withFilters(role, status, search), pageable)

            logger.info("\n\t[INFO] [admin_user_service][list_users] Retrieved {} users", result.numberOfElements)
            return result.toPageDTO { AdminUserDTO.from(it) }
        } catch(e: InvalidSortException) {
            logger.warn("\n\t[WARN] [admin_user_service][list_users] Invalid sort field: {}", sort)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_user_service][list_users] Error listing users: {}", e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getUserDetail(userId: UUID): AdminUserDetailDTO {
        logger.debug("\n\t[DEBUG] [admin_user_service][get_user_detail] Retrieving detail for user {}", userId)

        try {
            val response = AdminUserDetailDTO(
                user = AdminUserDTO.from(findUser(userId)),
                totalMatches = matchRepository.countCompletedMatchesForUser(userId),
                totalWins = matchRepository.countWonMatchesForUser(userId),
                collectionCount = collectionItemRepository.countByUserId(userId),
                recentMatches = matchService.getRecentMatchesForUser(userId, RECENT_MATCHES_LIMIT),
            )

            logger.info("\n\t[INFO] [admin_user_service][get_user_detail] Retrieved detail for user {}", userId)
            return response
        } catch(e: UserNotFoundException) {
            logger.warn("\n\t[WARN] [admin_user_service][get_user_detail] User {} not found", userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_user_service][get_user_detail] Error retrieving detail for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun suspendUser(adminId: UUID, userId: UUID): AdminUserDTO {
        logger.debug("\n\t[DEBUG] [admin_user_service][suspend_user] Admin {} suspending user {}", adminId, userId)

        try {
            if(adminId == userId) {
                throw CannotSuspendSelfException()
            }

            val user = findUser(userId)

            if(user.accountStatus != AccountStatus.ACTIVE) {
                throw InvalidAccountStatusTransitionException("Only active accounts can be suspended")
            }

            user.accountStatus = AccountStatus.SUSPENDED
            refreshTokenRepository.revokeAllByUserId(userId)

            logger.info("\n\t[INFO] [admin_user_service][suspend_user] User {} suspended by admin {}, sessions revoked", userId, adminId)
            return AdminUserDTO.from(user)
        } catch(e: CannotSuspendSelfException) {
            logger.warn("\n\t[WARN] [admin_user_service][suspend_user] Admin {} attempted to suspend themselves", adminId)
            throw e
        } catch(e: UserNotFoundException) {
            logger.warn("\n\t[WARN] [admin_user_service][suspend_user] User {} not found", userId)
            throw e
        } catch(e: InvalidAccountStatusTransitionException) {
            logger.warn("\n\t[WARN] [admin_user_service][suspend_user] Invalid transition for user {}: {}", userId, e.message)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_user_service][suspend_user] Error suspending user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun reactivateUser(userId: UUID): AdminUserDTO {
        logger.debug("\n\t[DEBUG] [admin_user_service][reactivate_user] Reactivating user {}", userId)

        try {
            val user = findUser(userId)

            if(user.accountStatus != AccountStatus.SUSPENDED) {
                throw InvalidAccountStatusTransitionException("Only suspended accounts can be reactivated")
            }

            user.accountStatus = AccountStatus.ACTIVE

            logger.info("\n\t[INFO] [admin_user_service][reactivate_user] User {} reactivated", userId)
            return AdminUserDTO.from(user)
        } catch(e: UserNotFoundException) {
            logger.warn("\n\t[WARN] [admin_user_service][reactivate_user] User {} not found", userId)
            throw e
        } catch(e: InvalidAccountStatusTransitionException) {
            logger.warn("\n\t[WARN] [admin_user_service][reactivate_user] Invalid transition for user {}: {}", userId, e.message)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_user_service][reactivate_user] Error reactivating user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun createAdmin(request: CreateAdminRequest): AdminUserDTO {
        logger.debug("\n\t[DEBUG] [admin_user_service][create_admin] Creating admin with email {}", request.email)

        try {
            val normalizedEmail = request.email.trim().lowercase()
            val trimmedUsername = request.username.trim()
            val normalizedUsername = trimmedUsername.lowercase()

            if(userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw EmailAlreadyTakenException(normalizedEmail)
            }

            if(userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
                throw UsernameAlreadyTakenException(normalizedUsername)
            }

            val saved = try {
                userRepository.saveAndFlush(
                    User(
                        username = trimmedUsername,
                        email = normalizedEmail,
                        passwordHash = passwordEncoder.encode(request.password.trim())!!,
                        role = UserRole.ADMIN,
                        accountStatus = AccountStatus.ACTIVE,
                    )
                )
            } catch(e: DataIntegrityViolationException) {
                throw duplicateUserException(e, normalizedEmail, normalizedUsername)
            }

            logger.info("\n\t[INFO] [admin_user_service][create_admin] Admin {} created", saved.id)
            return AdminUserDTO.from(saved)
        } catch(e: EmailAlreadyTakenException) {
            logger.warn("\n\t[WARN] [admin_user_service][create_admin] Email already taken: {}", request.email)
            throw e
        } catch(e: UsernameAlreadyTakenException) {
            logger.warn("\n\t[WARN] [admin_user_service][create_admin] Username already taken: {}", request.username)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_user_service][create_admin] Error creating admin: {}", e.message)
            throw e
        }
    }

    private fun findUser(userId: UUID): User =
        userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }
}