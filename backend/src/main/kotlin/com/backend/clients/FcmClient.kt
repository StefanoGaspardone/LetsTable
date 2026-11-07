package com.backend.clients

import com.backend.utils.FcmAccessTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

enum class FcmSendResult {
    SENT,
    INVALID_TOKEN,
    FAILED,
}

@Component
class FcmClient(
    private val fcmWebClient: WebClient,
    private val accessTokenProvider: FcmAccessTokenProvider,
    @Value($$"${fcm.project-id}") private val projectId: String,
    @Value($$"${fcm.send-timeout:10s}") private val sendTimeout: Duration,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val BAD_REQUEST_STATUS = 400
        private const val FORBIDDEN_STATUS = 403
        private const val NOT_FOUND_STATUS = 404
        private const val SENDER_ID_MISMATCH = "SENDER_ID_MISMATCH"
        private const val INVALID_REGISTRATION_TOKEN = "registration token"
    }

    fun send(token: String, data: Map<String, String>): FcmSendResult {
        try {
            fcmWebClient.post()
                .uri("/projects/{projectId}/messages:send", projectId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${accessTokenProvider.accessToken()}")
                .bodyValue(
                    mapOf(
                        "message" to mapOf(
                            "token" to token,
                            "data" to data,
                            "android" to mapOf("priority" to "HIGH"),
                        )
                    )
                )
                .retrieve()
                .toBodilessEntity()
                .timeout(sendTimeout)
                .block()

            return FcmSendResult.SENT
        } catch(e: WebClientResponseException) {
            if(isInvalidToken(e)) {
                logger.warn("\n\t[WARN] [fcm_client][send] Token rejected by FCM with {}", e.statusCode)
                return FcmSendResult.INVALID_TOKEN
            }

            logger.error("\n\t[ERROR] [fcm_client][send] FCM responded with {}: {}", e.statusCode, e.responseBodyAsString)
            return FcmSendResult.FAILED
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [fcm_client][send] Error sending FCM message: {}", e.message, e)
            return FcmSendResult.FAILED
        }
    }

    private fun isInvalidToken(e: WebClientResponseException): Boolean {
        val body = e.responseBodyAsString

        return when(e.statusCode.value()) {
            NOT_FOUND_STATUS -> true
            FORBIDDEN_STATUS -> body.contains(SENDER_ID_MISMATCH)
            BAD_REQUEST_STATUS -> body.contains(INVALID_REGISTRATION_TOKEN, ignoreCase = true)
            else -> false
        }
    }
}