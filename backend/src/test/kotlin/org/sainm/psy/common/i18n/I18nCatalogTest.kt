package org.sainm.psy.common.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

class I18nCatalogTest {
    private val placeholderRegex = Regex("\\{(\\d+)}")

    @Test
    fun `localized catalogs contain the same keys and parameters as English`() {
        val english = loadCatalog("i18n/messages.properties")

        listOf("i18n/messages_zh_CN.properties", "i18n/messages_ja_JP.properties").forEach { resource ->
            val localized = loadCatalog(resource)
            assertEquals(english.stringPropertyNames(), localized.stringPropertyNames(), "$resource keys")

            english.stringPropertyNames().forEach { key ->
                assertEquals(
                    placeholders(english.getProperty(key)),
                    placeholders(localized.getProperty(key)),
                    "$resource:$key parameters"
                )
            }
        }
    }

    private fun loadCatalog(resource: String): Properties =
        Properties().apply {
            val stream = checkNotNull(I18nCatalogTest::class.java.classLoader.getResourceAsStream(resource)) {
                "Missing resource: $resource"
            }
            stream.use { load(InputStreamReader(it, StandardCharsets.UTF_8)) }
        }

    private fun placeholders(value: String): List<String> =
        placeholderRegex.findAll(value).map { it.groupValues[1] }.sorted().toList()
}
