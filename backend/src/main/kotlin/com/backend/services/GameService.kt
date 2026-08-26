package com.backend.services

import com.backend.clients.BggClient
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.models.dtos.BggThingItemXml
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.entities.Game
import com.backend.models.mappers.toPageDTO
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.security.CurrentUser
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.collections.forEach

@Service
class GameService(
    private val bggClient: BggClient,
    private val gameRepository: GameRepository,
    private val collectionItemRepository: CollectionItemRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val STALE_AFTER_DAYS = 7L
    }

    @Transactional
    fun getOrSyncGame(bggId: Long, resolveBaseGame: Boolean = true): GameDTO {
        logger.debug("\n\t[DEBUG] [game_service][get_or_sync_game] Resolving game with bggId {}", bggId)

        try {
            val existing = gameRepository.findByBggId(bggId).orElse(null)
            val isStale = existing == null ||
                    existing.lastSyncedAt.isBefore(Instant.now().minus(STALE_AFTER_DAYS, ChronoUnit.DAYS))

            val game = if(isStale) {
                syncFromBgg(bggId, existing)
            } else {
                existing
            }

            val inCollection = game.id?.let { gameId ->
                collectionItemRepository.existsByUserIdAndGameId(CurrentUser.id(), gameId)
            }

            val baseGame = if(resolveBaseGame && game.isExpansion == true && game.baseGameBggId != null) {
                try {
                    getOrSyncGame(game.baseGameBggId!!, resolveBaseGame = false)
                } catch(e: Exception) {
                    logger.warn("\n\t[WARN] [game_service][get_or_sync_game] Could not resolve base game, skipping\n\tbaseGameBggId={}\n\treason={}", game.baseGameBggId, e.message)
                    null
                }
            } else null

            logger.info("\n\t[INFO] [game_service][get_or_sync_game] Resolved game with bggId {}", bggId)
            return GameDTO.from(game, inCollection, baseGame)
        } catch(e: GameNotFoundOnBggException) {
            logger.warn("\n\t[WARN] [game_service][get_or_sync_game] Game not found on BGG with id {}", bggId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_or_sync_game] Error resolving game with bggId {}: {}", bggId, e.message)
            throw e
        }
    }

    @Transactional
    fun getHotGames(page: Int, size: Int): PageDTO<GameDTO> {
        logger.debug("\n\t[DEBUG] [game_service][get_hot_games] Retrieving hot games\n\tpage={}\n\tsize={}", page, size)

        try {
            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 100)
            val pageable = PageRequest.of(pageSafe, sizeSafe)

            val result = gameRepository.findAllByRankIsNotNullOrderByRankAsc(pageable)

            val inCollectionMap = buildInCollectionMap(result.content.mapNotNull { it.id })

            logger.info("\n\t[INFO] [game_service][get_hot_games] Retrieved {} hot games", result.numberOfElements)
            return result.toPageDTO { GameDTO.from(it, inCollectionMap[it.id]) }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_hot_games] Error retrieving hot games: {}", e.message)
            throw e
        }
    }

    @Transactional
    fun refreshHotGames() {
        logger.debug("\n\t[DEBUG] [game_service][refresh_hot_games] Refreshing hot games cache from BGG")

        try {
            val hotItems = bggClient.getHotGames().items
            if(hotItems.isEmpty()) {
                logger.info("\n\t[INFO] [game_service][refresh_hot_games] No hot games returned from BGG")
                return
            }

            gameRepository.clearAllRanks()

            val bggIds = hotItems.map { it.id }

            val detailsByBggId = try {
                bggClient.getGameDetailsBatch(bggIds).items.associateBy { it.id }
            } catch(e: Exception) {
                logger.warn("\n\t[WARN] [game_service][refresh_hot_games] Batch enrichment failed\n\treason={}", e.message)
                emptyMap()
            }

            hotItems.forEach { item ->
                val existing = gameRepository.findByBggId(item.id).orElse(null)
                val details = detailsByBggId[item.id]

                val game = if(details != null) {
                    applyBggDetails(existing ?: Game(bggId = item.id, name = "", lastSyncedAt = Instant.now()), details)
                } else {
                    existing ?: Game(
                        bggId = item.id,
                        name = item.name?.value ?: "Sconosciuto",
                        thumbnailUrl = item.thumbnail?.value,
                        yearPublished = item.yearPublished?.value?.toIntOrNull(),
                        lastSyncedAt = Instant.EPOCH,
                        isExpansion = false
                    )
                }

                game.rank = item.rank
                gameRepository.save(game)
            }

            logger.info("\n\t[INFO] [game_service][refresh_hot_games] Hot games cache refreshed with {} entries", hotItems.size)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][refresh_hot_games] Error refreshing hot games cache: {}", e.message)
            throw e
        }
    }

    @Transactional
    fun search(query: String, page: Int, size: Int): PageDTO<GameDTO> {
        logger.debug("\n\t[DEBUG] [game_service][search] Searching games\n\tquery={}\n\tpage={}\n\tsize={}", query, page, size)

        try {
            val lightweightResults = bggClient.searchGames(query).items

            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 100)
            val pageable = PageRequest.of(pageSafe, sizeSafe)

            val start = pageSafe * sizeSafe
            val pageItems = if(start >= lightweightResults.size) emptyList()
            else lightweightResults.subList(start, minOf(start + sizeSafe, lightweightResults.size))

            val fallback = pageItems.associateBy({ it.id }) { GameDTO.fromSearchResult(it) }
            val enrichedContent = enrichWithBatchDetails(pageItems.map { it.id }, fallback)

            val pageResult = PageImpl(enrichedContent, pageable, lightweightResults.size.toLong())

            logger.info("\n\t[INFO] [game_service][search] Returning {} enriched results for query {}", enrichedContent.size, query)
            return pageResult.toPageDTO { it }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][search] Error searching games with query {}: {}", query, e.message)
            throw e
        }
    }

    @Transactional
    fun getExpansions(bggId: Long, page: Int, size: Int): PageDTO<GameDTO> {
        logger.debug("\n\t[DEBUG] [game_service][get_expansions] Fetching expansions\n\tbggId={}\n\tpage={}\n\tsize={}", bggId, page, size)

        try {
            val game = gameRepository.findByBggId(bggId)
                .orElseThrow { GameNotFoundOnBggException(bggId) }

            val allExpansions = game.expansionRefs.mapNotNull { ref ->
                try {
                    getOrSyncGame(ref.bggId, resolveBaseGame = false)
                } catch(e: Exception) {
                    logger.warn("\n\t[WARN] [game_service][get_expansions] Could not sync expansion, skipping\n\texpansionBggId={}\n\treason={}", ref.bggId, e.message)
                    null
                }
            }

            val sortedExpansions = allExpansions.sortedWith(compareBy({ it.yearPublished ?: Int.MAX_VALUE }, { it.name }))

            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 50)
            val pageable = PageRequest.of(pageSafe, sizeSafe)

            val start = pageSafe * sizeSafe
            val content = if(start >= sortedExpansions.size) emptyList() else sortedExpansions.subList(start, minOf(start + sizeSafe, sortedExpansions.size))

            val pageResult = PageImpl(content, pageable, sortedExpansions.size.toLong())

            logger.info("\n\t[INFO] [game_service][get_expansions] Resolved expansions page\n\tbggId={}\n\tpage={}\n\tresolvedCount={}\n\ttotalCount={}", bggId, pageSafe, content.size, sortedExpansions.size)
            return pageResult.toPageDTO { it }
        } catch(e: GameNotFoundOnBggException) {
            logger.warn("\n\t[WARN] [game_service][get_expansions] Game not found\n\tbggId={}", bggId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_expansions] Error fetching expansions\n\tbggId={}\n\treason={}", bggId, e.message)
            throw e
        }
    }

    private fun enrichWithBatchDetails(bggIds: List<Long>, fallback: Map<Long, GameDTO>): List<GameDTO> {
        if(bggIds.isEmpty()) return emptyList()

        val detailsByBggId = try {
            bggClient.getGameDetailsBatch(bggIds).items.associateBy { it.id }
        } catch(e: Exception) {
            logger.warn("\n\t[WARN] [game_service][enrich_with_batch_details] Batch enrichment failed, falling back to lightweight results\n\treason={}", e.message)
            emptyMap()
        }

        val savedGames = mutableMapOf<Long, Game>()

        bggIds.forEach { id ->
            val details = detailsByBggId[id]
            if(details != null) {
                try {
                    val existing = gameRepository.findByBggId(id).orElse(null)
                    val game = applyBggDetails(existing ?: Game(bggId = id, name = "", lastSyncedAt = Instant.now()), details)

                    savedGames[id] = gameRepository.save(game)
                } catch(e: Exception) {
                    logger.warn("\n\t[WARN] [game_service][enrich_with_batch_details] Failed to persist enriched game, using lightweight fallback\n\tbggId={}\n\treason={}", id, e.message)
                }
            }
        }

        val inCollectionMap = buildInCollectionMap(savedGames.values.mapNotNull { it.id })

        return bggIds.map { id ->
            val game = savedGames[id]

            if(game == null) {
                fallback.getValue(id)
            } else {
                GameDTO.from(game, inCollectionMap[game.id])
            }
        }
    }

    private fun applyBggDetails(game: Game, details: BggThingItemXml): Game {
        val cleanDescription = details.description
            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            ?.let { Jsoup.parse(it).body().wholeText() }
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()

        game.name = details.primaryName() ?: game.name
        game.yearPublished = details.yearPublished?.value?.toIntOrNull()
        game.thumbnailUrl = details.thumbnail
        game.imageUrl = details.image
        game.minPlayers = details.minPlayers?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.maxPlayers = details.maxPlayers?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.playingTimeMinutes = details.playingTime?.value?.toIntOrNull()?.takeIf { it > 0 }
        game.description = cleanDescription
        game.bestWith = parsePlayerCountRecommendation(details.pollSummaryValue("bestwith"))
        game.recommendedWith = parsePlayerCountRecommendation(details.pollSummaryValue("recommmendedwith"))
        game.expansionRefs = details.expansionRefs()
        game.lastSyncedAt = Instant.now()
        game.isExpansion = details.type != "boardgame"

        val baseGameRef = details.baseGameRef()
        game.baseGameBggId = baseGameRef?.bggId

        game.difficulty = details.statistics?.ratings?.averageWeight?.value?.toDoubleOrNull()?.takeIf { it > 0 }
        game.designers = details.links.filter { it.type == "boardgamedesigner" }.map { it.value }
        game.artists = details.links.filter { it.type == "boardgameartist" }.map { it.value }
        game.publishers = details.links.filter { it.type == "boardgamepublisher" }.map { it.value }

        return game
    }

    private fun syncFromBgg(bggId: Long, existing: Game?): Game {
        val details = bggClient.getGameDetails(bggId).items.firstOrNull()
            ?: throw GameNotFoundOnBggException(bggId)

        val game = existing ?: Game(bggId = bggId, name = "", lastSyncedAt = Instant.now())
        return gameRepository.save(applyBggDetails(game, details))
    }

    private val PLAYER_COUNT_REGEX = Regex("""(\d+(?:[–-]\d+)?)""")

    private fun parsePlayerCountRecommendation(raw: String?): String? {
        if(raw.isNullOrBlank()) return null
        if(raw == "(no votes)" || raw == "(Undetermined)") return null

        return PLAYER_COUNT_REGEX.find(raw)?.value
    }

    private fun buildInCollectionMap(gameIds: List<UUID>): Map<UUID, Boolean> {
        if(gameIds.isEmpty()) return emptyMap()

        val idsInCollection = collectionItemRepository.findGameIdsInCollection(CurrentUser.id(), gameIds)
        return gameIds.associateWith { it in idsInCollection }
    }
}