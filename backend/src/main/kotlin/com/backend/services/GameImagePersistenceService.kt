package com.backend.services

import com.backend.models.entities.Game
import com.backend.models.entities.GameImage
import com.backend.repositories.GameImageRepository
import com.backend.repositories.GameRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class GameImagePersistenceService(
    private val gameImageRepository: GameImageRepository,
    private val gameRepository: GameRepository,
) {

    @Transactional
    fun replaceImages(game: Game, images: List<GameImage>): List<GameImage> {
        val gameId = game.id!!

        gameImageRepository.deleteAllByGameId(gameId)

        val managedGame = gameRepository.findById(gameId).orElseThrow()
        val saved = gameImageRepository.saveAll(images.onEach { it.game = managedGame })

        managedGame.imagesSyncedAt = Instant.now()
        gameRepository.save(managedGame)

        return saved.sortedBy { it.position }
    }
}