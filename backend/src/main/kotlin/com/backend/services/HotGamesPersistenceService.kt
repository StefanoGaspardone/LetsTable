package com.backend.services

import com.backend.models.entities.Game
import com.backend.repositories.GameRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HotGamesPersistenceService(
    private val gameRepository: GameRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun saveHotGames(games: List<Game>) {
        logger.debug("\n\t[DEBUG] [hot_games_persistence_service][save_hot_games] Saving {} hot games", games.size)

        gameRepository.clearAllRanks()
        gameRepository.saveAll(games)

        logger.info("\n\t[INFO] [hot_games_persistence_service][save_hot_games] Successfully saved {} hot games", games.size)
    }

    @Transactional
    fun saveGames(games: List<Game>): List<Game> {
        logger.debug("\n\t[DEBUG] [hot_games_persistence_service][save_games] Saving {} games", games.size)

        return gameRepository.saveAll(games)
    }
}