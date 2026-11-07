package com.backend.integration.controllers

import com.backend.models.entities.FriendRequest
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FriendRequestStatus
import com.backend.models.enums.NotificationType
import com.backend.models.enums.UserRole
import com.backend.repositories.FriendRequestRepository
import com.backend.repositories.UserAchievementRepository
import com.backend.repositories.UserRepository
import com.backend.services.JwtService
import com.backend.services.PushNotificationService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
class FriendControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var friendRequestRepository: FriendRequestRepository

    @Autowired
    private lateinit var userAchievementRepository: UserAchievementRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var pushNotificationService: PushNotificationService

    companion object {
        private const val BASE_URL = "/api/v1/friends"
    }

    @AfterEach
    fun cleanUp() {
        friendRequestRepository.deleteAll()
        userAchievementRepository.deleteAll()
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

    private fun persistFriendRequest(sender: User, receiver: User, status: FriendRequestStatus = FriendRequestStatus.PENDING): FriendRequest =
        friendRequestRepository.saveAndFlush(FriendRequest(sender = sender, receiver = receiver, status = status))

    private fun perform(request: AbstractMockHttpServletRequestBuilder<*>, user: User?): ResultActions =
        mockMvc.perform(request.apply { user?.let { header(HttpHeaders.AUTHORIZATION, authHeader(it)) } })

    private fun sendRequest(from: User?, receiverId: Any?) =
        perform(post("$BASE_URL/requests").contentType(MediaType.APPLICATION_JSON).content("""{"receiverId":${receiverId?.let { "\"$it\"" } ?: "null"}}"""), from)

    private fun verifyNotified(user: User, type: NotificationType) =
        verify(pushNotificationService).sendToUser(eq(user.id!!), eq(type), anyString(), anyString(), anyMap())

    private fun verifyNobodyNotified() =
        verify(pushNotificationService, never()).sendToUser(any(), any(), anyString(), anyString(), anyMap())

    @Nested
    @DisplayName("POST /api/v1/friends/requests")
    inner class SendRequestTests {

        @Test
        fun `should create a pending friend request and return 201`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")

            sendRequest(sender, receiver.id)
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sender.username").value("sender"))
                .andExpect(jsonPath("$.receiver.username").value("receiver"))

            assertThat(friendRequestRepository.findAllBetween(sender.id!!, receiver.id!!)).singleElement().matches { it.sender.id == sender.id && it.status == FriendRequestStatus.PENDING }
        }

        @Test
        fun `should notify the receiver`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")

            sendRequest(sender, receiver.id).andExpect(status().isCreated)

            verifyNotified(receiver, NotificationType.FRIEND_REQUEST)
        }

        @Test
        fun `should auto-accept when a reverse pending request already exists`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            val reverse = persistFriendRequest(sender = userB, receiver = userA)

            sendRequest(userA, userB.id)
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.id").value(reverse.id.toString()))

            assertThat(friendRequestRepository.findAll()).hasSize(1)
            assertThat(friendRequestRepository.findFriendshipBetween(userA.id!!, userB.id!!)).isPresent
            verifyNotified(userB, NotificationType.FRIEND_ACCEPTED)
        }

        @Test
        fun `should return 400 when sending a request to yourself`() {
            val user = persistUser()

            sendRequest(user, user.id)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Cannot send a friend request to yourself"))

            assertThat(friendRequestRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 400 when receiverId is missing`() {
            sendRequest(persistUser(), null).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when receiverId is not a UUID`() {
            sendRequest(persistUser(), "not-a-uuid").andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when receiver does not exist`() {
            sendRequest(persistUser(), UUID.randomUUID()).andExpect(status().isNotFound)

            verifyNobodyNotified()
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus::class, names = ["ACTIVE"], mode = EnumSource.Mode.EXCLUDE)
        fun `should return 404 when the receiver account is not active`(accountStatus: AccountStatus) {
            val receiver = persistUser(username = "receiver", accountStatus = accountStatus)

            sendRequest(persistUser(username = "sender"), receiver.id).andExpect(status().isNotFound)

            assertThat(friendRequestRepository.findAll()).isEmpty()
        }

        @Test
        fun `should return 409 when already friends`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            persistFriendRequest(sender = userA, receiver = userB, status = FriendRequestStatus.ACCEPTED)

            sendRequest(userA, userB.id).andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when already friends through a request sent by the other user`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            persistFriendRequest(sender = userB, receiver = userA, status = FriendRequestStatus.ACCEPTED)

            sendRequest(userA, userB.id).andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when a pending request already exists in the same direction`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            persistFriendRequest(sender = sender, receiver = receiver)

            sendRequest(sender, receiver.id).andExpect(status().isConflict)

            assertThat(friendRequestRepository.findAll()).hasSize(1)
            verifyNobodyNotified()
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            sendRequest(null, persistUser().id).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/friends/requests/{requestId}/accept")
    inner class AcceptRequestTests {

        private fun accept(requestId: Any?, user: User) = perform(post("$BASE_URL/requests/$requestId/accept"), user)

        @Test
        fun `should accept the request and notify the sender`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            accept(request.id, receiver)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("ACCEPTED"))

            assertThat(friendRequestRepository.findById(request.id!!).orElseThrow().status).isEqualTo(FriendRequestStatus.ACCEPTED)
            verifyNotified(sender, NotificationType.FRIEND_ACCEPTED)
        }

        @Test
        fun `should make both users see each other as friends`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            accept(request.id, receiver).andExpect(status().isOk)

            perform(get(BASE_URL), sender).andExpect(jsonPath("$[*].username").value(containsInAnyOrder("receiver")))
            perform(get(BASE_URL), receiver).andExpect(jsonPath("$[*].username").value(containsInAnyOrder("sender")))
        }

        @Test
        fun `should return 403 when user is not the receiver`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            accept(request.id, sender)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("Only the receiver can respond to this friend request"))

            assertThat(friendRequestRepository.findById(request.id!!).orElseThrow().status).isEqualTo(FriendRequestStatus.PENDING)
            verifyNobodyNotified()
        }

        @Test
        fun `should return 403 for a stranger`() {
            val request = persistFriendRequest(sender = persistUser(username = "sender"), receiver = persistUser(username = "receiver"))

            accept(request.id, persistUser(username = "stranger")).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when the request was already accepted`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver, status = FriendRequestStatus.ACCEPTED)

            accept(request.id, receiver).andExpect(status().isNotFound)

            verifyNobodyNotified()
        }

        @Test
        fun `should return 404 when request does not exist`() {
            accept(UUID.randomUUID(), persistUser()).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when the request id is not a UUID`() {
            accept("not-a-uuid", persistUser()).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/friends/requests/{requestId}/reject")
    inner class RejectRequestTests {

        private fun reject(requestId: Any?, user: User) = perform(post("$BASE_URL/requests/$requestId/reject"), user)

        @Test
        fun `should delete the request and return 204 when user is the receiver`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            reject(request.id, receiver).andExpect(status().isNoContent)

            assertThat(friendRequestRepository.findById(request.id!!)).isEmpty()
            verifyNobodyNotified()
        }

        @Test
        fun `should let the sender send a new request after a rejection`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            reject(request.id, receiver).andExpect(status().isNoContent)

            sendRequest(sender, receiver.id).andExpect(status().isCreated)
        }

        @Test
        fun `should return 403 when user is not the receiver`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            reject(request.id, sender).andExpect(status().isForbidden)

            assertThat(friendRequestRepository.findById(request.id!!)).isPresent
        }

        @Test
        fun `should not remove an existing friendship`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val friendship = persistFriendRequest(sender = sender, receiver = receiver, status = FriendRequestStatus.ACCEPTED)

            reject(friendship.id, receiver).andExpect(status().isNotFound)

            assertThat(friendRequestRepository.findById(friendship.id!!)).isPresent
        }

        @Test
        fun `should return 404 when request does not exist`() {
            reject(UUID.randomUUID(), persistUser()).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/friends/requests/{requestId}")
    inner class CancelRequestTests {

        private fun cancel(requestId: Any?, user: User) = perform(delete("$BASE_URL/requests/$requestId"), user)

        @Test
        fun `should delete the request and return 204 when user is the sender`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            cancel(request.id, sender).andExpect(status().isNoContent)

            assertThat(friendRequestRepository.findById(request.id!!)).isEmpty()
            perform(get("$BASE_URL/requests/received"), receiver).andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 403 when user is not the sender`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            cancel(request.id, receiver)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("Only the sender can cancel this friend request"))

            assertThat(friendRequestRepository.findById(request.id!!)).isPresent
        }

        @Test
        fun `should not remove an existing friendship`() {
            val sender = persistUser(username = "sender")
            val friendship = persistFriendRequest(sender = sender, receiver = persistUser(username = "receiver"), status = FriendRequestStatus.ACCEPTED)

            cancel(friendship.id, sender).andExpect(status().isNotFound)

            assertThat(friendRequestRepository.findById(friendship.id!!)).isPresent
        }

        @Test
        fun `should return 404 when request does not exist`() {
            cancel(UUID.randomUUID(), persistUser()).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/friends/{friendUserId}")
    inner class RemoveFriendTests {

        private fun remove(friendId: Any?, user: User) = perform(delete("$BASE_URL/$friendId"), user)

        @Test
        fun `should remove the friendship and return 204`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            val friendship = persistFriendRequest(sender = userA, receiver = userB, status = FriendRequestStatus.ACCEPTED)

            remove(userB.id, userA).andExpect(status().isNoContent)

            assertThat(friendRequestRepository.findById(friendship.id!!)).isEmpty()
        }

        @Test
        fun `should remove the friendship regardless of which side initiated it`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            val friendship = persistFriendRequest(sender = userB, receiver = userA, status = FriendRequestStatus.ACCEPTED)

            remove(userB.id, userA).andExpect(status().isNoContent)

            assertThat(friendRequestRepository.findById(friendship.id!!)).isEmpty()
        }

        @Test
        fun `should remove only that friendship`() {
            val user = persistUser(username = "user")
            val exFriend = persistUser(username = "ex-friend")
            val friend = persistUser(username = "friend")
            persistFriendRequest(sender = user, receiver = exFriend, status = FriendRequestStatus.ACCEPTED)
            persistFriendRequest(sender = user, receiver = friend, status = FriendRequestStatus.ACCEPTED)

            remove(exFriend.id, user).andExpect(status().isNoContent)

            perform(get(BASE_URL), user).andExpect(jsonPath("$[*].username").value(containsInAnyOrder("friend")))
        }

        @Test
        fun `should return 404 when no friendship exists`() {
            remove(persistUser(username = "userB").id, persistUser(username = "userA")).andExpect(status().isNotFound)
        }

        @Test
        fun `should not remove a pending request that is not yet a friendship`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            val pending = persistFriendRequest(sender = userA, receiver = userB)

            remove(userB.id, userA).andExpect(status().isNotFound)

            assertThat(friendRequestRepository.findById(pending.id!!)).isPresent
        }

        @Test
        fun `should return 400 when the friend id is not a UUID`() {
            remove("not-a-uuid", persistUser()).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends")
    inner class ListFriendsTests {

        @Test
        fun `should list accepted friends regardless of who sent the original request`() {
            val user = persistUser(username = "user")
            persistFriendRequest(sender = user, receiver = persistUser(username = "friend-receiver-side"), status = FriendRequestStatus.ACCEPTED)
            persistFriendRequest(sender = persistUser(username = "friend-sender-side"), receiver = user, status = FriendRequestStatus.ACCEPTED)

            perform(get(BASE_URL), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].username").value(containsInAnyOrder("friend-receiver-side", "friend-sender-side")))
        }

        @Test
        fun `should not include friendships between other users`() {
            val user = persistUser(username = "user")
            persistFriendRequest(sender = persistUser(username = "a"), receiver = persistUser(username = "b"), status = FriendRequestStatus.ACCEPTED)

            perform(get(BASE_URL), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not list pending requests as friends`() {
            val user = persistUser(username = "user")
            persistFriendRequest(sender = user, receiver = persistUser(username = "other"))

            perform(get(BASE_URL), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return an empty list when user has no friends`() {
            perform(get(BASE_URL), persistUser())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return 403 when no auth header is provided`() {
            perform(get(BASE_URL), null).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/requests/received")
    inner class ListPendingReceivedTests {

        @Test
        fun `should list pending requests received by the current user`() {
            val receiver = persistUser(username = "receiver")
            persistFriendRequest(sender = persistUser(username = "sender"), receiver = receiver)
            persistFriendRequest(sender = persistUser(username = "sender2"), receiver = receiver)

            perform(get("$BASE_URL/requests/received"), receiver)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].sender.username").value(containsInAnyOrder("sender", "sender2")))
        }

        @Test
        fun `should not include accepted requests`() {
            val receiver = persistUser(username = "receiver")
            persistFriendRequest(sender = persistUser(username = "sender"), receiver = receiver, status = FriendRequestStatus.ACCEPTED)

            perform(get("$BASE_URL/requests/received"), receiver)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not include requests sent by the user`() {
            val user = persistUser(username = "user")
            persistFriendRequest(sender = user, receiver = persistUser(username = "other"))

            perform(get("$BASE_URL/requests/received"), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/requests/sent")
    inner class ListPendingSentTests {

        @Test
        fun `should list pending requests sent by the current user`() {
            val sender = persistUser(username = "sender")
            persistFriendRequest(sender = sender, receiver = persistUser(username = "receiver"))

            perform(get("$BASE_URL/requests/sent"), sender)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].receiver.username").value(containsInAnyOrder("receiver")))
        }

        @Test
        fun `should not include accepted requests`() {
            val sender = persistUser(username = "sender")
            persistFriendRequest(sender = sender, receiver = persistUser(username = "receiver"), status = FriendRequestStatus.ACCEPTED)

            perform(get("$BASE_URL/requests/sent"), sender)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not include requests received by the user`() {
            val user = persistUser(username = "user")
            persistFriendRequest(sender = persistUser(username = "other"), receiver = user)

            perform(get("$BASE_URL/requests/sent"), user)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }
}