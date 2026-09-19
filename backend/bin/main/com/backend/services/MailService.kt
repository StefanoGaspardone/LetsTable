package com.backend.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Service
class MailService(
    private val brevoWebClient: WebClient,
    private val templateEngine: TemplateEngine,
    @Value($$"${mail.from-address}") private val fromAddress: String,
    @Value($$"${mail.from-name}") private val fromName: String,
    @Value($$"${brevo.api-key}") private val brevoApiKey: String,
    @Value($$"${mail.enabled:true}") private val mailEnabled: Boolean,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    fun sendActivationOtp(to: String, otpCode: String, expiresInMinutes: Long) {
        val context = Context().apply {
            setVariable("otpCode", otpCode)
            setVariable("expiresInMinutes", expiresInMinutes)
        }

        send(to = to, subject = "Codice di attivazione - Let's Table", template = "emails/otp", context = context)
    }

    @Async
    fun sendPasswordResetOtp(to: String, otpCode: String, expiresInMinutes: Long) {
        val context = Context().apply {
            setVariable("otpCode", otpCode)
            setVariable("expiresInMinutes", expiresInMinutes)
        }

        send(to = to, subject = "Reimposta la tua password - Let's Table", template = "emails/password-reset", context = context)
    }

    private fun send(to: String, subject: String, template: String, context: Context) {
        logger.debug("\n\t[DEBUG] [mail_service][send] Sending email\n\tto={}\n\ttemplate={}", to, template)

        try {
            val htmlBody = templateEngine.process(template, context)

            if(!mailEnabled) {
                logger.info("\n\t[INFO] [mail_service][send] Mail sending disabled, skipping\n\tto={}\n\tsubject={}\n\tbody={}", to, subject, htmlBody)
                return
            }

            val payload = mapOf(
                "sender" to mapOf("name" to fromName, "email" to fromAddress),
                "to" to listOf(mapOf("email" to to)),
                "subject" to subject,
                "htmlContent" to htmlBody,
            )

            brevoWebClient.post()
                .uri("/smtp/email")
                .header("api-key", brevoApiKey)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block()

            logger.info("\n\t[INFO] [mail_service][send] Email sent\n\tto={}\n\ttemplate={}", to, template)
        } catch (e: Exception) {
            logger.error("\n\t[ERROR] [mail_service][send] Failed to send email\n\tto={}\n\ttemplate={}\n\terror={}", to, template, e.message)
            throw e
        }
    }
}