package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.AddWishlistItemRequest
import com.backend.models.dtos.AddWishlistMemberRequest
import com.backend.models.dtos.CreateWishlistRequest
import com.backend.models.entities.*
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.*
import com.backend.services.PushNotificationService
import com.backend.services.WishlistService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WishlistServiceTest {

    @Mock
    private lateinit var wishlistRepository: WishlistRepository

    @Mock
    private lateinit var wishlistMemberRepository: WishlistMemberRepository

    @Mock
    private lateinit var wishlistItemRepository: WishlistItemRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var gameRepository: GameRepository

    @Mock
    private lateinit var pushNotificationService: PushNotificationService

    @InjectMocks
    private lateinit var wishlistService: WishlistService

    private val ownerId: UUID = UUID.randomUUID()
    private val wishlistId: UUID = UUID.randomUUID()

    private fun buildUser(id: UUID = UUID.randomUUID(), username: String = "user-${UUID.randomUUID()}") = User(
        id = id,
        username = username,
        email = "$username@example.com",
        passwordHash = "hash",
        role = UserRole.USER,
        accountStatus = AccountStatus.ACTIVE,
    )

    private fun buildGame(id: UUID = UUID.randomUUID()) = Game(
        id = id,
        bggId = 1L,
        name = "Test Game",
        lastSyncedAt = Instant.now(),
    )

    private fun buildWishlist(
        id: UUID = wishlistId,
        owner: User,
        isShared: Boolean = false,
        isDefault: Boolean = false,
    ) = Wishlist(id = id, name = "My Wishlist", owner = owner, isShared = isShared, isDefault = isDefault)

    // ---------------------------------------------------------------------
    // createWishlist
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("createWishlist")
    inner class CreateWishlistTests {

        @Test
        fun `should create wishlist for existing user`() {
            val owner = buildUser(id = ownerId)
            val request = CreateWishlistRequest(name = "Games I want", isShared = true)
            val savedWishlist = buildWishlist(owner = owner, isShared = true)

            whenever(userRepository.findById(ownerId)).thenReturn(Optional.of(owner))
            whenever(wishlistRepository.save(any())).thenReturn(savedWishlist)

            val result = wishlistService.createWishlist(ownerId, request)

            assertThat(result.name).isEqualTo(savedWishlist.name)
            assertThat(result.isShared).isTrue()
            assertThat(result.owner.id).isEqualTo(ownerId)
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when owner does not exist`() {
            val request = CreateWishlistRequest(name = "Games I want", isShared = false)
            whenever(userRepository.findById(ownerId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.createWishlist(ownerId, request)
            }.isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verify(wishlistRepository, never()).save(any())
        }

        @Test
        fun `should rethrow generic exception when repository fails`() {
            val owner = buildUser(id = ownerId)
            val request = CreateWishlistRequest(name = "Games I want", isShared = false)
            whenever(userRepository.findById(ownerId)).thenReturn(Optional.of(owner))
            whenever(wishlistRepository.save(any())).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.createWishlist(ownerId, request)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // deleteWishlist
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("deleteWishlist")
    inner class DeleteWishlistTests {

        @Test
        fun `should delete wishlist when user is the owner and it is not default`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isDefault = false)
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            wishlistService.deleteWishlist(ownerId, wishlistId)

            verify(wishlistRepository).delete(wishlist)
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.deleteWishlist(ownerId, wishlistId)
            }.isInstanceOf(WishlistNotFoundException::class.java)

            verify(wishlistRepository, never()).delete(any())
        }

        @Test
        fun `should throw CannotModifyDefaultWishlistException when wishlist is default`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isDefault = true)
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.deleteWishlist(ownerId, wishlistId)
            }.isInstanceOf(CannotModifyDefaultWishlistException::class.java)

            verify(wishlistRepository, never()).delete(any())
        }

        @Test
        fun `should check default status before ownership status`() {
            val differentOwner = buildUser()
            val wishlist = buildWishlist(owner = differentOwner, isDefault = true)
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.deleteWishlist(ownerId, wishlistId)
            }.isInstanceOf(CannotModifyDefaultWishlistException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerException when user is not the owner`() {
            val differentOwner = buildUser()
            val wishlist = buildWishlist(owner = differentOwner, isDefault = false)
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.deleteWishlist(ownerId, wishlistId)
            }.isInstanceOf(NotWishlistOwnerException::class.java)

            verify(wishlistRepository, never()).delete(any())
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.deleteWishlist(ownerId, wishlistId)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // listAccessibleWishlists
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("listAccessibleWishlists")
    inner class ListAccessibleWishlistsTests {

        @Test
        fun `should return accessible wishlists for user`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner)
            whenever(wishlistRepository.findAllAccessibleByUser(ownerId)).thenReturn(listOf(wishlist))

            val result = wishlistService.listAccessibleWishlists(ownerId)

            assertThat(result).hasSize(1)
        }

        @Test
        fun `should return empty list when user has no accessible wishlists`() {
            whenever(wishlistRepository.findAllAccessibleByUser(ownerId)).thenReturn(emptyList())

            val result = wishlistService.listAccessibleWishlists(ownerId)

            assertThat(result).isEmpty()
        }

        @Test
        fun `should rethrow generic exception when repository fails`() {
            whenever(wishlistRepository.findAllAccessibleByUser(ownerId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.listAccessibleWishlists(ownerId)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // addMember
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("addMember")
    inner class AddMemberTests {

        @Test
        fun `should add member to a shared wishlist and send push notification`() {
            val owner = buildUser(id = ownerId)
            val newMemberId = UUID.randomUUID()
            val newMember = buildUser(id = newMemberId, username = "newmember")
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val request = AddWishlistMemberRequest(userId = newMemberId)
            val savedMember = WishlistMember(id = UUID.randomUUID(), wishlist = wishlist, user = newMember)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, newMemberId)).thenReturn(false)
            whenever(userRepository.findById(newMemberId)).thenReturn(Optional.of(newMember))
            whenever(wishlistMemberRepository.save(any())).thenReturn(savedMember)

            val result = wishlistService.addMember(ownerId, wishlistId, request)

            assertThat(result.user.id).isEqualTo(newMemberId)
            verify(pushNotificationService).sendToUser(
                userId = eq(newMemberId),
                title = any(),
                body = any(),
                data = any(),
            )
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            val request = AddWishlistMemberRequest(userId = UUID.randomUUID())
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.addMember(ownerId, wishlistId, request)
            }.isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerException when user is not the owner`() {
            val differentOwner = buildUser()
            val wishlist = buildWishlist(owner = differentOwner, isShared = true)
            val request = AddWishlistMemberRequest(userId = UUID.randomUUID())
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.addMember(ownerId, wishlistId, request)
            }.isInstanceOf(NotWishlistOwnerException::class.java)
        }

        @Test
        fun `should throw WishlistNotSharedException when wishlist is not shared`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isShared = false)
            val request = AddWishlistMemberRequest(userId = UUID.randomUUID())
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.addMember(ownerId, wishlistId, request)
            }.isInstanceOf(WishlistNotSharedException::class.java)
        }

        @Test
        fun `should throw CannotAddOwnerAsMemberException when trying to add the owner as member`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val request = AddWishlistMemberRequest(userId = ownerId)
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.addMember(ownerId, wishlistId, request)
            }.isInstanceOf(CannotAddOwnerAsMemberException::class.java)
        }

        @Test
        fun `should throw UserAlreadyWishlistMemberException when user is already a member`() {
            val owner = buildUser(id = ownerId)
            val existingMemberId = UUID.randomUUID()
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val request = AddWishlistMemberRequest(userId = existingMemberId)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, existingMemberId)).thenReturn(true)

            assertThatThrownBy {
                wishlistService.addMember(ownerId, wishlistId, request)
            }.isInstanceOf(UserAlreadyWishlistMemberException::class.java)
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when new member user does not exist`() {
            val owner = buildUser(id = ownerId)
            val newMemberId = UUID.randomUUID()
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val request = AddWishlistMemberRequest(userId = newMemberId)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, newMemberId)).thenReturn(false)
            whenever(userRepository.findById(newMemberId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.addMember(ownerId, wishlistId, request)
            }.isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verify(wishlistMemberRepository, never()).save(any())
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            val owner = buildUser(id = ownerId)
            buildWishlist(owner = owner, isShared = true)

            val request = AddWishlistMemberRequest(userId = UUID.randomUUID())
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.addMember(ownerId, wishlistId, request)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // removeMember
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("removeMember")
    inner class RemoveMemberTests {

        @Test
        fun `should remove member from wishlist when user is the owner`() {
            val owner = buildUser(id = ownerId)
            val memberUserId = UUID.randomUUID()
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val member = WishlistMember(id = UUID.randomUUID(), wishlist = wishlist, user = buildUser(id = memberUserId))

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, memberUserId))
                .thenReturn(Optional.of(member))

            wishlistService.removeMember(ownerId, wishlistId, memberUserId)

            verify(wishlistMemberRepository).delete(member)
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.removeMember(ownerId, wishlistId, UUID.randomUUID())
            }.isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerException when user is not the owner`() {
            val differentOwner = buildUser()
            val wishlist = buildWishlist(owner = differentOwner, isShared = true)
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.removeMember(ownerId, wishlistId, UUID.randomUUID())
            }.isInstanceOf(NotWishlistOwnerException::class.java)
        }

        @Test
        fun `should throw UserNotWishlistMemberException when target user is not a member`() {
            val owner = buildUser(id = ownerId)
            val memberUserId = UUID.randomUUID()
            val wishlist = buildWishlist(owner = owner, isShared = true)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, memberUserId))
                .thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.removeMember(ownerId, wishlistId, memberUserId)
            }.isInstanceOf(UserNotWishlistMemberException::class.java)

            verify(wishlistMemberRepository, never()).delete(any())
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.removeMember(ownerId, wishlistId, UUID.randomUUID())
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // leaveWishlist
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("leaveWishlist")
    inner class LeaveWishlistTests {

        @Test
        fun `should remove membership when user is a member`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val member = WishlistMember(id = UUID.randomUUID(), wishlist = wishlist, user = buildUser(id = ownerId))

            whenever(wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, ownerId))
                .thenReturn(Optional.of(member))

            wishlistService.leaveWishlist(ownerId, wishlistId)

            verify(wishlistMemberRepository).delete(member)
        }

        @Test
        fun `should throw UserNotWishlistMemberException when user is not a member`() {
            whenever(wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, ownerId))
                .thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.leaveWishlist(ownerId, wishlistId)
            }.isInstanceOf(UserNotWishlistMemberException::class.java)

            verify(wishlistMemberRepository, never()).delete(any())
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, ownerId))
                .thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.leaveWishlist(ownerId, wishlistId)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // listMembers
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("listMembers")
    inner class ListMembersTests {

        @Test
        fun `should return list of members when wishlist exists`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val member = WishlistMember(id = UUID.randomUUID(), wishlist = wishlist, user = buildUser())

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.findAllByWishlistId(wishlistId)).thenReturn(listOf(member))

            val result = wishlistService.listMembers(wishlistId)

            assertThat(result).hasSize(1)
        }

        @Test
        fun `should return empty list when wishlist has no members`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner, isShared = true)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.findAllByWishlistId(wishlistId)).thenReturn(emptyList())

            val result = wishlistService.listMembers(wishlistId)

            assertThat(result).isEmpty()
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.listMembers(wishlistId)
            }.isInstanceOf(WishlistNotFoundException::class.java)

            verify(wishlistMemberRepository, never()).findAllByWishlistId(any())
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.listMembers(wishlistId)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // addItem
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("addItem")
    inner class AddItemTests {

        @Test
        fun `should add game to wishlist when user is the owner`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isShared = false)
            val gameId = UUID.randomUUID()
            val game = buildGame(id = gameId)
            val request = AddWishlistItemRequest(gameId = gameId)
            val savedItem = WishlistItem(id = UUID.randomUUID(), wishlist = wishlist, game = game, addedBy = owner)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.existsByWishlistIdAndGameId(wishlistId, gameId)).thenReturn(false)
            whenever(gameRepository.findById(gameId)).thenReturn(Optional.of(game))
            whenever(userRepository.findById(ownerId)).thenReturn(Optional.of(owner))
            whenever(wishlistItemRepository.save(any())).thenReturn(savedItem)

            val result = wishlistService.addItem(ownerId, wishlistId, request)

            assertThat(result.game.id).isEqualTo(gameId)
        }

        @Test
        fun `should add game to wishlist when user is a member of a shared wishlist`() {
            val owner = buildUser()
            val memberId = UUID.randomUUID()
            val member = buildUser(id = memberId)
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val gameId = UUID.randomUUID()
            val game = buildGame(id = gameId)
            val request = AddWishlistItemRequest(gameId = gameId)
            val savedItem = WishlistItem(id = UUID.randomUUID(), wishlist = wishlist, game = game, addedBy = member)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, memberId)).thenReturn(true)
            whenever(wishlistItemRepository.existsByWishlistIdAndGameId(wishlistId, gameId)).thenReturn(false)
            whenever(gameRepository.findById(gameId)).thenReturn(Optional.of(game))
            whenever(userRepository.findById(memberId)).thenReturn(Optional.of(member))
            whenever(wishlistItemRepository.save(any())).thenReturn(savedItem)

            val result = wishlistService.addItem(memberId, wishlistId, request)

            assertThat(result.addedBy.id).isEqualTo(memberId)
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            val request = AddWishlistItemRequest(gameId = UUID.randomUUID())
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.addItem(ownerId, wishlistId, request)
            }.isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerOrMemberException when user has no access to a private wishlist`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner, isShared = false)
            val request = AddWishlistItemRequest(gameId = UUID.randomUUID())

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.addItem(ownerId, wishlistId, request)
            }.isInstanceOf(NotWishlistOwnerOrMemberException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerOrMemberException when user is not a member of a shared wishlist`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val request = AddWishlistItemRequest(gameId = UUID.randomUUID())

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, ownerId)).thenReturn(false)

            assertThatThrownBy {
                wishlistService.addItem(ownerId, wishlistId, request)
            }.isInstanceOf(NotWishlistOwnerOrMemberException::class.java)
        }

        @Test
        fun `should throw GameAlreadyInWishlistException when game already present`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isShared = false)
            val gameId = UUID.randomUUID()
            val request = AddWishlistItemRequest(gameId = gameId)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.existsByWishlistIdAndGameId(wishlistId, gameId)).thenReturn(true)

            assertThatThrownBy {
                wishlistService.addItem(ownerId, wishlistId, request)
            }.isInstanceOf(GameAlreadyInWishlistException::class.java)
        }

        @Test
        fun `should throw GameNotFoundException when game does not exist`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isShared = false)
            val gameId = UUID.randomUUID()
            val request = AddWishlistItemRequest(gameId = gameId)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.existsByWishlistIdAndGameId(wishlistId, gameId)).thenReturn(false)
            whenever(gameRepository.findById(gameId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.addItem(ownerId, wishlistId, request)
            }.isInstanceOf(GameNotFoundException::class.java)

            verify(wishlistItemRepository, never()).save(any())
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when adding user does not exist`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isShared = false)
            val gameId = UUID.randomUUID()
            val game = buildGame(id = gameId)
            val request = AddWishlistItemRequest(gameId = gameId)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.existsByWishlistIdAndGameId(wishlistId, gameId)).thenReturn(false)
            whenever(gameRepository.findById(gameId)).thenReturn(Optional.of(game))
            whenever(userRepository.findById(ownerId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.addItem(ownerId, wishlistId, request)
            }.isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verify(wishlistItemRepository, never()).save(any())
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            val request = AddWishlistItemRequest(gameId = UUID.randomUUID())
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.addItem(ownerId, wishlistId, request)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // removeItem
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("removeItem")
    inner class RemoveItemTests {

        @Test
        fun `should remove item when user is the owner`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isShared = false)
            val itemId = UUID.randomUUID()
            val item = WishlistItem(id = itemId, wishlist = wishlist, game = buildGame(), addedBy = owner)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.findByIdAndWishlistId(itemId, wishlistId)).thenReturn(Optional.of(item))

            wishlistService.removeItem(ownerId, wishlistId, itemId)

            verify(wishlistItemRepository).delete(item)
        }

        @Test
        fun `should remove item when user is a member of a shared wishlist`() {
            val owner = buildUser()
            val memberId = UUID.randomUUID()
            val wishlist = buildWishlist(owner = owner, isShared = true)
            val itemId = UUID.randomUUID()
            val item = WishlistItem(id = itemId, wishlist = wishlist, game = buildGame(), addedBy = owner)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, memberId)).thenReturn(true)
            whenever(wishlistItemRepository.findByIdAndWishlistId(itemId, wishlistId)).thenReturn(Optional.of(item))

            wishlistService.removeItem(memberId, wishlistId, itemId)

            verify(wishlistItemRepository).delete(item)
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.removeItem(ownerId, wishlistId, UUID.randomUUID())
            }.isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerOrMemberException when user has no access`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner, isShared = false)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.removeItem(ownerId, wishlistId, UUID.randomUUID())
            }.isInstanceOf(NotWishlistOwnerOrMemberException::class.java)
        }

        @Test
        fun `should throw WishlistItemNotFoundException when item does not exist in wishlist`() {
            val owner = buildUser(id = ownerId)
            val wishlist = buildWishlist(owner = owner, isShared = false)
            val itemId = UUID.randomUUID()

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.findByIdAndWishlistId(itemId, wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.removeItem(ownerId, wishlistId, itemId)
            }.isInstanceOf(WishlistItemNotFoundException::class.java)

            verify(wishlistItemRepository, never()).delete(any<WishlistItem>())
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.removeItem(ownerId, wishlistId, UUID.randomUUID())
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // listItems
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("listItems")
    inner class ListItemsTests {

        @Test
        fun `should return paged items with default sort when wishlist exists`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner)
            val item = WishlistItem(id = UUID.randomUUID(), wishlist = wishlist, game = buildGame(), addedBy = owner)
            val page = PageImpl(listOf(item), PageRequest.of(0, 20), 1)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.findAll(any<org.springframework.data.jpa.domain.Specification<WishlistItem>>(), any<PageRequest>()))
                .thenReturn(page)

            val result = wishlistService.listItems(wishlistId, 0, 20, null, null)

            assertThat(result.content).hasSize(1)
        }

        @Test
        fun `should clamp negative page number to zero`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner)
            val page = PageImpl<WishlistItem>(emptyList(), PageRequest.of(0, 20), 0)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.findAll(any<org.springframework.data.jpa.domain.Specification<WishlistItem>>(), any<PageRequest>()))
                .thenReturn(page)

            val result = wishlistService.listItems(wishlistId, -5, 20, null, null)

            assertThat(result.number).isEqualTo(0)
        }

        @Test
        fun `should clamp oversized page size to 100`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner)
            val page = PageImpl<WishlistItem>(emptyList(), PageRequest.of(0, 100), 0)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.findAll(any<org.springframework.data.jpa.domain.Specification<WishlistItem>>(), any<PageRequest>()))
                .thenReturn(page)

            wishlistService.listItems(wishlistId, 0, 500, null, null)

            verify(wishlistItemRepository).findAll(any<org.springframework.data.jpa.domain.Specification<WishlistItem>>(), eq(PageRequest.of(0, 100, org.springframework.data.domain.Sort.by("createdAt").descending())))
        }

        @Test
        fun `should clamp zero or negative page size to minimum of 1`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner)
            val page = PageImpl<WishlistItem>(emptyList(), PageRequest.of(0, 1), 0)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.findAll(any<org.springframework.data.jpa.domain.Specification<WishlistItem>>(), any<PageRequest>()))
                .thenReturn(page)

            wishlistService.listItems(wishlistId, 0, 0, null, null)

            verify(wishlistItemRepository).findAll(any<org.springframework.data.jpa.domain.Specification<WishlistItem>>(), eq(PageRequest.of(0, 1, org.springframework.data.domain.Sort.by("createdAt").descending())))
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.listItems(wishlistId, 0, 20, null, null)
            }.isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should throw InvalidSortException when sort field is not allowed`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner)
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            assertThatThrownBy {
                wishlistService.listItems(wishlistId, 0, 20, null, "notAllowedField-asc")
            }.isInstanceOf(InvalidSortException::class.java)

            verify(wishlistItemRepository, never())
                .findAll(any<org.springframework.data.jpa.domain.Specification<WishlistItem>>(), any<PageRequest>())
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.listItems(wishlistId, 0, 20, null, null)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // getWishlist
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("getWishlist")
    inner class GetWishlistTests {

        @Test
        fun `should return wishlist DTO when wishlist exists`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner)
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))

            val result = wishlistService.getWishlist(wishlistId)

            assertThat(result.id).isEqualTo(wishlistId)
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.getWishlist(wishlistId)
            }.isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.getWishlist(wishlistId)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // createDefaultWishlistForUser
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("createDefaultWishlistForUser")
    inner class CreateDefaultWishlistForUserTests {

        @Test
        fun `should create a default and non-shared wishlist named La mia wishlist`() {
            val user = buildUser(id = ownerId)
            whenever(wishlistRepository.save(any())).thenAnswer { it.getArgument<Wishlist>(0) }

            wishlistService.createDefaultWishlistForUser(user)

            val captor = org.mockito.kotlin.argumentCaptor<Wishlist>()
            verify(wishlistRepository).save(captor.capture())
            val saved = captor.firstValue

            assertThat(saved.name).isEqualTo("La mia wishlist")
            assertThat(saved.isDefault).isTrue()
            assertThat(saved.isShared).isFalse()
            assertThat(saved.owner).isEqualTo(user)
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            val user = buildUser(id = ownerId)
            whenever(wishlistRepository.save(any())).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.createDefaultWishlistForUser(user)
            }.isInstanceOf(RuntimeException::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // getItemStatus
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("getItemStatus")
    inner class GetItemStatusTests {

        @Test
        fun `should return inWishlist true with item id when game is present`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner)
            val gameId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val item = WishlistItem(id = itemId, wishlist = wishlist, game = buildGame(id = gameId), addedBy = owner)

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.findByWishlistIdAndGameId(wishlistId, gameId)).thenReturn(Optional.of(item))

            val result = wishlistService.getItemStatus(wishlistId, gameId)

            assertThat(result.inWishlist).isTrue()
            assertThat(result.itemId).isEqualTo(itemId)
        }

        @Test
        fun `should return inWishlist false with null item id when game is absent`() {
            val owner = buildUser()
            val wishlist = buildWishlist(owner = owner)
            val gameId = UUID.randomUUID()

            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist))
            whenever(wishlistItemRepository.findByWishlistIdAndGameId(wishlistId, gameId)).thenReturn(Optional.empty())

            val result = wishlistService.getItemStatus(wishlistId, gameId)

            assertThat(result.inWishlist).isFalse()
            assertThat(result.itemId).isNull()
        }

        @Test
        fun `should throw WishlistNotFoundException when wishlist does not exist`() {
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                wishlistService.getItemStatus(wishlistId, UUID.randomUUID())
            }.isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should rethrow generic exception when repository fails unexpectedly`() {
            whenever(wishlistRepository.findById(wishlistId)).thenThrow(RuntimeException("Database error"))

            assertThatThrownBy {
                wishlistService.getItemStatus(wishlistId, UUID.randomUUID())
            }.isInstanceOf(RuntimeException::class.java)
        }
    }
}