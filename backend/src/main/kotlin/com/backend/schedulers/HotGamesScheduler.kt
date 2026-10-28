package com.backend.schedulers

import com.backend.services.GameService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class HotGamesScheduler(
    private val gameService: GameService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun refreshHotGames() {
        logger.info("\n\t[INFO] [hot_games_scheduler][refresh_hot_games] Triggering hot games refresh")
        gameService.refreshHotGames()
    }
}