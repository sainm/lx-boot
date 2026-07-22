package org.sainm.psy.notification.config

import org.sainm.auth.core.spi.MailSenderService
import org.sainm.psy.notification.service.SmtpMailSenderService
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.mail.MailProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

/**
 * Overrides auth-starter's no-op [MailSenderService] with the SMTP
 * implementation whenever Spring Boot's mail auto-configuration is active
 * (i.e. `spring.mail.host` is set).
 */
@Configuration
class MailSenderConfiguration {

    @Bean
    @ConditionalOnBean(JavaMailSender::class)
    @ConditionalOnProperty("spring.mail.host")
    fun mailSenderService(
        javaMailSender: JavaMailSender,
        mailProperties: MailProperties
    ): MailSenderService =
        SmtpMailSenderService(javaMailSender, mailProperties)
}
