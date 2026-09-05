package com.backend.integration.controllers

import com.backend.models.entities.*
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.*
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.util.*

@AutoConfigureMockMvc
class CollectionControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var collectionItemRepository: CollectionItemRepository

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @Autowired
    private lateinit var matchPlayerRepository: MatchPlayerRepository

    @Autowired
    private lateinit var jwtService: JwtService

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

    private fun tokenFor(user: User): String =
        jwtService.generateAccessToken(user.id!!, user.role.name)

    private fun authHeader(user: User): String = "Bearer ${tokenFor(user)}"

    private fun persistGame(
        bggId: Long = (1..1_000_000).random().toLong(),
        name: String = "Test Game",
        isExpansion: Boolean? = false,
    ): Game =
        gameRepository.saveAndFlush(
            Game(
                bggId = bggId,
                name = name,
                lastSyncedAt = Instant.now(),
                isExpansion = isExpansion,
            )
        )

    private fun persistCollectionItem(user: User, game: Game): CollectionItem =
        collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = game))

    private fun persistMatchCreatedBy(user: User, game: Game): Match =
        matchRepository.saveAndFlush(
            Match(
                game = game,
                createdBy = user,
                playedAt = LocalDate.now(),
            )
        )

    private fun persistMatchWithPlayer(creator: User, player: User, game: Game): Match {
        val match = matchRepository.saveAndFlush(
            Match(
                game = game,
                createdBy = creator,
                playedAt = LocalDate.now(),
            )
        )
        matchPlayerRepository.saveAndFlush(
            MatchPlayer(match = match, user = player)
        )
        return match
    }

    @AfterEach
    fun cleanUp() {
        matchPlayerRepository.deleteAll()
        matchRepository.deleteAll()
        collectionItemRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/collection
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/collection")
    inner class AddToCollectionTests {

        @Test
        fun `should add game to collection and return 201`() {
            val user = persistUser()
            val game = persistGame()
            val payload = """{"gameId":"${game.id}"}"""

            mockMvc.perform(
                post("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.game.id").value(game.id.toString()))

            assertThat(collectionItemRepository.existsByUserIdAndGameId(user.id!!, game.id!!)).isTrue()
        }

        @Test
        fun `should return 404 when game does not exist`() {
            val user = persistUser()
            val payload = """{"gameId":"${UUID.randomUUID()}"}"""

            mockMvc.perform(
                post("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 409 when game already in collection`() {
            val user = persistUser()
            val game = persistGame()
            persistCollectionItem(user, game)

            val payload = """{"gameId":"${game.id}"}"""

            mockMvc.perform(
                post("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isConflict)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val game = persistGame()
            val payload = """{"gameId":"${game.id}"}"""

            mockMvc.perform(
                post("/api/v1/collection")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/collection/{itemId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/collection/{itemId}")
    inner class RemoveFromCollectionTests {

        @Test
        fun `should remove item and return 204 when user owns it`() {
            val user = persistUser()
            val game = persistGame()
            val item = persistCollectionItem(user, game)

            mockMvc.perform(
                delete("/api/v1/collection/${item.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNoContent)

            assertThat(collectionItemRepository.findById(item.id!!)).isEmpty()
        }

        @Test
        fun `should return 404 when item does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/collection/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when user does not own the item`() {
            val owner = persistUser(username = "owner")
            val otherUser = persistUser(username = "intruder")
            val game = persistGame()
            val item = persistCollectionItem(owner, game)

            mockMvc.perform(
                delete("/api/v1/collection/${item.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
            ).andExpect(status().isForbidden)

            assertThat(collectionItemRepository.findById(item.id!!)).isPresent
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/collection
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/collection")
    inner class ListCollectionTests {

        @Test
        fun `should return only the current user's collection items`() {
            val user = persistUser()
            val otherUser = persistUser(username = "someone-else")
            val game1 = persistGame(name = "Ark Nova")
            val game2 = persistGame(name = "Dune Imperium")
            val otherGame = persistGame(name = "Not Mine")

            persistCollectionItem(user, game1)
            persistCollectionItem(user, game2)
            persistCollectionItem(otherUser, otherGame)

            mockMvc.perform(
                get("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
        }

        @Test
        fun `should filter by gameName`() {
            val user = persistUser()
            val arkNova = persistGame(name = "Ark Nova")
            val duneImperium = persistGame(name = "Dune Imperium")
            persistCollectionItem(user, arkNova)
            persistCollectionItem(user, duneImperium)

            mockMvc.perform(
                get("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("gameName", "ark")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].game.name").value("Ark Nova"))
        }

        @Test
        fun `should filter by isExpansion`() {
            val user = persistUser()
            val baseGame = persistGame(name = "Base Game", isExpansion = false)
            val expansion = persistGame(name = "Expansion Pack", isExpansion = true)
            persistCollectionItem(user, baseGame)
            persistCollectionItem(user, expansion)

            mockMvc.perform(
                get("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("isExpansion", "true")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].game.name").value("Expansion Pack"))
        }

        @Test
        fun `should filter by played true when user created a match for the game`() {
            val user = persistUser()
            val playedGame = persistGame(name = "Played Game")
            val unplayedGame = persistGame(name = "Unplayed Game")
            persistCollectionItem(user, playedGame)
            persistCollectionItem(user, unplayedGame)
            persistMatchCreatedBy(user, playedGame)

            mockMvc.perform(
                get("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("played", "true")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].game.name").value("Played Game"))
        }

        @Test
        fun `should filter by played true when user is only a participant, not the creator`() {
            val user = persistUser(username = "participant")
            val creator = persistUser(username = "creator")
            val playedGame = persistGame(name = "Played As Participant")
            persistCollectionItem(user, playedGame)
            persistMatchWithPlayer(creator = creator, player = user, game = playedGame)

            mockMvc.perform(
                get("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("played", "true")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should filter by played false excluding games with a match`() {
            val user = persistUser()
            val playedGame = persistGame(name = "Played Game")
            val unplayedGame = persistGame(name = "Unplayed Game")
            persistCollectionItem(user, playedGame)
            persistCollectionItem(user, unplayedGame)
            persistMatchCreatedBy(user, playedGame)

            mockMvc.perform(
                get("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("played", "false")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].game.name").value("Unplayed Game"))
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("sort", "notAllowedField-asc")
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return empty page when user has no collection items`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/collection")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/collection/status
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/collection/status")
    inner class GetStatusTests {

        @Test
        fun `should return inCollection true with itemId when game is present`() {
            val user = persistUser()
            val game = persistGame()
            val item = persistCollectionItem(user, game)

            mockMvc.perform(
                get("/api/v1/collection/status")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("gameId", game.id.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(true))
                .andExpect(jsonPath("$.itemId").value(item.id.toString()))
        }

        @Test
        fun `should return inCollection false with null itemId when game is absent`() {
            val user = persistUser()
            val game = persistGame()

            mockMvc.perform(
                get("/api/v1/collection/status")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("gameId", game.id.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(false))
                .andExpect(jsonPath("$.itemId").doesNotExist())
        }

        @Test
        fun `should not see another user's collection status as own`() {
            val owner = persistUser(username = "owner")
            val otherUser = persistUser(username = "checker")
            val game = persistGame()
            persistCollectionItem(owner, game)

            mockMvc.perform(
                get("/api/v1/collection/status")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
                    .param("gameId", game.id.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(false))
        }
    }
}