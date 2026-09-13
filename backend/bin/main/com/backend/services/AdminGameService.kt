package com.backend.services

import com.backend.exceptions.GameNotFoundException
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.models.dtos.AdminGameDTO
import com.backend.models.dtos.AdminUploadedFileDTO
import com.backend.models.enums.FileOwnerType
import com.backend.repositories.GameRepository
import com.backend.repositories.UploadedFileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminGameService(
    private val gameService: GameService,
    private val gameRepository: GameRepository,
    private val uploadedFileRepository: UploadedFileRepository,
    private val uploadedFileService: UploadedFileService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun forceRefreshGame(bggId: Long): AdminGameDTO {
        logger.debug("\n\t[DEBUG] [admin_game_service][force_refresh_game] Force refreshing game with bggId {}", bggId)

        try {
            gameService.forceRefreshGame(bggId)
            val game = gameRepository.findByBggId(bggId)
                .orElseThrow { GameNotFoundOnBggException(bggId) }

            logger.info("\n\t[INFO] [admin_game_service][force_refresh_game] Refreshed game with bggId {}", bggId)
            return AdminGameDTO.from(game)
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

    fun listRuleFiles(gameId: UUID): List<AdminUploadedFileDTO> {
        logger.debug("\n\t[DEBUG] [admin_game_service][list_rule_files] Listing rule files for game {}", gameId)

        try {
            if(!gameRepository.existsById(gameId)) {
                throw GameNotFoundException(gameId)
            }

            val files = uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.GAME_RULE, gameId)

            logger.info("\n\t[INFO] [admin_game_service][list_rule_files] Found {} rule files for game {}", files.size, gameId)
            return files.map { AdminUploadedFileDTO.from(it) }
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
            uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId)
            logger.info("\n\t[INFO] [admin_game_service][delete_rule_file] Rule file {} deleted for game {}", fileId, gameId)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [admin_game_service][delete_rule_file] Error deleting rule file {} for game {}: {}", fileId, gameId, e.message)
            throw e
        }
    }
}