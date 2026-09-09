package com.backend.services

import com.backend.models.entities.Game
import com.backend.models.entities.GameSleeve
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class GameSleevePersistenceService(
    private val gameRepository: GameRepository,
    private val gameSleeveRepository: GameSleeveRepository,
) {

    @Transactional
    fun replaceSleeves(gameId: UUID, game: Game, sleeves: List<GameSleeve>) {
        gameSleeveRepository.deleteAllByGameId(gameId)

        gameSleeveRepository.saveAll(sleeves)

        game.sleevesSyncedAt = Instant.now()

        gameRepository.save(game)
    }
}