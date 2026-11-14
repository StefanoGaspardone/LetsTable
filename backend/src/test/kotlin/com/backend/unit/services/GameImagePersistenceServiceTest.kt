package com.backend.unit.services

import com.backend.models.entities.Game
import com.backend.models.entities.GameImage
import com.backend.repositories.GameImageRepository
import com.backend.repositories.GameRepository
import com.backend.services.GameImagePersistenceService
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class GameImagePersistenceServiceTest {

    @MockK
    private lateinit var gameImageRepository: GameImageRepository

    @MockK
    private lateinit var gameRepository: GameRepository

    private lateinit var gameImagePersistenceService: GameImagePersistenceService

    private val gameId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        gameImagePersistenceService = GameImagePersistenceService(gameImageRepository, gameRepository)

        every { gameImageRepository.deleteAllByGameId(any()) } just Runs
        every { gameImageRepository.saveAll(any<Iterable<GameImage>>()) } answers { firstArg<Iterable<GameImage>>().toList() }
        every { gameRepository.save(any<Game>()) } answers { firstArg() }
    }

    private fun game(imagesSyncedAt: Instant? = null) =
        Game(id = gameId, bggId = 13L, name = "Catan", imagesSyncedAt = imagesSyncedAt)

    private fun image(game: Game, bggImageId: Long, position: Int) = GameImage(
        game = game,
        bggImageId = bggImageId,
        thumbnailUrl = "https://cf.geekdo-images.com/$bggImageId-thumb.jpg",
        imageUrl = "https://cf.geekdo-images.com/$bggImageId-large.jpg",
        position = position,
    )

    @Nested
    @DisplayName("replaceImages")
    inner class ReplaceImages {

        @Test
        fun `should delete the old images before saving the new ones`() {
            val detached = game()
            every { gameRepository.findById(gameId) } returns Optional.of(game())

            gameImagePersistenceService.replaceImages(detached, listOf(image(detached, 1L, 0)))

            verifyOrder {
                gameImageRepository.deleteAllByGameId(gameId)
                gameImageRepository.saveAll(any<Iterable<GameImage>>())
            }
        }

        @Test
        fun `should attach the new images to the game loaded in the transaction`() {
            val detached = game()
            val managed = game()
            val saved = slot<Iterable<GameImage>>()
            every { gameRepository.findById(gameId) } returns Optional.of(managed)
            every { gameImageRepository.saveAll(capture(saved)) } answers { saved.captured.toList() }

            gameImagePersistenceService.replaceImages(detached, listOf(image(detached, 1L, 0), image(detached, 2L, 1)))

            assertThat(saved.captured).allMatch { it.game === managed }
        }

        @Test
        fun `should mark the game images as synced`() {
            val managed = game(imagesSyncedAt = Instant.EPOCH)
            every { gameRepository.findById(gameId) } returns Optional.of(managed)
            val before = Instant.now()

            gameImagePersistenceService.replaceImages(game(), emptyList())

            assertThat(managed.imagesSyncedAt).isAfterOrEqualTo(before)
            verify(exactly = 1) { gameRepository.save(managed) }
        }

        @Test
        fun `should mark an empty gallery as synced too`() {
            val managed = game()
            every { gameRepository.findById(gameId) } returns Optional.of(managed)

            val result = gameImagePersistenceService.replaceImages(game(), emptyList())

            assertThat(result).isEmpty()
            assertThat(managed.imagesSyncedAt).isNotNull()
            verify(exactly = 1) { gameImageRepository.deleteAllByGameId(gameId) }
        }

        @Test
        fun `should return the saved images ordered by position`() {
            val detached = game()
            every { gameRepository.findById(gameId) } returns Optional.of(game())

            val result = gameImagePersistenceService.replaceImages(detached, listOf(image(detached, 3L, 2), image(detached, 1L, 0), image(detached, 2L, 1)))

            assertThat(result.map { it.bggImageId }).containsExactly(1L, 2L, 3L)
        }

        @Test
        fun `should fail without saving when the game no longer exists`() {
            val detached = game()
            every { gameRepository.findById(gameId) } returns Optional.empty()

            assertThatThrownBy { gameImagePersistenceService.replaceImages(detached, listOf(image(detached, 1L, 0))) }
                .isInstanceOf(NoSuchElementException::class.java)

            verify(exactly = 0) { gameImageRepository.saveAll(any<Iterable<GameImage>>()) }
            verify(exactly = 0) { gameRepository.save(any<Game>()) }
        }

        @Test
        fun `should propagate repository errors so the caller can reload`() {
            val detached = game()
            every { gameRepository.findById(gameId) } returns Optional.of(game())
            every { gameImageRepository.saveAll(any<Iterable<GameImage>>()) } throws RuntimeException("duplicate key")

            assertThatThrownBy { gameImagePersistenceService.replaceImages(detached, listOf(image(detached, 1L, 0))) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("duplicate key")

            verify(exactly = 0) { gameRepository.save(any<Game>()) }
        }
    }
}