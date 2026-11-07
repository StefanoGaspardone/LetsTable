package com.backend.integration.controllers

import com.backend.models.entities.ExpansionRef
import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.MatchTeam
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.MatchTeamRepository
import com.backend.repositories.UserAchievementRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@AutoConfigureMockMvc
class MatchControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @Autowired
    private lateinit var matchTeamRepository: MatchTeamRepository

    @Autowired
    private lateinit var matchPlayerRepository: MatchPlayerRepository

    @Autowired
    private lateinit var userAchievementRepository: UserAchievementRepository

    @Autowired
    private lateinit var jwtService: JwtService

    private val jsonMapper = JsonMapper.builder().build()
    private var nextBggId = 1L

    companion object {
        private const val BASE_URL = "/api/v1/matches"
    }

    @AfterEach
    fun cleanUp() {
        matchPlayerRepository.deleteAll()
        matchTeamRepository.deleteAll()
        matchRepository.deleteAll()
        gameRepository.deleteAll()
        userAchievementRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)

    private fun persistUser(username: String = "stefano", accountStatus: AccountStatus = AccountStatus.ACTIVE): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = UserRole.USER,
                accountStatus = accountStatus,
            )
        )

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistGame(name: String = "Test Game", bggId: Long = nextBggId++, expansionRefs: List<ExpansionRef> = emptyList(), isExpansion: Boolean = false): Game =
        gameRepository.saveAndFlush(Game(bggId = bggId, name = name, lastSyncedAt = Instant.now(), expansionRefs = expansionRefs, isExpansion = isExpansion))

    private fun persistMatch(
        game: Game,
        createdBy: User,
        isTeamBased: Boolean = false,
        playedAt: Instant = Instant.now(),
        durationMinutes: Int? = 30,
    ): Match =
        matchRepository.saveAndFlush(Match(game = game, createdBy = createdBy, isTeamBased = isTeamBased, playedAt = playedAt, durationMinutes = durationMinutes))

    private fun persistPlayer(match: Match, user: User? = null, guestName: String? = null, isWinner: Boolean = false, score: Int = 0): MatchPlayer =
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, user = user, guestName = guestName, color = "red", isWinner = isWinner, score = score))

    private fun persistTeamWithPlayer(match: Match, user: User, isWinner: Boolean = false): MatchTeam {
        val team = matchTeamRepository.saveAndFlush(MatchTeam(match = match, color = "blue", isWinner = isWinner))
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, team = team, user = user))
        return team
    }

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User?): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    private fun sendJson(request: AbstractMockHttpServletRequestBuilder<*>, user: User?, body: String): ResultActions =
        perform(request.contentType(MediaType.APPLICATION_JSON).content(body), user)

    private fun player(userId: UUID? = null, guestName: String? = null, score: Int = 0, isWinner: Boolean = false, color: String = "red"): String =
        listOfNotNull(
            userId?.let { "\"userId\":\"$it\"" },
            guestName?.let { "\"guestName\":\"$it\"" },
            "\"color\":\"$color\"",
            "\"score\":$score",
            "\"isWinner\":$isWinner",
        ).joinToString(",", "{", "}")

    private fun team(name: String, score: Int, isWinner: Boolean, vararg members: String): String =
        """{"name":"$name","color":"blue","score":$score,"isWinner":$isWinner,"players":[${members.joinToString(",")}]}"""

    private fun member(userId: UUID? = null, guestName: String? = null): String =
        listOfNotNull(userId?.let { "\"userId\":\"$it\"" }, guestName?.let { "\"guestName\":\"$it\"" }).joinToString(",", "{", "}")

    private fun matchBody(
        gameId: UUID?,
        players: List<String>? = null,
        teams: List<String>? = null,
        isTeamBased: Boolean = teams != null,
        playedAt: LocalDate = today(),
        durationMinutes: Int? = null,
        place: String? = null,
        expansionIds: List<UUID> = emptyList(),
    ): String = listOfNotNull(
        gameId?.let { "\"gameId\":\"$it\"" },
        "\"playedAt\":\"$playedAt\"",
        "\"isTeamBased\":$isTeamBased",
        durationMinutes?.let { "\"durationMinutes\":$it" },
        place?.let { "\"place\":\"$it\"" },
        "\"expansionIds\":[${expansionIds.joinToString(",") { "\"$it\"" }}]",
        players?.let { "\"players\":[${it.joinToString(",")}]" },
        teams?.let { "\"teams\":[${it.joinToString(",")}]" },
    ).joinToString(",", "{", "}")

    private fun create(user: User?, body: String) = sendJson(post(BASE_URL), user, body)

    private fun idOf(result: ResultActions): UUID =
        UUID.fromString(jsonMapper.readTree(result.andReturn().response.contentAsString).get("id").asString())

    @Nested
    @DisplayName("POST /api/v1/matches")
    inner class CreateMatchTests {

        @Test
        fun `should create an individual match with a registered user and a guest`() {
            val creator = persistUser(username = "creator")
            val game = persistGame()

            val id = idOf(
                create(creator, matchBody(game.id, players = listOf(player(creator.id, score = 10, isWinner = true), player(guestName = "Guest Player", score = 5)), durationMinutes = 60))
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.isTeamBased").value(false))
                    .andExpect(jsonPath("$.players.length()").value(2))
                    .andExpect(jsonPath("$.durationMinutes").value(60))
            )

            val saved = matchRepository.findById(id).orElseThrow()
            assertThat(saved.createdBy.id).isEqualTo(creator.id)
            assertThat(saved.winnerScore).isEqualTo(10)
            assertThat(saved.playedAt.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(today())
            assertThat(matchPlayerRepository.findAllByMatchId(id).map { it.guestName }).containsExactlyInAnyOrder(null, "Guest Player")
        }

        @Test
        fun `should keep the date chosen by the user`() {
            val creator = persistUser()
            val date = LocalDate.of(2026, 3, 15)

            val id = idOf(create(creator, matchBody(persistGame().id, players = listOf(player(creator.id)), playedAt = date)).andExpect(status().isCreated))

            assertThat(matchRepository.findById(id).orElseThrow().playedAt.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(date)
        }

        @Test
        fun `should create a match in progress without duration nor winner score`() {
            val creator = persistUser()

            val id = idOf(create(creator, matchBody(persistGame().id, players = listOf(player(creator.id, score = 10, isWinner = true)))).andExpect(status().isCreated))

            val saved = matchRepository.findById(id).orElseThrow()
            assertThat(saved.durationMinutes).isNull()
            assertThat(saved.winnerScore).isNull()
        }

        @Test
        fun `should create a team-based match`() {
            val creator = persistUser(username = "creator")
            val teammate = persistUser(username = "teammate")

            create(creator, matchBody(persistGame().id, teams = listOf(team("Team Red", 20, true, member(creator.id), member(teammate.id)), team("Team Blue", 10, false, member(guestName = "Guest"))), durationMinutes = 45))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.isTeamBased").value(true))
                .andExpect(jsonPath("$.players").value(nullValue()))
                .andExpect(jsonPath("$.teams[*].name").value(containsInAnyOrder("Team Red", "Team Blue")))
                .andExpect(jsonPath("$.teams[?(@.name == 'Team Red')].players.length()").value(contains(2)))
        }

        @Test
        fun `should attach an expansion of the selected game`() {
            val creator = persistUser()
            val expansion = persistGame(name = "Seafarers", bggId = 500L, isExpansion = true)
            val base = persistGame(name = "Catan", bggId = 13L, expansionRefs = listOf(ExpansionRef(500L, "Seafarers")))

            create(creator, matchBody(base.id, players = listOf(player(creator.id)), expansionIds = listOf(expansion.id!!)))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.expansionsUsed[*].bggId").value(contains(500)))
        }

        @Test
        fun `should return 400 for an expansion of another game`() {
            val creator = persistUser()
            val unrelated = persistGame(name = "Unrelated", bggId = 999L, isExpansion = true)

            create(creator, matchBody(persistGame(name = "Catan").id, players = listOf(player(creator.id)), expansionIds = listOf(unrelated.id!!)))
                .andExpect(status().isBadRequest)

            assertThat(matchRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 when isTeamBased is true but teams are missing`() {
            val creator = persistUser()

            create(creator, matchBody(persistGame().id, isTeamBased = true))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("At least one team is required when isTeamBased is true"))
        }

        @Test
        fun `should return 400 when a player has both userId and guestName`() {
            val creator = persistUser()

            create(creator, matchBody(persistGame().id, players = listOf(player(creator.id, guestName = "Also a guest"))))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the same user appears twice`() {
            val creator = persistUser()

            create(creator, matchBody(persistGame().id, players = listOf(player(creator.id), player(creator.id, color = "blue"))))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Each registered user can appear only once in a match"))

            assertThat(matchRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 when a player has no color`() {
            val creator = persistUser()

            create(creator, matchBody(persistGame().id, players = listOf(player(creator.id, color = ""))))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when playedAt is not a date`() {
            val creator = persistUser()

            create(creator, """{"gameId":"${persistGame().id}","playedAt":"yesterday","isTeamBased":false,"players":[${player(creator.id)}]}""")
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when game does not exist`() {
            val creator = persistUser()

            create(creator, matchBody(UUID.randomUUID(), players = listOf(player(creator.id))))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when a participant does not exist`() {
            val creator = persistUser()

            create(creator, matchBody(persistGame().id, players = listOf(player(creator.id), player(UUID.randomUUID(), color = "blue"))))
                .andExpect(status().isNotFound)

            assertThat(matchRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 404 when a participant deleted the account`() {
            val creator = persistUser()
            val deleted = persistUser(username = "deleted-user", accountStatus = AccountStatus.DELETED)

            create(creator, matchBody(persistGame().id, players = listOf(player(creator.id), player(deleted.id, color = "blue"))))
                .andExpect(status().isNotFound)

            assertThat(matchRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            create(null, matchBody(persistGame().id, players = listOf(player(guestName = "Guest"))))
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/matches/{matchId}")
    inner class UpdateMatchTests {

        private fun update(matchId: Any?, user: User, body: String) = sendJson(put("$BASE_URL/$matchId"), user, body)

        @Test
        fun `should update the match and replace players when user is the creator`() {
            val creator = persistUser(username = "creator")
            val game = persistGame()
            val match = persistMatch(game, creator)
            persistPlayer(match, user = creator)

            update(match.id, creator, matchBody(game.id, players = listOf(player(guestName = "New Guest", score = 99, isWinner = true, color = "green")), place = "New Place"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.place").value("New Place"))
                .andExpect(jsonPath("$.players[*].guestName").value(contains("New Guest")))

            assertThat(matchPlayerRepository.findAllByMatchId(match.id!!).map { it.guestName }).containsExactly("New Guest")
            assertThat(matchRepository.findById(match.id!!).orElseThrow().winnerScore).isEqualTo(99)
        }

        @Test
        fun `should switch an individual match to teams`() {
            val creator = persistUser()
            val game = persistGame()
            val match = persistMatch(game, creator)
            persistPlayer(match, user = creator)

            update(match.id, creator, matchBody(game.id, teams = listOf(team("Solo", 5, true, member(creator.id)))))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isTeamBased").value(true))
                .andExpect(jsonPath("$.teams.length()").value(1))

            assertThat(matchTeamRepository.findAll()).hasSize(1)
            assertThat(matchPlayerRepository.findAllByMatchId(match.id!!)).allMatch { it.team != null }
        }

        @Test
        fun `should compute durationMinutes when match was in progress`() {
            val creator = persistUser()
            val game = persistGame()
            val match = persistMatch(game, creator, durationMinutes = null)

            update(match.id, creator, matchBody(game.id, players = listOf(player(creator.id))))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.durationMinutes").isNotEmpty)

            assertThat(matchRepository.findById(match.id!!).orElseThrow().durationMinutes).isGreaterThanOrEqualTo(1)
        }

        @Test
        fun `should keep the time of day when the date does not change`() {
            val creator = persistUser()
            val game = persistGame()
            val playedAt = Instant.parse("2026-03-15T18:30:00Z")
            val match = persistMatch(game, creator, playedAt = playedAt)

            update(match.id, creator, matchBody(game.id, players = listOf(player(creator.id)), playedAt = LocalDate.of(2026, 3, 15), durationMinutes = 30))
                .andExpect(status().isOk)

            assertThat(matchRepository.findById(match.id!!).orElseThrow().playedAt).isEqualTo(playedAt)
        }

        @Test
        fun `should return 403 when user is not the creator`() {
            val creator = persistUser(username = "creator")
            val intruder = persistUser(username = "intruder")
            val game = persistGame()
            val match = persistMatch(game, creator)
            persistPlayer(match, user = creator)

            update(match.id, intruder, matchBody(game.id, players = listOf(player(intruder.id)), place = "Hijacked"))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("Only the match creator can perform this action"))

            assertThat(matchRepository.findById(match.id!!).orElseThrow().place).isNull()
            assertThat(matchPlayerRepository.findAllByMatchId(match.id!!).map { it.user?.id }).containsExactly(creator.id)
        }

        @Test
        fun `should not let a participant edit a match created by someone else`() {
            val creator = persistUser(username = "creator")
            val participant = persistUser(username = "participant")
            val game = persistGame()
            val match = persistMatch(game, creator)
            persistPlayer(match, user = participant)

            update(match.id, participant, matchBody(game.id, players = listOf(player(participant.id, isWinner = true))))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should keep the old players when the new payload is invalid`() {
            val creator = persistUser()
            val game = persistGame()
            val match = persistMatch(game, creator)
            persistPlayer(match, user = creator)

            update(match.id, creator, matchBody(game.id, players = listOf(player(creator.id), player(creator.id, color = "blue"))))
                .andExpect(status().isBadRequest)

            assertThat(matchPlayerRepository.findAllByMatchId(match.id!!)).hasSize(1)
        }

        @Test
        fun `should return 404 when match does not exist`() {
            val user = persistUser()

            update(UUID.randomUUID(), user, matchBody(persistGame().id, players = listOf(player(user.id))))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/matches/{matchId}")
    inner class DeleteMatchTests {

        @Test
        fun `should delete the match when user is the creator`() {
            val creator = persistUser()
            val match = persistMatch(persistGame(), creator)

            perform(delete("$BASE_URL/${match.id}"), creator).andExpect(status().isNoContent)

            assertThat(matchRepository.findById(match.id!!)).isEmpty
        }

        @Test
        fun `should delete a match together with its players and teams`() {
            val creator = persistUser(username = "creator")
            val friend = persistUser(username = "friend")
            val match = persistMatch(persistGame(), creator, isTeamBased = true)
            persistTeamWithPlayer(match, creator, isWinner = true)
            persistTeamWithPlayer(match, friend)

            perform(delete("$BASE_URL/${match.id}"), creator).andExpect(status().isNoContent)

            assertThat(matchRepository.findById(match.id!!)).isEmpty
            assertThat(matchPlayerRepository.findAllByMatchId(match.id!!)).isEmpty()
            assertThat(matchTeamRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 403 when user is not the creator`() {
            val creator = persistUser(username = "creator")
            val intruder = persistUser(username = "intruder")
            val match = persistMatch(persistGame(), creator)

            perform(delete("$BASE_URL/${match.id}"), intruder).andExpect(status().isForbidden)

            assertThat(matchRepository.findById(match.id!!)).isPresent
        }

        @Test
        fun `should return 404 when match does not exist`() {
            perform(delete("$BASE_URL/${UUID.randomUUID()}"), persistUser()).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/matches/{matchId}")
    inner class GetMatchTests {

        @Test
        fun `should return match details to any user`() {
            val creator = persistUser(username = "creator")
            val viewer = persistUser(username = "viewer")
            val match = persistMatch(persistGame(name = "Catan"), creator, durationMinutes = 45)
            persistPlayer(match, user = creator, isWinner = true, score = 10)
            persistPlayer(match, guestName = "Luca", score = 4)

            perform(get("$BASE_URL/${match.id}"), viewer)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(match.id.toString()))
                .andExpect(jsonPath("$.game.name").value("Catan"))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.players.length()").value(2))
        }

        @Test
        fun `should return an in-progress match`() {
            val match = persistMatch(persistGame(), persistUser(username = "creator"), durationMinutes = null)

            perform(get("$BASE_URL/${match.id}"), persistUser(username = "other"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.durationMinutes").value(nullValue()))
        }

        @Test
        fun `should return team-based match details with teams and player refs`() {
            val creator = persistUser()
            val match = persistMatch(persistGame(), creator, isTeamBased = true)
            persistTeamWithPlayer(match, creator)

            perform(get("$BASE_URL/${match.id}"), creator)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isTeamBased").value(true))
                .andExpect(jsonPath("$.teams.length()").value(1))
                .andExpect(jsonPath("$.teams[0].players.length()").value(1))
        }

        @Test
        fun `should return 404 when match does not exist`() {
            perform(get("$BASE_URL/${UUID.randomUUID()}"), persistUser()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the match id is not a UUID`() {
            perform(get("$BASE_URL/not-a-uuid"), persistUser()).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/matches")
    inner class ListMyMatchesTests {

        private fun list(user: User, vararg params: Pair<String, String>) =
            perform(get(BASE_URL).apply { params.forEach { (key, value) -> param(key, value) } }, user)

        @Test
        fun `should return matches created by the user`() {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user)
            persistMatch(game, user)

            list(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
        }

        @Test
        fun `should include matches where user is a player but not the creator`() {
            val creator = persistUser(username = "creator")
            val participant = persistUser(username = "participant")
            val match = persistMatch(persistGame(), creator)
            persistPlayer(match, user = participant)

            list(participant)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].id").value(contains(match.id.toString())))
        }

        @Test
        fun `should list a match only once even with many players`() {
            val user = persistUser()
            val match = persistMatch(persistGame(), user)
            persistPlayer(match, user = user)
            persistPlayer(match, guestName = "Luca")
            persistPlayer(match, guestName = "Anna")

            list(user)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
        }

        @Test
        fun `should not include another user's unrelated matches`() {
            val user = persistUser(username = "user")
            persistMatch(persistGame(), persistUser(username = "other"))

            list(user).andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should filter by gameId`() {
            val user = persistUser()
            val gameA = persistGame(name = "Game A")
            persistMatch(gameA, user)
            persistMatch(persistGame(name = "Game B"), user)

            list(user, "gameId" to gameA.id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Game A")))
        }

        @Test
        fun `should filter by date range including both ends`() {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user, playedAt = Instant.parse("2026-03-09T23:00:00Z"))
            val first = persistMatch(game, user, playedAt = Instant.parse("2026-03-10T00:30:00Z"))
            val last = persistMatch(game, user, playedAt = Instant.parse("2026-03-12T23:30:00Z"))
            persistMatch(game, user, playedAt = Instant.parse("2026-03-13T00:30:00Z"))

            list(user, "fromDate" to "2026-03-10", "toDate" to "2026-03-12")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(first.id.toString(), last.id.toString())))
        }

        @Test
        fun `should return the matches of a single day`() {
            val user = persistUser()
            val game = persistGame()
            val sameDay = persistMatch(game, user, playedAt = Instant.parse("2026-03-10T20:00:00Z"))
            persistMatch(game, user, playedAt = Instant.parse("2026-03-11T20:00:00Z"))

            list(user, "fromDate" to "2026-03-10", "toDate" to "2026-03-10")
                .andExpect(jsonPath("$.content[*].id").value(contains(sameDay.id.toString())))
        }

        @Test
        fun `should return 400 when a date is not in the yyyy-MM-dd format`() {
            list(persistUser(), "fromDate" to "10/03/2026").andExpect(status().isBadRequest)
        }

        @Test
        fun `should sort by playedAt desc by default and asc when requested`() {
            val user = persistUser()
            val game = persistGame()
            val now = Instant.now()
            val oldest = persistMatch(game, user, playedAt = now.minus(Duration.ofDays(5)))
            val newest = persistMatch(game, user, playedAt = now)

            list(user).andExpect(jsonPath("$.content[*].id").value(contains(newest.id.toString(), oldest.id.toString())))
            list(user, "sort" to "playedAt-asc").andExpect(jsonPath("$.content[*].id").value(contains(oldest.id.toString(), newest.id.toString())))
        }

        @ParameterizedTest
        @ValueSource(strings = ["durationMinutes-desc", "durationMinutes-asc"])
        fun `should list in-progress matches last when sorting by duration`(sort: String) {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user, durationMinutes = 30)
            persistMatch(game, user, durationMinutes = null)
            persistMatch(game, user, durationMinutes = 90)

            val expected = if(sort.endsWith("desc")) listOf(90, 30) else listOf(30, 90)

            list(user, "sort" to sort)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].durationMinutes").value(expected[0]))
                .andExpect(jsonPath("$.content[1].durationMinutes").value(expected[1]))
                .andExpect(jsonPath("$.content[2].durationMinutes").value(nullValue()))
        }

        @Test
        fun `should sort by winner score with matches without a score last`() {
            val user = persistUser()
            val game = persistGame()
            val low = persistMatch(game, user).apply { winnerScore = 10 }
            val high = persistMatch(game, user).apply { winnerScore = 50 }
            val none = persistMatch(game, user)
            matchRepository.saveAllAndFlush(listOf(low, high))

            list(user, "sort" to "winnerScore-desc")
                .andExpect(jsonPath("$.content[*].id").value(contains(high.id.toString(), low.id.toString(), none.id.toString())))
        }

        @Test
        fun `should keep pagination stable when many matches share the same duration`() {
            val user = persistUser()
            val game = persistGame()
            val ids = (1..5).map { persistMatch(game, user, durationMinutes = 60).id.toString() }

            val seen: List<String> = (0..2).flatMap { page ->
                val body = list(user, "sort" to "durationMinutes-desc", "size" to "2", "page" to page.toString())
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString
                JsonPath.read<List<String>>(body, "$.content[*].id")
            }

            assertThat(seen).hasSize(5).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ids)
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            list(persistUser(), "sort" to "notAllowedField-asc").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get(BASE_URL), null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/matches/calendar")
    inner class GetMatchCalendarTests {

        private fun calendar(user: User, year: String, month: String) =
            perform(get("$BASE_URL/calendar").param("year", year).param("month", month), user)

        @Test
        fun `should return match counts grouped by day within the month`() {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user, playedAt = Instant.parse("2026-03-10T10:00:00Z"))
            persistMatch(game, user, playedAt = Instant.parse("2026-03-10T21:00:00Z"))
            persistMatch(game, user, playedAt = Instant.parse("2026-03-11T12:00:00Z"))

            calendar(user, "2026", "3")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].date").value(contains("2026-03-10", "2026-03-11")))
                .andExpect(jsonPath("$[*].count").value(contains(2, 1)))
        }

        @Test
        fun `should include matches played as a participant`() {
            val creator = persistUser(username = "creator")
            val participant = persistUser(username = "participant")
            val match = persistMatch(persistGame(), creator, playedAt = Instant.parse("2026-03-10T10:00:00Z"))
            persistPlayer(match, user = participant)

            calendar(participant, "2026", "3").andExpect(jsonPath("$[*].count").value(contains(1)))
        }

        @Test
        fun `should count a match with many players once`() {
            val user = persistUser()
            val match = persistMatch(persistGame(), user, playedAt = Instant.parse("2026-03-10T10:00:00Z"))
            persistPlayer(match, user = user)
            persistPlayer(match, guestName = "Luca")

            calendar(user, "2026", "3").andExpect(jsonPath("$[*].count").value(contains(1)))
        }

        @Test
        fun `should not include matches outside the requested month`() {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user, playedAt = Instant.parse("2026-01-31T23:59:00Z"))
            persistMatch(game, user, playedAt = Instant.parse("2026-02-15T12:00:00Z"))
            persistMatch(game, user, playedAt = Instant.parse("2026-03-01T00:00:00Z"))

            calendar(user, "2026", "2").andExpect(jsonPath("$[*].date").value(contains("2026-02-15")))
        }

        @Test
        fun `should return an empty list when no matches exist for the month`() {
            calendar(persistUser(), "2020", "1")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @ParameterizedTest
        @ValueSource(strings = ["0", "13", "-1"])
        fun `should return 400 for a month out of range`(month: String) {
            calendar(persistUser(), "2026", month).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the month is missing`() {
            perform(get("$BASE_URL/calendar").param("year", "2026"), persistUser()).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/matches/recent-games")
    inner class GetRecentGamesTests {

        @Test
        fun `should return distinct games from the user's last matches, most recent first`() {
            val user = persistUser()
            val gameA = persistGame(name = "Game A")
            val gameB = persistGame(name = "Game B")
            val now = Instant.now()
            persistMatch(gameA, user, playedAt = now.minus(Duration.ofDays(2)))
            persistMatch(gameA, user, playedAt = now.minus(Duration.ofDays(1)))
            persistMatch(gameB, user, playedAt = now)

            perform(get("$BASE_URL/recent-games"), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].name").value(contains("Game B", "Game A")))
                .andExpect(jsonPath("$.content").doesNotExist())
        }

        @Test
        fun `should look only at the last ten matches`() {
            val user = persistUser()
            val now = Instant.now()
            persistMatch(persistGame(name = "Old Game"), user, playedAt = now.minus(Duration.ofDays(30)))
            val recent = persistGame(name = "Recent Game")
            repeat(10) { persistMatch(recent, user, playedAt = now.minus(Duration.ofHours(it.toLong()))) }

            perform(get("$BASE_URL/recent-games"), user)
                .andExpect(jsonPath("$[*].name").value(contains("Recent Game")))
        }

        @Test
        fun `should include matches where the user is a player but not the creator`() {
            val participant = persistUser(username = "participant")
            val match = persistMatch(persistGame(name = "Shared Game"), persistUser(username = "creator"))
            persistPlayer(match, user = participant)

            perform(get("$BASE_URL/recent-games"), participant)
                .andExpect(jsonPath("$[*].name").value(contains("Shared Game")))
        }

        @Test
        fun `should return an empty list when the user has no matches`() {
            perform(get("$BASE_URL/recent-games"), persistUser())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/matches/win-stats")
    inner class GetWinStatsTests {

        @Test
        fun `should count individual match wins`() {
            val user = persistUser()
            val game = persistGame()
            persistPlayer(persistMatch(game, user), user = user, isWinner = true)
            persistPlayer(persistMatch(game, user), user = user, isWinner = false)

            perform(get("$BASE_URL/win-stats"), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(2))
                .andExpect(jsonPath("$.totalWins").value(1))
        }

        @Test
        fun `should count a win when the user's team wins`() {
            val user = persistUser()
            persistTeamWithPlayer(persistMatch(persistGame(), user, isTeamBased = true), user, isWinner = true)

            perform(get("$BASE_URL/win-stats"), user)
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.totalWins").value(1))
        }

        @Test
        fun `should not count a win when the user's team loses`() {
            val user = persistUser()
            persistTeamWithPlayer(persistMatch(persistGame(), user, isTeamBased = true), user, isWinner = false)

            perform(get("$BASE_URL/win-stats"), user)
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should not count in-progress matches`() {
            val user = persistUser()
            persistPlayer(persistMatch(persistGame(), user, durationMinutes = null), user = user, isWinner = true)

            perform(get("$BASE_URL/win-stats"), user)
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should count a match created by the user without a win even if the user did not play`() {
            val user = persistUser()
            persistMatch(persistGame(), user)

            perform(get("$BASE_URL/win-stats"), user)
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should not count the wins of other players`() {
            val user = persistUser(username = "user")
            val friend = persistUser(username = "friend")
            val match = persistMatch(persistGame(), user)
            persistPlayer(match, user = user, isWinner = false)
            persistPlayer(match, user = friend, isWinner = true)

            perform(get("$BASE_URL/win-stats"), user).andExpect(jsonPath("$.totalWins").value(0))
            perform(get("$BASE_URL/win-stats"), friend).andExpect(jsonPath("$.totalWins").value(1))
        }

        @Test
        fun `should return zero stats for a user with no matches`() {
            perform(get("$BASE_URL/win-stats"), persistUser())
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.totalWins").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/matches/game-stats")
    inner class ListGameStatsTests {

        private fun stats(user: User, vararg params: Pair<String, String>) =
            perform(get("$BASE_URL/game-stats").apply { params.forEach { (key, value) -> param(key, value) } }, user)

        @Test
        fun `should aggregate completed matches per game`() {
            val user = persistUser()
            val catan = persistGame(name = "Catan")
            val ark = persistGame(name = "Ark Nova")
            persistMatch(catan, user, durationMinutes = 30)
            persistMatch(catan, user, durationMinutes = 90)
            persistMatch(ark, user, durationMinutes = 60)

            stats(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Catan", "Ark Nova")))
                .andExpect(jsonPath("$.content[0].matchCount").value(2))
                .andExpect(jsonPath("$.content[0].totalMinutes").value(120))
                .andExpect(jsonPath("$.content[0].avgMinutes").value(60.0))
                .andExpect(jsonPath("$.content[1].matchCount").value(1))
        }

        @Test
        fun `should ignore in-progress matches`() {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user, durationMinutes = 45)
            persistMatch(game, user, durationMinutes = null)

            stats(user)
                .andExpect(jsonPath("$.content[0].matchCount").value(1))
                .andExpect(jsonPath("$.content[0].totalMinutes").value(45))
        }

        @Test
        fun `should omit a game that only has in-progress matches`() {
            val user = persistUser()
            persistMatch(persistGame(), user, durationMinutes = null)

            stats(user).andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should count individual wins and compute the win rate`() {
            val user = persistUser()
            val game = persistGame()
            persistPlayer(persistMatch(game, user), user = user, isWinner = true)
            persistPlayer(persistMatch(game, user), user = user, isWinner = false)

            stats(user)
                .andExpect(jsonPath("$.content[0].matchCount").value(2))
                .andExpect(jsonPath("$.content[0].wins").value(1))
                .andExpect(jsonPath("$.content[0].winRate").value(0.5))
        }

        @Test
        fun `should count a win when the user's team wins`() {
            val user = persistUser()
            persistTeamWithPlayer(persistMatch(persistGame(), user, isTeamBased = true), user, isWinner = true)

            stats(user)
                .andExpect(jsonPath("$.content[0].wins").value(1))
                .andExpect(jsonPath("$.content[0].winRate").value(1.0))
        }

        @Test
        fun `should include games where the user is a player but not the creator`() {
            val participant = persistUser(username = "participant")
            val match = persistMatch(persistGame(), persistUser(username = "creator"))
            persistPlayer(match, user = participant)

            stats(participant).andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should not include other users' matches`() {
            val user = persistUser(username = "user")
            persistMatch(persistGame(), persistUser(username = "other"))

            stats(user).andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should sort by totalMinutes desc`() {
            val user = persistUser()
            val short = persistGame(name = "Short")
            persistMatch(short, user, durationMinutes = 10)
            persistMatch(short, user, durationMinutes = 10)
            persistMatch(persistGame(name = "Long"), user, durationMinutes = 100)

            stats(user, "sort" to "totalMinutes-desc").andExpect(jsonPath("$.content[*].name").value(contains("Long", "Short")))
        }

        @Test
        fun `should sort by avgMinutes asc`() {
            val user = persistUser()
            persistMatch(persistGame(name = "Slow"), user, durationMinutes = 120)
            persistMatch(persistGame(name = "Quick"), user, durationMinutes = 15)

            stats(user, "sort" to "avgMinutes-asc").andExpect(jsonPath("$.content[*].name").value(contains("Quick", "Slow")))
        }

        @Test
        fun `should sort by winRate desc`() {
            val user = persistUser()
            persistPlayer(persistMatch(persistGame(name = "Unlucky"), user), user = user, isWinner = false)
            persistPlayer(persistMatch(persistGame(name = "Lucky"), user), user = user, isWinner = true)

            stats(user, "sort" to "winRate-desc").andExpect(jsonPath("$.content[*].name").value(contains("Lucky", "Unlucky")))
        }

        @Test
        fun `should sort by gameName asc`() {
            val user = persistUser()
            persistMatch(persistGame(name = "Zombicide"), user)
            persistMatch(persistGame(name = "Azul"), user)

            stats(user, "sort" to "gameName-asc").andExpect(jsonPath("$.content[*].name").value(contains("Azul", "Zombicide")))
        }

        @Test
        fun `should sort by lastPlayedAt desc`() {
            val user = persistUser()
            val now = Instant.now()
            persistMatch(persistGame(name = "Old"), user, playedAt = now.minus(Duration.ofDays(30)))
            persistMatch(persistGame(name = "Recent"), user, playedAt = now)

            stats(user, "sort" to "lastPlayedAt-desc").andExpect(jsonPath("$.content[*].name").value(contains("Recent", "Old")))
        }

        @Test
        fun `should break ties by game name`() {
            val user = persistUser()
            persistMatch(persistGame(name = "Wingspan"), user)
            persistMatch(persistGame(name = "Azul"), user)
            persistMatch(persistGame(name = "Catan"), user)

            stats(user).andExpect(jsonPath("$.content[*].name").value(contains("Azul", "Catan", "Wingspan")))
        }

        @Test
        fun `should paginate the results`() {
            val user = persistUser()
            listOf("A", "B", "C").forEach { persistMatch(persistGame(name = it), user) }

            stats(user, "size" to "2", "page" to "1", "sort" to "gameName-asc")
                .andExpect(jsonPath("$.content[*].name").value(contains("C")))
                .andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should return an empty page for a user with no matches`() {
            stats(persistUser())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            stats(persistUser(), "sort" to "notAllowedField-asc").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get("$BASE_URL/game-stats"), null).andExpect(status().isForbidden)
        }
    }
}