package org.sainm.psy.notification.service

import org.sainm.psy.notification.domain.PendingPushDelivery

data class PushDeliveryAttemptResult(
    val success: Boolean,
    val errorMessage: String? = null
)

interface PushDeliveryGateway {
    fun send(delivery: PendingPushDelivery): PushDeliveryAttemptResult
}
