package org.sainm.psy.notification.api

import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.notification.domain.UserDeviceSummary
import org.sainm.psy.notification.domain.MyNotificationSummary
import org.sainm.psy.notification.domain.NotificationActionResult
import org.sainm.psy.notification.service.NotificationService
import org.sainm.psy.notification.service.UserDeviceService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
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
    private val notificationService: NotificationService,
    private val userDeviceService: UserDeviceService
) {

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun findMyNotifications(): ApiResponse<List<MyNotificationSummary>> =
        ApiResponse.ok(notificationService.findMyNotifications())

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    fun markAsRead(@PathVariable id: Long): ApiResponse<NotificationActionResult> =
        ApiResponse.ok(notificationService.markAsRead(id))

    @GetMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    fun findMyDevices(): ApiResponse<List<UserDeviceSummary>> =
        ApiResponse.ok(userDeviceService.findMyDevices())

    @PostMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    fun registerMyDevice(@Valid @RequestBody request: RegisterDeviceRequest): ApiResponse<UserDeviceSummary> =
        ApiResponse.ok(userDeviceService.registerMyDevice(request))

    @DeleteMapping("/devices/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    fun deactivateMyDevice(@PathVariable deviceId: String): ApiResponse<UserDeviceSummary> =
        ApiResponse.ok(userDeviceService.deactivateMyDevice(deviceId))
}
