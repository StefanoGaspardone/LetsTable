package com.backend.unit.services

import com.backend.models.entities.Game
import com.backend.models.entities.GameSleeve
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import com.backend.services.GameSleevePersistenceService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GameSleevePersistenceServiceTest {

    private lateinit var gameRepository: GameRepository
    private lateinit var gameSleeveRepository: GameSleeveRepository
    private lateinit var service: GameSleevePersistenceService

    private val gameId = UUID.randomUUID()

    private lateinit var game: Game

    @BeforeEach
    fun setUp() {
        gameRepository = mockk()
        gameSleeveRepository = mockk()

        service = GameSleevePersistenceService(
            gameRepository,
            gameSleeveRepository
        )

        game = Game(
            id = gameId,
            bggId = 13L,
            name = "Catan",
            lastSyncedAt = Instant.now(),
            sleevesSyncedAt = null
        )
    }

    @Test
    fun `should replace sleeves and mark game as synced`() {
        val sleeves = listOf(
            GameSleeve(
                id = UUID.randomUUID(),
                game = game,
                name = "Standard",
                height = 91.0,
                width = 59.0,
                quantity = 110
            ),
            GameSleeve(
                id = UUID.randomUUID(),
                game = game,
                name = "Mini",
                height = 67.0,
                width = 44.0,
                quantity = 50
            )
        )

        every {
            gameSleeveRepository.deleteAllByGameId(gameId)
        } just Runs

        every {
            gameSleeveRepository.saveAll(sleeves)
        } returns sleeves

        every {
            gameRepository.save(game)
        } returns game

        val before = Instant.now()

        service.replaceSleeves(
            gameId = gameId,
            game = game,
            sleeves = sleeves
        )

        assertThat(game.sleevesSyncedAt)
            .isNotNull

        assertThat(game.sleevesSyncedAt)
            .isAfterOrEqualTo(before)

        verifySequence {
            gameSleeveRepository.deleteAllByGameId(gameId)
            gameSleeveRepository.saveAll(sleeves)
            gameRepository.save(game)
        }
    }

    @Test
    fun `should correctly persist empty sleeve list`() {
        every {
            gameSleeveRepository.deleteAllByGameId(gameId)
        } just Runs

        every {
            gameSleeveRepository.saveAll(emptyList())
        } returns emptyList()

        every {
            gameRepository.save(game)
        } returns game

        service.replaceSleeves(
            gameId = gameId,
            game = game,
            sleeves = emptyList()
        )

        assertThat(game.sleevesSyncedAt)
            .isNotNull

        verify(exactly = 1) {
            gameSleeveRepository.deleteAllByGameId(gameId)
        }

        verify(exactly = 1) {
            gameSleeveRepository.saveAll(emptyList())
        }

        verify(exactly = 1) {
            gameRepository.save(game)
        }
    }

    @Test
    fun `should propagate delete failure and not save new sleeves`() {
        val sleeves = listOf(
            GameSleeve(
                id = UUID.randomUUID(),
                game = game,
                name = "Standard"
            )
        )

        every {
            gameSleeveRepository.deleteAllByGameId(gameId)
        } throws RuntimeException("Delete failed")

        assertThatThrownBy {
            service.replaceSleeves(
                gameId = gameId,
                game = game,
                sleeves = sleeves
            )
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Delete failed")

        verify(exactly = 0) {
            gameSleeveRepository.saveAll(any<List<GameSleeve>>())
        }

        verify(exactly = 0) {
            gameRepository.save(any<Game>())
        }
    }

    @Test
    fun `should propagate sleeve save failure and not save game`() {
        val sleeves = listOf(
            GameSleeve(
                id = UUID.randomUUID(),
                game = game,
                name = "Standard"
            )
        )

        every {
            gameSleeveRepository.deleteAllByGameId(gameId)
        } just Runs

        every {
            gameSleeveRepository.saveAll(sleeves)
        } throws RuntimeException("Sleeve save failed")

        assertThatThrownBy {
            service.replaceSleeves(
                gameId = gameId,
                game = game,
                sleeves = sleeves
            )
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Sleeve save failed")

        verify(exactly = 0) {
            gameRepository.save(any<Game>())
        }
    }

    @Test
    fun `should propagate game save failure after sleeves are persisted`() {
        val sleeves = listOf(
            GameSleeve(
                id = UUID.randomUUID(),
                game = game,
                name = "Standard"
            )
        )

        every {
            gameSleeveRepository.deleteAllByGameId(gameId)
        } just Runs

        every {
            gameSleeveRepository.saveAll(sleeves)
        } returns sleeves

        every {
            gameRepository.save(game)
        } throws RuntimeException("Game save failed")

        assertThatThrownBy {
            service.replaceSleeves(
                gameId = gameId,
                game = game,
                sleeves = sleeves
            )
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Game save failed")

        verify(exactly = 1) {
            gameSleeveRepository.deleteAllByGameId(gameId)
        }

        verify(exactly = 1) {
            gameSleeveRepository.saveAll(sleeves)
        }

        verify(exactly = 1) {
            gameRepository.save(game)
        }
    }

    @Test
    fun `should set sleevesSyncedAt only during persistence operation`() {
        assertThat(game.sleevesSyncedAt)
            .isNull()

        every {
            gameSleeveRepository.deleteAllByGameId(gameId)
        } just Runs

        every {
            gameSleeveRepository.saveAll(emptyList())
        } returns emptyList()

        every {
            gameRepository.save(game)
        } returns game

        service.replaceSleeves(
            gameId = gameId,
            game = game,
            sleeves = emptyList()
        )

        assertThat(game.sleevesSyncedAt)
            .isNotNull
    }
}