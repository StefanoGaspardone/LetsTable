package com.backend.services

import com.backend.exceptions.*
import com.backend.models.dtos.AddToCollectionRequest
import com.backend.models.dtos.CollectionItemDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.entities.CollectionItem
import com.backend.models.mappers.toPageDTO
import com.backend.models.specifications.CollectionItemSpecification
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.UserRepository
import com.backend.utils.resolveSort
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class CollectionService(
    private val collectionItemRepository: CollectionItemRepository,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun addToCollection(userId: UUID, request: AddToCollectionRequest): CollectionItemDTO {
        logger.debug("\n\t[DEBUG] [collection_service][add_to_collection] User {} adding game {} to collection", userId, request.gameId)

        try {
            if (collectionItemRepository.existsByUserIdAndGameId(userId, request.gameId)) {
                throw GameAlreadyInCollectionException(request.gameId)
            }

            val user = userRepository.findById(userId).orElseThrow { UserNotFoundByIdentifierException(userId.toString()) }
            val game = gameRepository.findById(request.gameId).orElseThrow { GameNotFoundException(request.gameId) }

            val item = CollectionItem(user = user, game = game)
            val saved = collectionItemRepository.save(item)

            logger.info("\n\t[INFO] [collection_service][add_to_collection] Game {} added to collection of user {}", request.gameId, userId)
            return CollectionItemDTO.from(saved)
        } catch(e: GameAlreadyInCollectionException) {
            logger.warn("\n\t[WARN] [collection_service][add_to_collection] Game {} already in collection of user {}", request.gameId, userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [collection_service][add_to_collection] Error adding game {} to collection of user {}: {}", request.gameId, userId, e.message)
            throw e
        }
    }

    @Transactional
    fun removeFromCollection(userId: UUID, itemId: UUID) {
        logger.debug("\n\t[DEBUG] [collection_service][remove_from_collection] User {} removing collection item {}", userId, itemId)

        try {
            val item = collectionItemRepository.findById(itemId)
                .orElseThrow { CollectionItemNotFoundException(itemId) }

            if (item.user.id != userId) {
                throw NotCollectionItemOwnerException()
            }

            collectionItemRepository.delete(item)

            logger.info("\n\t[INFO] [collection_service][remove_from_collection] Collection item {} removed by user {}", itemId, userId)
        } catch(e: CollectionItemNotFoundException) {
            logger.warn("\n\t[WARN] [collection_service][remove_from_collection] Collection item {} not found", itemId)
            throw e
        } catch(e: NotCollectionItemOwnerException) {
            logger.warn("\n\t[WARN] [collection_service][remove_from_collection] User {} does not own collection item {}", userId, itemId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [collection_service][remove_from_collection] Error removing collection item {}: {}", itemId, e.message)
            throw e
        }
    }

    fun listCollection(userId: UUID, page: Int, size: Int, gameName: String?, sort: String?): PageDTO<CollectionItemDTO> {
        logger.debug("\n\t[DEBUG] [collection_service][list_collection] Listing collection\n\tuserId={}\n\tpage={}\n\tsize={}\n\tgameName={}\n\tsort={}", userId, page, size, gameName, sort)

        try {
            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 100)
            val sortObj = resolveSort(sort, setOf("game.name", "createdAt"), "createdAt")
            val pageable = PageRequest.of(pageSafe, sizeSafe, sortObj)

            val spec = CollectionItemSpecification.withFilters(userId, gameName)
            val result = collectionItemRepository.findAll(spec, pageable)

            logger.info("\n\t[INFO] [collection_service][list_collection] Retrieved collection\n\tresultSize={}\n\ttotalElements={}", result.numberOfElements, result.totalElements)
            return result.toPageDTO { CollectionItemDTO.from(it) }
        } catch(e: InvalidSortException) {
            logger.warn("\n\t[WARN] [collection_service][list_collection] Invalid sort field: {}", sort)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [collection_service][list_collection] Error listing collection for user {}: {}", userId, e.message)
            throw e
        }
    }
}