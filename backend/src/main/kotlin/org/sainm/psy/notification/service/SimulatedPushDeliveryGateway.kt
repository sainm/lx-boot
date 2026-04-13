package org.sainm.psy.notification.service

import org.sainm.psy.notification.domain.PendingPushDelivery
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(PushDeliveryGateway::class)
class SimulatedPushDeliveryGateway : PushDeliveryGateway {

    override fun send(delivery: PendingPushDelivery): PushDeliveryAttemptResult {
        val token = delivery.pushTokenSnapshot?.trim()
        if (token.isNullOrEmpty()) {
            return PushDeliveryAttemptResult(success = false, errorMessage = "PUSH_TOKEN_MISSING")
        }
        // First version only advances delivery state; a real vendor push/FCM adapter can replace this bean later.
        return PushDeliveryAttemptResult(success = true)
    }
}
