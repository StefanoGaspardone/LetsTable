package com.backend.integration.controllers

import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import com.backend.models.entities.WishlistMember
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.GameRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistMemberRepository
import com.backend.repositories.WishlistRepository
import com.backend.services.JwtService
import com.backend.services.PushNotificationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class WishlistControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    @Autowired
    private lateinit var wishlistMemberRepository: WishlistMemberRepository

    @Autowired
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var pushNotificationService: PushNotificationService

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

    private fun persistWishlist(owner: User, isShared: Boolean = false, isDefault: Boolean = false): Wishlist =
        wishlistRepository.saveAndFlush(Wishlist(name = "My Wishlist", owner = owner, isShared = isShared, isDefault = isDefault))

    private fun persistMember(wishlist: Wishlist, user: User): WishlistMember =
        wishlistMemberRepository.saveAndFlush(WishlistMember(wishlist = wishlist, user = user))

    private fun persistItem(wishlist: Wishlist, game: Game, addedBy: User): WishlistItem =
        wishlistItemRepository.saveAndFlush(WishlistItem(wishlist = wishlist, game = game, addedBy = addedBy))

    @BeforeEach
    fun stubPushNotificationService() {
        doNothing().whenever(pushNotificationService).sendToUser(any(), any(), any(), any())
    }

    @AfterEach
    fun cleanUp() {
        wishlistItemRepository.deleteAll()
        wishlistMemberRepository.deleteAll()
        wishlistRepository.deleteAll()
        gameRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/wishlists
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/wishlists")
    inner class CreateWishlistTests {

        @Test
        fun `should create a wishlist and return 201`() {
            val owner = persistUser()
            val payload = """{"name":"Games I want","isShared":true}"""

            mockMvc.perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("Games I want"))
                .andExpect(jsonPath("$.isShared").value(true))
                .andExpect(jsonPath("$.isDefault").value(false))

            assertThat(wishlistRepository.findAllAccessibleByUser(owner.id!!)).hasSize(1)
        }

        @Test
        fun `should return 400 when name is blank`() {
            val owner = persistUser()
            val payload = """{"name":"","isShared":false}"""

            mockMvc.perform(
                post("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/wishlists
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/wishlists")
    inner class ListAccessibleWishlistsTests {

        @Test
        fun `should list owned and shared-member wishlists`() {
            val user = persistUser(username = "user")
            val otherOwner = persistUser(username = "other-owner")
            persistWishlist(user)
            val sharedWishlist = persistWishlist(otherOwner, isShared = true)
            persistMember(sharedWishlist, user)

            mockMvc.perform(
                get("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        fun `should not include wishlists the user has no access to`() {
            val user = persistUser(username = "user")
            val otherOwner = persistUser(username = "other-owner")
            persistWishlist(otherOwner, isShared = false)

            mockMvc.perform(
                get("/api/v1/wishlists")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/wishlists/{wishlistId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{wishlistId}")
    inner class DeleteWishlistTests {

        @Test
        fun `should delete the wishlist when user is the owner`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)

            mockMvc.perform(
                delete("/api/v1/wishlists/${wishlist.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
            ).andExpect(status().isNoContent)

            assertThat(wishlistRepository.findById(wishlist.id!!)).isEmpty()
        }

        @Test
        fun `should return 403 when user is not the owner`() {
            val owner = persistUser(username = "owner")
            val otherUser = persistUser(username = "intruder")
            val wishlist = persistWishlist(owner)

            mockMvc.perform(
                delete("/api/v1/wishlists/${wishlist.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
            ).andExpect(status().isForbidden)

            assertThat(wishlistRepository.findById(wishlist.id!!)).isPresent
        }

        @Test
        fun `should return 404 when the default wishlist cannot be modified`() {
            val owner = persistUser()
            val defaultWishlist = persistWishlist(owner, isDefault = true)

            mockMvc.perform(
                delete("/api/v1/wishlists/${defaultWishlist.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/wishlists/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/wishlists/{wishlistId}/members
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/wishlists/{wishlistId}/members")
    inner class AddMemberTests {

        @Test
        fun `should add a member to a shared wishlist and return 201`() {
            val owner = persistUser(username = "owner")
            val newMember = persistUser(username = "new-member")
            val wishlist = persistWishlist(owner, isShared = true)
            val payload = """{"userId":"${newMember.id}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/members")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.user.id").value(newMember.id.toString()))
        }

        @Test
        fun `should return 400 when wishlist is not shared`() {
            val owner = persistUser(username = "owner")
            val newMember = persistUser(username = "new-member")
            val wishlist = persistWishlist(owner, isShared = false)
            val payload = """{"userId":"${newMember.id}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/members")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when user is not the owner`() {
            val owner = persistUser(username = "owner")
            val otherUser = persistUser(username = "intruder")
            val newMember = persistUser(username = "new-member")
            val wishlist = persistWishlist(owner, isShared = true)
            val payload = """{"userId":"${newMember.id}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/members")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 409 when user is already a member`() {
            val owner = persistUser(username = "owner")
            val member = persistUser(username = "member")
            val wishlist = persistWishlist(owner, isShared = true)
            persistMember(wishlist, member)
            val payload = """{"userId":"${member.id}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/members")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isConflict)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            val owner = persistUser()
            val payload = """{"userId":"${UUID.randomUUID()}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${UUID.randomUUID()}/members")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/wishlists/{wishlistId}/members/{memberUserId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{wishlistId}/members/{memberUserId}")
    inner class RemoveMemberTests {

        @Test
        fun `should remove the member when user is the owner`() {
            val owner = persistUser(username = "owner")
            val member = persistUser(username = "member")
            val wishlist = persistWishlist(owner, isShared = true)
            persistMember(wishlist, member)

            mockMvc.perform(
                delete("/api/v1/wishlists/${wishlist.id}/members/${member.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
            ).andExpect(status().isNoContent)

            assertThat(wishlistMemberRepository.findByWishlistIdAndUserId(wishlist.id!!, member.id!!)).isEmpty()
        }

        @Test
        fun `should return 403 when user is not the owner`() {
            val owner = persistUser(username = "owner")
            val member = persistUser(username = "member")
            val wishlist = persistWishlist(owner, isShared = true)
            persistMember(wishlist, member)

            mockMvc.perform(
                delete("/api/v1/wishlists/${wishlist.id}/members/${member.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(member))
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 when target user is not a member`() {
            val owner = persistUser(username = "owner")
            val notMember = persistUser(username = "not-member")
            val wishlist = persistWishlist(owner, isShared = true)

            mockMvc.perform(
                delete("/api/v1/wishlists/${wishlist.id}/members/${notMember.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/wishlists/{wishlistId}/leave
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/wishlists/{wishlistId}/leave")
    inner class LeaveWishlistTests {

        @Test
        fun `should leave the wishlist when user is a member`() {
            val owner = persistUser(username = "owner")
            val member = persistUser(username = "member")
            val wishlist = persistWishlist(owner, isShared = true)
            persistMember(wishlist, member)

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/leave")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(member))
            ).andExpect(status().isNoContent)

            assertThat(wishlistMemberRepository.findByWishlistIdAndUserId(wishlist.id!!, member.id!!)).isEmpty()
        }

        @Test
        fun `should return 403 when user is not a member`() {
            val owner = persistUser(username = "owner")
            val notMember = persistUser(username = "not-member")
            val wishlist = persistWishlist(owner, isShared = true)

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/leave")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(notMember))
            ).andExpect(status().isForbidden)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/wishlists/{wishlistId}/members
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}/members")
    inner class ListMembersTests {

        @Test
        fun `should list members of a wishlist`() {
            val owner = persistUser(username = "owner")
            val member = persistUser(username = "member")
            val wishlist = persistWishlist(owner, isShared = true)
            persistMember(wishlist, member)

            mockMvc.perform(
                get("/api/v1/wishlists/${wishlist.id}/members")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.username").value("member"))
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/wishlists/${UUID.randomUUID()}/members")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/wishlists/{wishlistId}/items
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/wishlists/{wishlistId}/items")
    inner class AddItemTests {

        @Test
        fun `should add a game to the wishlist when user is the owner`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game = persistGame()
            val payload = """{"gameId":"${game.id}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.game.id").value(game.id.toString()))
        }

        @Test
        fun `should allow a member to add a game to a shared wishlist`() {
            val owner = persistUser(username = "owner")
            val member = persistUser(username = "member")
            val wishlist = persistWishlist(owner, isShared = true)
            persistMember(wishlist, member)
            val game = persistGame()
            val payload = """{"gameId":"${game.id}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isCreated)
        }

        @Test
        fun `should return 403 when user has no access to a private wishlist`() {
            val owner = persistUser(username = "owner")
            val otherUser = persistUser(username = "intruder")
            val wishlist = persistWishlist(owner, isShared = false)
            val game = persistGame()
            val payload = """{"gameId":"${game.id}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when game does not exist`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val payload = """{"gameId":"${UUID.randomUUID()}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 409 when game already in wishlist`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game = persistGame()
            persistItem(wishlist, game, owner)
            val payload = """{"gameId":"${game.id}"}"""

            mockMvc.perform(
                post("/api/v1/wishlists/${wishlist.id}/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isConflict)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/wishlists/{wishlistId}/items/{itemId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{wishlistId}/items/{itemId}")
    inner class RemoveItemTests {

        @Test
        fun `should remove the item when user is the owner`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game = persistGame()
            val item = persistItem(wishlist, game, owner)

            mockMvc.perform(
                delete("/api/v1/wishlists/${wishlist.id}/items/${item.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
            ).andExpect(status().isNoContent)

            assertThat(wishlistItemRepository.findById(item.id!!)).isEmpty()
        }

        @Test
        fun `should return 403 when user has no access`() {
            val owner = persistUser(username = "owner")
            val otherUser = persistUser(username = "intruder")
            val wishlist = persistWishlist(owner, isShared = false)
            val game = persistGame()
            val item = persistItem(wishlist, game, owner)

            mockMvc.perform(
                delete("/api/v1/wishlists/${wishlist.id}/items/${item.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(otherUser))
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when item does not exist`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)

            mockMvc.perform(
                delete("/api/v1/wishlists/${wishlist.id}/items/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/wishlists/{wishlistId}/items
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}/items")
    inner class ListItemsTests {

        @Test
        fun `should list items in a wishlist`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game1 = persistGame(name = "Ark Nova")
            val game2 = persistGame(name = "Dune Imperium")
            persistItem(wishlist, game1, owner)
            persistItem(wishlist, game2, owner)

            mockMvc.perform(
                get("/api/v1/wishlists/${wishlist.id}/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(2))
        }

        @Test
        fun `should filter by gameName`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val arkNova = persistGame(name = "Ark Nova")
            val duneImperium = persistGame(name = "Dune Imperium")
            persistItem(wishlist, arkNova, owner)
            persistItem(wishlist, duneImperium, owner)

            mockMvc.perform(
                get("/api/v1/wishlists/${wishlist.id}/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .param("gameName", "ark")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].game.name").value("Ark Nova"))
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)

            mockMvc.perform(
                get("/api/v1/wishlists/${wishlist.id}/items")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .param("sort", "notAllowedField-asc")
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/wishlists/{wishlistId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}")
    inner class GetWishlistTests {

        @Test
        fun `should return wishlist details for any authenticated user`() {
            val owner = persistUser(username = "owner")
            val viewer = persistUser(username = "viewer")
            val wishlist = persistWishlist(owner)

            mockMvc.perform(
                get("/api/v1/wishlists/${wishlist.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(viewer))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(wishlist.id.toString()))
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/wishlists/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/wishlists/{wishlistId}/items/status
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}/items/status")
    inner class GetItemStatusTests {

        @Test
        fun `should return inWishlist true with itemId when game is present`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game = persistGame()
            val item = persistItem(wishlist, game, owner)

            mockMvc.perform(
                get("/api/v1/wishlists/${wishlist.id}/items/status")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .param("gameId", game.id.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inWishlist").value(true))
                .andExpect(jsonPath("$.itemId").value(item.id.toString()))
        }

        @Test
        fun `should return inWishlist false when game is absent`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game = persistGame()

            mockMvc.perform(
                get("/api/v1/wishlists/${wishlist.id}/items/status")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(owner))
                    .param("gameId", game.id.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inWishlist").value(false))
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/wishlists/${UUID.randomUUID()}/items/status")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .param("gameId", UUID.randomUUID().toString())
            ).andExpect(status().isNotFound)
        }
    }
}