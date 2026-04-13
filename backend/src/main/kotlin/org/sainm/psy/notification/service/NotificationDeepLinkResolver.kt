package org.sainm.psy.notification.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class NotificationDeepLinkResolver(
    @Value("\${psy.notification.deep-link.app-scheme:}")
    private val appScheme: String,
    @Value("\${psy.notification.deep-link.universal-link-base-url:}")
    private val universalLinkBaseUrl: String
) {

    fun resolve(targetPath: String?): String? {
        val path = targetPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val scheme = appScheme.trim()
        if (scheme.isNotEmpty()) {
            val prefix = if (scheme.contains("://")) scheme.trimEnd('/') else "$scheme://"
            return prefix + path.removePrefix("/")
        }

        val baseUrl = universalLinkBaseUrl.trim().trimEnd('/')
        if (baseUrl.isNotEmpty()) {
            return "$baseUrl/${path.removePrefix("/")}"
        }

        return path
    }
}
