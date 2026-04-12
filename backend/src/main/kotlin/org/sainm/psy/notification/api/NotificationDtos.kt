package org.sainm.psy.notification.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class MyNotificationListQuery(
    val page: Int = 1,
    val size: Int = 50
)

data class RegisterDeviceRequest(
    @field:NotBlank(message = "notification.device_type_required")
    @field:Size(max = 32, message = "notification.device_type_too_long")
    val deviceType: String,

    @field:NotBlank(message = "notification.device_id_required")
    @field:Size(max = 128, message = "notification.device_id_too_long")
    val deviceId: String,

    @field:Size(max = 512, message = "notification.push_token_too_long")
    val pushToken: String? = null,

    @field:Size(max = 64, message = "notification.app_version_too_long")
    val appVersion: String? = null
)
