package com.backend.services

import com.backend.exceptions.CollectionItemNotFoundException
import com.backend.exceptions.GameAlreadyInCollectionException
import com.backend.exceptions.GameNotFoundException
import com.backend.exceptions.InvalidSortException
import com.backend.exceptions.NotCollectionItemOwnerException
import com.backend.models.dtos.AddToCollectionRequest
import com.backend.models.dtos.CollectionItemDTO
import com.backend.models.dtos.CollectionStatusDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.entities.CollectionItem
import com.backend.models.mappers.toPageDTO
import com.backend.models.specifications.CollectionItemSpecification
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.UserRepository
import com.backend.utils.resolveSort
import com.backend.utils.safePageRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CollectionService(
    private val collectionItemRepository: CollectionItemRepository,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
    private val userAchievementService: UserAchievementService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SORT_FIELDS = setOf("game.name", "createdAt")
        private const val DEFAULT_SORT_FIELD = "createdAt"
    }

    @Transactional
    fun addToCollection(userId: UUID, request: AddToCollectionRequest): CollectionItemDTO {
        val gameId = request.gameId
        logger.debug("\n\t[DEBUG] [collection_service][add_to_collection] User {} adding game {} to collection", userId, gameId)

        try {
            if(collectionItemRepository.existsByUserIdAndGameId(userId, gameId)) {
                throw GameAlreadyInCollectionException(gameId)
            }

            val game = gameRepository.findById(gameId)
                .orElseThrow { GameNotFoundException(gameId) }

            val saved = try {
                collectionItemRepository.saveAndFlush(CollectionItem(user = userRepository.getReferenceById(userId), game = game))
            } catch(_: DataIntegrityViolationException) {
                throw GameAlreadyInCollectionException(gameId)
            }

            userAchievementService.evaluateAfterCommit(setOf(userId))

            logger.info("\n\t[INFO] [collection_service][add_to_collection] Game {} added to collection of user {}", gameId, userId)
            return CollectionItemDTO.from(saved)
        } catch(e: GameAlreadyInCollectionException) {
            logger.warn("\n\t[WARN] [collection_service][add_to_collection] Game {} already in collection of user {}", gameId, userId)
            throw e
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [collection_service][add_to_collection] Game {} not found", gameId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [collection_service][add_to_collection] Error adding game {} to collection of user {}: {}", gameId, userId, e.message)
            throw e
        }
    }

    @Transactional
    fun removeFromCollection(userId: UUID, itemId: UUID) {
        logger.debug("\n\t[DEBUG] [collection_service][remove_from_collection] User {} removing collection item {}", userId, itemId)

        try {
            val item = collectionItemRepository.findById(itemId)
                .orElseThrow { CollectionItemNotFoundException(itemId) }

            if(item.user.id != userId) {
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

    @Transactional(readOnly = true)
    fun listCollection(userId: UUID, page: Int, size: Int, gameName: String?, played: Boolean?, isExpansion: Boolean?, sort: String?): PageDTO<CollectionItemDTO> {
        logger.debug("\n\t[DEBUG] [collection_service][list_collection] Listing collection\n\tuserId={}\n\tpage={}\n\tsize={}\n\tgameName={}\n\tplayed={}\n\tisExpansion={}\n\tsort={}", userId, page, size, gameName, played, isExpansion, sort)

        try {
            val pageable = safePageRequest(page, size, resolveSort(sort, SORT_FIELDS, DEFAULT_SORT_FIELD))
            val result = collectionItemRepository.findAll(CollectionItemSpecification.withFilters(userId, gameName, played, isExpansion), pageable)

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

    @Transactional(readOnly = true)
    fun getStatus(userId: UUID, gameId: UUID): CollectionStatusDTO {
        logger.debug("\n\t[DEBUG] [collection_service][get_status] Checking status for user {} game {}", userId, gameId)

        try {
            val itemId = collectionItemRepository.findIdByUserIdAndGameId(userId, gameId)
            val response = CollectionStatusDTO(inCollection = itemId != null, itemId = itemId)

            logger.info("\n\t[INFO] [collection_service][get_status] Status for user {} game {}: {}", userId, gameId, response.inCollection)
            return response
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [collection_service][get_status] Error checking status for user {} game {}: {}", userId, gameId, e.message)
            throw e
        }
    }
}