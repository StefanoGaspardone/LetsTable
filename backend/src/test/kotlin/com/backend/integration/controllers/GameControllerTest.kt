package com.backend.integration.controllers

import com.backend.clients.BggClient
import com.backend.models.dtos.*
import com.backend.models.entities.BggRankIndex
import com.backend.models.entities.Category
import com.backend.models.entities.CollectionItem
import com.backend.models.entities.ExpansionRef
import com.backend.models.entities.Game
import com.backend.models.entities.Mechanic
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.BggRankIndexRepository
import com.backend.repositories.CategoryRepository
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import com.backend.repositories.MechanicRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@AutoConfigureMockMvc
class GameControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var gameSleeveRepository: GameSleeveRepository

    @Autowired
    private lateinit var bggRankIndexRepository: BggRankIndexRepository

    @Autowired
    private lateinit var collectionItemRepository: CollectionItemRepository

    @Autowired
    private lateinit var mechanicRepository: MechanicRepository

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var bggClient: BggClient

    private lateinit var user: User

    companion object {
        private const val BASE_URL = "/api/v1/games"
    }

    @BeforeEach
    fun setUp() {
        user = persistUser()
        given(bggClient.getCardSetsByGame(anyLong())).willReturn(CardSetsByGameResponse(cardSets = emptyList()))
    }

    @AfterEach
    fun cleanUp() {
        collectionItemRepository.deleteAll()
        bggRankIndexRepository.deleteAll()
        gameSleeveRepository.deleteAll()
        gameRepository.deleteAll()
        mechanicRepository.deleteAll()
        categoryRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(username: String = "stefano"): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = UserRole.USER,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistGame(
        bggId: Long,
        name: String = "Cached Game",
        lastSyncedAt: Instant = Instant.now(),
        isExpansion: Boolean? = false,
        baseGameBggId: Long? = null,
        expansionRefs: List<ExpansionRef> = emptyList(),
        rank: Int? = null,
        bggRank: Int? = null,
        mechanics: Set<Mechanic> = emptySet(),
        categories: Set<Category> = emptySet(),
    ): Game =
        gameRepository.saveAndFlush(
            Game(
                bggId = bggId,
                name = name,
                lastSyncedAt = lastSyncedAt,
                isExpansion = isExpansion,
                baseGameBggId = baseGameBggId,
                expansionRefs = expansionRefs,
            ).apply {
                this.rank = rank
                this.bggRank = bggRank
                this.mechanics = mechanics.toMutableSet()
                this.categories = categories.toMutableSet()
                this.sleevesSyncedAt = Instant.now()
            }
        )

    private fun persistMechanic(bggId: Long, name: String): Mechanic = mechanicRepository.saveAndFlush(Mechanic(bggId = bggId, name = name))

    private fun persistCategory(bggId: Long, name: String): Category = categoryRepository.saveAndFlush(Category(bggId = bggId, name = name))

    private fun bggThingItem(id: Long, name: String = "SETI", type: String = "boardgame", year: String = "2024") = BggThingItemXml(
        id = id,
        thumbnail = "https://example.com/thumb.jpg",
        image = "https://example.com/image.jpg",
        names = listOf(BggThingNameXml(type = "primary", value = name)),
        description = "A description",
        yearPublished = BggValueXml(value = year),
        minPlayers = BggValueXml(value = "1"),
        maxPlayers = BggValueXml(value = "4"),
        playingTime = BggValueXml(value = "90"),
        type = type,
    )

    private fun things(vararg items: BggThingItemXml) = BggThingResponseXml(items = items.toList())

    private fun perform(url: String, vararg params: Pair<String, String>, as_: User? = user): ResultActions =
        mockMvc.perform(get(url).apply {
            params.forEach { (key, value) -> param(key, value) }
            as_?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) }
        })

    @Nested
    @DisplayName("GET /api/v1/games/search - BoardGameGeek")
    inner class BggSearchTests {

        private fun searchResult(id: Long, name: String) =
            BggSearchItemXml(id = id, name = BggNameXml(value = name), yearPublished = BggValueXml(value = "2021"))

        @Test
        fun `should search and enrich results with batch details`() {
            given(bggClient.searchGames("ark")).willReturn(BggSearchResponseXml(items = listOf(searchResult(100L, "Ark Nova"))))
            given(bggClient.getGameDetailsBatch(listOf(100L))).willReturn(things(bggThingItem(id = 100L, name = "Ark Nova")))

            perform("$BASE_URL/search", "query" to "ark")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Ark Nova")))
                .andExpect(jsonPath("$.content[0].bggId").value(100))
                .andExpect(jsonPath("$.content[0].id").isNotEmpty)

            assertThat(gameRepository.findByBggId(100L)).isPresent
        }

        @Test
        fun `should keep the BGG order and enrich only the requested page`() {
            given(bggClient.searchGames("catan")).willReturn(BggSearchResponseXml(items = listOf(searchResult(1L, "Catan"), searchResult(2L, "Catan: Seafarers"), searchResult(3L, "Catan Junior"))))
            given(bggClient.getGameDetailsBatch(listOf(3L))).willReturn(things(bggThingItem(id = 3L, name = "Catan Junior")))

            perform("$BASE_URL/search", "query" to "catan", "page" to "1", "size" to "2")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Catan Junior")))
                .andExpect(jsonPath("$.totalElements").value(3))

            verify(bggClient, never()).getGameDetailsBatch(listOf(1L, 2L))
        }

        @Test
        fun `should not ask BGG for details of games already cached and fresh`() {
            persistGame(bggId = 150L, name = "Cached Ark Nova")
            given(bggClient.searchGames("ark")).willReturn(BggSearchResponseXml(items = listOf(searchResult(150L, "Ark Nova"))))

            perform("$BASE_URL/search", "query" to "ark")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].name").value("Cached Ark Nova"))

            verify(bggClient, never()).getGameDetailsBatch(listOf(150L))
        }

        @Test
        fun `should fall back to lightweight results when batch enrichment fails`() {
            given(bggClient.searchGames("fallback")).willReturn(BggSearchResponseXml(items = listOf(searchResult(200L, "Fallback Game"))))
            given(bggClient.getGameDetailsBatch(listOf(200L))).willThrow(RuntimeException("BGG unreachable"))

            perform("$BASE_URL/search", "query" to "fallback")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Fallback Game")))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
        }

        @Test
        fun `should return an empty page when BGG has no results`() {
            given(bggClient.searchGames("nothing")).willReturn(BggSearchResponseXml(items = emptyList()))

            perform("$BASE_URL/search", "query" to "nothing")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
        }

        @Test
        fun `should trim the query before searching`() {
            given(bggClient.searchGames("ark")).willReturn(BggSearchResponseXml(items = emptyList()))

            perform("$BASE_URL/search", "query" to "  ark  ").andExpect(status().isOk)

            verify(bggClient).searchGames("ark")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/games/search - local catalog")
    inner class CatalogSearchTests {

        @Test
        fun `should list the whole catalog alphabetically without a query`() {
            persistGame(bggId = 1L, name = "Wingspan")
            persistGame(bggId = 2L, name = "Azul")
            persistGame(bggId = 3L, name = "Catan")

            perform("$BASE_URL/search")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Azul", "Catan", "Wingspan")))

            verify(bggClient, never()).searchGames(anyString())
        }

        @Test
        fun `should treat a blank query as no query`() {
            persistGame(bggId = 1L, name = "Azul")

            perform("$BASE_URL/search", "query" to "   ")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Azul")))

            verify(bggClient, never()).searchGames(anyString())
        }

        @Test
        fun `should search the catalog instead of BGG when a filter is present`() {
            persistGame(bggId = 1L, name = "Catan")
            persistGame(bggId = 2L, name = "Catan: Seafarers", isExpansion = true)
            persistGame(bggId = 3L, name = "Azul")

            perform("$BASE_URL/search", "query" to "CATAN", "isExpansion" to "false")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Catan")))

            verify(bggClient, never()).searchGames(anyString())
        }

        @Test
        fun `should filter expansions`() {
            persistGame(bggId = 1L, name = "Catan")
            persistGame(bggId = 2L, name = "Catan: Seafarers", isExpansion = true)

            perform("$BASE_URL/search", "isExpansion" to "true")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Catan: Seafarers")))
        }

        @Test
        fun `should filter by mechanic`() {
            val workerPlacement = persistMechanic(82L, "Worker Placement")
            val handManagement = persistMechanic(2040L, "Hand Management")
            persistGame(bggId = 1L, name = "Agricola", mechanics = setOf(workerPlacement))
            persistGame(bggId = 2L, name = "Lords of Waterdeep", mechanics = setOf(workerPlacement, handManagement))
            persistGame(bggId = 3L, name = "Uno", mechanics = setOf(handManagement))

            perform("$BASE_URL/search", "mechanicId" to workerPlacement.id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Agricola", "Lords of Waterdeep")))
        }

        @Test
        fun `should filter by category`() {
            val animals = persistCategory(1089L, "Animals")
            persistGame(bggId = 1L, name = "Wingspan", categories = setOf(animals))
            persistGame(bggId = 2L, name = "Azul")

            perform("$BASE_URL/search", "categoryId" to animals.id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Wingspan")))
        }

        @Test
        fun `should combine mechanic and category filters`() {
            val engine = persistMechanic(2875L, "End Game Bonuses")
            val animals = persistCategory(1089L, "Animals")
            persistGame(bggId = 1L, name = "Wingspan", mechanics = setOf(engine), categories = setOf(animals))
            persistGame(bggId = 2L, name = "Ark Nova", mechanics = setOf(engine))
            persistGame(bggId = 3L, name = "Cascadia", categories = setOf(animals))

            perform("$BASE_URL/search", "mechanicId" to engine.id.toString(), "categoryId" to animals.id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Wingspan")))
        }

        @Test
        fun `should sort by BGG rank keeping unranked games last`() {
            persistGame(bggId = 1L, name = "Unranked", bggRank = null)
            persistGame(bggId = 2L, name = "Second", bggRank = 20)
            persistGame(bggId = 3L, name = "First", bggRank = 1)

            perform("$BASE_URL/search", "sort" to "bggRank-asc")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("First", "Second", "Unranked")))

            perform("$BASE_URL/search", "sort" to "bggRank-desc")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Second", "First", "Unranked")))
        }

        @Test
        fun `should mark the games in the user's collection`() {
            val owned = persistGame(bggId = 1L, name = "Azul")
            persistGame(bggId = 2L, name = "Catan")
            collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = owned))

            perform("$BASE_URL/search")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].inCollection").value(contains(true, false)))
        }

        @Test
        fun `should return 400 when the sort field is not allowed`() {
            perform("$BASE_URL/search", "sort" to "price-asc").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the mechanic id is not a UUID`() {
            perform("$BASE_URL/search", "mechanicId" to "worker-placement").andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/games/hot")
    inner class HotGamesTests {

        @Test
        fun `should return hot games cached in the database ordered by rank`() {
            persistGame(bggId = 2L, name = "Second", rank = 2)
            persistGame(bggId = 1L, name = "First", rank = 1)

            perform("$BASE_URL/hot")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("First", "Second")))

            verify(bggClient, never()).getHotGames()
        }

        @Test
        fun `should not include games without a rank`() {
            persistGame(bggId = 10L, name = "Ranked", rank = 1)
            persistGame(bggId = 11L, name = "Not Ranked")

            perform("$BASE_URL/hot")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Ranked")))
        }

        @Test
        fun `should paginate the hot games`() {
            (1..3).forEach { persistGame(bggId = it.toLong(), name = "Hot $it", rank = it) }

            perform("$BASE_URL/hot", "page" to "1", "size" to "2")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Hot 3")))
                .andExpect(jsonPath("$.totalElements").value(3))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/games/{bggId}")
    inner class GetGameTests {

        @Test
        fun `should sync from BGG when game is not cached`() {
            given(bggClient.getGameDetails(500L)).willReturn(things(bggThingItem(id = 500L, name = "New Game")))

            perform("$BASE_URL/500")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("New Game"))
                .andExpect(jsonPath("$.bggId").value(500))

            assertThat(gameRepository.findByBggId(500L)).isPresent
        }

        @Test
        fun `should return cached game without hitting BGG when not stale`() {
            persistGame(bggId = 600L, name = "Fresh Cache")

            perform("$BASE_URL/600")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Fresh Cache"))

            verify(bggClient, never()).getGameDetails(600L)
        }

        @Test
        fun `should re-sync when cached game is stale`() {
            val stale = persistGame(bggId = 700L, name = "Stale Game", lastSyncedAt = Instant.now().minus(10, ChronoUnit.DAYS))
            given(bggClient.getGameDetails(700L)).willReturn(things(bggThingItem(id = 700L, name = "Refreshed Game")))

            perform("$BASE_URL/700")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Refreshed Game"))
                .andExpect(jsonPath("$.id").value(stale.id.toString()))

            assertThat(gameRepository.findAll()).hasSize(1)
            assertThat(gameRepository.findByBggId(700L).orElseThrow().lastSyncedAt).isAfter(Instant.now().minus(1, ChronoUnit.MINUTES))
        }

        @Test
        fun `should return 404 when game does not exist on BGG`() {
            given(bggClient.getGameDetails(999999L)).willReturn(things())

            perform("$BASE_URL/999999").andExpect(status().isNotFound)

            assertThat(gameRepository.findByBggId(999999L)).isEmpty
        }

        @Test
        fun `should return 400 when the BGG id is not a number`() {
            perform("$BASE_URL/ark-nova").andExpect(status().isBadRequest)
        }

        @Test
        fun `should resolve base game when the requested game is an expansion`() {
            persistGame(bggId = 802L, name = "Base Game")
            persistGame(bggId = 801L, name = "Expansion", isExpansion = true, baseGameBggId = 802L)

            perform("$BASE_URL/801")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Expansion"))
                .andExpect(jsonPath("$.baseGame.name").value("Base Game"))
        }

        @Test
        fun `should still return the expansion when its base game cannot be loaded`() {
            persistGame(bggId = 811L, name = "Orphan Expansion", isExpansion = true, baseGameBggId = 812L)
            given(bggClient.getGameDetails(812L)).willReturn(things())

            perform("$BASE_URL/811")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Orphan Expansion"))
                .andExpect(jsonPath("$.baseGame").doesNotExist())
        }

        @Test
        fun `should report a game in the user's collection`() {
            val game = persistGame(bggId = 900L, name = "Owned Game")
            collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = game))

            perform("$BASE_URL/900")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(true))
        }

        @Test
        fun `should report a game owned only by someone else as not in collection`() {
            val game = persistGame(bggId = 901L, name = "Someone Else's Game")
            collectionItemRepository.saveAndFlush(CollectionItem(user = persistUser("marco"), game = game))

            perform("$BASE_URL/901")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(false))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            persistGame(bggId = 902L)

            perform("$BASE_URL/902", as_ = null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/games/{bggId}/expansions")
    inner class GetExpansionsTests {

        @Test
        fun `should sync expansions in batch and sort them by year then name`() {
            persistGame(bggId = 1000L, name = "Base", expansionRefs = listOf(ExpansionRef(1001L, "Expansion B"), ExpansionRef(1002L, "Expansion A"), ExpansionRef(1003L, "Expansion C")))
            given(bggClient.getGameDetailsBatch(listOf(1001L, 1002L, 1003L))).willReturn(
                things(
                    bggThingItem(id = 1001L, name = "Expansion B", type = "boardgameexpansion", year = "2022"),
                    bggThingItem(id = 1002L, name = "Expansion A", type = "boardgameexpansion", year = "2021"),
                    bggThingItem(id = 1003L, name = "Expansion C", type = "boardgameexpansion", year = "2021"),
                )
            )

            perform("$BASE_URL/1000/expansions")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Expansion A", "Expansion C", "Expansion B")))
                .andExpect(jsonPath("$.totalElements").value(3))

            assertThat(gameRepository.findAllByBggIdIn(listOf(1001L, 1002L, 1003L))).hasSize(3)
        }

        @Test
        fun `should use the cached expansions without calling BGG`() {
            persistGame(bggId = 1050L, name = "Base", expansionRefs = listOf(ExpansionRef(1051L, "Cached Expansion")))
            persistGame(bggId = 1051L, name = "Cached Expansion", isExpansion = true)

            perform("$BASE_URL/1050/expansions")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Cached Expansion")))

            verify(bggClient, never()).getGameDetailsBatch(listOf(1051L))
        }

        @Test
        fun `should paginate the expansions`() {
            persistGame(bggId = 1060L, name = "Base", expansionRefs = (1..3).map { ExpansionRef(1060L + it, "Expansion $it") })
            (1..3).forEach { persistGame(bggId = 1060L + it, name = "Expansion $it", isExpansion = true) }

            perform("$BASE_URL/1060/expansions", "page" to "1", "size" to "2")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Expansion 3")))
                .andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should return 404 when base game does not exist`() {
            perform("$BASE_URL/123456/expansions").andExpect(status().isNotFound)
        }

        @Test
        fun `should return an empty page when game has no expansions`() {
            persistGame(bggId = 1100L, name = "No Expansions Game")

            perform("$BASE_URL/1100/expansions")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))

            verify(bggClient, never()).getGameDetailsBatch(listOf())
        }

        @Test
        fun `should skip an expansion that BGG does not return and still return the others`() {
            persistGame(bggId = 1200L, name = "Base", expansionRefs = listOf(ExpansionRef(1201L, "Good Expansion"), ExpansionRef(1202L, "Broken Expansion")))
            given(bggClient.getGameDetailsBatch(listOf(1201L, 1202L))).willReturn(things(bggThingItem(id = 1201L, name = "Good Expansion", type = "boardgameexpansion")))

            perform("$BASE_URL/1200/expansions")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Good Expansion")))
        }

        @Test
        fun `should return an empty page when BGG is unreachable and nothing is cached`() {
            persistGame(bggId = 1300L, name = "Base", expansionRefs = listOf(ExpansionRef(1301L, "Unreachable Expansion")))
            given(bggClient.getGameDetailsBatch(listOf(1301L))).willThrow(RuntimeException("BGG timeout"))

            perform("$BASE_URL/1300/expansions")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/games/overall")
    inner class OverallGamesTests {

        @Test
        fun `should return games ordered by the uploaded rank index using cached data`() {
            persistGame(bggId = 2001L, name = "Rank Two Game")
            persistGame(bggId = 2000L, name = "Rank One Game")
            bggRankIndexRepository.saveAndFlush(BggRankIndex(bggId = 2001L, rank = 2))
            bggRankIndexRepository.saveAndFlush(BggRankIndex(bggId = 2000L, rank = 1))

            perform("$BASE_URL/overall")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Rank One Game", "Rank Two Game")))

            verify(bggClient, never()).getGameDetailsBatch(listOf(2000L, 2001L))
        }

        @Test
        fun `should sync a game from BGG when it is not cached yet`() {
            bggRankIndexRepository.saveAndFlush(BggRankIndex(bggId = 2100L, rank = 1))
            given(bggClient.getGameDetailsBatch(listOf(2100L))).willReturn(things(bggThingItem(id = 2100L, name = "Newly Synced")))

            perform("$BASE_URL/overall")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Newly Synced")))

            assertThat(gameRepository.findByBggId(2100L)).isPresent
        }

        @Test
        fun `should fall back to the stale cached game when BGG is unreachable`() {
            persistGame(bggId = 2150L, name = "Old Cached Name", lastSyncedAt = Instant.now().minus(30, ChronoUnit.DAYS))
            bggRankIndexRepository.saveAndFlush(BggRankIndex(bggId = 2150L, rank = 1))
            given(bggClient.getGameDetailsBatch(listOf(2150L))).willThrow(RuntimeException("BGG unreachable"))

            perform("$BASE_URL/overall")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Old Cached Name")))
        }

        @Test
        fun `should return an empty page when the rank index is empty`() {
            perform("$BASE_URL/overall")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
        }

        @Test
        fun `should skip a rank entry when batch enrichment fails and no cached game exists`() {
            bggRankIndexRepository.saveAndFlush(BggRankIndex(bggId = 2200L, rank = 1))
            given(bggClient.getGameDetailsBatch(listOf(2200L))).willThrow(RuntimeException("BGG unreachable"))

            perform("$BASE_URL/overall")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should respect pagination parameters`() {
            persistGame(bggId = 2300L, name = "First Page Game")
            persistGame(bggId = 2301L, name = "Second Page Game")
            bggRankIndexRepository.saveAndFlush(BggRankIndex(bggId = 2300L, rank = 1))
            bggRankIndexRepository.saveAndFlush(BggRankIndex(bggId = 2301L, rank = 2))

            perform("$BASE_URL/overall", "page" to "1", "size" to "1")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Second Page Game")))
                .andExpect(jsonPath("$.totalElements").value(2))
        }

        @Test
        fun `should cap the page size at 50`() {
            perform("$BASE_URL/overall", "size" to "500")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.size").value(50))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/games/mechanics and /categories")
    inner class GameTermTests {

        @Test
        fun `should list the mechanics sorted by name ignoring case`() {
            persistMechanic(1L, "worker Placement")
            persistMechanic(2L, "Area Control")
            persistMechanic(3L, "Hand Management")

            perform("$BASE_URL/mechanics")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].name").value(contains("Area Control", "Hand Management", "worker Placement")))
                .andExpect(jsonPath("$[0].bggId").value(2))
                .andExpect(jsonPath("$[0].id").isNotEmpty)
        }

        @Test
        fun `should list the categories sorted by name`() {
            persistCategory(1L, "Economic")
            persistCategory(2L, "Animals")

            perform("$BASE_URL/categories")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].name").value(contains("Animals", "Economic")))
        }

        @Test
        fun `should return empty lists when nothing has been synced`() {
            perform("$BASE_URL/mechanics").andExpect(jsonPath("$.length()").value(0))
            perform("$BASE_URL/categories").andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return a mechanic id usable to filter the search`() {
            val mechanic = persistMechanic(82L, "Worker Placement")
            persistGame(bggId = 1L, name = "Agricola", mechanics = setOf(mechanic))

            perform("$BASE_URL/mechanics").andExpect(jsonPath("$[0].id").value(mechanic.id.toString()))
            perform("$BASE_URL/search", "mechanicId" to mechanic.id.toString()).andExpect(jsonPath("$.content[*].name").value(contains("Agricola")))
        }
    }
}