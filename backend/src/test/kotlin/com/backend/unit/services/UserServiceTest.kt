package com.backend.unit.services

import com.backend.exceptions.UploadedFileNotFoundException
import com.backend.exceptions.UserNotFoundException
import com.backend.exceptions.UsernameAlreadyTakenException
import com.backend.models.dtos.UpdateUserRequest
import com.backend.models.entities.Game
import com.backend.models.entities.Match
import com.backend.models.entities.MatchPlayer
import com.backend.models.entities.MatchTeam
import com.backend.models.entities.UploadedFile
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FileOwnerType
import com.backend.models.enums.FriendshipStatus
import com.backend.repositories.*
import com.backend.services.FriendService
import com.backend.services.UploadedFileService
import com.backend.services.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Pageable
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.util.Optional
import java.util.UUID

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var matchRepository: MatchRepository
    private lateinit var matchPlayerRepository: MatchPlayerRepository
    private lateinit var matchTeamRepository: MatchTeamRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var pushTokenRepository: PushTokenRepository
    private lateinit var friendRequestRepository: FriendRequestRepository
    private lateinit var wishlistMemberRepository: WishlistMemberRepository
    private lateinit var uploadedFileRepository: UploadedFileRepository
    private lateinit var collectionItemRepository: CollectionItemRepository
    private lateinit var wishlistRepository: WishlistRepository
    private lateinit var wishlistItemRepository: WishlistItemRepository
    private lateinit var uploadedFileService: UploadedFileService
    private lateinit var friendService: FriendService
    private lateinit var emailVerificationRepository: EmailVerificationRepository
    private lateinit var passwordResetRepository: PasswordResetRepository
    private lateinit var userAchievementRepository: UserAchievementRepository
    private lateinit var wishlistActivityRepository: WishlistActivityRepository
    private lateinit var transactionManager: PlatformTransactionManager
    private lateinit var service: UserService

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val avatarId = UUID.randomUUID()
    private val definitions = mutableListOf<TransactionDefinition>()

    private lateinit var user: User
    private lateinit var otherUser: User

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        matchRepository = mockk(relaxed = true)
        matchPlayerRepository = mockk(relaxed = true)
        matchTeamRepository = mockk(relaxed = true)
        refreshTokenRepository = mockk(relaxed = true)
        pushTokenRepository = mockk(relaxed = true)
        friendRequestRepository = mockk(relaxed = true)
        wishlistMemberRepository = mockk(relaxed = true)
        uploadedFileRepository = mockk(relaxed = true)
        collectionItemRepository = mockk(relaxed = true)
        wishlistRepository = mockk(relaxed = true)
        wishlistItemRepository = mockk(relaxed = true)
        uploadedFileService = mockk(relaxUnitFun = true)
        friendService = mockk()
        emailVerificationRepository = mockk(relaxed = true)
        passwordResetRepository = mockk(relaxed = true)
        userAchievementRepository = mockk(relaxed = true)
        wishlistActivityRepository = mockk(relaxed = true)
        transactionManager = mockk(relaxed = true)

        definitions.clear()
        every { transactionManager.getTransaction(any()) } answers { definitions += firstArg<TransactionDefinition>(); SimpleTransactionStatus() }

        user = User(id = userId, username = "stefano", email = "stefano@example.com", passwordHash = "hash")
        otherUser = User(id = otherUserId, username = "marco", email = "marco@example.com", passwordHash = "hash")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.findById(otherUserId) } returns Optional.of(otherUser)
        every { userRepository.saveAndFlush(any<User>()) } answers { firstArg() }
        every { matchRepository.findAllForUser(any()) } returns emptyList()
        every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(any(), any()) } returns emptyList()

        service = UserService(
            userRepository = userRepository,
            matchRepository = matchRepository,
            matchPlayerRepository = matchPlayerRepository,
            matchTeamRepository = matchTeamRepository,
            refreshTokenRepository = refreshTokenRepository,
            pushTokenRepository = pushTokenRepository,
            friendRequestRepository = friendRequestRepository,
            wishlistMemberRepository = wishlistMemberRepository,
            uploadedFileRepository = uploadedFileRepository,
            collectionItemRepository = collectionItemRepository,
            wishlistRepository = wishlistRepository,
            wishlistItemRepository = wishlistItemRepository,
            uploadedFileService = uploadedFileService,
            friendService = friendService,
            emailVerificationRepository = emailVerificationRepository,
            passwordResetRepository = passwordResetRepository,
            userAchievementRepository = userAchievementRepository,
            wishlistActivityRepository = wishlistActivityRepository,
            transactionManager = transactionManager,
        )
    }

    @AfterEach
    fun tearDown() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun avatarFile(id: UUID = avatarId) = UploadedFile(
        id = id,
        ownerType = FileOwnerType.USER_AVATAR,
        ownerId = userId,
        uploadedBy = user,
        fileName = "avatar.webp",
        objectKey = "user_avatar/$userId/$id.webp",
        contentType = "image/webp",
        size = 100L,
    )

    @Nested
    @DisplayName("searchByUsername")
    inner class SearchByUsernameTests {

        private val patternSlot = slot<String>()
        private val pageableSlot = slot<Pageable>()

        @BeforeEach
        fun stubSearch() {
            every { userRepository.searchActiveByUsername(capture(patternSlot), userId, capture(pageableSlot)) } returns listOf(otherUser)
        }

        @Test
        fun `should return the matching users`() {
            val result = service.searchByUsername(userId, "mar")

            assertThat(result.map { it.id }).containsExactly(otherUserId)
            assertThat(result[0].username).isEqualTo("marco")
        }

        @Test
        fun `should exclude the current user and limit the results to 20`() {
            service.searchByUsername(userId, "mar")

            verify(exactly = 1) { userRepository.searchActiveByUsername(any(), userId, any()) }
            assertThat(pageableSlot.captured.pageNumber).isZero()
            assertThat(pageableSlot.captured.pageSize).isEqualTo(20)
        }

        @Test
        fun `should trim the query`() {
            service.searchByUsername(userId, "  mar  ")

            assertThat(patternSlot.captured).isEqualTo("mar")
        }

        @Test
        fun `should escape the like wildcards`() {
            service.searchByUsername(userId, """50%_off\""")

            assertThat(patternSlot.captured).isEqualTo("""50\%\_off\\""")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "m", "  m  "])
        fun `should not query for a query shorter than two characters`(query: String) {
            assertThat(service.searchByUsername(userId, query)).isEmpty()

            verify(exactly = 0) { userRepository.searchActiveByUsername(any(), any(), any()) }
        }

        @Test
        fun `should propagate a repository failure`() {
            every { userRepository.searchActiveByUsername(any(), any(), any()) } throws RuntimeException("Database error")

            assertThatThrownBy { service.searchByUsername(userId, "mar") }
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("getUserById")
    inner class GetUserByIdTests {

        @Test
        fun `should return the user`() {
            val result = service.getUserById(userId)

            assertThat(result.id).isEqualTo(userId)
            assertThat(result.username).isEqualTo("stefano")
        }

        @Test
        fun `should still return a deleted user marked as deleted`() {
            user.accountStatus = AccountStatus.DELETED

            assertThat(service.getUserById(userId).deleted).isTrue()
        }

        @Test
        fun `should throw UserNotFoundException when the user does not exist`() {
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { service.getUserById(userId) }
                .isInstanceOf(UserNotFoundException::class.java)
        }

        @Test
        fun `should propagate a repository failure`() {
            every { userRepository.findById(userId) } throws RuntimeException("Database error")

            assertThatThrownBy { service.getUserById(userId) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("getUserProfile")
    inner class GetUserProfileTests {

        @Test
        fun `should return the profile with stats and friendship status`() {
            every { friendService.getFriendshipStatus(userId, otherUserId) } returns FriendshipStatus.FRIENDS
            every { matchRepository.countCompletedMatchesForUser(otherUserId) } returns 15L
            every { matchRepository.countWonMatchesForUser(otherUserId) } returns 6L

            val result = service.getUserProfile(userId, otherUserId)

            assertThat(result.user.id).isEqualTo(otherUserId)
            assertThat(result.user.username).isEqualTo("marco")
            assertThat(result.friendshipStatus).isEqualTo("FRIENDS")
            assertThat(result.totalMatches).isEqualTo(15L)
            assertThat(result.totalWins).isEqualTo(6L)
        }

        @Test
        fun `should return SELF when viewing your own profile`() {
            every { friendService.getFriendshipStatus(userId, userId) } returns FriendshipStatus.SELF

            assertThat(service.getUserProfile(userId, userId).friendshipStatus).isEqualTo("SELF")
        }

        @Test
        fun `should throw UserNotFoundException without computing anything else`() {
            every { userRepository.findById(otherUserId) } returns Optional.empty()

            assertThatThrownBy { service.getUserProfile(userId, otherUserId) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { friendService.getFriendshipStatus(any(), any()) }
            verify(exactly = 0) { matchRepository.countCompletedMatchesForUser(any()) }
        }

        @Test
        fun `should propagate a failure of the friend service`() {
            every { friendService.getFriendshipStatus(userId, otherUserId) } throws RuntimeException("Database error")

            assertThatThrownBy { service.getUserProfile(userId, otherUserId) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    @Nested
    @DisplayName("updateProfile")
    inner class UpdateProfileTests {

        @BeforeEach
        fun stubUsernames() {
            every { userRepository.existsByUsernameIgnoreCase(any()) } returns false
        }

        @Test
        fun `should change the username after checking it is free`() {
            val result = service.updateProfile(userId, UpdateUserRequest(username = "stefano92"))

            assertThat(result.username).isEqualTo("stefano92")
            assertThat(user.username).isEqualTo("stefano92")
            verify(exactly = 1) { userRepository.existsByUsernameIgnoreCase("stefano92") }
            verify(exactly = 1) { userRepository.saveAndFlush(user) }
        }

        @Test
        fun `should trim the new username`() {
            service.updateProfile(userId, UpdateUserRequest(username = "  stefano92  "))

            assertThat(user.username).isEqualTo("stefano92")
            verify(exactly = 1) { userRepository.existsByUsernameIgnoreCase("stefano92") }
        }

        @Test
        fun `should apply a casing change without checking uniqueness`() {
            service.updateProfile(userId, UpdateUserRequest(username = "Stefano"))

            assertThat(user.username).isEqualTo("Stefano")
            verify(exactly = 0) { userRepository.existsByUsernameIgnoreCase(any()) }
        }

        @Test
        fun `should throw UsernameAlreadyTakenException without saving when the username is taken`() {
            every { userRepository.existsByUsernameIgnoreCase("Marco") } returns true

            assertThatThrownBy { service.updateProfile(userId, UpdateUserRequest(username = "Marco")) }
                .isInstanceOf(UsernameAlreadyTakenException::class.java)

            verify(exactly = 0) { userRepository.saveAndFlush(any<User>()) }
        }

        @Test
        fun `should throw UsernameAlreadyTakenException when someone takes the username at the same time`() {
            every { userRepository.saveAndFlush(any<User>()) } throws DataIntegrityViolationException("duplicate key value violates unique constraint")

            assertThatThrownBy { service.updateProfile(userId, UpdateUserRequest(username = "stefano92")) }
                .isInstanceOf(UsernameAlreadyTakenException::class.java)
        }

        @Test
        fun `should change only the notifications`() {
            val result = service.updateProfile(userId, UpdateUserRequest(notificationsEnabled = false))

            assertThat(result.notificationsEnabled).isFalse()
            assertThat(user.username).isEqualTo("stefano")
            verify(exactly = 0) { userRepository.existsByUsernameIgnoreCase(any()) }
        }

        @Test
        fun `should leave everything unchanged when nothing is sent`() {
            val result = service.updateProfile(userId, UpdateUserRequest())

            assertThat(result.username).isEqualTo("stefano")
            assertThat(result.notificationsEnabled).isTrue()
            assertThat(user.avatarId).isNull()
        }

        @Test
        fun `should set an avatar uploaded by the user`() {
            every { uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(avatarId, FileOwnerType.USER_AVATAR, userId) } returns Optional.of(avatarFile())

            service.updateProfile(userId, UpdateUserRequest(avatarId = avatarId))

            assertThat(user.avatarId).isEqualTo(avatarId)
        }

        @Test
        fun `should refuse an avatar that does not belong to the user`() {
            every { uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(avatarId, FileOwnerType.USER_AVATAR, userId) } returns Optional.empty()

            assertThatThrownBy { service.updateProfile(userId, UpdateUserRequest(avatarId = avatarId)) }
                .isInstanceOf(UploadedFileNotFoundException::class.java)

            assertThat(user.avatarId).isNull()
            verify(exactly = 0) { userRepository.saveAndFlush(any<User>()) }
        }

        @Test
        fun `should remove the avatar ignoring any avatar id sent together`() {
            user.avatarId = avatarId

            service.updateProfile(userId, UpdateUserRequest(avatarId = UUID.randomUUID(), removeAvatar = true))

            assertThat(user.avatarId).isNull()
            verify(exactly = 0) { uploadedFileRepository.findByIdAndOwnerTypeAndOwnerId(any(), any(), any()) }
        }

        @Test
        fun `should throw UserNotFoundException when the user does not exist`() {
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { service.updateProfile(userId, UpdateUserRequest(username = "stefano92")) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { userRepository.saveAndFlush(any<User>()) }
        }
    }

    @Nested
    @DisplayName("deleteAvatar")
    inner class DeleteAvatarTests {

        @Test
        fun `should delete the file and clear the current avatar`() {
            user.avatarId = avatarId

            service.deleteAvatar(userId, avatarId)

            verify(exactly = 1) { uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, avatarId) }
            assertThat(user.avatarId).isNull()
        }

        @Test
        fun `should keep the current avatar when deleting an older file`() {
            val olderId = UUID.randomUUID()
            user.avatarId = avatarId

            service.deleteAvatar(userId, olderId)

            verify(exactly = 1) { uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, olderId) }
            assertThat(user.avatarId).isEqualTo(avatarId)
        }

        @Test
        fun `should propagate a file that does not belong to the user`() {
            user.avatarId = avatarId
            every { uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, avatarId) } throws UploadedFileNotFoundException(avatarId)

            assertThatThrownBy { service.deleteAvatar(userId, avatarId) }
                .isInstanceOf(UploadedFileNotFoundException::class.java)

            assertThat(user.avatarId).isEqualTo(avatarId)
        }
    }

    @Nested
    @DisplayName("deleteAccount")
    inner class DeleteAccountTests {

        private val game = Game(id = UUID.randomUUID(), bggId = 13L, name = "Catan")

        @BeforeEach
        fun startTransaction() {
            TransactionSynchronizationManager.initSynchronization()
        }

        private fun match() = Match(id = UUID.randomUUID(), game = game, createdBy = user, playedAt = Instant.now())

        private fun player(match: Match, user: User? = null, guestName: String? = null) =
            MatchPlayer(id = UUID.randomUUID(), match = match, user = user, guestName = guestName)

        private fun stubMatches(players: Map<Match, List<MatchPlayer>>) {
            every { matchRepository.findAllForUser(userId) } returns players.keys.toList()
            every { matchPlayerRepository.findAllByMatchIdIn(any()) } returns players.values.flatten()
        }

        private fun synchronizations(): List<TransactionSynchronization> = TransactionSynchronizationManager.getSynchronizations()

        private fun commit() = synchronizations().forEach { it.afterCommit(); it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }

        @Test
        fun `should anonymize the user and mark it as deleted`() {
            user.avatarId = avatarId

            val result = service.deleteAccount(userId)

            assertThat(result.message).isEqualTo("Your account has been deleted")
            assertThat(user.username).isEqualTo("deleted-user-$userId")
            assertThat(user.email).isEqualTo("deleted-$userId@letstable.invalid")
            assertThat(user.passwordHash).isEmpty()
            assertThat(user.avatarId).isNull()
            assertThat(user.notificationsEnabled).isFalse()
            assertThat(user.accountStatus).isEqualTo(AccountStatus.DELETED)
        }

        @Test
        fun `should turn the user into a guest named after the former username`() {
            service.deleteAccount(userId)

            verify(exactly = 1) { matchPlayerRepository.convertUserToGuest(userId, "stefano") }
        }

        @Test
        fun `should remove every personal data of the user`() {
            service.deleteAccount(userId)

            verify(exactly = 1) { refreshTokenRepository.revokeAllByUserId(userId) }
            verify(exactly = 1) { pushTokenRepository.deleteAllByUserId(userId) }
            verify(exactly = 1) { emailVerificationRepository.deleteAllByUserId(userId) }
            verify(exactly = 1) { passwordResetRepository.deleteAllByUserId(userId) }
            verify(exactly = 1) { userAchievementRepository.deleteAllByUserId(userId) }
            verify(exactly = 1) { wishlistActivityRepository.deleteAllByActorId(userId) }
            verify(exactly = 1) { friendRequestRepository.deleteAllInvolvingUser(userId) }
            verify(exactly = 1) { collectionItemRepository.deleteAllByUserId(userId) }
            verify(exactly = 1) { wishlistItemRepository.deleteAllByWishlistOwnerId(userId) }
            verify(exactly = 1) { wishlistMemberRepository.deleteAllByWishlistOwnerId(userId) }
            verify(exactly = 1) { wishlistMemberRepository.deleteAllByUserId(userId) }
            verify(exactly = 1) { wishlistRepository.deleteAllByOwnerId(userId) }
            verify(exactly = 1) { uploadedFileRepository.clearUploadedByUser(userId) }
        }

        @Test
        fun `should delete the wishlist content before the wishlists`() {
            service.deleteAccount(userId)

            verifyOrder {
                wishlistItemRepository.deleteAllByWishlistOwnerId(userId)
                wishlistMemberRepository.deleteAllByWishlistOwnerId(userId)
                wishlistRepository.deleteAllByOwnerId(userId)
            }
        }

        @Test
        fun `should delete a match played only with guests`() {
            val solo = match()
            val players = listOf(player(solo, user = user), player(solo, guestName = "Luca"))
            val teams = listOf(MatchTeam(id = UUID.randomUUID(), match = solo, name = "A", color = "Red", score = 0, isWinner = false, isStartingFirst = false))
            stubMatches(mapOf(solo to players))
            every { matchTeamRepository.findAllByMatchIdIn(listOf(solo.id!!)) } returns teams

            service.deleteAccount(userId)

            verify(exactly = 1) { matchPlayerRepository.deleteAll(players) }
            verify(exactly = 1) { matchTeamRepository.deleteAll(teams) }
            verify(exactly = 1) { matchRepository.deleteAll(listOf(solo)) }
        }

        @Test
        fun `should keep a match played with another registered user`() {
            val shared = match()
            stubMatches(mapOf(shared to listOf(player(shared, user = user), player(shared, user = otherUser))))

            service.deleteAccount(userId)

            verify(exactly = 0) { matchRepository.deleteAll(any<Iterable<Match>>()) }
            verify(exactly = 0) { matchPlayerRepository.deleteAll(any<Iterable<MatchPlayer>>()) }
        }

        @Test
        fun `should delete only the solo matches among many`() {
            val solo = match()
            val shared = match()
            val soloPlayers = listOf(player(solo, user = user))
            stubMatches(mapOf(solo to soloPlayers, shared to listOf(player(shared, user = user), player(shared, user = otherUser))))

            service.deleteAccount(userId)

            verify(exactly = 1) { matchPlayerRepository.deleteAll(soloPlayers) }
            verify(exactly = 1) { matchTeamRepository.findAllByMatchIdIn(listOf(solo.id!!)) }
            verify(exactly = 1) { matchRepository.deleteAll(listOf(solo)) }
        }

        @Test
        fun `should delete a match created by the user without players`() {
            val empty = match()
            stubMatches(mapOf(empty to emptyList()))

            service.deleteAccount(userId)

            verify(exactly = 1) { matchRepository.deleteAll(listOf(empty)) }
        }

        @Test
        fun `should not load players when the user has no matches`() {
            service.deleteAccount(userId)

            verify(exactly = 0) { matchPlayerRepository.findAllByMatchIdIn(any()) }
            verify(exactly = 0) { matchRepository.deleteAll(any<Iterable<Match>>()) }
        }

        @Test
        fun `should delete the solo matches before turning the user into a guest`() {
            val solo = match()
            stubMatches(mapOf(solo to listOf(player(solo, user = user))))

            service.deleteAccount(userId)

            verifyOrder {
                matchRepository.deleteAll(listOf(solo))
                matchPlayerRepository.convertUserToGuest(userId, "stefano")
            }
        }

        @Test
        fun `should delete the avatars only after the commit, each in a new transaction`() {
            val secondId = UUID.randomUUID()
            every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.USER_AVATAR, userId) } returns listOf(avatarFile(), avatarFile(secondId))

            service.deleteAccount(userId)

            verify(exactly = 0) { uploadedFileService.deleteFile(any(), any(), any()) }

            commit()

            verify(exactly = 1) { uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, avatarId) }
            verify(exactly = 1) { uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, secondId) }
            assertThat(definitions).hasSize(2).allMatch { it.propagationBehavior == TransactionDefinition.PROPAGATION_REQUIRES_NEW }
        }

        @Test
        fun `should keep deleting the other avatars when one fails`() {
            val secondId = UUID.randomUUID()
            every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.USER_AVATAR, userId) } returns listOf(avatarFile(), avatarFile(secondId))
            every { uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, avatarId) } throws RuntimeException("Storage error")

            service.deleteAccount(userId)

            assertThatCode { commit() }.doesNotThrowAnyException()
            verify(exactly = 1) { uploadedFileService.deleteFile(FileOwnerType.USER_AVATAR, userId, secondId) }
        }

        @Test
        fun `should keep the avatars when the transaction rolls back`() {
            every { uploadedFileRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(FileOwnerType.USER_AVATAR, userId) } returns listOf(avatarFile())

            service.deleteAccount(userId)
            synchronizations().forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 0) { uploadedFileService.deleteFile(any(), any(), any()) }
        }

        @Test
        fun `should not schedule anything when the user has no avatars`() {
            service.deleteAccount(userId)

            assertThat(synchronizations()).isEmpty()
        }

        @Test
        fun `should throw UserNotFoundException without deleting anything when the user does not exist`() {
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { service.deleteAccount(userId) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { matchRepository.findAllForUser(any()) }
            verify(exactly = 0) { matchPlayerRepository.convertUserToGuest(any(), any()) }
        }

        @Test
        fun `should throw UserNotFoundException when the account is already deleted`() {
            user.accountStatus = AccountStatus.DELETED

            assertThatThrownBy { service.deleteAccount(userId) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { matchPlayerRepository.convertUserToGuest(any(), any()) }
            verify(exactly = 0) { refreshTokenRepository.revokeAllByUserId(any()) }
        }

        @Test
        fun `should leave the user untouched when a deletion fails`() {
            every { friendRequestRepository.deleteAllInvolvingUser(userId) } throws RuntimeException("Database error")

            assertThatThrownBy { service.deleteAccount(userId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")

            assertThat(user.username).isEqualTo("stefano")
            assertThat(user.accountStatus).isNotEqualTo(AccountStatus.DELETED)
            assertThat(synchronizations()).isEmpty()
        }
    }

    @Nested
    @DisplayName("getMyStats")
    inner class GetMyStatsTests {

        @Test
        fun `should collect the stats of the user`() {
            every { matchRepository.countCompletedMatchesForUser(userId) } returns 20L
            every { matchRepository.countWonMatchesForUser(userId) } returns 8L
            every { collectionItemRepository.countByUserId(userId) } returns 35L
            every { friendRequestRepository.countFriendshipsForUser(userId) } returns 4L

            val result = service.getMyStats(userId)

            assertThat(result.totalMatches).isEqualTo(20L)
            assertThat(result.totalWins).isEqualTo(8L)
            assertThat(result.totalGames).isEqualTo(35L)
            assertThat(result.totalFriends).isEqualTo(4L)
        }

        @Test
        fun `should propagate a repository failure`() {
            every { matchRepository.countCompletedMatchesForUser(userId) } throws RuntimeException("Database error")

            assertThatThrownBy { service.getMyStats(userId) }
                .isInstanceOf(RuntimeException::class.java)
        }
    }
}