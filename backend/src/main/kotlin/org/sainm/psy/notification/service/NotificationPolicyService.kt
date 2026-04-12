package org.sainm.psy.notification.service

import org.sainm.psy.notification.domain.NotificationPolicy
import org.sainm.psy.notification.domain.NotificationPolicySnapshot
import org.sainm.psy.notification.repository.NotificationPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationPolicyService(
    private val notificationPolicyRepository: NotificationPolicyRepository
) {

    fun listPolicies(): List<NotificationPolicy> = notificationPolicyRepository.findAll()

    @Transactional
    fun upsertPolicy(
        notificationType: String,
        inAppEnabled: Boolean,
        pushEnabled: Boolean,
        cooldownMinutes: Int
    ): NotificationPolicy =
        notificationPolicyRepository.upsert(
            notificationType = notificationType,
            inAppEnabled = inAppEnabled,
            pushEnabled = pushEnabled,
            cooldownMinutes = cooldownMinutes
        )

    fun resolvePolicy(notificationType: String): NotificationPolicySnapshot {
        val policy = notificationPolicyRepository.findByType(notificationType)
        return NotificationPolicySnapshot(
            notificationType = notificationType,
            inAppEnabled = policy?.inAppEnabled ?: true,
            pushEnabled = policy?.pushEnabled ?: true,
            cooldownMinutes = policy?.cooldownMinutes ?: 0
        )
    }
}
