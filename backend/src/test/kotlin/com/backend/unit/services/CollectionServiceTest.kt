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
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
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

    @InjectMockKs
    private lateinit var collectionService: CollectionService

    private val userId: UUID = UUID.randomUUID()
    private val gameId: UUID = UUID.randomUUID()
    private val itemId: UUID = UUID.randomUUID()

    private val mockUser = User(
        id = userId,
        username = "stefano",
        email = "stefano@test.com",
        passwordHash = "hashed_pwd",
        role = UserRole.USER,
        accountStatus = AccountStatus.ACTIVE
    )

    private val mockGame = Game(
        id = gameId,
        bggId = 12345L,
        name = "Catan"
    )

    private val mockCollectionItem = CollectionItem(
        id = itemId,
        user = mockUser,
        game = mockGame
    )

    @Nested
    @DisplayName("addToCollection")
    inner class AddToCollection {

        @Test
        fun `should successfully add game to collection`() {
            val request = AddToCollectionRequest(gameId = gameId)
            val itemSlot = slot<CollectionItem>()

            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns false
            every { userRepository.findById(userId) } returns Optional.of(mockUser)
            every { gameRepository.findById(gameId) } returns Optional.of(mockGame)
            every { collectionItemRepository.save(capture(itemSlot)) } returns mockCollectionItem

            val result = collectionService.addToCollection(userId, request)

            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(itemId)
            assertThat(itemSlot.captured.user).isEqualTo(mockUser)
            assertThat(itemSlot.captured.game).isEqualTo(mockGame)

            verify(exactly = 1) { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) }
            verify(exactly = 1) { userRepository.findById(userId) }
            verify(exactly = 1) { gameRepository.findById(gameId) }
            verify(exactly = 1) { collectionItemRepository.save(any()) }
        }

        @Test
        fun `should throw GameAlreadyInCollectionException when game is already in collection`() {
            val request = AddToCollectionRequest(gameId = gameId)
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns true

            assertThatThrownBy { collectionService.addToCollection(userId, request) }
                .isInstanceOf(GameAlreadyInCollectionException::class.java)

            verify(exactly = 1) { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) }
            verify(exactly = 0) { userRepository.findById(any()) }
            verify(exactly = 0) { gameRepository.findById(any()) }
            verify(exactly = 0) { collectionItemRepository.save(any()) }
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when user does not exist`() {
            val request = AddToCollectionRequest(gameId = gameId)
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns false
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { collectionService.addToCollection(userId, request) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)

            verify(exactly = 1) { userRepository.findById(userId) }
            verify(exactly = 0) { gameRepository.findById(any()) }
            verify(exactly = 0) { collectionItemRepository.save(any()) }
        }

        @Test
        fun `should throw GameNotFoundException when game does not exist`() {
            val request = AddToCollectionRequest(gameId = gameId)
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } returns false
            every { userRepository.findById(userId) } returns Optional.of(mockUser)
            every { gameRepository.findById(gameId) } returns Optional.empty()

            assertThatThrownBy { collectionService.addToCollection(userId, request) }
                .isInstanceOf(GameNotFoundException::class.java)

            verify(exactly = 1) { userRepository.findById(userId) }
            verify(exactly = 1) { gameRepository.findById(gameId) }
            verify(exactly = 0) { collectionItemRepository.save(any()) }
        }

        @Test
        fun `should rethrow unexpected database exception`() {
            val request = AddToCollectionRequest(gameId = gameId)
            every { collectionItemRepository.existsByUserIdAndGameId(userId, gameId) } throws RuntimeException("Database error")

            assertThatThrownBy { collectionService.addToCollection(userId, request) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")
        }
    }

    @Nested
    @DisplayName("removeFromCollection")
    inner class RemoveFromCollection {

        @Test
        fun `should successfully remove item from collection`() {
            every { collectionItemRepository.findById(itemId) } returns Optional.of(mockCollectionItem)
            every { collectionItemRepository.delete(mockCollectionItem) } just Runs

            collectionService.removeFromCollection(userId, itemId)

            verify(exactly = 1) { collectionItemRepository.findById(itemId) }
            verify(exactly = 1) { collectionItemRepository.delete(mockCollectionItem) }
        }

        @Test
        fun `should throw CollectionItemNotFoundException when item does not exist`() {
            every { collectionItemRepository.findById(itemId) } returns Optional.empty()

            assertThatThrownBy { collectionService.removeFromCollection(userId, itemId) }
                .isInstanceOf(CollectionItemNotFoundException::class.java)

            verify(exactly = 1) { collectionItemRepository.findById(itemId) }
            verify(exactly = 0) { collectionItemRepository.delete(any<CollectionItem>()) }
        }

        @Test
        fun `should throw NotCollectionItemOwnerException when item belongs to another user`() {
            val otherUserId = UUID.randomUUID()
            val otherUser = User(
                id = otherUserId,
                username = "other",
                email = "other@test.com",
                passwordHash = "pwd"
            )
            val itemOfOtherUser = CollectionItem(id = itemId, user = otherUser, game = mockGame)

            every { collectionItemRepository.findById(itemId) } returns Optional.of(itemOfOtherUser)

            assertThatThrownBy { collectionService.removeFromCollection(userId, itemId) }
                .isInstanceOf(NotCollectionItemOwnerException::class.java)

            verify(exactly = 1) { collectionItemRepository.findById(itemId) }
            verify(exactly = 0) { collectionItemRepository.delete(any<CollectionItem>()) }
        }

        @Test
        fun `should rethrow generic exception during removal`() {
            every { collectionItemRepository.findById(itemId) } throws RuntimeException("DB Connection failed")

            assertThatThrownBy { collectionService.removeFromCollection(userId, itemId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB Connection failed")
        }
    }

    @Nested
    @DisplayName("listCollection")
    inner class ListCollection {

        private fun mockSpecificationObject(): Specification<CollectionItem> {
            val dummySpec = mockk<Specification<CollectionItem>>()
            every { dummySpec.toPredicate(any(), any(), any()) } returns null
            mockkObject(CollectionItemSpecification)
            every { CollectionItemSpecification.withFilters(any(), any(), any(), any()) } returns dummySpec
            return dummySpec
        }

        @Test
        fun `should return paged collection with valid parameters`() {
            val page = 0
            val size = 10
            val pageableSlot = slot<Pageable>()
            val dummySpec = mockSpecificationObject()

            try {
                every {
                    collectionItemRepository.findAll(
                        eq(dummySpec),
                        capture(pageableSlot)
                    )
                } answers {
                    val capturedPageable = pageableSlot.captured
                    PageImpl(listOf(mockCollectionItem), capturedPageable, 1)
                }

                val result = collectionService.listCollection(
                    userId = userId,
                    page = page,
                    size = size,
                    gameName = "Catan",
                    played = true,
                    isExpansion = false,
                    sort = "game.name"
                )

                assertThat(result).isNotNull
                assertThat(result.content).hasSize(1)
                assertThat((pageableSlot.captured as PageRequest).pageNumber).isEqualTo(0)
                assertThat((pageableSlot.captured as PageRequest).pageSize).isEqualTo(10)

                verify(exactly = 1) {
                    collectionItemRepository.findAll(
                        eq(dummySpec),
                        any<Pageable>()
                    )
                }
            } finally {
                unmockkObject(CollectionItemSpecification)
            }
        }

        @Test
        fun `should normalize negative page to 0 and clamp size between 1 and 100`() {
            val pageableSlot = slot<Pageable>()
            val dummySpec = mockSpecificationObject()

            try {
                every {
                    collectionItemRepository.findAll(
                        eq(dummySpec),
                        capture(pageableSlot)
                    )
                } answers {
                    val capturedPageable = pageableSlot.captured
                    PageImpl(listOf(mockCollectionItem), capturedPageable, 1)
                }

                collectionService.listCollection(
                    userId = userId,
                    page = -5,
                    size = 500,
                    gameName = null,
                    played = null,
                    isExpansion = null,
                    sort = null
                )

                assertThat((pageableSlot.captured as PageRequest).pageNumber).isEqualTo(0)
                assertThat((pageableSlot.captured as PageRequest).pageSize).isEqualTo(100)
            } finally {
                unmockkObject(CollectionItemSpecification)
            }
        }

        @Test
        fun `should clamp size lower bound to 1 when size is non-positive`() {
            val pageableSlot = slot<Pageable>()
            val dummySpec = mockSpecificationObject()

            try {
                every {
                    collectionItemRepository.findAll(
                        eq(dummySpec),
                        capture(pageableSlot)
                    )
                } answers {
                    val capturedPageable = pageableSlot.captured
                    PageImpl(listOf(mockCollectionItem), capturedPageable, 1)
                }

                collectionService.listCollection(
                    userId = userId,
                    page = 0,
                    size = -10,
                    gameName = null,
                    played = null,
                    isExpansion = null,
                    sort = null
                )

                assertThat((pageableSlot.captured as PageRequest).pageSize).isEqualTo(1)
            } finally {
                unmockkObject(CollectionItemSpecification)
            }
        }

        @Test
        fun `should throw InvalidSortException when invalid sort parameter is passed`() {
            assertThatThrownBy {
                collectionService.listCollection(
                    userId = userId,
                    page = 0,
                    size = 10,
                    gameName = null,
                    played = null,
                    isExpansion = null,
                    sort = "invalidField,asc"
                )
            }.isInstanceOf(InvalidSortException::class.java)
        }
    }

    @Nested
    @DisplayName("getStatus")
    inner class GetStatus {

        @Test
        fun `should return inCollection true and itemId when item exists`() {
            every { collectionItemRepository.findByUserIdAndGameId(userId, gameId) } returns Optional.of(mockCollectionItem)

            val result = collectionService.getStatus(userId, gameId)

            assertThat(result.inCollection).isTrue
            assertThat(result.itemId).isEqualTo(itemId)

            verify(exactly = 1) { collectionItemRepository.findByUserIdAndGameId(userId, gameId) }
        }

        @Test
        fun `should return inCollection false and null itemId when item does not exist`() {
            every { collectionItemRepository.findByUserIdAndGameId(userId, gameId) } returns Optional.empty()

            val result = collectionService.getStatus(userId, gameId)

            assertThat(result.inCollection).isFalse
            assertThat(result.itemId).isNull()

            verify(exactly = 1) { collectionItemRepository.findByUserIdAndGameId(userId, gameId) }
        }

        @Test
        fun `should rethrow generic exception in getStatus`() {
            every { collectionItemRepository.findByUserIdAndGameId(userId, gameId) } throws RuntimeException("Unexpected error")

            assertThatThrownBy { collectionService.getStatus(userId, gameId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Unexpected error")
        }
    }
}