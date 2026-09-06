package com.backend.unit.services

import com.backend.models.entities.Game
import com.backend.repositories.GameRepository
import com.backend.services.HotGamesPersistenceService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class HotGamesPersistenceServiceTest {

    private lateinit var gameRepository: GameRepository
    private lateinit var service: HotGamesPersistenceService

    @BeforeEach
    fun setUp() {
        gameRepository = mockk()
        service = HotGamesPersistenceService(gameRepository)
    }

    @Test
    fun `should clear existing ranks and save hot games`() {
        val game1 = Game(
            id = UUID.randomUUID(),
            bggId = 1L,
            name = "Game 1",
            rank = 1,
            lastSyncedAt = Instant.now()
        )

        val game2 = Game(
            id = UUID.randomUUID(),
            bggId = 2L,
            name = "Game 2",
            rank = 2,
            lastSyncedAt = Instant.now()
        )

        every {
            gameRepository.clearAllRanks()
        } just Runs

        every {
            gameRepository.saveAll(listOf(game1, game2))
        } returns listOf(game1, game2)

        service.saveHotGames(
            listOf(game1, game2)
        )

        verifySequence {
            gameRepository.clearAllRanks()
            gameRepository.saveAll(listOf(game1, game2))
        }
    }

    @Test
    fun `should handle empty hot games list`() {
        every {
            gameRepository.clearAllRanks()
        } just Runs

        every {
            gameRepository.saveAll(emptyList())
        } returns emptyList()

        service.saveHotGames(emptyList())

        verify(exactly = 1) {
            gameRepository.clearAllRanks()
        }

        verify(exactly = 1) {
            gameRepository.saveAll(emptyList())
        }
    }

    @Test
    fun `should return saved games from saveGames`() {
        val game = Game(
            id = UUID.randomUUID(),
            bggId = 1L,
            name = "Game",
            lastSyncedAt = Instant.now()
        )

        val savedGames = listOf(game)

        every {
            gameRepository.saveAll(listOf(game))
        } returns savedGames

        val result = service.saveGames(listOf(game))

        assertThat(result)
            .containsExactly(game)

        verify(exactly = 1) {
            gameRepository.saveAll(listOf(game))
        }

        verify(exactly = 0) {
            gameRepository.clearAllRanks()
        }
    }

    @Test
    fun `should handle empty list in saveGames`() {
        every {
            gameRepository.saveAll(emptyList())
        } returns emptyList()

        val result = service.saveGames(emptyList())

        assertThat(result).isEmpty()

        verify(exactly = 1) {
            gameRepository.saveAll(emptyList())
        }
    }

    @Test
    fun `should propagate exception from saveGames`() {
        every {
            gameRepository.saveAll(emptyList())
        } throws RuntimeException("Save games failed")

        assertThatThrownBy {
            service.saveGames(emptyList())
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Save games failed")
    }
}