package org.sainm.psy.auth

import org.sainm.auth.autoconfigure.properties.AuthModuleProperties
import org.sainm.auth.autoconfigure.properties.DeviceGovernanceSignalPoliciesProperties
import org.sainm.auth.autoconfigure.properties.DeviceGovernanceSignalPolicyProperties
import org.sainm.auth.core.device.DeviceGovernanceRiskPolicy
import org.sainm.auth.core.device.DeviceGovernanceSignalPolicies
import org.sainm.auth.core.device.DeviceProfileSupport
import org.sainm.auth.core.device.DeviceSignalPolicy
import org.sainm.auth.core.device.UserDeviceAutoDisposition
import org.sainm.auth.core.device.UserDeviceRiskLevel
import org.sainm.auth.core.spi.DeviceGovernanceService
import org.sainm.auth.core.spi.DeviceRegistrationCommand
import org.sainm.auth.core.spi.SessionManagementService
import org.sainm.auth.core.spi.UserDeviceDeactivationResult
import org.sainm.auth.core.spi.UserDeviceSummary
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.notification.api.RegisterDeviceRequest
import org.sainm.psy.notification.service.UserDeviceService
import org.sainm.psy.notification.service.UserDeviceService.DeviceDeactivationAuditSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class AuthDeviceGovernanceAdapter(
    private val userDeviceService: UserDeviceService,
    private val sessionManagementServiceProvider: ObjectProvider<SessionManagementService>,
    private val authModuleProperties: AuthModuleProperties,
    private val securityAuditService: SecurityAuditService
) : DeviceGovernanceService {

    private val deviceProfileSupport = DeviceProfileSupport()

    override fun listMyDevices(userId: Long): List<UserDeviceSummary> =
        listUserDevices(userId)

    override fun registerMyDevice(command: DeviceRegistrationCommand): UserDeviceSummary =
        userDeviceService.registerMyDevice(
            RegisterDeviceRequest(
                deviceType = command.deviceType,
                deviceId = command.deviceId,
                pushToken = command.pushToken,
                appVersion = command.appVersion
            )
        ).toAuthSummary().let(::evaluateProfile)

    override fun deactivateMyDevice(userId: Long, deviceId: String): UserDeviceSummary =
        userDeviceService.deactivateDevice(userId, deviceId).toAuthSummary().let(::evaluateProfile)

    override fun listUserDevices(userId: Long): List<UserDeviceSummary> =
        userDeviceService.findDevicesByUser(userId)
            .map { it.toAuthSummary() }
            .map { applyAutomaticDisposition(userId, it) }
            .map(::evaluateProfile)

    override fun deactivateUserDevice(userId: Long, deviceId: String): UserDeviceDeactivationResult =
        userDeviceService.deactivateDeviceWithResult(userId, deviceId).let {
            UserDeviceDeactivationResult(
                device = evaluateProfile(it.device.toAuthSummary()),
                revokedSessionCount = it.revokedSessionCount
            )
        }

    private fun evaluateProfile(summary: UserDeviceSummary): UserDeviceSummary =
        deviceProfileSupport.compose(summary, riskPolicy())

    private fun applyAutomaticDisposition(userId: Long, summary: UserDeviceSummary): UserDeviceSummary {
        val evaluated = evaluateProfile(summary)
        return when (UserDeviceAutoDisposition.valueOf(evaluated.autoDisposition)) {
            UserDeviceAutoDisposition.NONE,
            UserDeviceAutoDisposition.REVIEW_ONLY -> evaluated
            UserDeviceAutoDisposition.REVOKE_DEVICE_SESSIONS -> {
                val revokedCount = sessionManagementServiceProvider.ifAvailable?.revokeSessionsByDevice(
                    userId = userId,
                    deviceId = evaluated.deviceId,
                    reason = "AUTO_DEVICE_RISK_CONTROL"
                ) ?: 0
                securityAuditService.recordUserDeviceAutoDisposed(
                    targetUserId = userId,
                    device = evaluated,
                    revokedSessionCount = revokedCount
                )
                evaluateProfile(
                    userDeviceService.findDevicesByUser(userId)
                        .map { it.toAuthSummary() }
                        .firstOrNull { it.deviceId == evaluated.deviceId }
                        ?: evaluated
                )
            }
            UserDeviceAutoDisposition.DEACTIVATE_DEVICE_AND_REVOKE_SESSIONS ->
                userDeviceService.deactivateDeviceWithResult(
                    userId = userId,
                    deviceId = evaluated.deviceId,
                    auditSource = DeviceDeactivationAuditSource.AUTO_DISPOSITION
                ).also {
                    securityAuditService.recordUserDeviceAutoDisposed(
                        targetUserId = userId,
                        device = evaluated,
                        revokedSessionCount = it.revokedSessionCount
                    )
                }.device.toAuthSummary().let(::evaluateProfile)
        }
    }

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

    private fun org.sainm.psy.notification.domain.UserDeviceSummary.toAuthSummary(): UserDeviceSummary =
        UserDeviceSummary(
            id = id,
            deviceType = deviceType,
            deviceId = deviceId,
            pushTokenMasked = pushTokenMasked,
            appVersion = appVersion,
            activeFlag = activeFlag,
            authSessionId = authSessionId,
            authSessionStatus = authSessionStatus,
            authSessionLastSeenAt = authSessionLastSeenAt?.toIsoString(),
            deviceTrustLevel = deviceTrustLevel,
            riskSignals = riskSignals,
            riskLevel = riskLevel,
            autoDisposition = autoDisposition,
            autoDispositionReason = autoDispositionReason,
            lastActiveAt = lastActiveAt?.toIsoString(),
            createdAt = createdAt.toIsoString(),
            updatedAt = updatedAt.toIsoString()
        )

    private fun LocalDateTime.toIsoString(): String =
        atZone(ZoneId.systemDefault()).toInstant().toString()

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
            autoDisposition = org.sainm.auth.core.device.UserDeviceAutoDisposition.valueOf(autoDisposition.trim().uppercase())
        )
}
