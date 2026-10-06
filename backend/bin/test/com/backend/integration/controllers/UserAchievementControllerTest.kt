package com.backend.integration.controllers

import com.backend.models.entities.*
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.AchievementType
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@AutoConfigureMockMvc
class UserAchievementControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @Autowired
    private lateinit var matchPlayerRepository: MatchPlayerRepository

    @Autowired
    private lateinit var userAchievementRepository: UserAchievementRepository

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

    private fun persistGame(bggId: Long = (1..1_000_000).random().toLong()): Game =
        gameRepository.saveAndFlush(Game(bggId = bggId, name = "Test Game", lastSyncedAt = Instant.now()))

    private fun persistFinishedMatchFor(user: User): Match {
        val match = matchRepository.saveAndFlush(
            Match(game = persistGame(), createdBy = user, playedAt = Instant.now(), durationMinutes = 30)
        )
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, user = user))
        return match
    }

    private fun persistUserAchievement(
        user: User,
        type: AchievementType,
        unlockedAt: Instant = Instant.now(),
        seenAt: Instant? = null,
    ): UserAchievement =
        userAchievementRepository.saveAndFlush(
            UserAchievement(
                userId = user.id!!,
                achievementCode = type.name,
                unlockedAt = unlockedAt,
                seenAt = seenAt,
            )
        )

    @AfterEach
    fun cleanUp() {
        userAchievementRepository.deleteAll()
        matchPlayerRepository.deleteAll()
        matchRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/achievements/me
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/achievements/me")
    inner class GetMyAchievementsTests {

        @Test
        fun `should return the whole catalog with nothing unlocked for a new user`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/achievements/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(AchievementType.entries.size))
                .andExpect(jsonPath("$[?(@.unlocked == true)]").isEmpty)
        }

        @Test
        fun `should return already unlocked achievements as unlocked`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            mockMvc.perform(
                get("/api/v1/achievements/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_FRIEND')].unlocked").value(true))
                .andExpect(jsonPath("$[?(@.code == 'FIRST_WIN')].unlocked").value(false))
        }

        @Test
        fun `should unlock achievements reached by the user's current activity`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            mockMvc.perform(
                get("/api/v1/achievements/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_MATCH')].unlocked").value(true))

            assertThat(userAchievementRepository.findCodesByUserId(user.id!!))
                .contains(AchievementType.FIRST_MATCH.name)
        }

        @Test
        fun `should show the progress on achievements not yet unlocked`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            mockMvc.perform(
                get("/api/v1/achievements/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'MATCHES_10')].progress").value(1))
                .andExpect(jsonPath("$[?(@.code == 'MATCHES_10')].unlocked").value(false))
        }

        @Test
        fun `should not return another user's achievements`() {
            val user = persistUser(username = "me")
            val other = persistUser(username = "other")
            persistUserAchievement(other, AchievementType.FIRST_FRIEND)

            mockMvc.perform(
                get("/api/v1/achievements/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_FRIEND')].unlocked").value(false))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(
                get("/api/v1/achievements/me")
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/achievements/unseen
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/achievements/unseen")
    inner class GetUnseenAchievementsTests {

        @Test
        fun `should return an empty list when there is nothing unseen`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/achievements/unseen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return only the achievements not yet seen`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)
            persistUserAchievement(user, AchievementType.FIRST_WIN, seenAt = Instant.now())

            mockMvc.perform(
                get("/api/v1/achievements/unseen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("FIRST_FRIEND"))
                .andExpect(jsonPath("$[0].unlocked").value(true))
        }

        @Test
        fun `should return unseen achievements oldest first`() {
            val user = persistUser()
            val now = Instant.now()
            persistUserAchievement(user, AchievementType.FIRST_WIN, unlockedAt = now.minus(1, ChronoUnit.MINUTES))
            persistUserAchievement(user, AchievementType.FIRST_FRIEND, unlockedAt = now.minus(1, ChronoUnit.HOURS))

            mockMvc.perform(
                get("/api/v1/achievements/unseen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("FIRST_FRIEND"))
                .andExpect(jsonPath("$[1].code").value("FIRST_WIN"))
        }

        @Test
        fun `should unlock and return achievements reached by existing activity`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            mockMvc.perform(
                get("/api/v1/achievements/unseen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_MATCH')].unlocked").value(true))

            val saved = userAchievementRepository.findAllByUserId(user.id!!)
            assertThat(saved.map { it.achievementCode }).contains(AchievementType.FIRST_MATCH.name)
            assertThat(saved.first { it.achievementCode == AchievementType.FIRST_MATCH.name }.seenAt).isNull()
        }

        @Test
        fun `should not return another user's unseen achievements`() {
            val user = persistUser(username = "me")
            val other = persistUser(username = "other")
            persistUserAchievement(other, AchievementType.FIRST_FRIEND)

            mockMvc.perform(
                get("/api/v1/achievements/unseen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(
                get("/api/v1/achievements/unseen")
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/achievements/seen
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/achievements/seen")
    inner class MarkSeenTests {

        @Test
        fun `should mark the given achievements as seen and return 204`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": ["FIRST_FRIEND"]}""")
            ).andExpect(status().isNoContent)

            val saved = userAchievementRepository.findAllByUserId(user.id!!).single()
            assertThat(saved.seenAt).isNotNull()
        }

        @Test
        fun `should not return the achievements in unseen once they are marked as seen`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": ["FIRST_FRIEND"]}""")
            ).andExpect(status().isNoContent)

            mockMvc.perform(
                get("/api/v1/achievements/unseen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not show again an achievement unlocked by activity once it has been seen`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            mockMvc.perform(
                get("/api/v1/achievements/unseen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isOk)

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": ["FIRST_MATCH"]}""")
            ).andExpect(status().isNoContent)

            mockMvc.perform(
                get("/api/v1/achievements/unseen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_MATCH')]").isEmpty)
        }

        @Test
        fun `should mark only the given codes`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)
            persistUserAchievement(user, AchievementType.FIRST_WIN)

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": ["FIRST_FRIEND"]}""")
            ).andExpect(status().isNoContent)

            val byCode = userAchievementRepository.findAllByUserId(user.id!!).associateBy { it.achievementCode }
            assertThat(byCode.getValue("FIRST_FRIEND").seenAt).isNotNull()
            assertThat(byCode.getValue("FIRST_WIN").seenAt).isNull()
        }

        @Test
        fun `should not mark another user's achievements as seen`() {
            val user = persistUser(username = "me")
            val other = persistUser(username = "other")
            persistUserAchievement(other, AchievementType.FIRST_FRIEND)

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": ["FIRST_FRIEND"]}""")
            ).andExpect(status().isNoContent)

            val saved = userAchievementRepository.findAllByUserId(other.id!!).single()
            assertThat(saved.seenAt).isNull()
        }

        @Test
        fun `should ignore unknown codes and return 204`() {
            val user = persistUser()

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": ["NOT_AN_ACHIEVEMENT"]}""")
            ).andExpect(status().isNoContent)
        }

        @Test
        fun `should keep the original seen date when marking an already seen achievement`() {
            val user = persistUser()
            val seenAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)
            persistUserAchievement(user, AchievementType.FIRST_FRIEND, seenAt = seenAt)

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": ["FIRST_FRIEND"]}""")
            ).andExpect(status().isNoContent)

            val saved = userAchievementRepository.findAllByUserId(user.id!!).single()
            assertThat(saved.seenAt).isEqualTo(seenAt)
        }

        @Test
        fun `should return 400 when the list of codes is empty`() {
            val user = persistUser()

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": []}""")
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the body is empty`() {
            val user = persistUser()

            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{}""")
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(
                post("/api/v1/achievements/seen")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"codes": ["FIRST_FRIEND"]}""")
            ).andExpect(status().isForbidden)
        }
    }
}