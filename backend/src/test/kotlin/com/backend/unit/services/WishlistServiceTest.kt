package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.AddWishlistItemRequest
import com.backend.models.dtos.AddWishlistMemberRequest
import com.backend.models.dtos.CreateWishlistRequest
import com.backend.models.dtos.UpdateWishlistRequest
import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.entities.Wishlist
import com.backend.models.entities.WishlistActivity
import com.backend.models.entities.WishlistItem
import com.backend.models.entities.WishlistMember
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.NotificationType
import com.backend.models.enums.WishlistActivityAction
import com.backend.models.specifications.WishlistFilterType
import com.backend.models.specifications.WishlistItemSpecification
import com.backend.models.specifications.WishlistSpecification
import com.backend.repositories.*
import com.backend.services.PushNotificationService
import com.backend.services.WishlistService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.util.Optional
import java.util.UUID

class WishlistServiceTest {

    private lateinit var wishlistRepository: WishlistRepository
    private lateinit var wishlistMemberRepository: WishlistMemberRepository
    private lateinit var wishlistItemRepository: WishlistItemRepository
    private lateinit var userRepository: UserRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var pushNotificationService: PushNotificationService
    private lateinit var wishlistActivityRepository: WishlistActivityRepository
    private lateinit var service: WishlistService

    private val ownerId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val strangerId = UUID.randomUUID()
    private val wishlistId = UUID.randomUUID()
    private val gameId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()

    private val owner = User(id = ownerId, username = "stefano", email = "stefano@example.com", passwordHash = "hash", accountStatus = AccountStatus.ACTIVE)
    private val member = User(id = memberId, username = "marco", email = "marco@example.com", passwordHash = "hash", accountStatus = AccountStatus.ACTIVE)
    private val stranger = User(id = strangerId, username = "anna", email = "anna@example.com", passwordHash = "hash", accountStatus = AccountStatus.ACTIVE)
    private val game = Game(id = gameId, bggId = 13L, name = "Catan")

    private val pageableSlot = slot<Pageable>()
    private val activitySlot = slot<WishlistActivity>()

    @BeforeEach
    fun setUp() {
        wishlistRepository = mockk(relaxUnitFun = true)
        wishlistMemberRepository = mockk(relaxUnitFun = true)
        wishlistItemRepository = mockk(relaxUnitFun = true)
        userRepository = mockk()
        gameRepository = mockk()
        pushNotificationService = mockk(relaxUnitFun = true)
        wishlistActivityRepository = mockk()

        mockkObject(WishlistSpecification, WishlistItemSpecification)

        listOf(owner, member, stranger).forEach { user ->
            every { userRepository.findById(user.id!!) } returns Optional.of(user)
            every { userRepository.getReferenceById(user.id!!) } returns user
        }
        every { gameRepository.findById(gameId) } returns Optional.of(game)

        every { wishlistRepository.findById(any()) } returns Optional.empty()
        every { wishlistRepository.save(any<Wishlist>()) } answers { firstArg<Wishlist>().apply { if(id == null) id = UUID.randomUUID() } }
        every { wishlistRepository.saveAndFlush(any<Wishlist>()) } answers { firstArg() }
        every { wishlistMemberRepository.existsByWishlistIdAndUserId(any(), any()) } returns false
        every { wishlistMemberRepository.existsByWishlistId(any()) } returns false
        every { wishlistMemberRepository.saveAndFlush(any<WishlistMember>()) } answers { firstArg<WishlistMember>().apply { id = UUID.randomUUID() } }
        every { wishlistItemRepository.existsByWishlistIdAndGameId(any(), any()) } returns false
        every { wishlistItemRepository.saveAndFlush(any<WishlistItem>()) } answers { firstArg<WishlistItem>().apply { id = itemId } }
        every { wishlistActivityRepository.save(capture(activitySlot)) } answers { firstArg() }

        service = WishlistService(wishlistRepository, wishlistMemberRepository, wishlistItemRepository, userRepository, gameRepository, pushNotificationService, wishlistActivityRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(WishlistSpecification, WishlistItemSpecification)
    }

    private fun wishlist(isShared: Boolean = false, isDefault: Boolean = false, by: User = owner, id: UUID = wishlistId): Wishlist =
        Wishlist(id = id, name = "Da comprare", owner = by, isShared = isShared, isDefault = isDefault)
            .also { every { wishlistRepository.findById(id) } returns Optional.of(it) }

    private fun joined(userId: UUID = memberId) {
        every { wishlistMemberRepository.existsByWishlistIdAndUserId(wishlistId, userId) } returns true
        every { wishlistMemberRepository.existsByWishlistId(wishlistId) } returns true
    }

    private fun item(target: Wishlist) = WishlistItem(id = itemId, wishlist = target, game = game, addedBy = owner)
        .also { every { wishlistItemRepository.findByIdAndWishlistId(itemId, wishlistId) } returns Optional.of(it) }

    private fun stubWishlistPage(content: List<Wishlist> = emptyList()) {
        every { wishlistRepository.findAll(any<Specification<Wishlist>>(), capture(pageableSlot)) } answers { PageImpl(content, secondArg(), content.size.toLong()) }
    }

    private fun stubItemPage(content: List<WishlistItem> = emptyList()) {
        every { wishlistItemRepository.findAll(any<Specification<WishlistItem>>(), capture(pageableSlot)) } answers { PageImpl(content, secondArg(), content.size.toLong()) }
    }

    private fun orders(): List<Pair<String, Sort.Direction>> = pageableSlot.captured.sort.map { it.property to it.direction }.toList()

    private fun verifyNoActivity() = verify(exactly = 0) { wishlistActivityRepository.save(any()) }

    @Nested
    @DisplayName("createWishlist")
    inner class CreateWishlistTests {

        @Test
        fun `should create a wishlist owned by the user with a trimmed name`() {
            val result = service.createWishlist(ownerId, CreateWishlistRequest(name = "  Natale  ", isShared = true))

            val saved = slot<Wishlist>()
            verify { wishlistRepository.save(capture(saved)) }
            assertThat(saved.captured.name).isEqualTo("Natale")
            assertThat(saved.captured.owner).isSameAs(owner)
            assertThat(saved.captured.isShared).isTrue()
            assertThat(saved.captured.isDefault).isFalse()
            assertThat(result.name).isEqualTo("Natale")
            assertThat(result.owner.id).isEqualTo(ownerId)
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException without saving`() {
            every { userRepository.findById(ownerId) } returns Optional.empty()

            assertThatThrownBy { service.createWishlist(ownerId, CreateWishlistRequest(name = "Natale", isShared = false)) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verify(exactly = 0) { wishlistRepository.save(any<Wishlist>()) }
        }

        @Test
        fun `should propagate a repository failure`() {
            every { wishlistRepository.save(any<Wishlist>()) } throws RuntimeException("Database error")

            assertThatThrownBy { service.createWishlist(ownerId, CreateWishlistRequest(name = "Natale", isShared = false)) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("updateWishlist")
    inner class UpdateWishlistTests {

        @Test
        fun `should rename the wishlist with a trimmed name`() {
            val target = wishlist(isShared = true)

            val result = service.updateWishlist(ownerId, wishlistId, UpdateWishlistRequest(name = "  Compleanno  "))

            assertThat(target.name).isEqualTo("Compleanno")
            assertThat(result.name).isEqualTo("Compleanno")
            assertThat(result.isShared).isTrue()
            assertThat(result.isDefault).isFalse()
            verify(exactly = 1) { wishlistRepository.saveAndFlush(target) }
        }

        @Test
        fun `should throw WishlistNotFoundException when the wishlist does not exist`() {
            assertThatThrownBy { service.updateWishlist(ownerId, wishlistId, UpdateWishlistRequest(name = "Compleanno")) }
                .isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerException when the user is a member but not the owner`() {
            val target = wishlist(isShared = true)
            joined()

            assertThatThrownBy { service.updateWishlist(memberId, wishlistId, UpdateWishlistRequest(name = "Compleanno")) }
                .isInstanceOf(NotWishlistOwnerException::class.java)

            assertThat(target.name).isEqualTo("Da comprare")
            verify(exactly = 0) { wishlistRepository.saveAndFlush(any<Wishlist>()) }
        }

        @Test
        fun `should check the ownership before the default flag`() {
            wishlist(isDefault = true)

            assertThatThrownBy { service.updateWishlist(strangerId, wishlistId, UpdateWishlistRequest(name = "Compleanno")) }
                .isInstanceOf(NotWishlistOwnerException::class.java)
        }

        @Test
        fun `should refuse to rename the default wishlist`() {
            val target = wishlist(isDefault = true)

            assertThatThrownBy { service.updateWishlist(ownerId, wishlistId, UpdateWishlistRequest(name = "Compleanno")) }
                .isInstanceOf(CannotModifyDefaultWishlistException::class.java)

            assertThat(target.name).isEqualTo("Da comprare")
            verify(exactly = 0) { wishlistRepository.saveAndFlush(any<Wishlist>()) }
        }

        @Test
        fun `should propagate a repository failure`() {
            wishlist()
            every { wishlistRepository.saveAndFlush(any<Wishlist>()) } throws RuntimeException("Database error")

            assertThatThrownBy { service.updateWishlist(ownerId, wishlistId, UpdateWishlistRequest(name = "Compleanno")) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteWishlist")
    inner class DeleteWishlistTests {

        @Test
        fun `should delete the wishlist of the owner`() {
            val target = wishlist(isShared = true)

            service.deleteWishlist(ownerId, wishlistId)

            verify(exactly = 1) { wishlistRepository.delete(target) }
        }

        @Test
        fun `should throw WishlistNotFoundException when the wishlist does not exist`() {
            assertThatThrownBy { service.deleteWishlist(ownerId, wishlistId) }
                .isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerException for a member`() {
            wishlist(isShared = true)
            joined()

            assertThatThrownBy { service.deleteWishlist(memberId, wishlistId) }
                .isInstanceOf(NotWishlistOwnerException::class.java)

            verify(exactly = 0) { wishlistRepository.delete(any<Wishlist>()) }
        }

        @Test
        fun `should refuse to delete the default wishlist`() {
            wishlist(isDefault = true)

            assertThatThrownBy { service.deleteWishlist(ownerId, wishlistId) }
                .isInstanceOf(CannotModifyDefaultWishlistException::class.java)

            verify(exactly = 0) { wishlistRepository.delete(any<Wishlist>()) }
        }
    }

    @Nested
    @DisplayName("listAccessibleWishlists")
    inner class ListAccessibleWishlistsTests {

        @Test
        fun `should map the accessible wishlists`() {
            stubWishlistPage(listOf(wishlist(isShared = true)))

            val result = service.listAccessibleWishlists(ownerId, 0, 20, null, null)

            assertThat(result.content.map { it.id }).containsExactly(wishlistId)
        }

        @Test
        fun `should filter by user and type`() {
            stubWishlistPage()

            service.listAccessibleWishlists(ownerId, 0, 20, WishlistFilterType.SHARED, null)

            verify(exactly = 1) { WishlistSpecification.withFilters(ownerId, WishlistFilterType.SHARED) }
        }

        @Test
        fun `should sort by the last update with the id as tie-breaker by default`() {
            stubWishlistPage()

            service.listAccessibleWishlists(ownerId, 0, 20, null, null)

            assertThat(orders().map { it.first }).containsExactly("updatedAt", "id")
            assertThat(orders()[0].second).isEqualTo(Sort.Direction.DESC)
        }

        @Test
        fun `should sort by the requested field`() {
            stubWishlistPage()

            service.listAccessibleWishlists(ownerId, 0, 20, null, "name-asc")

            assertThat(orders()[0]).isEqualTo("name" to Sort.Direction.ASC)
            assertThat(orders().last().first).isEqualTo("id")
        }

        @Test
        fun `should clamp the page and the size`() {
            stubWishlistPage()

            service.listAccessibleWishlists(ownerId, -5, 500, null, null)

            assertThat(pageableSlot.captured.pageNumber).isZero()
            assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
        }

        @Test
        fun `should throw InvalidSortException without querying`() {
            assertThatThrownBy { service.listAccessibleWishlists(ownerId, 0, 20, null, "owner-asc") }
                .isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { wishlistRepository.findAll(any<Specification<Wishlist>>(), any<Pageable>()) }
        }

        @Test
        fun `should propagate a repository failure`() {
            every { wishlistRepository.findAll(any<Specification<Wishlist>>(), any<Pageable>()) } throws RuntimeException("Database error")

            assertThatThrownBy { service.listAccessibleWishlists(ownerId, 0, 20, null, null) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("access rules")
    inner class AccessTests {

        @Test
        fun `should let the owner see a private wishlist`() {
            wishlist()

            assertThat(service.getWishlist(ownerId, wishlistId).id).isEqualTo(wishlistId)
        }

        @Test
        fun `should let a member see a shared wishlist`() {
            wishlist(isShared = true)
            joined()

            assertThat(service.getWishlist(memberId, wishlistId).id).isEqualTo(wishlistId)
        }

        @Test
        fun `should hide a shared wishlist from a user who is not a member`() {
            wishlist(isShared = true)

            assertThatThrownBy { service.getWishlist(strangerId, wishlistId) }
                .isInstanceOf(NotWishlistOwnerOrMemberException::class.java)
        }

        @Test
        fun `should ignore a membership on a wishlist that is not shared`() {
            wishlist(isShared = false)
            joined()

            assertThatThrownBy { service.getWishlist(memberId, wishlistId) }
                .isInstanceOf(NotWishlistOwnerOrMemberException::class.java)

            verify(exactly = 0) { wishlistMemberRepository.existsByWishlistIdAndUserId(any(), any()) }
        }

        @Test
        fun `should let anyone see a default wishlist`() {
            wishlist(isDefault = true)

            assertThat(service.getWishlist(strangerId, wishlistId).isDefault).isTrue()
        }

        @Test
        fun `should not let anyone else write into a default wishlist`() {
            wishlist(isDefault = true)

            assertThatThrownBy { service.addItem(strangerId, wishlistId, AddWishlistItemRequest(gameId = gameId)) }
                .isInstanceOf(NotWishlistOwnerOrMemberException::class.java)
        }

        @Test
        fun `should throw WishlistNotFoundException when the wishlist does not exist`() {
            assertThatThrownBy { service.getWishlist(ownerId, wishlistId) }
                .isInstanceOf(WishlistNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("addMember")
    inner class AddMemberTests {

        private fun add(userId: UUID = memberId) = service.addMember(ownerId, wishlistId, AddWishlistMemberRequest(userId = userId))

        @Test
        fun `should add the member and notify them`() {
            wishlist(isShared = true)

            val result = add()

            val saved = slot<WishlistMember>()
            verify { wishlistMemberRepository.saveAndFlush(capture(saved)) }
            assertThat(saved.captured.user).isSameAs(member)
            assertThat(saved.captured.wishlist.id).isEqualTo(wishlistId)
            assertThat(result.user.id).isEqualTo(memberId)
            verify(exactly = 1) {
                pushNotificationService.sendToUser(
                    userId = memberId,
                    type = NotificationType.WISHLIST_MEMBER_ADDED,
                    title = "Aggiunto a una wishlist",
                    body = "Sei stato aggiunto alla wishlist \"Da comprare\"",
                    data = mapOf("wishlistId" to wishlistId.toString()),
                )
            }
        }

        @Test
        fun `should throw WishlistNotFoundException when the wishlist does not exist`() {
            assertThatThrownBy { add() }.isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun `should throw NotWishlistOwnerException when a member tries to add someone`() {
            wishlist(isShared = true)
            joined()

            assertThatThrownBy { service.addMember(memberId, wishlistId, AddWishlistMemberRequest(userId = strangerId)) }
                .isInstanceOf(NotWishlistOwnerException::class.java)
        }

        @Test
        fun `should throw WishlistNotSharedException for a private wishlist`() {
            wishlist(isShared = false)

            assertThatThrownBy { add() }.isInstanceOf(WishlistNotSharedException::class.java)
        }

        @Test
        fun `should throw CannotAddOwnerAsMemberException when adding the owner`() {
            wishlist(isShared = true)

            assertThatThrownBy { add(ownerId) }.isInstanceOf(CannotAddOwnerAsMemberException::class.java)
        }

        @Test
        fun `should throw UserAlreadyWishlistMemberException without looking up the user`() {
            wishlist(isShared = true)
            joined()

            assertThatThrownBy { add() }.isInstanceOf(UserAlreadyWishlistMemberException::class.java)

            verify(exactly = 0) { userRepository.findById(memberId) }
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when the user does not exist`() {
            wishlist(isShared = true)
            val ghostId = UUID.randomUUID()
            every { userRepository.findById(ghostId) } returns Optional.empty()

            assertThatThrownBy { add(ghostId) }.isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verify(exactly = 0) { wishlistMemberRepository.saveAndFlush(any<WishlistMember>()) }
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["ACTIVE"], mode = EnumSource.Mode.EXCLUDE)
        fun `should refuse a user whose account is not active`(status: AccountStatus) {
            wishlist(isShared = true)
            member.accountStatus = status

            assertThatThrownBy { add() }.isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verify(exactly = 0) { wishlistMemberRepository.saveAndFlush(any<WishlistMember>()) }
        }

        @Test
        fun `should throw UserAlreadyWishlistMemberException when the user is added twice at the same time`() {
            wishlist(isShared = true)
            every { wishlistMemberRepository.saveAndFlush(any<WishlistMember>()) } throws DataIntegrityViolationException("duplicate key")

            assertThatThrownBy { add() }.isInstanceOf(UserAlreadyWishlistMemberException::class.java)

            verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("members")
    inner class MemberTests {

        @Test
        fun `should remove a member`() {
            val target = wishlist(isShared = true)
            val membership = WishlistMember(id = UUID.randomUUID(), wishlist = target, user = member)
            every { wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, memberId) } returns Optional.of(membership)

            service.removeMember(ownerId, wishlistId, memberId)

            verify(exactly = 1) { wishlistMemberRepository.delete(membership) }
        }

        @Test
        fun `should not let a member remove someone else`() {
            wishlist(isShared = true)
            joined()

            assertThatThrownBy { service.removeMember(memberId, wishlistId, strangerId) }
                .isInstanceOf(NotWishlistOwnerException::class.java)

            verify(exactly = 0) { wishlistMemberRepository.delete(any<WishlistMember>()) }
        }

        @Test
        fun `should throw UserNotWishlistMemberException when removing someone who is not a member`() {
            wishlist(isShared = true)
            every { wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, strangerId) } returns Optional.empty()

            assertThatThrownBy { service.removeMember(ownerId, wishlistId, strangerId) }
                .isInstanceOf(UserNotWishlistMemberException::class.java)
        }

        @Test
        fun `should let a member leave`() {
            val membership = WishlistMember(id = UUID.randomUUID(), wishlist = wishlist(isShared = true), user = member)
            every { wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, memberId) } returns Optional.of(membership)

            service.leaveWishlist(memberId, wishlistId)

            verify(exactly = 1) { wishlistMemberRepository.delete(membership) }
        }

        @Test
        fun `should throw UserNotWishlistMemberException when leaving a wishlist you are not in`() {
            every { wishlistMemberRepository.findByWishlistIdAndUserId(wishlistId, ownerId) } returns Optional.empty()

            assertThatThrownBy { service.leaveWishlist(ownerId, wishlistId) }
                .isInstanceOf(UserNotWishlistMemberException::class.java)

            verify(exactly = 0) { wishlistMemberRepository.delete(any<WishlistMember>()) }
        }

        @Test
        fun `should list the members of a viewable wishlist`() {
            val target = wishlist(isShared = true)
            joined()
            every { wishlistMemberRepository.findAllByWishlistId(wishlistId) } returns listOf(WishlistMember(id = UUID.randomUUID(), wishlist = target, user = member))

            val result = service.listMembers(memberId, wishlistId)

            assertThat(result.map { it.user.id }).containsExactly(memberId)
        }

        @Test
        fun `should not list the members to someone without access`() {
            wishlist(isShared = true)

            assertThatThrownBy { service.listMembers(strangerId, wishlistId) }
                .isInstanceOf(NotWishlistOwnerOrMemberException::class.java)

            verify(exactly = 0) { wishlistMemberRepository.findAllByWishlistId(any()) }
        }
    }

    @Nested
    @DisplayName("addItem")
    inner class AddItemTests {

        private fun add(userId: UUID = ownerId) = service.addItem(userId, wishlistId, AddWishlistItemRequest(gameId = gameId))

        @Test
        fun `should save the game added by the user`() {
            wishlist()

            val result = add()

            val saved = slot<WishlistItem>()
            verify { wishlistItemRepository.saveAndFlush(capture(saved)) }
            assertThat(saved.captured.game).isSameAs(game)
            assertThat(saved.captured.addedBy).isSameAs(owner)
            assertThat(result.game.id).isEqualTo(gameId)
        }

        @Test
        fun `should not record activity on a private wishlist`() {
            wishlist()

            add()

            verifyNoActivity()
        }

        @Test
        fun `should not record activity on a shared wishlist without members`() {
            wishlist(isShared = true)

            add()

            verifyNoActivity()
        }

        @Test
        fun `should record the activity when the owner changes a shared wishlist with members`() {
            wishlist(isShared = true)
            joined()

            add()

            assertThat(activitySlot.captured.actor).isSameAs(owner)
            assertThat(activitySlot.captured.game).isSameAs(game)
            assertThat(activitySlot.captured.action).isEqualTo(WishlistActivityAction.ADDED)
        }

        @Test
        fun `should record the activity when a member adds a game`() {
            wishlist(isShared = true)
            joined()

            val result = add(memberId)

            assertThat(result.addedBy.id).isEqualTo(memberId)
            assertThat(activitySlot.captured.actor).isSameAs(member)
        }

        @Test
        fun `should refuse a user without access`() {
            wishlist(isShared = true)

            assertThatThrownBy { add(strangerId) }.isInstanceOf(NotWishlistOwnerOrMemberException::class.java)

            verify(exactly = 0) { wishlistItemRepository.saveAndFlush(any<WishlistItem>()) }
        }

        @Test
        fun `should throw GameAlreadyInWishlistException without looking up the game`() {
            wishlist()
            every { wishlistItemRepository.existsByWishlistIdAndGameId(wishlistId, gameId) } returns true

            assertThatThrownBy { add() }.isInstanceOf(GameAlreadyInWishlistException::class.java)

            verify(exactly = 0) { gameRepository.findById(any()) }
        }

        @Test
        fun `should throw GameNotFoundException without saving`() {
            wishlist()
            every { gameRepository.findById(gameId) } returns Optional.empty()

            assertThatThrownBy { add() }.isInstanceOf(GameNotFoundException::class.java)

            verify(exactly = 0) { wishlistItemRepository.saveAndFlush(any<WishlistItem>()) }
        }

        @Test
        fun `should throw GameAlreadyInWishlistException when the game is added twice at the same time`() {
            wishlist(isShared = true)
            joined()
            every { wishlistItemRepository.saveAndFlush(any<WishlistItem>()) } throws DataIntegrityViolationException("duplicate key")

            assertThatThrownBy { add() }.isInstanceOf(GameAlreadyInWishlistException::class.java)

            verifyNoActivity()
        }

        @Test
        fun `should throw WishlistNotFoundException when the wishlist does not exist`() {
            assertThatThrownBy { add() }.isInstanceOf(WishlistNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("removeItem")
    inner class RemoveItemTests {

        @Test
        fun `should delete the item without activity on a private wishlist`() {
            val target = item(wishlist())

            service.removeItem(ownerId, wishlistId, itemId)

            verify(exactly = 1) { wishlistItemRepository.delete(target) }
            verifyNoActivity()
        }

        @Test
        fun `should record the removal before deleting the item`() {
            val target = item(wishlist(isShared = true))
            joined()

            service.removeItem(memberId, wishlistId, itemId)

            assertThat(activitySlot.captured.action).isEqualTo(WishlistActivityAction.REMOVED)
            assertThat(activitySlot.captured.actor).isSameAs(member)
            verifyOrder {
                wishlistActivityRepository.save(any())
                wishlistItemRepository.delete(target)
            }
        }

        @Test
        fun `should throw WishlistItemNotFoundException when the item is not in the wishlist`() {
            wishlist()
            every { wishlistItemRepository.findByIdAndWishlistId(itemId, wishlistId) } returns Optional.empty()

            assertThatThrownBy { service.removeItem(ownerId, wishlistId, itemId) }
                .isInstanceOf(WishlistItemNotFoundException::class.java)

            verify(exactly = 0) { wishlistItemRepository.delete(any<WishlistItem>()) }
        }

        @Test
        fun `should refuse a user without access`() {
            wishlist(isShared = true)

            assertThatThrownBy { service.removeItem(strangerId, wishlistId, itemId) }
                .isInstanceOf(NotWishlistOwnerOrMemberException::class.java)

            verify(exactly = 0) { wishlistItemRepository.findByIdAndWishlistId(any(), any()) }
        }
    }

    @Nested
    @DisplayName("listItems")
    inner class ListItemsTests {

        @Test
        fun `should map the items of a viewable wishlist`() {
            stubItemPage(listOf(WishlistItem(id = itemId, wishlist = wishlist(), game = game, addedBy = owner)))

            val result = service.listItems(ownerId, wishlistId, 0, 20, null, null)

            assertThat(result.content.map { it.game.id }).containsExactly(gameId)
        }

        @Test
        fun `should filter by wishlist and game name`() {
            wishlist()
            stubItemPage()

            service.listItems(ownerId, wishlistId, 0, 20, "cat", null)

            verify(exactly = 1) { WishlistItemSpecification.withFilters(wishlistId, "cat") }
        }

        @Test
        fun `should sort by the newest items with the id as tie-breaker by default`() {
            wishlist()
            stubItemPage()

            service.listItems(ownerId, wishlistId, 0, 20, null, null)

            assertThat(orders().map { it.first }).containsExactly("createdAt", "id")
            assertThat(orders()[0].second).isEqualTo(Sort.Direction.DESC)
        }

        @Test
        fun `should sort by game name`() {
            wishlist()
            stubItemPage()

            service.listItems(ownerId, wishlistId, 0, 20, null, "game.name-asc")

            assertThat(orders()[0]).isEqualTo("game.name" to Sort.Direction.ASC)
        }

        @Test
        fun `should clamp the size between 1 and 100`() {
            wishlist()
            stubItemPage()

            service.listItems(ownerId, wishlistId, 0, 0, null, null)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(1)

            service.listItems(ownerId, wishlistId, 0, 500, null, null)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
        }

        @Test
        fun `should throw InvalidSortException without querying`() {
            wishlist()

            assertThatThrownBy { service.listItems(ownerId, wishlistId, 0, 20, null, "addedBy-asc") }
                .isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { wishlistItemRepository.findAll(any<Specification<WishlistItem>>(), any<Pageable>()) }
        }

        @Test
        fun `should not list the items to someone without access`() {
            wishlist(isShared = true)

            assertThatThrownBy { service.listItems(strangerId, wishlistId, 0, 20, null, null) }
                .isInstanceOf(NotWishlistOwnerOrMemberException::class.java)

            verify(exactly = 0) { wishlistItemRepository.findAll(any<Specification<WishlistItem>>(), any<Pageable>()) }
        }
    }

    @Nested
    @DisplayName("getItemStatus")
    inner class GetItemStatusTests {

        @Test
        fun `should report a game in the wishlist with its item id`() {
            wishlist()
            every { wishlistItemRepository.findIdByWishlistIdAndGameId(wishlistId, gameId) } returns itemId

            val result = service.getItemStatus(ownerId, wishlistId, gameId)

            assertThat(result.inWishlist).isTrue()
            assertThat(result.itemId).isEqualTo(itemId)
        }

        @Test
        fun `should report a game not in the wishlist`() {
            wishlist()
            every { wishlistItemRepository.findIdByWishlistIdAndGameId(wishlistId, gameId) } returns null

            val result = service.getItemStatus(ownerId, wishlistId, gameId)

            assertThat(result.inWishlist).isFalse()
            assertThat(result.itemId).isNull()
        }

        @Test
        fun `should not reveal the status to someone without access`() {
            wishlist(isShared = true)

            assertThatThrownBy { service.getItemStatus(strangerId, wishlistId, gameId) }
                .isInstanceOf(NotWishlistOwnerOrMemberException::class.java)
        }
    }

    @Nested
    @DisplayName("default wishlist")
    inner class DefaultWishlistTests {

        @Test
        fun `should create a private default wishlist for a new user`() {
            service.createDefaultWishlistForUser(owner)

            val saved = slot<Wishlist>()
            verify { wishlistRepository.save(capture(saved)) }
            assertThat(saved.captured.name).isEqualTo("La mia wishlist")
            assertThat(saved.captured.owner).isSameAs(owner)
            assertThat(saved.captured.isDefault).isTrue()
            assertThat(saved.captured.isShared).isFalse()
        }

        @Test
        fun `should propagate a failure while creating the default wishlist`() {
            every { wishlistRepository.save(any<Wishlist>()) } throws RuntimeException("Database error")

            assertThatThrownBy { service.createDefaultWishlistForUser(owner) }
                .isInstanceOf(RuntimeException::class.java)
        }

        @Test
        fun `should list the items of the default wishlist of a user`() {
            val target = wishlist(isDefault = true)
            every { wishlistRepository.findDefaultByOwnerId(ownerId) } returns Optional.of(target)
            stubItemPage(listOf(WishlistItem(id = itemId, wishlist = target, game = game, addedBy = owner)))

            val result = service.getDefaultWishlistForUser(ownerId, -1, 20)

            assertThat(result.content).hasSize(1)
            assertThat(pageableSlot.captured.pageNumber).isZero()
            assertThat(orders().map { it.first }).containsExactly("createdAt", "id")
            verify(exactly = 1) { WishlistItemSpecification.withFilters(wishlistId, null) }
        }

        @Test
        fun `should throw DefaultWishlistNotFoundException without querying the items`() {
            every { wishlistRepository.findDefaultByOwnerId(ownerId) } returns Optional.empty()

            assertThatThrownBy { service.getDefaultWishlistForUser(ownerId, 0, 20) }
                .isInstanceOf(DefaultWishlistNotFoundException::class.java)

            verify(exactly = 0) { wishlistItemRepository.findAll(any<Specification<WishlistItem>>(), any<Pageable>()) }
        }

        @Test
        fun `should return my default wishlist`() {
            every { wishlistRepository.findDefaultByOwnerId(ownerId) } returns Optional.of(wishlist(isDefault = true))

            val result = service.getMyDefaultWishlist(ownerId)

            assertThat(result.id).isEqualTo(wishlistId)
            assertThat(result.isDefault).isTrue()
        }

        @Test
        fun `should throw DefaultWishlistNotFoundException when I have no default wishlist`() {
            every { wishlistRepository.findDefaultByOwnerId(ownerId) } returns Optional.empty()

            assertThatThrownBy { service.getMyDefaultWishlist(ownerId) }
                .isInstanceOf(DefaultWishlistNotFoundException::class.java)
        }
    }
}