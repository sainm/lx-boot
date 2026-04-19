package org.sainm.psy.notification.service

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class PushDeliveryGatewaySelector(
    private val simulatedGatewayProvider: ObjectProvider<SimulatedPushDeliveryGateway>,
    private val httpGatewayProvider: ObjectProvider<HttpPushDeliveryGateway>,
    private val fcmGatewayProvider: ObjectProvider<FcmPushDeliveryGateway>
) : PushDeliveryGateway {

    private lateinit var delegate: PushDeliveryGateway

    @PostConstruct
    fun initialize() {
        val candidates = listOfNotNull(
            fcmGatewayProvider.ifAvailable,
            httpGatewayProvider.ifAvailable,
            simulatedGatewayProvider.ifAvailable
        )
        val enabledRealGateways = listOfNotNull(
            fcmGatewayProvider.ifAvailable,
            httpGatewayProvider.ifAvailable
        )
        require(enabledRealGateways.size <= 1) {
            "Only one real push gateway can be enabled at a time: FCM or HTTP"
        }
        delegate = candidates.firstOrNull()
            ?: error("No PushDeliveryGateway implementation is available")
    }

    override fun send(delivery: org.sainm.psy.notification.domain.PendingPushDelivery): PushDeliveryAttemptResult =
        delegate.send(delivery)
}
