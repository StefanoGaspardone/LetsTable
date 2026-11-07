package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.AddToCollectionRequest
import com.backend.models.entities.CollectionItem
import com.backend.models.entities.Game
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.UserRole
import com.backend.models.specifications.CollectionItemSpecification
import com.backend.repositories.CollectionItemRepository
import com.backend.repositories.GameRepository
import com.backend.repositories.UserRepository
import com.backend.services.CollectionService
import com.backend.services.UserAchievementService
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.util.*

@ExtendWith(MockKExtension::class)
class CollectionServiceTest {

    @MockK
    private lateinit var collectionItemRepository: CollectionItemRepository

    @MockK
    private lateinit var gameRepository: GameRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @RelaxedMockK
    private lateinit var userAchievementService: UserAchievementService

    private lateinit var collectionService: CollectionService

    private val userId: UUID = UUID.randomUUID()
    private val gameId: UUID = UUID.randomUUID()
    private val itemId: UUID = UUID.randomUUID()

    private val user = User(
        id = userId,
        username = "stefano",
        email = "stefano@test.com",
        passwordHash = "hashed_pwd",
        role = UserRole.USER,
        accountStatus = AccountStatus.ACTIVE,
    )

    private val game = Game(id = gameId, bggId = 12345L, name = "Catan")

    private val item = CollectionItem(id = itemId, user = user, game = game)

    @BeforeEach
    fun setUp() {
        collectionService = CollectionService(collectionItemRepository, gameRepository, userRepository, userAchievementService)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CollectionItemSpecification)
    }

    @Nested
    @DisplayName("addToCollection")
    inner class AddToCollection {

        private val request = AddToCollectionRequest(gameId = gameId)
        private val savedItem = slot<CollectionItem>()

        private fun stubAdd() {
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns false
            every { gameRepository.findById(gameId) } returns Optional.of(game)
            every { userRepository.getReferenceById(userId) } returns user
            every { collectionItemRepository.saveAndFlush(capture(savedItem)) } answers { savedItem.captured.apply { id = itemId } }
        }

        @Test
        fun `should add the game to the collection of the user`() {
            stubAdd()

            val result = collectionService.addToCollection(userId, request)

            assertThat(result.id).isEqualTo(itemId)
            assertThat(savedItem.captured.user).isSameAs(user)
            assertThat(savedItem.captured.game).isSameAs(game)
        }

        @Test
        fun `should use a reference to the user instead of loading it`() {
            stubAdd()

            collectionService.addToCollection(userId, request)

            verify(exactly = 1) { userRepository.getReferenceById(userId) }
            verify(exactly = 0) { userRepository.findById(any()) }
        }

        @Test
        fun `should evaluate the achievements of the user after commit`() {
            stubAdd()

            collectionService.addToCollection(userId, request)

            verifyOrder {
                collectionItemRepository.saveAndFlush(any())
                userAchievementService.evaluateAfterCommit(setOf(userId))
            }
        }

        @Test
        fun `should throw GameAlreadyInCollectionException without loading the game`() {
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns true

            assertThatThrownBy { collectionService.addToCollection(userId, request) }
                .isInstanceOf(GameAlreadyInCollectionException::class.java)

            verify(exactly = 0) { gameRepository.findById(any()) }
            verify(exactly = 0) { collectionItemRepository.saveAndFlush(any()) }
            verify(exactly = 0) { userAchievementService.evaluateAfterCommit(any()) }
        }

        @Test
        fun `should map a concurrent insert of the same game to GameAlreadyInCollectionException`() {
            stubAdd()
            every { collectionItemRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate key value violates unique constraint")

            assertThatThrownBy { collectionService.addToCollection(userId, request) }
                .isInstanceOf(GameAlreadyInCollectionException::class.java)

            verify(exactly = 0) { userAchievementService.evaluateAfterCommit(any()) }
        }

        @Test
        fun `should throw GameNotFoundException when the game does not exist`() {
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns false
            every { gameRepository.findById(gameId) } returns Optional.empty()

            assertThatThrownBy { collectionService.addToCollection(userId, request) }
                .isInstanceOf(GameNotFoundException::class.java)

            verify(exactly = 0) { collectionItemRepository.saveAndFlush(any()) }
            verify(exactly = 0) { userAchievementService.evaluateAfterCommit(any()) }
        }

        @Test
        fun `should rethrow unexpected errors without evaluating achievements`() {
            stubAdd()
            every { collectionItemRepository.saveAndFlush(any()) } throws RuntimeException("Save failed")

            assertThatThrownBy { collectionService.addToCollection(userId, request) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Save failed")

            verify(exactly = 0) { userAchievementService.evaluateAfterCommit(any()) }
        }
    }

    @Nested
    @DisplayName("removeFromCollection")
    inner class RemoveFromCollection {

        @Test
        fun `should remove an item owned by the user`() {
            every { collectionItemRepository.findById(itemId) } returns Optional.of(item)
            every { collectionItemRepository.delete(item) } just Runs

            collectionService.removeFromCollection(userId, itemId)

            verify(exactly = 1) { collectionItemRepository.delete(item) }
        }

        @Test
        fun `should throw CollectionItemNotFoundException when the item does not exist`() {
            every { collectionItemRepository.findById(itemId) } returns Optional.empty()

            assertThatThrownBy { collectionService.removeFromCollection(userId, itemId) }
                .isInstanceOf(CollectionItemNotFoundException::class.java)

            verify(exactly = 0) { collectionItemRepository.delete(any<CollectionItem>()) }
        }

        @Test
        fun `should refuse to remove an item of another user`() {
            val otherUser = User(id = UUID.randomUUID(), username = "other", email = "other@test.com", passwordHash = "pwd")
            every { collectionItemRepository.findById(itemId) } returns Optional.of(CollectionItem(id = itemId, user = otherUser, game = game))

            assertThatThrownBy { collectionService.removeFromCollection(userId, itemId) }
                .isInstanceOf(NotCollectionItemOwnerException::class.java)

            verify(exactly = 0) { collectionItemRepository.delete(any<CollectionItem>()) }
        }

        @Test
        fun `should rethrow unexpected errors`() {
            every { collectionItemRepository.findById(itemId) } throws RuntimeException("DB Connection failed")

            assertThatThrownBy { collectionService.removeFromCollection(userId, itemId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB Connection failed")
        }
    }

    @Nested
    @DisplayName("listCollection")
    inner class ListCollection {

        private val pageableSlot = slot<Pageable>()

        private fun stubFindAll(items: List<CollectionItem> = listOf(item)) {
            every { collectionItemRepository.findAll(any<Specification<CollectionItem>>(), capture(pageableSlot)) } answers {
                PageImpl(items, secondArg(), items.size.toLong())
            }
        }

        private fun list(page: Int = 0, size: Int = 10, gameName: String? = null, played: Boolean? = null, isExpansion: Boolean? = null, sort: String? = null) =
            collectionService.listCollection(userId, page, size, gameName, played, isExpansion, sort)

        @Test
        fun `should return the page of collection items`() {
            stubFindAll()

            val result = list()

            assertThat(result.content.map { it.id }).containsExactly(itemId)
            assertThat(result.totalElements).isEqualTo(1L)
        }

        @Test
        fun `should pass the user and every filter to the specification`() {
            mockkObject(CollectionItemSpecification)
            stubFindAll()

            list(gameName = "Catan", played = true, isExpansion = false)

            verify(exactly = 1) { CollectionItemSpecification.withFilters(userId, "Catan", true, false) }
        }

        @Test
        fun `should show the most recently added games first by default`() {
            stubFindAll()

            list()

            assertThat(pageableSlot.captured.sort.getOrderFor("createdAt")?.direction).isEqualTo(Sort.Direction.DESC)
        }

        @Test
        fun `should sort by game name when requested`() {
            stubFindAll()

            list(sort = "game.name-asc")

            assertThat(pageableSlot.captured.sort.getOrderFor("game.name")?.direction).isEqualTo(Sort.Direction.ASC)
        }

        @Test
        fun `should keep the requested page and size`() {
            stubFindAll()

            list(page = 2, size = 15)

            assertThat(pageableSlot.captured.pageNumber).isEqualTo(2)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(15)
        }

        @Test
        fun `should normalize a negative page and clamp an oversized size`() {
            stubFindAll()

            list(page = -5, size = 500)

            assertThat(pageableSlot.captured.pageNumber).isEqualTo(0)
            assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
        }

        @Test
        fun `should clamp a non-positive size to one`() {
            stubFindAll()

            list(size = -10)

            assertThat(pageableSlot.captured.pageSize).isEqualTo(1)
        }

        @Test
        fun `should throw InvalidSortException without querying for an invalid sort field`() {
            assertThatThrownBy { list(sort = "user.email-asc") }
                .isInstanceOf(InvalidSortException::class.java)

            verify(exactly = 0) { collectionItemRepository.findAll(any<Specification<CollectionItem>>(), any<Pageable>()) }
        }
    }

    @Nested
    @DisplayName("getStatus")
    inner class GetStatus {

        @Test
        fun `should return the item id when the game is in the collection`() {
            every { collectionItemRepository.findIdByUserIdAndGameId(userId, gameId) } returns itemId

            val result = collectionService.getStatus(userId, gameId)

            assertThat(result.inCollection).isTrue
            assertThat(result.itemId).isEqualTo(itemId)
        }

        @Test
        fun `should return no item when the game is not in the collection`() {
            every { collectionItemRepository.findIdByUserIdAndGameId(userId, gameId) } returns null

            val result = collectionService.getStatus(userId, gameId)

            assertThat(result.inCollection).isFalse
            assertThat(result.itemId).isNull()
        }

        @Test
        fun `should rethrow unexpected errors`() {
            every { collectionItemRepository.findIdByUserIdAndGameId(userId, gameId) } throws RuntimeException("Unexpected error")

            assertThatThrownBy { collectionService.getStatus(userId, gameId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Unexpected error")
        }
    }
}