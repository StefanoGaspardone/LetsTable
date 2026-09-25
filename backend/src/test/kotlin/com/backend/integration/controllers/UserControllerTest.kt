package com.backend.integration.controllers

import com.backend.models.entities.*
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FriendRequestStatus
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import java.time.Instant
import java.util.*

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
    private lateinit var matchPlayerRepository: MatchPlayerRepository

    @Autowired
    private lateinit var friendRequestRepository: FriendRequestRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    @Autowired
    private lateinit var wishlistItemRepository: WishlistItemRepository

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

    private fun persistMatch(game: Game, createdBy: User, durationMinutes: Int? = 30): Match =
        matchRepository.saveAndFlush(Match(game = game, createdBy = createdBy, playedAt = Instant.now(), durationMinutes = durationMinutes))

    private fun persistWishlist(owner: User, isDefault: Boolean = false, isShared: Boolean = false): Wishlist =
        wishlistRepository.saveAndFlush(Wishlist(name = "My Wishlist", owner = owner, isShared = isShared, isDefault = isDefault))

    private fun persistWishlistItem(wishlist: Wishlist, game: Game, addedBy: User): WishlistItem =
        wishlistItemRepository.saveAndFlush(WishlistItem(wishlist = wishlist, game = game, addedBy = addedBy))

    private fun persistPlayer(match: Match, user: User? = null, guestName: String? = null): MatchPlayer =
        matchPlayerRepository.saveAndFlush(MatchPlayer(match = match, user = user, guestName = guestName))

    private fun persistRefreshToken(user: User, revoked: Boolean = false): RefreshToken =
        refreshTokenRepository.saveAndFlush(
            RefreshToken(
                user = user,
                tokenHash = "hash-${UUID.randomUUID()}",
                expiresAt = Instant.now().plusSeconds(3600),
                revoked = revoked,
            )
        )

    @AfterEach
    fun cleanUp() {
        friendRequestRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        wishlistItemRepository.deleteAll()
        wishlistRepository.deleteAll()
        matchPlayerRepository.deleteAll()
        matchRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/search
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/search")
    inner class SearchByUsernameTests {

        @Test
        fun `should return matching active users excluding the current user`() {
            val user = persistUser(username = "marco-self")
            val otherUser = persistUser(username = "marco-other")

            mockMvc.perform(
                get("/api/v1/users/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "marco")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherUser.id.toString()))
        }

        @Test
        fun `should return an empty list when no users match`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/users/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "nonexistent-name")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not return inactive users`() {
            val user = persistUser(username = "searcher")
            userRepository.saveAndFlush(
                User(
                    username = "inactive-marco",
                    email = "inactive@example.com",
                    passwordHash = "hash",
                    role = UserRole.USER,
                    accountStatus = AccountStatus.INACTIVE,
                )
            )

            mockMvc.perform(
                get("/api/v1/users/search")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("query", "marco")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/{userId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{userId}")
    inner class GetUserTests {

        @Test
        fun `should return the public profile of any user`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")

            mockMvc.perform(
                get("/api/v1/users/${target.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("target"))
        }

        @Test
        fun `should return 404 when user does not exist`() {
            val requester = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val target = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${target.id}")
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/me
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/me")
    inner class GetMyProfileTests {

        @Test
        fun `should return the authenticated user's own profile`() {
            val user = persistUser(username = "myself")

            mockMvc.perform(
                get("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(user.id.toString()))
                .andExpect(jsonPath("$.username").value("myself"))
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/users/me
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/users/me")
    inner class DeleteMyAccountTests {

        @Test
        fun `should anonymize the account and return 200`() {
            val user = persistUser(username = "to-delete")

            mockMvc.perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isOk)

            val updated = userRepository.findById(user.id!!).orElseThrow()
            assertThat(updated.username).isEqualTo("deleted-user-${user.id}")
            assertThat(updated.email).isEqualTo("deleted-${user.id}@letstable.invalid")
            assertThat(updated.passwordHash).isEmpty()
            assertThat(updated.accountStatus).isEqualTo(AccountStatus.DELETED)
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

            mockMvc.perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isOk)

            assertThat(matchRepository.findById(soloMatch.id!!)).isEmpty()
            assertThat(matchRepository.findById(sharedMatch.id!!)).isPresent
        }

        @Test
        fun `should revoke all active refresh tokens`() {
            val user = persistUser()
            val token = persistRefreshToken(user, revoked = false)

            mockMvc.perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isOk)

            val updated = refreshTokenRepository.findById(token.id!!).orElseThrow()
            assertThat(updated.revoked).isTrue()
        }

        @Test
        fun `should return a success message`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.message").value("Your account has been deleted"))
        }
    }

    // ---------------------------------------------------------------------
    // PATCH /api/v1/users/me
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/v1/users/me")
    inner class PatchMyProfileTests {

        @Test
        fun `should update the username`() {
            val user = persistUser(username = "oldname")

            mockMvc.perform(
                patch("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username": "newname"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("newname"))

            val updated = userRepository.findById(user.id!!).orElseThrow()
            assertThat(updated.username).isEqualTo("newname")
        }

        @Test
        fun `should update notificationsEnabled`() {
            val user = persistUser()

            mockMvc.perform(
                patch("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"notificationsEnabled": false}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.notificationsEnabled").value(false))

            val updated = userRepository.findById(user.id!!).orElseThrow()
            assertThat(updated.notificationsEnabled).isFalse()
        }

        @Test
        fun `should not change fields left null in the request`() {
            val user = persistUser(username = "unchanged")

            mockMvc.perform(
                patch("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("unchanged"))
                .andExpect(jsonPath("$.notificationsEnabled").value(true))
        }

        @Test
        fun `should return 409 when the username is already taken by another user`() {
            val user = persistUser(username = "requester")
            persistUser(username = "taken")

            mockMvc.perform(
                patch("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username": "taken"}""")
            ).andExpect(status().isConflict)

            val unchanged = userRepository.findById(user.id!!).orElseThrow()
            assertThat(unchanged.username).isEqualTo("requester")
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            mockMvc.perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username": "newname"}""")
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/{userId}/profile
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/profile")
    inner class GetUserProfileTests {

        @Test
        fun `should return public profile with stats and recent matches`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")
            val game = persistGame()

            val match = persistMatch(game, target)
            persistPlayer(match, user = target)

            mockMvc.perform(
                get("/api/v1/users/${target.id}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.user.id").value(target.id.toString()))
                .andExpect(jsonPath("$.user.username").value("target"))
        }

        @Test
        fun `should return zero stats for a user with no matches`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "no-matches")

            mockMvc.perform(
                get("/api/v1/users/${target.id}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.totalWins").value(0))
        }

        @Test
        fun `should be viewable by any authenticated user, not just the profile owner`() {
            val requester = persistUser(username = "unrelated-requester")
            val target = persistUser(username = "target")

            mockMvc.perform(
                get("/api/v1/users/${target.id}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            ).andExpect(status().isOk)
        }

        @Test
        fun `should return 404 when the user does not exist`() {
            val requester = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${UUID.randomUUID()}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val target = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${target.id}/profile")
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return SELF as friendship status when viewing your own profile`() {
            val user = persistUser(username = "myself")

            mockMvc.perform(
                get("/api/v1/users/${user.id}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.friendshipStatus").value("SELF"))
        }

        @Test
        fun `should return NONE as friendship status when there is no relationship`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "stranger")

            mockMvc.perform(
                get("/api/v1/users/${target.id}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.friendshipStatus").value("NONE"))
        }

        @Test
        fun `should return FRIENDS as friendship status when the users are already friends`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "friend")

            friendRequestRepository.saveAndFlush(
                FriendRequest(sender = requester, receiver = target, status = FriendRequestStatus.ACCEPTED)
            )

            mockMvc.perform(
                get("/api/v1/users/${target.id}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.friendshipStatus").value("FRIENDS"))
        }

        @Test
        fun `should return REQUEST_SENT when the requester has sent a pending request to the target`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")

            friendRequestRepository.saveAndFlush(
                FriendRequest(sender = requester, receiver = target, status = FriendRequestStatus.PENDING)
            )

            mockMvc.perform(
                get("/api/v1/users/${target.id}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.friendshipStatus").value("REQUEST_SENT"))
        }

        @Test
        fun `should return REQUEST_RECEIVED when the target has sent a pending request to the requester`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")

            friendRequestRepository.saveAndFlush(
                FriendRequest(sender = target, receiver = requester, status = FriendRequestStatus.PENDING)
            )

            mockMvc.perform(
                get("/api/v1/users/${target.id}/profile")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.friendshipStatus").value("REQUEST_RECEIVED"))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/{userId}/matches
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/matches")
    inner class GetUserMatchesTests {

        @Test
        fun `should return only completed matches for the target user`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")
            val game = persistGame()

            persistMatch(game, target, durationMinutes = 30)
            persistMatch(game, target, durationMinutes = null)

            mockMvc.perform(
                get("/api/v1/users/${target.id}/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should return an empty page when the user has no completed matches`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")

            mockMvc.perform(
                get("/api/v1/users/${target.id}/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should return 400 when querying your own matches via this endpoint`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${user.id}/matches")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val target = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${target.id}/matches")
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/{userId}/friends
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/friends")
    inner class GetUserFriendsTests {

        @Test
        fun `should return the target user's accepted friends`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")
            val friendOfTarget = persistUser(username = "friend-of-target")

            friendRequestRepository.saveAndFlush(
                FriendRequest(sender = target, receiver = friendOfTarget, status = FriendRequestStatus.ACCEPTED)
            )

            mockMvc.perform(
                get("/api/v1/users/${target.id}/friends")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(friendOfTarget.id.toString()))
        }

        @Test
        fun `should return an empty list when the target user has no friends`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")

            mockMvc.perform(
                get("/api/v1/users/${target.id}/friends")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 400 when querying your own friends via this endpoint`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${user.id}/friends")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val target = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${target.id}/friends")
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/users/{userId}/wishlist
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/wishlist")
    inner class GetUserDefaultWishlistTests {

        @Test
        fun `should return the games in the target user's default wishlist`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")
            val defaultWishlist = persistWishlist(target, isDefault = true)
            val game = persistGame()
            persistWishlistItem(defaultWishlist, game, target)

            mockMvc.perform(
                get("/api/v1/users/${target.id}/wishlist")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should not include games from a non-default wishlist`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")
            persistWishlist(target, isDefault = true)
            val otherWishlist = persistWishlist(target, isDefault = false)
            val game = persistGame()
            persistWishlistItem(otherWishlist, game, target)

            mockMvc.perform(
                get("/api/v1/users/${target.id}/wishlist")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should return 404 when the target user has no default wishlist`() {
            val requester = persistUser(username = "requester")
            val target = persistUser(username = "target")

            mockMvc.perform(
                get("/api/v1/users/${target.id}/wishlist")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(requester))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when querying your own wishlist via this endpoint`() {
            val user = persistUser()
            persistWishlist(user, isDefault = true)

            mockMvc.perform(
                get("/api/v1/users/${user.id}/wishlist")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val target = persistUser()

            mockMvc.perform(
                get("/api/v1/users/${target.id}/wishlist")
            ).andExpect(status().isForbidden)
        }
    }
}