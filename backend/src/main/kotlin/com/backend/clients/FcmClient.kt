package com.backend.clients

import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.io.FileInputStream
import java.time.Duration

enum class FcmSendResult {
    SENT,
    INVALID_TOKEN,
    FAILED,
}

@Component
class FcmClient(
    private val fcmWebClient: WebClient,
    @Value($$"${fcm.project-id}") private val projectId: String,
    @Value($$"${fcm.credentials-path:}") private val credentialsPath: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        private val SEND_TIMEOUT: Duration = Duration.ofSeconds(10)
        private const val UNREGISTERED_STATUS = 404
    }

    private val credentials: GoogleCredentials by lazy {
        val base = if(credentialsPath.isBlank()) {
            GoogleCredentials.getApplicationDefault()
        } else {
            FileInputStream(credentialsPath).use { GoogleCredentials.fromStream(it) }
        }

        base.createScoped(listOf(MESSAGING_SCOPE))
    }

    @Synchronized
    private fun accessToken(): String {
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }

    fun send(token: String, data: Map<String, String>): FcmSendResult {
        try {
            fcmWebClient.post()
                .uri("/projects/{projectId}/messages:send", projectId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${accessToken()}")
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
                .timeout(SEND_TIMEOUT)
                .block()

            return FcmSendResult.SENT
        } catch(e: WebClientResponseException) {
            if(e.statusCode.value() == UNREGISTERED_STATUS) {
                logger.warn("\n\t[WARN] [fcm_client][send] Token no longer registered")
                return FcmSendResult.INVALID_TOKEN
            }

            logger.error("\n\t[ERROR] [fcm_client][send] FCM responded with {}: {}", e.statusCode, e.responseBodyAsString)
            return FcmSendResult.FAILED
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [fcm_client][send] Error sending FCM message: {}", e.message)
            return FcmSendResult.FAILED
        }
    }
}