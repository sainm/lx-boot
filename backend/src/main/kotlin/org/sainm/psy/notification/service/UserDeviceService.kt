package org.sainm.psy.notification.service

import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.api.RegisterDeviceRequest
import org.sainm.psy.notification.domain.UserDeviceSummary
import org.sainm.psy.notification.repository.UserDeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserDeviceService(
    private val userDeviceRepository: UserDeviceRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages
) {

    fun findMyDevices(): List<UserDeviceSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return userDeviceRepository.findByUser(currentUser.userId)
    }

    @Transactional
    fun registerMyDevice(request: RegisterDeviceRequest): UserDeviceSummary {
        val currentUser = currentUserFacade.requireCurrentUser()
        return userDeviceRepository.upsertDevice(
            userId = currentUser.userId,
            deviceType = request.deviceType.trim().uppercase(),
            deviceId = request.deviceId.trim(),
            pushToken = request.pushToken?.trim()?.ifBlank { null },
            appVersion = request.appVersion?.trim()?.ifBlank { null }
        )
    }

    @Transactional
    fun deactivateMyDevice(deviceId: String): UserDeviceSummary {
        val currentUser = currentUserFacade.requireCurrentUser()
        val normalizedDeviceId = deviceId.trim()
        val updated = userDeviceRepository.deactivate(currentUser.userId, normalizedDeviceId)
        if (!updated) {
            throw BizException("NOTIFICATION_DEVICE_NOT_FOUND", messages.get("notification.device_not_found"))
        }
        return userDeviceRepository.findByUser(currentUser.userId)
            .firstOrNull { it.deviceId == normalizedDeviceId }
            ?: throw BizException("NOTIFICATION_DEVICE_NOT_FOUND", messages.get("notification.device_not_found"))
    }
}
