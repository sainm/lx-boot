package org.sainm.psy.notification.service

import org.sainm.auth.core.spi.MailSenderService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.mail.MailProperties
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.sainm.psy.common.i18n.LocalizedMessages
import org.springframework.web.util.HtmlUtils

/**
 * [MailSenderService] backed by Spring Boot's [JavaMailSender] (SMTP).
 * Activated by the standard spring.mail.* properties.
 */
class SmtpMailSenderService(
    private val javaMailSender: JavaMailSender,
    private val mailProperties: MailProperties,
    private val messages: LocalizedMessages
) : MailSenderService {

    private val log = LoggerFactory.getLogger(SmtpMailSenderService::class.java)

    override fun send(to: String, subject: String, bodyHtml: String) {
        try {
            val localized = localizeActivationEmail(subject, bodyHtml)
            javaMailSender.send { mimeMessage ->
                val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")
                helper.setFrom(mailProperties.properties["from"] as? String ?: mailProperties.username ?: "noreply@psy-backend.local")
                helper.setTo(to)
                helper.setSubject(localized.first)
                helper.setText(localized.second, true)
            }
        } catch (ex: Exception) {
            log.error("Failed to send email to [{}]: {}", to, ex.message, ex)
            // Don't propagate — email failure should not block registration.
        }
    }

    private fun localizeActivationEmail(subject: String, bodyHtml: String): Pair<String, String> {
        if (!bodyHtml.contains("/auth/email-verify?token=")) return subject to bodyHtml
        val link = Regex("""href="([^"]+)"""").find(bodyHtml)?.groupValues?.get(1)
            ?: return subject to bodyHtml
        return messages.get("external_registration.activation.subject") to messages.get(
            "external_registration.activation.body_html",
            HtmlUtils.htmlEscape(link)
        )
    }
}
