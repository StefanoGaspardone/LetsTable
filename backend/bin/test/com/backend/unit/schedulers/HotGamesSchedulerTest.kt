package com.backend.unit.schedulers

import com.backend.schedulers.HotGamesScheduler
import com.backend.services.GameService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class HotGamesSchedulerTest {

    @Mock
    private lateinit var gameService: GameService

    @InjectMocks
    private lateinit var hotGamesScheduler: HotGamesScheduler

    @Nested
    @DisplayName("refreshHotGames")
    inner class RefreshHotGamesTests {

        @Test
        fun `should call gameService refreshHotGames`() {
            hotGamesScheduler.refreshHotGames()

            verify(gameService).refreshHotGames()
        }

        @Test
        fun `should swallow exception when gameService throws and not propagate it`() {
            whenever(gameService.refreshHotGames()).thenThrow(RuntimeException("BGG unreachable"))

            hotGamesScheduler.refreshHotGames()

            verify(gameService).refreshHotGames()
        }
    }
}