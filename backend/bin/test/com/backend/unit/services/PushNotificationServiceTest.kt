package com.backend.unit.services

import com.backend.exceptions.PushNotificationSendException
import com.backend.models.entities.PushToken
import com.backend.models.entities.User
import com.backend.models.enums.UserRole
import com.backend.repositories.PushTokenRepository
import com.backend.repositories.UserRepository
import com.backend.services.ExpoPushMessage
import com.backend.services.PushNotificationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PushNotificationServiceTest {

    @Mock
    private lateinit var pushTokenRepository: PushTokenRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var expoPushWebClient: WebClient

    @Mock
    private lateinit var requestBodyUriSpec: WebClient.RequestBodyUriSpec

    @Mock
    private lateinit var requestBodySpec: WebClient.RequestBodySpec

    @Mock
    private lateinit var responseSpec: WebClient.ResponseSpec

    private lateinit var pushNotificationService: PushNotificationService

    private val userId: UUID = UUID.randomUUID()
    private val sampleUser = User(
        id = userId,
        username = "stefano",
        email = "stefano@example.com",
        passwordHash = "hash",
        role = UserRole.USER,
        notificationsEnabled = true
    )

    private val tokenValue1 = "ExponentPushToken[xxxxxxxxxxxxxx]"
    private val tokenValue2 = "ExponentPushToken[yyyyyyyyyyyyyy]"

    @BeforeEach
    fun setUp() {
        pushNotificationService = PushNotificationService(
            pushTokenRepository = pushTokenRepository,
            userRepository = userRepository,
            expoPushWebClient = expoPushWebClient
        )
    }

    private fun setupWebClientMockChain() {
        whenever(expoPushWebClient.post()).thenReturn(requestBodyUriSpec)
        whenever(requestBodyUriSpec.uri("/send")).thenReturn(requestBodySpec)
    }

    @Nested
    @DisplayName("sendToUser")
    inner class SendToUserTests {

        @Test
        fun `should do nothing when user is not found`() {
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            pushNotificationService.sendToUser(userId, "Test Title", "Test Body")

            verify(userRepository).findById(userId)
            verify(pushTokenRepository, never()).findAllByUserId(any())
            verify(expoPushWebClient, never()).post()
        }

        @Test
        fun `should do nothing when user has notifications disabled`() {
            val userWithDisabledNotifications = User(
                id = userId,
                username = "stefano",
                email = "stefano@example.com",
                passwordHash = "hash",
                role = UserRole.USER,
                notificationsEnabled = false
            )

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(userWithDisabledNotifications))

            pushNotificationService.sendToUser(userId, "Test Title", "Test Body")

            verify(userRepository).findById(userId)
            verify(pushTokenRepository, never()).findAllByUserId(any())
            verify(expoPushWebClient, never()).post()
        }

        @Test
        fun `should do nothing when user has no registered tokens`() {
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser))
            whenever(pushTokenRepository.findAllByUserId(userId)).thenReturn(emptyList())

            pushNotificationService.sendToUser(userId, "Test Title", "Test Body")

            verify(userRepository).findById(userId)
            verify(pushTokenRepository).findAllByUserId(userId)
            verify(expoPushWebClient, never()).post()
        }

        @Test
        fun `should send notification messages successfully when tokens exist`() {
            setupWebClientMockChain()

            val token1 = PushToken(
                id = UUID.randomUUID(),
                user = sampleUser,
                token = tokenValue1,
                deviceName = "iPhone di Stefano"
            )
            val token2 = PushToken(
                id = UUID.randomUUID(),
                user = sampleUser,
                token = tokenValue2,
                deviceName = "iPad"
            )

            @Suppress("UNCHECKED_CAST")
            val payloadCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<ExpoPushMessage>>

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser))
            whenever(pushTokenRepository.findAllByUserId(userId)).thenReturn(listOf(token1, token2))
            whenever(requestBodySpec.bodyValue(payloadCaptor.capture())).thenReturn(requestBodySpec)
            whenever(requestBodySpec.retrieve()).thenReturn(responseSpec)
            whenever(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()))

            pushNotificationService.sendToUser(
                userId = userId,
                title = "Match Invitation",
                body = "You have been invited to a match!",
                data = mapOf("matchId" to "12345")
            )

            verify(userRepository).findById(userId)
            verify(pushTokenRepository).findAllByUserId(userId)
            verify(expoPushWebClient).post()

            val capturedMessages = payloadCaptor.value
            assertThat(capturedMessages).hasSize(2)
            assertThat(capturedMessages[0].to).isEqualTo(tokenValue1)
            assertThat(capturedMessages[0].title).isEqualTo("Match Invitation")
            assertThat(capturedMessages[0].body).isEqualTo("You have been invited to a match!")
            assertThat(capturedMessages[0].data).containsEntry("matchId", "12345")
            assertThat(capturedMessages[1].to).isEqualTo(tokenValue2)
        }

        @Test
        fun `should throw PushNotificationSendException when repository throws exception`() {
            whenever(userRepository.findById(userId)).thenThrow(RuntimeException("Database connection error"))

            assertThatThrownBy {
                pushNotificationService.sendToUser(userId, "Title", "Body")
            }.isInstanceOf(PushNotificationSendException::class.java)
        }

        @Test
        fun `should throw PushNotificationSendException when WebClient HTTP request fails`() {
            setupWebClientMockChain()

            val token = PushToken(
                id = UUID.randomUUID(),
                user = sampleUser,
                token = tokenValue1
            )

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser))
            whenever(pushTokenRepository.findAllByUserId(userId)).thenReturn(listOf(token))
            whenever(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec)
            whenever(requestBodySpec.retrieve()).thenReturn(responseSpec)
            whenever(responseSpec.toBodilessEntity()).thenReturn(Mono.error(RuntimeException("HTTP 500 Internal Server Error")))

            assertThatThrownBy {
                pushNotificationService.sendToUser(userId, "Title", "Body")
            }.isInstanceOf(PushNotificationSendException::class.java)
        }
    }
}