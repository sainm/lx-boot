package org.sainm.psy.common.i18n

import org.springframework.context.i18n.LocaleContextHolder
import java.util.Locale

/**
 * Canonical locale codes used by versioned ScalePackage content.
 *
 * System message bundles accept regional variants such as en-US, while the
 * persisted content contract intentionally uses only zh-CN, ja-JP, and en.
 */
object SupportedContentLocale {
    fun currentCode(): String = from(LocaleContextHolder.getLocale())

    fun toLocale(localeCode: String?): Locale = when (localeCode) {
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        "ja-JP" -> Locale.JAPAN
        else -> Locale.ENGLISH
    }

    fun from(locale: Locale): String = when (locale.language.lowercase(Locale.ROOT)) {
        Locale.CHINESE.language -> "zh-CN"
        Locale.JAPANESE.language -> "ja-JP"
        else -> "en"
    }
}
