package org.sainm.psy.common.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class SupportedContentLocaleTest {
    @Test
    fun `regional request locales map to canonical ScalePackage locale codes`() {
        assertEquals("zh-CN", SupportedContentLocale.from(Locale.SIMPLIFIED_CHINESE))
        assertEquals("zh-CN", SupportedContentLocale.from(Locale.TRADITIONAL_CHINESE))
        assertEquals("ja-JP", SupportedContentLocale.from(Locale.JAPAN))
        assertEquals("en", SupportedContentLocale.from(Locale.US))
        assertEquals("en", SupportedContentLocale.from(Locale.UK))
        assertEquals("en", SupportedContentLocale.from(Locale.FRENCH))
    }

    @Test
    fun `canonical content locale resolves the matching regional message bundle locale`() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, SupportedContentLocale.toLocale("zh-CN"))
        assertEquals(Locale.JAPAN, SupportedContentLocale.toLocale("ja-JP"))
        assertEquals(Locale.ENGLISH, SupportedContentLocale.toLocale("en"))
    }
}
