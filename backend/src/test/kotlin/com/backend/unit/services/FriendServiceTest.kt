package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.SendFriendRequestRequest
import com.backend.models.entities.FriendRequest
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FriendRequestStatus
import com.backend.models.enums.FriendshipStatus
import com.backend.models.enums.NotificationType
import com.backend.models.enums.UserRole
import com.backend.repositories.FriendRequestRepository
import com.backend.repositories.UserRepository
import com.backend.services.FriendService
import com.backend.services.PushNotificationService
import com.backend.services.UserAchievementService
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.dao.DataIntegrityViolationException
import java.util.*

@ExtendWith(MockKExtension::class)
class FriendServiceTest {

    @MockK(relaxUnitFun = true)
    private lateinit var friendRequestRepository: FriendRequestRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @RelaxedMockK
    private lateinit var pushNotificationService: PushNotificationService

    @RelaxedMockK
    private lateinit var userAchievementService: UserAchievementService

    private lateinit var friendService: FriendService

    private val senderId: UUID = UUID.randomUUID()
    private val receiverId: UUID = UUID.randomUUID()
    private val requestId: UUID = UUID.randomUUID()

    private val sender = user(senderId, "senderUser")
    private val receiver = user(receiverId, "receiverUser")

    @BeforeEach
    fun setUp() {
        friendService = FriendService(friendRequestRepository, userRepository, pushNotificationService, userAchievementService)
    }

    private fun user(id: UUID = UUID.randomUUID(), username: String = "user-$id", status: AccountStatus = AccountStatus.ACTIVE) =
        User(id = id, username = username, email = "$username@test.com", passwordHash = "hash", role = UserRole.USER, accountStatus = status)

    private fun friendRequest(
        from: User = sender,
        to: User = receiver,
        status: FriendRequestStatus = FriendRequestStatus.PENDING,
        id: UUID = requestId,
    ) = FriendRequest(id = id, sender = from, receiver = to, status = status)

    private fun verifyNoSideEffects() {
        verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { userAchievementService.evaluateAfterCommit(any()) }
    }

    @Nested
    @DisplayName("sendRequest")
    inner class SendRequest {

        private val request = SendFriendRequestRequest(receiverId = receiverId)
        private val savedRequest = slot<FriendRequest>()

        private fun stubUsers(receiverUser: User? = receiver) {
            every { userRepository.findById(senderId) } returns Optional.of(sender)
            every { userRepository.findById(receiverId) } returns Optional.ofNullable(receiverUser)
        }

        private fun stubExisting(vararg requests: FriendRequest) {
            every { friendRequestRepository.findAllBetween(senderId, receiverId) } returns requests.toList()
            every { friendRequestRepository.saveAndFlush(capture(savedRequest)) } answers { savedRequest.captured.apply { id = requestId } }
        }

        @Test
        fun `should create a pending request and notify the receiver`() {
            stubUsers()
            stubExisting()

            val result = friendService.sendRequest(senderId, request)

            assertThat(result.id).isEqualTo(requestId)
            assertThat(savedRequest.captured.sender).isSameAs(sender)
            assertThat(savedRequest.captured.receiver).isSameAs(receiver)
            assertThat(savedRequest.captured.status).isEqualTo(FriendRequestStatus.PENDING)

            verify(exactly = 1) {
                pushNotificationService.sendToUser(
                    userId = receiverId,
                    type = NotificationType.FRIEND_REQUEST,
                    title = "Nuova richiesta di amicizia",
                    body = "senderUser ti ha inviato una richiesta di amicizia",
                    data = mapOf("requestId" to requestId.toString()),
                )
            }
            verify(exactly = 0) { userAchievementService.evaluateAfterCommit(any()) }
        }

        @Test
        fun `should accept a pending request in the opposite direction instead of creating a new one`() {
            val reverse = friendRequest(from = receiver, to = sender)
            stubUsers()
            stubExisting(reverse)

            val result = friendService.sendRequest(senderId, request)

            assertThat(result.id).isEqualTo(requestId)
            assertThat(reverse.status).isEqualTo(FriendRequestStatus.ACCEPTED)
            verify(exactly = 0) { friendRequestRepository.saveAndFlush(any()) }
            verify(exactly = 1) {
                pushNotificationService.sendToUser(
                    userId = receiverId,
                    type = NotificationType.FRIEND_ACCEPTED,
                    title = "Richiesta accettata",
                    body = "senderUser ha accettato la tua richiesta di amicizia",
                    data = mapOf("requestId" to requestId.toString()),
                )
            }
            verify(exactly = 1) { userAchievementService.evaluateAfterCommit(setOf(senderId, receiverId)) }
        }

        @Test
        fun `should refuse to send a request to yourself without any lookup`() {
            assertThatThrownBy { friendService.sendRequest(senderId, SendFriendRequestRequest(receiverId = senderId)) }
                .isInstanceOf(CannotFriendSelfException::class.java)

            verify(exactly = 0) { userRepository.findById(any()) }
            verifyNoSideEffects()
        }

        @Test
        fun `should throw UserNotFoundException when the sender does not exist`() {
            every { userRepository.findById(senderId) } returns Optional.empty()

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(UserNotFoundException::class.java)

            verifyNoSideEffects()
        }

        @Test
        fun `should throw UserNotFoundException when the receiver does not exist`() {
            stubUsers(receiverUser = null)

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { friendRequestRepository.findAllBetween(any(), any()) }
            verifyNoSideEffects()
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["ACTIVE"], mode = EnumSource.Mode.EXCLUDE)
        fun `should treat a receiver that is not active as not found`(status: AccountStatus) {
            stubUsers(receiverUser = user(receiverId, "receiverUser", status))

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(UserNotFoundException::class.java)

            verify(exactly = 0) { friendRequestRepository.saveAndFlush(any()) }
            verifyNoSideEffects()
        }

        @Test
        fun `should throw AlreadyFriendsException when the users are already friends`() {
            stubUsers()
            stubExisting(friendRequest(from = receiver, to = sender, status = FriendRequestStatus.ACCEPTED))

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(AlreadyFriendsException::class.java)

            verify(exactly = 0) { friendRequestRepository.saveAndFlush(any()) }
            verifyNoSideEffects()
        }

        @Test
        fun `should throw FriendRequestAlreadyExistsException when the request was already sent`() {
            stubUsers()
            stubExisting(friendRequest())

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(FriendRequestAlreadyExistsException::class.java)

            verify(exactly = 0) { friendRequestRepository.saveAndFlush(any()) }
            verifyNoSideEffects()
        }

        @Test
        fun `should map a concurrent duplicate request to FriendRequestAlreadyExistsException`() {
            stubUsers()
            stubExisting()
            every { friendRequestRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate key value violates unique constraint")

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(FriendRequestAlreadyExistsException::class.java)

            verifyNoSideEffects()
        }

        @Test
        fun `should rethrow unexpected errors`() {
            every { userRepository.findById(senderId) } throws RuntimeException("DB Connection error")

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB Connection error")

            verifyNoSideEffects()
        }
    }

    @Nested
    @DisplayName("acceptRequest")
    inner class AcceptRequest {

        @Test
        fun `should accept the request, notify the sender and evaluate achievements`() {
            val pending = friendRequest()
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(pending)

            val result = friendService.acceptRequest(receiverId, requestId)

            assertThat(result.id).isEqualTo(requestId)
            assertThat(pending.status).isEqualTo(FriendRequestStatus.ACCEPTED)
            verify(exactly = 1) {
                pushNotificationService.sendToUser(
                    userId = senderId,
                    type = NotificationType.FRIEND_ACCEPTED,
                    title = "Richiesta accettata",
                    body = "receiverUser ha accettato la tua richiesta di amicizia",
                    data = mapOf("requestId" to requestId.toString()),
                )
            }
            verify(exactly = 1) { userAchievementService.evaluateAfterCommit(setOf(senderId, receiverId)) }
        }

        @Test
        fun `should throw FriendRequestNotFoundException when the request does not exist`() {
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.empty()

            assertThatThrownBy { friendService.acceptRequest(receiverId, requestId) }
                .isInstanceOf(FriendRequestNotFoundException::class.java)

            verifyNoSideEffects()
        }

        @Test
        fun `should treat an already accepted request as not found`() {
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(friendRequest(status = FriendRequestStatus.ACCEPTED))

            assertThatThrownBy { friendService.acceptRequest(receiverId, requestId) }
                .isInstanceOf(FriendRequestNotFoundException::class.java)

            verifyNoSideEffects()
        }

        @Test
        fun `should not let the sender accept their own request`() {
            val pending = friendRequest()
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(pending)

            assertThatThrownBy { friendService.acceptRequest(senderId, requestId) }
                .isInstanceOf(NotFriendRequestReceiverException::class.java)

            assertThat(pending.status).isEqualTo(FriendRequestStatus.PENDING)
            verifyNoSideEffects()
        }

        @Test
        fun `should not let a third user accept the request`() {
            val pending = friendRequest()
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(pending)

            assertThatThrownBy { friendService.acceptRequest(UUID.randomUUID(), requestId) }
                .isInstanceOf(NotFriendRequestReceiverException::class.java)

            assertThat(pending.status).isEqualTo(FriendRequestStatus.PENDING)
            verifyNoSideEffects()
        }
    }

    @Nested
    @DisplayName("rejectRequest")
    inner class RejectRequest {

        @Test
        fun `should delete the request without notifying anyone`() {
            val pending = friendRequest()
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(pending)

            friendService.rejectRequest(receiverId, requestId)

            verify(exactly = 1) { friendRequestRepository.delete(pending) }
            verifyNoSideEffects()
        }

        @Test
        fun `should not let the sender reject their own request`() {
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(friendRequest())

            assertThatThrownBy { friendService.rejectRequest(senderId, requestId) }
                .isInstanceOf(NotFriendRequestReceiverException::class.java)

            verify(exactly = 0) { friendRequestRepository.delete(any()) }
        }

        @Test
        fun `should not reject a friendship through the request endpoint`() {
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(friendRequest(status = FriendRequestStatus.ACCEPTED))

            assertThatThrownBy { friendService.rejectRequest(receiverId, requestId) }
                .isInstanceOf(FriendRequestNotFoundException::class.java)

            verify(exactly = 0) { friendRequestRepository.delete(any()) }
        }
    }

    @Nested
    @DisplayName("cancelRequest")
    inner class CancelRequest {

        @Test
        fun `should delete the request sent by the user`() {
            val pending = friendRequest()
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(pending)

            friendService.cancelRequest(senderId, requestId)

            verify(exactly = 1) { friendRequestRepository.delete(pending) }
            verifyNoSideEffects()
        }

        @Test
        fun `should not let the receiver cancel the request`() {
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(friendRequest())

            assertThatThrownBy { friendService.cancelRequest(receiverId, requestId) }
                .isInstanceOf(NotFriendRequestSenderException::class.java)

            verify(exactly = 0) { friendRequestRepository.delete(any()) }
        }

        @Test
        fun `should not cancel a friendship through the request endpoint`() {
            every { friendRequestRepository.findWithUsersById(requestId) } returns Optional.of(friendRequest(status = FriendRequestStatus.ACCEPTED))

            assertThatThrownBy { friendService.cancelRequest(senderId, requestId) }
                .isInstanceOf(FriendRequestNotFoundException::class.java)

            verify(exactly = 0) { friendRequestRepository.delete(any()) }
        }
    }

    @Nested
    @DisplayName("removeFriend")
    inner class RemoveFriend {

        @Test
        fun `should delete the friendship`() {
            val friendship = friendRequest(status = FriendRequestStatus.ACCEPTED)
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } returns Optional.of(friendship)

            friendService.removeFriend(senderId, receiverId)

            verify(exactly = 1) { friendRequestRepository.delete(friendship) }
            verifyNoSideEffects()
        }

        @Test
        fun `should throw FriendshipNotFoundException when they are not friends`() {
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } returns Optional.empty()

            assertThatThrownBy { friendService.removeFriend(senderId, receiverId) }
                .isInstanceOf(FriendshipNotFoundException::class.java)

            verify(exactly = 0) { friendRequestRepository.delete(any()) }
        }
    }

    @Nested
    @DisplayName("lists")
    inner class ListTests {

        @Test
        fun `should return the other user of each friendship`() {
            val third = user(username = "user3")
            every { friendRequestRepository.findAllFriendshipsForUser(senderId) } returns listOf(
                friendRequest(from = sender, to = receiver, status = FriendRequestStatus.ACCEPTED, id = UUID.randomUUID()),
                friendRequest(from = third, to = sender, status = FriendRequestStatus.ACCEPTED, id = UUID.randomUUID()),
            )

            val friends = friendService.listFriends(senderId)

            assertThat(friends.map { it.id }).containsExactly(receiverId, third.id)
            assertThat(friends.map { it.username }).containsExactly("receiverUser", "user3")
        }

        @Test
        fun `should return an empty list when the user has no friends`() {
            every { friendRequestRepository.findAllFriendshipsForUser(senderId) } returns emptyList()

            assertThat(friendService.listFriends(senderId)).isEmpty()
        }

        @Test
        fun `should list the pending requests received`() {
            every { friendRequestRepository.findByReceiverIdAndStatus(receiverId, FriendRequestStatus.PENDING) } returns listOf(friendRequest())

            val results = friendService.listPendingReceived(receiverId)

            assertThat(results.map { it.id }).containsExactly(requestId)
            assertThat(results[0].sender.id).isEqualTo(senderId)
        }

        @Test
        fun `should list the pending requests sent`() {
            every { friendRequestRepository.findBySenderIdAndStatus(senderId, FriendRequestStatus.PENDING) } returns listOf(friendRequest())

            val results = friendService.listPendingSent(senderId)

            assertThat(results.map { it.id }).containsExactly(requestId)
            assertThat(results[0].receiver.id).isEqualTo(receiverId)
        }

        @Test
        fun `should rethrow query errors`() {
            every { friendRequestRepository.findAllFriendshipsForUser(senderId) } throws RuntimeException("Query error")

            assertThatThrownBy { friendService.listFriends(senderId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Query error")
        }
    }

    @Nested
    @DisplayName("getFriendshipStatus")
    inner class GetFriendshipStatus {

        private fun stubBetween(vararg requests: FriendRequest) {
            every { friendRequestRepository.findAllBetween(senderId, receiverId) } returns requests.toList()
        }

        @Test
        fun `should return SELF without querying when checking yourself`() {
            assertThat(friendService.getFriendshipStatus(senderId, senderId)).isEqualTo(FriendshipStatus.SELF)

            verify(exactly = 0) { friendRequestRepository.findAllBetween(any(), any()) }
        }

        @Test
        fun `should return FRIENDS whoever sent the original request`() {
            stubBetween(friendRequest(from = receiver, to = sender, status = FriendRequestStatus.ACCEPTED))

            assertThat(friendService.getFriendshipStatus(senderId, receiverId)).isEqualTo(FriendshipStatus.FRIENDS)
        }

        @Test
        fun `should prefer FRIENDS over a leftover pending request`() {
            stubBetween(
                friendRequest(status = FriendRequestStatus.ACCEPTED),
                friendRequest(from = receiver, to = sender, id = UUID.randomUUID()),
            )

            assertThat(friendService.getFriendshipStatus(senderId, receiverId)).isEqualTo(FriendshipStatus.FRIENDS)
        }

        @Test
        fun `should return REQUEST_SENT when the current user sent a pending request`() {
            stubBetween(friendRequest())

            assertThat(friendService.getFriendshipStatus(senderId, receiverId)).isEqualTo(FriendshipStatus.REQUEST_SENT)
        }

        @Test
        fun `should return REQUEST_RECEIVED when the other user sent a pending request`() {
            stubBetween(friendRequest(from = receiver, to = sender))

            assertThat(friendService.getFriendshipStatus(senderId, receiverId)).isEqualTo(FriendshipStatus.REQUEST_RECEIVED)
        }

        @Test
        fun `should return NONE when there is no relationship`() {
            stubBetween()

            assertThat(friendService.getFriendshipStatus(senderId, receiverId)).isEqualTo(FriendshipStatus.NONE)
        }
    }
}