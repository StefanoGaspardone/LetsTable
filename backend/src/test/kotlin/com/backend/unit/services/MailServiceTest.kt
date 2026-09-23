package com.backend.unit.services

import com.backend.services.MailService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import reactor.core.publisher.Mono

@ExtendWith(MockitoExtension::class)
class MailServiceTest {

    @Mock
    private lateinit var templateEngine: TemplateEngine

    @Captor
    private lateinit var contextCaptor: ArgumentCaptor<Context>

    @Captor
    private lateinit var payloadCaptor: ArgumentCaptor<Any>

    private lateinit var brevoWebClient: WebClient
    private lateinit var requestBodyUriSpec: WebClient.RequestBodyUriSpec
    private lateinit var requestBodySpec: WebClient.RequestBodySpec
    private lateinit var requestHeadersSpec: WebClient.RequestHeadersSpec<*>
    private lateinit var responseSpec: WebClient.ResponseSpec

    private lateinit var mailService: MailService

    private val fromAddress = "noreply@letstable.com"
    private val fromName = "Let's Table Team"
    private val brevoApiKey = "test-api-key"

    @BeforeEach
    fun setUp() {
        brevoWebClient = mock()
        requestBodyUriSpec = mock()
        requestBodySpec = mock()
        requestHeadersSpec = mock()
        responseSpec = mock()

        mailService = MailService(
            brevoWebClient = brevoWebClient,
            templateEngine = templateEngine,
            fromAddress = fromAddress,
            fromName = fromName,
            brevoApiKey = brevoApiKey,
            mailEnabled = true,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubSuccessfulSend() {
        `when`(brevoWebClient.post()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri("/smtp/email")).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.bodyValue(payloadCaptor.capture())).thenReturn(requestHeadersSpec as WebClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()))
    }

    @Nested
    @DisplayName("sendActivationOtp")
    inner class SendActivationOtpTests {

        @Test
        fun `should process template and send activation email with correct details`() {
            val recipient = "user@example.com"
            val otpCode = "123456"
            val expiresInMinutes = 10L
            val expectedHtml = "<html>Activation OTP: 123456</html>"

            `when`(templateEngine.process(eq("emails/otp"), contextCaptor.capture()))
                .thenReturn(expectedHtml)
            stubSuccessfulSend()

            mailService.sendActivationOtp(recipient, otpCode, expiresInMinutes)

            val capturedContext = contextCaptor.value
            assertThat(capturedContext.getVariable("otpCode")).isEqualTo(otpCode)
            assertThat(capturedContext.getVariable("expiresInMinutes")).isEqualTo(expiresInMinutes)

            @Suppress("UNCHECKED_CAST")
            val payload = payloadCaptor.value as Map<String, Any>
            assertThat(payload["subject"]).isEqualTo("Codice di attivazione - Let's Table")
            assertThat(payload["htmlContent"]).isEqualTo(expectedHtml)

            @Suppress("UNCHECKED_CAST")
            val toList = payload["to"] as List<Map<String, String>>
            assertThat(toList[0]["email"]).isEqualTo(recipient)
        }

        @Test
        fun `should propagate exception when template rendering fails`() {
            val recipient = "user@example.com"

            `when`(templateEngine.process(eq("emails/otp"), any(Context::class.java)))
                .thenThrow(RuntimeException("Template processing error"))

            assertThatThrownBy {
                mailService.sendActivationOtp(recipient, "123456", 10L)
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("Template processing error")
        }
    }

    @Nested
    @DisplayName("sendPasswordResetOtp")
    inner class SendPasswordResetOtpTests {

        @Test
        fun `should process template and send password reset email with correct details`() {
            val recipient = "user@example.com"
            val otpCode = "654321"
            val expiresInMinutes = 5L
            val expectedHtml = "<html>Reset OTP: 654321</html>"

            `when`(templateEngine.process(eq("emails/password-reset"), contextCaptor.capture()))
                .thenReturn(expectedHtml)
            stubSuccessfulSend()

            mailService.sendPasswordResetOtp(recipient, otpCode, expiresInMinutes)

            val capturedContext = contextCaptor.value
            assertThat(capturedContext.getVariable("otpCode")).isEqualTo(otpCode)
            assertThat(capturedContext.getVariable("expiresInMinutes")).isEqualTo(expiresInMinutes)

            @Suppress("UNCHECKED_CAST")
            val payload = payloadCaptor.value as Map<String, Any>
            assertThat(payload["subject"]).isEqualTo("Reimposta la tua password - Let's Table")
        }

        @Test
        fun `should rethrow exception when the Brevo API call fails`() {
            val recipient = "user@example.com"

            `when`(templateEngine.process(eq("emails/password-reset"), any(Context::class.java)))
                .thenReturn("<html>Reset OTP</html>")

            `when`(brevoWebClient.post()).thenReturn(requestBodyUriSpec)
            `when`(requestBodyUriSpec.uri("/smtp/email")).thenReturn(requestBodySpec)
            `when`(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec)
            @Suppress("UNCHECKED_CAST")
            `when`(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec as WebClient.RequestHeadersSpec<Nothing>)
            `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
            `when`(responseSpec.toBodilessEntity()).thenReturn(Mono.error(RuntimeException("Brevo API error")))

            assertThatThrownBy {
                mailService.sendPasswordResetOtp(recipient, "654321", 5L)
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("Brevo API error")
        }
    }

    @Nested
    @DisplayName("mail.enabled flag")
    inner class MailEnabledTests {

        @Test
        fun `should skip sending and not call Brevo when mail is disabled`() {
            val disabledMailService = MailService(
                brevoWebClient = brevoWebClient,
                templateEngine = templateEngine,
                fromAddress = fromAddress,
                fromName = fromName,
                brevoApiKey = brevoApiKey,
                mailEnabled = false,
            )

            `when`(templateEngine.process(eq("emails/otp"), any(Context::class.java)))
                .thenReturn("<html>OTP</html>")

            disabledMailService.sendActivationOtp("user@example.com", "123456", 10L)

            org.mockito.Mockito.verify(brevoWebClient, org.mockito.Mockito.never()).post()
        }
    }
}