package com.backend.services

import com.backend.models.entities.Game
import com.backend.repositories.GameRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HotGamesPersistenceService(private val gameRepository: GameRepository) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun saveHotGames(games: List<Game>) {
        logger.debug("\n\t[DEBUG] [hot_games_persistence_service][save_hot_games] Saving hot games")

        try {
            gameRepository.clearAllRanks()
            gameRepository.saveAll(games)

            logger.info("\n\t[INFO] [hot_games_persistence_service][save_hot_games] Successfully saved hot games")
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [hot_games_persistence_service][save_hot_games] Failed to save hot games: {}", e.message)
        }
    }

    @Transactional
    fun saveGames(games: List<Game>): List<Game> {
        logger.debug("\n\t[DEBUG] [hot_games_persistence_service][save_games] Saving games")

        return gameRepository.saveAll(games)
    }
}