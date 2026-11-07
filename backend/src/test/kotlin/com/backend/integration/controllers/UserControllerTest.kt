package com.backend.integration.controllers

import com.backend.models.entities.CollectionItem
import com.backend.models.entities.FriendRequest
import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.MatchTeam
import com.backend.models.entities.PushToken
import com.backend.models.entities.RefreshToken
import com.backend.models.entities.User
import com.backend.models.entities.UserAchievement
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.AchievementType
import com.backend.models.enums.FriendRequestStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.FriendRequestRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.MatchPlayerRepository
import com.backend.repositories.MatchRepository
import com.backend.repositories.MatchTeamRepository
import com.backend.repositories.PushTokenRepository
import com.backend.repositories.RefreshTokenRepository
import com.backend.repositories.UploadedFileRepository
import com.backend.repositories.UserAchievementRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistRepository
import com.backend.services.JwtService
import com.backend.services.StorageService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.BDDMockito.willAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class UserControllerTest : AbstractIntegrationTest() {

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
    private lateinit var friendRequestRepository: FriendRequestRepository

    @Autowired
    private lateinit var userAchievementRepository: UserAchievementRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var pushTokenRepository: PushTokenRepository

    @Autowired
    private lateinit var collectionItemRepository: CollectionItemRepository

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    @Autowired
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @Autowired
    private lateinit var uploadedFileRepository: UploadedFileRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var storageService: StorageService

    private val jsonMapper = JsonMapper.builder().build()
    private var nextBggId = 1L

    companion object {
        private const val BASE_URL = "/api/v1/users"
    }

    @BeforeEach
    fun setUpStorageMock() {
        willAnswer { it.getArgument<InputStream>(1).readBytes(); null }
            .given(storageService).putObject(anyString(), any<InputStream>(), anyLong(), anyString())
    }

    @AfterEach
    fun cleanUp() {
        pushTokenRepository.deleteAll()
        userAchievementRepository.deleteAll()
        friendRequestRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        collectionItemRepository.deleteAll()
        wishlistItemRepository.deleteAll()
        wishlistRepository.deleteAll()
        matchPlayerRepository.deleteAll()
        matchTeamRepository.deleteAll()
        matchRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.findAll().filter { it.avatarId != null }.forEach { userRepository.saveAndFlush(it.apply { avatarId = null }) }
        uploadedFileRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun persistUser(username: String = "stefano", role: UserRole = UserRole.USER, accountStatus: AccountStatus = AccountStatus.ACTIVE): User =
        userRepository.saveAndFlush(
            User(
                username = username,
                email = "$username@example.com",
                passwordHash = "irrelevant-hash",
                role = role,
                accountStatus = accountStatus,
            )
        )

    private fun authHeader(user: User): String =
        "Bearer ${jwtService.generateAccessToken(user.id!!, user.role.name)}"

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User?): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    private fun persistGame(name: String = "Test Game"): Game =
        gameRepository.saveAndFlush(Game(bggId = nextBggId++, name = name, lastSyncedAt = Instant.now()))

    private fun persistMatch(game: Game, createdBy: User, durationMinutes: Int? = 30, playedAt: Instant = Instant.now()): Match =
        matchRepository.saveAndFlush(Match(game = game, createdBy = createdBy, playedAt = playedAt, durationMinutes = durationMinutes))

    private fun persistPlayer(match: Match, user: User? = null, guestName: String? = null, isWinner: Boolean = false): MatchPlayer =
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, user = user, guestName = guestName, isWinner = isWinner))

    private fun persistTeamWithPlayer(match: Match, user: User, isWinner: Boolean): MatchTeam {
        val team = matchTeamRepository.saveAndFlush(MatchTeam(match = match, color = "blue", isWinner = isWinner))
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, team = team, user = user))
        return team
    }

    private fun persistFriendship(sender: User, receiver: User, status: FriendRequestStatus = FriendRequestStatus.ACCEPTED): FriendRequest =
        friendRequestRepository.saveAndFlush(FriendRequest(sender = sender, receiver = receiver, status = status))

    private fun persistWishlist(owner: User, isDefault: Boolean = false, isShared: Boolean = false): Wishlist =
        wishlistRepository.saveAndFlush(Wishlist(name = "My Wishlist", owner = owner, isShared = isShared, isDefault = isDefault))

    private fun persistWishlistItem(wishlist: Wishlist, game: Game, addedBy: User): WishlistItem =
        wishlistItemRepository.saveAndFlush(WishlistItem(wishlist = wishlist, game = game, addedBy = addedBy))

    private fun persistRefreshToken(user: User): RefreshToken =
        refreshTokenRepository.saveAndFlush(RefreshToken(user = user, tokenHash = "hash-${UUID.randomUUID()}", expiresAt = Instant.now().plusSeconds(3600), revoked = false))

    private fun persistUserAchievement(user: User, type: AchievementType): UserAchievement =
        userAchievementRepository.saveAndFlush(UserAchievement(userId = user.id!!, achievementCode = type.name, unlockedAt = Instant.now(), seenAt = null))

    private fun uploadAvatar(user: User): UUID {
        val body = perform(multipart("$BASE_URL/me/avatar").file(MockMultipartFile("file", "avatar.png", "image/png", "png".toByteArray())), user)
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return UUID.fromString(jsonMapper.readTree(body).get("id").asString())
    }

    private fun reload(user: User): User = userRepository.findById(user.id!!).orElseThrow()

    @Nested
    @DisplayName("GET /api/v1/users/search")
    inner class SearchByUsernameTests {

        private fun search(user: User, query: String) = perform(get("$BASE_URL/search").param("query", query), user)

        @Test
        fun `should return matching active users excluding the current user`() {
            val user = persistUser(username = "marco-self")
            val otherUser = persistUser(username = "marco-other")

            search(user, "marco")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].id").value(contains(otherUser.id.toString())))
        }

        @Test
        fun `should match ignoring case and surrounding spaces`() {
            val user = persistUser(username = "searcher")
            persistUser(username = "Marco")

            search(user, "  mARc  ").andExpect(jsonPath("$[*].username").value(contains("Marco")))
        }

        @Test
        fun `should match a part in the middle of the username`() {
            val user = persistUser(username = "searcher")
            persistUser(username = "supermarcone")

            search(user, "marco").andExpect(jsonPath("$[*].username").value(contains("supermarcone")))
        }

        @Test
        fun `should return results ordered by username`() {
            val user = persistUser(username = "searcher")
            listOf("marco-c", "marco-a", "marco-b").forEach { persistUser(username = it) }

            search(user, "marco").andExpect(jsonPath("$[*].username").value(contains("marco-a", "marco-b", "marco-c")))
        }

        @Test
        fun `should return at most 20 users`() {
            val user = persistUser(username = "searcher")
            (1..25).forEach { persistUser(username = "player-%02d".format(it)) }

            search(user, "player").andExpect(jsonPath("$.length()").value(20))
        }

        @ParameterizedTest
        @ValueSource(strings = ["m", " m ", "", "   "])
        fun `should return an empty list when the query is shorter than two characters`(query: String) {
            val user = persistUser(username = "searcher")
            persistUser(username = "marco")

            search(user, query)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @ParameterizedTest
        @ValueSource(strings = ["%%", "__", "_a%"])
        fun `should treat like wildcards as plain characters`(query: String) {
            val user = persistUser(username = "searcher")
            persistUser(username = "marco")

            search(user, query).andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should find a username containing an underscore`() {
            val user = persistUser(username = "searcher")
            persistUser(username = "marco_rossi")
            persistUser(username = "marcoxrossi")

            search(user, "o_r").andExpect(jsonPath("$[*].username").value(contains("marco_rossi")))
        }

        @Test
        fun `should return an empty list when no users match`() {
            search(persistUser(), "nonexistent-name")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not return inactive, suspended or deleted users`() {
            val user = persistUser(username = "searcher")
            persistUser(username = "marco-inactive", accountStatus = AccountStatus.INACTIVE)
            persistUser(username = "marco-suspended", accountStatus = AccountStatus.SUSPENDED)
            persistUser(username = "marco-deleted", accountStatus = AccountStatus.DELETED)
            persistUser(username = "marco-active")

            search(user, "marco").andExpect(jsonPath("$[*].username").value(contains("marco-active")))
        }

        @Test
        fun `should return 400 when the query parameter is missing`() {
            perform(get("$BASE_URL/search"), persistUser()).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get("$BASE_URL/search").param("query", "marco"), null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me")
    inner class GetMyProfileTests {

        @Test
        fun `should return the authenticated user's own profile`() {
            val user = persistUser(username = "myself")

            perform(get("$BASE_URL/me"), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(user.id.toString()))
                .andExpect(jsonPath("$.username").value("myself"))
                .andExpect(jsonPath("$.email").value("myself@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.notificationsEnabled").value(true))
        }

        @Test
        fun `should not be shadowed by the userId path`() {
            perform(get("$BASE_URL/me"), persistUser()).andExpect(status().isOk)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get("$BASE_URL/me"), null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me")
    inner class PatchMyProfileTests {

        private fun update(user: User?, body: String) =
            perform(patch("$BASE_URL/me").contentType(MediaType.APPLICATION_JSON).content(body), user)

        @Test
        fun `should update the username`() {
            val user = persistUser(username = "oldname")

            update(user, """{"username":"newname"}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("newname"))

            assertThat(reload(user).username).isEqualTo("newname")
        }

        @Test
        fun `should let the user change only the case of the own username`() {
            val user = persistUser(username = "stefano")

            update(user, """{"username":"Stefano"}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("Stefano"))
        }

        @Test
        fun `should update notificationsEnabled`() {
            val user = persistUser()

            update(user, """{"notificationsEnabled":false}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.notificationsEnabled").value(false))

            assertThat(reload(user).notificationsEnabled).isFalse()
        }

        @Test
        fun `should update many fields at once`() {
            val user = persistUser(username = "oldname")

            update(user, """{"username":"newname","notificationsEnabled":false}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("newname"))
                .andExpect(jsonPath("$.notificationsEnabled").value(false))
        }

        @Test
        fun `should not change fields left null in the request`() {
            val user = persistUser(username = "unchanged")

            update(user, """{}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("unchanged"))
                .andExpect(jsonPath("$.notificationsEnabled").value(true))
        }

        @Test
        fun `should set an own uploaded file as avatar`() {
            val user = persistUser()
            val fileId = uploadAvatar(user)

            update(user, """{"avatarId":"$fileId"}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.avatarId").value(fileId.toString()))

            assertThat(reload(user).avatarId).isEqualTo(fileId)
        }

        @Test
        fun `should remove the avatar when removeAvatar is true even if an avatarId is sent`() {
            val user = persistUser()
            val fileId = uploadAvatar(user)
            userRepository.saveAndFlush(reload(user).apply { avatarId = fileId })

            update(user, """{"avatarId":"$fileId","removeAvatar":true}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.avatarId").doesNotExist())

            assertThat(reload(user).avatarId).isNull()
            assertThat(uploadedFileRepository.findById(fileId)).isPresent
        }

        @Test
        fun `should return 404 when the avatar belongs to another user`() {
            val user = persistUser(username = "me")
            val othersFile = uploadAvatar(persistUser(username = "other"))

            update(user, """{"avatarId":"$othersFile"}""").andExpect(status().isNotFound)

            assertThat(reload(user).avatarId).isNull()
        }

        @Test
        fun `should return 404 when the avatar does not exist`() {
            update(persistUser(), """{"avatarId":"${UUID.randomUUID()}"}""").andExpect(status().isNotFound)
        }

        @Test
        fun `should not change anything when the avatar is invalid`() {
            val user = persistUser(username = "oldname")

            update(user, """{"username":"newname","avatarId":"${UUID.randomUUID()}"}""").andExpect(status().isNotFound)

            assertThat(reload(user).username).isEqualTo("oldname")
        }

        @Test
        fun `should return 409 when the username is already taken by another user`() {
            val user = persistUser(username = "requester")
            persistUser(username = "taken")

            update(user, """{"username":"taken"}""").andExpect(status().isConflict)

            assertThat(reload(user).username).isEqualTo("requester")
        }

        @Test
        fun `should return 409 when the username is taken with a different case`() {
            val user = persistUser(username = "requester")
            persistUser(username = "taken")

            update(user, """{"username":"TAKEN"}""").andExpect(status().isConflict)
        }

        @ParameterizedTest
        @ValueSource(strings = ["ab", "   ", " padded", "padded ", "    abc    "])
        fun `should return 400 for an invalid username`(username: String) {
            val user = persistUser(username = "original")

            update(user, """{"username":"$username"}""").andExpect(status().isBadRequest)

            assertThat(reload(user).username).isEqualTo("original")
        }

        @Test
        fun `should return 400 when the username is longer than 50 characters`() {
            update(persistUser(), """{"username":"${"a".repeat(51)}"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the body is malformed`() {
            update(persistUser(), """{"username":""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            update(null, """{"username":"newname"}""").andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me")
    inner class DeleteMyAccountTests {

        private fun deleteMe(user: User) = perform(delete("$BASE_URL/me"), user)

        @Test
        fun `should anonymize the account and return a success message`() {
            val user = persistUser(username = "to-delete")

            deleteMe(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.message").value("Your account has been deleted"))

            val updated = reload(user)
            assertThat(updated.username).isEqualTo("deleted-user-${user.id}")
            assertThat(updated.email).isEqualTo("deleted-${user.id}@letstable.invalid")
            assertThat(updated.passwordHash).isEmpty()
            assertThat(updated.notificationsEnabled).isFalse()
            assertThat(updated.accountStatus).isEqualTo(AccountStatus.DELETED)
        }

        @Test
        fun `should free the old username for new users`() {
            val user = persistUser(username = "to-delete")

            deleteMe(user).andExpect(status().isOk)

            assertThat(userRepository.existsByUsernameIgnoreCase("to-delete")).isFalse()
        }

        @Test
        fun `should delete solo matches but keep matches shared with others`() {
            val user = persistUser(username = "to-delete")
            val otherUser = persistUser(username = "stays")
            val game = persistGame()

            val soloMatch = persistMatch(game, user)
            persistPlayer(soloMatch, user = user)

            val sharedMatch = persistMatch(game, user)
            persistPlayer(sharedMatch, user = user)
            persistPlayer(sharedMatch, user = otherUser)

            deleteMe(user).andExpect(status().isOk)

            assertThat(matchRepository.findById(soloMatch.id!!)).isEmpty
            assertThat(matchPlayerRepository.findAllByMatchId(soloMatch.id!!)).isEmpty()
            assertThat(matchRepository.findById(sharedMatch.id!!)).isPresent
        }

        @Test
        fun `should consider a match played only with guests as solo`() {
            val user = persistUser(username = "to-delete")
            val match = persistMatch(persistGame(), user)
            persistPlayer(match, user = user)
            persistPlayer(match, guestName = "Luca")

            deleteMe(user).andExpect(status().isOk)

            assertThat(matchRepository.findById(match.id!!)).isEmpty
        }

        @Test
        fun `should delete a solo team match together with its teams`() {
            val user = persistUser(username = "to-delete")
            val match = persistMatch(persistGame(), user)
            persistTeamWithPlayer(match, user, isWinner = true)

            deleteMe(user).andExpect(status().isOk)

            assertThat(matchRepository.findById(match.id!!)).isEmpty
            assertThat(matchTeamRepository.findAll()).isEmpty()
        }

        @Test
        fun `should turn the user into a guest with the former username in shared matches`() {
            val user = persistUser(username = "to-delete")
            val otherUser = persistUser(username = "stays")
            val sharedMatch = persistMatch(persistGame(), otherUser)
            persistPlayer(sharedMatch, user = user, isWinner = true)
            persistPlayer(sharedMatch, user = otherUser)

            deleteMe(user).andExpect(status().isOk)

            val players = matchPlayerRepository.findAllByMatchId(sharedMatch.id!!)
            assertThat(players).hasSize(2)
            val converted = players.single { it.user == null }
            assertThat(converted.guestName).isEqualTo("to-delete")
            assertThat(converted.isWinner).isTrue()
        }

        @Test
        fun `should revoke all active refresh tokens`() {
            val user = persistUser()
            val token = persistRefreshToken(user)

            deleteMe(user).andExpect(status().isOk)

            assertThat(refreshTokenRepository.findById(token.id!!).orElseThrow().revoked).isTrue()
        }

        @Test
        fun `should remove push tokens, achievements, friendships, collection and wishlists`() {
            val user = persistUser(username = "to-delete")
            val friend = persistUser(username = "friend")
            val game = persistGame()
            pushTokenRepository.saveAndFlush(PushToken(user = user, token = "ExponentPushToken[me]"))
            persistUserAchievement(user, AchievementType.FIRST_FRIEND)
            persistFriendship(user, friend)
            collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = game))
            persistWishlistItem(persistWishlist(user, isDefault = true), game, user)

            deleteMe(user).andExpect(status().isOk)

            assertThat(pushTokenRepository.findAll()).isEmpty()
            assertThat(userAchievementRepository.findAllByUserId(user.id!!)).isEmpty()
            assertThat(friendRequestRepository.findAll()).isEmpty()
            assertThat(collectionItemRepository.countByUserId(user.id!!)).isZero()
            assertThat(wishlistRepository.findAll()).isEmpty()
            assertThat(wishlistItemRepository.findAll()).isEmpty()
        }

        @Test
        fun `should not touch the data of other users`() {
            val user = persistUser(username = "to-delete")
            val other = persistUser(username = "other")
            val friendOfOther = persistUser(username = "friend-of-other")
            val game = persistGame()
            pushTokenRepository.saveAndFlush(PushToken(user = other, token = "ExponentPushToken[other]"))
            persistFriendship(other, friendOfOther)
            collectionItemRepository.saveAndFlush(CollectionItem(user = other, game = game))
            persistWishlist(other, isDefault = true)

            deleteMe(user).andExpect(status().isOk)

            assertThat(pushTokenRepository.findAll()).hasSize(1)
            assertThat(friendRequestRepository.findAll()).hasSize(1)
            assertThat(collectionItemRepository.countByUserId(other.id!!)).isEqualTo(1)
            assertThat(wishlistRepository.findAll()).hasSize(1)
            assertThat(reload(other).accountStatus).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        fun `should delete the avatar files after commit`() {
            val user = persistUser()
            val fileId = uploadAvatar(user)
            userRepository.saveAndFlush(reload(user).apply { avatarId = fileId })

            deleteMe(user).andExpect(status().isOk)

            assertThat(reload(user).avatarId).isNull()
            assertThat(uploadedFileRepository.findById(fileId)).isEmpty
            verify(storageService).deleteObject(startsWith("user_avatar/${user.id}/"))
        }

        @Test
        fun `should not call the storage when the user has no avatar`() {
            deleteMe(persistUser()).andExpect(status().isOk)

            verify(storageService, never()).deleteObject(anyString())
        }

        @Test
        fun `should return 404 when the account was already deleted`() {
            val user = persistUser()

            deleteMe(user).andExpect(status().isOk)
            deleteMe(user).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(delete("$BASE_URL/me"), null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{userId}")
    inner class GetUserTests {

        @Test
        fun `should return the public account information of any user`() {
            val target = persistUser(username = "target")

            perform(get("$BASE_URL/${target.id}"), persistUser(username = "requester"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(target.id.toString()))
                .andExpect(jsonPath("$.username").value("target"))
        }

        @Test
        fun `should return a deleted user as anonymized`() {
            val target = persistUser(username = "target")
            perform(delete("$BASE_URL/me"), target).andExpect(status().isOk)

            perform(get("$BASE_URL/${target.id}"), persistUser(username = "requester"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value(not("target")))
        }

        @Test
        fun `should return 404 when user does not exist`() {
            perform(get("$BASE_URL/${UUID.randomUUID()}"), persistUser()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the id is not a UUID`() {
            perform(get("$BASE_URL/not-a-uuid"), persistUser()).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get("$BASE_URL/${persistUser().id}"), null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/profile")
    inner class GetUserProfileTests {

        private fun profile(requester: User?, targetId: Any?) = perform(get("$BASE_URL/$targetId/profile"), requester)

        @Test
        fun `should return the public profile with account info`() {
            val target = persistUser(username = "target")

            profile(persistUser(username = "requester"), target.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.user.id").value(target.id.toString()))
                .andExpect(jsonPath("$.user.username").value("target"))
        }

        @Test
        fun `should count completed matches and wins of the target`() {
            val target = persistUser(username = "target")
            val game = persistGame()
            persistPlayer(persistMatch(game, target), user = target, isWinner = true)
            persistPlayer(persistMatch(game, target), user = target, isWinner = false)
            persistPlayer(persistMatch(game, target, durationMinutes = null), user = target, isWinner = true)

            profile(persistUser(username = "requester"), target.id)
                .andExpect(jsonPath("$.totalMatches").value(2))
                .andExpect(jsonPath("$.totalWins").value(1))
        }

        @Test
        fun `should count matches played in someone else's match and team wins`() {
            val target = persistUser(username = "target")
            val creator = persistUser(username = "creator")
            persistTeamWithPlayer(persistMatch(persistGame(), creator), target, isWinner = true)

            profile(persistUser(username = "requester"), target.id)
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.totalWins").value(1))
        }

        @Test
        fun `should not count the wins of other players in the target's matches`() {
            val target = persistUser(username = "target")
            val friend = persistUser(username = "friend")
            val match = persistMatch(persistGame(), target)
            persistPlayer(match, user = target, isWinner = false)
            persistPlayer(match, user = friend, isWinner = true)

            profile(persistUser(username = "requester"), target.id)
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should return zero stats for a user with no matches`() {
            profile(persistUser(username = "requester"), persistUser(username = "no-matches").id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should return SELF as friendship status when viewing your own profile`() {
            val user = persistUser(username = "myself")

            profile(user, user.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.friendshipStatus").value("SELF"))
        }

        @Test
        fun `should return NONE as friendship status when there is no relationship`() {
            profile(persistUser(username = "requester"), persistUser(username = "stranger").id)
                .andExpect(jsonPath("$.friendshipStatus").value("NONE"))
        }

        @Test
        fun `should return FRIENDS whoever sent the accepted request`() {
            val requester = persistUser(username = "requester")
            val sentByMe = persistUser(username = "sent-by-me")
            val sentToMe = persistUser(username = "sent-to-me")
            persistFriendship(requester, sentByMe)
            persistFriendship(sentToMe, requester)

            profile(requester, sentByMe.id).andExpect(jsonPath("$.friendshipStatus").value("FRIENDS"))
            profile(requester, sentToMe.id).andExpect(jsonPath("$.friendshipStatus").value("FRIENDS"))
        }

        @Test
        fun `should return REQUEST_SENT when the requester has sent a pending request to the target`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")
            persistFriendship(requester, target, FriendRequestStatus.PENDING)

            profile(requester, target.id).andExpect(jsonPath("$.friendshipStatus").value("REQUEST_SENT"))
        }

        @Test
        fun `should return REQUEST_RECEIVED when the target has sent a pending request to the requester`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")
            persistFriendship(target, requester, FriendRequestStatus.PENDING)

            profile(requester, target.id).andExpect(jsonPath("$.friendshipStatus").value("REQUEST_RECEIVED"))
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            profile(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the id is not a UUID`() {
            profile(persistUser(), "not-a-uuid").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            profile(null, persistUser().id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/matches")
    inner class GetUserMatchesTests {

        private fun matches(requester: User?, targetId: Any?, vararg params: Pair<String, String>) =
            perform(get("$BASE_URL/$targetId/matches").apply { params.forEach { (key, value) -> param(key, value) } }, requester)

        @Test
        fun `should return only completed matches for the target user`() {
            val target = persistUser(username = "target")
            val game = persistGame()
            val completed = persistMatch(game, target, durationMinutes = 30)
            persistMatch(game, target, durationMinutes = null)

            matches(persistUser(username = "requester"), target.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].id").value(contains(completed.id.toString())))
                .andExpect(jsonPath("$.totalElements").value(1))
        }

        @Test
        fun `should include matches where the target is a player but not the creator`() {
            val target = persistUser(username = "target")
            val match = persistMatch(persistGame(), persistUser(username = "creator"))
            persistPlayer(match, user = target)

            matches(persistUser(username = "requester"), target.id)
                .andExpect(jsonPath("$.content[*].id").value(contains(match.id.toString())))
        }

        @Test
        fun `should not include the requester's matches without the target`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")
            persistMatch(persistGame(), requester)

            matches(requester, target.id).andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should sort by playedAt desc by default and asc when requested`() {
            val target = persistUser(username = "target")
            val game = persistGame()
            val now = Instant.now()
            val oldest = persistMatch(game, target, playedAt = now.minus(Duration.ofDays(3)))
            val newest = persistMatch(game, target, playedAt = now)
            val requester = persistUser(username = "requester")

            matches(requester, target.id).andExpect(jsonPath("$.content[*].id").value(contains(newest.id.toString(), oldest.id.toString())))
            matches(requester, target.id, "sort" to "playedAt-asc").andExpect(jsonPath("$.content[*].id").value(contains(oldest.id.toString(), newest.id.toString())))
        }

        @Test
        fun `should paginate the results`() {
            val target = persistUser(username = "target")
            val game = persistGame()
            repeat(3) { persistMatch(game, target) }

            matches(persistUser(username = "requester"), target.id, "size" to "2", "page" to "1")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should return an empty page when the user has no completed matches`() {
            matches(persistUser(username = "requester"), persistUser(username = "target").id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            matches(persistUser(username = "requester"), persistUser(username = "target").id, "sort" to "notAllowedField-asc")
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when querying your own matches via this endpoint`() {
            val user = persistUser()

            matches(user, user.id)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Cannot access your own resource via this endpoint, use the dedicated endpoint instead"))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            matches(null, persistUser().id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/friends")
    inner class GetUserFriendsTests {

        private fun friends(requester: User?, targetId: Any?) = perform(get("$BASE_URL/$targetId/friends"), requester)

        @Test
        fun `should return the target's accepted friends whoever sent the request`() {
            val target = persistUser(username = "target")
            val sentByTarget = persistUser(username = "sent-by-target")
            val sentToTarget = persistUser(username = "sent-to-target")
            persistFriendship(target, sentByTarget)
            persistFriendship(sentToTarget, target)

            friends(persistUser(username = "requester"), target.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(sentByTarget.id.toString(), sentToTarget.id.toString())))
        }

        @Test
        fun `should not include pending requests`() {
            val target = persistUser(username = "target")
            persistFriendship(target, persistUser(username = "pending"), FriendRequestStatus.PENDING)

            friends(persistUser(username = "requester"), target.id).andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return an empty list when the target user has no friends`() {
            friends(persistUser(username = "requester"), persistUser(username = "target").id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 400 when querying your own friends via this endpoint`() {
            val user = persistUser()

            friends(user, user.id).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            friends(null, persistUser().id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/wishlist")
    inner class GetUserDefaultWishlistTests {

        private fun wishlist(requester: User?, targetId: Any?, vararg params: Pair<String, String>) =
            perform(get("$BASE_URL/$targetId/wishlist").apply { params.forEach { (key, value) -> param(key, value) } }, requester)

        @Test
        fun `should return the games in the target user's default wishlist`() {
            val target = persistUser(username = "target")
            persistWishlistItem(persistWishlist(target, isDefault = true), persistGame(name = "Catan"), target)

            wishlist(persistUser(username = "requester"), target.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
        }

        @Test
        fun `should not include games from a non-default wishlist`() {
            val target = persistUser(username = "target")
            persistWishlist(target, isDefault = true)
            persistWishlistItem(persistWishlist(target, isDefault = false, isShared = true), persistGame(), target)

            wishlist(persistUser(username = "requester"), target.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should paginate the results`() {
            val target = persistUser(username = "target")
            val default = persistWishlist(target, isDefault = true)
            repeat(3) { persistWishlistItem(default, persistGame(name = "Game $it"), target) }

            wishlist(persistUser(username = "requester"), target.id, "size" to "2", "page" to "1")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should return 404 when the target user has no default wishlist`() {
            wishlist(persistUser(username = "requester"), persistUser(username = "target").id).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when the target user does not exist`() {
            wishlist(persistUser(username = "requester"), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when querying your own wishlist via this endpoint`() {
            val user = persistUser()
            persistWishlist(user, isDefault = true)

            wishlist(user, user.id).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            wishlist(null, persistUser().id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/achievements")
    inner class GetUserAchievementsTests {

        private fun achievements(requester: User?, targetId: Any?) = perform(get("$BASE_URL/$targetId/achievements"), requester)

        @Test
        fun `should return the whole catalog for the target user`() {
            achievements(persistUser(username = "requester"), persistUser(username = "target").id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(AchievementType.entries.size))
        }

        @Test
        fun `should mark as unlocked only the achievements the target user has unlocked`() {
            val target = persistUser(username = "target")
            persistUserAchievement(target, AchievementType.FIRST_MATCH)

            achievements(persistUser(username = "requester"), target.id)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_MATCH')].unlocked").value(true))
                .andExpect(jsonPath("$[?(@.code == 'FIRST_WIN')].unlocked").value(false))
        }

        @Test
        fun `should not return the requester's achievements`() {
            val requester = persistUser(username = "requester")
            persistUserAchievement(requester, AchievementType.FIRST_MATCH)

            achievements(requester, persistUser(username = "target").id)
                .andExpect(jsonPath("$[?(@.code == 'FIRST_MATCH')].unlocked").value(false))
        }

        @Test
        fun `should show the target user's progress on locked achievements`() {
            val target = persistUser(username = "target")
            persistPlayer(persistMatch(persistGame(), target), user = target)

            achievements(persistUser(username = "requester"), target.id)
                .andExpect(jsonPath("$[?(@.code == 'MATCHES_10')].progress").value(1))
                .andExpect(jsonPath("$[?(@.code == 'MATCHES_10')].unlocked").value(false))
        }

        @Test
        fun `should not unlock achievements for the target user when someone else views them`() {
            val target = persistUser(username = "target")
            persistPlayer(persistMatch(persistGame(), target), user = target)

            achievements(persistUser(username = "requester"), target.id).andExpect(status().isOk)

            assertThat(userAchievementRepository.findAllByUserId(target.id!!)).isEmpty()
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            achievements(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when querying your own achievements via this endpoint`() {
            val user = persistUser()

            achievements(user, user.id).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            achievements(null, persistUser().id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/stats")
    inner class GetMyStatsTests {

        private fun stats(user: User?) = perform(get("$BASE_URL/me/stats"), user)

        @Test
        fun `should return zero stats for a new user`() {
            stats(persistUser())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.totalWins").value(0))
                .andExpect(jsonPath("$.totalGames").value(0))
                .andExpect(jsonPath("$.totalFriends").value(0))
        }

        @Test
        fun `should count completed matches, wins, collection and accepted friends`() {
            val user = persistUser(username = "me")
            val friendA = persistUser(username = "friend-a")
            val friendB = persistUser(username = "friend-b")
            val game = persistGame()
            persistPlayer(persistMatch(game, user), user = user, isWinner = true)
            persistPlayer(persistMatch(game, user), user = user, isWinner = false)
            persistPlayer(persistMatch(game, user, durationMinutes = null), user = user, isWinner = true)
            collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = game))
            collectionItemRepository.saveAndFlush(CollectionItem(user = user, game = persistGame(name = "Other")))
            persistFriendship(user, friendA)
            persistFriendship(friendB, user)
            persistFriendship(user, persistUser(username = "pending"), FriendRequestStatus.PENDING)

            stats(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(2))
                .andExpect(jsonPath("$.totalWins").value(1))
                .andExpect(jsonPath("$.totalGames").value(2))
                .andExpect(jsonPath("$.totalFriends").value(2))
        }

        @Test
        fun `should not count the data of other users`() {
            val user = persistUser(username = "me")
            val other = persistUser(username = "other")
            val game = persistGame()
            persistPlayer(persistMatch(game, other), user = other, isWinner = true)
            collectionItemRepository.saveAndFlush(CollectionItem(user = other, game = game))
            persistFriendship(other, persistUser(username = "friend-of-other"))

            stats(user)
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.totalWins").value(0))
                .andExpect(jsonPath("$.totalGames").value(0))
                .andExpect(jsonPath("$.totalFriends").value(0))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            stats(null).andExpect(status().isForbidden)
        }
    }
}