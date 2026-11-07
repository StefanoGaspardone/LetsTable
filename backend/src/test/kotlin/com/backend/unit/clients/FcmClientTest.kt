package com.backend.unit.clients

import com.backend.clients.FcmClient
import com.backend.clients.FcmSendResult
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.TimeUnit

class FcmClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fcmClient: FcmClient

    private val jsonMapper = JsonMapper.builder().build()
    private var tokenRequests = 0

    companion object {
        private const val PROJECT_ID = "test-project"
        private const val ACCESS_TOKEN = "ya29.test-access-token"
        private const val DEVICE_TOKEN = "device-token-abc"
        private val TEST_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val SHORT_TIMEOUT: Duration = Duration.ofMillis(300)
        private val DATA = mapOf("title" to "Nuova richiesta", "message" to "Marco ti ha aggiunto", "channelId" to "friends")

        private fun fcmError(code: Int, status: String, message: String, errorCode: String) = """
            {
                "error": {
                    "code": $code,
                    "message": "$message",
                    "status": "$status",
                    "details": [
                        { "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", "errorCode": "$errorCode" }
                    ]
                }
            }
        """.trimIndent()
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenRequests = 0
        fcmClient = createClient()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.close()
    }

    private fun createClient(timeout: Duration = TEST_TIMEOUT, baseUrl: String = mockWebServer.url("/v1").toString()): FcmClient {
        val webClient = WebClient.builder().baseUrl(baseUrl).build()
        return FcmClient(webClient, { tokenRequests += 1; ACCESS_TOKEN }, PROJECT_ID, timeout)
    }

    private fun enqueue(code: Int, body: String = "{}") {
        mockWebServer.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .build()
        )
    }

    @Nested
    @DisplayName("request")
    inner class RequestTests {

        @Test
        fun `should post to the project messages endpoint with the access token`() {
            enqueue(200, "{\"name\":\"projects/test-project/messages/1\"}")

            fcmClient.send(DEVICE_TOKEN, DATA)

            val request = mockWebServer.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/projects/$PROJECT_ID/messages:send", request.target)
            assertEquals("Bearer $ACCESS_TOKEN", request.headers[HttpHeaders.AUTHORIZATION])
            assertTrue(request.headers[HttpHeaders.CONTENT_TYPE]!!.startsWith("application/json"))
        }

        @Test
        fun `should send a data-only message with high android priority`() {
            enqueue(200)

            fcmClient.send(DEVICE_TOKEN, DATA)

            val message = jsonMapper.readTree(mockWebServer.takeRequest().body!!.utf8()).get("message")
            assertEquals(DEVICE_TOKEN, message.get("token").asString())
            assertEquals(DATA, DATA.keys.associateWith { message.get("data").get(it).asString() })
            assertEquals(DATA.size, message.get("data").size())
            assertEquals("HIGH", message.get("android").get("priority").asString())
            assertFalse(message.has("notification"))
        }

        @Test
        fun `should ask for a fresh access token on every send`() {
            enqueue(200)
            enqueue(200)

            fcmClient.send(DEVICE_TOKEN, DATA)
            fcmClient.send(DEVICE_TOKEN, DATA)

            assertEquals(2, tokenRequests)
        }
    }

    @Nested
    @DisplayName("results")
    inner class ResultTests {

        @Test
        fun `should return SENT when FCM accepts the message`() {
            enqueue(200, "{\"name\":\"projects/test-project/messages/1\"}")

            assertEquals(FcmSendResult.SENT, fcmClient.send(DEVICE_TOKEN, DATA))
        }

        @Test
        fun `should return INVALID_TOKEN when the token is no longer registered`() {
            enqueue(404, fcmError(404, "NOT_FOUND", "Requested entity was not found.", "UNREGISTERED"))

            assertEquals(FcmSendResult.INVALID_TOKEN, fcmClient.send(DEVICE_TOKEN, DATA))
        }

        @Test
        fun `should return INVALID_TOKEN when the token belongs to another Firebase project`() {
            enqueue(403, fcmError(403, "PERMISSION_DENIED", "SenderId mismatch", "SENDER_ID_MISMATCH"))

            assertEquals(FcmSendResult.INVALID_TOKEN, fcmClient.send(DEVICE_TOKEN, DATA))
        }

        @Test
        fun `should return INVALID_TOKEN when the token is malformed`() {
            enqueue(400, fcmError(400, "INVALID_ARGUMENT", "The registration token is not a valid FCM registration token", "INVALID_ARGUMENT"))

            assertEquals(FcmSendResult.INVALID_TOKEN, fcmClient.send(DEVICE_TOKEN, DATA))
        }

        @Test
        fun `should return FAILED when the payload is rejected`() {
            enqueue(400, fcmError(400, "INVALID_ARGUMENT", "Invalid value at 'message.data[0].value'", "INVALID_ARGUMENT"))

            assertEquals(FcmSendResult.FAILED, fcmClient.send(DEVICE_TOKEN, DATA))
        }

        @Test
        fun `should return FAILED when our service account is not allowed to send`() {
            enqueue(403, fcmError(403, "PERMISSION_DENIED", "Firebase Cloud Messaging API has not been used in project", "THIRD_PARTY_AUTH_ERROR"))

            assertEquals(FcmSendResult.FAILED, fcmClient.send(DEVICE_TOKEN, DATA))
        }

        @ParameterizedTest
        @ValueSource(ints = [401, 429, 500, 503])
        fun `should return FAILED on other FCM errors`(status: Int) {
            enqueue(status, fcmError(status, "UNAVAILABLE", "Error", "UNAVAILABLE"))

            assertEquals(FcmSendResult.FAILED, fcmClient.send(DEVICE_TOKEN, DATA))
        }
    }

    @Nested
    @DisplayName("failures")
    inner class FailureTests {

        @Test
        fun `should return FAILED without calling FCM when the access token cannot be obtained`() {
            val webClient = WebClient.builder().baseUrl(mockWebServer.url("/v1").toString()).build()
            val client = FcmClient(webClient, { throw IllegalStateException("No credentials") }, PROJECT_ID, TEST_TIMEOUT)

            assertEquals(FcmSendResult.FAILED, client.send(DEVICE_TOKEN, DATA))
            assertEquals(0, mockWebServer.requestCount)
        }

        @Test
        fun `should return FAILED when FCM does not answer in time`() {
            mockWebServer.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(2, TimeUnit.SECONDS).build())

            assertEquals(FcmSendResult.FAILED, createClient(timeout = SHORT_TIMEOUT).send(DEVICE_TOKEN, DATA))
        }

        @Test
        fun `should return FAILED when FCM is unreachable`() {
            val client = createClient(baseUrl = "http://localhost:1/v1")

            assertEquals(FcmSendResult.FAILED, client.send(DEVICE_TOKEN, DATA))
        }
    }
}