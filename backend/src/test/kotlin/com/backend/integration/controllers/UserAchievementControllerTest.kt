package com.backend.integration.controllers

import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.User
import com.backend.models.entities.UserAchievement
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.AchievementType
import com.backend.models.enums.UserRole
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.UserAchievementRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit

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

    private var nextBggId = 1L

    companion object {
        private const val BASE_URL = "/api/v1/achievements"
    }

    @AfterEach
    fun cleanUp() {
        userAchievementRepository.deleteAll()
        matchPlayerRepository.deleteAll()
        matchRepository.deleteAll()
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

    private fun persistGame(): Game =
        gameRepository.saveAndFlush(Game(bggId = nextBggId++, name = "Test Game", lastSyncedAt = Instant.now()))

    private fun persistFinishedMatchFor(user: User): Match {
        val match = matchRepository.saveAndFlush(Match(game = persistGame(), createdBy = user, playedAt = Instant.now(), durationMinutes = 30))
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, user = user))
        return match
    }

    private fun persistUserAchievement(user: User, type: AchievementType, unlockedAt: Instant = Instant.now(), seenAt: Instant? = null): UserAchievement =
        userAchievementRepository.saveAndFlush(UserAchievement(userId = user.id!!, achievementCode = type.name, unlockedAt = unlockedAt, seenAt = seenAt))

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User?): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    private fun getMine(user: User?) = perform(get("$BASE_URL/me"), user)

    private fun getUnseen(user: User?) = perform(get("$BASE_URL/unseen"), user)

    private fun markSeen(user: User?, body: String) =
        perform(post("$BASE_URL/seen").contentType(MediaType.APPLICATION_JSON).content(body), user)

    private fun rowsOf(user: User, type: AchievementType): List<UserAchievement> =
        userAchievementRepository.findAllByUserId(user.id!!).filter { it.achievementCode == type.name }

    @Nested
    @DisplayName("GET /api/v1/achievements/me")
    inner class GetMyAchievementsTests {

        @Test
        fun `should return the whole catalog with nothing unlocked for a new user`() {
            getMine(persistUser())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(AchievementType.entries.size))
                .andExpect(jsonPath("$[?(@.unlocked == true)]").isEmpty)
        }

        @Test
        fun `should return every catalog code exactly once`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            getMine(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].code").value(containsInAnyOrder(*AchievementType.entries.map { it.name }.toTypedArray())))
        }

        @Test
        fun `should return already unlocked achievements as unlocked`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            getMine(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_FRIEND')].unlocked").value(true))
                .andExpect(jsonPath("$[?(@.code == 'FIRST_WIN')].unlocked").value(false))
        }

        @Test
        fun `should unlock achievements reached by the user's current activity and leave them unseen`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            getMine(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_MATCH')].unlocked").value(true))

            val saved = rowsOf(user, AchievementType.FIRST_MATCH).single()
            assertThat(saved.seenAt).isNull()

            getUnseen(user).andExpect(jsonPath("$[*].code").value(contains("FIRST_MATCH")))
        }

        @Test
        fun `should not unlock the same achievement twice on repeated calls`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            getMine(user).andExpect(status().isOk)
            val firstUnlock = rowsOf(user, AchievementType.FIRST_MATCH).single().unlockedAt

            getMine(user).andExpect(status().isOk)
            getUnseen(user).andExpect(status().isOk)

            val rows = rowsOf(user, AchievementType.FIRST_MATCH)
            assertThat(rows).hasSize(1)
            assertThat(rows.single().unlockedAt).isEqualTo(firstUnlock)
        }

        @Test
        fun `should keep an achievement unlocked after it has been seen`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND, seenAt = Instant.now())

            getMine(user).andExpect(jsonPath("$[?(@.code == 'FIRST_FRIEND')].unlocked").value(true))
        }

        @Test
        fun `should show the progress on achievements not yet unlocked`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            getMine(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'MATCHES_10')].progress").value(1))
                .andExpect(jsonPath("$[?(@.code == 'MATCHES_10')].unlocked").value(false))
        }

        @Test
        fun `should not return another user's achievements`() {
            val user = persistUser(username = "me")
            val other = persistUser(username = "other")
            persistUserAchievement(other, AchievementType.FIRST_FRIEND)
            persistFinishedMatchFor(other)

            getMine(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.unlocked == true)]").isEmpty)

            assertThat(userAchievementRepository.findAllByUserId(user.id!!)).isEmpty()
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            getMine(null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/achievements/unseen")
    inner class GetUnseenAchievementsTests {

        @Test
        fun `should return an empty list when there is nothing unseen`() {
            getUnseen(persistUser())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return only the achievements not yet seen`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)
            persistUserAchievement(user, AchievementType.FIRST_WIN, seenAt = Instant.now())

            getUnseen(user)
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

            getUnseen(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].code").value(contains("FIRST_FRIEND", "FIRST_WIN")))
        }

        @Test
        fun `should unlock and return achievements reached by existing activity`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            getUnseen(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_MATCH')].unlocked").value(true))

            assertThat(rowsOf(user, AchievementType.FIRST_MATCH).single().seenAt).isNull()
        }

        @Test
        fun `should not return another user's unseen achievements`() {
            val user = persistUser(username = "me")
            persistUserAchievement(persistUser(username = "other"), AchievementType.FIRST_FRIEND)

            getUnseen(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            getUnseen(null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/achievements/seen")
    inner class MarkSeenTests {

        @Test
        fun `should mark the given achievements as seen and return 204`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            markSeen(user, """{"codes":["FIRST_FRIEND"]}""").andExpect(status().isNoContent)

            assertThat(rowsOf(user, AchievementType.FIRST_FRIEND).single().seenAt).isNotNull()
        }

        @Test
        fun `should mark many codes at once`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)
            persistUserAchievement(user, AchievementType.FIRST_WIN)

            markSeen(user, """{"codes":["FIRST_FRIEND","FIRST_WIN"]}""").andExpect(status().isNoContent)

            assertThat(userAchievementRepository.findAllByUserId(user.id!!)).allMatch { it.seenAt != null }
        }

        @Test
        fun `should not return the achievements in unseen once they are marked as seen`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            markSeen(user, """{"codes":["FIRST_FRIEND"]}""").andExpect(status().isNoContent)

            getUnseen(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not show again an achievement unlocked by activity once it has been seen`() {
            val user = persistUser()
            persistFinishedMatchFor(user)

            getUnseen(user).andExpect(jsonPath("$[*].code").value(contains("FIRST_MATCH")))

            markSeen(user, """{"codes":["FIRST_MATCH"]}""").andExpect(status().isNoContent)

            getUnseen(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_MATCH')]").isEmpty)
            assertThat(rowsOf(user, AchievementType.FIRST_MATCH)).hasSize(1)
        }

        @Test
        fun `should mark only the given codes`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)
            persistUserAchievement(user, AchievementType.FIRST_WIN)

            markSeen(user, """{"codes":["FIRST_FRIEND"]}""").andExpect(status().isNoContent)

            assertThat(rowsOf(user, AchievementType.FIRST_FRIEND).single().seenAt).isNotNull()
            assertThat(rowsOf(user, AchievementType.FIRST_WIN).single().seenAt).isNull()
        }

        @Test
        fun `should not unlock a locked achievement when its code is marked as seen`() {
            val user = persistUser()

            markSeen(user, """{"codes":["FIRST_WIN"]}""").andExpect(status().isNoContent)

            assertThat(userAchievementRepository.findAllByUserId(user.id!!)).isEmpty()
            getMine(user).andExpect(jsonPath("$[?(@.code == 'FIRST_WIN')].unlocked").value(false))
        }

        @Test
        fun `should not mark another user's achievements as seen`() {
            val user = persistUser(username = "me")
            val other = persistUser(username = "other")
            persistUserAchievement(other, AchievementType.FIRST_FRIEND)

            markSeen(user, """{"codes":["FIRST_FRIEND"]}""").andExpect(status().isNoContent)

            assertThat(rowsOf(other, AchievementType.FIRST_FRIEND).single().seenAt).isNull()
        }

        @Test
        fun `should ignore unknown codes and still mark the known ones`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            markSeen(user, """{"codes":["NOT_AN_ACHIEVEMENT","FIRST_FRIEND"]}""").andExpect(status().isNoContent)

            assertThat(rowsOf(user, AchievementType.FIRST_FRIEND).single().seenAt).isNotNull()
        }

        @Test
        fun `should accept duplicated codes`() {
            val user = persistUser()
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)

            markSeen(user, """{"codes":["FIRST_FRIEND","FIRST_FRIEND"]}""").andExpect(status().isNoContent)

            assertThat(rowsOf(user, AchievementType.FIRST_FRIEND).single().seenAt).isNotNull()
        }

        @Test
        fun `should keep the original seen date when marking an already seen achievement`() {
            val user = persistUser()
            val seenAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)
            persistUserAchievement(user, AchievementType.FIRST_FRIEND, seenAt = seenAt)

            markSeen(user, """{"codes":["FIRST_FRIEND"]}""").andExpect(status().isNoContent)

            assertThat(rowsOf(user, AchievementType.FIRST_FRIEND).single().seenAt).isEqualTo(seenAt)
        }

        @Test
        fun `should return 400 when the list of codes is empty`() {
            markSeen(persistUser(), """{"codes":[]}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when codes are missing`() {
            markSeen(persistUser(), """{}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when codes is not a list`() {
            markSeen(persistUser(), """{"codes":"FIRST_FRIEND"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the body is malformed`() {
            markSeen(persistUser(), """{"codes":[""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the body is missing`() {
            perform(post("$BASE_URL/seen").contentType(MediaType.APPLICATION_JSON), persistUser()).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            markSeen(null, """{"codes":["FIRST_FRIEND"]}""").andExpect(status().isForbidden)
        }
    }
}