package com.backend.services

import com.backend.exceptions.PushNotificationSendException
import com.backend.repositories.PushTokenRepository
import com.backend.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
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

    @Async
    fun sendToUser(userId: UUID, title: String, body: String, data: Map<String, Any> = emptyMap()) {
        logger.debug("\n\t[DEBUG] [push_notification_service][send_to_user] Sending push notification to user {}", userId)

        try {
            val user = userRepository.findById(userId).orElse(null)

            if(user == null || !user.notificationsEnabled) {
                logger.info("\n\t[INFO] [push_notification_service][send_to_user] Skipping notification for user {} (not found or notifications disabled)", userId)
                return
            }

            val tokens = pushTokenRepository.findAllByUserId(userId)

            if(tokens.isEmpty()) {
                logger.info("\n\t[INFO] [push_notification_service][send_to_user] No push tokens registered for user {}", userId)
                return
            }

            val messages = tokens.map {
                ExpoPushMessage(to = it.token, title = title, body = body, data = data)
            }

            expoPushWebClient.post()
                .uri("/send")
                .bodyValue(messages)
                .retrieve()
                .toBodilessEntity()
                .block()

            logger.info("\n\t[INFO] [push_notification_service][send_to_user] Push notification sent to {} device(s) for user {}", tokens.size, userId)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [push_notification_service][send_to_user] Error sending push notification to user {}: {}", userId, e.message)
            throw PushNotificationSendException(e)
        }
    }
}