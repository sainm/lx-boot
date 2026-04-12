package org.sainm.psy.notification.api

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.sainm.psy.notification.domain.NotificationPolicy

data class NotificationPolicyResponse(
    val id: Long,
    val notificationType: String,
    val inAppEnabled: Boolean,
    val pushEnabled: Boolean,
    val cooldownMinutes: Int
) {
    companion object {
        fun from(policy: NotificationPolicy) = NotificationPolicyResponse(
            id = policy.id,
            notificationType = policy.notificationType,
            inAppEnabled = policy.inAppEnabled,
            pushEnabled = policy.pushEnabled,
            cooldownMinutes = policy.cooldownMinutes
        )
    }
}

data class UpdateNotificationPolicyRequest(
    @field:NotBlank(message = "notification.policy.type_required")
    @field:Size(max = 64, message = "notification.policy.type_too_long")
    val notificationType: String,

    val inAppEnabled: Boolean = true,
    val pushEnabled: Boolean = true,

    @field:Min(value = 0, message = "notification.policy.cooldown_invalid")
    val cooldownMinutes: Int = 0
)
