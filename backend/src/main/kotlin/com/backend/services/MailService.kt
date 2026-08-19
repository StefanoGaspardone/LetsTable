package com.backend.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Service
class MailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: TemplateEngine,
    @Value($$"${mail.from-address}") private val fromAddress: String,
    @Value($$"${mail.from-name}") private val fromName: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    fun sendActivationOtp(to: String, otpCode: String, expiresInMinutes: Long) {
        val context = Context().apply {
            setVariable("otpCode", otpCode)
            setVariable("expiresInMinutes", expiresInMinutes)
        }

        send(to = to, subject = "Your Let's Table activation code", template = "emails/otp", context = context)
    }

    @Async
    fun sendPasswordResetOtp(to: String, otpCode: String, expiresInMinutes: Long) {
        val context = Context().apply {
            setVariable("otpCode", otpCode)
            setVariable("expiresInMinutes", expiresInMinutes)
        }

        send(to = to, subject = "Reset your Let's Table password", template = "emails/password-reset", context = context)
    }

    private fun send(to: String, subject: String, template: String, context: Context) {
        logger.debug("\n\t[DEBUG] [mail_service][send] Sending email\n\tto={}\n\ttemplate={}", to, template)

        try {
            val htmlBody = templateEngine.process(template, context)

            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, false, "UTF-8")

            helper.setTo(to)
            helper.setSubject(subject)
            helper.setFrom(fromAddress, fromName)
            helper.setText(htmlBody, true)

            mailSender.send(message)

            logger.info("\n\t[INFO] [mail_service][send] Email sent\n\tto={}\n\ttemplate={}", to, template)
        } catch (e: Exception) {
            logger.error("\n\t[ERROR] [mail_service][send] Failed to send email\n\tto={}\n\ttemplate={}\n\terror={}", to, template, e.message)
            throw e
        }
    }
}