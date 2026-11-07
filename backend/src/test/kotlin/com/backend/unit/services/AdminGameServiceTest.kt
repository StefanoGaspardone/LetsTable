package com.backend.unit.services

import com.backend.exceptions.GameNotFoundException
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.exceptions.InvalidSortException
import com.backend.exceptions.UploadedFileNotFoundException
import com.backend.models.entities.Game
import com.backend.models.entities.UploadedFile
import com.backend.models.enums.FileOwnerType
import com.backend.models.specifications.GameSpecification
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
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

    @AfterEach
    fun tearDown() {
        unmockkObject(GameSpecification)
    }

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

            assertThat(result.id).isEqualTo(gameId)
            assertThat(result.bggId).isEqualTo(bggId)
            assertThat(result.name).isEqualTo("Refreshed Catan")

            verifyOrder {
                gameService.forceRefreshGame(bggId)
                gameRepository.findByBggId(bggId)
                gameSleeveRepository.findAllByGameId(gameId)
            }
        }

        @Test
        fun `should propagate GameNotFoundOnBggException when BGG does not know the game`() {
            every { gameService.forceRefreshGame(bggId) } throws GameNotFoundOnBggException(bggId)

            assertThatThrownBy { adminGameService.forceRefreshGame(bggId) }
                .isInstanceOf(GameNotFoundOnBggException::class.java)

            verify(exactly = 0) { gameRepository.findByBggId(any()) }
        }

        @Test
        fun `should throw GameNotFoundOnBggException when the game cannot be found after refresh`() {
            every { gameService.forceRefreshGame(bggId) } returns mockk(relaxed = true)
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()

            assertThatThrownBy { adminGameService.forceRefreshGame(bggId) }
                .isInstanceOf(GameNotFoundOnBggException::class.java)
        }

        @Test
        fun `should propagate exception thrown by the underlying game service`() {
            every { gameService.forceRefreshGame(bggId) } throws RuntimeException("BGG unavailable")

            assertThatThrownBy { adminGameService.forceRefreshGame(bggId) }
                .isInstanceOf(RuntimeException::class.java)
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

            assertThatThrownBy { adminGameService.forceRefreshHotGames() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Persistence failure")
        }
    }

    @Nested
    @DisplayName("listRuleFiles")
    inner class ListRuleFiles {

        @Test
        fun `should return rule files for an existing game in repository order`() {
            val newer = buildUploadedFile()
            val older = buildUploadedFile()

            every { gameRepository.existsById(gameId) } returns true
            every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.GAME_RULE, gameId) } returns listOf(newer, older)

            val result = adminGameService.listRuleFiles(gameId)

            assertThat(result.map { it.id }).containsExactly(newer.id, older.id)
        }

        @Test
        fun `should return an empty list when the game has no rule files`() {
            every { gameRepository.existsById(gameId) } returns true
            every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.GAME_RULE, gameId) } returns emptyList()

            assertThat(adminGameService.listRuleFiles(gameId)).isEmpty()
        }

        @Test
        fun `should throw GameNotFoundException when the game does not exist`() {
            every { gameRepository.existsById(gameId) } returns false

            assertThatThrownBy { adminGameService.listRuleFiles(gameId) }
                .isInstanceOf(GameNotFoundException::class.java)

            verify(exactly = 0) { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(any(), any()) }
        }
    }

    @Nested
    @DisplayName("deleteRuleFile")
    inner class DeleteRuleFile {

        @Test
        fun `should delegate deletion to the uploaded file service`() {
            every { gameRepository.existsById(gameId) } returns true
            every { uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId) } just Runs

            adminGameService.deleteRuleFile(gameId, fileId)

            verify(exactly = 1) { uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId) }
        }

        @Test
        fun `should throw GameNotFoundException without deleting when the game does not exist`() {
            every { gameRepository.existsById(gameId) } returns false

            assertThatThrownBy { adminGameService.deleteRuleFile(gameId, fileId) }
                .isInstanceOf(GameNotFoundException::class.java)

            verify(exactly = 0) { uploadedFileService.deleteFile(any(), any(), any()) }
        }

        @Test
        fun `should propagate UploadedFileNotFoundException when the file does not exist`() {
            every { gameRepository.existsById(gameId) } returns true
            every { uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId) } throws UploadedFileNotFoundException(fileId)

            assertThatThrownBy { adminGameService.deleteRuleFile(gameId, fileId) }
                .isInstanceOf(UploadedFileNotFoundException::class.java)
        }

        @Test
        fun `should propagate unexpected storage errors`() {
            every { gameRepository.existsById(gameId) } returns true
            every { uploadedFileService.deleteFile(FileOwnerType.GAME_RULE, gameId, fileId) } throws RuntimeException("Storage unavailable")

            assertThatThrownBy { adminGameService.deleteRuleFile(gameId, fileId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Storage unavailable")
        }
    }

    @Nested
    @DisplayName("getGame")
    inner class GetGame {

        @Test
        fun `should return the game with its sleeves`() {
            every { gameRepository.findById(gameId) } returns Optional.of(buildGame())
            every { gameSleeveRepository.findAllByGameId(gameId) } returns emptyList()

            val result = adminGameService.getGame(gameId)

            assertThat(result.id).isEqualTo(gameId)
            verify(exactly = 1) { gameSleeveRepository.findAllByGameId(gameId) }
        }

        @Test
        fun `should throw GameNotFoundException when the game does not exist`() {
            every { gameRepository.findById(gameId) } returns Optional.empty()

            assertThatThrownBy { adminGameService.getGame(gameId) }
                .isInstanceOf(GameNotFoundException::class.java)

            verify(exactly = 0) { gameSleeveRepository.findAllByGameId(any()) }
        }
    }

    @Nested
    @DisplayName("listGames")
    inner class ListGames {

        private val pageableSlot = slot<Pageable>()

        private fun stubFindAll(games: List<Game>) {
            every { gameRepository.findAll(any<Specification<Game>>(), capture(pageableSlot)) } answers {
                PageImpl(games, secondArg(), games.size.toLong())
            }
        }

        @Test
        fun `should return a page of games with their sleeves`() {
            stubFindAll(listOf(buildGame(name = "Catan")))
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) } returns emptyList()

            val result = adminGameService.listGames(0, 20, null, null, null)

            assertThat(result.content.map { it.name }).containsExactly("Catan")
            verify(exactly = 1) { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) }
        }

        @Test
        fun `should load the sleeves of all games in a single query`() {
            val otherId = UUID.randomUUID()
            stubFindAll(listOf(buildGame(), buildGame(id = otherId, bggId = 14L, name = "Carcassonne")))
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId, otherId)) } returns emptyList()

            adminGameService.listGames(0, 20, null, null, null)

            verify(exactly = 1) { gameSleeveRepository.findAllByGameIdIn(listOf(gameId, otherId)) }
        }

        @Test
        fun `should not query sleeves when there are no games`() {
            stubFindAll(emptyList())

            val result = adminGameService.listGames(0, 20, null, null, null)

            assertThat(result.content).isEmpty()
            verify(exactly = 0) { gameSleeveRepository.findAllByGameIdIn(any()) }
        }

        @Test
        fun `should clamp negative page and oversized size`() {
            stubFindAll(emptyList())

            adminGameService.listGames(-5, 500, null, null, null)

            assertThat(pageableSlot.captured.pageNumber).isEqualTo(0)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
        }

        @Test
        fun `should pass search and isExpansion to the specification`() {
            mockkObject(GameSpecification)
            stubFindAll(emptyList())

            adminGameService.listGames(0, 20, "Seafarers", true, null)

            verify(exactly = 1) { GameSpecification.withFilters("Seafarers", true) }
        }

        @Test
        fun `should sort by name ascending by default`() {
            stubFindAll(emptyList())

            adminGameService.listGames(0, 20, null, null, null)

            val nameOrder = pageableSlot.captured.sort.getOrderFor("name")
            assertThat(nameOrder?.direction).isEqualTo(Sort.Direction.ASC)
        }

        @Test
        fun `should sort by the requested field with nulls last and a stable tie breaker`() {
            stubFindAll(emptyList())

            adminGameService.listGames(0, 20, null, null, "bggRank-desc")

            val orders = pageableSlot.captured.sort.toList()
            assertThat(orders.map { it.property }).containsExactly("bggRank", "id")
            assertThat(orders[0].direction).isEqualTo(Sort.Direction.DESC)
            assertThat(orders[0].nullHandling).isEqualTo(Sort.NullHandling.NULLS_LAST)
        }

        @Test
        fun `should throw InvalidSortException when sort field is not allowed`() {
            assertThatThrownBy { adminGameService.listGames(0, 20, null, null, "notAllowedField-asc") }
                .isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { gameRepository.findAll(any<Specification<Game>>(), any<Pageable>()) }
        }
    }
}