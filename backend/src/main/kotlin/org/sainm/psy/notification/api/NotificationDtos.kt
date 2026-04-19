package org.sainm.psy.notification.api

import org.sainm.psy.notification.domain.UserDeviceSummary
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class MyNotificationListQuery(
    val page: Int = 1,
    val size: Int = 50
)

data class RegisterDeviceRequest(
    @field:NotBlank(message = "notification.device_type_required")
    @field:Size(max = 32, message = "notification.device_type_too_long")
    val deviceType: String,

    @field:Size(max = 128, message = "notification.device_id_too_long")
    val deviceId: String? = null,

    @field:Size(max = 512, message = "notification.push_token_too_long")
    val pushToken: String? = null,

    @field:Size(max = 64, message = "notification.app_version_too_long")
    val appVersion: String? = null
)

data class ReportPushDeliveryReceiptRequest(
    val occurredAt: LocalDateTime? = null
)

data class ReportPushDeliveryCallbackRequest(
    @field:NotBlank(message = "notification.delivery_status_required")
    @field:Size(max = 32, message = "notification.delivery_status_too_long")
    val deliveryStatus: String,

    @field:Size(max = 64, message = "notification.provider_name_too_long")
    val providerName: String? = null,

    @field:Size(max = 255, message = "notification.provider_message_id_too_long")
    val providerMessageId: String? = null,

    @field:Size(max = 2000, message = "notification.error_message_too_long")
    val errorMessage: String? = null,

    @field:Size(max = 20000, message = "notification.callback_payload_too_long")
    val callbackPayloadJson: String? = null,

    val deliveredAt: LocalDateTime? = null,
    val clickedAt: LocalDateTime? = null,
    val readAt: LocalDateTime? = null
)

data class UserDeviceDeactivationResponse(
    val device: UserDeviceSummaryResponse,
    val revokedSessionCount: Int
) {
    companion object {
        fun from(result: org.sainm.psy.notification.domain.UserDeviceDeactivationResult) =
            UserDeviceDeactivationResponse(
                device = UserDeviceSummaryResponse.from(result.device),
                revokedSessionCount = result.revokedSessionCount
            )
    }
}

data class UserDeviceSummaryResponse(
    val id: Long,
    val deviceType: String,
    val deviceId: String,
    val pushTokenMasked: String?,
    val appVersion: String?,
    val activeFlag: Boolean,
    val authSessionId: String?,
    val authSessionStatus: String?,
    val authSessionLastSeenAt: LocalDateTime?,
    val deviceTrustLevel: String,
    val riskSignals: List<String>,
    val riskLevel: String,
    val autoDisposition: String,
    val autoDispositionReason: String?,
    val lastActiveAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(summary: UserDeviceSummary) =
            UserDeviceSummaryResponse(
                id = summary.id,
                deviceType = summary.deviceType,
                deviceId = summary.deviceId,
                pushTokenMasked = summary.pushTokenMasked,
                appVersion = summary.appVersion,
                activeFlag = summary.activeFlag,
                authSessionId = summary.authSessionId,
                authSessionStatus = summary.authSessionStatus,
                authSessionLastSeenAt = summary.authSessionLastSeenAt,
                deviceTrustLevel = summary.deviceTrustLevel,
                riskSignals = summary.riskSignals,
                riskLevel = summary.riskLevel,
                autoDisposition = summary.autoDisposition,
                autoDispositionReason = summary.autoDispositionReason,
                lastActiveAt = summary.lastActiveAt,
                createdAt = summary.createdAt,
                updatedAt = summary.updatedAt
            )
    }
}
