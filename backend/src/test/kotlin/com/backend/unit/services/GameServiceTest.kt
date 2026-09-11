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
import com.backend.services.GameSleevePersistenceService
import com.backend.services.HotGamesPersistenceService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
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

    @MockK
    private lateinit var hotGamesPersistenceService: HotGamesPersistenceService

    @MockK
    private lateinit var gameSleevePersistenceService: GameSleevePersistenceService

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
            linksList.add(
                BggThingLinkXml(
                    type = "boardgameexpansion",
                    id = baseGameBggId.toString(),
                    value = "Base Game",
                    inbound = true
                )
            )
        }

        expansions.forEach { exp ->
            linksList.add(
                BggThingLinkXml(
                    type = "boardgameexpansion",
                    id = exp.bggId.toString(),
                    value = exp.name,
                    inbound = false
                )
            )
        }

        linksList.add(
            BggThingLinkXml(
                type = "boardgamedesigner",
                id = "1",
                value = "Klaus Teuber"
            )
        )

        linksList.add(
            BggThingLinkXml(
                type = "boardgameartist",
                id = "2",
                value = "Artist Name"
            )
        )

        linksList.add(
            BggThingLinkXml(
                type = "boardgamepublisher",
                id = "3",
                value = "Kosmos"
            )
        )

        val pollSummaries = listOf(
            BggThingPollSummaryXml(
                name = "suggested_numplayers",
                results = listOf(
                    BggThingPollSummaryResultXml(
                        name = "bestwith",
                        value = "3–4 players"
                    ),
                    BggThingPollSummaryResultXml(
                        name = "recommmendedwith",
                        value = "2–4 players"
                    )
                )
            )
        )

        return BggThingItemXml(
            id = id,
            type = type,
            thumbnail = "http://thumb.png",
            image = "http://image.png",
            names = listOf(
                BggThingNameXml(
                    type = "primary",
                    value = primaryName
                )
            ),
            description = description,
            yearPublished = BggValueXml("1995"),
            minPlayers = BggValueXml("3"),
            maxPlayers = BggValueXml("4"),
            playingTime = BggValueXml("90"),
            links = linksList,
            pollSummaries = pollSummaries,
            statistics = BggThingStatisticsXml(
                ratings = BggThingRatingsXml(
                    averageWeight = BggValueXml("2.32")
                )
            )
        )
    }

    @Nested
    @DisplayName("getOrSyncGame")
    inner class GetOrSyncGame {

        @Test
        fun `should return existing fresh game without syncing from BGG and without syncing sleeves`() {
            val freshGame = Game(
                id = gameId,
                bggId = bggId,
                name = "Catan",
                lastSyncedAt = Instant.now(),
                sleevesSyncedAt = Instant.now()
            )

            val sleeve = GameSleeve(
                id = UUID.randomUUID(),
                game = freshGame,
                name = "Standard",
                height = 91.0,
                width = 59.0,
                quantity = 110
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(freshGame)

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns listOf(sleeve)

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns true

            val result = gameService.getOrSyncGame(
                bggId,
                resolveBaseGame = true
            )

            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("Catan")
            assertThat(result.inCollection).isTrue
            assertThat(result.sleeves).hasSize(1)

            verify(exactly = 0) {
                bggClient.getGameDetails(any())
            }

            verify(exactly = 0) {
                bggClient.getCardSetsByGame(any())
            }

            verify(exactly = 0) {
                gameSleevePersistenceService.replaceSleeves(
                    any(),
                    any(),
                    any()
                )
            }
        }

        @Test
        fun `should sync sleeves when game exists and sleeves were never synced`() {
            val game = Game(
                id = gameId,
                bggId = bggId,
                name = "Catan",
                lastSyncedAt = Instant.now(),
                sleevesSyncedAt = null
            )

            val sleeve = GameSleeve(
                id = UUID.randomUUID(),
                game = game,
                name = "Card Sleeve",
                height = 90.0,
                width = 60.0,
                quantity = 50
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(game)

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList() andThen listOf(sleeve)

            every {
                bggClient.getCardSetsByGame(bggId)
            } returns CardSetsByGameResponse(
                listOf(
                    CardSetJson(
                        listOf(
                            CardTypeJson(
                                name = "Card Sleeve",
                                height = "90.0",
                                width = "60.0",
                                quantity = "50"
                            )
                        )
                    )
                )
            )

            every {
                gameSleevePersistenceService.replaceSleeves(
                    gameId,
                    game,
                    any()
                )
            } just Runs

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            val result = gameService.getOrSyncGame(
                bggId,
                resolveBaseGame = true
            )

            assertThat(result).isNotNull
            assertThat(result.sleeves).hasSize(1)
            assertThat(result.sleeves[0].name).isEqualTo("Card Sleeve")

            verify(exactly = 1) {
                bggClient.getCardSetsByGame(bggId)
            }

            verify(exactly = 1) {
                gameSleevePersistenceService.replaceSleeves(
                    gameId,
                    game,
                    match {
                        it.size == 1 &&
                                it[0].name == "Card Sleeve" &&
                                it[0].height == 90.0 &&
                                it[0].width == 60.0 &&
                                it[0].quantity == 50
                    }
                )
            }
        }

        @Test
        fun `should sync from BGG when game is stale`() {
            val staleGame = Game(
                id = gameId,
                bggId = bggId,
                name = "Old Catan",
                lastSyncedAt = Instant.now().minus(10, ChronoUnit.DAYS),
                sleevesSyncedAt = Instant.now()
            )

            val bggDetails = createSampleBggThingItem()

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(staleGame)

            every {
                bggClient.getGameDetails(bggId)
            } returns BggThingResponseXml(
                listOf(bggDetails)
            )

            every {
                gameRepository.save(any<Game>())
            } answers {
                firstArg()
            }

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            val result = gameService.getOrSyncGame(
                bggId,
                resolveBaseGame = false
            )

            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("Catan")

            verify(exactly = 1) {
                bggClient.getGameDetails(bggId)
            }

            verify(exactly = 0) {
                bggClient.getCardSetsByGame(any())
            }
        }

        @Test
        fun `should sync new game from BGG when game does not exist`() {
            val bggDetails = createSampleBggThingItem()

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.empty()

            every {
                bggClient.getGameDetails(bggId)
            } returns BggThingResponseXml(
                listOf(bggDetails)
            )

            every {
                gameRepository.save(any<Game>())
            } answers {
                val game = firstArg<Game>()
                game.id = gameId
                game
            }

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            val result = gameService.getOrSyncGame(
                bggId,
                resolveBaseGame = false
            )

            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("Catan")
            assertThat(result.bggId).isEqualTo(bggId)

            verify(exactly = 1) {
                bggClient.getGameDetails(bggId)
            }
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
                lastSyncedAt = Instant.now(),
                sleevesSyncedAt = Instant.now()
            )

            val baseGameEntity = Game(
                id = UUID.randomUUID(),
                bggId = baseGameId,
                name = "Catan Base Game",
                isExpansion = false,
                lastSyncedAt = Instant.now(),
                sleevesSyncedAt = Instant.now()
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(expansionGame)

            every {
                gameRepository.findByBggId(baseGameId)
            } returns Optional.of(baseGameEntity)

            every {
                gameSleeveRepository.findAllByGameId(any())
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    any()
                )
            } returns false

            val result = gameService.getOrSyncGame(
                bggId,
                resolveBaseGame = true
            )

            assertThat(result.baseGame).isNotNull
            assertThat(result.baseGame?.name)
                .isEqualTo("Catan Base Game")
        }

        @Test
        fun `should not resolve base game when resolveBaseGame is false`() {
            val baseGameId = 100L

            val expansionGame = Game(
                id = gameId,
                bggId = bggId,
                name = "Catan Expansion",
                isExpansion = true,
                baseGameBggId = baseGameId,
                lastSyncedAt = Instant.now(),
                sleevesSyncedAt = Instant.now()
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(expansionGame)

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            val result = gameService.getOrSyncGame(
                bggId,
                resolveBaseGame = false
            )

            assertThat(result.baseGame).isNull()

            verify(exactly = 0) {
                gameRepository.findByBggId(baseGameId)
            }
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
                lastSyncedAt = Instant.now(),
                sleevesSyncedAt = Instant.now()
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(expansionGame)

            every {
                gameRepository.findByBggId(baseGameId)
            } returns Optional.empty()

            every {
                bggClient.getGameDetails(baseGameId)
            } throws GameNotFoundOnBggException(baseGameId)

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            val result = gameService.getOrSyncGame(
                bggId,
                resolveBaseGame = true
            )

            assertThat(result).isNotNull
            assertThat(result.baseGame).isNull()
        }

        @Test
        fun `should throw GameNotFoundOnBggException when game does not exist on BGG`() {
            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.empty()

            every {
                bggClient.getGameDetails(bggId)
            } returns BggThingResponseXml(emptyList())

            assertThatThrownBy {
                gameService.getOrSyncGame(bggId)
            }
                .isInstanceOf(GameNotFoundOnBggException::class.java)
        }

        @Test
        fun `should rethrow unexpected error during getOrSyncGame`() {
            every {
                gameRepository.findByBggId(bggId)
            } throws RuntimeException("DB Outage")

            assertThatThrownBy {
                gameService.getOrSyncGame(bggId)
            }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB Outage")
        }

        @Test
        fun `should cover all null and boundary branches in applyBggDetails`() {
            val game = Game(
                id = gameId,
                bggId = 100L,
                name = "Original Name",
                lastSyncedAt = Instant.now().minus(
                    10,
                    ChronoUnit.DAYS
                ),
                sleevesSyncedAt = Instant.now()
            )

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
                    ratings = BggThingRatingsXml(
                        averageWeight = BggValueXml("-1.0")
                    )
                )
            )

            every {
                gameRepository.findByBggId(100L)
            } returns Optional.of(game)

            every {
                bggClient.getGameDetails(100L)
            } returns BggThingResponseXml(
                listOf(emptyDetails)
            )

            every {
                gameRepository.save(any<Game>())
            } answers {
                firstArg()
            }

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            val result = gameService.getOrSyncGame(
                100L,
                resolveBaseGame = false
            )

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
            assertThat(result.isExpansion).isFalse
        }

        @Test
        fun `should cover multi-line description and expansion type branches in applyBggDetails`() {
            val game = Game(
                id = gameId,
                bggId = 200L,
                name = "Expansion Name",
                lastSyncedAt = Instant.now().minus(
                    10,
                    ChronoUnit.DAYS
                ),
                sleevesSyncedAt = Instant.now()
            )

            val expansionDetails = BggThingItemXml(
                id = 200L,
                type = "boardgameexpansion",
                names = listOf(
                    BggThingNameXml(
                        type = "primary",
                        value = "Expansion Name"
                    )
                ),
                description = "Line 1<br/><br/><br/><br/>Line 2",
                yearPublished = BggValueXml("2023"),
                minPlayers = BggValueXml("2"),
                maxPlayers = BggValueXml("4"),
                playingTime = BggValueXml("60"),
                links = listOf(
                    BggThingLinkXml(
                        type = "boardgameexpansion",
                        id = "50",
                        value = "Base Game",
                        inbound = true
                    )
                ),
                statistics = BggThingStatisticsXml(
                    ratings = BggThingRatingsXml(
                        averageWeight = BggValueXml("3.5")
                    )
                )
            )

            every {
                gameRepository.findByBggId(200L)
            } returns Optional.of(game)

            every {
                bggClient.getGameDetails(200L)
            } returns BggThingResponseXml(
                listOf(expansionDetails)
            )

            every {
                gameRepository.save(any<Game>())
            } answers {
                firstArg()
            }

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            val result = gameService.getOrSyncGame(
                200L,
                resolveBaseGame = false
            )

            assertThat(result.isExpansion).isTrue
            assertThat(result.description)
                .contains("Line 1\n\nLine 2")
            assertThat(result.difficulty)
                .isEqualTo(3.5)
        }

        @Test
        fun `should cover sleeve property null branches`() {
            val game = Game(
                id = gameId,
                bggId = 400L,
                name = "Blank Sleeve Game",
                lastSyncedAt = Instant.now(),
                sleevesSyncedAt = null
            )

            every {
                gameRepository.findByBggId(400L)
            } returns Optional.of(game)

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList() andThen listOf(
                GameSleeve(
                    id = UUID.randomUUID(),
                    game = game,
                    name = "Custom Sleeve"
                )
            )

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            every {
                bggClient.getCardSetsByGame(400L)
            } returns CardSetsByGameResponse(
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

            every {
                gameSleevePersistenceService.replaceSleeves(
                    gameId,
                    game,
                    match<List<GameSleeve>> {
                        val sleeve = it.first()
                        sleeve.name == "Custom Sleeve" &&
                                sleeve.height == null &&
                                sleeve.width == null &&
                                sleeve.quantity == null &&
                                sleeve.quantityNote == null
                    }
                )
            } just Runs

            val result = gameService.getOrSyncGame(
                400L,
                resolveBaseGame = false
            )

            assertThat(result).isNotNull

            verify(exactly = 1) {
                gameSleevePersistenceService.replaceSleeves(
                    gameId,
                    game,
                    match<List<GameSleeve>> {
                        val sleeve = it.first()
                        sleeve.name == "Custom Sleeve" &&
                                sleeve.height == null &&
                                sleeve.width == null &&
                                sleeve.quantity == null &&
                                sleeve.quantityNote == null
                    }
                )
            }
        }

        @Test
        fun `should continue when sleeve synchronization throws exception`() {
            val game = Game(
                id = gameId,
                bggId = bggId,
                name = "Catch Test Game",
                lastSyncedAt = Instant.now(),
                sleevesSyncedAt = null
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(game)

            every {
                gameSleeveRepository.findAllByGameId(gameId)
            } returns emptyList()

            every {
                bggClient.getCardSetsByGame(bggId)
            } throws RuntimeException("BGG Sleeve API Error")

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    gameId
                )
            } returns false

            val result = gameService.getOrSyncGame(
                bggId,
                resolveBaseGame = false
            )

            assertThat(result).isNotNull

            verify(exactly = 1) {
                bggClient.getCardSetsByGame(bggId)
            }

            verify(exactly = 0) {
                gameSleevePersistenceService.replaceSleeves(
                    any(),
                    any(),
                    any()
                )
            }
        }
    }

    @Nested
    @DisplayName("getHotGames")
    inner class GetHotGames {

        @Test
        fun `should clamp page and size and return paged hot games`() {
            val game1 = Game(
                id = gameId,
                bggId = 1L,
                name = "Hot Game 1",
                rank = 1
            )

            val pageableSlot = slot<Pageable>()

            val pageImpl = PageImpl(
                listOf(game1),
                PageRequest.of(0, 100),
                1
            )

            every {
                gameRepository.findAllByRankIsNotNullOrderByRankAsc(
                    capture(pageableSlot)
                )
            } returns pageImpl

            every {
                collectionItemRepository.findGameIdsInCollection(
                    userId,
                    listOf(gameId)
                )
            } returns setOf(gameId)

            every {
                gameSleeveRepository.findAllByGameIdIn(
                    listOf(gameId)
                )
            } returns listOf(
                GameSleeve(
                    id = UUID.randomUUID(),
                    game = game1,
                    name = "Sleeve 1"
                )
            )

            val result = gameService.getHotGames(
                page = -10,
                size = 500
            )

            assertThat(result).isNotNull
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].inCollection).isTrue
            assertThat(result.content[0].sleeves).hasSize(1)

            assertThat(
                (pageableSlot.captured as PageRequest).pageNumber
            ).isEqualTo(0)

            assertThat(
                (pageableSlot.captured as PageRequest).pageSize
            ).isEqualTo(100)
        }

        @Test
        fun `should return empty collection and sleeves when no related data exists`() {
            val game1 = Game(
                id = gameId,
                bggId = 1L,
                name = "Hot Game 1",
                rank = 1
            )

            every {
                gameRepository.findAllByRankIsNotNullOrderByRankAsc(any())
            } returns PageImpl(
                listOf(game1),
                PageRequest.of(0, 10),
                1
            )

            every {
                collectionItemRepository.findGameIdsInCollection(
                    userId,
                    listOf(gameId)
                )
            } returns emptySet()

            every {
                gameSleeveRepository.findAllByGameIdIn(
                    listOf(gameId)
                )
            } returns emptyList()

            val result = gameService.getHotGames(0, 10)

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].inCollection).isFalse
            assertThat(result.content[0].sleeves).isEmpty()
        }

        @Test
        fun `should rethrow exception during getHotGames`() {
            every {
                gameRepository.findAllByRankIsNotNullOrderByRankAsc(any())
            } throws RuntimeException("Query error")

            assertThatThrownBy {
                gameService.getHotGames(0, 10)
            }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Query error")
        }
    }

    @Nested
    @DisplayName("refreshHotGames")
    inner class RefreshHotGames {

        @Test
        fun `should do nothing when hot games response from BGG is empty`() {
            every {
                bggClient.getHotGames()
            } returns BggHotResponseXml(emptyList())

            gameService.refreshHotGames()

            verify(exactly = 0) {
                gameRepository.findAllByBggIdIn(any())
            }

            verify(exactly = 0) {
                hotGamesPersistenceService.saveHotGames(any())
            }

            verify(exactly = 0) {
                bggClient.getGameDetailsBatch(any())
            }

            verify(exactly = 0) {
                bggClient.getCardSetsByGame(any())
            }
        }

        @Test
        fun `should refresh hot games with full enriched details without syncing sleeves`() {
            val hotItems = listOf(
                BggHotItemXml(
                    id = 10L,
                    rank = 1,
                    name = BggValueXml("Hot Game 1")
                )
            )

            val details = createSampleBggThingItem(
                id = 10L,
                primaryName = "Hot Game Enriched"
            )

            val existingGame = Game(
                id = gameId,
                bggId = 10L,
                name = "Old Name",
                lastSyncedAt = Instant.now()
            )

            every {
                bggClient.getHotGames()
            } returns BggHotResponseXml(hotItems)

            every {
                bggClient.getGameDetailsBatch(listOf(10L))
            } returns BggThingResponseXml(
                listOf(details)
            )

            every {
                gameRepository.findAllByBggIdIn(listOf(10L))
            } returns listOf(existingGame)

            every {
                hotGamesPersistenceService.saveHotGames(any())
            } just Runs

            gameService.refreshHotGames()

            val gamesSlot = slot<List<Game>>()

            verify(exactly = 1) {
                hotGamesPersistenceService.saveHotGames(
                    capture(gamesSlot)
                )
            }

            assertThat(gamesSlot.captured).hasSize(1)
            assertThat(gamesSlot.captured[0]).isSameAs(existingGame)
            assertThat(gamesSlot.captured[0].name)
                .isEqualTo("Hot Game Enriched")
            assertThat(gamesSlot.captured[0].rank)
                .isEqualTo(1)

            verify(exactly = 1) {
                gameRepository.findAllByBggIdIn(listOf(10L))
            }

            verify(exactly = 0) {
                gameRepository.clearAllRanks()
            }

            verify(exactly = 0) {
                gameRepository.save(any<Game>())
            }

            verify(exactly = 0) {
                bggClient.getCardSetsByGame(any())
            }
        }

        @Test
        fun `should create missing hot game when batch details are available`() {
            val hotItems = listOf(
                BggHotItemXml(
                    id = 10L,
                    rank = 1,
                    name = BggValueXml("Hot Game 1")
                )
            )

            val details = createSampleBggThingItem(
                id = 10L,
                primaryName = "Hot Game 1"
            )

            every {
                bggClient.getHotGames()
            } returns BggHotResponseXml(hotItems)

            every {
                bggClient.getGameDetailsBatch(listOf(10L))
            } returns BggThingResponseXml(
                listOf(details)
            )

            every {
                gameRepository.findAllByBggIdIn(listOf(10L))
            } returns emptyList()

            every {
                hotGamesPersistenceService.saveHotGames(any())
            } just Runs

            gameService.refreshHotGames()

            val gamesSlot = slot<List<Game>>()

            verify(exactly = 1) {
                hotGamesPersistenceService.saveHotGames(
                    capture(gamesSlot)
                )
            }

            val savedGame = gamesSlot.captured.single()

            assertThat(savedGame.bggId).isEqualTo(10L)
            assertThat(savedGame.name).isEqualTo("Hot Game 1")
            assertThat(savedGame.rank).isEqualTo(1)
        }

        @Test
        fun `should fallback to lightweight hot data when batch details fail`() {
            val hotItems = listOf(
                BggHotItemXml(
                    id = 10L,
                    rank = 1,
                    name = BggValueXml("Hot Game 1"),
                    yearPublished = BggValueXml("2023")
                )
            )

            every {
                bggClient.getHotGames()
            } returns BggHotResponseXml(hotItems)

            every {
                bggClient.getGameDetailsBatch(listOf(10L))
            } throws RuntimeException("BGG Batch Timeout")

            every {
                gameRepository.findAllByBggIdIn(listOf(10L))
            } returns emptyList()

            every {
                hotGamesPersistenceService.saveHotGames(any())
            } just Runs

            gameService.refreshHotGames()

            val gamesSlot = slot<List<Game>>()

            verify(exactly = 1) {
                hotGamesPersistenceService.saveHotGames(
                    capture(gamesSlot)
                )
            }

            val savedGame = gamesSlot.captured.single()

            assertThat(savedGame.bggId).isEqualTo(10L)
            assertThat(savedGame.name).isEqualTo("Hot Game 1")
            assertThat(savedGame.yearPublished).isEqualTo(2023)
            assertThat(savedGame.rank).isEqualTo(1)
            assertThat(savedGame.lastSyncedAt)
                .isEqualTo(Instant.EPOCH)
        }

        @Test
        fun `should preserve existing game when batch details fail`() {
            val hotItems = listOf(
                BggHotItemXml(
                    id = 10L,
                    rank = 5,
                    name = BggValueXml("Lightweight Name")
                )
            )

            val existingGame = Game(
                id = gameId,
                bggId = 10L,
                name = "Existing Game",
                lastSyncedAt = Instant.now(),
                rank = 99
            )

            every {
                bggClient.getHotGames()
            } returns BggHotResponseXml(hotItems)

            every {
                bggClient.getGameDetailsBatch(listOf(10L))
            } throws RuntimeException("Batch failed")

            every {
                gameRepository.findAllByBggIdIn(listOf(10L))
            } returns listOf(existingGame)

            every {
                hotGamesPersistenceService.saveHotGames(any())
            } just Runs

            gameService.refreshHotGames()

            assertThat(existingGame.name)
                .isEqualTo("Existing Game")

            assertThat(existingGame.rank)
                .isEqualTo(5)
        }

        @Test
        fun `should refresh multiple hot games preserving their ranks`() {
            val hotItems = listOf(
                BggHotItemXml(
                    id = 10L,
                    rank = 1,
                    name = BggValueXml("Game A")
                ),
                BggHotItemXml(
                    id = 20L,
                    rank = 2,
                    name = BggValueXml("Game B")
                )
            )

            every {
                bggClient.getHotGames()
            } returns BggHotResponseXml(hotItems)

            every {
                bggClient.getGameDetailsBatch(
                    listOf(10L, 20L)
                )
            } returns BggThingResponseXml(emptyList())

            every {
                gameRepository.findAllByBggIdIn(
                    listOf(10L, 20L)
                )
            } returns emptyList()

            every {
                hotGamesPersistenceService.saveHotGames(any())
            } just Runs

            gameService.refreshHotGames()

            val gamesSlot = slot<List<Game>>()

            verify(exactly = 1) {
                hotGamesPersistenceService.saveHotGames(
                    capture(gamesSlot)
                )
            }

            assertThat(
                gamesSlot.captured.map { it.bggId }
            ).containsExactly(10L, 20L)

            assertThat(
                gamesSlot.captured.map { it.rank }
            ).containsExactly(1, 2)
        }

        @Test
        fun `should not throw when persistence fails during refreshHotGames`() {
            val hotItems = listOf(
                BggHotItemXml(id = 10L, rank = 1, name = BggValueXml("Hot Game"))
            )

            every { bggClient.getHotGames() } returns BggHotResponseXml(hotItems)
            every { bggClient.getGameDetailsBatch(listOf(10L)) } returns BggThingResponseXml(emptyList())
            every { gameRepository.findAllByBggIdIn(listOf(10L)) } returns emptyList()
            every { hotGamesPersistenceService.saveHotGames(any()) } throws RuntimeException("DB failure")

            assertThatCode { gameService.refreshHotGames() }.doesNotThrowAnyException()
        }

        @Test
        fun `should not throw when BGG call fails during refreshHotGames`() {
            every { bggClient.getHotGames() } throws RuntimeException("Network Error")

            assertThatCode { gameService.refreshHotGames() }.doesNotThrowAnyException()
        }
    }

    @Nested
    @DisplayName("search")
    inner class Search {

        @Test
        fun `should return empty page when requested start offset exceeds search results`() {
            val searchResults = listOf(
                BggSearchItemXml(
                    id = 1L,
                    name = BggNameXml("Game 1")
                )
            )

            every {
                bggClient.searchGames("catan")
            } returns BggSearchResponseXml(searchResults)

            val result = gameService.search(
                "catan",
                page = 5,
                size = 10
            )

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(1)

            verify(exactly = 0) {
                gameRepository.findAllByBggIdIn(any())
            }
        }

        @Test
        fun `should use fresh cached games without fetching BGG details`() {
            val searchResults = listOf(
                BggSearchItemXml(
                    id = 1L,
                    name = BggNameXml("Cached Game")
                )
            )

            val existingGame = Game(
                id = gameId,
                bggId = 1L,
                name = "Cached Game",
                lastSyncedAt = Instant.now()
            )

            every {
                bggClient.searchGames("cached")
            } returns BggSearchResponseXml(searchResults)

            every {
                gameRepository.findAllByBggIdIn(listOf(1L))
            } returns listOf(existingGame)

            every {
                collectionItemRepository.findGameIdsInCollection(
                    userId,
                    listOf(gameId)
                )
            } returns emptySet()

            val result = gameService.search(
                "cached",
                page = 0,
                size = 10
            )

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name)
                .isEqualTo("Cached Game")

            verify(exactly = 0) {
                bggClient.getGameDetailsBatch(any())
            }

            verify(exactly = 0) {
                hotGamesPersistenceService.saveGames(any())
            }
        }

        @Test
        fun `should enrich stale and missing games while keeping fresh games from cache`() {
            val searchResults = listOf(
                BggSearchItemXml(
                    id = 1L,
                    name = BggNameXml("Fresh Game")
                ),
                BggSearchItemXml(
                    id = 2L,
                    name = BggNameXml("Stale Game")
                ),
                BggSearchItemXml(
                    id = 3L,
                    name = BggNameXml("New Game")
                )
            )

            val freshGame = Game(
                id = UUID.randomUUID(),
                bggId = 1L,
                name = "Fresh Game",
                lastSyncedAt = Instant.now()
            )

            val staleGame = Game(
                id = UUID.randomUUID(),
                bggId = 2L,
                name = "Old Stale Game",
                lastSyncedAt = Instant.now().minus(
                    10,
                    ChronoUnit.DAYS
                )
            )

            val details2 = createSampleBggThingItem(
                id = 2L,
                primaryName = "Stale Game Updated"
            )

            val details3 = createSampleBggThingItem(
                id = 3L,
                primaryName = "New Game"
            )

            val savedStaleGame = Game(
                id = staleGame.id,
                bggId = 2L,
                name = "Stale Game Updated",
                lastSyncedAt = Instant.now()
            )

            val savedNewGame = Game(
                id = UUID.randomUUID(),
                bggId = 3L,
                name = "New Game",
                lastSyncedAt = Instant.now()
            )

            every {
                bggClient.searchGames("mixed")
            } returns BggSearchResponseXml(searchResults)

            every {
                gameRepository.findAllByBggIdIn(
                    listOf(1L, 2L, 3L)
                )
            } returns listOf(
                freshGame,
                staleGame
            )

            every {
                bggClient.getGameDetailsBatch(
                    listOf(2L, 3L)
                )
            } returns BggThingResponseXml(
                listOf(details2, details3)
            )

            every {
                hotGamesPersistenceService.saveGames(any())
            } returns listOf(
                savedStaleGame,
                savedNewGame
            )

            every {
                collectionItemRepository.findGameIdsInCollection(
                    userId,
                    listOf(
                        freshGame.id!!,
                        savedStaleGame.id!!,
                        savedNewGame.id!!
                    )
                )
            } returns setOf(freshGame.id!!)

            val result = gameService.search(
                "mixed",
                page = 0,
                size = 10
            )

            assertThat(result.content).hasSize(3)
            assertThat(result.content[0].name)
                .isEqualTo("Fresh Game")
            assertThat(result.content[1].name)
                .isEqualTo("Stale Game Updated")
            assertThat(result.content[2].name)
                .isEqualTo("New Game")

            verify(exactly = 1) {
                bggClient.getGameDetailsBatch(
                    listOf(2L, 3L)
                )
            }

            verify(exactly = 1) {
                hotGamesPersistenceService.saveGames(any())
            }
        }

        @Test
        fun `should fallback to lightweight search result when batch enrichment fails`() {
            val searchResults = listOf(
                BggSearchItemXml(
                    id = 1L,
                    name = BggNameXml("Lightweight Game"),
                    yearPublished = BggValueXml("2021")
                )
            )

            every {
                bggClient.searchGames("lightweight")
            } returns BggSearchResponseXml(searchResults)

            every {
                gameRepository.findAllByBggIdIn(listOf(1L))
            } returns emptyList()

            every {
                bggClient.getGameDetailsBatch(listOf(1L))
            } throws RuntimeException("Batch enrichment failed")

            val result = gameService.search(
                "lightweight",
                page = 0,
                size = 10
            )

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].bggId).isEqualTo(1L)
            assertThat(result.content[0].name)
                .isEqualTo("Lightweight Game")

            verify(exactly = 0) {
                hotGamesPersistenceService.saveGames(any())
            }
        }

        @Test
        fun `should return lightweight result when BGG details return no matching item`() {
            val searchResults = listOf(
                BggSearchItemXml(
                    id = 1L,
                    name = BggNameXml("Lightweight Game")
                )
            )

            every {
                bggClient.searchGames("missing-details")
            } returns BggSearchResponseXml(searchResults)

            every {
                gameRepository.findAllByBggIdIn(listOf(1L))
            } returns emptyList()

            every {
                bggClient.getGameDetailsBatch(listOf(1L))
            } returns BggThingResponseXml(emptyList())

            val result = gameService.search(
                "missing-details",
                page = 0,
                size = 10
            )

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].bggId).isEqualTo(1L)
            assertThat(result.content[0].name)
                .isEqualTo("Lightweight Game")
        }

        @Test
        fun `should rethrow persistence exception during search enrichment`() {
            val searchResults = listOf(
                BggSearchItemXml(
                    id = 1L,
                    name = BggNameXml("Game")
                )
            )

            val details = createSampleBggThingItem(
                id = 1L
            )

            every {
                bggClient.searchGames("save-error")
            } returns BggSearchResponseXml(searchResults)

            every {
                gameRepository.findAllByBggIdIn(listOf(1L))
            } returns emptyList()

            every {
                bggClient.getGameDetailsBatch(listOf(1L))
            } returns BggThingResponseXml(
                listOf(details)
            )

            every {
                hotGamesPersistenceService.saveGames(any())
            } throws RuntimeException("DB failure")

            assertThatThrownBy {
                gameService.search(
                    "save-error",
                    0,
                    10
                )
            }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB failure")
        }

        @Test
        fun `should rethrow exception during search`() {
            every {
                bggClient.searchGames("error")
            } throws RuntimeException("Search API Down")

            assertThatThrownBy {
                gameService.search(
                    "error",
                    0,
                    10
                )
            }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Search API Down")
        }
    }

    @Nested
    @DisplayName("getExpansions")
    inner class GetExpansions {

        @Test
        fun `should throw GameNotFoundOnBggException when game does not exist in db`() {
            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.empty()

            assertThatThrownBy {
                gameService.getExpansions(
                    bggId,
                    0,
                    10
                )
            }
                .isInstanceOf(GameNotFoundOnBggException::class.java)
        }

        @Test
        fun `should return sorted expansions and skip failed expansion syncs`() {
            val gameWithExpansions = Game(
                id = gameId,
                bggId = bggId,
                name = "Base Game",
                expansionRefs = listOf(
                    ExpansionRef(201L, "Exp B"),
                    ExpansionRef(202L, "Exp A")
                )
            )

            val expA = Game(
                id = UUID.randomUUID(),
                bggId = 202L,
                name = "Exp A",
                yearPublished = 2020,
                sleevesSyncedAt = Instant.now()
            )

            val expB = Game(
                id = UUID.randomUUID(),
                bggId = 201L,
                name = "Exp B",
                yearPublished = 2018,
                sleevesSyncedAt = Instant.now()
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(gameWithExpansions)

            every {
                gameRepository.findByBggId(202L)
            } returns Optional.of(expA)

            every {
                gameRepository.findByBggId(201L)
            } returns Optional.of(expB)

            every {
                gameSleeveRepository.findAllByGameId(any())
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    any()
                )
            } returns false

            val result = gameService.getExpansions(
                bggId,
                page = 0,
                size = 10
            )

            assertThat(result.content).hasSize(2)
            assertThat(result.content[0].bggId)
                .isEqualTo(201L)
            assertThat(result.content[1].bggId)
                .isEqualTo(202L)
        }

        @Test
        fun `should skip expansion when expansion sync fails`() {
            val gameWithExpansions = Game(
                id = gameId,
                bggId = bggId,
                name = "Base Game",
                expansionRefs = listOf(
                    ExpansionRef(201L, "Exp B"),
                    ExpansionRef(202L, "Exp A")
                )
            )

            val validExpansion = Game(
                id = UUID.randomUUID(),
                bggId = 201L,
                name = "Exp B",
                yearPublished = 2018,
                sleevesSyncedAt = Instant.now()
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(gameWithExpansions)

            every {
                gameRepository.findByBggId(201L)
            } returns Optional.of(validExpansion)

            every {
                gameRepository.findByBggId(202L)
            } throws RuntimeException("Expansion error")

            every {
                gameSleeveRepository.findAllByGameId(any())
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    any()
                )
            } returns false

            val result = gameService.getExpansions(
                bggId,
                page = 0,
                size = 10
            )

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].bggId)
                .isEqualTo(201L)
        }

        @Test
        fun `should return empty page content if start index exceeds total expansions`() {
            val gameWithExpansions = Game(
                id = gameId,
                bggId = bggId,
                name = "Base Game",
                expansionRefs = listOf(
                    ExpansionRef(201L, "Exp B")
                )
            )

            val expB = Game(
                id = UUID.randomUUID(),
                bggId = 201L,
                name = "Exp B",
                yearPublished = 2018,
                sleevesSyncedAt = Instant.now()
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(gameWithExpansions)

            every {
                gameRepository.findByBggId(201L)
            } returns Optional.of(expB)

            every {
                gameSleeveRepository.findAllByGameId(any())
            } returns emptyList()

            every {
                collectionItemRepository.existsByUserIdAndGameId(
                    userId,
                    any()
                )
            } returns false

            val result = gameService.getExpansions(
                bggId,
                page = 5,
                size = 10
            )

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(1)
        }

        @Test
        fun `should clamp page and size in getExpansions`() {
            val gameWithNoExpansions = Game(
                id = gameId,
                bggId = bggId,
                name = "Base Game",
                expansionRefs = emptyList()
            )

            every {
                gameRepository.findByBggId(bggId)
            } returns Optional.of(gameWithNoExpansions)

            val result = gameService.getExpansions(
                bggId,
                page = -10,
                size = 500
            )

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0)
        }

        @Test
        fun `should rethrow unexpected exception during getExpansions`() {
            every {
                gameRepository.findByBggId(bggId)
            } throws RuntimeException("Unexpected error")

            assertThatThrownBy {
                gameService.getExpansions(
                    bggId,
                    0,
                    10
                )
            }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Unexpected error")
        }
    }
}