package com.backend.services

import com.backend.clients.FcmClient
import com.backend.clients.FcmSendResult
import com.backend.models.entities.PushToken
import com.backend.models.enums.NotificationType
import com.backend.repositories.PushTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class PushNotificationService(
    private val pushTokenRepository: PushTokenRepository,
    private val fcmClient: FcmClient,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val newTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun sendToUser(userId: UUID, type: NotificationType, title: String, body: String, data: Map<String, Any> = emptyMap()) =
        sendToUsers(setOf(userId), type, title, body, data)

    fun sendToUsers(userIds: Collection<UUID>, type: NotificationType, title: String, body: String, data: Map<String, Any> = emptyMap()) {
        if(userIds.isEmpty()) {
            return
        }

        logger.debug("\n\t[DEBUG] [push_notification_service][send_to_users] Preparing {} notification for {} user(s)", type, userIds.size)

        try {
            val tokens = pushTokenRepository.findAllForNotifiableUsers(userIds)

            if(tokens.isEmpty()) {
                logger.info("\n\t[INFO] [push_notification_service][send_to_users] No notifiable devices for {} notification", type)
                return
            }

            val message = buildMessage(type, title, body, data + ("type" to type.name))

            runAfterCommit { send(type, tokens, message) }
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [push_notification_service][send_to_users] Error preparing {} notification: {}", type, e.message, e)
        }
    }

    private fun buildMessage(type: NotificationType, title: String, body: String, payload: Map<String, Any>): Map<String, String> =
        buildMap {
            put("title", title)
            put("message", body)
            put("body", objectMapper.writeValueAsString(payload))
            put("channelId", type.channel.id)
            type.categoryId?.let { put("categoryId", it) }
        }

    private fun runAfterCommit(action: () -> Unit) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = action()
            })
        } else {
            action()
        }
    }

    private fun send(type: NotificationType, tokens: List<PushToken>, message: Map<String, String>) {
        try {
            val results = tokens.associate { it.token to fcmClient.send(it.token, message) }
            val invalidTokens = results.filterValues { it == FcmSendResult.INVALID_TOKEN }.keys

            if(invalidTokens.isNotEmpty()) {
                removeInvalidTokens(invalidTokens)
            }

            logger.info("\n\t[INFO] [push_notification_service][send] {} notification sent to {}/{} device(s)", type, results.count { it.value == FcmSendResult.SENT }, tokens.size)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [push_notification_service][send] Error sending {} notification: {}", type, e.message, e)
        }
    }

    private fun removeInvalidTokens(invalidTokens: Set<String>) {
        try {
            newTransaction.executeWithoutResult { pushTokenRepository.deleteAllByTokenIn(invalidTokens) }
            logger.info("\n\t[INFO] [push_notification_service][remove_invalid_tokens] Removed {} unregistered token(s)", invalidTokens.size)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [push_notification_service][remove_invalid_tokens] Error removing unregistered tokens: {}", e.message, e)
        }
    }
}