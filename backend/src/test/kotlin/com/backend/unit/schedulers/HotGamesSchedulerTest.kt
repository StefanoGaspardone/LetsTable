package com.backend.unit.schedulers

import com.backend.schedulers.HotGamesScheduler
import com.backend.services.GameService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HotGamesSchedulerTest {

    private lateinit var gameService: GameService
    private lateinit var scheduler: HotGamesScheduler

    @BeforeEach
    fun setUp() {
        gameService = mockk(relaxed = true)
        scheduler = HotGamesScheduler(gameService)
    }

    @Test
    fun `should refresh the hot games once`() {
        scheduler.refreshHotGames()

        verify(exactly = 1) { gameService.refreshHotGames() }
    }

    @Test
    fun `should propagate a failure of the refresh`() {
        every { gameService.refreshHotGames() } throws RuntimeException("BGG unreachable")

        assertThatThrownBy { scheduler.refreshHotGames() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("BGG unreachable")
    }
}