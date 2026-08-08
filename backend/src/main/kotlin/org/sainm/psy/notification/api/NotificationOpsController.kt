package org.sainm.psy.notification.api

import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.notification.domain.NotificationDeliveryRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryReceiptResult
import org.sainm.psy.notification.domain.NotificationDeliveryOpsSummary
import org.sainm.psy.notification.domain.NotificationDeliverySummary
import org.sainm.psy.notification.service.NotificationOpsService
import org.sainm.psy.notification.service.NotificationPolicyService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody
import jakarta.validation.Valid

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationOpsController(
    private val notificationOpsService: NotificationOpsService,
    private val notificationPolicyService: NotificationPolicyService
) {

    @GetMapping("/ops/feed")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findAdminNotifications(query: NotificationOpsListQuery): ApiResponse<List<AdminNotificationOpsItemResponse>> =
        ApiResponse.ok(
            notificationOpsService.findAdminNotifications(
                notificationType = query.notificationType,
                bizType = query.bizType,
                deliveryStatus = query.deliveryStatus,
                limit = query.limit
            ).map(AdminNotificationOpsItemResponse::from)
        )

    @GetMapping("/deliveries/summary")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findDeliveryOpsSummary(): ApiResponse<NotificationDeliveryOpsSummary> =
        ApiResponse.ok(notificationOpsService.findDeliveryOpsSummary())

    @GetMapping("/{id}/deliveries")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findDeliveries(@PathVariable id: Long): ApiResponse<List<NotificationDeliverySummary>> =
        ApiResponse.ok(notificationOpsService.findDeliveries(id))

    @PostMapping("/{id}/deliveries/retry")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun retryFailedDeliveries(
        @PathVariable id: Long,
        @RequestParam(required = false) deliveryChannel: String?
    ): ApiResponse<NotificationDeliveryRetryResult> =
        ApiResponse.ok(notificationOpsService.retryFailedDeliveries(id, deliveryChannel))

    @PostMapping("/deliveries/retry-batch")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun retryFailedDeliveriesBatch(
        @Valid @RequestBody request: BatchRetryNotificationDeliveriesRequest
    ): ApiResponse<NotificationBatchRetryResultResponse> =
        ApiResponse.ok(
            NotificationBatchRetryResultResponse.from(
                notificationOpsService.retryFailedDeliveriesBatch(
                    notificationIds = request.notificationIds,
                    deliveryChannel = request.deliveryChannel
                )
            )
        )

    @PostMapping("/deliveries/{deliveryId}/callbacks")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun applyPushDeliveryCallback(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody request: ReportPushDeliveryCallbackRequest
    ): ApiResponse<NotificationDeliveryReceiptResult> =
        ApiResponse.ok(
            notificationOpsService.applyPushDeliveryCallback(
                deliveryId = deliveryId,
                deliveryStatus = request.deliveryStatus,
                providerName = request.providerName,
                providerMessageId = request.providerMessageId,
                errorMessage = request.errorMessage,
                callbackPayloadJson = request.callbackPayloadJson,
                deliveredAt = request.deliveredAt,
                clickedAt = request.clickedAt,
                readAt = request.readAt
            )
        )

    @GetMapping("/policies")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun listPolicies(): ApiResponse<List<NotificationPolicyResponse>> =
        ApiResponse.ok(notificationPolicyService.listPolicies().map(NotificationPolicyResponse::from))

    @PostMapping("/policies")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun upsertPolicy(@Valid @RequestBody request: UpdateNotificationPolicyRequest): ApiResponse<NotificationPolicyResponse> =
        ApiResponse.ok(
            NotificationPolicyResponse.from(
                notificationPolicyService.upsertPolicy(
                    notificationType = request.notificationType.trim().uppercase(),
                    inAppEnabled = request.inAppEnabled,
                    pushEnabled = request.pushEnabled,
                    cooldownMinutes = request.cooldownMinutes
                )
            )
        )
}
