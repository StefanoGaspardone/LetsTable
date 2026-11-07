package com.backend.integration.controllers

import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistItem
import com.backend.models.entities.WishlistMember
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.NotificationType
import com.backend.models.enums.UserRole
import com.backend.models.enums.WishlistActivityAction
import com.backend.repositories.GameRepository
import com.backend.repositories.UserRepository
import com.backend.repositories.WishlistActivityRepository
import com.backend.repositories.WishlistItemRepository
import com.backend.repositories.WishlistMemberRepository
import com.backend.repositories.WishlistRepository
import com.backend.services.JwtService
import com.backend.services.PushNotificationService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
    private lateinit var wishlistActivityRepository: WishlistActivityRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var pushNotificationService: PushNotificationService

    private var nextBggId = 1L

    companion object {
        private const val BASE_URL = "/api/v1/wishlists"
    }

    @AfterEach
    fun cleanUp() {
        wishlistActivityRepository.deleteAll()
        wishlistItemRepository.deleteAll()
        wishlistMemberRepository.deleteAll()
        wishlistRepository.deleteAll()
        gameRepository.deleteAll()
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

    private fun sendJson(request: AbstractMockHttpServletRequestBuilder<*>, user: User?, body: String): ResultActions =
        perform(request.contentType(MediaType.APPLICATION_JSON).content(body), user)

    private fun persistGame(name: String = "Test Game"): Game =
        gameRepository.saveAndFlush(Game(bggId = nextBggId++, name = name, lastSyncedAt = Instant.now()))

    private fun persistWishlist(owner: User, name: String = "My Wishlist", isShared: Boolean = false, isDefault: Boolean = false): Wishlist =
        wishlistRepository.saveAndFlush(Wishlist(name = name, owner = owner, isShared = isShared, isDefault = isDefault))

    private fun persistMember(wishlist: Wishlist, user: User): WishlistMember =
        wishlistMemberRepository.saveAndFlush(WishlistMember(wishlist = wishlist, user = user))

    private fun persistItem(wishlist: Wishlist, game: Game, addedBy: User): WishlistItem =
        wishlistItemRepository.saveAndFlush(WishlistItem(wishlist = wishlist, game = game, addedBy = addedBy))

    private fun sharedWishlistWithMember(): Triple<User, User, Wishlist> {
        val owner = persistUser(username = "owner")
        val member = persistUser(username = "member")
        val wishlist = persistWishlist(owner, isShared = true)
        persistMember(wishlist, member)
        return Triple(owner, member, wishlist)
    }

    private fun nameOf(wishlist: Wishlist): String = wishlistRepository.findById(wishlist.id!!).orElseThrow().name

    @Nested
    @DisplayName("POST /api/v1/wishlists")
    inner class CreateWishlistTests {

        private fun create(user: User?, body: String) = sendJson(post(BASE_URL), user, body)

        @Test
        fun `should create a shared wishlist owned by the current user`() {
            val owner = persistUser()

            create(owner, """{"name":"Games I want","isShared":true}""")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("Games I want"))
                .andExpect(jsonPath("$.isShared").value(true))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.owner.id").value(owner.id.toString()))

            val saved = wishlistRepository.findAll().single()
            assertThat(saved.owner.id).isEqualTo(owner.id)
            assertThat(saved.isDefault).isFalse()
        }

        @Test
        fun `should create a private wishlist`() {
            create(persistUser(), """{"name":"Secret","isShared":false}""")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.isShared").value(false))
        }

        @Test
        fun `should trim the name`() {
            create(persistUser(), """{"name":"  Spazi  ","isShared":false}""")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("Spazi"))
        }

        @Test
        fun `should never create a default wishlist even if requested`() {
            create(persistUser(), """{"name":"Fake default","isShared":false,"isDefault":true}""")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.isDefault").value(false))
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   "])
        fun `should return 400 when name is blank`(name: String) {
            create(persistUser(), """{"name":"$name","isShared":false}""").andExpect(status().isBadRequest)

            assertThat(wishlistRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 when name exceeds 100 characters`() {
            create(persistUser(), """{"name":"${"a".repeat(101)}","isShared":false}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when name is missing`() {
            create(persistUser(), """{"isShared":false}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the body is malformed`() {
            create(persistUser(), """{"name":""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            create(null, """{"name":"Games I want","isShared":true}""").andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists")
    inner class ListAccessibleWishlistsTests {

        private fun list(user: User?, vararg params: Pair<String, String>) =
            perform(get(BASE_URL).apply { params.forEach { (key, value) -> param(key, value) } }, user)

        @Test
        fun `should list owned, default and shared-member wishlists`() {
            val user = persistUser(username = "user")
            val otherOwner = persistUser(username = "other-owner")
            val default = persistWishlist(user, name = "Default", isDefault = true)
            val own = persistWishlist(user, name = "Own")
            val shared = persistWishlist(otherOwner, name = "Shared", isShared = true)
            persistMember(shared, user)

            list(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(default.id.toString(), own.id.toString(), shared.id.toString())))
                .andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should not include wishlists the user has no access to`() {
            val user = persistUser(username = "user")
            val otherOwner = persistUser(username = "other-owner")
            persistWishlist(otherOwner, isShared = false)
            persistWishlist(otherOwner, isShared = true)

            list(user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should not include the default wishlist of other users`() {
            val user = persistUser(username = "user")
            val mine = persistWishlist(user, isDefault = true)
            persistWishlist(persistUser(username = "other"), isDefault = true)

            list(user).andExpect(jsonPath("$.content[*].id").value(contains(mine.id.toString())))
        }

        @Test
        fun `should list a shared wishlist only once even with many members`() {
            val (owner, _, wishlist) = sharedWishlistWithMember()
            persistMember(wishlist, persistUser(username = "member-2"))

            list(owner).andExpect(jsonPath("$.totalElements").value(1))
        }

        @Test
        fun `should filter by SHARED type including wishlists shared with the user`() {
            val user = persistUser(username = "user")
            persistWishlist(user, name = "Private")
            persistWishlist(user, name = "Mine shared", isShared = true)
            val othersShared = persistWishlist(persistUser(username = "other"), name = "Theirs shared", isShared = true)
            persistMember(othersShared, user)

            list(user, "type" to "SHARED")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder("Mine shared", "Theirs shared")))
        }

        @Test
        fun `should filter by PRIVATE type and exclude the default wishlist`() {
            val user = persistUser(username = "user")
            persistWishlist(user, name = "Default", isDefault = true)
            persistWishlist(user, name = "Private")
            persistWishlist(user, name = "Shared", isShared = true)

            list(user, "type" to "PRIVATE")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].name").value(contains("Private")))
        }

        @Test
        fun `should return everything with type ALL`() {
            val user = persistUser(username = "user")
            persistWishlist(user, name = "Default", isDefault = true)
            persistWishlist(user, name = "Private")
            persistWishlist(user, name = "Shared", isShared = true)

            list(user, "type" to "ALL").andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should sort by name`() {
            val user = persistUser()
            listOf("Zeta", "Alpha", "Mid").forEach { persistWishlist(user, name = it) }

            list(user, "sort" to "name-asc").andExpect(jsonPath("$.content[*].name").value(contains("Alpha", "Mid", "Zeta")))
            list(user, "sort" to "name-desc").andExpect(jsonPath("$.content[*].name").value(contains("Zeta", "Mid", "Alpha")))
        }

        @Test
        fun `should paginate the results`() {
            val user = persistUser()
            listOf("A", "B", "C").forEach { persistWishlist(user, name = it) }

            list(user, "sort" to "name-asc", "size" to "2", "page" to "1")
                .andExpect(jsonPath("$.content[*].name").value(contains("C")))
                .andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            list(persistUser(), "sort" to "notAllowedField-asc").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when type is not valid`() {
            list(persistUser(), "type" to "PUBLIC").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            list(null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}")
    inner class GetWishlistTests {

        private fun getOne(user: User?, wishlistId: Any?) = perform(get("$BASE_URL/$wishlistId"), user)

        @Test
        fun `should return the wishlist to its owner`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner, name = "Mine")

            getOne(owner, wishlist.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(wishlist.id.toString()))
                .andExpect(jsonPath("$.name").value("Mine"))
        }

        @Test
        fun `should return a shared wishlist to its members`() {
            val (_, member, wishlist) = sharedWishlistWithMember()

            getOne(member, wishlist.id).andExpect(status().isOk)
        }

        @Test
        fun `should return a default wishlist to any user`() {
            val wishlist = persistWishlist(persistUser(username = "owner"), isDefault = true)

            getOne(persistUser(username = "viewer"), wishlist.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isDefault").value(true))
        }

        @Test
        fun `should return 403 for a private wishlist of another user`() {
            val wishlist = persistWishlist(persistUser(username = "owner"))

            getOne(persistUser(username = "viewer"), wishlist.id)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("You do not have access to this wishlist"))
        }

        @Test
        fun `should return 403 for a shared wishlist the user is not a member of`() {
            val wishlist = persistWishlist(persistUser(username = "owner"), isShared = true)

            getOne(persistUser(username = "viewer"), wishlist.id).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            getOne(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the id is not a UUID`() {
            getOne(persistUser(), "not-a-uuid").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            getOne(null, persistWishlist(persistUser()).id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists/default")
    inner class GetMyDefaultWishlistTests {

        @Test
        fun `should return the current user's default wishlist`() {
            val user = persistUser(username = "me")
            val default = persistWishlist(user, name = "La mia wishlist", isDefault = true)
            persistWishlist(user, name = "Other")
            persistWishlist(persistUser(username = "other"), isDefault = true)

            perform(get("$BASE_URL/default"), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(default.id.toString()))
                .andExpect(jsonPath("$.isDefault").value(true))
        }

        @Test
        fun `should return 404 when the user has no default wishlist`() {
            val user = persistUser()
            persistWishlist(user)

            perform(get("$BASE_URL/default"), user).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get("$BASE_URL/default"), null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/wishlists/{wishlistId}")
    inner class UpdateWishlistTests {

        private fun rename(user: User?, wishlistId: Any?, body: String) = sendJson(patch("$BASE_URL/$wishlistId"), user, body)

        @Test
        fun `should rename the wishlist when user is the owner`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)

            rename(owner, wishlist.id, """{"name":"Nuovo nome"}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(wishlist.id.toString()))
                .andExpect(jsonPath("$.name").value("Nuovo nome"))

            assertThat(nameOf(wishlist)).isEqualTo("Nuovo nome")
        }

        @Test
        fun `should trim whitespace from the new name`() {
            val owner = persistUser()

            rename(owner, persistWishlist(owner).id, """{"name":"  Spazi  "}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Spazi"))
        }

        @Test
        fun `should not change sharing or default flags`() {
            val owner = persistUser()

            rename(owner, persistWishlist(owner, isShared = true).id, """{"name":"Rinominata","isShared":false,"isDefault":true}""")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isShared").value(true))
                .andExpect(jsonPath("$.isDefault").value(false))
        }

        @Test
        fun `should return 403 when user is not the owner`() {
            val wishlist = persistWishlist(persistUser(username = "owner"))

            rename(persistUser(username = "intruder"), wishlist.id, """{"name":"Hack"}""")
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("Only the wishlist owner can perform this action"))

            assertThat(nameOf(wishlist)).isEqualTo("My Wishlist")
        }

        @Test
        fun `should return 403 when user is only a member of a shared wishlist`() {
            val (_, member, wishlist) = sharedWishlistWithMember()

            rename(member, wishlist.id, """{"name":"Hack"}""").andExpect(status().isForbidden)

            assertThat(nameOf(wishlist)).isEqualTo("My Wishlist")
        }

        @Test
        fun `should return 409 when renaming the default wishlist`() {
            val owner = persistUser()
            val defaultWishlist = persistWishlist(owner, isDefault = true)

            rename(owner, defaultWishlist.id, """{"name":"Nuovo nome"}""")
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.message").value("The default wishlist cannot be renamed or deleted"))

            assertThat(nameOf(defaultWishlist)).isEqualTo("My Wishlist")
        }

        @Test
        fun `should return 403 when another user tries to rename a default wishlist`() {
            val defaultWishlist = persistWishlist(persistUser(username = "owner"), isDefault = true)

            rename(persistUser(username = "intruder"), defaultWishlist.id, """{"name":"Hack"}""").andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            rename(persistUser(), UUID.randomUUID(), """{"name":"Nuovo nome"}""").andExpect(status().isNotFound)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   "])
        fun `should return 400 when name is blank`(name: String) {
            val owner = persistUser()

            rename(owner, persistWishlist(owner).id, """{"name":"$name"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when name exceeds 100 characters`() {
            val owner = persistUser()

            rename(owner, persistWishlist(owner).id, """{"name":"${"a".repeat(101)}"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            rename(null, persistWishlist(persistUser()).id, """{"name":"Nuovo nome"}""").andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{wishlistId}")
    inner class DeleteWishlistTests {

        private fun deleteOne(user: User?, wishlistId: Any?) = perform(delete("$BASE_URL/$wishlistId"), user)

        @Test
        fun `should delete the wishlist when user is the owner`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)

            deleteOne(owner, wishlist.id).andExpect(status().isNoContent)

            assertThat(wishlistRepository.findById(wishlist.id!!)).isEmpty
        }

        @Test
        fun `should delete a shared wishlist together with its items, members and activity`() {
            val (owner, member, wishlist) = sharedWishlistWithMember()
            val game = persistGame()

            sendJson(post("$BASE_URL/${wishlist.id}/items"), member, """{"gameId":"${game.id}"}""").andExpect(status().isCreated)
            assertThat(wishlistActivityRepository.findAll()).isNotEmpty

            deleteOne(owner, wishlist.id).andExpect(status().isNoContent)

            assertThat(wishlistRepository.findById(wishlist.id!!)).isEmpty
            assertThat(wishlistItemRepository.findAll()).isEmpty()
            assertThat(wishlistMemberRepository.findAll()).isEmpty()
            assertThat(wishlistActivityRepository.findAll()).isEmpty()
            assertThat(gameRepository.findById(game.id!!)).isPresent
        }

        @Test
        fun `should return 403 when user is not the owner`() {
            val wishlist = persistWishlist(persistUser(username = "owner"))

            deleteOne(persistUser(username = "intruder"), wishlist.id).andExpect(status().isForbidden)

            assertThat(wishlistRepository.findById(wishlist.id!!)).isPresent
        }

        @Test
        fun `should return 403 when user is only a member`() {
            val (_, member, wishlist) = sharedWishlistWithMember()

            deleteOne(member, wishlist.id).andExpect(status().isForbidden)

            assertThat(wishlistRepository.findById(wishlist.id!!)).isPresent
        }

        @Test
        fun `should return 409 when deleting the default wishlist`() {
            val owner = persistUser()
            val defaultWishlist = persistWishlist(owner, isDefault = true)

            deleteOne(owner, defaultWishlist.id).andExpect(status().isConflict)

            assertThat(wishlistRepository.findById(defaultWishlist.id!!)).isPresent
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            deleteOne(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val wishlist = persistWishlist(persistUser())

            deleteOne(null, wishlist.id).andExpect(status().isForbidden)

            assertThat(wishlistRepository.findById(wishlist.id!!)).isPresent
        }
    }

    @Nested
    @DisplayName("POST /api/v1/wishlists/{wishlistId}/members")
    inner class AddMemberTests {

        private fun addMember(user: User?, wishlistId: Any?, body: String) = sendJson(post("$BASE_URL/$wishlistId/members"), user, body)

        @Test
        fun `should add a member to a shared wishlist and notify them`() {
            val owner = persistUser(username = "owner")
            val newMember = persistUser(username = "new-member")
            val wishlist = persistWishlist(owner, name = "Natale", isShared = true)

            addMember(owner, wishlist.id, """{"userId":"${newMember.id}"}""")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.user.id").value(newMember.id.toString()))
                .andExpect(jsonPath("$.addedAt").exists())

            assertThat(wishlistMemberRepository.findByWishlistIdAndUserId(wishlist.id!!, newMember.id!!)).isPresent
            verify(pushNotificationService).sendToUser(eq(newMember.id!!), eq(NotificationType.WISHLIST_MEMBER_ADDED), anyString(), anyString(), anyMap())
        }

        @Test
        fun `should give the new member access to the wishlist`() {
            val owner = persistUser(username = "owner")
            val newMember = persistUser(username = "new-member")
            val wishlist = persistWishlist(owner, isShared = true)

            perform(get("$BASE_URL/${wishlist.id}"), newMember).andExpect(status().isForbidden)

            addMember(owner, wishlist.id, """{"userId":"${newMember.id}"}""").andExpect(status().isCreated)

            perform(get("$BASE_URL/${wishlist.id}"), newMember).andExpect(status().isOk)
        }

        @Test
        fun `should return 400 when wishlist is not shared`() {
            val owner = persistUser(username = "owner")
            val newMember = persistUser(username = "new-member")
            val wishlist = persistWishlist(owner, isShared = false)

            addMember(owner, wishlist.id, """{"userId":"${newMember.id}"}""").andExpect(status().isBadRequest)

            assertThat(wishlistMemberRepository.findAll()).isEmpty()
            verifyNoInteractions(pushNotificationService)
        }

        @Test
        fun `should return 400 when the default wishlist is not shared`() {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner, isDefault = true)

            addMember(owner, wishlist.id, """{"userId":"${persistUser(username = "friend").id}"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when the owner adds themselves`() {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner, isShared = true)

            addMember(owner, wishlist.id, """{"userId":"${owner.id}"}""").andExpect(status().isBadRequest)

            assertThat(wishlistMemberRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 when userId is missing or not a UUID`() {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner, isShared = true)

            addMember(owner, wishlist.id, """{}""").andExpect(status().isBadRequest)
            addMember(owner, wishlist.id, """{"userId":"not-a-uuid"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when user is not the owner`() {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner, isShared = true)

            addMember(persistUser(username = "intruder"), wishlist.id, """{"userId":"${persistUser(username = "new-member").id}"}""")
                .andExpect(status().isForbidden)

            assertThat(wishlistMemberRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 403 when a member tries to add another member`() {
            val (_, member, wishlist) = sharedWishlistWithMember()

            addMember(member, wishlist.id, """{"userId":"${persistUser(username = "new-member").id}"}""").andExpect(status().isForbidden)
        }

        @Test
        fun `should return 409 when user is already a member`() {
            val (owner, member, wishlist) = sharedWishlistWithMember()

            addMember(owner, wishlist.id, """{"userId":"${member.id}"}""").andExpect(status().isConflict)

            assertThat(wishlistMemberRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should return 404 when the user to add does not exist`() {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner, isShared = true)

            addMember(owner, wishlist.id, """{"userId":"${UUID.randomUUID()}"}""").andExpect(status().isNotFound)
        }

        @ParameterizedTest
        @ValueSource(strings = ["INACTIVE", "SUSPENDED", "DELETED"])
        fun `should return 404 when the user to add is not active`(accountStatus: String) {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner, isShared = true)
            val notActive = persistUser(username = "not-active", accountStatus = AccountStatus.valueOf(accountStatus))

            addMember(owner, wishlist.id, """{"userId":"${notActive.id}"}""").andExpect(status().isNotFound)

            assertThat(wishlistMemberRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            addMember(persistUser(), UUID.randomUUID(), """{"userId":"${UUID.randomUUID()}"}""").andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val wishlist = persistWishlist(persistUser(username = "owner"), isShared = true)

            addMember(null, wishlist.id, """{"userId":"${persistUser(username = "new-member").id}"}""").andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{wishlistId}/members/{memberUserId}")
    inner class RemoveMemberTests {

        private fun removeMember(user: User?, wishlistId: Any?, memberId: Any?) = perform(delete("$BASE_URL/$wishlistId/members/$memberId"), user)

        @Test
        fun `should remove the member when user is the owner`() {
            val (owner, member, wishlist) = sharedWishlistWithMember()

            removeMember(owner, wishlist.id, member.id).andExpect(status().isNoContent)

            assertThat(wishlistMemberRepository.findByWishlistIdAndUserId(wishlist.id!!, member.id!!)).isEmpty
        }

        @Test
        fun `should take away the access of the removed member`() {
            val (owner, member, wishlist) = sharedWishlistWithMember()

            removeMember(owner, wishlist.id, member.id).andExpect(status().isNoContent)

            perform(get("$BASE_URL/${wishlist.id}"), member).andExpect(status().isForbidden)
            sendJson(post("$BASE_URL/${wishlist.id}/items"), member, """{"gameId":"${persistGame().id}"}""").andExpect(status().isForbidden)
        }

        @Test
        fun `should keep the games added by the removed member`() {
            val (owner, member, wishlist) = sharedWishlistWithMember()
            persistItem(wishlist, persistGame(), member)

            removeMember(owner, wishlist.id, member.id).andExpect(status().isNoContent)

            assertThat(wishlistItemRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should return 403 when user is not the owner`() {
            val (_, member, wishlist) = sharedWishlistWithMember()

            removeMember(member, wishlist.id, member.id).andExpect(status().isForbidden)

            assertThat(wishlistMemberRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should return 404 when target user is not a member`() {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner, isShared = true)

            removeMember(owner, wishlist.id, persistUser(username = "not-member").id).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when the user is a member of a different wishlist`() {
            val (owner, member, _) = sharedWishlistWithMember()
            val otherWishlist = persistWishlist(owner, name = "Other", isShared = true)

            removeMember(owner, otherWishlist.id, member.id).andExpect(status().isNotFound)

            assertThat(wishlistMemberRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            removeMember(persistUser(), UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val (_, member, wishlist) = sharedWishlistWithMember()

            removeMember(null, wishlist.id, member.id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/wishlists/{wishlistId}/leave")
    inner class LeaveWishlistTests {

        private fun leave(user: User?, wishlistId: Any?) = perform(post("$BASE_URL/$wishlistId/leave"), user)

        @Test
        fun `should leave the wishlist when user is a member`() {
            val (_, member, wishlist) = sharedWishlistWithMember()

            leave(member, wishlist.id).andExpect(status().isNoContent)

            assertThat(wishlistMemberRepository.findByWishlistIdAndUserId(wishlist.id!!, member.id!!)).isEmpty
            perform(get("$BASE_URL/${wishlist.id}"), member).andExpect(status().isForbidden)
        }

        @Test
        fun `should not remove the other members`() {
            val (_, member, wishlist) = sharedWishlistWithMember()
            val stays = persistUser(username = "stays")
            persistMember(wishlist, stays)

            leave(member, wishlist.id).andExpect(status().isNoContent)

            assertThat(wishlistMemberRepository.findAll().map { it.user.id }).containsExactly(stays.id)
        }

        @Test
        fun `should return 404 when user is not a member`() {
            val wishlist = persistWishlist(persistUser(username = "owner"), isShared = true)

            leave(persistUser(username = "not-member"), wishlist.id).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when the owner tries to leave the own wishlist`() {
            val (owner, _, wishlist) = sharedWishlistWithMember()

            leave(owner, wishlist.id).andExpect(status().isNotFound)

            assertThat(wishlistRepository.findById(wishlist.id!!)).isPresent
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            leave(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val (_, _, wishlist) = sharedWishlistWithMember()

            leave(null, wishlist.id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}/members")
    inner class ListMembersTests {

        private fun members(user: User?, wishlistId: Any?) = perform(get("$BASE_URL/$wishlistId/members"), user)

        @Test
        fun `should list members to the owner`() {
            val (owner, _, wishlist) = sharedWishlistWithMember()

            members(owner, wishlist.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].user.username").value(contains("member")))
        }

        @Test
        fun `should list members to a member`() {
            val (_, member, wishlist) = sharedWishlistWithMember()
            persistMember(wishlist, persistUser(username = "member-2"))

            members(member, wishlist.id).andExpect(jsonPath("$[*].user.username").value(containsInAnyOrder("member", "member-2")))
        }

        @Test
        fun `should return an empty list for a wishlist without members`() {
            val owner = persistUser()

            members(owner, persistWishlist(owner, isShared = true).id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 403 to a user without access`() {
            val (_, _, wishlist) = sharedWishlistWithMember()

            members(persistUser(username = "stranger"), wishlist.id).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            members(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val (_, _, wishlist) = sharedWishlistWithMember()

            members(null, wishlist.id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/wishlists/{wishlistId}/items")
    inner class AddItemTests {

        private fun addItem(user: User?, wishlistId: Any?, body: String) = sendJson(post("$BASE_URL/$wishlistId/items"), user, body)

        @Test
        fun `should add a game to the wishlist when user is the owner`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game = persistGame()

            addItem(owner, wishlist.id, """{"gameId":"${game.id}"}""")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.game.id").value(game.id.toString()))
                .andExpect(jsonPath("$.addedBy.id").value(owner.id.toString()))

            assertThat(wishlistItemRepository.existsByWishlistIdAndGameId(wishlist.id!!, game.id!!)).isTrue()
        }

        @Test
        fun `should not record activity for a private wishlist`() {
            val owner = persistUser()

            addItem(owner, persistWishlist(owner).id, """{"gameId":"${persistGame().id}"}""").andExpect(status().isCreated)

            assertThat(wishlistActivityRepository.findAll()).isEmpty()
        }

        @Test
        fun `should not record activity for a shared wishlist without members`() {
            val owner = persistUser()

            addItem(owner, persistWishlist(owner, isShared = true).id, """{"gameId":"${persistGame().id}"}""").andExpect(status().isCreated)

            assertThat(wishlistActivityRepository.findAll()).isEmpty()
        }

        @Test
        fun `should allow a member to add a game and record the activity`() {
            val (_, member, wishlist) = sharedWishlistWithMember()

            addItem(member, wishlist.id, """{"gameId":"${persistGame().id}"}""")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.addedBy.id").value(member.id.toString()))

            val activity = wishlistActivityRepository.findAll().single()
            assertThat(activity.action).isEqualTo(WishlistActivityAction.ADDED)
            assertThat(activity.actor.id).isEqualTo(member.id)
        }

        @Test
        fun `should let the same game be in two different wishlists`() {
            val owner = persistUser()
            val game = persistGame()
            persistItem(persistWishlist(owner, name = "First"), game, owner)

            addItem(owner, persistWishlist(owner, name = "Second").id, """{"gameId":"${game.id}"}""").andExpect(status().isCreated)
        }

        @Test
        fun `should return 403 when user has no access to a private wishlist`() {
            val wishlist = persistWishlist(persistUser(username = "owner"))

            addItem(persistUser(username = "intruder"), wishlist.id, """{"gameId":"${persistGame().id}"}""").andExpect(status().isForbidden)

            assertThat(wishlistItemRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 403 when another user adds to a default wishlist`() {
            val wishlist = persistWishlist(persistUser(username = "owner"), isDefault = true)

            addItem(persistUser(username = "intruder"), wishlist.id, """{"gameId":"${persistGame().id}"}""").andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when game does not exist`() {
            val owner = persistUser()

            addItem(owner, persistWishlist(owner).id, """{"gameId":"${UUID.randomUUID()}"}""").andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            addItem(persistUser(), UUID.randomUUID(), """{"gameId":"${persistGame().id}"}""").andExpect(status().isNotFound)
        }

        @Test
        fun `should return 409 when game already in wishlist`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game = persistGame()
            persistItem(wishlist, game, owner)

            addItem(owner, wishlist.id, """{"gameId":"${game.id}"}""").andExpect(status().isConflict)

            assertThat(wishlistItemRepository.findAll()).hasSize(1)
        }

        @Test
        fun `should return 400 when gameId is missing or not a UUID`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)

            addItem(owner, wishlist.id, """{}""").andExpect(status().isBadRequest)
            addItem(owner, wishlist.id, """{"gameId":"not-a-uuid"}""").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            addItem(null, persistWishlist(persistUser()).id, """{"gameId":"${persistGame().id}"}""").andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{wishlistId}/items/{itemId}")
    inner class RemoveItemTests {

        private fun removeItem(user: User?, wishlistId: Any?, itemId: Any?) = perform(delete("$BASE_URL/$wishlistId/items/$itemId"), user)

        @Test
        fun `should remove the item when user is the owner`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val item = persistItem(wishlist, persistGame(), owner)

            removeItem(owner, wishlist.id, item.id).andExpect(status().isNoContent)

            assertThat(wishlistItemRepository.findById(item.id!!)).isEmpty
        }

        @Test
        fun `should let a member remove a game added by the owner and record the activity`() {
            val (owner, member, wishlist) = sharedWishlistWithMember()
            val item = persistItem(wishlist, persistGame(), owner)

            removeItem(member, wishlist.id, item.id).andExpect(status().isNoContent)

            assertThat(wishlistItemRepository.findById(item.id!!)).isEmpty
            assertThat(wishlistActivityRepository.findAll().map { it.action }).containsExactly(WishlistActivityAction.REMOVED)
        }

        @Test
        fun `should return 403 when user has no access`() {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner)
            val item = persistItem(wishlist, persistGame(), owner)

            removeItem(persistUser(username = "intruder"), wishlist.id, item.id).andExpect(status().isForbidden)

            assertThat(wishlistItemRepository.findById(item.id!!)).isPresent
        }

        @Test
        fun `should return 404 when the item belongs to another wishlist`() {
            val user = persistUser(username = "user")
            val mine = persistWishlist(user, name = "Mine")
            val othersWishlist = persistWishlist(persistUser(username = "other"), name = "Theirs")
            val othersItem = persistItem(othersWishlist, persistGame(), othersWishlist.owner)

            removeItem(user, mine.id, othersItem.id).andExpect(status().isNotFound)

            assertThat(wishlistItemRepository.findById(othersItem.id!!)).isPresent
        }

        @Test
        fun `should return 404 when item does not exist`() {
            val owner = persistUser()

            removeItem(owner, persistWishlist(owner).id, UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val item = persistItem(wishlist, persistGame(), owner)

            removeItem(null, wishlist.id, item.id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}/items")
    inner class ListItemsTests {

        private fun items(user: User?, wishlistId: Any?, vararg params: Pair<String, String>) =
            perform(get("$BASE_URL/$wishlistId/items").apply { params.forEach { (key, value) -> param(key, value) } }, user)

        @Test
        fun `should list items in a wishlist`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            persistItem(wishlist, persistGame(name = "Ark Nova"), owner)
            persistItem(wishlist, persistGame(name = "Dune Imperium"), owner)

            items(owner, wishlist.id)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(containsInAnyOrder("Ark Nova", "Dune Imperium")))
                .andExpect(jsonPath("$.totalElements").value(2))
        }

        @Test
        fun `should not list items of other wishlists`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner, name = "Mine")
            persistItem(persistWishlist(owner, name = "Other"), persistGame(), owner)

            items(owner, wishlist.id).andExpect(jsonPath("$.content.length()").value(0))
        }

        @Test
        fun `should filter by gameName ignoring case`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            persistItem(wishlist, persistGame(name = "Ark Nova"), owner)
            persistItem(wishlist, persistGame(name = "Dune Imperium"), owner)

            items(owner, wishlist.id, "gameName" to "ARK")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[*].game.name").value(contains("Ark Nova")))
        }

        @Test
        fun `should sort by game name`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            listOf("Wingspan", "Azul", "Catan").forEach { persistItem(wishlist, persistGame(name = it), owner) }

            items(owner, wishlist.id, "sort" to "game.name-asc").andExpect(jsonPath("$.content[*].game.name").value(contains("Azul", "Catan", "Wingspan")))
        }

        @Test
        fun `should paginate the results`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            listOf("A", "B", "C").forEach { persistItem(wishlist, persistGame(name = it), owner) }

            items(owner, wishlist.id, "sort" to "game.name-asc", "size" to "2", "page" to "1")
                .andExpect(jsonPath("$.content[*].game.name").value(contains("C")))
                .andExpect(jsonPath("$.totalElements").value(3))
        }

        @Test
        fun `should list the items of another user's default wishlist`() {
            val owner = persistUser(username = "owner")
            val wishlist = persistWishlist(owner, isDefault = true)
            persistItem(wishlist, persistGame(), owner)

            items(persistUser(username = "viewer"), wishlist.id).andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should let a member list the items`() {
            val (owner, member, wishlist) = sharedWishlistWithMember()
            persistItem(wishlist, persistGame(), owner)

            items(member, wishlist.id).andExpect(jsonPath("$.content.length()").value(1))
        }

        @Test
        fun `should return 403 for a private wishlist of another user`() {
            items(persistUser(username = "viewer"), persistWishlist(persistUser(username = "owner")).id).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 400 when sort field is not allowed`() {
            val owner = persistUser()

            items(owner, persistWishlist(owner).id, "sort" to "notAllowedField-asc").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            items(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            items(null, persistWishlist(persistUser()).id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}/items/status")
    inner class GetItemStatusTests {

        private fun itemStatus(user: User?, wishlistId: Any?, gameId: String?) =
            perform(get("$BASE_URL/$wishlistId/items/status").apply { gameId?.let { param("gameId", it) } }, user)

        @Test
        fun `should return inWishlist true with itemId when game is present`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)
            val game = persistGame()
            val item = persistItem(wishlist, game, owner)

            itemStatus(owner, wishlist.id, game.id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inWishlist").value(true))
                .andExpect(jsonPath("$.itemId").value(item.id.toString()))
        }

        @Test
        fun `should return inWishlist false when game is absent`() {
            val owner = persistUser()

            itemStatus(owner, persistWishlist(owner).id, persistGame().id.toString())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.inWishlist").value(false))
                .andExpect(jsonPath("$.itemId").doesNotExist())
        }

        @Test
        fun `should return false when the game is only in another wishlist`() {
            val owner = persistUser()
            val game = persistGame()
            persistItem(persistWishlist(owner, name = "Other"), game, owner)

            itemStatus(owner, persistWishlist(owner, name = "Mine").id, game.id.toString()).andExpect(jsonPath("$.inWishlist").value(false))
        }

        @Test
        fun `should return false for a game that does not exist`() {
            val owner = persistUser()

            itemStatus(owner, persistWishlist(owner).id, UUID.randomUUID().toString()).andExpect(jsonPath("$.inWishlist").value(false))
        }

        @Test
        fun `should return 403 for a private wishlist of another user`() {
            itemStatus(persistUser(username = "viewer"), persistWishlist(persistUser(username = "owner")).id, persistGame().id.toString())
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 400 when gameId is missing or not a UUID`() {
            val owner = persistUser()
            val wishlist = persistWishlist(owner)

            itemStatus(owner, wishlist.id, null).andExpect(status().isBadRequest)
            itemStatus(owner, wishlist.id, "not-a-uuid").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when wishlist does not exist`() {
            itemStatus(persistUser(), UUID.randomUUID(), UUID.randomUUID().toString()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            itemStatus(null, persistWishlist(persistUser()).id, UUID.randomUUID().toString()).andExpect(status().isForbidden)
        }
    }
}