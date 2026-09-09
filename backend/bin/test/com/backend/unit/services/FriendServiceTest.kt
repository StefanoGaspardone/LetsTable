package com.backend.unit.services

import com.backend.exceptions.*
import com.backend.models.dtos.SendFriendRequestRequest
import com.backend.models.entities.FriendRequest
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FriendRequestStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.FriendRequestRepository
import com.backend.repositories.UserRepository
import com.backend.services.FriendService
import com.backend.services.PushNotificationService
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
import java.util.*

@ExtendWith(MockKExtension::class)
class FriendServiceTest {

    @MockK
    private lateinit var friendRequestRepository: FriendRequestRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var pushNotificationService: PushNotificationService

    @InjectMockKs
    private lateinit var friendService: FriendService

    private val senderId: UUID = UUID.randomUUID()
    private val receiverId: UUID = UUID.randomUUID()
    private val requestId: UUID = UUID.randomUUID()

    private val mockSender = User(
        id = senderId,
        username = "senderUser",
        email = "sender@test.com",
        passwordHash = "hash",
        role = UserRole.USER,
        accountStatus = AccountStatus.ACTIVE
    )

    private val mockReceiver = User(
        id = receiverId,
        username = "receiverUser",
        email = "receiver@test.com",
        passwordHash = "hash",
        role = UserRole.USER,
        accountStatus = AccountStatus.ACTIVE
    )

    private val mockFriendRequest = FriendRequest(
        id = requestId,
        sender = mockSender,
        receiver = mockReceiver,
        status = FriendRequestStatus.PENDING
    )

    @Nested
    @DisplayName("sendRequest")
    inner class SendRequest {

        @Test
        fun `should successfully send friend request when no prior request exists`() {
            val request = SendFriendRequestRequest(receiverId = receiverId)
            val slotRequest = slot<FriendRequest>()

            every { userRepository.findById(senderId) } returns Optional.of(mockSender)
            every { userRepository.findById(receiverId) } returns Optional.of(mockReceiver)
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } returns Optional.empty()
            every { friendRequestRepository.findBySenderIdAndReceiverId(senderId, receiverId) } returns Optional.empty()
            every { friendRequestRepository.findBySenderIdAndReceiverId(receiverId, senderId) } returns Optional.empty()
            every { friendRequestRepository.save(capture(slotRequest)) } returns mockFriendRequest
            every { pushNotificationService.sendToUser(any(), any(), any(), any()) } just Runs

            val result = friendService.sendRequest(senderId, request)

            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(requestId)
            assertThat(slotRequest.captured.sender).isEqualTo(mockSender)
            assertThat(slotRequest.captured.receiver).isEqualTo(mockReceiver)
            assertThat(slotRequest.captured.status).isEqualTo(FriendRequestStatus.PENDING)

            verify(exactly = 1) {
                pushNotificationService.sendToUser(
                    userId = receiverId,
                    title = "Nuova richiesta di amicizia",
                    body = "senderUser ti ha inviato una richiesta di amicizia",
                    data = mapOf("type" to "FRIEND_REQUEST", "requestId" to requestId.toString())
                )
            }
        }

        @Test
        fun `should auto-accept friendship when a reverse request exists`() {
            val request = SendFriendRequestRequest(receiverId = receiverId)
            val reverseRequest = FriendRequest(
                id = requestId,
                sender = mockReceiver,
                receiver = mockSender,
                status = FriendRequestStatus.PENDING
            )
            val slotRequest = slot<FriendRequest>()

            every { userRepository.findById(senderId) } returns Optional.of(mockSender)
            every { userRepository.findById(receiverId) } returns Optional.of(mockReceiver)
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } returns Optional.empty()
            every { friendRequestRepository.findBySenderIdAndReceiverId(senderId, receiverId) } returns Optional.empty()
            every { friendRequestRepository.findBySenderIdAndReceiverId(receiverId, senderId) } returns Optional.of(reverseRequest)
            every { friendRequestRepository.save(capture(slotRequest)) } answers { slotRequest.captured }
            every { pushNotificationService.sendToUser(any(), any(), any(), any()) } just Runs

            val result = friendService.sendRequest(senderId, request)

            assertThat(result).isNotNull
            assertThat(slotRequest.captured.status).isEqualTo(FriendRequestStatus.ACCEPTED)

            verify(exactly = 1) {
                pushNotificationService.sendToUser(
                    userId = receiverId,
                    title = "Richiesta accettata",
                    body = "senderUser ha accettato la tua richiesta di amicizia",
                    data = mapOf("type" to "FRIEND_ACCEPTED", "requestId" to requestId.toString())
                )
            }
        }

        @Test
        fun `should throw CannotFriendSelfException when sender and receiver are the same`() {
            val request = SendFriendRequestRequest(receiverId = senderId)

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(CannotFriendSelfException::class.java)

            verify(exactly = 0) { userRepository.findById(any()) }
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when sender does not exist`() {
            val request = SendFriendRequestRequest(receiverId = receiverId)
            every { userRepository.findById(senderId) } returns Optional.empty()

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)
        }

        @Test
        fun `should throw UserNotFoundByIdentifierException when receiver does not exist`() {
            val request = SendFriendRequestRequest(receiverId = receiverId)
            every { userRepository.findById(senderId) } returns Optional.of(mockSender)
            every { userRepository.findById(receiverId) } returns Optional.empty()

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(UserNotFoundByIdentifierException::class.java)
        }

        @Test
        fun `should throw AlreadyFriendsException when friendship already exists`() {
            val request = SendFriendRequestRequest(receiverId = receiverId)
            every { userRepository.findById(senderId) } returns Optional.of(mockSender)
            every { userRepository.findById(receiverId) } returns Optional.of(mockReceiver)
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } returns Optional.of(mockFriendRequest)

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(AlreadyFriendsException::class.java)
        }

        @Test
        fun `should throw FriendRequestAlreadyExistsException when identical pending request exists`() {
            val request = SendFriendRequestRequest(receiverId = receiverId)
            every { userRepository.findById(senderId) } returns Optional.of(mockSender)
            every { userRepository.findById(receiverId) } returns Optional.of(mockReceiver)
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } returns Optional.empty()
            every { friendRequestRepository.findBySenderIdAndReceiverId(senderId, receiverId) } returns Optional.of(mockFriendRequest)

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(FriendRequestAlreadyExistsException::class.java)
        }

        @Test
        fun `should rethrow unexpected exception during sendRequest`() {
            val request = SendFriendRequestRequest(receiverId = receiverId)
            every { userRepository.findById(senderId) } throws RuntimeException("DB Connection error")

            assertThatThrownBy { friendService.sendRequest(senderId, request) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("DB Connection error")
        }
    }

    @Nested
    @DisplayName("acceptRequest")
    inner class AcceptRequest {

        @Test
        fun `should successfully accept pending request`() {
            val slotRequest = slot<FriendRequest>()
            every { friendRequestRepository.findById(requestId) } returns Optional.of(mockFriendRequest)
            every { friendRequestRepository.save(capture(slotRequest)) } answers { slotRequest.captured }
            every { pushNotificationService.sendToUser(any(), any(), any(), any()) } just Runs

            val result = friendService.acceptRequest(receiverId, requestId)

            assertThat(result).isNotNull
            assertThat(slotRequest.captured.status).isEqualTo(FriendRequestStatus.ACCEPTED)

            verify(exactly = 1) {
                pushNotificationService.sendToUser(
                    userId = senderId,
                    title = "Richiesta accettata",
                    body = "receiverUser ha accettato la tua richiesta di amicizia",
                    data = mapOf("type" to "FRIEND_ACCEPTED", "requestId" to requestId.toString())
                )
            }
        }

        @Test
        fun `should throw FriendRequestNotFoundException when request does not exist`() {
            every { friendRequestRepository.findById(requestId) } returns Optional.empty()

            assertThatThrownBy { friendService.acceptRequest(receiverId, requestId) }
                .isInstanceOf(FriendRequestNotFoundException::class.java)
        }

        @Test
        fun `should throw NotFriendRequestReceiverException when current user is not receiver`() {
            val otherUserId = UUID.randomUUID()
            every { friendRequestRepository.findById(requestId) } returns Optional.of(mockFriendRequest)

            assertThatThrownBy { friendService.acceptRequest(otherUserId, requestId) }
                .isInstanceOf(NotFriendRequestReceiverException::class.java)
        }

        @Test
        fun `should rethrow generic exception in acceptRequest`() {
            every { friendRequestRepository.findById(requestId) } throws RuntimeException("Unexpected DB Fail")

            assertThatThrownBy { friendService.acceptRequest(receiverId, requestId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Unexpected DB Fail")
        }
    }

    @Nested
    @DisplayName("rejectRequest")
    inner class RejectRequest {

        @Test
        fun `should successfully reject and delete friend request`() {
            every { friendRequestRepository.findById(requestId) } returns Optional.of(mockFriendRequest)
            every { friendRequestRepository.delete(mockFriendRequest) } just Runs

            friendService.rejectRequest(receiverId, requestId)

            verify(exactly = 1) { friendRequestRepository.delete(mockFriendRequest) }
        }

        @Test
        fun `should throw FriendRequestNotFoundException when request to reject is not found`() {
            every { friendRequestRepository.findById(requestId) } returns Optional.empty()

            assertThatThrownBy { friendService.rejectRequest(receiverId, requestId) }
                .isInstanceOf(FriendRequestNotFoundException::class.java)
        }

        @Test
        fun `should throw NotFriendRequestReceiverException when user rejecting is not receiver`() {
            val wrongUserId = UUID.randomUUID()
            every { friendRequestRepository.findById(requestId) } returns Optional.of(mockFriendRequest)

            assertThatThrownBy { friendService.rejectRequest(wrongUserId, requestId) }
                .isInstanceOf(NotFriendRequestReceiverException::class.java)
        }

        @Test
        fun `should rethrow generic exception in rejectRequest`() {
            every { friendRequestRepository.findById(requestId) } throws RuntimeException("Delete failed")

            assertThatThrownBy { friendService.rejectRequest(receiverId, requestId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Delete failed")
        }
    }

    @Nested
    @DisplayName("cancelRequest")
    inner class CancelRequest {

        @Test
        fun `should successfully cancel sent friend request`() {
            every { friendRequestRepository.findById(requestId) } returns Optional.of(mockFriendRequest)
            every { friendRequestRepository.delete(mockFriendRequest) } just Runs

            friendService.cancelRequest(senderId, requestId)

            verify(exactly = 1) { friendRequestRepository.delete(mockFriendRequest) }
        }

        @Test
        fun `should throw FriendRequestNotFoundException when request to cancel is not found`() {
            every { friendRequestRepository.findById(requestId) } returns Optional.empty()

            assertThatThrownBy { friendService.cancelRequest(senderId, requestId) }
                .isInstanceOf(FriendRequestNotFoundException::class.java)
        }

        @Test
        fun `should throw NotFriendRequestSenderException when user cancelling is not sender`() {
            val wrongUserId = UUID.randomUUID()
            every { friendRequestRepository.findById(requestId) } returns Optional.of(mockFriendRequest)

            assertThatThrownBy { friendService.cancelRequest(wrongUserId, requestId) }
                .isInstanceOf(NotFriendRequestSenderException::class.java)
        }

        @Test
        fun `should rethrow generic exception in cancelRequest`() {
            every { friendRequestRepository.findById(requestId) } throws RuntimeException("Database error")

            assertThatThrownBy { friendService.cancelRequest(senderId, requestId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Database error")
        }
    }

    @Nested
    @DisplayName("removeFriend")
    inner class RemoveFriend {

        @Test
        fun `should successfully remove existing friendship`() {
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } returns Optional.of(mockFriendRequest)
            every { friendRequestRepository.delete(mockFriendRequest) } just Runs

            friendService.removeFriend(senderId, receiverId)

            verify(exactly = 1) { friendRequestRepository.delete(mockFriendRequest) }
        }

        @Test
        fun `should throw FriendshipNotFoundException when friendship does not exist`() {
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } returns Optional.empty()

            assertThatThrownBy { friendService.removeFriend(senderId, receiverId) }
                .isInstanceOf(FriendshipNotFoundException::class.java)
        }

        @Test
        fun `should rethrow generic exception in removeFriend`() {
            every { friendRequestRepository.findFriendshipBetween(senderId, receiverId) } throws RuntimeException("Fatal DB Error")

            assertThatThrownBy { friendService.removeFriend(senderId, receiverId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Fatal DB Error")
        }
    }

    @Nested
    @DisplayName("listFriends")
    inner class ListFriends {

        @Test
        fun `should correctly map and return friend user DTOs when current user is sender or receiver`() {
            val user3Id = UUID.randomUUID()
            val mockUser3 = User(
                id = user3Id,
                username = "user3",
                email = "user3@test.com",
                passwordHash = "hash",
                role = UserRole.USER,
                accountStatus = AccountStatus.ACTIVE
            )

            // Current user (senderId) is sender in request 1, and receiver in request 2
            val friendship1 = FriendRequest(id = UUID.randomUUID(), sender = mockSender, receiver = mockReceiver, status = FriendRequestStatus.ACCEPTED)
            val friendship2 = FriendRequest(id = UUID.randomUUID(), sender = mockUser3, receiver = mockSender, status = FriendRequestStatus.ACCEPTED)

            every { friendRequestRepository.findAllFriendshipsForUser(senderId) } returns listOf(friendship1, friendship2)

            val friends = friendService.listFriends(senderId)

            assertThat(friends).hasSize(2)
            assertThat(friends[0].id).isEqualTo(receiverId)
            assertThat(friends[0].username).isEqualTo("receiverUser")
            assertThat(friends[1].id).isEqualTo(user3Id)
            assertThat(friends[1].username).isEqualTo("user3")
        }

        @Test
        fun `should rethrow generic exception in listFriends`() {
            every { friendRequestRepository.findAllFriendshipsForUser(senderId) } throws RuntimeException("Query error")

            assertThatThrownBy { friendService.listFriends(senderId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Query error")
        }
    }

    @Nested
    @DisplayName("listPendingReceived")
    inner class ListPendingReceived {

        @Test
        fun `should return mapped DTOs of pending received requests`() {
            every {
                friendRequestRepository.findByReceiverIdAndStatus(receiverId, FriendRequestStatus.PENDING)
            } returns listOf(mockFriendRequest)

            val results = friendService.listPendingReceived(receiverId)

            assertThat(results).hasSize(1)
            assertThat(results[0].id).isEqualTo(requestId)
            assertThat(results[0].sender.id).isEqualTo(senderId)
        }

        @Test
        fun `should rethrow generic exception in listPendingReceived`() {
            every {
                friendRequestRepository.findByReceiverIdAndStatus(receiverId, FriendRequestStatus.PENDING)
            } throws RuntimeException("Query error")

            assertThatThrownBy { friendService.listPendingReceived(receiverId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Query error")
        }
    }

    @Nested
    @DisplayName("listPendingSent")
    inner class ListPendingSent {

        @Test
        fun `should return mapped DTOs of pending sent requests`() {
            every {
                friendRequestRepository.findBySenderIdAndStatus(senderId, FriendRequestStatus.PENDING)
            } returns listOf(mockFriendRequest)

            val results = friendService.listPendingSent(senderId)

            assertThat(results).hasSize(1)
            assertThat(results[0].id).isEqualTo(requestId)
            assertThat(results[0].receiver.id).isEqualTo(receiverId)
        }

        @Test
        fun `should rethrow generic exception in listPendingSent`() {
            every {
                friendRequestRepository.findBySenderIdAndStatus(senderId, FriendRequestStatus.PENDING)
            } throws RuntimeException("Query error")

            assertThatThrownBy { friendService.listPendingSent(senderId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Query error")
        }
    }
}