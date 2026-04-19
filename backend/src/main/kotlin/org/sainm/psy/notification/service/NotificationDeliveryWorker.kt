package org.sainm.psy.notification.service

import org.sainm.psy.common.scheduler.SchedulerLockService
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.notification.repository.NotificationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class NotificationDeliveryWorker(
    private val notificationRepository: NotificationRepository,
    private val pushDeliveryGateway: PushDeliveryGateway,
    private val schedulerLockService: SchedulerLockService? = null,
    private val psyMetrics: PsyMetrics? = null,
    @Value("\${psy.notification.delivery-batch-size:100}")
    private val deliveryBatchSize: Int
) {

    @Transactional
    @Scheduled(fixedDelayString = "\${psy.notification.delivery-scan-delay-ms:60000}")
    fun processPendingPushDeliveries(): Int {
        val lock = schedulerLockService ?: return processPendingPushDeliveriesUnlocked()
        val jobName = "notification.push-delivery"
        val result = lock.withLock("notification:push-delivery", Duration.ofMinutes(2)) {
            psyMetrics?.recordSchedulerRun(jobName) { processPendingPushDeliveriesUnlocked() }
                ?: processPendingPushDeliveriesUnlocked()
        }
        if (result == null) {
            psyMetrics?.recordSchedulerSkipped(jobName)
        }
        return result ?: 0
    }

    private fun processPendingPushDeliveriesUnlocked(): Int {
        val deliveries = notificationRepository.findPendingPushDeliveries(deliveryBatchSize.coerceIn(1, 500))
        var processed = 0
        deliveries.forEach { delivery ->
            if (!notificationRepository.markDeliveryProcessing(delivery.id)) {
                return@forEach
            }
            val result = pushDeliveryGateway.send(delivery)
            if (result.success) {
                notificationRepository.markDeliverySent(
                    deliveryId = delivery.id,
                    providerName = result.providerName,
                    providerMessageId = result.providerMessageId
                )
            } else {
                notificationRepository.markDeliveryFailed(delivery.id, result.errorMessage ?: "PUSH_DELIVERY_FAILED")
            }
            processed += 1
        }
        return processed
    }
}
