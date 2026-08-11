package org.sainm.psy.common.i18n

import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.context.i18n.LocaleContextHolder
import java.util.Locale

@Service
class LocalizedMessages(
    private val messageSource: MessageSource
) {

    fun get(key: String, vararg args: Any?): String =
        messageSource.getMessage(key, args, locale())

    fun getForLocale(localeCode: String?, key: String, vararg args: Any?): String =
        messageSource.getMessage(key, args, SupportedContentLocale.toLocale(localeCode))

    private fun locale(): Locale = LocaleContextHolder.getLocale()
}
