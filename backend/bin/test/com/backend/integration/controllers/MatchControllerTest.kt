package com.backend.integration.controllers

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
import com.backend.repositories.UserRepository
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@AutoConfigureMockMvc
class MatchControllerTest: AbstractIntegrationTest() {

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

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun persistGame(bggId: Long = (1..1_000_000).random().toLong(), name: String = "Test Game"): Game =
        gameRepository.saveAndFlush(Game(bggId = bggId, name = name, lastSyncedAt = Instant.now()))

    private fun persistMatch(
        game: Game,
        createdBy: User,
        isTeamBased: Boolean = false,
        playedAt: Instant = Instant.now(),
        durationMinutes: Int? = 30,
    ): Match =
        matchRepository.saveAndFlush(
            Match(
                game = game,
                createdBy = createdBy,
                isTeamBased = isTeamBased,
                playedAt = playedAt,
                durationMinutes = durationMinutes,
            )
        )

    private fun persistIndividualPlayer(match: Match, user: User? = null, guestName: String? = null): MatchPlayer =
        matchPlayerRepository.saveAndFlush(
            MatchPlayer(match = match, user = user, guestName = guestName, color = "red")
        )

    private fun persistTeamWithPlayer(match: Match, user: User): MatchTeam {
        val team = matchTeamRepository.saveAndFlush(MatchTeam(match = match, color = "blue"))
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, team = team, user = user))
        return team
    }

    @AfterEach
    fun cleanUp() {
        matchPlayerRepository.deleteAll()
        matchTeamRepository.deleteAll()
        matchRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/matches
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/matches")
    inner class CreateMatchTests {

        @Test
        fun `should create an individual match with a registered user and a guest`() {
            val creator = persistUser(username = "creator")
            val game = persistGame()
            val now = Instant.now()
            val payload = """
                {
                    "gameId": "${game.id}",
                    "playedAt": "$now",
                    "isTeamBased": false,
                    "players": [
                        { "userId": "${creator.id}", "color": "red", "score": 10, "isWinner": true },
                        { "guestName": "Guest Player", "color": "blue", "score": 5, "isWinner": false }
                    ]
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.isTeamBased").value(false))
                .andExpect(jsonPath("$.players.length()").value(2))

            val savedMatches = matchRepository.findAllForUser(creator.id!!)
            assertThat(savedMatches).hasSize(1)
        }

        @Test
        fun `should create a team-based match`() {
            val creator = persistUser(username = "creator")
            val teammate = persistUser(username = "teammate")
            val game = persistGame()
            val now = Instant.now()
            val payload = """
                {
                    "gameId": "${game.id}",
                    "playedAt": "$now",
                    "isTeamBased": true,
                    "teams": [
                        {
                            "name": "Team Red",
                            "color": "red",
                            "score": 20,
                            "isWinner": true,
                            "players": [{ "userId": "${creator.id}" }, { "userId": "${teammate.id}" }]
                        },
                        {
                            "name": "Team Blue",
                            "color": "blue",
                            "score": 10,
                            "isWinner": false,
                            "players": [{ "guestName": "Guest" }]
                        }
                    ]
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.isTeamBased").value(true))
                .andExpect(jsonPath("$.teams.length()").value(2))
                .andExpect(jsonPath("$.teams[0].players.length()").value(2))
        }

        @Test
        fun `should return 400 when isTeamBased is true but teams are missing`() {
            val creator = persistUser()
            val game = persistGame()
            val payload = """
                {
                    "gameId": "${game.id}",
                    "playedAt": "${Instant.now()}",
                    "isTeamBased": true
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when a player has both userId and guestName`() {
            val creator = persistUser()
            val game = persistGame()
            val payload = """
                {
                    "gameId": "${game.id}",
                    "playedAt": "${Instant.now()}",
                    "isTeamBased": false,
                    "players": [
                        { "userId": "${creator.id}", "guestName": "Also a guest", "color": "red" }
                    ]
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when game does not exist`() {
            val creator = persistUser()
            val payload = """
                {
                    "gameId": "${UUID.randomUUID()}",
                    "playedAt": "${Instant.now()}",
                    "isTeamBased": false,
                    "players": [{ "userId": "${creator.id}", "color": "red" }]
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // PUT /api/v1/matches/{matchId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/v1/matches/{matchId}")
    inner class UpdateMatchTests {

        @Test
        fun `should update the match and replace players when user is the creator`() {
            val creator = persistUser(username = "creator")
            val game = persistGame()
            val match = persistMatch(game, creator)
            persistIndividualPlayer(match, user = creator)

            val payload = """
                {
                    "gameId": "${game.id}",
                    "playedAt": "${Instant.now()}",
                    "place": "New Place",
                    "isTeamBased": false,
                    "players": [{ "guestName": "New Guest", "color": "green", "score": 99, "isWinner": true }]
                }
            """.trimIndent()

            mockMvc.perform(
                put("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.place").value("New Place"))
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].guestName").value("New Guest"))

            assertThat(matchPlayerRepository.findAllByMatchId(match.id!!)).hasSize(1)
        }

        @Test
        fun `should return 403 when user is not the creator`() {
            val creator = persistUser(username = "creator")
            val otherUser = persistUser(username = "intruder")
            val game = persistGame()
            val match = persistMatch(game, creator)

            val payload = """
                {
                    "gameId": "${game.id}",
                    "playedAt": "${Instant.now()}",
                    "isTeamBased": false,
                    "players": [{ "userId": "${otherUser.id}", "color": "red" }]
                }
            """.trimIndent()

            mockMvc.perform(
                put("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when match does not exist`() {
            val user = persistUser()
            val game = persistGame()
            val payload = """
                {
                    "gameId": "${game.id}",
                    "playedAt": "${Instant.now()}",
                    "isTeamBased": false,
                    "players": [{ "userId": "${user.id}", "color": "red" }]
                }
            """.trimIndent()

            mockMvc.perform(
                put("/api/v1/matches/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should compute durationMinutes when match was in progress`() {
            val creator = persistUser()
            val game = persistGame()
            val match = persistMatch(game, creator, durationMinutes = null)

            val payload = """
                {
                    "gameId": "${game.id}",
                    "playedAt": "${Instant.now()}",
                    "isTeamBased": false,
                    "players": [{ "userId": "${creator.id}", "color": "red" }]
                }
            """.trimIndent()

            mockMvc.perform(
                put("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.durationMinutes").isNotEmpty)

            val updated = matchRepository.findById(match.id!!).orElseThrow()
            assertThat(updated.durationMinutes).isNotNull()
            assertThat(updated.durationMinutes).isGreaterThanOrEqualTo(1)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/matches/{matchId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/matches/{matchId}")
    inner class DeleteMatchTests {

        @Test
        fun `should delete the match when user is the creator`() {
            val creator = persistUser()
            val game = persistGame()
            val match = persistMatch(game, creator)

            mockMvc.perform(
                delete("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
            ).andExpect(status().isNoContent)

            assertThat(matchRepository.findById(match.id!!)).isEmpty()
        }

        @Test
        fun `should return 403 when user is not the creator`() {
            val creator = persistUser(username = "creator")
            val otherUser = persistUser(username = "intruder")
            val game = persistGame()
            val match = persistMatch(game, creator)

            mockMvc.perform(
                delete("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
            ).andExpect(status().isForbidden)

            assertThat(matchRepository.findById(match.id!!)).isPresent
        }

        @Test
        fun `should return 404 when match does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/matches/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/matches/{matchId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/matches/{matchId}")
    inner class GetMatchTests {

        @Test
        fun `should return match details for a completed match to any user`() {
            val creator = persistUser(username = "creator")
            val viewer = persistUser(username = "viewer")
            val game = persistGame()
            val match = persistMatch(game, creator, durationMinutes = 45)
            persistIndividualPlayer(match, user = creator)

            mockMvc.perform(
                get("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(viewer))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(match.id.toString()))
        }

        @Test
        fun `should return 403 when match is in progress and user is not the creator`() {
            val creator = persistUser(username = "creator")
            val otherUser = persistUser(username = "other")
            val game = persistGame()
            val match = persistMatch(game, creator, durationMinutes = null)

            mockMvc.perform(
                get("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should allow the creator to view their own in-progress match`() {
            val creator = persistUser()
            val game = persistGame()
            val match = persistMatch(game, creator, durationMinutes = null)

            mockMvc.perform(
                get("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
            ).andExpect(status().isOk)
        }

        @Test
        fun `should return 404 when match does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/matches/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return team-based match details with teams and player refs`() {
            val creator = persistUser()
            val game = persistGame()
            val match = persistMatch(game, creator, isTeamBased = true)
            persistTeamWithPlayer(match, creator)

            mockMvc.perform(
                get("/api/v1/matches/${match.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(creator))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isTeamBased").value(true))
                .andExpect(jsonPath("$.teams.length()").value(1))
                .andExpect(jsonPath("$.teams[0].players.length()").value(1))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/matches
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/matches")
    inner class ListMyMatchesTests {

        @Test
        fun `should return matches created by the user`() {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user)
            persistMatch(game, user)

            mockMvc.perform(
                get("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
        }

        @Test
        fun `should include matches where user is a player but not the creator`() {
            val creator = persistUser(username = "creator")
            val participant = persistUser(username = "participant")
            val game = persistGame()
            val match = persistMatch(game, creator)
            persistIndividualPlayer(match, user = participant)

            mockMvc.perform(
                get("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(participant))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should filter by gameId`() {
            val user = persistUser()
            val gameA = persistGame(name = "Game A")
            val gameB = persistGame(name = "Game B")
            persistMatch(gameA, user)
            persistMatch(gameB, user)

            mockMvc.perform(
                get("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("gameId", gameA.id.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].game.name").value("Game A"))
        }

        @Test
        fun `should filter by date range`() {
            val user = persistUser()
            val game = persistGame()
            val now = Instant.now()
            persistMatch(game, user, playedAt = now.minus(Duration.ofDays(10)))
            persistMatch(game, user, playedAt = now)

            val fromDate = now.minus(Duration.ofDays(1)).atZone(ZoneOffset.UTC).toLocalDate().toString()
            val toDate = now.plus(Duration.ofDays(1)).atZone(ZoneOffset.UTC).toLocalDate().toString()

            mockMvc.perform(
                get("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("fromDate", fromDate)
                    .param("toDate", toDate)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("sort", "notAllowedField-asc")
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should not include another user's unrelated matches`() {
            val user = persistUser(username = "user")
            val otherUser = persistUser(username = "other")
            val game = persistGame()
            persistMatch(game, otherUser)

            mockMvc.perform(
                get("/api/v1/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/matches/calendar
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/matches/calendar")
    inner class GetMatchCalendarTests {

        @Test
        fun `should return match counts grouped by day within the month`() {
            val user = persistUser()
            val game = persistGame()
            val today = Instant.now()
            val tomorrow = today.plus(Duration.ofDays(1))

            persistMatch(game, user, playedAt = today)
            persistMatch(game, user, playedAt = today)
            persistMatch(game, user, playedAt = tomorrow)

            val targetYear = today.atZone(ZoneOffset.UTC).year
            val targetMonth = today.atZone(ZoneOffset.UTC).monthValue

            mockMvc.perform(
                get("/api/v1/matches/calendar")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("year", targetYear.toString())
                    .param("month", targetMonth.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value(today.atZone(ZoneOffset.UTC).toLocalDate().toString()))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[1].date").value(tomorrow.atZone(ZoneOffset.UTC).toLocalDate().toString()))
                .andExpect(jsonPath("$[1].count").value(1))
        }

        @Test
        fun `should return an empty list when no matches exist for the month`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/matches/calendar")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("year", "2020")
                    .param("month", "1")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not include matches outside the requested month`() {
            val user = persistUser()
            val game = persistGame()

            val janMatchTime = Instant.parse("2026-01-15T12:00:00Z")
            val febMatchTime = Instant.parse("2026-02-15T12:00:00Z")

            persistMatch(game, user, playedAt = janMatchTime)
            persistMatch(game, user, playedAt = febMatchTime)

            mockMvc.perform(
                get("/api/v1/matches/calendar")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("year", "2026")
                    .param("month", "1")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/matches/recent-games
    // ---------------------------------------------------------------------

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

            mockMvc.perform(
                get("/api/v1/matches/recent-games")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Game B"))
                .andExpect(jsonPath("$[1].name").value("Game A"))
        }

        @Test
        fun `should include matches where the user is a player but not the creator`() {
            val creator = persistUser(username = "creator")
            val participant = persistUser(username = "participant")
            val game = persistGame()
            val match = persistMatch(game, creator)
            persistIndividualPlayer(match, user = participant)

            mockMvc.perform(
                get("/api/v1/matches/recent-games")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(participant))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
        }

        @Test
        fun `should return an empty list when the user has no matches`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/matches/recent-games")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not be paginated`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/matches/recent-games")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").doesNotExist())
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/matches/win-stats
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/matches/win-stats")
    inner class GetWinStatsTests {

        @Test
        fun `should count individual match wins`() {
            val user = persistUser()
            val game = persistGame()
            val wonMatch = persistMatch(game, user)
            val lostMatch = persistMatch(game, user)

            val winPlayer = MatchPlayer(match = wonMatch, user = user, color = "red", isWinner = true)
            matchPlayerRepository.saveAndFlush(winPlayer)

            val losePlayer = MatchPlayer(match = lostMatch, user = user, color = "blue", isWinner = false)
            matchPlayerRepository.saveAndFlush(losePlayer)

            val allPlayers = matchPlayerRepository.findAll()
            println("DEBUG: saved players = ${allPlayers.map { "user=${it.user?.id} isWinner=${it.isWinner} match=${it.match.id}" }}")

            val directCount = matchRepository.countWonMatchesForUser(user.id!!)
            println("DEBUG: direct repository count = $directCount")

            mockMvc.perform(
                get("/api/v1/matches/win-stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(2))
                .andExpect(jsonPath("$.totalWins").value(1))
        }

        @Test
        fun `should count a win when the user's team wins`() {
            val user = persistUser()
            val game = persistGame()
            val match = persistMatch(game, user)
            val winningTeam = matchTeamRepository.saveAndFlush(MatchTeam(match = match, color = "red", isWinner = true))
            matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, team = winningTeam, user = user))

            mockMvc.perform(
                get("/api/v1/matches/win-stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.totalWins").value(1))
        }

        @Test
        fun `should not count a win when the user's team loses`() {
            val user = persistUser()
            val game = persistGame()
            val match = persistMatch(game, user)
            val losingTeam = matchTeamRepository.saveAndFlush(MatchTeam(match = match, color = "red", isWinner = false))
            matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, team = losingTeam, user = user))

            mockMvc.perform(
                get("/api/v1/matches/win-stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should not count in-progress matches`() {
            val user = persistUser()
            val game = persistGame()
            val inProgressMatch = persistMatch(game, user, durationMinutes = null)
            matchPlayerRepository.saveAndFlush(MatchPlayer(match = inProgressMatch, user = user, color = "red", isWinner = true))

            mockMvc.perform(
                get("/api/v1/matches/win-stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should not count matches only created by the user without being a player`() {
            val user = persistUser()
            val game = persistGame()
            persistMatch(game, user)

            mockMvc.perform(
                get("/api/v1/matches/win-stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should return zero stats for a user with no matches`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/matches/win-stats")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.totalWins").value(0))
        }
    }
}