package com.backend.integration.controllers

import com.backend.clients.BggClient
import com.backend.models.dtos.*
import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.entities.UploadedFile
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FileOwnerType
import com.backend.models.enums.UserRole
import com.backend.repositories.GameRepository
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import com.backend.services.StorageService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class AdminGameControllerTest : AbstractIntegrationTest() {

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

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistGame(bggId: Long, name: String = "Test Game", rank: Int? = null): Game =
        gameRepository.saveAndFlush(Game(bggId = bggId, name = name, rank = rank, lastSyncedAt = Instant.now()))

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

    private fun emptyCardSetsResponse() = CardSetsByGameResponse(cardSets = emptyList())

    @AfterEach
    fun cleanUp() {
        uploadedFileRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/admin/games/{bggId}/refresh
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/admin/games/{bggId}/refresh")
    inner class ForceRefreshGameTests {

        @Test
        fun `should force refresh a game even when it is not stale`() {
            val admin = persistUser(role = UserRole.ADMIN)
            persistGame(bggId = 500L, name = "Old Name")

            whenever(bggClient.getGameDetails(500L))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 500L, name = "Refreshed Name"))))
            whenever(bggClient.getCardSetsByGame(500L)).thenReturn(emptyCardSetsResponse())

            mockMvc.perform(
                post("/api/v1/admin/games/500/refresh")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Refreshed Name"))
                .andExpect(jsonPath("$.bggId").value(500))

            val updated = gameRepository.findByBggId(500L).orElseThrow()
            assertThat(updated.name).isEqualTo("Refreshed Name")
        }

        @Test
        fun `should sync a new game from BGG when it does not exist yet`() {
            val admin = persistUser(role = UserRole.ADMIN)

            whenever(bggClient.getGameDetails(600L))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 600L, name = "New Game"))))
            whenever(bggClient.getCardSetsByGame(600L)).thenReturn(emptyCardSetsResponse())

            mockMvc.perform(
                post("/api/v1/admin/games/600/refresh")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("New Game"))

            assertThat(gameRepository.findByBggId(600L)).isPresent
        }

        @Test
        fun `should return 404 when the game does not exist on BGG`() {
            val admin = persistUser(role = UserRole.ADMIN)

            whenever(bggClient.getGameDetails(999999L)).thenReturn(BggThingResponseXml(items = emptyList()))

            mockMvc.perform(
                post("/api/v1/admin/games/999999/refresh")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser()

            mockMvc.perform(
                post("/api/v1/admin/games/500/refresh")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(post("/api/v1/admin/games/500/refresh")).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/admin/games/hot-games/refresh
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/admin/games/hot-games/refresh")
    inner class ForceRefreshHotGamesTests {

        @Test
        fun `should refresh the hot games cache`() {
            val admin = persistUser(role = UserRole.ADMIN)

            whenever(bggClient.getHotGames()).thenReturn(
                BggHotResponseXml(items = listOf(BggHotItemXml(id = 10L, rank = 1, name = BggValueXml("Hot Game"))))
            )
            whenever(bggClient.getGameDetailsBatch(listOf(10L)))
                .thenReturn(BggThingResponseXml(items = listOf(bggThingItem(id = 10L, name = "Hot Game"))))

            mockMvc.perform(
                post("/api/v1/admin/games/hot-games/refresh")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isNoContent)

            val updated = gameRepository.findByBggId(10L).orElseThrow()
            assertThat(updated.rank).isEqualTo(1)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser()

            mockMvc.perform(
                post("/api/v1/admin/games/hot-games/refresh")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/admin/games/{gameId}/rules
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/admin/games/{gameId}/rules")
    inner class ListRuleFilesTests {

        @Test
        fun `should return the rule files uploaded for a game`() {
            val admin = persistUser(role = UserRole.ADMIN)
            val game = persistGame(bggId = 700L)
            persistRuleFile(game.id!!)

            mockMvc.perform(
                get("/api/v1/admin/games/${game.id}/rules")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("rules.pdf"))
        }

        @Test
        fun `should return an empty list when the game has no rule files`() {
            val admin = persistUser(role = UserRole.ADMIN)
            val game = persistGame(bggId = 701L)

            mockMvc.perform(
                get("/api/v1/admin/games/${game.id}/rules")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 404 when the game does not exist`() {
            val admin = persistUser(role = UserRole.ADMIN)

            mockMvc.perform(
                get("/api/v1/admin/games/${UUID.randomUUID()}/rules")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser()
            val game = persistGame(bggId = 702L)

            mockMvc.perform(
                get("/api/v1/admin/games/${game.id}/rules")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/admin/games/{gameId}/rules/{fileId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/admin/games/{gameId}/rules/{fileId}")
    inner class DeleteRuleFileTests {

        @Test
        fun `should delete the rule file`() {
            val admin = persistUser(role = UserRole.ADMIN)
            val game = persistGame(bggId = 800L)
            val file = persistRuleFile(game.id!!)

            doNothing().whenever(storageService).deleteObject(file.objectKey)

            mockMvc.perform(
                delete("/api/v1/admin/games/${game.id}/rules/${file.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isNoContent)

            assertThat(uploadedFileRepository.findById(file.id!!)).isEmpty
        }

        @Test
        fun `should return 404 when the file does not exist`() {
            val admin = persistUser(role = UserRole.ADMIN)
            val game = persistGame(bggId = 801L)

            mockMvc.perform(
                delete("/api/v1/admin/games/${game.id}/rules/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(admin))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when requested by a non-admin user`() {
            val user = persistUser()
            val game = persistGame(bggId = 802L)
            val file = persistRuleFile(game.id!!)

            mockMvc.perform(
                delete("/api/v1/admin/games/${game.id}/rules/${file.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isForbidden)
        }
    }
}