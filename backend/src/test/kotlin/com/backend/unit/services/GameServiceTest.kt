package com.backend.unit.services

import com.backend.clients.BggClient
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.exceptions.InvalidSortException
import com.backend.models.dtos.*
import com.backend.models.entities.BggRankIndex
import com.backend.models.entities.Category
import com.backend.models.entities.ExpansionRef
import com.backend.models.entities.Game
import com.backend.models.entities.GameSleeve
import com.backend.models.entities.Mechanic
import com.backend.repositories.BggRankIndexRepository
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import com.backend.security.CurrentUser
import com.backend.services.GameService
import com.backend.services.GameSleevePersistenceService
import com.backend.services.GameTermService
import com.backend.services.GameTerms
import com.backend.services.HotGamesPersistenceService
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@ExtendWith(MockKExtension::class)
class GameServiceTest {

    @MockK
    private lateinit var bggClient: BggClient

    @MockK
    private lateinit var gameRepository: GameRepository

    @MockK
    private lateinit var collectionItemRepository: CollectionItemRepository

    @MockK
    private lateinit var gameSleeveRepository: GameSleeveRepository

    @MockK
    private lateinit var hotGamesPersistenceService: HotGamesPersistenceService

    @MockK(relaxUnitFun = true)
    private lateinit var gameSleevePersistenceService: GameSleevePersistenceService

    @MockK
    private lateinit var gameTermService: GameTermService

    @MockK
    private lateinit var bggRankIndexRepository: BggRankIndexRepository

    private lateinit var gameService: GameService

    private val userId: UUID = UUID.randomUUID()
    private val gameId: UUID = UUID.randomUUID()
    private val bggId: Long = 13L

    @BeforeEach
    fun setUp() {
        gameService = GameService(
            bggClient,
            gameRepository,
            collectionItemRepository,
            gameSleeveRepository,
            hotGamesPersistenceService,
            gameSleevePersistenceService,
            gameTermService,
            bggRankIndexRepository,
        )

        mockkObject(CurrentUser)
        every { CurrentUser.id() } returns userId
        every { gameTermService.resolve(any()) } returns GameTerms()
        every { collectionItemRepository.findGameIdsInCollection(userId, any()) } returns emptySet()
        every { gameSleeveRepository.findAllByGameIdIn(any()) } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CurrentUser)
    }

    private fun game(
        id: UUID? = gameId,
        bggId: Long = this.bggId,
        name: String = "Catan",
        syncedAt: Instant = Instant.now(),
        sleevesSyncedAt: Instant? = Instant.now(),
        isExpansion: Boolean? = false,
        baseGameBggId: Long? = null,
        yearPublished: Int? = null,
        rank: Int? = null,
        expansionRefs: List<ExpansionRef> = emptyList(),
    ) = Game(
        id = id,
        bggId = bggId,
        name = name,
        lastSyncedAt = syncedAt,
        sleevesSyncedAt = sleevesSyncedAt,
        isExpansion = isExpansion,
        baseGameBggId = baseGameBggId,
        yearPublished = yearPublished,
        rank = rank,
        expansionRefs = expansionRefs,
    )

    private fun staleTime(): Instant = Instant.now().minus(10, ChronoUnit.DAYS)

    private fun thing(
        id: Long = bggId,
        name: String = "Catan",
        type: String = "boardgame",
        links: List<BggThingLinkXml> = emptyList(),
    ) = BggThingItemXml(
        id = id,
        type = type,
        names = listOf(BggThingNameXml(type = "primary", value = name)),
        yearPublished = BggValueXml("1995"),
        minPlayers = BggValueXml("3"),
        maxPlayers = BggValueXml("4"),
        playingTime = BggValueXml("90"),
        links = links,
    )

    private fun stubSaveReturnsArgument() {
        every { gameRepository.save(any<Game>()) } answers { firstArg<Game>().apply { if(id == null) id = gameId } }
    }

    private fun stubSaveGamesAssignsIds() {
        every { hotGamesPersistenceService.saveGames(any()) } answers {
            firstArg<List<Game>>().onEach { if(it.id == null) it.id = UUID.randomUUID() }
        }
    }

    @Nested
    @DisplayName("getOrSyncGame")
    inner class GetOrSyncGame {

        private fun stubDetailLookups(id: UUID = gameId) {
            every { gameSleeveRepository.findAllByGameId(id) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, id) } returns false
        }

        @Test
        fun `should return a fresh game from the database without calling BGG`() {
            val fresh = game()
            every { gameRepository.findByBggId(bggId) } returns Optional.of(fresh)
            every { gameSleeveRepository.findAllByGameId(gameId) } returns listOf(GameSleeve(id = UUID.randomUUID(), game = fresh, name = "Standard", height = 91.0, width = 59.0, quantity = 110))
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns true

            val result = gameService.getOrSyncGame(bggId)

            assertThat(result.name).isEqualTo("Catan")
            assertThat(result.inCollection).isTrue
            assertThat(result.sleeves.map { it.name }).containsExactly("Standard")
            verify(exactly = 0) { bggClient.getGameDetails(any()) }
            verify(exactly = 0) { bggClient.getCardSetsByGame(any()) }
        }

        @Test
        fun `should sync the sleeves once when they were never synced`() {
            val existing = game(sleevesSyncedAt = null)
            val sleeves = slot<List<GameSleeve>>()
            every { gameRepository.findByBggId(bggId) } returns Optional.of(existing)
            every { bggClient.getCardSetsByGame(bggId) } returns CardSetsByGameResponse(
                listOf(
                    CardSetJson(
                        listOf(
                            CardTypeJson(name = "Standard", height = "88.0", width = "63.5", quantity = "110", quantityNote = "incl. spares"),
                            CardTypeJson(name = "Broken", height = "invalid", width = null, quantity = "invalid", quantityNote = "   "),
                        )
                    )
                )
            )
            every { gameSleevePersistenceService.replaceSleeves(gameId, existing, capture(sleeves)) } just Runs
            stubDetailLookups()

            gameService.getOrSyncGame(bggId)

            assertThat(sleeves.captured.map { it.name }).containsExactly("Standard", "Broken")
            assertThat(sleeves.captured[0].height).isEqualTo(88.0)
            assertThat(sleeves.captured[0].width).isEqualTo(63.5)
            assertThat(sleeves.captured[0].quantity).isEqualTo(110)
            assertThat(sleeves.captured[0].quantityNote).isEqualTo("incl. spares")
            assertThat(sleeves.captured[1].height).isNull()
            assertThat(sleeves.captured[1].width).isNull()
            assertThat(sleeves.captured[1].quantity).isNull()
            assertThat(sleeves.captured[1].quantityNote).isNull()
        }

        @Test
        fun `should still return the game when the sleeve sync fails`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(game(sleevesSyncedAt = null))
            every { bggClient.getCardSetsByGame(bggId) } throws RuntimeException("BGG Sleeve API Error")
            stubDetailLookups()

            assertThat(gameService.getOrSyncGame(bggId).name).isEqualTo("Catan")
            verify(exactly = 0) { gameSleevePersistenceService.replaceSleeves(any(), any(), any()) }
        }

        @Test
        fun `should re-sync a stale game from BGG`() {
            val stale = game(name = "Old Catan", syncedAt = staleTime())
            every { gameRepository.findByBggId(bggId) } returns Optional.of(stale)
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(listOf(thing(name = "Catan")))
            stubSaveReturnsArgument()
            stubDetailLookups()

            val result = gameService.getOrSyncGame(bggId)

            assertThat(result.name).isEqualTo("Catan")
            assertThat(stale.lastSyncedAt).isAfter(Instant.now().minusSeconds(5))
            verify(exactly = 1) { gameRepository.save(stale) }
            verify(exactly = 0) { bggClient.getCardSetsByGame(any()) }
        }

        @Test
        fun `should create a game that is not in the database yet`() {
            val saved = slot<Game>()
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(listOf(thing(name = "Catan")))
            every { gameRepository.save(capture(saved)) } answers { saved.captured.apply { id = gameId } }
            every { bggClient.getCardSetsByGame(bggId) } returns CardSetsByGameResponse()
            stubDetailLookups()

            val result = gameService.getOrSyncGame(bggId)

            assertThat(result.bggId).isEqualTo(bggId)
            assertThat(saved.captured.name).isEqualTo("Catan")
            assertThat(saved.captured.yearPublished).isEqualTo(1995)
        }

        @Test
        fun `should attach the mechanics and categories resolved for the game`() {
            val details = thing(
                links = listOf(
                    BggThingLinkXml(type = "boardgamemechanic", id = "2041", value = "Open Drafting"),
                    BggThingLinkXml(type = "boardgamecategory", id = "1089", value = "Animals"),
                    BggThingLinkXml(type = "boardgamemechanic", id = "9999", value = "Not resolved"),
                )
            )
            val mechanic = Mechanic(id = UUID.randomUUID(), bggId = 2041L, name = "Open Drafting", nameIt = "Draft aperto")
            val category = Category(id = UUID.randomUUID(), bggId = 1089L, name = "Animals")
            every { gameRepository.findByBggId(bggId) } returns Optional.of(game(syncedAt = staleTime()))
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(listOf(details))
            every { gameTermService.resolve(listOf(details)) } returns GameTerms(mapOf(2041L to mechanic), mapOf(1089L to category))
            stubSaveReturnsArgument()
            stubDetailLookups()

            val result = gameService.getOrSyncGame(bggId)

            assertThat(result.mechanics.map { it.name }).containsExactly("Draft aperto")
            assertThat(result.categories.map { it.name }).containsExactly("Animals")
        }

        @Test
        fun `should reload the game when it was inserted concurrently`() {
            val concurrent = game()
            every { gameRepository.findByBggId(bggId) } returnsMany listOf(Optional.empty(), Optional.of(concurrent))
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(listOf(thing()))
            every { gameRepository.save(any<Game>()) } throws DataIntegrityViolationException("duplicate key value violates unique constraint")
            stubDetailLookups()

            assertThat(gameService.getOrSyncGame(bggId).id).isEqualTo(gameId)
        }

        @Test
        fun `should throw GameNotFoundOnBggException when BGG does not know the game`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(emptyList())

            assertThatThrownBy { gameService.getOrSyncGame(bggId) }
                .isInstanceOf(GameNotFoundOnBggException::class.java)

            verify(exactly = 0) { gameRepository.save(any<Game>()) }
        }

        @Test
        fun `should resolve the base game of an expansion`() {
            val baseId = UUID.randomUUID()
            every { gameRepository.findByBggId(bggId) } returns Optional.of(game(name = "Seafarers", isExpansion = true, baseGameBggId = 100L))
            every { gameRepository.findByBggId(100L) } returns Optional.of(game(id = baseId, bggId = 100L, name = "Catan"))
            stubDetailLookups()
            stubDetailLookups(baseId)

            val result = gameService.getOrSyncGame(bggId)

            assertThat(result.baseGame?.name).isEqualTo("Catan")
            assertThat(result.baseGame?.baseGame).isNull()
        }

        @Test
        fun `should not resolve the base game when asked not to`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(game(isExpansion = true, baseGameBggId = 100L))
            stubDetailLookups()

            assertThat(gameService.getOrSyncGame(bggId, resolveBaseGame = false).baseGame).isNull()
            verify(exactly = 0) { gameRepository.findByBggId(100L) }
        }

        @Test
        fun `should not resolve a base game for a game that is not an expansion`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(game(isExpansion = false, baseGameBggId = 100L))
            stubDetailLookups()

            assertThat(gameService.getOrSyncGame(bggId).baseGame).isNull()
            verify(exactly = 0) { gameRepository.findByBggId(100L) }
        }

        @Test
        fun `should return the expansion without base game when the base game cannot be resolved`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(game(isExpansion = true, baseGameBggId = 100L))
            every { gameRepository.findByBggId(100L) } returns Optional.empty()
            every { bggClient.getGameDetails(100L) } throws GameNotFoundOnBggException(100L)
            stubDetailLookups()

            val result = gameService.getOrSyncGame(bggId)

            assertThat(result.name).isEqualTo("Catan")
            assertThat(result.baseGame).isNull()
        }

        @Test
        fun `should rethrow unexpected errors`() {
            every { gameRepository.findByBggId(bggId) } throws RuntimeException("DB Outage")

            assertThatThrownBy { gameService.getOrSyncGame(bggId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB Outage")
        }
    }

    @Nested
    @DisplayName("getHotGames")
    inner class GetHotGames {

        private val pageableSlot = slot<Pageable>()

        @Test
        fun `should map collection status and sleeves for the page`() {
            val hot = game(rank = 1)
            every { gameRepository.findAllByRankIsNotNullOrderByRankAsc(capture(pageableSlot)) } answers { PageImpl(listOf(hot), firstArg(), 1) }
            every { collectionItemRepository.findGameIdsInCollection(userId, listOf(gameId)) } returns setOf(gameId)
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) } returns listOf(GameSleeve(id = UUID.randomUUID(), game = hot, name = "Sleeve 1"))

            val result = gameService.getHotGames(0, 20)

            assertThat(result.content.single().inCollection).isTrue
            assertThat(result.content.single().sleeves.map { it.name }).containsExactly("Sleeve 1")
        }

        @Test
        fun `should clamp page and size`() {
            every { gameRepository.findAllByRankIsNotNullOrderByRankAsc(capture(pageableSlot)) } answers { PageImpl(emptyList(), firstArg(), 0) }

            gameService.getHotGames(-10, 500)

            assertThat(pageableSlot.captured.pageNumber).isEqualTo(0)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
        }

        @Test
        fun `should not query collection or sleeves for an empty page`() {
            every { gameRepository.findAllByRankIsNotNullOrderByRankAsc(any()) } answers { PageImpl(emptyList(), firstArg(), 0) }

            assertThat(gameService.getHotGames(0, 20).content).isEmpty()
            verify(exactly = 0) { collectionItemRepository.findGameIdsInCollection(any(), any()) }
            verify(exactly = 0) { gameSleeveRepository.findAllByGameIdIn(any()) }
        }

        @Test
        fun `should rethrow query errors`() {
            every { gameRepository.findAllByRankIsNotNullOrderByRankAsc(any()) } throws RuntimeException("Query error")

            assertThatThrownBy { gameService.getHotGames(0, 10) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Query error")
        }
    }

    @Nested
    @DisplayName("hot games refresh")
    inner class HotGamesRefresh {

        private val savedHotGames = slot<List<Game>>()

        @BeforeEach
        fun stubSave() {
            every { hotGamesPersistenceService.saveHotGames(capture(savedHotGames)) } just Runs
        }

        private fun hotItem(id: Long, rank: Int, name: String = "Hot $id", year: String? = null) =
            BggHotItemXml(id = id, rank = rank, name = BggValueXml(name), yearPublished = year?.let { BggValueXml(it) })

        @Test
        fun `should do nothing when BGG returns no hot games`() {
            every { bggClient.getHotGames() } returns BggHotResponseXml(emptyList())

            gameService.forceRefreshHotGames()

            verify(exactly = 0) { bggClient.getGameDetailsBatch(any()) }
            verify(exactly = 0) { hotGamesPersistenceService.saveHotGames(any()) }
        }

        @Test
        fun `should update existing games in place with details and rank`() {
            val existing = game(bggId = 10L, name = "Old Name")
            every { bggClient.getHotGames() } returns BggHotResponseXml(listOf(hotItem(10L, 1)))
            every { bggClient.getGameDetailsBatch(listOf(10L)) } returns BggThingResponseXml(listOf(thing(id = 10L, name = "Enriched")))
            every { gameRepository.findAllByBggIdIn(listOf(10L)) } returns listOf(existing)

            gameService.forceRefreshHotGames()

            assertThat(savedHotGames.captured.single()).isSameAs(existing)
            assertThat(existing.name).isEqualTo("Enriched")
            assertThat(existing.rank).isEqualTo(1)
            verify(exactly = 0) { bggClient.getCardSetsByGame(any()) }
        }

        @Test
        fun `should resolve the terms of all hot games in one call`() {
            val details = listOf(thing(id = 10L), thing(id = 20L))
            every { bggClient.getHotGames() } returns BggHotResponseXml(listOf(hotItem(10L, 1), hotItem(20L, 2)))
            every { bggClient.getGameDetailsBatch(listOf(10L, 20L)) } returns BggThingResponseXml(details)
            every { gameRepository.findAllByBggIdIn(listOf(10L, 20L)) } returns emptyList()

            gameService.forceRefreshHotGames()

            verify(exactly = 1) { gameTermService.resolve(match { it.map { item -> item.id }.toSet() == setOf(10L, 20L) }) }
        }

        @Test
        fun `should create new games from the details`() {
            every { bggClient.getHotGames() } returns BggHotResponseXml(listOf(hotItem(10L, 1)))
            every { bggClient.getGameDetailsBatch(listOf(10L)) } returns BggThingResponseXml(listOf(thing(id = 10L, name = "New Hot")))
            every { gameRepository.findAllByBggIdIn(listOf(10L)) } returns emptyList()

            gameService.forceRefreshHotGames()

            val saved = savedHotGames.captured.single()
            assertThat(saved.bggId).isEqualTo(10L)
            assertThat(saved.name).isEqualTo("New Hot")
            assertThat(saved.rank).isEqualTo(1)
        }

        @Test
        fun `should create a lightweight game marked as stale when details are not available`() {
            every { bggClient.getHotGames() } returns BggHotResponseXml(listOf(hotItem(10L, 1, "Light", "2023")))
            every { bggClient.getGameDetailsBatch(listOf(10L)) } throws RuntimeException("BGG Batch Timeout")
            every { gameRepository.findAllByBggIdIn(listOf(10L)) } returns emptyList()

            gameService.forceRefreshHotGames()

            val saved = savedHotGames.captured.single()
            assertThat(saved.name).isEqualTo("Light")
            assertThat(saved.yearPublished).isEqualTo(2023)
            assertThat(saved.rank).isEqualTo(1)
            assertThat(saved.lastSyncedAt).isEqualTo(Instant.EPOCH)
            assertThat(saved.isExpansion).isFalse()
        }

        @Test
        fun `should name a lightweight game without name as unknown`() {
            every { bggClient.getHotGames() } returns BggHotResponseXml(listOf(BggHotItemXml(id = 10L, rank = 1)))
            every { bggClient.getGameDetailsBatch(listOf(10L)) } returns BggThingResponseXml(emptyList())
            every { gameRepository.findAllByBggIdIn(listOf(10L)) } returns emptyList()

            gameService.forceRefreshHotGames()

            assertThat(savedHotGames.captured.single().name).isEqualTo("Sconosciuto")
        }

        @Test
        fun `should keep an existing game and only update its rank when details are not available`() {
            val existing = game(bggId = 10L, name = "Existing Game", rank = 99)
            every { bggClient.getHotGames() } returns BggHotResponseXml(listOf(hotItem(10L, 5, "Lightweight Name")))
            every { bggClient.getGameDetailsBatch(listOf(10L)) } throws RuntimeException("Batch failed")
            every { gameRepository.findAllByBggIdIn(listOf(10L)) } returns listOf(existing)

            gameService.forceRefreshHotGames()

            assertThat(savedHotGames.captured.single()).isSameAs(existing)
            assertThat(existing.name).isEqualTo("Existing Game")
            assertThat(existing.rank).isEqualTo(5)
        }

        @Test
        fun `should keep the BGG order and ranks`() {
            every { bggClient.getHotGames() } returns BggHotResponseXml(listOf(hotItem(10L, 1), hotItem(20L, 2), hotItem(30L, 3)))
            every { bggClient.getGameDetailsBatch(listOf(10L, 20L, 30L)) } returns BggThingResponseXml(emptyList())
            every { gameRepository.findAllByBggIdIn(listOf(10L, 20L, 30L)) } returns emptyList()

            gameService.forceRefreshHotGames()

            assertThat(savedHotGames.captured.map { it.bggId to it.rank }).containsExactly(10L to 1, 20L to 2, 30L to 3)
        }

        @Test
        fun `should propagate errors when forced`() {
            every { bggClient.getHotGames() } throws RuntimeException("Network error")

            assertThatThrownBy { gameService.forceRefreshHotGames() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Network error")
        }

        @Test
        fun `should swallow BGG errors when refreshing on schedule`() {
            every { bggClient.getHotGames() } throws RuntimeException("Network error")

            assertThatCode { gameService.refreshHotGames() }.doesNotThrowAnyException()
        }

        @Test
        fun `should swallow persistence errors when refreshing on schedule`() {
            every { bggClient.getHotGames() } returns BggHotResponseXml(listOf(hotItem(10L, 1)))
            every { bggClient.getGameDetailsBatch(listOf(10L)) } returns BggThingResponseXml(emptyList())
            every { gameRepository.findAllByBggIdIn(listOf(10L)) } returns emptyList()
            every { hotGamesPersistenceService.saveHotGames(any()) } throws RuntimeException("DB failure")

            assertThatCode { gameService.refreshHotGames() }.doesNotThrowAnyException()
        }
    }

    @Nested
    @DisplayName("search routing")
    inner class SearchRoutingTests {

        @BeforeEach
        fun stubBoth() {
            every { bggClient.searchGames(any()) } returns BggSearchResponseXml(emptyList())
            every { gameRepository.searchCatalog(any(), any(), any(), any(), any()) } answers { PageImpl(emptyList(), arg(4), 0) }
        }

        @Test
        fun `should search BGG live when only a query is given`() {
            gameService.search("  catan  ", null, null, null, null, 0, 20)

            verify(exactly = 1) { bggClient.searchGames("catan") }
            verify(exactly = 0) { gameRepository.searchCatalog(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should browse the catalog when the query is blank`() {
            gameService.search("   ", null, null, null, null, 0, 20)

            verify(exactly = 0) { bggClient.searchGames(any()) }
            verify(exactly = 1) { gameRepository.searchCatalog("%", null, null, null, any()) }
        }

        @Test
        fun `should search the catalog when a query comes with a filter`() {
            val mechanicId = UUID.randomUUID()

            gameService.search("catan", mechanicId, null, null, null, 0, 20)

            verify(exactly = 0) { bggClient.searchGames(any()) }
            verify(exactly = 1) { gameRepository.searchCatalog("%catan%", null, mechanicId, null, any()) }
        }

        @Test
        fun `should search the catalog when a query comes with a sort`() {
            gameService.search("catan", null, null, null, "name-desc", 0, 20)

            verify(exactly = 0) { bggClient.searchGames(any()) }
        }
    }

    @Nested
    @DisplayName("search on BGG")
    inner class SearchOnBggTests {

        private fun searchResults(vararg items: Pair<Long, String>) =
            BggSearchResponseXml(items.map { (id, name) -> BggSearchItemXml(id = id, name = BggNameXml(name)) })

        @Test
        fun `should return an empty page beyond the results without touching the database`() {
            every { bggClient.searchGames("catan") } returns searchResults(1L to "Game 1")

            val result = gameService.search("catan", null, null, null, null, 5, 10)

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(1)
            verify(exactly = 0) { gameRepository.findAllByBggIdIn(any()) }
        }

        @Test
        fun `should only enrich the games of the requested page`() {
            every { bggClient.searchGames("catan") } returns searchResults(1L to "A", 2L to "B", 3L to "C")
            every { gameRepository.findAllByBggIdIn(listOf(3L)) } returns listOf(game(bggId = 3L, name = "C"))

            val result = gameService.search("catan", null, null, null, null, 1, 2)

            assertThat(result.content.map { it.bggId }).containsExactly(3L)
            assertThat(result.totalElements).isEqualTo(3)
        }

        @Test
        fun `should use fresh cached games without fetching BGG details`() {
            every { bggClient.searchGames("cached") } returns searchResults(1L to "Cached Game")
            every { gameRepository.findAllByBggIdIn(listOf(1L)) } returns listOf(game(bggId = 1L, name = "Cached Game"))

            assertThat(gameService.search("cached", null, null, null, null, 0, 10).content.single().name).isEqualTo("Cached Game")
            verify(exactly = 0) { bggClient.getGameDetailsBatch(any()) }
            verify(exactly = 0) { hotGamesPersistenceService.saveGames(any()) }
        }

        @Test
        fun `should enrich only stale and missing games and keep the BGG order`() {
            val fresh = game(id = UUID.randomUUID(), bggId = 1L, name = "Fresh Game")
            val stale = game(id = UUID.randomUUID(), bggId = 2L, name = "Old Stale Game", syncedAt = staleTime())
            every { bggClient.searchGames("mixed") } returns searchResults(1L to "Fresh Game", 2L to "Stale Game", 3L to "New Game")
            every { gameRepository.findAllByBggIdIn(listOf(1L, 2L, 3L)) } returns listOf(stale, fresh)
            every { bggClient.getGameDetailsBatch(listOf(2L, 3L)) } returns BggThingResponseXml(listOf(thing(id = 2L, name = "Stale Game Updated"), thing(id = 3L, name = "New Game")))
            stubSaveGamesAssignsIds()
            every { collectionItemRepository.findGameIdsInCollection(userId, any()) } returns setOf(fresh.id!!)

            val result = gameService.search("mixed", null, null, null, null, 0, 10)

            assertThat(result.content.map { it.name }).containsExactly("Fresh Game", "Stale Game Updated", "New Game")
            assertThat(result.content.map { it.inCollection }).containsExactly(true, false, false)
            verify(exactly = 1) { bggClient.getGameDetailsBatch(listOf(2L, 3L)) }
        }

        @Test
        fun `should fall back to the search result when details are not available`() {
            every { bggClient.searchGames("lightweight") } returns BggSearchResponseXml(listOf(BggSearchItemXml(id = 1L, name = BggNameXml("Lightweight Game"), yearPublished = BggValueXml("2021"))))
            every { gameRepository.findAllByBggIdIn(listOf(1L)) } returns emptyList()
            every { bggClient.getGameDetailsBatch(listOf(1L)) } throws RuntimeException("Batch enrichment failed")

            val result = gameService.search("lightweight", null, null, null, null, 0, 10)

            assertThat(result.content.single().bggId).isEqualTo(1L)
            assertThat(result.content.single().name).isEqualTo("Lightweight Game")
            verify(exactly = 0) { hotGamesPersistenceService.saveGames(any()) }
        }

        @Test
        fun `should reload games inserted concurrently while enriching`() {
            val concurrent = game(id = UUID.randomUUID(), bggId = 1L, name = "Concurrent")
            every { bggClient.searchGames("race") } returns searchResults(1L to "Concurrent")
            every { gameRepository.findAllByBggIdIn(listOf(1L)) } returnsMany listOf(emptyList(), listOf(concurrent))
            every { bggClient.getGameDetailsBatch(listOf(1L)) } returns BggThingResponseXml(listOf(thing(id = 1L, name = "Concurrent")))
            every { hotGamesPersistenceService.saveGames(any()) } throws DataIntegrityViolationException("duplicate key")

            val result = gameService.search("race", null, null, null, null, 0, 10)

            assertThat(result.content.single().id).isEqualTo(concurrent.id)
        }

        @Test
        fun `should rethrow other persistence errors`() {
            every { bggClient.searchGames("save-error") } returns searchResults(1L to "Game")
            every { gameRepository.findAllByBggIdIn(listOf(1L)) } returns emptyList()
            every { bggClient.getGameDetailsBatch(listOf(1L)) } returns BggThingResponseXml(listOf(thing(id = 1L)))
            every { hotGamesPersistenceService.saveGames(any()) } throws RuntimeException("DB failure")

            assertThatThrownBy { gameService.search("save-error", null, null, null, null, 0, 10) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB failure")
        }

        @Test
        fun `should rethrow BGG search errors`() {
            every { bggClient.searchGames("error") } throws RuntimeException("Search API Down")

            assertThatThrownBy { gameService.search("error", null, null, null, null, 0, 10) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Search API Down")
        }
    }

    @Nested
    @DisplayName("search in the catalog")
    inner class SearchCatalogTests {

        private val pageableSlot = slot<Pageable>()

        private fun stubCatalog(games: List<Game> = emptyList()) {
            every { gameRepository.searchCatalog(any(), any(), any(), any(), capture(pageableSlot)) } answers { PageImpl(games, arg(4), games.size.toLong()) }
        }

        @Test
        fun `should pass every filter to the repository`() {
            val mechanicId = UUID.randomUUID()
            val categoryId = UUID.randomUUID()
            stubCatalog()

            gameService.search(null, mechanicId, categoryId, true, null, 0, 20)

            verify(exactly = 1) { gameRepository.searchCatalog("%", true, mechanicId, categoryId, any()) }
        }

        @Test
        fun `should lower case and escape the name pattern`() {
            stubCatalog()

            gameService.search("100% Pure_Fun", null, null, false, null, 0, 20)

            verify(exactly = 1) { gameRepository.searchCatalog("%100\\% pure\\_fun%", false, null, null, any()) }
        }

        @Test
        fun `should sort by name ascending by default`() {
            stubCatalog()

            gameService.search(null, null, null, true, null, 0, 20)

            val order = pageableSlot.captured.sort.getOrderFor("name")
            assertThat(order?.direction).isEqualTo(Sort.Direction.ASC)
        }

        @Test
        fun `should put games without rating last when sorting by rating`() {
            stubCatalog()

            gameService.search(null, null, null, null, "avgRating-desc", 0, 20)

            val order = pageableSlot.captured.sort.getOrderFor("avgRating")
            assertThat(order?.direction).isEqualTo(Sort.Direction.DESC)
            assertThat(order?.nullHandling).isEqualTo(Sort.NullHandling.NULLS_LAST)
        }

        @Test
        fun `should throw InvalidSortException without querying for an invalid sort`() {
            assertThatThrownBy { gameService.search(null, null, null, null, "price-asc", 0, 20) }
                .isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { gameRepository.searchCatalog(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should mark the games in the collection of the user`() {
            val owned = game(id = UUID.randomUUID(), bggId = 1L, name = "Owned")
            val other = game(id = UUID.randomUUID(), bggId = 2L, name = "Other")
            stubCatalog(listOf(owned, other))
            every { collectionItemRepository.findGameIdsInCollection(userId, listOf(owned.id!!, other.id!!)) } returns setOf(owned.id!!)

            val result = gameService.search(null, null, null, true, null, 0, 20)

            assertThat(result.content.map { it.inCollection }).containsExactly(true, false)
        }
    }

    @Nested
    @DisplayName("getExpansions")
    inner class GetExpansions {

        private fun baseGame(vararg refs: Long) =
            game(name = "Base Game", expansionRefs = refs.map { ExpansionRef(it, "Exp $it") })

        @Test
        fun `should throw GameNotFoundOnBggException when the game is not in the database`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()

            assertThatThrownBy { gameService.getExpansions(bggId, 0, 10) }
                .isInstanceOf(GameNotFoundOnBggException::class.java)
        }

        @Test
        fun `should sort expansions by year and then by name with unknown years last`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(baseGame(201L, 202L, 203L, 204L))
            every { gameRepository.findAllByBggIdIn(listOf(201L, 202L, 203L, 204L)) } returns listOf(
                game(id = UUID.randomUUID(), bggId = 201L, name = "Zeta", yearPublished = 2020),
                game(id = UUID.randomUUID(), bggId = 202L, name = "Alpha", yearPublished = 2020),
                game(id = UUID.randomUUID(), bggId = 203L, name = "Unknown year", yearPublished = null),
                game(id = UUID.randomUUID(), bggId = 204L, name = "Oldest", yearPublished = 2018),
            )

            val result = gameService.getExpansions(bggId, 0, 10)

            assertThat(result.content.map { it.name }).containsExactly("Oldest", "Alpha", "Zeta", "Unknown year")
        }

        @Test
        fun `should ignore duplicated expansion references`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(baseGame(201L, 201L))
            every { gameRepository.findAllByBggIdIn(listOf(201L)) } returns listOf(game(id = UUID.randomUUID(), bggId = 201L, name = "Exp"))

            assertThat(gameService.getExpansions(bggId, 0, 10).content).hasSize(1)
        }

        @Test
        fun `should skip expansions that cannot be loaded from BGG`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(baseGame(201L, 202L))
            every { gameRepository.findAllByBggIdIn(listOf(201L, 202L)) } returns listOf(game(id = UUID.randomUUID(), bggId = 201L, name = "Cached"))
            every { bggClient.getGameDetailsBatch(listOf(202L)) } throws RuntimeException("BGG unreachable")

            val result = gameService.getExpansions(bggId, 0, 10)

            assertThat(result.content.map { it.bggId }).containsExactly(201L)
            assertThat(result.totalElements).isEqualTo(1)
        }

        @Test
        fun `should paginate the expansions`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(baseGame(201L, 202L, 203L))
            every { gameRepository.findAllByBggIdIn(listOf(201L, 202L, 203L)) } returns listOf(
                game(id = UUID.randomUUID(), bggId = 201L, name = "A", yearPublished = 2001),
                game(id = UUID.randomUUID(), bggId = 202L, name = "B", yearPublished = 2002),
                game(id = UUID.randomUUID(), bggId = 203L, name = "C", yearPublished = 2003),
            )

            val second = gameService.getExpansions(bggId, 1, 2)
            val beyond = gameService.getExpansions(bggId, 5, 2)

            assertThat(second.content.map { it.name }).containsExactly("C")
            assertThat(second.totalElements).isEqualTo(3)
            assertThat(beyond.content).isEmpty()
        }

        @Test
        fun `should cap the page size at fifty`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.of(baseGame())

            val result = gameService.getExpansions(bggId, -10, 500)

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0)
            assertThat(result.size).isEqualTo(50)
        }

        @Test
        fun `should rethrow unexpected errors`() {
            every { gameRepository.findByBggId(bggId) } throws RuntimeException("Unexpected error")

            assertThatThrownBy { gameService.getExpansions(bggId, 0, 10) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Unexpected error")
        }
    }

    @Nested
    @DisplayName("getOverallGames")
    inner class GetOverallGames {

        private val pageableSlot = slot<Pageable>()

        private fun stubRanks(vararg bggIds: Long, total: Long = bggIds.size.toLong()) {
            every { bggRankIndexRepository.findAllByOrderByRankAsc(capture(pageableSlot)) } answers {
                PageImpl(bggIds.mapIndexed { index, id -> BggRankIndex(bggId = id, rank = index + 1) }, firstArg(), total)
            }
        }

        @Test
        fun `should return an empty page without lookups when the index is empty`() {
            stubRanks()

            val result = gameService.getOverallGames(0, 20)

            assertThat(result.content).isEmpty()
            verify(exactly = 0) { gameRepository.findAllByBggIdIn(any()) }
        }

        @Test
        fun `should follow the rank order even when the database returns another order`() {
            stubRanks(1L, 2L, 3L)
            every { gameRepository.findAllByBggIdIn(listOf(1L, 2L, 3L)) } returns listOf(
                game(id = UUID.randomUUID(), bggId = 3L, name = "Third"),
                game(id = UUID.randomUUID(), bggId = 1L, name = "First"),
                game(id = UUID.randomUUID(), bggId = 2L, name = "Second"),
            )

            val result = gameService.getOverallGames(0, 20)

            assertThat(result.content.map { it.name }).containsExactly("First", "Second", "Third")
            verify(exactly = 0) { bggClient.getGameDetailsBatch(any()) }
        }

        @Test
        fun `should enrich games that are missing or stale`() {
            stubRanks(1L, 2L)
            every { gameRepository.findAllByBggIdIn(listOf(1L, 2L)) } returns listOf(game(id = UUID.randomUUID(), bggId = 2L, name = "Stale", syncedAt = staleTime()))
            every { bggClient.getGameDetailsBatch(listOf(1L, 2L)) } returns BggThingResponseXml(listOf(thing(id = 1L, name = "Missing"), thing(id = 2L, name = "Refreshed")))
            stubSaveGamesAssignsIds()

            val result = gameService.getOverallGames(0, 20)

            assertThat(result.content.map { it.name }).containsExactly("Missing", "Refreshed")
        }

        @Test
        fun `should skip entries that cannot be enriched but keep the total of the index`() {
            stubRanks(1L, total = 180000L)
            every { gameRepository.findAllByBggIdIn(listOf(1L)) } returns emptyList()
            every { bggClient.getGameDetailsBatch(listOf(1L)) } throws RuntimeException("BGG unreachable")

            val result = gameService.getOverallGames(0, 20)

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(180000L)
        }

        @Test
        fun `should cap the page size at fifty`() {
            stubRanks()

            gameService.getOverallGames(-5, 500)

            assertThat(pageableSlot.captured.pageNumber).isEqualTo(0)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(50)
        }

        @Test
        fun `should rethrow index errors`() {
            every { bggRankIndexRepository.findAllByOrderByRankAsc(any()) } throws RuntimeException("DB outage")

            assertThatThrownBy { gameService.getOverallGames(0, 20) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB outage")
        }
    }

    @Nested
    @DisplayName("forceRefreshGame")
    inner class ForceRefreshGame {

        @Test
        fun `should sync from BGG and refresh the sleeves even for a fresh game`() {
            val fresh = game(name = "Old Name")
            every { gameRepository.findByBggId(bggId) } returns Optional.of(fresh)
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(listOf(thing(name = "Refreshed Name")))
            stubSaveReturnsArgument()
            every { bggClient.getCardSetsByGame(bggId) } returns CardSetsByGameResponse()
            every { gameSleeveRepository.findAllByGameId(gameId) } returns emptyList()

            val result = gameService.forceRefreshGame(bggId)

            assertThat(result.name).isEqualTo("Refreshed Name")
            verify(exactly = 1) { bggClient.getGameDetails(bggId) }
            verify(exactly = 1) { gameSleevePersistenceService.replaceSleeves(gameId, fresh, emptyList()) }
        }

        @Test
        fun `should create the game when it does not exist yet`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(listOf(thing(name = "New Game")))
            stubSaveReturnsArgument()
            every { bggClient.getCardSetsByGame(bggId) } returns CardSetsByGameResponse()
            every { gameSleeveRepository.findAllByGameId(gameId) } returns emptyList()

            val result = gameService.forceRefreshGame(bggId)

            assertThat(result.bggId).isEqualTo(bggId)
            assertThat(result.name).isEqualTo("New Game")
        }

        @Test
        fun `should throw GameNotFoundOnBggException when BGG does not know the game`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(emptyList())

            assertThatThrownBy { gameService.forceRefreshGame(bggId) }
                .isInstanceOf(GameNotFoundOnBggException::class.java)
        }

        @Test
        fun `should rethrow unexpected errors`() {
            every { gameRepository.findByBggId(bggId) } throws RuntimeException("DB outage")

            assertThatThrownBy { gameService.forceRefreshGame(bggId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB outage")
        }
    }
}