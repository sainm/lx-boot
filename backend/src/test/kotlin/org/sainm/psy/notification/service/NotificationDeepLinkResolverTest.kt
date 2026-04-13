package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationDeepLinkResolverTest {

    @Test
    fun `resolve keeps web path when mobile configuration is empty`() {
        val resolver = NotificationDeepLinkResolver(appScheme = "", universalLinkBaseUrl = "")

        assertEquals("/my/tasks/12?source=push", resolver.resolve("/my/tasks/12?source=push"))
    }

    @Test
    fun `resolve builds app scheme link when app scheme is configured`() {
        val resolver = NotificationDeepLinkResolver(appScheme = "psy", universalLinkBaseUrl = "https://app.example.test")

        assertEquals("psy://reports/3?taskId=9", resolver.resolve("/reports/3?taskId=9"))
    }

    @Test
    fun `resolve builds universal link when scheme is not configured`() {
        val resolver = NotificationDeepLinkResolver(appScheme = "", universalLinkBaseUrl = "https://app.example.test/app/")

        assertEquals("https://app.example.test/app/warnings", resolver.resolve("/warnings"))
    }

    @Test
    fun `resolve returns null for blank path`() {
        val resolver = NotificationDeepLinkResolver(appScheme = "psy", universalLinkBaseUrl = "")

        assertNull(resolver.resolve(" "))
    }
}
