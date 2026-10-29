package com.backend.services

import com.backend.exceptions.GameNotFoundException
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.exceptions.InvalidSortException
import com.backend.exceptions.UploadedFileNotFoundException
import com.backend.models.dtos.AdminGameDTO
import com.backend.models.dtos.AdminUploadedFileDTO
import com.backend.models.dtos.PageDTO
import com.backend.models.enums.FileOwnerType
import com.backend.models.mappers.toPageDTO
import com.backend.models.specifications.GameSpecification
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import com.backend.repositories.UploadedFileRepository
import com.backend.utils.resolveSort
import com.backend.utils.safePageRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminGameService(
    private val gameService: GameService,
    private val gameRepository: GameRepository,
    private val gameSleeveRepository: GameSleeveRepository,
    private val uploadedFileRepository: UploadedFileRepository,
    private val uploadedFileService: UploadedFileService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SORT_FIELDS = setOf("name", "isExpansion", "rank", "bggRank", "lastSyncedAt")
        private const val DEFAULT_SORT_FIELD = "name"
    }

    fun forceRefreshGame(bggId: Long): AdminGameDTO {
        logger.debug("\n\t[DEBUG] [admin_game_service][force_refresh_game] Force refreshing game with bggId {}", bggId)

        try {
            gameService.forceRefreshGame(bggId)

            val game = gameRepository.findByBggId(bggId)
                .orElseThrow { GameNotFoundOnBggException(bggId) }

            logger.info("\n\t[INFO] [admin_game_service][force_refresh_game] Refreshed game with bggId {}", bggId)
            return AdminGameDTO.from(game, gameSleeveRepository.findAllByGameId(game.id!!))
        } catch(e: GameNotFoundOnBggException) {
            logger.warn("\n\t[WARN] [admin_game_service][force_refresh_game] Game not found on BGG with id {}", bggId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_game_service][force_refresh_game] Error refreshing game with bggId {}: {}", bggId, e.message)
            throw e
        }
    }

    fun forceRefreshHotGames() {
        logger.debug("\n\t[DEBUG] [admin_game_service][force_refresh_hot_games] Force refreshing hot games cache")

        try {
            gameService.forceRefreshHotGames()

            logger.info("\n\t[INFO] [admin_game_service][force_refresh_hot_games] Hot games cache refreshed")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_game_service][force_refresh_hot_games] Error refreshing hot games cache: {}", e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun listGames(page: Int, size: Int, search: String?, isExpansion: Boolean?, sort: String?): PageDTO<AdminGameDTO> {
        logger.debug("\n\t[DEBUG] [admin_game_service][list_games] Listing games\n\tpage={}\n\tsize={}\n\tsearch={}\n\tisExpansion={}\n\tsort={}", page, size, search, isExpansion, sort)

        try {
            val pageable = safePageRequest(page, size, resolveSort(sort, SORT_FIELDS, DEFAULT_SORT_FIELD))
            val result = gameRepository.findAll(GameSpecification.withFilters(search, isExpansion), pageable)

            val gameIds = result.content.mapNotNull { it.id }
            val sleevesByGameId = if(gameIds.isEmpty()) emptyMap() else gameSleeveRepository.findAllByGameIdIn(gameIds).groupBy { it.game.id }

            logger.info("\n\t[INFO] [admin_game_service][list_games] Retrieved {} games", result.numberOfElements)
            return result.toPageDTO { AdminGameDTO.from(it, sleevesByGameId[it.id].orEmpty()) }
        } catch(e: InvalidSortException) {
            logger.warn("\n\t[WARN] [admin_game_service][list_games] Invalid sort field: {}", sort)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_game_service][list_games] Error listing games: {}", e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getGame(gameId: UUID): AdminGameDTO {
        logger.debug("\n\t[DEBUG] [admin_game_service][get_game] Retrieving game {}", gameId)

        try {
            val game = gameRepository.findById(gameId)
                .orElseThrow { GameNotFoundException(gameId) }

            logger.info("\n\t[INFO] [admin_game_service][get_game] Retrieved game {}", gameId)
            return AdminGameDTO.from(game, gameSleeveRepository.findAllByGameId(gameId))
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [admin_game_service][get_game] Game {} not found", gameId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_game_service][get_game] Error retrieving game {}: {}", gameId, e.message)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun listRuleFiles(gameId: UUID): List<AdminUploadedFileDTO> {
        logger.debug("\n\t[DEBUG] [admin_game_service][list_rule_files] Listing rule files for game {}", gameId)

        try {
            ensureGameExists(gameId)

            val files = uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.GAME_RULE, gameId)
                .map { AdminUploadedFileDTO.from(it) }

            logger.info("\n\t[INFO] [admin_game_service][list_rule_files] Found {} rule files for game {}", files.size, gameId)
            return files
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [admin_game_service][list_rule_files] Game {} not found", gameId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_game_service][list_rule_files] Error listing rule files for game {}: {}", gameId, e.message)
            throw e
        }
    }

    @Transactional
    fun deleteRuleFile(gameId: UUID, fileId: UUID) {
        logger.debug("\n\t[DEBUG] [admin_game_service][delete_rule_file] Deleting rule file {} for game {}", fileId, gameId)

        try {
            ensureGameExists(gameId)
            uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId)

            logger.info("\n\t[INFO] [admin_game_service][delete_rule_file] Rule file {} deleted for game {}", fileId, gameId)
        } catch(e: GameNotFoundException) {
            logger.warn("\n\t[WARN] [admin_game_service][delete_rule_file] Game {} not found", gameId)
            throw e
        } catch(e: UploadedFileNotFoundException) {
            logger.warn("\n\t[WARN] [admin_game_service][delete_rule_file] Rule file {} not found for game {}", fileId, gameId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_game_service][delete_rule_file] Error deleting rule file {} for game {}: {}", fileId, gameId, e.message)
            throw e
        }
    }

    private fun ensureGameExists(gameId: UUID) {
        if(!gameRepository.existsById(gameId)) {
            throw GameNotFoundException(gameId)
        }
    }
}