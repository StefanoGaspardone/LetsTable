package com.backend.services

import com.backend.exceptions.UserNotFoundException
import com.backend.models.dtos.RegisterPushTokenRequest
import com.backend.models.entities.PushToken
import com.backend.repositories.PushTokenRepository
import com.backend.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PushTokenService(
    private val pushTokenRepository: PushTokenRepository,
    private val userRepository: UserRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun register(userId: UUID, request: RegisterPushTokenRequest) {
        logger.debug("\n\t[DEBUG] [push_token_service][register] Registering push token for user {}", userId)

        try {
            val user = userRepository.findById(userId).orElseThrow { UserNotFoundException(userId) }
            val existing = pushTokenRepository.findByToken(request.token).orElse(null)

            if(existing != null) {
                existing.user = user
                existing.deviceName = request.deviceName
                pushTokenRepository.save(existing)
            } else {
                pushTokenRepository.save(PushToken(user = user, token = request.token, deviceName = request.deviceName))
            }

            logger.info("\n\t[INFO] [push_token_service][register] Push token registered for user {}", userId)
        } catch(e: UserNotFoundException) {
            logger.warn("\n\t[WARN] [push_token_service][register] User {} not found", userId)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [push_token_service][register] Error registering push token for user {}: {}", userId, e.message, e)
            throw e
        }
    }

    @Transactional
    fun unregister(userId: UUID, token: String) {
        logger.debug("\n\t[DEBUG] [push_token_service][unregister] Unregistering push token for user {}", userId)

        try {
            pushTokenRepository.deleteByTokenAndUserId(token, userId)
            logger.info("\n\t[INFO] [push_token_service][unregister] Push token unregistered for user {}", userId)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [push_token_service][unregister] Error unregistering push token for user {}: {}", userId, e.message, e)
            throw e
        }
    }
}