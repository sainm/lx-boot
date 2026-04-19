package org.sainm.psy.notification.service

import org.sainm.auth.core.device.DeviceGovernanceRiskPolicy
import org.sainm.auth.core.device.DeviceProfileSupport
import org.sainm.auth.core.spi.SessionManagementService
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.auth.autoconfigure.properties.AuthModuleProperties
import org.sainm.auth.autoconfigure.properties.DeviceGovernanceSignalPoliciesProperties
import org.sainm.auth.autoconfigure.properties.DeviceGovernanceSignalPolicyProperties
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.api.RegisterDeviceRequest
import org.sainm.psy.notification.domain.UserDeviceDeactivationResult
import org.sainm.psy.notification.domain.UserDeviceSummary
import org.sainm.psy.notification.repository.UserDeviceRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.sainm.auth.core.device.DeviceGovernanceSignalPolicies
import org.sainm.auth.core.device.DeviceSignalPolicy
import org.sainm.auth.core.device.UserDeviceAutoDisposition
import org.sainm.auth.core.device.UserDeviceRiskLevel

@Service
class UserDeviceService(
    private val userDeviceRepository: UserDeviceRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val sessionManagementServiceProvider: ObjectProvider<SessionManagementService>,
    private val securityAuditService: SecurityAuditService,
    private val authModuleProperties: AuthModuleProperties
) {

    enum class DeviceDeactivationAuditSource {
        MANUAL,
        AUTO_DISPOSITION
    }

    private val deviceProfileSupport = DeviceProfileSupport()

    fun findDevicesByUser(userId: Long): List<UserDeviceSummary> =
        userDeviceRepository.findByUser(userId)
            .map { attachAuthSession(userId, it) }
            .map(::composeProfile)

    fun findMyDevices(): List<UserDeviceSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return findDevicesByUser(currentUser.userId)
    }

    @Transactional
    fun registerMyDevice(request: RegisterDeviceRequest): UserDeviceSummary {
        val currentUser = currentUserFacade.requireCurrentUser()
        val resolvedDeviceId = request.deviceId?.trim()?.ifBlank { null }
            ?: (currentUser.attributes["deviceId"] as? String)?.trim()?.ifBlank { null }
            ?: throw BizException("NOTIFICATION_DEVICE_ID_REQUIRED", messages.get("notification.device_id_required"))
        return userDeviceRepository.upsertDevice(
            userId = currentUser.userId,
            deviceType = request.deviceType.trim().uppercase(),
            deviceId = resolvedDeviceId,
            pushToken = request.pushToken?.trim()?.ifBlank { null },
            appVersion = request.appVersion?.trim()?.ifBlank { null }
        ).let { attachAuthSession(currentUser.userId, it) }
            .let(::composeProfile)
    }

    @Transactional
    fun deactivateMyDevice(deviceId: String): UserDeviceSummary {
        val currentUser = currentUserFacade.requireCurrentUser()
        return deactivateDeviceWithResult(currentUser.userId, deviceId).device
    }

    @Transactional
    fun deactivateDevice(userId: Long, deviceId: String): UserDeviceSummary {
        return deactivateDeviceWithResult(userId, deviceId).device
    }

    @Transactional
    fun deactivateDeviceWithResult(
        userId: Long,
        deviceId: String,
        auditSource: DeviceDeactivationAuditSource = DeviceDeactivationAuditSource.MANUAL
    ): UserDeviceDeactivationResult {
        val normalizedDeviceId = deviceId.trim()
        val updated = userDeviceRepository.deactivate(userId, normalizedDeviceId)
        if (!updated) {
            throw BizException("NOTIFICATION_DEVICE_NOT_FOUND", messages.get("notification.device_not_found"))
        }
        val revokedSessionCount = revokeActiveSessionsForDevice(userId, normalizedDeviceId)
        val device = userDeviceRepository.findByUser(userId)
            .firstOrNull { it.deviceId == normalizedDeviceId }
            ?.let { attachAuthSession(userId, it) }
            ?.let(::composeProfile)
            ?: throw BizException("NOTIFICATION_DEVICE_NOT_FOUND", messages.get("notification.device_not_found"))
        if (auditSource == DeviceDeactivationAuditSource.MANUAL) {
            securityAuditService.recordUserDeviceDeactivated(
                targetUserId = userId,
                device = device,
                revokedSessionCount = revokedSessionCount
            )
        }
        return UserDeviceDeactivationResult(
            device = device,
            revokedSessionCount = revokedSessionCount
        )
    }

    private fun revokeActiveSessionsForDevice(userId: Long, deviceId: String): Int {
        val sessionManagementService = sessionManagementServiceProvider.ifAvailable ?: return 0
        return sessionManagementService.revokeSessionsByDevice(
            userId = userId,
            deviceId = deviceId,
            reason = "DEVICE_DEACTIVATED"
        )
    }

    private fun attachAuthSession(userId: Long, summary: UserDeviceSummary): UserDeviceSummary {
        val session = sessionManagementServiceProvider.ifAvailable
            ?.findLatestSessionByDevice(userId, summary.deviceId)
        return summary.copy(
            authSessionId = session?.sessionId,
            authSessionStatus = session?.status,
            authSessionLastSeenAt = session?.lastSeenAt?.let {
                Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
            }
        )
    }

    private fun composeProfile(summary: UserDeviceSummary): UserDeviceSummary =
        deviceProfileSupport.compose(summary.toAuthSummary(), riskPolicy()).toNotificationSummary(summary)

    private fun riskPolicy(): DeviceGovernanceRiskPolicy =
        DeviceGovernanceRiskPolicy(
            deviceStaleDays = authModuleProperties.deviceGovernance.deviceStaleDays,
            sessionStaleDays = authModuleProperties.deviceGovernance.sessionStaleDays,
            requiredPushTokenDeviceTypes = authModuleProperties.deviceGovernance.requiredPushTokenDeviceTypes
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .toSet(),
            signalPolicies = authModuleProperties.deviceGovernance.signalPolicies.toCorePolicies()
        )

    private fun UserDeviceSummary.toAuthSummary(): org.sainm.auth.core.spi.UserDeviceSummary =
        org.sainm.auth.core.spi.UserDeviceSummary(
            id = id,
            deviceType = deviceType,
            deviceId = deviceId,
            pushTokenMasked = pushTokenMasked,
            appVersion = appVersion,
            activeFlag = activeFlag,
            authSessionId = authSessionId,
            authSessionStatus = authSessionStatus,
            authSessionLastSeenAt = authSessionLastSeenAt?.let { it.atZone(ZoneId.systemDefault()).toInstant().toString() },
            deviceTrustLevel = deviceTrustLevel,
            riskSignals = riskSignals,
            riskLevel = riskLevel,
            autoDisposition = autoDisposition,
            autoDispositionReason = autoDispositionReason,
            lastActiveAt = lastActiveAt?.let { it.atZone(ZoneId.systemDefault()).toInstant().toString() },
            createdAt = createdAt.atZone(ZoneId.systemDefault()).toInstant().toString(),
            updatedAt = updatedAt.atZone(ZoneId.systemDefault()).toInstant().toString()
        )

    private fun org.sainm.auth.core.spi.UserDeviceSummary.toNotificationSummary(
        original: UserDeviceSummary
    ): UserDeviceSummary =
        original.copy(
            deviceTrustLevel = deviceTrustLevel,
            riskSignals = riskSignals,
            riskLevel = riskLevel,
            autoDisposition = autoDisposition,
            autoDispositionReason = autoDispositionReason
        )

    private fun DeviceGovernanceSignalPoliciesProperties.toCorePolicies(): DeviceGovernanceSignalPolicies =
        DeviceGovernanceSignalPolicies(
            deviceInactive = deviceInactive.toCorePolicy(),
            pushTokenMissing = pushTokenMissing.toCorePolicy(),
            authSessionMissing = authSessionMissing.toCorePolicy(),
            abnormalAuthSessionStatus = abnormalAuthSessionStatus.toCorePolicy(),
            authSessionStale = authSessionStale.toCorePolicy(),
            deviceStale = deviceStale.toCorePolicy(),
            inactiveDeviceWithActiveSession = inactiveDeviceWithActiveSession.toCorePolicy(),
            activeDeviceWithoutActiveSession = activeDeviceWithoutActiveSession.toCorePolicy()
        )

    private fun DeviceGovernanceSignalPolicyProperties.toCorePolicy(): DeviceSignalPolicy =
        DeviceSignalPolicy(
            enabled = enabled,
            riskLevel = UserDeviceRiskLevel.valueOf(riskLevel.trim().uppercase()),
            autoDisposition = UserDeviceAutoDisposition.valueOf(autoDisposition.trim().uppercase())
        )
}
