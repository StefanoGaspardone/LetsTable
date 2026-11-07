package com.backend.unit.services

import com.backend.services.MailService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.IContext
import reactor.core.Exceptions
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class MailServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var templateEngine: TemplateEngine
    private lateinit var mailService: MailService

    private val jsonMapper = JsonMapper.builder().build()
    private val contextSlot = slot<IContext>()

    companion object {
        private const val FROM_ADDRESS = "noreply@letstable.com"
        private const val FROM_NAME = "Let's Table"
        private const val API_KEY = "test-api-key"
        private const val RECIPIENT = "user@example.com"
        private const val OTP_CODE = "123456"
        private const val EXPIRES_IN_MINUTES = 10L
        private const val HTML = "<html>OTP 123456</html>"
        private val TEST_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val SHORT_TIMEOUT: Duration = Duration.ofMillis(300)
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        templateEngine = mockk()
        every { templateEngine.process(any<String>(), capture(contextSlot)) } returns HTML

        mailService = createService()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.close()
    }

    private fun createService(mailEnabled: Boolean = true, timeout: Duration = TEST_TIMEOUT): MailService {
        val webClient = WebClient.builder().baseUrl(mockWebServer.url("/v3").toString()).build()
        return MailService(webClient, templateEngine, FROM_ADDRESS, FROM_NAME, API_KEY, mailEnabled, timeout)
    }

    private fun enqueue(code: Int, body: String = "{\"messageId\":\"<abc@smtp-relay.brevo.com>\"}") {
        mockWebServer.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .build()
        )
    }

    private fun sentPayload(): JsonNode = jsonMapper.readTree(mockWebServer.takeRequest().body!!.utf8())

    @Nested
    @DisplayName("request")
    inner class RequestTests {

        @Test
        fun `should post to the Brevo smtp endpoint with the api key`() {
            enqueue(201)

            mailService.sendActivationOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES)

            val request = mockWebServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.target).isEqualTo("/v3/smtp/email")
            assertThat(request.headers["api-key"]).isEqualTo(API_KEY)
            assertThat(request.headers[HttpHeaders.CONTENT_TYPE]).startsWith("application/json")
        }

        @Test
        fun `should send the sender, the recipient and the rendered html`() {
            enqueue(201)

            mailService.sendActivationOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES)

            val payload = sentPayload()
            assertThat(payload.get("sender").get("name").asString()).isEqualTo(FROM_NAME)
            assertThat(payload.get("sender").get("email").asString()).isEqualTo(FROM_ADDRESS)
            assertThat(payload.get("to").size()).isEqualTo(1)
            assertThat(payload.get("to").get(0).get("email").asString()).isEqualTo(RECIPIENT)
            assertThat(payload.get("htmlContent").asString()).isEqualTo(HTML)
        }
    }

    @Nested
    @DisplayName("sendActivationOtp")
    inner class SendActivationOtpTests {

        @Test
        fun `should render the otp template with the code and the expiration`() {
            enqueue(201)

            mailService.sendActivationOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES)

            verify(exactly = 1) { templateEngine.process("emails/otp", any<IContext>()) }
            assertThat(contextSlot.captured.getVariable("otpCode")).isEqualTo(OTP_CODE)
            assertThat(contextSlot.captured.getVariable("expiresInMinutes")).isEqualTo(EXPIRES_IN_MINUTES)
        }

        @Test
        fun `should use the activation subject`() {
            enqueue(201)

            mailService.sendActivationOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES)

            assertThat(sentPayload().get("subject").asString()).isEqualTo("Codice di attivazione - Let's Table")
        }
    }

    @Nested
    @DisplayName("sendPasswordResetOtp")
    inner class SendPasswordResetOtpTests {

        @Test
        fun `should render the password reset template with the code and the expiration`() {
            enqueue(201)

            mailService.sendPasswordResetOtp(RECIPIENT, "654321", 5L)

            verify(exactly = 1) { templateEngine.process("emails/password-reset", any<IContext>()) }
            assertThat(contextSlot.captured.getVariable("otpCode")).isEqualTo("654321")
            assertThat(contextSlot.captured.getVariable("expiresInMinutes")).isEqualTo(5L)
        }

        @Test
        fun `should use the password reset subject`() {
            enqueue(201)

            mailService.sendPasswordResetOtp(RECIPIENT, "654321", 5L)

            assertThat(sentPayload().get("subject").asString()).isEqualTo("Reimposta la tua password - Let's Table")
        }
    }

    @Nested
    @DisplayName("errors")
    inner class ErrorTests {

        @Test
        fun `should propagate a template failure without calling Brevo`() {
            every { templateEngine.process(any<String>(), any<IContext>()) } throws IllegalStateException("Template error")

            assertThatThrownBy { mailService.sendActivationOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Template error")

            assertThat(mockWebServer.requestCount).isZero()
        }

        @ParameterizedTest
        @ValueSource(ints = [400, 401, 403, 429, 500, 503])
        fun `should propagate an error response from Brevo`(code: Int) {
            enqueue(code, "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}")

            val thrown = catchThrowable { mailService.sendPasswordResetOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES) }

            assertThat(thrown).isInstanceOf(WebClientResponseException::class.java)
            assertThat((thrown as WebClientResponseException).statusCode.value()).isEqualTo(code)
            assertThat(thrown.responseBodyAsString).contains("Key not found")
        }

        @Test
        fun `should fail when Brevo does not answer in time`() {
            mockWebServer.enqueue(MockResponse.Builder().code(201).headersDelay(2, TimeUnit.SECONDS).build())
            val service = createService(timeout = SHORT_TIMEOUT)

            val thrown = catchThrowable { service.sendActivationOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES) }

            assertThat(Exceptions.unwrap(thrown)).isInstanceOf(TimeoutException::class.java)
        }
    }

    @Nested
    @DisplayName("mail disabled")
    inner class MailDisabledTests {

        @Test
        fun `should render the template but not call Brevo`() {
            val service = createService(mailEnabled = false)

            service.sendActivationOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES)

            verify(exactly = 1) { templateEngine.process("emails/otp", any<IContext>()) }
            assertThat(mockWebServer.requestCount).isZero()
        }

        @Test
        fun `should still propagate a template failure`() {
            every { templateEngine.process(any<String>(), any<IContext>()) } throws IllegalStateException("Template error")
            val service = createService(mailEnabled = false)

            assertThatThrownBy { service.sendPasswordResetOtp(RECIPIENT, OTP_CODE, EXPIRES_IN_MINUTES) }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }
}