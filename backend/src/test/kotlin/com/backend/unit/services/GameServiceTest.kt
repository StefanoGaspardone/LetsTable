package com.backend.unit.services

import com.backend.clients.BggClient
import com.backend.exceptions.GameNotFoundOnBggException
import com.backend.models.dtos.*
import com.backend.models.entities.ExpansionRef
import com.backend.models.entities.Game
import com.backend.models.entities.GameSleeve
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import com.backend.security.CurrentUser
import com.backend.services.GameService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
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

    @InjectMockKs
    private lateinit var gameService: GameService

    private val userId: UUID = UUID.randomUUID()
    private val gameId: UUID = UUID.randomUUID()
    private val bggId: Long = 13L

    @BeforeEach
    fun setUp() {
        mockkObject(CurrentUser)
        every { CurrentUser.id() } returns userId
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CurrentUser)
    }

    private fun createSampleBggThingItem(
        id: Long = bggId,
        primaryName: String = "Catan",
        type: String = "boardgame",
        description: String = "A game<br/>about trading",
        baseGameBggId: Long? = null,
        expansions: List<ExpansionRef> = emptyList()
    ): BggThingItemXml {
        val linksList = mutableListOf<BggThingLinkXml>()
        if (baseGameBggId != null) {
            linksList.add(BggThingLinkXml(type = "boardgameexpansion", id = baseGameBggId.toString(), value = "Base Game", inbound = true))
        }
        expansions.forEach { exp ->
            linksList.add(BggThingLinkXml(type = "boardgameexpansion", id = exp.bggId.toString(), value = exp.name, inbound = false))
        }
        linksList.add(BggThingLinkXml(type = "boardgamedesigner", id = "1", value = "Klaus Teuber"))
        linksList.add(BggThingLinkXml(type = "boardgameartist", id = "2", value = "Artist Name"))
        linksList.add(BggThingLinkXml(type = "boardgamepublisher", id = "3", value = "Kosmos"))

        val pollSummaries = listOf(
            BggThingPollSummaryXml(
                name = "suggested_numplayers",
                results = listOf(
                    BggThingPollSummaryResultXml(name = "bestwith", value = "3–4 players"),
                    BggThingPollSummaryResultXml(name = "recommmendedwith", value = "2–4 players")
                )
            )
        )

        return BggThingItemXml(
            id = id,
            type = type,
            thumbnail = "http://thumb.png",
            image = "http://image.png",
            names = listOf(BggThingNameXml(type = "primary", value = primaryName)),
            description = description,
            yearPublished = BggValueXml("1995"),
            minPlayers = BggValueXml("3"),
            maxPlayers = BggValueXml("4"),
            playingTime = BggValueXml("90"),
            links = linksList,
            pollSummaries = pollSummaries,
            statistics = BggThingStatisticsXml(
                ratings = BggThingRatingsXml(averageWeight = BggValueXml("2.32"))
            )
        )
    }

    @Nested
    @DisplayName("getOrSyncGame")
    inner class GetOrSyncGame {

        @Test
        fun `should return existing fresh game without syncing from BGG`() {
            val freshGame = Game(
                id = gameId,
                bggId = bggId,
                name = "Catan",
                lastSyncedAt = Instant.now()
            )
            val sleeve = GameSleeve(id = UUID.randomUUID(), game = freshGame, name = "Standard", height = 91.0, width = 59.0, quantity = 110)

            every { gameRepository.findByBggId(bggId) } returns Optional.of(freshGame)
            every { gameSleeveRepository.findAllByGameId(gameId) } returns listOf(sleeve)
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) } returns listOf(sleeve)
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns true

            val result = gameService.getOrSyncGame(bggId, resolveBaseGame = true)

            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("Catan")
            assertThat(result.inCollection).isTrue
            assertThat(result.sleeves).hasSize(1)

            verify(exactly = 0) { bggClient.getGameDetails(any()) }
        }

        @Test
        fun `should sync from BGG when game is null or stale and sync sleeves if missing`() {
            val staleGame = Game(
                id = gameId,
                bggId = bggId,
                name = "Old Catan",
                lastSyncedAt = Instant.now().minus(10, ChronoUnit.DAYS)
            )
            val bggDetails = createSampleBggThingItem()
            val expectedSleeve = GameSleeve(
                id = UUID.randomUUID(),
                game = staleGame,
                name = "Card Sleeve",
                height = 90.0,
                width = 60.0,
                quantity = 50
            )

            every { gameRepository.findByBggId(bggId) } returns Optional.of(staleGame)
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(listOf(bggDetails))
            every { gameRepository.save(any()) } answers { firstArg() }

            every { gameSleeveRepository.findAllByGameId(gameId) } returns emptyList() andThen listOf(expectedSleeve)
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) } returns listOf(expectedSleeve)

            every { bggClient.getCardSetsByGame(bggId) } returns CardSetsByGameResponse(
                listOf(CardSetJson(listOf(CardTypeJson(name = "Card Sleeve", height = "90.0", width = "60.0", quantity = "50"))))
            )
            every { gameSleeveRepository.deleteAllByGameId(gameId) } just Runs
            every { gameSleeveRepository.saveAll(any<List<GameSleeve>>()) } answers { firstArg<List<GameSleeve>>() }
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns false

            val result = gameService.getOrSyncGame(bggId, resolveBaseGame = true)

            assertThat(result).isNotNull
            assertThat(result.sleeves).hasSize(1)
            assertThat(result.sleeves[0].name).isEqualTo("Card Sleeve")

            verify(exactly = 1) { bggClient.getGameDetails(bggId) }
            verify(atLeast = 1) { gameSleeveRepository.deleteAllByGameId(gameId) }
            verify(atLeast = 1) { gameSleeveRepository.saveAll(any<List<GameSleeve>>()) }
        }

        @Test
        fun `should resolve base game when game is expansion`() {
            val baseGameId = 100L
            val expansionGame = Game(
                id = gameId,
                bggId = bggId,
                name = "Catan 5-6 Player Extension",
                isExpansion = true,
                baseGameBggId = baseGameId,
                lastSyncedAt = Instant.now()
            )
            val baseGameEntity = Game(
                id = UUID.randomUUID(),
                bggId = baseGameId,
                name = "Catan Base Game",
                isExpansion = false,
                lastSyncedAt = Instant.now()
            )

            every { gameRepository.findByBggId(bggId) } returns Optional.of(expansionGame)
            every { gameRepository.findByBggId(baseGameId) } returns Optional.of(baseGameEntity)
            every { gameSleeveRepository.findAllByGameId(any()) } returns emptyList()
            every { gameSleeveRepository.findAllByGameIdIn(any()) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, any()) } returns false

            val result = gameService.getOrSyncGame(bggId, resolveBaseGame = true)

            assertThat(result.baseGame).isNotNull
            assertThat(result.baseGame?.name).isEqualTo("Catan Base Game")
        }

        @Test
        fun `should handle gracefully when base game resolution throws exception`() {
            val baseGameId = 100L
            val expansionGame = Game(
                id = gameId,
                bggId = bggId,
                name = "Catan Expansion",
                isExpansion = true,
                baseGameBggId = baseGameId,
                lastSyncedAt = Instant.now()
            )

            every { gameRepository.findByBggId(bggId) } returns Optional.of(expansionGame)
            every { gameRepository.findByBggId(baseGameId) } returns Optional.empty()
            every { bggClient.getGameDetails(baseGameId) } throws GameNotFoundOnBggException(baseGameId)
            every { gameSleeveRepository.findAllByGameId(any()) } returns emptyList()
            every { gameSleeveRepository.findAllByGameIdIn(any()) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, any()) } returns false

            val result = gameService.getOrSyncGame(bggId, resolveBaseGame = true)

            assertThat(result).isNotNull
            assertThat(result.baseGame).isNull()
        }

        @Test
        fun `should throw GameNotFoundOnBggException when game does not exist on BGG`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(emptyList())

            assertThatThrownBy { gameService.getOrSyncGame(bggId) }
                .isInstanceOf(GameNotFoundOnBggException::class.java)
        }

        @Test
        fun `should rethrow unexpected error during getOrSyncGame`() {
            every { gameRepository.findByBggId(bggId) } throws RuntimeException("DB Outage")

            assertThatThrownBy { gameService.getOrSyncGame(bggId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB Outage")
        }

        @Test
        fun `should cover all null and boundary branches in applyBggDetails`() {
            val game = Game(id = gameId, bggId = 100L, name = "Original Name", lastSyncedAt = Instant.now().minus(10, ChronoUnit.DAYS))

            val emptyDetails = BggThingItemXml(
                id = 100L,
                type = "boardgame",
                thumbnail = null,
                image = null,
                names = emptyList(),
                description = null,
                yearPublished = BggValueXml("invalid_year"),
                minPlayers = BggValueXml("-5"),
                maxPlayers = BggValueXml("0"),
                playingTime = BggValueXml("abc"),
                links = emptyList(),
                pollSummaries = emptyList(),
                statistics = BggThingStatisticsXml(
                    ratings = BggThingRatingsXml(averageWeight = BggValueXml("-1.0"))
                )
            )

            every { gameRepository.findByBggId(100L) } returns Optional.of(game)
            every { bggClient.getGameDetails(100L) } returns BggThingResponseXml(listOf(emptyDetails))
            every { gameRepository.save(any()) } answers { firstArg() }
            every { gameSleeveRepository.findAllByGameId(any()) } returns emptyList()
            every { gameSleeveRepository.findAllByGameIdIn(any()) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, any()) } returns false
            every { bggClient.getCardSetsByGame(100L) } returns CardSetsByGameResponse(emptyList())
            every { gameSleeveRepository.deleteAllByGameId(gameId) } just Runs
            every { gameSleeveRepository.saveAll(any<List<GameSleeve>>()) } answers { firstArg<List<GameSleeve>>() }

            val result = gameService.getOrSyncGame(100L, resolveBaseGame = false)

            assertThat(result.name).isEqualTo("Original Name")
            assertThat(result.yearPublished).isNull()
            assertThat(result.minPlayers).isNull()
            assertThat(result.maxPlayers).isNull()
            assertThat(result.playingTimeMinutes).isNull()
            assertThat(result.description).isNull()
            assertThat(result.difficulty).isNull()
            assertThat(result.designers).isEmpty()
            assertThat(result.artists).isEmpty()
            assertThat(result.publishers).isEmpty()
            assertThat(result.isExpansion).isFalse()
        }

        @Test
        fun `should cover multi-line description and expansion type branches in applyBggDetails`() {
            val game = Game(id = gameId, bggId = 200L, name = "Expansion Name", lastSyncedAt = Instant.now().minus(10, ChronoUnit.DAYS))

            val expansionDetails = BggThingItemXml(
                id = 200L,
                type = "boardgameexpansion",
                names = listOf(BggThingNameXml(type = "primary", value = "Expansion Name")),
                description = "Line 1<br/><br/><br/><br/>Line 2",
                yearPublished = BggValueXml("2023"),
                minPlayers = BggValueXml("2"),
                maxPlayers = BggValueXml("4"),
                playingTime = BggValueXml("60"),
                links = listOf(
                    BggThingLinkXml(type = "boardgameexpansion", id = "50", value = "Base Game", inbound = true)
                ),
                statistics = BggThingStatisticsXml(
                    ratings = BggThingRatingsXml(averageWeight = BggValueXml("3.5"))
                )
            )

            every { gameRepository.findByBggId(200L) } returns Optional.of(game)
            every { bggClient.getGameDetails(200L) } returns BggThingResponseXml(listOf(expansionDetails))
            every { gameRepository.save(any()) } answers { firstArg() }
            every { gameSleeveRepository.findAllByGameId(any()) } returns emptyList()
            every { gameSleeveRepository.findAllByGameIdIn(any()) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, any()) } returns false
            every { bggClient.getCardSetsByGame(200L) } returns CardSetsByGameResponse(emptyList())
            every { gameSleeveRepository.deleteAllByGameId(gameId) } just Runs
            every { gameSleeveRepository.saveAll(any<List<GameSleeve>>()) } answers { firstArg<List<GameSleeve>>() }

            val result = gameService.getOrSyncGame(200L, resolveBaseGame = false)

            assertThat(result.isExpansion).isTrue
            assertThat(result.description).contains("Line 1\n\nLine 2")
            assertThat(result.difficulty).isEqualTo(3.5)
        }

        @Test
        fun `should early return in syncSleeves when game id is null`() {
            val gameWithNullId = Game(id = null, bggId = 300L, name = "No ID Game", lastSyncedAt = Instant.now().minus(10, ChronoUnit.DAYS))
            val bggDetails = createSampleBggThingItem(id = 300L)

            every { gameRepository.findByBggId(300L) } returns Optional.of(gameWithNullId)
            every { bggClient.getGameDetails(300L) } returns BggThingResponseXml(listOf(bggDetails))
            every { gameRepository.save(any()) } answers { firstArg() }
            every { collectionItemRepository.existsByUserIdAndGameId(userId, any()) } returns false

            gameService.getOrSyncGame(300L, resolveBaseGame = false)

            verify(exactly = 0) { bggClient.getCardSetsByGame(any()) }
            verify(exactly = 0) { gameSleeveRepository.deleteAllByGameId(any()) }
        }

        @Test
        fun `should cover exception catch block in syncSleeves`() {
            val gameForException = Game(id = gameId, bggId = bggId, name = "Catch Test Game", lastSyncedAt = Instant.now().minus(10, ChronoUnit.DAYS))
            val bggDetails = createSampleBggThingItem(id = bggId)

            every { gameRepository.findByBggId(bggId) } returns Optional.of(gameForException)
            every { bggClient.getGameDetails(bggId) } returns BggThingResponseXml(listOf(bggDetails))
            every { gameRepository.save(any()) } answers { firstArg() }
            every { gameSleeveRepository.findAllByGameId(gameId) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns false

            every { bggClient.getCardSetsByGame(bggId) } throws RuntimeException("BGG Sleeve API Error")

            val result = gameService.getOrSyncGame(bggId, resolveBaseGame = false)
            assertThat(result).isNotNull
        }

        @Test
        fun `should cover sleeve property null branches in syncSleeves`() {
            val gameForNullSleeveFields = Game(id = gameId, bggId = 400L, name = "Blank Sleeve Game", lastSyncedAt = Instant.now().minus(10, ChronoUnit.DAYS))

            every { gameRepository.findByBggId(400L) } returns Optional.of(gameForNullSleeveFields)
            every { bggClient.getGameDetails(400L) } returns BggThingResponseXml(listOf(createSampleBggThingItem(id = 400L)))
            every { gameRepository.save(any()) } answers { firstArg() }
            every { gameSleeveRepository.findAllByGameId(gameId) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns false

            every { bggClient.getCardSetsByGame(400L) } returns CardSetsByGameResponse(
                listOf(
                    CardSetJson(
                        listOf(
                            CardTypeJson(
                                name = "Custom Sleeve",
                                height = "invalid",
                                width = null,
                                quantity = "invalid",
                                quantityNote = "   "
                            )
                        )
                    )
                )
            )
            every { gameSleeveRepository.deleteAllByGameId(gameId) } just Runs
            every { gameSleeveRepository.saveAll(any<List<GameSleeve>>()) } answers { firstArg<List<GameSleeve>>() }

            gameService.getOrSyncGame(400L, resolveBaseGame = false)

            verify(atLeast = 1) {
                gameSleeveRepository.saveAll(match<List<GameSleeve>> { sleeves ->
                    val sleeve = sleeves.first()
                    sleeve.height == null && sleeve.width == null && sleeve.quantity == null && sleeve.quantityNote == null
                })
            }
        }
    }

    @Nested
    @DisplayName("getHotGames")
    inner class GetHotGames {

        @Test
        fun `should clamp page and size and return paged hot games`() {
            val game1 = Game(id = gameId, bggId = 1L, name = "Hot Game 1", rank = 1)
            val pageableSlot = slot<Pageable>()
            val pageImpl = PageImpl(listOf(game1), PageRequest.of(0, 100), 1)

            every { gameRepository.findAllByRankIsNotNullOrderByRankAsc(capture(pageableSlot)) } returns pageImpl
            every { collectionItemRepository.findGameIdsInCollection(userId, listOf(gameId)) } returns setOf(gameId)
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) } returns listOf(
                GameSleeve(id = UUID.randomUUID(), game = game1, name = "Sleeve 1")
            )

            val result = gameService.getHotGames(page = -10, size = 500)

            assertThat(result).isNotNull
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].inCollection).isTrue
            assertThat(result.content[0].sleeves).hasSize(1)
            assertThat((pageableSlot.captured as PageRequest).pageNumber).isEqualTo(0)
            assertThat((pageableSlot.captured as PageRequest).pageSize).isEqualTo(100)
        }

        @Test
        fun `should rethrow exception during getHotGames`() {
            every { gameRepository.findAllByRankIsNotNullOrderByRankAsc(any()) } throws RuntimeException("Query error")

            assertThatThrownBy { gameService.getHotGames(0, 10) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Query error")
        }
    }

    @Nested
    @DisplayName("refreshHotGames")
    inner class RefreshHotGames {

        @Test
        fun `should do nothing when hot games response from BGG is empty`() {
            every { bggClient.getHotGames() } returns BggHotResponseXml(emptyList())

            gameService.refreshHotGames()

            verify(exactly = 0) { gameRepository.clearAllRanks() }
        }

        @Test
        fun `should refresh hot games and fallback gracefully when batch details fail`() {
            val hotItems = listOf(
                BggHotItemXml(id = 10L, rank = 1, name = BggValueXml("Hot Game 1"), yearPublished = BggValueXml("2023"))
            )
            every { bggClient.getHotGames() } returns BggHotResponseXml(hotItems)
            every { gameRepository.clearAllRanks() } just Runs
            every { bggClient.getGameDetailsBatch(listOf(10L)) } throws RuntimeException("BGG Batch Timeout")
            every { gameRepository.findByBggId(10L) } returns Optional.empty()
            every { gameRepository.save(any()) } answers { firstArg() }

            gameService.refreshHotGames()

            verify(exactly = 1) { gameRepository.clearAllRanks() }
            verify(exactly = 1) { gameRepository.save(match { it.bggId == 10L && it.rank == 1 && it.name == "Hot Game 1" }) }
        }

        @Test
        fun `should refresh hot games with full enriched details and sync sleeves`() {
            val hotItems = listOf(
                BggHotItemXml(id = 10L, rank = 1, name = BggValueXml("Hot Game 1"))
            )
            val details = createSampleBggThingItem(id = 10L, primaryName = "Hot Game Enriched")

            every { bggClient.getHotGames() } returns BggHotResponseXml(hotItems)
            every { gameRepository.clearAllRanks() } just Runs
            every { bggClient.getGameDetailsBatch(listOf(10L)) } returns BggThingResponseXml(listOf(details))
            every { gameRepository.findByBggId(10L) } returns Optional.empty()

            val savedGame = Game(id = gameId, bggId = 10L, name = "Hot Game Enriched")
            every { gameRepository.save(any()) } returns savedGame
            every { bggClient.getCardSetsByGame(10L) } returns CardSetsByGameResponse(emptyList())
            every { gameSleeveRepository.deleteAllByGameId(gameId) } just Runs
            every { gameSleeveRepository.saveAll(any<List<GameSleeve>>()) } answers { firstArg<List<GameSleeve>>() }

            gameService.refreshHotGames()

            verify(exactly = 1) { gameRepository.save(any()) }
            verify(exactly = 1) { gameSleeveRepository.deleteAllByGameId(gameId) }
        }

        @Test
        fun `should rethrow exception during refreshHotGames`() {
            every { bggClient.getHotGames() } throws RuntimeException("Network Error")

            assertThatThrownBy { gameService.refreshHotGames() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Network Error")
        }
    }

    @Nested
    @DisplayName("search")
    inner class Search {

        @Test
        fun `should return empty page when requested start offset exceeds search results`() {
            val searchResults = listOf(
                BggSearchItemXml(id = 1L, name = BggNameXml("Game 1"))
            )
            every { bggClient.searchGames("catan") } returns BggSearchResponseXml(searchResults)

            val result = gameService.search("catan", page = 5, size = 10)

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(1)
        }

        @Test
        fun `should fallback to lightweight search result when batch enrichment or database save fails`() {
            val searchResults = listOf(
                BggSearchItemXml(id = 1L, name = BggNameXml("Lightweight Game"), yearPublished = BggValueXml("2021"))
            )
            every { bggClient.searchGames("lightweight") } returns BggSearchResponseXml(searchResults)
            every { bggClient.getGameDetailsBatch(listOf(1L)) } throws RuntimeException("Batch enrichment failed")
            every { collectionItemRepository.findGameIdsInCollection(userId, emptyList()) } returns emptySet()

            val result = gameService.search("lightweight", page = 0, size = 10)

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].bggId).isEqualTo(1L)
            assertThat(result.content[0].name).isEqualTo("Lightweight Game")
        }

        @Test
        fun `should enrich search results with BGG batch details successfully`() {
            val searchResults = listOf(
                BggSearchItemXml(id = 1L, name = BggNameXml("Full Game"))
            )
            val details = createSampleBggThingItem(id = 1L, primaryName = "Enriched Full Game")

            every { bggClient.searchGames("full") } returns BggSearchResponseXml(searchResults)
            every { bggClient.getGameDetailsBatch(listOf(1L)) } returns BggThingResponseXml(listOf(details))
            every { gameRepository.findByBggId(1L) } returns Optional.empty()

            val savedGame = Game(id = gameId, bggId = 1L, name = "Enriched Full Game")
            every { gameRepository.save(any()) } returns savedGame
            every { collectionItemRepository.findGameIdsInCollection(userId, listOf(gameId)) } returns setOf(gameId)
            every { gameSleeveRepository.findAllByGameIdIn(listOf(gameId)) } returns emptyList()

            val result = gameService.search("full", page = 0, size = 10)

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo("Enriched Full Game")
            assertThat(result.content[0].inCollection).isTrue
        }

        @Test
        fun `should rethrow exception during search`() {
            every { bggClient.searchGames("error") } throws RuntimeException("Search API Down")

            assertThatThrownBy { gameService.search("error", 0, 10) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Search API Down")
        }
    }

    @Nested
    @DisplayName("getExpansions")
    inner class GetExpansions {

        @Test
        fun `should throw GameNotFoundOnBggException when game does not exist in db`() {
            every { gameRepository.findByBggId(bggId) } returns Optional.empty()

            assertThatThrownBy { gameService.getExpansions(bggId, 0, 10) }
                .isInstanceOf(GameNotFoundOnBggException::class.java)
        }

        @Test
        fun `should return sorted expansions and skip failed expansion syncs`() {
            val gameWithExpansions = Game(
                id = gameId,
                bggId = bggId,
                name = "Base Game",
                expansionRefs = listOf(ExpansionRef(201L, "Exp B"), ExpansionRef(202L, "Exp A"))
            )

            val expA = Game(id = UUID.randomUUID(), bggId = 202L, name = "Exp A", yearPublished = 2020)
            val expB = Game(id = UUID.randomUUID(), bggId = 201L, name = "Exp B", yearPublished = 2018)

            every { gameRepository.findByBggId(bggId) } returns Optional.of(gameWithExpansions)

            every { gameRepository.findByBggId(202L) } returns Optional.of(expA)
            every { gameRepository.findByBggId(201L) } returns Optional.of(expB)
            every { gameSleeveRepository.findAllByGameId(any()) } returns emptyList()
            every { gameSleeveRepository.findAllByGameIdIn(any()) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, any()) } returns false

            val result = gameService.getExpansions(bggId, page = 0, size = 10)

            assertThat(result.content).hasSize(2)
            assertThat(result.content[0].bggId).isEqualTo(201L)
            assertThat(result.content[1].bggId).isEqualTo(202L)
        }

        @Test
        fun `should return empty page content if start index exceeds total expansions`() {
            val gameWithExpansions = Game(
                id = gameId,
                bggId = bggId,
                name = "Base Game",
                expansionRefs = listOf(ExpansionRef(201L, "Exp B"))
            )
            val expB = Game(id = UUID.randomUUID(), bggId = 201L, name = "Exp B", yearPublished = 2018)

            every { gameRepository.findByBggId(bggId) } returns Optional.of(gameWithExpansions)
            every { gameRepository.findByBggId(201L) } returns Optional.of(expB)
            every { gameSleeveRepository.findAllByGameId(any()) } returns emptyList()
            every { gameSleeveRepository.findAllByGameIdIn(any()) } returns emptyList()
            every { collectionItemRepository.existsByUserIdAndGameId(userId, any()) } returns false

            val result = gameService.getExpansions(bggId, page = 5, size = 10)

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(1)
        }

        @Test
        fun `should rethrow unexpected exception during getExpansions`() {
            every { gameRepository.findByBggId(bggId) } throws RuntimeException("Unexpected error")

            assertThatThrownBy { gameService.getExpansions(bggId, 0, 10) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Unexpected error")
        }
    }
}