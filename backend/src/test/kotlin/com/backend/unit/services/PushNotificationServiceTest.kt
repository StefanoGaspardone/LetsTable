package com.backend.unit.services

import com.backend.clients.FcmClient
import com.backend.clients.FcmSendResult
import com.backend.models.entities.PushToken
import com.backend.models.entities.User
import com.backend.models.enums.NotificationType
import com.backend.repositories.PushTokenRepository
import com.backend.services.PushNotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class PushNotificationServiceTest {

    private lateinit var pushTokenRepository: PushTokenRepository
    private lateinit var fcmClient: FcmClient
    private lateinit var transactionManager: PlatformTransactionManager
    private lateinit var service: PushNotificationService

    private val jsonMapper = JsonMapper.builder().build()
    private val definitionSlot = slot<TransactionDefinition>()

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val user = User(id = userId, username = "stefano", email = "stefano@example.com", passwordHash = "hash")

    companion object {
        private val TYPE = NotificationType.FRIEND_REQUEST
        private const val TITLE = "Nuova richiesta di amicizia"
        private const val BODY = "Marco ti ha aggiunto"
        private const val TOKEN_A = "fcm-token-a"
        private const val TOKEN_B = "fcm-token-b"
        private const val TOKEN_C = "fcm-token-c"
    }

    @BeforeEach
    fun setUp() {
        pushTokenRepository = mockk(relaxed = true)
        fcmClient = mockk()
        transactionManager = mockk(relaxed = true)

        every { fcmClient.send(any(), any()) } returns FcmSendResult.SENT
        every { transactionManager.getTransaction(capture(definitionSlot)) } returns SimpleTransactionStatus()

        service = PushNotificationService(pushTokenRepository, fcmClient, jsonMapper, transactionManager)
    }

    @AfterEach
    fun tearDown() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun token(value: String) = PushToken(id = UUID.randomUUID(), user = user, token = value)

    private fun stubTokens(vararg values: String) {
        every { pushTokenRepository.findAllForNotifiableUsers(any()) } returns values.map { token(it) }
    }

    private fun sentMessage(token: String = TOKEN_A): Map<String, String> {
        val messageSlot = slot<Map<String, String>>()
        verify { fcmClient.send(token, capture(messageSlot)) }
        return messageSlot.captured
    }

    private fun synchronizations(): List<TransactionSynchronization> = TransactionSynchronizationManager.getSynchronizations()

    private fun commit() = synchronizations().forEach { it.afterCommit(); it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }

    private fun rollback() = synchronizations().forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

    @Nested
    @DisplayName("recipients")
    inner class RecipientTests {

        @Test
        fun `should do nothing for an empty list of users`() {
            service.sendToUsers(emptyList(), TYPE, TITLE, BODY)

            verify(exactly = 0) { pushTokenRepository.findAllForNotifiableUsers(any()) }
            verify(exactly = 0) { fcmClient.send(any(), any()) }
        }

        @Test
        fun `should look up the tokens of the single user`() {
            stubTokens(TOKEN_A)

            service.sendToUser(userId, TYPE, TITLE, BODY)

            verify(exactly = 1) { pushTokenRepository.findAllForNotifiableUsers(setOf(userId)) }
        }

        @Test
        fun `should look up the tokens of every user`() {
            stubTokens(TOKEN_A)

            service.sendToUsers(listOf(userId, otherUserId), TYPE, TITLE, BODY)

            verify(exactly = 1) { pushTokenRepository.findAllForNotifiableUsers(listOf(userId, otherUserId)) }
        }

        @Test
        fun `should not call FCM when no device can be notified`() {
            stubTokens()

            service.sendToUser(userId, TYPE, TITLE, BODY)

            verify(exactly = 0) { fcmClient.send(any(), any()) }
        }

        @Test
        fun `should send to every device`() {
            stubTokens(TOKEN_A, TOKEN_B, TOKEN_C)

            service.sendToUsers(listOf(userId, otherUserId), TYPE, TITLE, BODY)

            verify(exactly = 1) { fcmClient.send(TOKEN_A, any()) }
            verify(exactly = 1) { fcmClient.send(TOKEN_B, any()) }
            verify(exactly = 1) { fcmClient.send(TOKEN_C, any()) }
        }

        @Test
        fun `should send the same message to every device`() {
            stubTokens(TOKEN_A, TOKEN_B)

            service.sendToUser(userId, TYPE, TITLE, BODY)

            assertThat(sentMessage(TOKEN_A)).isEqualTo(sentMessage(TOKEN_B))
        }
    }

    @Nested
    @DisplayName("message")
    inner class MessageTests {

        @Test
        fun `should carry the title and the text`() {
            stubTokens(TOKEN_A)

            service.sendToUser(userId, TYPE, TITLE, BODY)

            val message = sentMessage()
            assertThat(message["title"]).isEqualTo(TITLE)
            assertThat(message["message"]).isEqualTo(BODY)
        }

        @Test
        fun `should serialize the data and the type as json in the body`() {
            stubTokens(TOKEN_A)
            val friendId = UUID.randomUUID()

            service.sendToUser(userId, TYPE, TITLE, BODY, mapOf("friendId" to friendId, "count" to 3))

            val payload = jsonMapper.readTree(sentMessage()["body"])
            assertThat(payload.get("friendId").asString()).isEqualTo(friendId.toString())
            assertThat(payload.get("count").asInt()).isEqualTo(3)
            assertThat(payload.get("type").asString()).isEqualTo(TYPE.name)
            assertThat(payload.size()).isEqualTo(3)
        }

        @Test
        fun `should send only the type when there is no data`() {
            stubTokens(TOKEN_A)

            service.sendToUser(userId, TYPE, TITLE, BODY)

            val payload = jsonMapper.readTree(sentMessage()["body"])
            assertThat(payload.size()).isEqualTo(1)
            assertThat(payload.get("type").asString()).isEqualTo(TYPE.name)
        }

        @Test
        fun `should not let the data override the notification type`() {
            stubTokens(TOKEN_A)

            service.sendToUser(userId, TYPE, TITLE, BODY, mapOf("type" to "SOMETHING_ELSE"))

            assertThat(jsonMapper.readTree(sentMessage()["body"]).get("type").asString()).isEqualTo(TYPE.name)
        }

        @ParameterizedTest
        @EnumSource(NotificationType::class)
        fun `should use the channel and the category of the type`(type: NotificationType) {
            stubTokens(TOKEN_A)

            service.sendToUser(userId, type, TITLE, BODY)

            val message = sentMessage()
            assertThat(message["channelId"]).isEqualTo(type.channel.id)
            if(type.categoryId == null) {
                assertThat(message).doesNotContainKey("categoryId")
            } else {
                assertThat(message["categoryId"]).isEqualTo(type.categoryId)
            }
        }
    }

    @Nested
    @DisplayName("invalid tokens")
    inner class InvalidTokenTests {

        @Test
        fun `should remove only the tokens FCM no longer recognizes`() {
            stubTokens(TOKEN_A, TOKEN_B, TOKEN_C)
            every { fcmClient.send(TOKEN_B, any()) } returns FcmSendResult.INVALID_TOKEN
            every { fcmClient.send(TOKEN_C, any()) } returns FcmSendResult.FAILED

            service.sendToUser(userId, TYPE, TITLE, BODY)

            verify(exactly = 1) { pushTokenRepository.deleteAllByTokenIn(setOf(TOKEN_B)) }
        }

        @Test
        fun `should remove the tokens in a new transaction`() {
            stubTokens(TOKEN_A)
            every { fcmClient.send(TOKEN_A, any()) } returns FcmSendResult.INVALID_TOKEN

            service.sendToUser(userId, TYPE, TITLE, BODY)

            assertThat(definitionSlot.captured.propagationBehavior).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
            verify(exactly = 1) { transactionManager.commit(any()) }
        }

        @Test
        fun `should not remove anything when every token works`() {
            stubTokens(TOKEN_A, TOKEN_B)

            service.sendToUser(userId, TYPE, TITLE, BODY)

            verify(exactly = 0) { pushTokenRepository.deleteAllByTokenIn(any()) }
            verify(exactly = 0) { transactionManager.getTransaction(any()) }
        }

        @Test
        fun `should not remove tokens that failed for other reasons`() {
            stubTokens(TOKEN_A)
            every { fcmClient.send(TOKEN_A, any()) } returns FcmSendResult.FAILED

            service.sendToUser(userId, TYPE, TITLE, BODY)

            verify(exactly = 0) { pushTokenRepository.deleteAllByTokenIn(any()) }
        }

        @Test
        fun `should not fail when removing the tokens fails`() {
            stubTokens(TOKEN_A)
            every { fcmClient.send(TOKEN_A, any()) } returns FcmSendResult.INVALID_TOKEN
            every { pushTokenRepository.deleteAllByTokenIn(any()) } throws RuntimeException("Database error")

            assertThatCode { service.sendToUser(userId, TYPE, TITLE, BODY) }.doesNotThrowAnyException()

            verify(exactly = 1) { transactionManager.rollback(any()) }
        }
    }

    @Nested
    @DisplayName("transaction")
    inner class TransactionTests {

        @BeforeEach
        fun startTransaction() {
            TransactionSynchronizationManager.initSynchronization()
        }

        @Test
        fun `should wait for the commit before sending`() {
            stubTokens(TOKEN_A)

            service.sendToUser(userId, TYPE, TITLE, BODY)

            verify(exactly = 0) { fcmClient.send(any(), any()) }
            assertThat(synchronizations()).hasSize(1)

            commit()

            verify(exactly = 1) { fcmClient.send(TOKEN_A, any()) }
        }

        @Test
        fun `should not send when the transaction rolls back`() {
            stubTokens(TOKEN_A)

            service.sendToUser(userId, TYPE, TITLE, BODY)
            rollback()

            verify(exactly = 0) { fcmClient.send(any(), any()) }
        }

        @Test
        fun `should not register anything when there are no devices`() {
            stubTokens()

            service.sendToUser(userId, TYPE, TITLE, BODY)

            assertThat(synchronizations()).isEmpty()
        }

        @Test
        fun `should remove invalid tokens after the commit in a new transaction`() {
            stubTokens(TOKEN_A)
            every { fcmClient.send(TOKEN_A, any()) } returns FcmSendResult.INVALID_TOKEN

            service.sendToUser(userId, TYPE, TITLE, BODY)
            verify(exactly = 0) { pushTokenRepository.deleteAllByTokenIn(any()) }

            commit()

            verify(exactly = 1) { pushTokenRepository.deleteAllByTokenIn(setOf(TOKEN_A)) }
            assertThat(definitionSlot.captured.propagationBehavior).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        }

        @Test
        fun `should not break the commit when sending fails`() {
            stubTokens(TOKEN_A)
            every { fcmClient.send(any(), any()) } throws IllegalStateException("Unexpected")

            service.sendToUser(userId, TYPE, TITLE, BODY)

            assertThatCode { commit() }.doesNotThrowAnyException()
        }
    }

    @Nested
    @DisplayName("errors")
    inner class ErrorTests {

        @Test
        fun `should swallow a failure while loading the tokens`() {
            every { pushTokenRepository.findAllForNotifiableUsers(any()) } throws RuntimeException("Database error")

            assertThatCode { service.sendToUser(userId, TYPE, TITLE, BODY) }.doesNotThrowAnyException()

            verify(exactly = 0) { fcmClient.send(any(), any()) }
        }

        @Test
        fun `should keep sending to the other devices when one fails`() {
            stubTokens(TOKEN_A, TOKEN_B)
            every { fcmClient.send(TOKEN_A, any()) } returns FcmSendResult.FAILED

            service.sendToUser(userId, TYPE, TITLE, BODY)

            verify(exactly = 1) { fcmClient.send(TOKEN_B, any()) }
        }
    }
}