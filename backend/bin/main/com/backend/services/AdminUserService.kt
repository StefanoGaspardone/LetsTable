package com.backend.services

import com.backend.exceptions.*
import com.backend.models.dtos.*
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.models.mappers.toPageDTO
import com.backend.models.specifications.UserSpecification
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.UserRepository
import com.backend.utils.resolveSort
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val collectionItemRepository: CollectionItemRepository,
    private val matchService: MatchService,
    private val passwordEncoder: PasswordEncoder,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun listUsers(role: UserRole?, status: AccountStatus?, search: String?, page: Int, size: Int, sort: String?): PageDTO<AdminUserDTO> {
        logger.debug("\n\t[DEBUG] [admin_user_service][list_users] Listing users\n\trole={}\n\tstatus={}\n\tsearch={}", role, status, search)

        try {
            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 100)
            val sortObj = resolveSort(sort, setOf("username", "createdAt"), "createdAt")
            val pageable = PageRequest.of(pageSafe, sizeSafe, sortObj)

            val spec = UserSpecification.withFilters(role, status, search)
            val result = userRepository.findAll(spec, pageable)

            logger.info("\n\t[INFO] [admin_user_service][list_users] Retrieved {} users", result.numberOfElements)
            return result.toPageDTO { AdminUserDTO.from(it) }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_user_service][list_users] Error listing users: {}", e.message)
            throw e
        }
    }

    fun getUserDetail(userId: UUID): AdminUserDetailDTO {
        logger.debug("\n\t[DEBUG] [admin_user_service][get_user_detail] Retrieving detail for user {}", userId)

        try {
            val user = userRepository.findById(userId)
                .orElseThrow { UserNotFoundException(userId) }

            val totalMatches = matchRepository.countCompletedMatchesForUser(userId)
            val totalWins = matchRepository.countWonMatchesForUser(userId)
            val collectionCount = collectionItemRepository.countByUserId(userId)
            val recentMatches = matchService.getRecentMatchesForUser(userId, 10)

            val response = AdminUserDetailDTO(
                user = AdminUserDTO.from(user),
                totalMatches = totalMatches,
                totalWins = totalWins,
                collectionCount = collectionCount,
                recentMatches = recentMatches,
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

            val user = userRepository.findById(userId)
                .orElseThrow { UserNotFoundException(userId) }

            if(user.accountStatus != AccountStatus.ACTIVE) {
                throw InvalidAccountStatusTransitionException("Only active accounts can be suspended")
            }

            user.accountStatus = AccountStatus.SUSPENDED
            val saved = userRepository.save(user)

            logger.info("\n\t[INFO] [admin_user_service][suspend_user] User {} suspended by admin {}", userId, adminId)
            return AdminUserDTO.from(saved)
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
            val user = userRepository.findById(userId)
                .orElseThrow { UserNotFoundException(userId) }

            if(user.accountStatus != AccountStatus.SUSPENDED) {
                throw InvalidAccountStatusTransitionException("Only suspended accounts can be reactivated")
            }

            user.accountStatus = AccountStatus.ACTIVE
            val saved = userRepository.save(user)

            logger.info("\n\t[INFO] [admin_user_service][reactivate_user] User {} reactivated", userId)
            return AdminUserDTO.from(saved)
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
            val normalizedUsername = request.username.trim().lowercase()

            if(userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw EmailAlreadyTakenException(normalizedEmail)
            }

            if(userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
                throw UsernameAlreadyTakenException(normalizedUsername)
            }

            val admin = User(
                username = request.username.trim(),
                email = normalizedEmail,
                passwordHash = passwordEncoder.encode(request.password)!!,
                role = UserRole.ADMIN,
                accountStatus = AccountStatus.ACTIVE,
            )
            val saved = userRepository.saveAndFlush(admin)

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
}