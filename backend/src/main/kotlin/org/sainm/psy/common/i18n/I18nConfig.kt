package org.sainm.psy.common.i18n

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale

@Configuration
class I18nConfig {

    @Bean
    fun localeResolver(): LocaleResolver =
        AcceptHeaderLocaleResolver().apply {
            setDefaultLocale(Locale.SIMPLIFIED_CHINESE)
            setSupportedLocales(listOf(Locale.SIMPLIFIED_CHINESE, Locale.JAPAN, Locale.US))
        }

    @Bean
    fun messageSource(): MessageSource =
        ReloadableResourceBundleMessageSource().apply {
            setBasename("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
            setUseCodeAsDefaultMessage(true)
        }
}
