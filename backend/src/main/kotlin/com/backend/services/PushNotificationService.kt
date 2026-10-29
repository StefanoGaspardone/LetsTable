package com.backend.services

import com.backend.repositories.PushTokenRepository
import com.backend.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.UUID

data class ExpoPushMessage(
    val to: String,
    val title: String,
    val body: String,
    val data: Map<String, Any> = emptyMap(),
    val sound: String = "default",
)

@Service
class PushNotificationService(
    private val pushTokenRepository: PushTokenRepository,
    private val userRepository: UserRepository,
    private val expoPushWebClient: WebClient,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SEND_TIMEOUT: Duration = Duration.ofSeconds(10)
    }

    fun sendToUser(userId: UUID, title: String, body: String, data: Map<String, Any> = emptyMap()) {
        logger.debug("\n\t[DEBUG] [push_notification_service][send_to_user] Preparing push notification for user {}", userId)

        try {
            val messages = buildMessages(userId, title, body, data)

            if(messages.isEmpty()) {
                return
            }

            if(TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() = send(userId, messages)
                })
            } else {
                send(userId, messages)
            }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [push_notification_service][send_to_user] Error preparing push notification for user {}: {}", userId, e.message)
        }
    }

    private fun buildMessages(userId: UUID, title: String, body: String, data: Map<String, Any>): List<ExpoPushMessage> {
        val user = userRepository.findById(userId).orElse(null)

        if(user == null || !user.notificationsEnabled) {
            logger.info("\n\t[INFO] [push_notification_service][build_messages] Skipping notification for user {} (not found or notifications disabled)", userId)
            return emptyList()
        }

        val tokens = pushTokenRepository.findAllByUserId(userId)

        if(tokens.isEmpty()) {
            logger.info("\n\t[INFO] [push_notification_service][build_messages] No push tokens registered for user {}", userId)
            return emptyList()
        }

        return tokens.map { ExpoPushMessage(to = it.token, title = title, body = body, data = data) }
    }

    private fun send(userId: UUID, messages: List<ExpoPushMessage>) {
        try {
            expoPushWebClient.post()
                .uri("/send")
                .bodyValue(messages)
                .retrieve()
                .toBodilessEntity()
                .timeout(SEND_TIMEOUT)
                .block()

            logger.info("\n\t[INFO] [push_notification_service][send] Push notification sent to {} device(s) for user {}", messages.size, userId)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [push_notification_service][send] Error sending push notification to user {}: {}", userId, e.message)
        }
    }
}