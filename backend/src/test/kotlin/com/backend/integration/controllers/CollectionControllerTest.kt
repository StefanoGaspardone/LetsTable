package com.backend.integration.controllers

import com.backend.models.entities.CollectionItem
import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

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

    private var nextBggId = 1L

    companion object {
        private const val BASE_URL = "/api/v1/collection"
    }

    @AfterEach
    fun cleanUp() {
        matchPlayerRepository.deleteAll()
        matchRepository.deleteAll()
        collectionItemRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(username: String = "stefano", role: UserRole = UserRole.USER): User =
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

    private fun persistGame(name: String = "Test Game", isExpansion: Boolean? = false): Game =
        gameRepository.saveAndFlush(Game(bggId = nextBggId++, name = name, lastSyncedAt = Instant.now(), isExpansion = isExpansion))

    private fun persistCollectionItem(user: User, game: Game): CollectionItem =
        collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = game))

    private fun persistMatchCreatedBy(user: User, game: Game): Match =
        matchRepository.saveAndFlush(Match(game = game, createdBy = user, playedAt = Instant.now()))

    private fun persistMatchWithPlayer(creator: User, player: User, game: Game): Match =
        persistMatchCreatedBy(creator, game).also { matchPlayerRepository.saveAndFlush(MatchPlayer(match = it, user = player)) }

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User?): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    private fun add(user: User?, body: String) =
        perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body), user)

    private fun list(user: User, vararg params: Pair<String, String>) =
        perform(get(BASE_URL).apply { params.forEach { (key, value) -> param(key, value) } }, user)

    private fun collectionStatus(user: User, gameId: String) =
        perform(get("$BASE_URL/status").param("gameId", gameId), user)

    @Nested
    @DisplayName("POST /api/v1/collection")
    inner class AddToCollectionTests {

        @Test
        fun `should add game to collection and return 201`() {
            val user = persistUser()
            val game = persistGame(name = "Ark Nova")

            add(user, """{"gameId":"${game.id}"}""")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.game.id").value(game.id.toString()))
                .andExpect(jsonPath("$.game.name").value("Ark Nova"))

            assertThat(collectionItemRepository.existsByUserIdAndGameId(user.id!!, game.id!!)).isTrue()
        }

        @Test
        fun `should show the added game in the list and in the status`() {
            val user = persistUser()
            val game = persistGame(name = "Ark Nova")

            add(user, """{"gameId":"${game.id}"}""").andExpect(status().isCreated)

            list(user).andExpect(jsonPath("$.content[*].game.name").value(contains("Ark Nova")))
            collectionStatus(user, game.id.toString()).andExpect(jsonPath("$.inCollection").value(true))
        }

        @Test
        fun `should let two users own the same game`() {
            val first = persistUser(username = "first")
            val second = persistUser(username = "second")
            val game = persistGame()
            persistCollectionItem(first, game)

            add(second, """{"gameId":"${game.id}"}""").andExpect(status().isCreated)

            assertThat(collectionItemRepository.findAll()).hasSize(2)
        }

        @Test
        fun `should return 404 when game does not exist`() {
            val user = persistUser()

            add(user, """{"gameId":"${UUID.randomUUID()}"}""").andExpect(status().isNotFound)

            assertThat(collectionItemRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 409 when game already in collection`() {
            val user = persistUser()
            val game = persistGame()
            persistCollectionItem(user, game)

            add(user, """{"gameId":"${game.id}"}""").andExpect(status().isConflict)

            assertThat(collectionItemRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should return 400 when gameId is missing`() {
            add(persistUser(), "{}").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when gameId is not a UUID`() {
            add(persistUser(), """{"gameId":"not-a-uuid"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 for a malformed body`() {
            add(persistUser(), "{ not json").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val game = persistGame()

            add(null, """{"gameId":"${game.id}"}""").andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/collection/{itemId}")
    inner class RemoveFromCollectionTests {

        @Test
        fun `should remove item and return 204 when user owns it`() {
            val user = persistUser()
            val game = persistGame()
            val item = persistCollectionItem(user, game)

            perform(delete("$BASE_URL/${item.id}"), user).andExpect(status().isNoContent)

            assertThat(collectionItemRepository.findById(item.id!!)).isEmpty()
            assertThat(gameRepository.findById(game.id!!)).isPresent
        }

        @Test
        fun `should let the user add the game again after removing it`() {
            val user = persistUser()
            val game = persistGame()
            val item = persistCollectionItem(user, game)

            perform(delete("$BASE_URL/${item.id}"), user).andExpect(status().isNoContent)

            add(user, """{"gameId":"${game.id}"}""").andExpect(status().isCreated)
        }

        @Test
        fun `should return 404 when item does not exist`() {
            perform(delete("$BASE_URL/${UUID.randomUUID()}"), persistUser()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when user does not own the item`() {
            val owner = persistUser(username = "owner")
            val intruder = persistUser(username = "intruder")
            val item = persistCollectionItem(owner, persistGame())

            perform(delete("$BASE_URL/${item.id}"), intruder)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("You do not own this collection item"))

            assertThat(collectionItemRepository.findById(item.id!!)).isPresent
        }

        @Test
        fun `should return 400 when the item id is not a UUID`() {
            perform(delete("$BASE_URL/not-a-uuid"), persistUser()).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val item = persistCollectionItem(persistUser(), persistGame())

            perform(delete("$BASE_URL/${item.id}"), null).andExpect(status().isForbidden)

            assertThat(collectionItemRepository.findById(item.id!!)).isPresent
        }
    }

    @Nested
    @DisplayName("GET /api/v1/collection")
    inner class ListCollectionTests {

        @Test
        fun `should return only the current user's collection items`() {
            val user = persistUser()
            val other = persistUser(username = "someone-else")
            persistCollectionItem(user, persistGame(name = "Ark Nova"))
            persistCollectionItem(user, persistGame(name = "Dune Imperium"))
            persistCollectionItem(other, persistGame(name = "Not Mine"))

            list(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
        }

        @Test
        fun `should filter by a partial case-insensitive game name`() {
            val user = persistUser()
            persistCollectionItem(user, persistGame(name = "Ark Nova"))
            persistCollectionItem(user, persistGame(name = "Dune Imperium"))

            list(user, "gameName" to "ARK")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Ark Nova")))
        }

        @Test
        fun `should filter by isExpansion`() {
            val user = persistUser()
            persistCollectionItem(user, persistGame(name = "Base Game", isExpansion = false))
            persistCollectionItem(user, persistGame(name = "Expansion Pack", isExpansion = true))

            list(user, "isExpansion" to "true")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Expansion Pack")))

            list(user, "isExpansion" to "false")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Base Game")))
        }

        @Test
        fun `should filter by played true when user created a match for the game`() {
            val user = persistUser()
            val played = persistGame(name = "Played Game")
            persistCollectionItem(user, played)
            persistCollectionItem(user, persistGame(name = "Unplayed Game"))
            persistMatchCreatedBy(user, played)

            list(user, "played" to "true")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Played Game")))
        }

        @Test
        fun `should filter by played true when user is only a participant, not the creator`() {
            val user = persistUser(username = "participant")
            val creator = persistUser(username = "creator")
            val played = persistGame(name = "Played As Participant")
            persistCollectionItem(user, played)
            persistMatchWithPlayer(creator = creator, player = user, game = played)

            list(user, "played" to "true")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Played As Participant")))
        }

        @Test
        fun `should not count matches of other users as played`() {
            val user = persistUser()
            val stranger = persistUser(username = "stranger")
            val game = persistGame(name = "Owned Game")
            persistCollectionItem(user, game)
            persistMatchCreatedBy(stranger, game)

            list(user, "played" to "true")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))

            list(user, "played" to "false")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Owned Game")))
        }

        @Test
        fun `should filter by played false excluding games with a match`() {
            val user = persistUser()
            val played = persistGame(name = "Played Game")
            persistCollectionItem(user, played)
            persistCollectionItem(user, persistGame(name = "Unplayed Game"))
            persistMatchCreatedBy(user, played)

            list(user, "played" to "false")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Unplayed Game")))
        }

        @Test
        fun `should combine the filters`() {
            val user = persistUser()
            persistCollectionItem(user, persistGame(name = "Catan", isExpansion = false))
            persistCollectionItem(user, persistGame(name = "Catan: Seafarers", isExpansion = true))
            persistCollectionItem(user, persistGame(name = "Wingspan: Europe", isExpansion = true))

            list(user, "gameName" to "catan", "isExpansion" to "true")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Catan: Seafarers")))
        }

        @Test
        fun `should sort by game name`() {
            val user = persistUser()
            persistCollectionItem(user, persistGame(name = "Wingspan"))
            persistCollectionItem(user, persistGame(name = "Azul"))
            persistCollectionItem(user, persistGame(name = "Catan"))

            list(user, "sort" to "game.name-asc")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Azul", "Catan", "Wingspan")))

            list(user, "sort" to "game.name-desc")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Wingspan", "Catan", "Azul")))
        }

        @Test
        fun `should paginate the results`() {
            val user = persistUser()
            listOf("Azul", "Catan", "Wingspan").forEach { persistCollectionItem(user, persistGame(name = it)) }

            list(user, "sort" to "game.name-asc", "page" to "1", "size" to "2")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Wingspan")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            list(persistUser(), "sort" to "notAllowedField-asc").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when played is not a boolean`() {
            list(persistUser(), "played" to "maybe").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return empty page when user has no collection items`() {
            list(persistUser())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get(BASE_URL), null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/collection/status")
    inner class GetStatusTests {

        @Test
        fun `should return inCollection true with itemId when game is present`() {
            val user = persistUser()
            val game = persistGame()
            val item = persistCollectionItem(user, game)

            collectionStatus(user, game.id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(true))
                .andExpect(jsonPath("$.itemId").value(item.id.toString()))
        }

        @Test
        fun `should return inCollection false with null itemId when game is absent`() {
            val user = persistUser()

            collectionStatus(user, persistGame().id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(false))
                .andExpect(jsonPath("$.itemId").doesNotExist())
        }

        @Test
        fun `should return false for a game that does not exist`() {
            collectionStatus(persistUser(), UUID.randomUUID().toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(false))
        }

        @Test
        fun `should not see another user's collection status as own`() {
            val owner = persistUser(username = "owner")
            val checker = persistUser(username = "checker")
            val game = persistGame()
            persistCollectionItem(owner, game)

            collectionStatus(checker, game.id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inCollection").value(false))
        }

        @Test
        fun `should return 400 when gameId is missing`() {
            perform(get("$BASE_URL/status"), persistUser()).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when gameId is not a UUID`() {
            collectionStatus(persistUser(), "not-a-uuid").andExpect(status().isBadRequest)
        }
    }
}