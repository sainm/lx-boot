package org.sainm.psy.notification.api

import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.notification.domain.MyNotificationSummary
import org.sainm.psy.notification.domain.NotificationActionResult
import org.sainm.psy.notification.domain.NotificationDeliveryReceiptResult
import org.sainm.psy.notification.service.NotificationService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/my/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun findMyNotifications(): ApiResponse<List<MyNotificationSummary>> =
        ApiResponse.ok(notificationService.findMyNotifications())

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    fun markAsRead(@PathVariable id: Long): ApiResponse<NotificationActionResult> =
        ApiResponse.ok(notificationService.markAsRead(id))

    @PostMapping("/deliveries/{deliveryId}/received")
    @PreAuthorize("isAuthenticated()")
    fun reportPushDeliveryReceived(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody(required = false) request: ReportPushDeliveryReceiptRequest?
    ): ApiResponse<NotificationDeliveryReceiptResult> =
        ApiResponse.ok(notificationService.reportPushDeliveryReceived(deliveryId, request?.occurredAt))

    @PostMapping("/deliveries/{deliveryId}/clicked")
    @PreAuthorize("isAuthenticated()")
    fun reportPushDeliveryClicked(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody(required = false) request: ReportPushDeliveryReceiptRequest?
    ): ApiResponse<NotificationDeliveryReceiptResult> =
        ApiResponse.ok(notificationService.reportPushDeliveryClicked(deliveryId, request?.occurredAt))

}
