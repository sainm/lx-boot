package org.sainm.psy.notification.domain

import java.time.LocalDateTime

data class MyNotificationSummary(
    val id: Long,
    val notificationType: String,
    val title: String,
    val content: String,
    val bizType: String?,
    val bizId: Long?,
    val targetPath: String?,
    val readFlag: Boolean,
    val readTime: LocalDateTime?,
    val createdAt: LocalDateTime
)

data class NotificationActionResult(
    val notificationId: Long,
    val readFlag: Boolean
)
