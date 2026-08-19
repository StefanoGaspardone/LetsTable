package com.backend.services

import com.backend.exceptions.*
import com.backend.models.dtos.*
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import com.backend.models.entities.WishlistMember
import com.backend.repositories.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class WishlistService(
    private val wishlistRepository: WishlistRepository,
    private val wishlistMemberRepository: WishlistMemberRepository,
    private val wishlistItemRepository: WishlistItemRepository,
    private val userRepository: UserRepository,
    private val gameRepository: GameRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createWishlist(userId: UUID, request: CreateWishlistRequest): WishlistDTO {
        logger.debug("\n\t[DEBUG] [wishlist_service][create_wishlist] User {} creating wishlist {}", userId, request.name)

        try {
            val owner = userRepository.findById(userId)
                .orElseThrow { UserNotFoundByIdentifierException(userId.toString()) }

            val wishlist = Wishlist(name = request.name, owner = owner, isShared = request.isShared)
            val saved = wishlistRepository.save(wishlist)

            logger.info("\n\t[INFO] [wishlist_service][create_wishlist] Wishlist {} created by user {}", saved.id, userId)
            return WishlistDTO.from(saved)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][create_wishlist] Error creating wishlist for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun deleteWishlist(userId: UUID, wishlistId: UUID) {
        logger.debug("\n\t[DEBUG] [wishlist_service][delete_wishlist] User {} deleting wishlist {}", userId, wishlistId)

        try {
            val wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow { WishlistNotFoundException(wishlistId) }

            if(wishlist.owner.id != userId) {
                throw NotWishlistOwnerException()
            }

            wishlistRepository.delete(wishlist)

            logger.info("\n\t[INFO] [wishlist_service][delete_wishlist] Wishlist {} deleted by user {}", wishlistId, userId)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][delete_wishlist] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerException) {
            logger.warn("\n\t[WARN] [wishlist_service][delete_wishlist] User {} is not the owner of wishlist {}", userId, wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][delete_wishlist] Error deleting wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    fun listAccessibleWishlists(userId: UUID): List<WishlistDTO> {
        logger.debug("\n\t[DEBUG] [wishlist_service][list_accessible_wishlists] Listing wishlists for user {}", userId)

        try {
            val wishlists = wishlistRepository.findAllAccessibleByUser(userId).map { WishlistDTO.from(it) }

            logger.info("\n\t[INFO] [wishlist_service][list_accessible_wishlists] Found {} wishlists for user {}", wishlists.size, userId)
            return wishlists
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][list_accessible_wishlists] Error listing wishlists for user {}: {}", userId, e.message)
            throw e
        }
    }

    @Transactional
    fun addMember(userId: UUID, wishlistId: UUID, request: AddWishlistMemberRequest): WishlistMemberDTO {
        logger.debug("\n\t[DEBUG] [wishlist_service][add_member] User {} adding member {} to wishlist {}", userId, request.userId, wishlistId)

        try {
            val wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow { WishlistNotFoundException(wishlistId) }

            if(wishlist.owner.id != userId) {
                throw NotWishlistOwnerException()
            }

            if(!wishlist.isShared) {
                throw WishlistNotSharedException(wishlistId)
            }

            if(request.userId == wishlist.owner.id) {
                throw CannotAddOwnerAsMemberException()
            }

            if(wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, request.userId)) {
                throw UserAlreadyWishlistMemberException(request.userId)
            }

            val newMemberUser = userRepository.findById(request.userId)
                .orElseThrow { UserNotFoundByIdentifierException(request.userId.toString()) }

            val member = WishlistMember(wishlist = wishlist, user = newMemberUser)
            val saved = wishlistMemberRepository.save(member)

            logger.info("\n\t[INFO] [wishlist_service][add_member] User {} added as member to wishlist {} by {}", request.userId, wishlistId, userId)
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
            logger.warn("\n\t[WARN] [wishlist_service][add_member] User {} already a member of wishlist {}", request.userId, wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][add_member] Error adding member {} to wishlist {}: {}", request.userId, wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun removeMember(userId: UUID, wishlistId: UUID, memberUserId: UUID) {
        logger.debug("\n\t[DEBUG] [wishlist_service][remove_member] User {} removing member {} from wishlist {}", userId, memberUserId, wishlistId)

        try {
            val wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow { WishlistNotFoundException(wishlistId) }

            if(wishlist.owner.id != userId) {
                throw NotWishlistOwnerException()
            }

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

    fun listMembers(wishlistId: UUID): List<WishlistMemberDTO> {
        logger.debug("\n\t[DEBUG] [wishlist_service][list_members] Listing members of wishlist {}", wishlistId)

        try {
            wishlistRepository.findById(wishlistId)
                .orElseThrow { WishlistNotFoundException(wishlistId) }

            val members = wishlistMemberRepository.findAllByWishlistId(wishlistId).map { WishlistMemberDTO.from(it) }

            logger.info("\n\t[INFO] [wishlist_service][list_members] Found {} members for wishlist {}", members.size, wishlistId)
            return members
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][list_members] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][list_members] Error listing members of wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun addItem(userId: UUID, wishlistId: UUID, request: AddWishlistItemRequest): WishlistItemDTO {
        logger.debug("\n\t[DEBUG] [wishlist_service][add_item] User {} adding game {} to wishlist {}", userId, request.gameId, wishlistId)

        try {
            val wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow { WishlistNotFoundException(wishlistId) }

            requireAccess(wishlist, userId)

            if(wishlistItemRepository.existsByWishlistIdAndGameId(wishlistId, request.gameId)) {
                throw GameAlreadyInWishlistException(request.gameId)
            }

            val game = gameRepository.findById(request.gameId)
                .orElseThrow { GameNotFoundException(request.gameId) }
            val addedBy = userRepository.findById(userId)
                .orElseThrow { UserNotFoundByIdentifierException(userId.toString()) }

            val item = WishlistItem(wishlist = wishlist, game = game, addedBy = addedBy)
            val saved = wishlistItemRepository.save(item)

            logger.info("\n\t[INFO] [wishlist_service][add_item] Game {} added to wishlist {} by user {}", request.gameId, wishlistId, userId)
            return WishlistItemDTO.from(saved)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_item] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: NotWishlistOwnerOrMemberException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_item] User {} has no access to wishlist {}", userId, wishlistId)
            throw e
        } catch(e: GameAlreadyInWishlistException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_item] Game {} already in wishlist {}", request.gameId, wishlistId)
            throw e
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][add_item] Game {} not found", request.gameId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][add_item] Error adding game {} to wishlist {}: {}", request.gameId, wishlistId, e.message)
            throw e
        }
    }

    @Transactional
    fun removeItem(userId: UUID, wishlistId: UUID, itemId: UUID) {
        logger.debug("\n\t[DEBUG] [wishlist_service][remove_item] User {} removing item {} from wishlist {}", userId, itemId, wishlistId)

        try {
            val wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow { WishlistNotFoundException(wishlistId) }

            requireAccess(wishlist, userId)

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

    fun listItems(wishlistId: UUID): List<WishlistItemDTO> {
        logger.debug("\n\t[DEBUG] [wishlist_service][list_items] Listing items of wishlist {}", wishlistId)

        try {
            wishlistRepository.findById(wishlistId)
                .orElseThrow { WishlistNotFoundException(wishlistId) }

            val items = wishlistItemRepository.findAllByWishlistId(wishlistId).map { WishlistItemDTO.from(it) }

            logger.info("\n\t[INFO] [wishlist_service][list_items] Found {} items in wishlist {}", items.size, wishlistId)
            return items
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][list_items] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][list_items] Error listing items of wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    fun getWishlist(wishlistId: UUID): WishlistDTO {
        logger.debug("\n\t[DEBUG] [wishlist_service][get_wishlist] Retrieving wishlist {}", wishlistId)

        try {
            val wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow { WishlistNotFoundException(wishlistId) }

            logger.info("\n\t[INFO] [wishlist_service][get_wishlist] Retrieved wishlist {}", wishlistId)
            return WishlistDTO.from(wishlist)
        } catch(e: WishlistNotFoundException) {
            logger.warn("\n\t[WARN] [wishlist_service][get_wishlist] Wishlist {} not found", wishlistId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [wishlist_service][get_wishlist] Error retrieving wishlist {}: {}", wishlistId, e.message)
            throw e
        }
    }

    private fun requireAccess(wishlist: Wishlist, userId: UUID) {
        val isOwner = wishlist.owner.id == userId
        val isMember = wishlist.isShared && wishlistMemberRepository.existsByWishlistIdAndUserId(wishlist.id!!, userId)

        if(!isOwner && !isMember) {
            throw NotWishlistOwnerOrMemberException()
        }
    }
}