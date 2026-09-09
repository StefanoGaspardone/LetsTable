package com.backend.unit.services

import com.backend.services.MailService
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mail.javamail.JavaMailSender
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.util.Properties

@ExtendWith(MockitoExtension::class)
class MailServiceTest {

    @Mock
    private lateinit var mailSender: JavaMailSender

    @Mock
    private lateinit var templateEngine: TemplateEngine

    @Captor
    private lateinit var contextCaptor: ArgumentCaptor<Context>

    @Captor
    private lateinit var mimeMessageCaptor: ArgumentCaptor<MimeMessage>

    private lateinit var mailService: MailService

    private val fromAddress = "noreply@letstable.com"
    private val fromName = "Let's Table Team"

    @BeforeEach
    fun setUp() {
        mailService = MailService(
            mailSender = mailSender,
            templateEngine = templateEngine,
            fromAddress = fromAddress,
            fromName = fromName
        )
    }

    private fun createDummyMimeMessage(): MimeMessage {
        return MimeMessage(Session.getInstance(Properties()))
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
            val mimeMessage = createDummyMimeMessage()

            `when`(templateEngine.process(eq("emails/otp"), contextCaptor.capture()))
                .thenReturn(expectedHtml)
            `when`(mailSender.createMimeMessage()).thenReturn(mimeMessage)

            mailService.sendActivationOtp(recipient, otpCode, expiresInMinutes)

            verify(mailSender).send(mimeMessageCaptor.capture())

            val capturedContext = contextCaptor.value
            assertThat(capturedContext.getVariable("otpCode")).isEqualTo(otpCode)
            assertThat(capturedContext.getVariable("expiresInMinutes")).isEqualTo(expiresInMinutes)

            val sentMessage = mimeMessageCaptor.value
            assertThat(sentMessage.allRecipients[0].toString()).isEqualTo(recipient)
            assertThat(sentMessage.subject).isEqualTo("Your Let's Table activation code")
        }

        @Test
        fun `should propagate exception when template rendering fails`() {
            val recipient = "user@example.com"
            val otpCode = "123456"
            val expiresInMinutes = 10L

            `when`(templateEngine.process(eq("emails/otp"), any(Context::class.java)))
                .thenThrow(RuntimeException("Template processing error"))

            assertThatThrownBy {
                mailService.sendActivationOtp(recipient, otpCode, expiresInMinutes)
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("Template processing error")

            verify(mailSender, never()).send(any(MimeMessage::class.java))
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
            val mimeMessage = createDummyMimeMessage()

            `when`(templateEngine.process(eq("emails/password-reset"), contextCaptor.capture()))
                .thenReturn(expectedHtml)
            `when`(mailSender.createMimeMessage()).thenReturn(mimeMessage)

            mailService.sendPasswordResetOtp(recipient, otpCode, expiresInMinutes)

            verify(mailSender).send(mimeMessageCaptor.capture())

            val capturedContext = contextCaptor.value
            assertThat(capturedContext.getVariable("otpCode")).isEqualTo(otpCode)
            assertThat(capturedContext.getVariable("expiresInMinutes")).isEqualTo(expiresInMinutes)

            val sentMessage = mimeMessageCaptor.value
            assertThat(sentMessage.allRecipients[0].toString()).isEqualTo(recipient)
            assertThat(sentMessage.subject).isEqualTo("Reset your Let's Table password")
        }

        @Test
        fun `should rethrow exception when mail sender throws exception`() {
            val recipient = "user@example.com"
            val otpCode = "654321"
            val expiresInMinutes = 5L
            val mimeMessage = createDummyMimeMessage()

            `when`(templateEngine.process(eq("emails/password-reset"), any(Context::class.java)))
                .thenReturn("<html>Reset OTP</html>")
            `when`(mailSender.createMimeMessage()).thenReturn(mimeMessage)
            doThrow(RuntimeException("SMTP connection failed")).`when`(mailSender).send(mimeMessage)

            assertThatThrownBy {
                mailService.sendPasswordResetOtp(recipient, otpCode, expiresInMinutes)
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("SMTP connection failed")
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Values")
    inner class EdgeCasesTests {

        @ParameterizedTest
        @ValueSource(strings = ["", "000000", "999999999999"])
        fun `should handle different OTP code lengths and values`(otpCode: String) {
            val recipient = "test@domain.org"
            val mimeMessage = createDummyMimeMessage()

            `when`(templateEngine.process(eq("emails/otp"), contextCaptor.capture()))
                .thenReturn("<html>OTP</html>")
            `when`(mailSender.createMimeMessage()).thenReturn(mimeMessage)

            mailService.sendActivationOtp(recipient, otpCode, 15L)

            assertThat(contextCaptor.value.getVariable("otpCode")).isEqualTo(otpCode)
        }

        @ParameterizedTest
        @ValueSource(longs = [0L, 1L, Long.MAX_VALUE])
        fun `should handle edge cases for expiration time in minutes`(expiresInMinutes: Long) {
            val recipient = "test@domain.org"
            val mimeMessage = createDummyMimeMessage()

            `when`(templateEngine.process(eq("emails/otp"), contextCaptor.capture()))
                .thenReturn("<html>OTP</html>")
            `when`(mailSender.createMimeMessage()).thenReturn(mimeMessage)

            mailService.sendActivationOtp(recipient, "1234", expiresInMinutes)

            assertThat(contextCaptor.value.getVariable("expiresInMinutes")).isEqualTo(expiresInMinutes)
        }
    }
}