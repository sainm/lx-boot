package org.sainm.psy.notification.service

import org.sainm.auth.core.spi.MailSenderService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.mail.MailProperties
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

/**
 * [MailSenderService] backed by Spring Boot's [JavaMailSender] (SMTP).
 * Activated by the standard spring.mail.* properties.
 */
class SmtpMailSenderService(
    private val javaMailSender: JavaMailSender,
    private val mailProperties: MailProperties
) : MailSenderService {

    private val log = LoggerFactory.getLogger(SmtpMailSenderService::class.java)

    override fun send(to: String, subject: String, bodyHtml: String) {
        try {
            javaMailSender.send { mimeMessage ->
                val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")
                helper.setFrom(mailProperties.properties["from"] as? String ?: mailProperties.username ?: "noreply@psy-backend.local")
                helper.setTo(to)
                helper.setSubject(subject)
                helper.setText(bodyHtml, true)
            }
        } catch (ex: Exception) {
            log.error("Failed to send email to [{}]: {}", to, ex.message, ex)
            // Don't propagate — email failure should not block registration.
        }
    }
}
