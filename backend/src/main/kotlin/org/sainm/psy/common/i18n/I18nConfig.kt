package org.sainm.psy.common.i18n

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import jakarta.servlet.http.HttpServletRequest
import java.util.Locale

@Configuration
class I18nConfig {

    @Bean
    fun localeResolver(): LocaleResolver = NormalizingAcceptHeaderLocaleResolver()

    /**
     * Accepts bare language tags (`zh`, `ja`, `en`) in addition to the regional
     * forms.  Spring's stock resolver only matches the exact supported locales,
     * so a plain `Accept-Language: ja` used to fall back to Chinese and
     * `zh` would have resolved to a non-existent `messages_zh` bundle (F-36 /
     * review finding #3).  Language tags are normalised to the canonical
     * zh-CN / ja-JP / en locale before the message bundle is resolved.
     */
    private class NormalizingAcceptHeaderLocaleResolver : AcceptHeaderLocaleResolver() {
        init {
            setDefaultLocale(Locale.SIMPLIFIED_CHINESE)
            setSupportedLocales(listOf(Locale.SIMPLIFIED_CHINESE, Locale.JAPAN, Locale.ENGLISH, Locale.US))
        }

        override fun resolveLocale(request: HttpServletRequest): Locale {
            val firstTag = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)
                ?.split(",")
                ?.firstOrNull()
                ?.substringBefore(";")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (firstTag != null) {
                when (Locale.forLanguageTag(firstTag).language.lowercase(Locale.ROOT)) {
                    "zh" -> return Locale.SIMPLIFIED_CHINESE
                    "ja" -> return Locale.JAPAN
                    "en" -> return Locale.ENGLISH
                }
            }
            return super.resolveLocale(request)
        }

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
