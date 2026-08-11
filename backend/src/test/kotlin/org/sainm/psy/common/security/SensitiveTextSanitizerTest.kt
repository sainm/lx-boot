package org.sainm.psy.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SensitiveTextSanitizerTest {
    @Test
    fun `redact removes quoted plain and bearer credentials`() {
        val source = """{"token":"secret-token","api_key":"key-1"} password=hunter2 Authorization: Bearer abc.def"""

        assertEquals(
            """{"token":"[REDACTED]","api_key":"[REDACTED]"} password=[REDACTED] Authorization: [REDACTED]""",
            SensitiveTextSanitizer.redact(source, 500)
        )
    }

    @Test
    fun `redact enforces storage length after sanitizing`() {
        assertEquals("token=[", SensitiveTextSanitizer.redact("token=secret-value", 7))
        assertEquals(null, SensitiveTextSanitizer.redact(null, 20))
    }
}
