package com.backend.services

import com.backend.exceptions.*
import com.backend.models.dtos.*
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import com.backend.models.entities.WishlistMember
import com.backend.models.enums.AccountStatus
import com.backend.models.mappers.toPageDTO
import com.backend.models.specifications.WishlistFilterType
import com.backend.models.specifications.WishlistItemSpecification
import com.backend.models.specifications.WishlistSpecification
import com.backend.repositories.GameRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistMemberRepository
import com.backend.repositories.WishlistRepository
import com.backend.utils.resolveSort
import com.backend.utils.safePageRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WishlistService(
    private val wishlistRepository: WishlistRepository,
    private val wishlistMemberRepository: WishlistMemberRepository,
    private val wishlistItemRepository: WishlistItemRepository,
    private val userRepository: UserRepository,
    private val gameRepository: GameRepository,
    private val pushNotificationService: PushNotificationService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val WISHLIST_SORT_FIELDS = setOf("updatedAt", "name", "createdAt")
        private const val DEFAULT_WISHLIST_SORT_FIELD = "updatedAt"
        private val ITEM_SORT_FIELDS = setOf("game.name", "createdAt")
        private const val DEFAULT_ITEM_SORT_FIELD = "createdAt"
        private const val DEFAULT_WISHLIST_NAME = "La mia wishlist"
    }

    @Transactional
    fun createWishlist(userId: UUID, request: CreateWishlistRequest): WishlistDTO {
        logger.debug("\n\t[DEBUG] [wishlist_service][create_wishlist] User {} creating wishlist {}", userId, request.name)

        try {
            val owner = userRepository.findById(userId)
                .orElseThrow { UserNotFoundByIdentifierException(userId.toString()) }

            val saved = wishlistRepository.save(Wishlist(name = request.name.trim(), owner = owner, isShared = request.isShared))

            logger.info("\n\t[INFO] [wishlist_service][create_wishlist] Wishlist {} created by user {}", saved.id, userId)
            return WishlistDTO.from(saved)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][create_wishlist] Error creating wishlist for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun updateWishlist(userId: UUID, wishlistId: UUID, request: UpdateWishlistRequest): WishlistDTO {
        logger.debug("\n\t[DEBUG] [wishlist_service][update_wishlist] User {} updating wishlist {}", userId, wishlistId)

        try {
            val wishlist = findEditableWishlist(userId, wishlistId)
            wishlist.name = request.name.trim()

            val saved = wishlistRepository.saveAndFlush(wishlist)

            logger.info("\n\t[INFO] [wishlist_service][update_wishlist] Wishlist {} renamed by user {}", wishlistId, userId)
            return WishlistDTO.from(saved)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][update_wishlist] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerException) {
            logger.warn("\n\t[WARN] [wishlist_service][update_wishlist] User {} is not the owner of wishlist {}", userId, wishlistId)
            throw e
        } catch(e: CannotModifyDefaultWishlistException) {
            logger.warn("\n\t[WARN] [wishlist_service][update_wishlist] Wishlist {} is the default one and cannot be modified", wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][update_wishlist] Error updating wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun deleteWishlist(userId: UUID, wishlistId: UUID) {
        logger.debug("\n\t[DEBUG] [wishlist_service][delete_wishlist] User {} deleting wishlist {}", userId, wishlistId)

        try {
            wishlistRepository.delete(findEditableWishlist(userId, wishlistId))

            logger.info("\n\t[INFO] [wishlist_service][delete_wishlist] Wishlist {} deleted by user {}", wishlistId, userId)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][delete_wishlist] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerException) {
            logger.warn("\n\t[WARN] [wishlist_service][delete_wishlist] User {} is not the owner of wishlist {}", userId, wishlistId)
            throw e
        } catch(e: CannotModifyDefaultWishlistException) {
            logger.warn("\n\t[WARN] [wishlist_service][delete_wishlist] Wishlist {} is the default one and cannot be deleted", wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][delete_wishlist] Error deleting wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun listAccessibleWishlists(userId: UUID, page: Int, size: Int, type: WishlistFilterType?, sort: String?): PageDTO<WishlistDTO> {
        logger.debug("\n\t[DEBUG] [wishlist_service][list_accessible_wishlists] Listing wishlists\n\tuserId={}\n\tpage={}\n\tsize={}\n\ttype={}\n\tsort={}", userId, page, size, type, sort)

        try {
            val pageable = safePageRequest(page, size, resolveSort(sort, WISHLIST_SORT_FIELDS, DEFAULT_WISHLIST_SORT_FIELD))
            val result = wishlistRepository.findAll(WishlistSpecification.withFilters(userId, type), pageable)

            logger.info("\n\t[INFO] [wishlist_service][list_accessible_wishlists] Retrieved {} wishlists for user {}", result.numberOfElements, userId)
            return result.toPageDTO { WishlistDTO.from(it) }
        } catch(e: InvalidSortException) {
            logger.warn("\n\t[WARN] [wishlist_service][list_accessible_wishlists] Invalid sort field: {}", sort)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][list_accessible_wishlists] Error listing wishlists for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getWishlist(userId: UUID, wishlistId: UUID): WishlistDTO {
        logger.debug("\n\t[DEBUG] [wishlist_service][get_wishlist] User {} retrieving wishlist {}", userId, wishlistId)

        try {
            val wishlist = findViewableWishlist(userId, wishlistId)

            logger.info("\n\t[INFO] [wishlist_service][get_wishlist] Retrieved wishlist {}", wishlistId)
            return WishlistDTO.from(wishlist)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][get_wishlist] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerOrMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][get_wishlist] User {} has no access to wishlist {}", userId, wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][get_wishlist] Error retrieving wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun addMember(userId: UUID, wishlistId: UUID, request: AddWishlistMemberRequest): WishlistMemberDTO {
        val memberUserId = request.userId
        logger.debug("\n\t[DEBUG] [wishlist_service][add_member] User {} adding member {} to wishlist {}", userId, memberUserId, wishlistId)

        try {
            val wishlist = findOwnedWishlist(userId, wishlistId)

            if(!wishlist.isShared) {
                throw WishlistNotSharedException(wishlistId)
            }

            if(memberUserId == userId) {
                throw CannotAddOwnerAsMemberException()
            }

            if(wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, memberUserId)) {
                throw UserAlreadyWishlistMemberException(memberUserId)
            }

            val memberUser = userRepository.findById(memberUserId)
                .filter { it.accountStatus == AccountStatus.ACTIVE }
                .orElseThrow { UserNotFoundByIdentifierException(memberUserId.toString()) }

            val saved = try {
                wishlistMemberRepository.saveAndFlush(WishlistMember(wishlist = wishlist, user = memberUser))
            } catch(_: DataIntegrityViolationException) {
                throw UserAlreadyWishlistMemberException(memberUserId)
            }

            pushNotificationService.sendToUser(
                userId = memberUserId,
                title = "Aggiunto a una wishlist",
                body = "Sei stato aggiunto alla wishlist \"${wishlist.name}\"",
                data = mapOf("type" to "WISHLIST_MEMBER_ADDED", "wishlistId" to wishlistId.toString()),
            )

            logger.info("\n\t[INFO] [wishlist_service][add_member] User {} added as member to wishlist {} by {}", memberUserId, wishlistId, userId)
            return WishlistMemberDTO.from(saved)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_member] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_member] User {} is not the owner of wishlist {}", userId, wishlistId)
            throw e
        } catch(e: WishlistNotSharedException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_member] Wishlist {} is not shared", wishlistId)
            throw e
        } catch(e: CannotAddOwnerAsMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_member] Attempted to add owner as member of wishlist {}", wishlistId)
            throw e
        } catch(e: UserAlreadyWishlistMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_member] User {} already a member of wishlist {}", memberUserId, wishlistId)
            throw e
        } catch(e: UserNotFoundByIdentifierException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_member] User {} not found or not active", memberUserId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][add_member] Error adding member {} to wishlist {}: {}", memberUserId, wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun removeMember(userId: UUID, wishlistId: UUID, memberUserId: UUID) {
        logger.debug("\n\t[DEBUG] [wishlist_service][remove_member] User {} removing member {} from wishlist {}", userId, memberUserId, wishlistId)

        try {
            findOwnedWishlist(userId, wishlistId)

            val member = wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, memberUserId)
                .orElseThrow { UserNotWishlistMemberException(memberUserId) }

            wishlistMemberRepository.delete(member)

            logger.info("\n\t[INFO] [wishlist_service][remove_member] Member {} removed from wishlist {} by {}", memberUserId, wishlistId, userId)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][remove_member] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerException) {
            logger.warn("\n\t[WARN] [wishlist_service][remove_member] User {} is not the owner of wishlist {}", userId, wishlistId)
            throw e
        } catch(e: UserNotWishlistMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][remove_member] User {} is not a member of wishlist {}", memberUserId, wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][remove_member] Error removing member {} from wishlist {}: {}", memberUserId, wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun leaveWishlist(userId: UUID, wishlistId: UUID) {
        logger.debug("\n\t[DEBUG] [wishlist_service][leave_wishlist] User {} leaving wishlist {}", userId, wishlistId)

        try {
            val member = wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, userId)
                .orElseThrow { UserNotWishlistMemberException(userId) }

            wishlistMemberRepository.delete(member)

            logger.info("\n\t[INFO] [wishlist_service][leave_wishlist] User {} left wishlist {}", userId, wishlistId)
        } catch(e: UserNotWishlistMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][leave_wishlist] User {} is not a member of wishlist {}", userId, wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][leave_wishlist] Error leaving wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun listMembers(userId: UUID, wishlistId: UUID): List<WishlistMemberDTO> {
        logger.debug("\n\t[DEBUG] [wishlist_service][list_members] User {} listing members of wishlist {}", userId, wishlistId)

        try {
            findViewableWishlist(userId, wishlistId)

            val members = wishlistMemberRepository.findAllByWishlistId(wishlistId).map { WishlistMemberDTO.from(it) }

            logger.info("\n\t[INFO] [wishlist_service][list_members] Found {} members for wishlist {}", members.size, wishlistId)
            return members
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][list_members] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerOrMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][list_members] User {} has no access to wishlist {}", userId, wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][list_members] Error listing members of wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun addItem(userId: UUID, wishlistId: UUID, request: AddWishlistItemRequest): WishlistItemDTO {
        val gameId = request.gameId
        logger.debug("\n\t[DEBUG] [wishlist_service][add_item] User {} adding game {} to wishlist {}", userId, gameId, wishlistId)

        try {
            val wishlist = findWritableWishlist(userId, wishlistId)

            if(wishlistItemRepository.existsByWishlistIdAndGameId(wishlistId, gameId)) {
                throw GameAlreadyInWishlistException(gameId)
            }

            val game = gameRepository.findById(gameId)
                .orElseThrow { GameNotFoundException(gameId) }

            val saved = try {
                wishlistItemRepository.saveAndFlush(WishlistItem(wishlist = wishlist, game = game, addedBy = userRepository.getReferenceById(userId)))
            } catch(_: DataIntegrityViolationException) {
                throw GameAlreadyInWishlistException(gameId)
            }

            logger.info("\n\t[INFO] [wishlist_service][add_item] Game {} added to wishlist {} by user {}", gameId, wishlistId, userId)
            return WishlistItemDTO.from(saved)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_item] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerOrMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_item] User {} has no access to wishlist {}", userId, wishlistId)
            throw e
        } catch(e: GameAlreadyInWishlistException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_item] Game {} already in wishlist {}", gameId, wishlistId)
            throw e
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_item] Game {} not found", gameId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][add_item] Error adding game {} to wishlist {}: {}", gameId, wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun removeItem(userId: UUID, wishlistId: UUID, itemId: UUID) {
        logger.debug("\n\t[DEBUG] [wishlist_service][remove_item] User {} removing item {} from wishlist {}", userId, itemId, wishlistId)

        try {
            findWritableWishlist(userId, wishlistId)

            val item = wishlistItemRepository.findByIdAndWishlistId(itemId, wishlistId)
                .orElseThrow { WishlistItemNotFoundException(itemId) }

            wishlistItemRepository.delete(item)

            logger.info("\n\t[INFO] [wishlist_service][remove_item] Item {} removed from wishlist {} by user {}", itemId, wishlistId, userId)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][remove_item] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerOrMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][remove_item] User {} has no access to wishlist {}", userId, wishlistId)
            throw e
        } catch(e: WishlistItemNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][remove_item] Item {} not found in wishlist {}", itemId, wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][remove_item] Error removing item {} from wishlist {}: {}", itemId, wishlistId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun listItems(userId: UUID, wishlistId: UUID, page: Int, size: Int, gameName: String?, sort: String?): PageDTO<WishlistItemDTO> {
        logger.debug("\n\t[DEBUG] [wishlist_service][list_items] Listing items\n\tuserId={}\n\twishlistId={}\n\tpage={}\n\tsize={}\n\tgameName={}\n\tsort={}", userId, wishlistId, page, size, gameName, sort)

        try {
            findViewableWishlist(userId, wishlistId)

            val result = findItems(wishlistId, gameName, safePageRequest(page, size, resolveSort(sort, ITEM_SORT_FIELDS, DEFAULT_ITEM_SORT_FIELD)))

            logger.info("\n\t[INFO] [wishlist_service][list_items] Retrieved {} items for wishlist {}", result.numberOfElements, wishlistId)
            return result.toPageDTO { WishlistItemDTO.from(it) }
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][list_items] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerOrMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][list_items] User {} has no access to wishlist {}", userId, wishlistId)
            throw e
        } catch(e: InvalidSortException) {
            logger.warn("\n\t[WARN] [wishlist_service][list_items] Invalid sort field: {}", sort)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][list_items] Error listing items of wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getItemStatus(userId: UUID, wishlistId: UUID, gameId: UUID): WishlistItemStatusDTO {
        logger.debug("\n\t[DEBUG] [wishlist_service][get_item_status] User {} checking status for wishlist {} game {}", userId, wishlistId, gameId)

        try {
            findViewableWishlist(userId, wishlistId)

            val itemId = wishlistItemRepository.findIdByWishlistIdAndGameId(wishlistId, gameId)
            val response = WishlistItemStatusDTO(inWishlist = itemId != null, itemId = itemId)

            logger.info("\n\t[INFO] [wishlist_service][get_item_status] Status for wishlist {} game {}: {}", wishlistId, gameId, response.inWishlist)
            return response
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][get_item_status] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerOrMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][get_item_status] User {} has no access to wishlist {}", userId, wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][get_item_status] Error checking status for wishlist {} game {}: {}", wishlistId, gameId, e.message)
            throw e
        }
    }

    @Transactional
    fun createDefaultWishlistForUser(user: User) {
        logger.debug("\n\t[DEBUG] [wishlist_service][create_default_wishlist_for_user] Creating default wishlist for user {}", user.id)

        try {
            wishlistRepository.save(Wishlist(name = DEFAULT_WISHLIST_NAME, owner = user, isShared = false, isDefault = true))

            logger.info("\n\t[INFO] [wishlist_service][create_default_wishlist_for_user] Default wishlist created for user {}", user.id)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][create_default_wishlist_for_user] Error creating default wishlist for user {}: {}", user.id, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getDefaultWishlistForUser(userId: UUID, page: Int, size: Int): PageDTO<WishlistItemDTO> {
        logger.debug("\n\t[DEBUG] [wishlist_service][get_default_wishlist_for_user] Retrieving default wishlist items\n\tuserId={}\n\tpage={}\n\tsize={}", userId, page, size)

        try {
            val wishlist = wishlistRepository.findDefaultByOwnerId(userId)
                .orElseThrow { DefaultWishlistNotFoundException(userId) }

            val result = findItems(wishlist.id!!, null, safePageRequest(page, size, resolveSort(null, ITEM_SORT_FIELDS, DEFAULT_ITEM_SORT_FIELD)))

            logger.info("\n\t[INFO] [wishlist_service][get_default_wishlist_for_user] Retrieved {} items for user {}", result.numberOfElements, userId)
            return result.toPageDTO { WishlistItemDTO.from(it) }
        } catch(e: DefaultWishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][get_default_wishlist_for_user] Default wishlist not found for user {}", userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][get_default_wishlist_for_user] Error retrieving default wishlist items for user {}: {}", userId, e.message)
            throw e
        }
    }

    private fun findItems(wishlistId: UUID, gameName: String?, pageable: Pageable): Page<WishlistItem> =
        wishlistItemRepository.findAll(WishlistItemSpecification.withFilters(wishlistId, gameName), pageable)

    private fun findWishlist(wishlistId: UUID): Wishlist =
        wishlistRepository.findById(wishlistId)
            .orElseThrow { WishlistNotFoundException(wishlistId) }

    private fun findViewableWishlist(userId: UUID, wishlistId: UUID): Wishlist {
        val wishlist = findWishlist(wishlistId)

        if(!wishlist.isDefault && !isOwnerOrMember(wishlist, userId)) {
            throw NotWishlistOwnerOrMemberException()
        }

        return wishlist
    }

    private fun findWritableWishlist(userId: UUID, wishlistId: UUID): Wishlist {
        val wishlist = findWishlist(wishlistId)

        if(!isOwnerOrMember(wishlist, userId)) {
            throw NotWishlistOwnerOrMemberException()
        }

        return wishlist
    }

    private fun findOwnedWishlist(userId: UUID, wishlistId: UUID): Wishlist {
        val wishlist = findWishlist(wishlistId)

        if(wishlist.owner.id != userId) {
            throw NotWishlistOwnerException()
        }

        return wishlist
    }

    private fun findEditableWishlist(userId: UUID, wishlistId: UUID): Wishlist {
        val wishlist = findOwnedWishlist(userId, wishlistId)

        if(wishlist.isDefault) {
            throw CannotModifyDefaultWishlistException()
        }

        return wishlist
    }

    private fun isOwnerOrMember(wishlist: Wishlist, userId: UUID): Boolean =
        wishlist.owner.id == userId ||
                (wishlist.isShared && wishlistMemberRepository.existsByWishlistIdAndUserId(wishlist.id!!, userId))
}