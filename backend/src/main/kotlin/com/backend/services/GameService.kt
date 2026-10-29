package com.backend.services

import com.backend.clients.BggClient
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.models.dtos.BggThingItemXml
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.GameSleeveDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.entities.Game
import com.backend.models.entities.GameSleeve
import com.backend.models.mappers.toPageDTO
import com.backend.repositories.BggRankIndexRepository
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import com.backend.security.CurrentUser
import com.backend.utils.BggDetails
import com.backend.utils.safePageRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class GameService(
    private val bggClient: BggClient,
    private val gameRepository: GameRepository,
    private val collectionItemRepository: CollectionItemRepository,
    private val gameSleeveRepository: GameSleeveRepository,
    private val hotGamesPersistenceService: HotGamesPersistenceService,
    private val gameSleevePersistenceService: GameSleevePersistenceService,
    private val bggRankIndexRepository: BggRankIndexRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val STALE_AFTER_DAYS = 7L
        private const val MAX_EXPANSIONS_PAGE_SIZE = 50
        private const val MAX_OVERALL_PAGE_SIZE = 50
        private const val UNKNOWN_GAME_NAME = "Sconosciuto"
    }

    fun getOrSyncGame(bggId: Long, resolveBaseGame: Boolean = true): GameDTO {
        logger.debug("\n\t[DEBUG] [game_service][get_or_sync_game] Resolving game with bggId {}", bggId)

        try {
            val existing = gameRepository.findByBggId(bggId).orElse(null)
            val game = if(existing == null || isStale(existing)) syncFromBgg(bggId, existing) else existing
            val gameId = game.id!!

            if(game.sleevesSyncedAt == null) {
                syncSleeves(game)
            }

            val sleeves = gameSleeveRepository.findAllByGameId(gameId).map { GameSleeveDTO.from(it) }
            val inCollection = collectionItemRepository.existsByUserIdAndGameId(CurrentUser.id(), gameId)

            val baseGame = game.baseGameBggId
                ?.takeIf { resolveBaseGame && game.isExpansion == true }
                ?.let { findBaseGame(it) }

            logger.info("\n\t[INFO] [game_service][get_or_sync_game] Resolved game with bggId {}", bggId)
            return GameDTO.from(game, inCollection, baseGame, sleeves)
        } catch(e: GameNotFoundOnBggException) {
            logger.warn("\n\t[WARN] [game_service][get_or_sync_game] Game not found on BGG with id {}", bggId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_or_sync_game] Error resolving game with bggId {}: {}", bggId, e.message, e)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getHotGames(page: Int, size: Int): PageDTO<GameDTO> {
        logger.debug("\n\t[DEBUG] [game_service][get_hot_games] Retrieving hot games\n\tpage={}\n\tsize={}", page, size)

        try {
            val result = gameRepository.findAllByRankIsNotNullOrderByRankAsc(safePageRequest(page, size))
            val gameIds = result.content.mapNotNull { it.id }

            val inCollectionMap = buildInCollectionMap(gameIds)
            val sleevesMap = buildSleevesMap(gameIds)

            logger.info("\n\t[INFO] [game_service][get_hot_games] Retrieved {} hot games", result.numberOfElements)
            return result.toPageDTO { GameDTO.from(it, inCollectionMap[it.id], sleeves = sleevesMap[it.id].orEmpty()) }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_hot_games] Error retrieving hot games: {}", e.message, e)
            throw e
        }
    }

    fun refreshHotGames() {
        try {
            forceRefreshHotGames()
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][refresh_hot_games] Error refreshing hot games cache: {}", e.message, e)
        }
    }

    fun forceRefreshHotGames() {
        logger.debug("\n\t[DEBUG] [game_service][force_refresh_hot_games] Refreshing hot games cache from BGG")

        val hotItems = bggClient.getHotGames().items

        if(hotItems.isEmpty()) {
            logger.info("\n\t[INFO] [game_service][force_refresh_hot_games] No hot games returned from BGG")
            return
        }

        val bggIds = hotItems.map { it.id }
        val detailsByBggId = fetchDetailsOrEmpty(bggIds)
        val existingByBggId = gameRepository.findAllByBggIdIn(bggIds).associateBy { it.bggId }

        val gamesToSave = hotItems.map { item ->
            val existing = existingByBggId[item.id]
            val details = detailsByBggId[item.id]

            val game = when {
                details != null -> applyDetails(item.id, existing, details)
                existing != null -> existing
                else -> Game(
                    bggId = item.id,
                    name = item.name?.value ?: UNKNOWN_GAME_NAME,
                    thumbnailUrl = item.thumbnail?.value,
                    yearPublished = item.yearPublished?.value?.toIntOrNull(),
                    lastSyncedAt = Instant.EPOCH,
                    isExpansion = false,
                )
            }

            game.rank = item.rank
            game
        }

        hotGamesPersistenceService.saveHotGames(gamesToSave)

        logger.info("\n\t[INFO] [game_service][force_refresh_hot_games] Hot games cache refreshed with {} entries", gamesToSave.size)
    }

    fun search(query: String, page: Int, size: Int): PageDTO<GameDTO> {
        logger.debug("\n\t[DEBUG] [game_service][search] Searching games\n\tquery={}\n\tpage={}\n\tsize={}", query, page, size)

        try {
            val results = bggClient.searchGames(query).items
            val pageable = safePageRequest(page, size)
            val pageItems = slice(results, pageable)

            val gamesByBggId = resolveGames(pageItems.map { it.id })
            val inCollectionMap = buildInCollectionMap(gamesByBggId.values.mapNotNull { it.id })

            val content = pageItems.map { item ->
                gamesByBggId[item.id]?.let { GameDTO.from(it, inCollectionMap[it.id]) } ?: GameDTO.fromSearchResult(item)
            }

            logger.info("\n\t[INFO] [game_service][search] Returning {} results for query {}", content.size, query)
            return paginate(content, pageable, results.size.toLong())
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][search] Error searching games with query {}: {}", query, e.message, e)
            throw e
        }
    }

    fun getExpansions(bggId: Long, page: Int, size: Int): PageDTO<GameDTO> {
        logger.debug("\n\t[DEBUG] [game_service][get_expansions] Fetching expansions\n\tbggId={}\n\tpage={}\n\tsize={}", bggId, page, size)

        try {
            val game = gameRepository.findByBggId(bggId)
                .orElseThrow { GameNotFoundOnBggException(bggId) }

            val expansions = resolveGames(game.expansionRefs.map { it.bggId }.distinct()).values
                .sortedWith(compareBy({ it.yearPublished ?: Int.MAX_VALUE }, { it.name }))

            val pageable = safePageRequest(page, size, maxSize = MAX_EXPANSIONS_PAGE_SIZE)
            val pageGames = slice(expansions, pageable)
            val pageGameIds = pageGames.mapNotNull { it.id }

            val inCollectionMap = buildInCollectionMap(pageGameIds)
            val sleevesMap = buildSleevesMap(pageGameIds)

            val content = pageGames.map { GameDTO.from(it, inCollectionMap[it.id], sleeves = sleevesMap[it.id].orEmpty()) }

            logger.info("\n\t[INFO] [game_service][get_expansions] Resolved expansions page\n\tbggId={}\n\tpage={}\n\tresolvedCount={}\n\ttotalCount={}", bggId, pageable.pageNumber, content.size, expansions.size)
            return paginate(content, pageable, expansions.size.toLong())
        } catch(e: GameNotFoundOnBggException) {
            logger.warn("\n\t[WARN] [game_service][get_expansions] Game not found\n\tbggId={}", bggId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_expansions] Error fetching expansions\n\tbggId={}\n\treason={}", bggId, e.message, e)
            throw e
        }
    }

    fun getOverallGames(page: Int, size: Int): PageDTO<GameDTO> {
        logger.debug("\n\t[DEBUG] [game_service][get_overall_games] Retrieving overall ranking\n\tpage={}\n\tsize={}", page, size)

        try {
            val pageable = safePageRequest(page, size, maxSize = MAX_OVERALL_PAGE_SIZE)
            val rankPage = bggRankIndexRepository.findAllByOrderByRankAsc(pageable)
            val bggIds = rankPage.content.map { it.bggId }

            val gamesByBggId = resolveGames(bggIds)
            val inCollectionMap = buildInCollectionMap(gamesByBggId.values.mapNotNull { it.id })

            val content = bggIds.mapNotNull { gamesByBggId[it] }.map { GameDTO.from(it, inCollectionMap[it.id]) }

            logger.info("\n\t[INFO] [game_service][get_overall_games] Retrieved {} games for overall ranking page {}", content.size, pageable.pageNumber)
            return paginate(content, pageable, rankPage.totalElements)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_overall_games] Error retrieving overall ranking: {}", e.message, e)
            throw e
        }
    }

    fun forceRefreshGame(bggId: Long): GameDTO {
        logger.debug("\n\t[DEBUG] [game_service][force_refresh_game] Forcing refresh for game with bggId {}", bggId)

        try {
            val game = syncFromBgg(bggId, gameRepository.findByBggId(bggId).orElse(null))

            syncSleeves(game)
            val sleeves = gameSleeveRepository.findAllByGameId(game.id!!).map { GameSleeveDTO.from(it) }

            logger.info("\n\t[INFO] [game_service][force_refresh_game] Refreshed game with bggId {}", bggId)
            return GameDTO.from(game, sleeves = sleeves)
        } catch(e: GameNotFoundOnBggException) {
            logger.warn("\n\t[WARN] [game_service][force_refresh_game] Game not found on BGG with id {}", bggId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][force_refresh_game] Error refreshing game with bggId {}: {}", bggId, e.message, e)
            throw e
        }
    }

    private fun findBaseGame(baseGameBggId: Long): GameDTO? =
        try {
            getOrSyncGame(baseGameBggId, resolveBaseGame = false)
        } catch(e: Exception) {
            logger.warn("\n\t[WARN] [game_service][find_base_game] Could not resolve base game, skipping\n\tbaseGameBggId={}\n\treason={}", baseGameBggId, e.message)
            null
        }

    private fun resolveGames(bggIds: List<Long>): Map<Long, Game> {
        if(bggIds.isEmpty()) {
            return emptyMap()
        }

        val existingByBggId = gameRepository.findAllByBggIdIn(bggIds).associateBy { it.bggId }
        val toEnrich = bggIds.filter { id -> existingByBggId[id]?.let { isStale(it) } ?: true }

        logger.debug("\n\t[DEBUG] [game_service][resolve_games] Cache status\n\tfresh={}\n\ttoEnrich={}", bggIds.size - toEnrich.size, toEnrich.size)

        val enrichedByBggId = enrichWithBatchDetails(toEnrich, existingByBggId)

        return bggIds
            .mapNotNull { id -> (enrichedByBggId[id] ?: existingByBggId[id])?.let { id to it } }
            .toMap()
    }

    private fun enrichWithBatchDetails(bggIds: List<Long>, existingByBggId: Map<Long, Game>): Map<Long, Game> {
        if(bggIds.isEmpty()) {
            return emptyMap()
        }

        val detailsByBggId = fetchDetailsOrEmpty(bggIds)
        val gamesToSave = bggIds.mapNotNull { id -> detailsByBggId[id]?.let { applyDetails(id, existingByBggId[id], it) } }

        if(gamesToSave.isEmpty()) {
            return emptyMap()
        }

        val saved = try {
            hotGamesPersistenceService.saveGames(gamesToSave)
        } catch(e: DataIntegrityViolationException) {
            logger.warn("\n\t[WARN] [game_service][enrich_with_batch_details] Concurrent insert detected, reloading games\n\treason={}", e.message)
            gameRepository.findAllByBggIdIn(gamesToSave.map { it.bggId })
        }

        return saved.associateBy { it.bggId }
    }

    private fun fetchDetailsOrEmpty(bggIds: List<Long>): Map<Long, BggThingItemXml> =
        try {
            bggClient.getGameDetailsBatch(bggIds).items.associateBy { it.id }
        } catch(e: Exception) {
            logger.warn("\n\t[WARN] [game_service][fetch_details_or_empty] Batch enrichment failed\n\treason={}", e.message)
            emptyMap()
        }

    private fun syncFromBgg(bggId: Long, existing: Game?): Game {
        val details = bggClient.getGameDetails(bggId).items.firstOrNull()
            ?: throw GameNotFoundOnBggException(bggId)

        return try {
            gameRepository.save(applyDetails(bggId, existing, details))
        } catch(e: DataIntegrityViolationException) {
            gameRepository.findByBggId(bggId).orElseThrow { e }
        }
    }

    private fun syncSleeves(game: Game) {
        try {
            val sleeves = bggClient.getCardSetsByGame(game.bggId).cardSets
                .flatMap { it.cardTypes }
                .map { cardType ->
                    GameSleeve(
                        game = game,
                        name = cardType.name,
                        height = cardType.height?.toDoubleOrNull(),
                        width = cardType.width?.toDoubleOrNull(),
                        quantity = cardType.quantity?.toIntOrNull(),
                        quantityNote = cardType.quantityNote?.takeIf { it.isNotBlank() },
                    )
                }

            gameSleevePersistenceService.replaceSleeves(gameId = game.id!!, game = game, sleeves = sleeves)

            logger.info("\n\t[INFO] [game_service][sync_sleeves] Synced {} card types for game {}", sleeves.size, game.bggId)
        } catch(e: Exception) {
            logger.warn("\n\t[WARN] [game_service][sync_sleeves] Error syncing sleeves for game {}: {}", game.bggId, e.message)
        }
    }

    private fun applyDetails(bggId: Long, existing: Game?, details: BggThingItemXml): Game =
        BggDetails.applyBggDetails(existing ?: Game(bggId = bggId, name = "", lastSyncedAt = Instant.now()), details)

    private fun isStale(game: Game): Boolean =
        game.lastSyncedAt.isBefore(Instant.now().minus(STALE_AFTER_DAYS, ChronoUnit.DAYS))

    private fun buildInCollectionMap(gameIds: List<UUID>): Map<UUID, Boolean> {
        if(gameIds.isEmpty()) {
            return emptyMap()
        }

        val idsInCollection = collectionItemRepository.findGameIdsInCollection(CurrentUser.id(), gameIds)
        return gameIds.associateWith { it in idsInCollection }
    }

    private fun buildSleevesMap(gameIds: List<UUID>): Map<UUID, List<GameSleeveDTO>> {
        if(gameIds.isEmpty()) {
            return emptyMap()
        }

        return gameSleeveRepository.findAllByGameIdIn(gameIds).groupBy({ it.game.id!! }, { GameSleeveDTO.from(it) })
    }

    private fun <T> slice(items: List<T>, pageable: Pageable): List<T> {
        val start = pageable.offset.toInt()

        if(start >= items.size) {
            return emptyList()
        }

        return items.subList(start, minOf(start + pageable.pageSize, items.size))
    }

    private fun <T : Any> paginate(content: List<T>, pageable: Pageable, total: Long): PageDTO<T> =
        PageImpl(content, pageable, total).toPageDTO { it }
}