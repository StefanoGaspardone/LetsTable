package com.backend.integration.controllers

import com.backend.clients.BggClient
import com.backend.models.dtos.*
import com.backend.models.entities.Game
import com.backend.models.entities.UploadedFile
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FileOwnerType
import com.backend.models.enums.UserRole
import com.backend.repositories.GameRepository
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import com.backend.services.StorageService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class AdminGameControllerTest: AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var uploadedFileRepository: UploadedFileRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var bggClient: BggClient

    @MockitoBean
    private lateinit var storageService: StorageService

    companion object {
        private const val BASE_URL = "/api/v1/admin/games"
        private const val RANKS_HEADER = "id,name,yearpublished,rank,bayesaverage,average,usersrated,is_expansion,abstracts_rank,cgs_rank,childrensgames_rank,familygames_rank,partygames_rank,strategygames_rank,thematic_rank,wargames_rank"
    }

    @AfterEach
    fun cleanUp() {
        uploadedFileRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(username: String = "user-${UUID.randomUUID()}", role: UserRole = UserRole.USER): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = role,
                accountStatus = AccountStatus.ACTIVE,
            )
        )

    private fun admin(): User = persistUser(role = UserRole.ADMIN)

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistGame(bggId: Long, name: String = "Test Game", rank: Int? = null, isExpansion: Boolean = false): Game =
        gameRepository.saveAndFlush(Game(bggId = bggId, name = name, rank = rank, isExpansion = isExpansion, lastSyncedAt = Instant.now()))

    private fun persistRuleFile(gameId: UUID): UploadedFile =
        uploadedFileRepository.saveAndFlush(
            UploadedFile(
                ownerType = FileOwnerType.GAME_RULE,
                ownerId = gameId,
                fileName = "rules.pdf",
                objectKey = "game_rule/$gameId/${UUID.randomUUID()}.pdf",
                contentType = "application/pdf",
                size = 1024L,
            )
        )

    private fun bggThingItem(id: Long, name: String = "Refreshed Game") = BggThingItemXml(
        id = id,
        thumbnail = "https://example.com/thumb.jpg",
        image = "https://example.com/image.jpg",
        names = listOf(BggThingNameXml(type = "primary", value = name)),
        description = "A description",
        yearPublished = BggValueXml(value = "2024"),
        minPlayers = BggValueXml(value = "1"),
        maxPlayers = BggValueXml(value = "4"),
        playingTime = BggValueXml(value = "90"),
        type = "boardgame",
    )

    private fun stubBggGame(bggId: Long, name: String) {
        given(bggClient.getGameDetails(bggId)).willReturn(BggThingResponseXml(items = listOf(bggThingItem(id = bggId, name = name))))
        given(bggClient.getCardSetsByGame(bggId)).willReturn(CardSetsByGameResponse(cardSets = emptyList()))
    }

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User? = admin()): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    @Nested
    @DisplayName("POST /api/v1/admin/games/{bggId}/refresh")
    inner class ForceRefreshGameTests {

        @Test
        fun `should force refresh a game even when it is not stale`() {
            persistGame(bggId = 500L, name = "Old Name")
            stubBggGame(500L, "Refreshed Name")

            perform(post("$BASE_URL/500/refresh"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Refreshed Name"))
                .andExpect(jsonPath("$.bggId").value(500))

            assertThat(gameRepository.findByBggId(500L).orElseThrow().name).isEqualTo("Refreshed Name")
            assertThat(gameRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should sync a new game from BGG when it does not exist yet`() {
            stubBggGame(600L, "New Game")

            perform(post("$BASE_URL/600/refresh"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("New Game"))

            assertThat(gameRepository.findByBggId(600L)).isPresent
        }

        @Test
        fun `should return 404 when the game does not exist on BGG`() {
            given(bggClient.getGameDetails(999999L)).willReturn(BggThingResponseXml(items = emptyList()))

            perform(post("$BASE_URL/999999/refresh"))
                .andExpect(status().isNotFound)

            assertThat(gameRepository.findByBggId(999999L)).isEmpty
        }

        @Test
        fun `should return 400 when the BGG id is not a number`() {
            perform(post("$BASE_URL/abc/refresh"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            perform(post("$BASE_URL/500/refresh"), user = persistUser())
                .andExpect(status().isForbidden)

            verify(bggClient, never()).getGameDetails(500L)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(post("$BASE_URL/500/refresh"), user = null)
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/games/hot-games/refresh")
    inner class ForceRefreshHotGamesTests {

        @Test
        fun `should refresh the hot games cache`() {
            given(bggClient.getHotGames()).willReturn(BggHotResponseXml(items = listOf(BggHotItemXml(id = 10L, rank = 1, name = BggValueXml("Hot Game")))))
            given(bggClient.getGameDetailsBatch(listOf(10L))).willReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 10L, name = "Hot Game"))))

            perform(post("$BASE_URL/hot-games/refresh"))
                .andExpect(status().isNoContent)

            assertThat(gameRepository.findByBggId(10L).orElseThrow().rank).isEqualTo(1)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            perform(post("$BASE_URL/hot-games/refresh"), user = persistUser())
                .andExpect(status().isForbidden)

            verify(bggClient, never()).getHotGames()
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/games/{gameId}/rules")
    inner class ListRuleFilesTests {

        @Test
        fun `should return the rule files uploaded for a game`() {
            val game = persistGame(bggId = 700L)
            persistRuleFile(game.id!!)

            perform(get("$BASE_URL/${game.id}/rules"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("rules.pdf"))
        }

        @Test
        fun `should not return the rule files of other games`() {
            val game = persistGame(bggId = 703L)
            val other = persistGame(bggId = 704L)
            persistRuleFile(other.id!!)

            perform(get("$BASE_URL/${game.id}/rules"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return an empty list when the game has no rule files`() {
            val game = persistGame(bggId = 701L)

            perform(get("$BASE_URL/${game.id}/rules"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 404 when the game does not exist`() {
            perform(get("$BASE_URL/${UUID.randomUUID()}/rules"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the game id is not a UUID`() {
            perform(get("$BASE_URL/not-a-uuid/rules"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val game = persistGame(bggId = 702L)

            perform(get("$BASE_URL/${game.id}/rules"), user = persistUser())
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/games/{gameId}/rules/{fileId}")
    inner class DeleteRuleFileTests {

        @Test
        fun `should delete the rule file from the database and the storage`() {
            val game = persistGame(bggId = 800L)
            val file = persistRuleFile(game.id!!)

            perform(delete("$BASE_URL/${game.id}/rules/${file.id}"))
                .andExpect(status().isNoContent)

            assertThat(uploadedFileRepository.findById(file.id!!)).isEmpty
            verify(storageService).deleteObject(file.objectKey)
        }

        @Test
        fun `should return 404 when the file does not exist`() {
            val game = persistGame(bggId = 801L)

            perform(delete("$BASE_URL/${game.id}/rules/${UUID.randomUUID()}"))
                .andExpect(status().isNotFound)

            verify(storageService, never()).deleteObject(anyString())
        }

        @Test
        fun `should return 404 when the file belongs to another game`() {
            val game = persistGame(bggId = 803L)
            val other = persistGame(bggId = 804L)
            val file = persistRuleFile(other.id!!)

            perform(delete("$BASE_URL/${game.id}/rules/${file.id}"))
                .andExpect(status().isNotFound)

            assertThat(uploadedFileRepository.findById(file.id!!)).isPresent
            verify(storageService, never()).deleteObject(anyString())
        }

        @Test
        fun `should return 404 when the game does not exist`() {
            perform(delete("$BASE_URL/${UUID.randomUUID()}/rules/${UUID.randomUUID()}"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val game = persistGame(bggId = 802L)
            val file = persistRuleFile(game.id!!)

            perform(delete("$BASE_URL/${game.id}/rules/${file.id}"), user = persistUser())
                .andExpect(status().isForbidden)

            assertThat(uploadedFileRepository.findById(file.id!!)).isPresent
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/games")
    inner class ListGamesTests {

        @Test
        fun `should return the games in alphabetical order by default`() {
            persistGame(bggId = 901L, name = "Beta Game")
            persistGame(bggId = 900L, name = "Alpha Game")

            perform(get(BASE_URL))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Alpha Game", "Beta Game")))
                .andExpect(jsonPath("$.totalElements").value(2))
        }

        @Test
        fun `should filter games by a partial case-insensitive search term`() {
            persistGame(bggId = 902L, name = "Catan")
            persistGame(bggId = 903L, name = "Ark Nova")

            perform(get(BASE_URL).param("search", "cat"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Catan"))
        }

        @Test
        fun `should filter expansions`() {
            persistGame(bggId = 906L, name = "Catan")
            persistGame(bggId = 907L, name = "Catan: Seafarers", isExpansion = true)

            perform(get(BASE_URL).param("isExpansion", "true"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Catan: Seafarers")))

            perform(get(BASE_URL).param("isExpansion", "false"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Catan")))
        }

        @Test
        fun `should sort games by name descending`() {
            persistGame(bggId = 904L, name = "Alpha Game")
            persistGame(bggId = 905L, name = "Zeta Game")

            perform(get(BASE_URL).param("sort", "name-desc"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Zeta Game", "Alpha Game")))
        }

        @Test
        fun `should put games without rank last`() {
            persistGame(bggId = 908L, name = "Unranked", rank = null)
            persistGame(bggId = 909L, name = "Second", rank = 2)
            persistGame(bggId = 910L, name = "First", rank = 1)

            perform(get(BASE_URL).param("sort", "rank-asc"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("First", "Second", "Unranked")))

            perform(get(BASE_URL).param("sort", "rank-desc"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Second", "First", "Unranked")))
        }

        @Test
        fun `should paginate the results`() {
            (1..3).forEach { persistGame(bggId = 920L + it, name = "Game $it") }

            perform(get(BASE_URL).param("page", "1").param("size", "2"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Game 3")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            perform(get(BASE_URL).param("sort", "notAllowedField-asc"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when isExpansion is not a boolean`() {
            perform(get(BASE_URL).param("isExpansion", "maybe"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            perform(get(BASE_URL), user = persistUser())
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/games/{gameId}")
    inner class GetGameTests {

        @Test
        fun `should return the game details`() {
            val game = persistGame(bggId = 950L, name = "Detailed Game")

            perform(get("$BASE_URL/${game.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(game.id.toString()))
                .andExpect(jsonPath("$.bggId").value(950))
                .andExpect(jsonPath("$.name").value("Detailed Game"))
        }

        @Test
        fun `should return 404 when the game does not exist`() {
            perform(get("$BASE_URL/${UUID.randomUUID()}"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the game id is not a UUID`() {
            perform(get("$BASE_URL/not-a-uuid"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val game = persistGame(bggId = 951L)

            perform(get("$BASE_URL/${game.id}"), user = persistUser())
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/games/rank-index/upload")
    inner class UploadRankIndexTests {

        private fun file(content: String, filename: String = "ranks.csv", contentType: String = "text/csv") =
            MockMultipartFile("file", filename, contentType, content.toByteArray())

        private fun upload(file: MockMultipartFile, user: User? = admin()) =
            perform(multipart("$BASE_URL/rank-index/upload").file(file), user)

        @Test
        fun `should upload a valid CSV and return the number of entries indexed`() {
            val csv = """
                $RANKS_HEADER
                224517,"Brass: Birmingham",2018,1,8.39028,8.55979,60175,0,,,,,,1,,
                342942,"Ark Nova",2021,2,8.35429,8.53813,63067,0,,,,,,2,,
            """.trimIndent()

            upload(file(csv))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.count").value(2))
        }

        @Test
        fun `should accept a CSV that starts with a byte order mark`() {
            val csv = "﻿$RANKS_HEADER\n224517,\"Brass: Birmingham\",2018,1,8.39028,8.55979,60175,0,,,,,,1,,"

            upload(file(csv))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.count").value(1))
        }

        @Test
        fun `should count a game listed twice only once`() {
            val csv = """
                $RANKS_HEADER
                224517,"Brass: Birmingham",2018,1,8.39028,8.55979,60175,0,,,,,,1,,
                224517,"Brass: Birmingham",2018,1,8.39028,8.55979,60175,0,,,,,,1,,
            """.trimIndent()

            upload(file(csv))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.count").value(1))
        }

        @Test
        fun `should return 400 when the uploaded file has no valid rank entries`() {
            upload(file("not,a,valid,csv,header\n1,2,3,4,5"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the file is not a CSV`() {
            upload(file("\u0089PNG", filename = "ranks.png", contentType = "image/png"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the file part is missing`() {
            perform(multipart("$BASE_URL/rank-index/upload"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            upload(file("$RANKS_HEADER\n1,Game,2020,1,,,,0,,,,,,,,"), user = persistUser())
                .andExpect(status().isForbidden)
        }
    }
}