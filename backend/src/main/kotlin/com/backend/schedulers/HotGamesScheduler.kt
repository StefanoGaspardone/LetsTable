package com.backend.schedulers

import com.backend.services.GameService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HotGamesScheduler(
    private val gameService: GameService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 6 * 60 * 60 * 1000, initialDelay = 0)
    fun refreshHotGames() {
        logger.debug("\n\t[DEBUG] [hot_games_scheduler][refresh_hot_games] Triggering scheduled hot games refresh")

        try {
            gameService.refreshHotGames()
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [hot_games_scheduler][refresh_hot_games] Scheduled refresh failed: {}", e.message)
        }
    }
}