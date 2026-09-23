package com.backend.unit.services

import com.backend.exceptions.GameNotFoundException
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.exceptions.InvalidSortException
import com.backend.models.entities.Game
import com.backend.models.entities.UploadedFile
import com.backend.models.enums.FileOwnerType
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import com.backend.repositories.UploadedFileRepository
import com.backend.services.AdminGameService
import com.backend.services.GameService
import com.backend.services.UploadedFileService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminGameServiceTest {

    @MockK
    private lateinit var gameService: GameService

    @MockK
    private lateinit var gameRepository: GameRepository

    @MockK
    private lateinit var gameSleeveRepository: GameSleeveRepository

    @MockK
    private lateinit var uploadedFileRepository: UploadedFileRepository

    @MockK
    private lateinit var uploadedFileService: UploadedFileService

    @InjectMockKs
    private lateinit var adminGameService: AdminGameService

    private val gameId: UUID = UUID.randomUUID()
    private val fileId: UUID = UUID.randomUUID()
    private val bggId: Long = 13L

    private fun buildGame(
        id: UUID = gameId,
        bggId: Long = this.bggId,
        name: String = "Catan",
        rank: Int? = null,
    ) = Game(
        id = id,
        bggId = bggId,
        name = name,
        rank = rank,
        lastSyncedAt = Instant.now(),
    )

    private fun buildUploadedFile(
        id: UUID = UUID.randomUUID(),
        ownerId: UUID = gameId,
    ) = UploadedFile(
        id = id,
        ownerType = FileOwnerType.GAME_RULE,
        ownerId = ownerId,
        fileName = "rules.pdf",
        objectKey = "game_rule/$ownerId/$id.pdf",
        contentType = "application/pdf",
        size = 1024L,
    )

    @Nested
    @DisplayName("forceRefreshGame")
    inner class ForceRefreshGame {

        @Test
        fun `should refresh the game and return its updated data`() {
            val game = buildGame(name = "Refreshed Catan")

            every { gameService.forceRefreshGame(bggId) } returns mockk(relaxed = true)
            every { gameRepository.findByBggId(bggId) } returns Optional.of(game)
            every { gameSleeveRepository.findAllByGameId(gameId) } returns emptyList()

            val result = adminGameService.forceRefreshGame(bggId)

            assertThat(result.bggId).isEqualTo(bggId)
            assertThat(result.name).isEqualTo("Refreshed Catan")

            verify(exactly = 1) { gameService.forceRefreshGame(bggId) }
        }

        @Test
        fun `should throw GameNotFoundOnBggException when the game cannot be found after refresh`() {
            every { gameService.forceRefreshGame(bggId) } returns mockk(relaxed = true)
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()

            assertThatThrownBy {
                adminGameService.forceRefreshGame(bggId)
            }.isInstanceOf(GameNotFoundOnBggException::class.java)
        }

        @Test
        fun `should propagate exception thrown by the underlying game service`() {
            every { gameService.forceRefreshGame(bggId) } throws RuntimeException("BGG unavailable")

            assertThatThrownBy {
                adminGameService.forceRefreshGame(bggId)
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("BGG unavailable")
        }
    }

    @Nested
    @DisplayName("forceRefreshHotGames")
    inner class ForceRefreshHotGames {

        @Test
        fun `should delegate to the game service`() {
            every { gameService.forceRefreshHotGames() } just Runs

            adminGameService.forceRefreshHotGames()

            verify(exactly = 1) { gameService.forceRefreshHotGames() }
        }

        @Test
        fun `should propagate exception thrown by the underlying game service`() {
            every { gameService.forceRefreshHotGames() } throws RuntimeException("Persistence failure")

            assertThatThrownBy {
                adminGameService.forceRefreshHotGames()
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("Persistence failure")
        }
    }

    @Nested
    @DisplayName("listRuleFiles")
    inner class ListRuleFiles {

        @Test
        fun `should return rule files for an existing game`() {
            val file = buildUploadedFile()

            every { gameRepository.existsById(gameId) } returns true
            every {
                uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.GAME_RULE, gameId)
            } returns listOf(file)

            val result = adminGameService.listRuleFiles(gameId)

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo(file.id)
        }

        @Test
        fun `should return an empty list when the game has no rule files`() {
            every { gameRepository.existsById(gameId) } returns true
            every {
                uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.GAME_RULE, gameId)
            } returns emptyList()

            val result = adminGameService.listRuleFiles(gameId)

            assertThat(result).isEmpty()
        }

        @Test
        fun `should throw GameNotFoundException when the game does not exist`() {
            every { gameRepository.existsById(gameId) } returns false

            assertThatThrownBy {
                adminGameService.listRuleFiles(gameId)
            }.isInstanceOf(GameNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteRuleFile")
    inner class DeleteRuleFile {

        @Test
        fun `should delegate deletion to the uploaded file service`() {
            every { uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId) } just Runs

            adminGameService.deleteRuleFile(gameId, fileId)

            verify(exactly = 1) { uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId) }
        }

        @Test
        fun `should propagate exception when the file does not exist`() {
            every {
                uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId)
            } throws RuntimeException("File not found")

            assertThatThrownBy {
                adminGameService.deleteRuleFile(gameId, fileId)
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("File not found")
        }
    }

    @Nested
    @DisplayName("getGame")
    inner class GetGame {

        @Test
        fun `should return the game with its sleeves`() {
            val game = buildGame()

            every { gameRepository.findById(gameId) } returns Optional.of(game)
            every { gameSleeveRepository.findAllByGameId(gameId) } returns emptyList()

            val result = adminGameService.getGame(gameId)

            assertThat(result.id).isEqualTo(gameId)
        }

        @Test
        fun `should throw GameNotFoundException when the game does not exist`() {
            every { gameRepository.findById(gameId) } returns Optional.empty()

            assertThatThrownBy {
                adminGameService.getGame(gameId)
            }.isInstanceOf(GameNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("listGames")
    inner class ListGames {

        @Test
        fun `should return a page of games with their sleeves`() {
            val game = buildGame(name = "Catan")
            val pageResult = PageImpl(listOf(game), PageRequest.of(0, 20), 1)

            every { gameRepository.findAll(any<Specification<Game>>(), any<Pageable>()) } returns pageResult
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) } returns emptyList()

            val result = adminGameService.listGames(0, 20, null, null, null)

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo("Catan")
        }

        @Test
        fun `should clamp negative page and oversized size`() {
            val pageableSlot = slot<Pageable>()
            val pageResult = PageImpl<Game>(emptyList(), PageRequest.of(0, 100), 0)

            every { gameRepository.findAll(any<Specification<Game>>(), capture(pageableSlot)) } returns pageResult
            every { gameSleeveRepository.findAllByGameIdIn(emptyList()) } returns emptyList()

            adminGameService.listGames(-5, 500, null, null, null)

            assertThat(pageableSlot.captured.pageNumber).isEqualTo(0)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
        }

        @Test
        fun `should apply search and isExpansion filters via the specification`() {
            val expansion = buildGame(name = "Catan: Seafarers")
            val pageResult = PageImpl(listOf(expansion), PageRequest.of(0, 20), 1)

            every { gameRepository.findAll(any<Specification<Game>>(), any<Pageable>()) } returns pageResult
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) } returns emptyList()

            val result = adminGameService.listGames(0, 20, "Seafarers", true, null)

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo("Catan: Seafarers")
        }

        @Test
        fun `should sort by the requested field`() {
            val pageableSlot = slot<Pageable>()
            val pageResult = PageImpl<Game>(emptyList(), PageRequest.of(0, 20), 0)

            every { gameRepository.findAll(any<Specification<Game>>(), capture(pageableSlot)) } returns pageResult
            every { gameSleeveRepository.findAllByGameIdIn(emptyList()) } returns emptyList()

            adminGameService.listGames(0, 20, null, null, "bggRank-desc")

            assertThat(pageableSlot.captured.sort.getOrderFor("bggRank")?.direction).isEqualTo(Sort.Direction.DESC)
        }

        @Test
        fun `should throw InvalidSortException when sort field is not allowed`() {
            assertThatThrownBy {
                adminGameService.listGames(0, 20, null, null, "notAllowedField-asc")
            }.isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { gameRepository.findAll(any<Specification<Game>>(), any<Pageable>()) }
        }

        @Test
        fun `should return an empty page when there are no games`() {
            val pageResult = PageImpl<Game>(emptyList(), PageRequest.of(0, 20), 0)

            every { gameRepository.findAll(any<Specification<Game>>(), any<Pageable>()) } returns pageResult
            every { gameSleeveRepository.findAllByGameIdIn(emptyList()) } returns emptyList()

            val result = adminGameService.listGames(0, 20, null, null, null)

            assertThat(result.content).isEmpty()
        }
    }
}