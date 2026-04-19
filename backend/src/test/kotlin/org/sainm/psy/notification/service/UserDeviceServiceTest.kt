package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.autoconfigure.properties.AuthModuleProperties
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.spi.SessionManagementService
import org.sainm.auth.core.spi.UserSessionSummary
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.api.RegisterDeviceRequest
import org.sainm.psy.notification.domain.UserDeviceSummary
import org.sainm.psy.notification.repository.UserDeviceRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class UserDeviceServiceTest {

    @Mock private lateinit var userDeviceRepository: UserDeviceRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var sessionManagementService: SessionManagementService
    @Mock private lateinit var sessionManagementServiceProvider: ObjectProvider<SessionManagementService>
    @Mock private lateinit var securityAuditService: SecurityAuditService

    private val messages = LocalizedMessages(
        ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
    )

    @Test
    fun `registerMyDevice normalizes request and upserts current user device`() {
        val service = service()
        val user = currentUser()
        val summary = deviceSummary(deviceType = "ANDROID", deviceId = "phone-1", authSessionId = null, authSessionStatus = null, authSessionLastSeenAt = null)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(sessionManagementServiceProvider.ifAvailable).thenReturn(null)
        `when`(
            userDeviceRepository.upsertDevice(
                userId = 5L,
                deviceType = "ANDROID",
                deviceId = "phone-1",
                pushToken = "token-123",
                appVersion = "1.0.0"
            )
        ).thenReturn(summary)

        val result = service.registerMyDevice(
            RegisterDeviceRequest(
                deviceType = " android ",
                deviceId = " phone-1 ",
                pushToken = " token-123 ",
                appVersion = " 1.0.0 "
            )
        )

        assertEquals("phone-1", result.deviceId)
        assertEquals("STALE", result.deviceTrustLevel)
        assertTrue(result.riskSignals.contains("AUTH_SESSION_MISSING"))
    }

    @Test
    fun `findMyDevices enriches trust level and risk signals`() {
        val service = service()
        val now = LocalDateTime.now()
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser())
        `when`(sessionManagementServiceProvider.ifAvailable).thenReturn(sessionManagementService)
        `when`(userDeviceRepository.findByUser(5L)).thenReturn(
            listOf(
                deviceSummary(
                    deviceId = "trusted-web",
                    pushTokenMasked = "toke****-123",
                    activeFlag = true,
                    authSessionId = null,
                    authSessionStatus = null,
                    authSessionLastSeenAt = null,
                    lastActiveAt = now.minusDays(1)
                ),
                deviceSummary(
                    deviceId = "review-web",
                    pushTokenMasked = null,
                    activeFlag = true,
                    authSessionId = null,
                    authSessionStatus = null,
                    authSessionLastSeenAt = null,
                    lastActiveAt = now.minusDays(2)
                ),
                deviceSummary(
                    deviceId = "stale-web",
                    activeFlag = false,
                    authSessionId = null,
                    authSessionStatus = null,
                    authSessionLastSeenAt = null,
                    lastActiveAt = now.minusDays(40)
                )
            )
        )
        `when`(sessionManagementService.findLatestSessionByDevice(5L, "trusted-web"))
            .thenReturn(sessionSummary(sessionId = "session-trusted", deviceId = "trusted-web", status = "ACTIVE", lastSeenAt = now.minusDays(1)))
        `when`(sessionManagementService.findLatestSessionByDevice(5L, "review-web"))
            .thenReturn(sessionSummary(sessionId = "session-review", deviceId = "review-web", status = "REVOKED", lastSeenAt = now.minusDays(3)))
        `when`(sessionManagementService.findLatestSessionByDevice(5L, "stale-web"))
            .thenReturn(sessionSummary(sessionId = "session-stale", deviceId = "stale-web", status = "ACTIVE", lastSeenAt = now.minusDays(45)))

        val result = service.findMyDevices()

        assertEquals("TRUSTED", result[0].deviceTrustLevel)
        assertTrue(result[0].riskSignals.isEmpty())
        assertEquals("STALE", result[1].deviceTrustLevel)
        assertTrue(result[1].riskSignals.contains("PUSH_TOKEN_MISSING"))
        assertTrue(result[1].riskSignals.contains("AUTH_SESSION_REVOKED"))
        assertTrue(result[1].riskSignals.contains("ACTIVE_DEVICE_WITHOUT_ACTIVE_SESSION"))
        assertEquals("STALE", result[2].deviceTrustLevel)
        assertTrue(result[2].riskSignals.contains("DEVICE_INACTIVE"))
        assertTrue(result[2].riskSignals.contains("AUTH_SESSION_STALE"))
        assertTrue(result[2].riskSignals.contains("DEVICE_STALE"))
    }

    @Test
    fun `deactivateMyDevice throws when active device is not found`() {
        val service = service()
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser())
        `when`(userDeviceRepository.deactivate(5L, "phone-404")).thenReturn(false)

        val ex = assertThrows<BizException> {
            service.deactivateMyDevice(" phone-404 ")
        }

        assertEquals("NOTIFICATION_DEVICE_NOT_FOUND", ex.code)
    }

    @Test
    fun `deactivateMyDevice returns deactivated device`() {
        val service = service()
        val deactivated = deviceSummary(activeFlag = false, authSessionId = null, authSessionStatus = null, authSessionLastSeenAt = null)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser())
        `when`(userDeviceRepository.deactivate(5L, "phone-1")).thenReturn(true)
        `when`(sessionManagementServiceProvider.ifAvailable).thenReturn(sessionManagementService)
        `when`(sessionManagementService.revokeSessionsByDevice(5L, "phone-1", "DEVICE_DEACTIVATED")).thenReturn(1)
        `when`(sessionManagementService.findLatestSessionByDevice(5L, "phone-1"))
            .thenReturn(sessionSummary(sessionId = "session-1", deviceId = "phone-1", status = "REVOKED"))
        `when`(userDeviceRepository.findByUser(5L)).thenReturn(listOf(deactivated))

        val result = service.deactivateMyDevice("phone-1")

        assertEquals(false, result.activeFlag)
        verify(userDeviceRepository).deactivate(5L, "phone-1")
        verify(sessionManagementService).revokeSessionsByDevice(5L, "phone-1", "DEVICE_DEACTIVATED")
        verify(securityAuditService).recordUserDeviceDeactivated(5L, result, 1)
    }

    @Test
    fun `deactivateDevice supports admin-side target user operations`() {
        val service = service()
        val deactivated = deviceSummary(deviceId = "browser-2", activeFlag = false, authSessionId = null, authSessionStatus = null, authSessionLastSeenAt = null)
        `when`(userDeviceRepository.deactivate(9L, "browser-2")).thenReturn(true)
        `when`(sessionManagementServiceProvider.ifAvailable).thenReturn(sessionManagementService)
        `when`(sessionManagementService.revokeSessionsByDevice(9L, "browser-2", "DEVICE_DEACTIVATED")).thenReturn(1)
        `when`(sessionManagementService.findLatestSessionByDevice(9L, "browser-2"))
            .thenReturn(sessionSummary(sessionId = "session-admin-1", userId = 9L, deviceId = "browser-2", status = "REVOKED"))
        `when`(userDeviceRepository.findByUser(9L)).thenReturn(listOf(deactivated))

        val result = service.deactivateDevice(9L, " browser-2 ")

        assertEquals("browser-2", result.deviceId)
        assertEquals(false, result.activeFlag)
        verify(userDeviceRepository).deactivate(9L, "browser-2")
        verify(sessionManagementService).revokeSessionsByDevice(9L, "browser-2", "DEVICE_DEACTIVATED")
        verify(securityAuditService).recordUserDeviceDeactivated(9L, result, 1)
    }

    @Test
    fun `deactivateDevice skips auth revoke when session management is unavailable`() {
        val service = service()
        val deactivated = deviceSummary(deviceId = "browser-3", activeFlag = false, authSessionId = null, authSessionStatus = null, authSessionLastSeenAt = null)
        `when`(userDeviceRepository.deactivate(9L, "browser-3")).thenReturn(true)
        `when`(sessionManagementServiceProvider.ifAvailable).thenReturn(null)
        `when`(userDeviceRepository.findByUser(9L)).thenReturn(listOf(deactivated))

        val result = service.deactivateDevice(9L, "browser-3")

        assertEquals("browser-3", result.deviceId)
        verifyNoInteractions(sessionManagementService)
        verify(securityAuditService).recordUserDeviceDeactivated(9L, result, 0)
    }

    private fun service() = UserDeviceService(
        userDeviceRepository,
        currentUserFacade,
        messages,
        sessionManagementServiceProvider,
        securityAuditService,
        AuthModuleProperties()
    )

    private fun currentUser() = UserPrincipal(
        userId = 5L,
        username = "tester",
        displayName = "Tester",
        status = UserStatus.ENABLED,
        tenantId = null,
        groupId = null,
        roles = emptySet(),
        permissions = emptySet()
    )

    private fun deviceSummary(
        deviceType: String = "ANDROID",
        deviceId: String = "phone-1",
        activeFlag: Boolean = true,
        pushTokenMasked: String? = "toke****-123",
        authSessionId: String? = "session-1",
        authSessionStatus: String? = if (activeFlag) "ACTIVE" else "REVOKED",
        authSessionLastSeenAt: LocalDateTime? = LocalDateTime.now(),
        lastActiveAt: LocalDateTime? = LocalDateTime.now()
    ) = UserDeviceSummary(
        id = 1L,
        deviceType = deviceType,
        deviceId = deviceId,
        pushTokenMasked = pushTokenMasked,
        appVersion = "1.0.0",
        activeFlag = activeFlag,
        authSessionId = authSessionId,
        authSessionStatus = authSessionStatus,
        authSessionLastSeenAt = authSessionLastSeenAt,
        deviceTrustLevel = "TRUSTED",
        riskSignals = emptyList(),
        riskLevel = "LOW",
        autoDisposition = "NONE",
        autoDispositionReason = null,
        lastActiveAt = lastActiveAt,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private fun sessionSummary(
        sessionId: String,
        userId: Long = 5L,
        deviceId: String?,
        status: String,
        lastSeenAt: LocalDateTime = LocalDateTime.now()
    ) = UserSessionSummary(
        sessionId = sessionId,
        userId = userId,
        username = "tester",
        tenantId = null,
        clientId = "admin-web",
        deviceId = deviceId,
        deviceType = "WEB",
        deviceName = "Admin Web",
        userAgent = "Mozilla/5.0",
        ip = "127.0.0.1",
        status = status,
        lastSeenAt = lastSeenAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toString(),
        accessExpireAt = LocalDateTime.now().plusHours(1).toString(),
        refreshExpireAt = LocalDateTime.now().plusDays(7).toString(),
        createdAt = LocalDateTime.now().minusDays(1).toString(),
        updatedAt = LocalDateTime.now().toString(),
        revokedAt = null,
        revokeReason = null
    )
}


