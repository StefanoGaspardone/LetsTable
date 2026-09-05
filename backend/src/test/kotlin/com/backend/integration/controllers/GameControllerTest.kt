package com.backend.integration.controllers

import com.backend.clients.BggClient
import com.backend.models.dtos.*
import com.backend.models.entities.ExpansionRef
import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.GameRepository
import com.backend.repositories.GameSleeveRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

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
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var bggClient: BggClient

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
    ): Game =
        gameRepository.saveAndFlush(
            Game(
                bggId = bggId,
                name = name,
                lastSyncedAt = lastSyncedAt,
                isExpansion = isExpansion,
                baseGameBggId = baseGameBggId,
                expansionRefs = expansionRefs,
            )
        )

    private fun bggThingItem(
        id: Long,
        name: String = "SETI",
        type: String = "boardgame",
    ) = BggThingItemXml(
        id = id,
        thumbnail = "https://example.com/thumb.jpg",
        image = "https://example.com/image.jpg",
        names = listOf(BggThingNameXml(type = "primary", value = name)),
        description = "A description",
        yearPublished = BggValueXml(value = "2024"),
        minPlayers = BggValueXml(value = "1"),
        maxPlayers = BggValueXml(value = "4"),
        playingTime = BggValueXml(value = "90"),
        type = type,
    )

    private fun emptyCardSetsResponse() = CardSetsByGameResponse(cardSets = emptyList())

    @AfterEach
    fun cleanUp() {
        gameSleeveRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/games/search
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/games/search")
    inner class SearchTests {

        @Test
        fun `should search and enrich results with batch details`() {
            val user = persistUser()
            val searchResponse = BggSearchResponseXml(
                items = listOf(
                    BggSearchItemXml(id = 100L, name = BggNameXml(value = "Ark Nova"), yearPublished = BggValueXml(value = "2021"))
                )
            )
            whenever(bggClient.searchGames("ark")).thenReturn(searchResponse)
            whenever(bggClient.getGameDetailsBatch(listOf(100L)))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 100L, name = "Ark Nova"))))
            whenever(bggClient.getCardSetsByGame(100L)).thenReturn(emptyCardSetsResponse())

            mockMvc.perform(
                get("/api/v1/games/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "ark")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Ark Nova"))
                .andExpect(jsonPath("$.content[0].bggId").value(100))

            assertThat(gameRepository.findByBggId(100L)).isPresent
        }

        @Test
        fun `should fall back to lightweight results when batch enrichment fails`() {
            val user = persistUser()
            val searchResponse = BggSearchResponseXml(
                items = listOf(
                    BggSearchItemXml(id = 200L, name = BggNameXml(value = "Fallback Game"), yearPublished = BggValueXml(value = "2020"))
                )
            )
            whenever(bggClient.searchGames("fallback")).thenReturn(searchResponse)
            whenever(bggClient.getGameDetailsBatch(listOf(200L))).thenThrow(RuntimeException("BGG unreachable"))

            mockMvc.perform(
                get("/api/v1/games/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "fallback")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Fallback Game"))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
        }

        @Test
        fun `should return an empty page when BGG has no results`() {
            val user = persistUser()
            whenever(bggClient.searchGames("nothing")).thenReturn(BggSearchResponseXml(items = emptyList()))

            mockMvc.perform(
                get("/api/v1/games/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "nothing")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/games/hot
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/games/hot")
    inner class HotGamesTests {

        @Test
        fun `should return hot games cached in the database ordered by rank`() {
            val user = persistUser()
            val gameA = persistGame(bggId = 1L, name = "First").apply { rank = 1 }
            val gameB = persistGame(bggId = 2L, name = "Second").apply { rank = 2 }
            gameRepository.saveAndFlush(gameA)
            gameRepository.saveAndFlush(gameB)

            mockMvc.perform(
                get("/api/v1/games/hot")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("First"))
                .andExpect(jsonPath("$.content[1].name").value("Second"))
        }

        @Test
        fun `should not include games without a rank`() {
            val user = persistUser()
            val ranked = persistGame(bggId = 10L, name = "Ranked").apply { rank = 1 }
            gameRepository.saveAndFlush(ranked)
            persistGame(bggId = 11L, name = "Not Ranked")

            mockMvc.perform(
                get("/api/v1/games/hot")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Ranked"))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/games/{bggId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/games/{bggId}")
    inner class GetGameTests {

        @Test
        fun `should sync from BGG when game is not cached`() {
            val user = persistUser()
            whenever(bggClient.getGameDetails(500L))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 500L, name = "New Game"))))
            whenever(bggClient.getCardSetsByGame(500L)).thenReturn(emptyCardSetsResponse())

            mockMvc.perform(
                get("/api/v1/games/500")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("New Game"))
                .andExpect(jsonPath("$.bggId").value(500))

            assertThat(gameRepository.findByBggId(500L)).isPresent
        }

        @Test
        fun `should return cached game without hitting BGG when not stale`() {
            val user = persistUser()
            persistGame(bggId = 600L, name = "Fresh Cache", lastSyncedAt = Instant.now())

            mockMvc.perform(
                get("/api/v1/games/600")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Fresh Cache"))
        }

        @Test
        fun `should re-sync when cached game is stale`() {
            val user = persistUser()
            persistGame(bggId = 700L, name = "Stale Game", lastSyncedAt = Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS))
            whenever(bggClient.getGameDetails(700L))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 700L, name = "Refreshed Game"))))
            whenever(bggClient.getCardSetsByGame(700L)).thenReturn(emptyCardSetsResponse())

            mockMvc.perform(
                get("/api/v1/games/700")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Refreshed Game"))
        }

        @Test
        fun `should return 404 when game does not exist on BGG`() {
            val user = persistUser()
            whenever(bggClient.getGameDetails(999999L)).thenReturn(BggThingResponseXml(items = emptyList()))

            mockMvc.perform(
                get("/api/v1/games/999999")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should resolve base game when the requested game is an expansion`() {
            val user = persistUser()
            persistGame(bggId = 802L, name = "Base Game")
            persistGame(bggId = 801L, name = "Expansion", isExpansion = true, baseGameBggId = 802L, lastSyncedAt = Instant.now())

            mockMvc.perform(
                get("/api/v1/games/801")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Expansion"))
                .andExpect(jsonPath("$.baseGame.name").value("Base Game"))
        }

        @Test
        fun `should reflect inCollection status for the authenticated user`() {
            val user = persistUser()
            persistGame(bggId = 900L, name = "Owned Game")

            mockMvc.perform(
                get("/api/v1/games/900")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(false))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/games/{bggId}/expansions
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/games/{bggId}/expansions")
    inner class GetExpansionsTests {

        @Test
        fun `should return synced expansions sorted by year then name`() {
            val user = persistUser()
            persistGame(
                bggId = 1000L,
                name = "Base With Expansions",
                expansionRefs = listOf(ExpansionRef(1001L, "Expansion B"), ExpansionRef(1002L, "Expansion A")),
            )
            whenever(bggClient.getGameDetails(1001L))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 1001L, name = "Expansion B", type = "boardgameexpansion").copy(yearPublished = BggValueXml(value = "2022")))))
            whenever(bggClient.getGameDetails(1002L))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 1002L, name = "Expansion A", type = "boardgameexpansion").copy(yearPublished = BggValueXml(value = "2021")))))
            whenever(bggClient.getCardSetsByGame(1001L)).thenReturn(emptyCardSetsResponse())
            whenever(bggClient.getCardSetsByGame(1002L)).thenReturn(emptyCardSetsResponse())

            mockMvc.perform(
                get("/api/v1/games/1000/expansions")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Expansion A"))
                .andExpect(jsonPath("$.content[1].name").value("Expansion B"))
        }

        @Test
        fun `should return 404 when base game does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/games/123456/expansions")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return an empty page when game has no expansions`() {
            val user = persistUser()
            persistGame(bggId = 1100L, name = "No Expansions Game", expansionRefs = emptyList())

            mockMvc.perform(
                get("/api/v1/games/1100/expansions")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should skip an expansion that fails to sync and still return the others`() {
            val user = persistUser()
            persistGame(
                bggId = 1200L,
                name = "Base With Partial Expansions",
                expansionRefs = listOf(ExpansionRef(1201L, "Good Expansion"), ExpansionRef(1202L, "Broken Expansion")),
            )
            whenever(bggClient.getGameDetails(1201L))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 1201L, name = "Good Expansion", type = "boardgameexpansion"))))
            whenever(bggClient.getCardSetsByGame(1201L)).thenReturn(emptyCardSetsResponse())
            whenever(bggClient.getGameDetails(1202L)).thenThrow(RuntimeException("BGG timeout"))

            mockMvc.perform(
                get("/api/v1/games/1200/expansions")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Good Expansion"))
        }
    }
}