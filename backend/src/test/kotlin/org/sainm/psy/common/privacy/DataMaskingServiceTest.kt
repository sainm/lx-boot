package org.sainm.psy.common.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

class DataMaskingServiceTest {

    private val service = DataMaskingService()

    @Test
    fun `maskText masks structured personal identifiers`() {
        val masked = service.maskText(
            "mobile=13812345678 id=11010119900307456X email=student@example.com"
        )

        assertEquals(
            "mobile=138****5678 id=110101********456X email=s***@example.com",
            masked
        )
    }
}
