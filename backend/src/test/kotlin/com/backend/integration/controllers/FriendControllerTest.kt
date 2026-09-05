package com.backend.integration.controllers

import com.backend.models.entities.FriendRequest
import com.backend.models.entities.User
import com.backend.models.enums.AccountStatus
import com.backend.models.enums.FriendRequestStatus
import com.backend.models.enums.UserRole
import com.backend.repositories.FriendRequestRepository
import com.backend.repositories.UserRepository
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

    private fun tokenFor(user: User): String =
        jwtService.generateAccessToken(user.id!!, user.role.name)

    private fun authHeader(user: User): String = "Bearer ${tokenFor(user)}"

    private fun persistFriendRequest(sender: User, receiver: User, status: FriendRequestStatus = FriendRequestStatus.PENDING): FriendRequest =
        friendRequestRepository.saveAndFlush(
            FriendRequest(sender = sender, receiver = receiver, status = status)
        )

    @BeforeEach
    fun stubPushNotificationService() {
        doNothing().whenever(pushNotificationService).sendToUser(any(), any(), any(), any())
    }

    @AfterEach
    fun cleanUp() {
        friendRequestRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/friends/requests
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/friends/requests")
    inner class SendRequestTests {

        @Test
        fun `should create a pending friend request and return 201`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val payload = """{"receiverId":"${receiver.id}"}"""

            mockMvc.perform(
                post("/api/v1/friends/requests")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(sender))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sender.username").value("sender"))
                .andExpect(jsonPath("$.receiver.username").value("receiver"))

            val stored = friendRequestRepository.findBySenderIdAndReceiverId(sender.id!!, receiver.id!!)
            assertThat(stored).isPresent
        }

        @Test
        fun `should auto-accept when a reverse pending request already exists`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            persistFriendRequest(sender = userB, receiver = userA, status = FriendRequestStatus.PENDING)

            val payload = """{"receiverId":"${userB.id}"}"""

            mockMvc.perform(
                post("/api/v1/friends/requests")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("ACCEPTED"))

            val friendship = friendRequestRepository.findFriendshipBetween(userA.id!!, userB.id!!)
            assertThat(friendship).isPresent
        }

        @Test
        fun `should return 400 when sending a request to yourself`() {
            val user = persistUser()
            val payload = """{"receiverId":"${user.id}"}"""

            mockMvc.perform(
                post("/api/v1/friends/requests")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 404 when receiver does not exist`() {
            val sender = persistUser()
            val payload = """{"receiverId":"${UUID.randomUUID()}"}"""

            mockMvc.perform(
                post("/api/v1/friends/requests")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(sender))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 409 when already friends`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            persistFriendRequest(sender = userA, receiver = userB, status = FriendRequestStatus.ACCEPTED)

            val payload = """{"receiverId":"${userB.id}"}"""

            mockMvc.perform(
                post("/api/v1/friends/requests")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isConflict)
        }

        @Test
        fun `should return 409 when a pending request already exists in the same direction`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            persistFriendRequest(sender = sender, receiver = receiver, status = FriendRequestStatus.PENDING)

            val payload = """{"receiverId":"${receiver.id}"}"""

            mockMvc.perform(
                post("/api/v1/friends/requests")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(sender))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            ).andExpect(status().isConflict)
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/friends/requests/{requestId}/accept
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/friends/requests/{requestId}/accept")
    inner class AcceptRequestTests {

        @Test
        fun `should accept the request and return 200 when user is the receiver`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            mockMvc.perform(
                post("/api/v1/friends/requests/${request.id}/accept")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(receiver))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("ACCEPTED"))

            val updated = friendRequestRepository.findById(request.id!!).orElseThrow()
            assertThat(updated.status).isEqualTo(FriendRequestStatus.ACCEPTED)
        }

        @Test
        fun `should return 403 when user is not the receiver`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            mockMvc.perform(
                post("/api/v1/friends/requests/${request.id}/accept")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(sender))
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when request does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                post("/api/v1/friends/requests/${UUID.randomUUID()}/accept")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/friends/requests/{requestId}/reject
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/friends/requests/{requestId}/reject")
    inner class RejectRequestTests {

        @Test
        fun `should delete the request and return 204 when user is the receiver`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            mockMvc.perform(
                post("/api/v1/friends/requests/${request.id}/reject")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(receiver))
            ).andExpect(status().isNoContent)

            assertThat(friendRequestRepository.findById(request.id!!)).isEmpty()
        }

        @Test
        fun `should return 403 when user is not the receiver`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            mockMvc.perform(
                post("/api/v1/friends/requests/${request.id}/reject")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(sender))
            ).andExpect(status().isForbidden)

            assertThat(friendRequestRepository.findById(request.id!!)).isPresent
        }

        @Test
        fun `should return 404 when request does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                post("/api/v1/friends/requests/${UUID.randomUUID()}/reject")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/friends/requests/{requestId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/friends/requests/{requestId}")
    inner class CancelRequestTests {

        @Test
        fun `should delete the request and return 204 when user is the sender`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            mockMvc.perform(
                delete("/api/v1/friends/requests/${request.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(sender))
            ).andExpect(status().isNoContent)

            assertThat(friendRequestRepository.findById(request.id!!)).isEmpty()
        }

        @Test
        fun `should return 403 when user is not the sender`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            val request = persistFriendRequest(sender = sender, receiver = receiver)

            mockMvc.perform(
                delete("/api/v1/friends/requests/${request.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(receiver))
            ).andExpect(status().isForbidden)

            assertThat(friendRequestRepository.findById(request.id!!)).isPresent
        }

        @Test
        fun `should return 404 when request does not exist`() {
            val user = persistUser()

            mockMvc.perform(
                delete("/api/v1/friends/requests/${UUID.randomUUID()}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/friends/{friendUserId}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/friends/{friendUserId}")
    inner class RemoveFriendTests {

        @Test
        fun `should remove the friendship and return 204`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            val friendship = persistFriendRequest(sender = userA, receiver = userB, status = FriendRequestStatus.ACCEPTED)

            mockMvc.perform(
                delete("/api/v1/friends/${userB.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userA))
            ).andExpect(status().isNoContent)

            assertThat(friendRequestRepository.findById(friendship.id!!)).isEmpty()
        }

        @Test
        fun `should remove the friendship regardless of which side initiated it`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            val friendship = persistFriendRequest(sender = userB, receiver = userA, status = FriendRequestStatus.ACCEPTED)

            mockMvc.perform(
                delete("/api/v1/friends/${userB.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userA))
            ).andExpect(status().isNoContent)

            assertThat(friendRequestRepository.findById(friendship.id!!)).isEmpty()
        }

        @Test
        fun `should return 404 when no friendship exists`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")

            mockMvc.perform(
                delete("/api/v1/friends/${userB.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userA))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should not remove a pending request that is not yet a friendship`() {
            val userA = persistUser(username = "userA")
            val userB = persistUser(username = "userB")
            persistFriendRequest(sender = userA, receiver = userB, status = FriendRequestStatus.PENDING)

            mockMvc.perform(
                delete("/api/v1/friends/${userB.id}")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userA))
            ).andExpect(status().isNotFound)
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/friends
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/friends")
    inner class ListFriendsTests {

        @Test
        fun `should list accepted friends regardless of who sent the original request`() {
            val user = persistUser(username = "user")
            val friendAsSender = persistUser(username = "friend-sender-side")
            val friendAsReceiver = persistUser(username = "friend-receiver-side")

            persistFriendRequest(sender = user, receiver = friendAsSender, status = FriendRequestStatus.ACCEPTED)
            persistFriendRequest(sender = friendAsReceiver, receiver = user, status = FriendRequestStatus.ACCEPTED)

            mockMvc.perform(
                get("/api/v1/friends")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        fun `should not list pending requests as friends`() {
            val user = persistUser(username = "user")
            val other = persistUser(username = "other")
            persistFriendRequest(sender = user, receiver = other, status = FriendRequestStatus.PENDING)

            mockMvc.perform(
                get("/api/v1/friends")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should return an empty list when user has no friends`() {
            val user = persistUser()

            mockMvc.perform(
                get("/api/v1/friends")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/friends/requests/received
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/friends/requests/received")
    inner class ListPendingReceivedTests {

        @Test
        fun `should list pending requests received by the current user`() {
            val receiver = persistUser(username = "receiver")
            val sender = persistUser(username = "sender")
            persistFriendRequest(sender = sender, receiver = receiver, status = FriendRequestStatus.PENDING)

            mockMvc.perform(
                get("/api/v1/friends/requests/received")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(receiver))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sender.username").value("sender"))
        }

        @Test
        fun `should not include accepted requests`() {
            val receiver = persistUser(username = "receiver")
            val sender = persistUser(username = "sender")
            persistFriendRequest(sender = sender, receiver = receiver, status = FriendRequestStatus.ACCEPTED)

            mockMvc.perform(
                get("/api/v1/friends/requests/received")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(receiver))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not include requests sent by the user`() {
            val user = persistUser(username = "user")
            val other = persistUser(username = "other")
            persistFriendRequest(sender = user, receiver = other, status = FriendRequestStatus.PENDING)

            mockMvc.perform(
                get("/api/v1/friends/requests/received")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/friends/requests/sent
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/friends/requests/sent")
    inner class ListPendingSentTests {

        @Test
        fun `should list pending requests sent by the current user`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            persistFriendRequest(sender = sender, receiver = receiver, status = FriendRequestStatus.PENDING)

            mockMvc.perform(
                get("/api/v1/friends/requests/sent")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(sender))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].receiver.username").value("receiver"))
        }

        @Test
        fun `should not include accepted requests`() {
            val sender = persistUser(username = "sender")
            val receiver = persistUser(username = "receiver")
            persistFriendRequest(sender = sender, receiver = receiver, status = FriendRequestStatus.ACCEPTED)

            mockMvc.perform(
                get("/api/v1/friends/requests/sent")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(sender))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `should not include requests received by the user`() {
            val user = persistUser(username = "user")
            val other = persistUser(username = "other")
            persistFriendRequest(sender = other, receiver = user, status = FriendRequestStatus.PENDING)

            mockMvc.perform(
                get("/api/v1/friends/requests/sent")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }
}