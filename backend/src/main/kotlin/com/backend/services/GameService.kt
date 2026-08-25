package com.backend.services

import com.backend.clients.BggClient
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.models.dtos.GameDTO
import com.backend.models.dtos.GameSearchResultResponse
import com.backend.models.dtos.HotGameResponse
import com.backend.models.dtos.PageDTO
import com.backend.models.entities.Game
import com.backend.models.entities.HotGame
import com.backend.models.mappers.toPageDTO
import com.backend.repositories.GameRepository
import com.backend.repositories.HotGameRepository
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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

    fun getHotGames(page: Int, size: Int): PageDTO<HotGameResponse> {
        logger.debug("\n\t[DEBUG] [game_service][get_hot_games] Retrieving hot games\n\tpage={}\n\tsize={}", page, size)

        try {
            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 100)
            val pageable = PageRequest.of(pageSafe, sizeSafe, Sort.by("rank").ascending())

            val result = hotGameRepository.findAll(pageable)

            logger.info("\n\t[INFO] [game_service][get_hot_games] Retrieved {} hot games", result.numberOfElements)
            return result.toPageDTO {
                HotGameResponse(
                    bggId = it.bggId,
                    rank = it.rank,
                    name = it.name,
                    thumbnailUrl = it.thumbnailUrl,
                    yearPublished = it.yearPublished,
                )
            }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][get_hot_games] Error retrieving hot games: {}", e.message)
            throw e
        }
    }

    fun search(query: String, page: Int, size: Int): PageDTO<GameSearchResultResponse> {
        logger.debug("\n\t[DEBUG] [game_service][search] Searching games\n\tquery={}\n\tpage={}\n\tsize={}", query, page, size)

        try {
            val allResults = bggClient.searchGames(query).items.map {
                GameSearchResultResponse(
                    bggId = it.id,
                    name = it.name?.value ?: "Unknown",
                    yearPublished = it.yearPublished?.value?.toIntOrNull(),
                )
            }

            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 100)
            val pageable = PageRequest.of(pageSafe, sizeSafe)

            val start = pageSafe * sizeSafe
            val content = if(start >= allResults.size) emptyList() else allResults.subList(start, minOf(start + sizeSafe, allResults.size))

            val pageResult = PageImpl(content, pageable, allResults.size.toLong())

            logger.info("\n\t[INFO] [game_service][search] Returning {} results for query {}", content.size, query)
            return pageResult.toPageDTO { it }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [game_service][search] Error searching games with query {}: {}", query, e.message)
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

    @Transactional
    fun getExpansions(bggId: Long, page: Int, size: Int): PageDTO<GameDTO> {
        logger.debug("\n\t[DEBUG] [game_service][get_expansions] Fetching expansions\n\tbggId={}\n\tpage={}\n\tsize={}", bggId, page, size)

        try {
            val game = gameRepository.findByBggId(bggId)
                .orElseThrow { GameNotFoundOnBggException(bggId) }

            val allExpansions = game.expansionRefs.mapNotNull { ref ->
                try {
                    getOrSyncGame(ref.bggId)
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
        game.bestWith = parsePlayerCountRecommendation(details.pollSummaryValue("bestwith"))
        game.recommendedWith = parsePlayerCountRecommendation(details.pollSummaryValue("recommmendedwith"))
        game.expansionRefs = details.expansionRefs()
        game.lastSyncedAt = Instant.now()

        return gameRepository.save(game)
    }

    private val PLAYER_COUNT_REGEX = Regex("""(\d+(?:[–-]\d+)?)""")

    private fun parsePlayerCountRecommendation(raw: String?): String? {
        if(raw.isNullOrBlank()) return null
        if(raw == "(no votes)" || raw == "(Undetermined)") return null

        return PLAYER_COUNT_REGEX.find(raw)?.value
    }
}