package org.sainm.psy.notification.service

import org.sainm.psy.common.scheduler.SchedulerLockService
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.notification.repository.NotificationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.min

@Service
class NotificationDeliveryWorker(
    private val notificationRepository: NotificationRepository,
    private val pushDeliveryGateway: PushDeliveryGateway,
    private val schedulerLockService: SchedulerLockService? = null,
    private val psyMetrics: PsyMetrics? = null,
    @Value("\${psy.notification.delivery-batch-size:100}")
    private val deliveryBatchSize: Int,
    @Value("\${psy.notification.max-attempts:5}")
    private val maxAttempts: Int = 5,
    @Value("\${psy.notification.initial-retry-delay-seconds:60}")
    private val initialRetryDelaySeconds: Long = 60,
    @Value("\${psy.notification.max-retry-delay-seconds:3600}")
    private val maxRetryDelaySeconds: Long = 3600,
    @Value("\${psy.notification.processing-timeout-minutes:10}")
    private val processingTimeoutMinutes: Long = 10,
    @Value("\${psy.notification.processing-timeout-seconds:0}")
    private val processingTimeoutSeconds: Long = 0,
    private val clock: Clock = Clock.systemDefaultZone()
) {

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
        val now = LocalDateTime.now(clock)
        val recovered = notificationRepository.recoverStaleProcessingDeliveries(
            cutoff = now.minus(processingTimeout()),
            now = now,
            maxAttempts = maxAttempts
        )
        psyMetrics?.recordRecoveredNotificationDeliveries(recovered)
        val deliveries = notificationRepository.findPendingPushDeliveries(
            deliveryBatchSize.coerceIn(1, 500),
            now
        )
        var processed = 0
        deliveries.forEach { delivery ->
            val processingToken = notificationRepository.markDeliveryProcessing(delivery.id, now)
            if (processingToken == null) {
                return@forEach
            }
            val result = runCatching { pushDeliveryGateway.send(delivery) }
                .getOrElse {
                    PushDeliveryAttemptResult(
                        success = false,
                        errorMessage = it.javaClass.simpleName.ifBlank { "PUSH_DELIVERY_EXCEPTION" }
                    )
                }
            if (result.success) {
                val completed = notificationRepository.markDeliverySent(
                    deliveryId = delivery.id,
                    processingToken = processingToken,
                    providerName = result.providerName,
                    providerMessageId = result.providerMessageId
                )
                psyMetrics?.recordNotificationDeliveryAttempt(if (completed) "sent" else "claim_lost")
            } else {
                val retryNumber = delivery.retryCount + 1
                val nextRetryAt = if (retryNumber >= maxAttempts.coerceAtLeast(1)) {
                    null
                } else {
                    now.plusSeconds(retryDelaySeconds(delivery.retryCount))
                }
                val failureStatus = notificationRepository.markDeliveryAttemptFailed(
                    deliveryId = delivery.id,
                    processingToken = processingToken,
                    previousRetryCount = delivery.retryCount,
                    maxAttempts = maxAttempts,
                    nextRetryAt = nextRetryAt,
                    errorMessage = sanitizeError(result.errorMessage),
                    now = now
                )
                psyMetrics?.recordNotificationDeliveryAttempt(
                    when (failureStatus) {
                        "DEAD_LETTER" -> "dead_letter"
                        "PENDING" -> "retry_scheduled"
                        else -> "claim_lost"
                    }
                )
            }
            processed += 1
        }
        recordQueueState(now)
        return processed
    }

    private fun recordQueueState(now: LocalDateTime) {
        val metrics = psyMetrics ?: return
        val summary = notificationRepository.findDeliveryOpsSummary()
        val oldestPendingSeconds = summary.oldestPendingCreatedAt
            ?.let { Duration.between(it, now).seconds.coerceAtLeast(0) }
            ?: 0
        metrics.recordNotificationQueueState(
            pending = summary.totalPending,
            processing = summary.totalProcessing,
            failed = summary.totalFailed,
            oldestPendingSeconds = oldestPendingSeconds
        )
    }

    private fun retryDelaySeconds(previousRetryCount: Int): Long {
        val exponent = previousRetryCount.coerceIn(0, 20)
        val multiplier = 1L shl exponent
        return min(
            maxRetryDelaySeconds.coerceAtLeast(1),
            initialRetryDelaySeconds.coerceAtLeast(1) * multiplier
        )
    }

    private fun processingTimeout(): Duration =
        if (processingTimeoutSeconds > 0) {
            Duration.ofSeconds(processingTimeoutSeconds.coerceAtLeast(1))
        } else {
            Duration.ofMinutes(processingTimeoutMinutes.coerceAtLeast(1))
        }

    private fun sanitizeError(errorMessage: String?): String = errorMessage
        ?.replace(Regex("(?i)(bearer|token|password|secret|credential)\\s*[:=]?\\s*[^\\s,;]+"), "\$1=[REDACTED]")
        ?.take(500)
        ?.ifBlank { "PUSH_DELIVERY_FAILED" }
        ?: "PUSH_DELIVERY_FAILED"
}
