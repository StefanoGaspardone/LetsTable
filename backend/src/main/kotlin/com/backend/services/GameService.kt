package com.backend.services

import com.backend.clients.BggClient
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.GameSearchResultResponse
import com.backend.models.dtos.HotGameResponse
import com.backend.models.entities.Game
import com.backend.models.entities.HotGame
import com.backend.repositories.GameRepository
import com.backend.repositories.HotGameRepository
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class GameService(
    private val bggClient: BggClient,
    private val gameRepository: GameRepository,
    private val hotGameRepository: HotGameRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val STALE_AFTER_DAYS = 7L
    }

    fun search(query: String): List<GameSearchResultResponse> {
        logger.debug("\n\t[DEBUG] [game_service][search] Searching games with query {}", query)

        try {
            val results = bggClient.searchGames(query).items.map {
                GameSearchResultResponse(
                    bggId = it.id,
                    name = it.name?.value ?: "Unknown",
                    yearPublished = it.yearPublished?.value?.toIntOrNull(),
                )
            }

            logger.info("\n\t[INFO] [game_service][search] Returning {} results for query {}", results.size, query)
            return results
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][search] Error searching games with query {}: {}", query, e.message)
            throw e
        }
    }

    @Transactional
    fun getOrSyncGame(bggId: Long): GameDTO {
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

            logger.info("\n\t[INFO] [game_service][get_or_sync_game] Resolved game with bggId {}", bggId)
            return GameDTO.from(game)
        } catch(e: GameNotFoundOnBggException) {
            logger.warn("\n\t[WARN] [game_service][get_or_sync_game] Game not found on BGG with id {}", bggId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_or_sync_game] Error resolving game with bggId {}: {}", bggId, e.message)
            throw e
        }
    }

    fun getHotGames(): List<HotGameResponse> {
        logger.debug("\n\t[DEBUG] [game_service][get_hot_games] Retrieving cached hot games")

        try {
            val hotGames = hotGameRepository.findAllByOrderByRankAsc().map {
                HotGameResponse(
                    bggId = it.bggId,
                    rank = it.rank,
                    name = it.name,
                    thumbnailUrl = it.thumbnailUrl,
                    yearPublished = it.yearPublished,
                )
            }

            logger.info("\n\t[INFO] [game_service][get_hot_games] Returning {} hot games", hotGames.size)
            return hotGames
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_hot_games] Error retrieving cached hot games: {}", e.message)
            throw e
        }
    }

    @Transactional
    fun refreshHotGames() {
        logger.debug("\n\t[DEBUG] [game_service][refresh_hot_games] Refreshing hot games cache from BGG")

        try {
            val fetched = bggClient.getHotGames().items.map {
                HotGame(
                    bggId = it.id,
                    rank = it.rank,
                    name = it.name?.value ?: "Unknown",
                    thumbnailUrl = it.thumbnail?.value,
                    yearPublished = it.yearPublished?.value?.toIntOrNull(),
                )
            }

            hotGameRepository.deleteAllInBatch()
            hotGameRepository.saveAll(fetched)

            logger.info("\n\t[INFO] [game_service][refresh_hot_games] Hot games cache refreshed with {} entries", fetched.size)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][refresh_hot_games] Error refreshing hot games cache: {}", e.message)
            throw e
        }
    }

    private fun syncFromBgg(bggId: Long, existing: Game?): Game {
        val details = bggClient.getGameDetails(bggId).items.firstOrNull()
            ?: throw GameNotFoundOnBggException(bggId)

        val cleanDescription = details.description?.let { Jsoup.parse(it).text() }

        val game = existing ?: Game(bggId = bggId, name = "", lastSyncedAt = Instant.now())

        game.name = details.primaryName() ?: game.name
        game.yearPublished = details.yearPublished?.value?.toIntOrNull()
        game.thumbnailUrl = details.thumbnail
        game.imageUrl = details.image
        game.minPlayers = details.minPlayers?.value?.toIntOrNull()
        game.maxPlayers = details.maxPlayers?.value?.toIntOrNull()
        game.playingTimeMinutes = details.playingTime?.value?.toIntOrNull()
        game.description = cleanDescription
        game.lastSyncedAt = Instant.now()

        return gameRepository.save(game)
    }
}