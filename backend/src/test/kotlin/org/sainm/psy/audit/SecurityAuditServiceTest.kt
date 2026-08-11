package org.sainm.psy.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.core.spi.AuditEvent
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.security.support.CurrentUserFacade

@ExtendWith(MockitoExtension::class)
class SecurityAuditServiceTest {
    @Mock private lateinit var publisher: AuditEventPublisher
    @Mock private lateinit var currentUserFacade: CurrentUserFacade

    @Test
    fun `scale package export audit includes tenant and fingerprint evidence`() {
        whenever(currentUserFacade.requireCurrentUser()).thenReturn(user)
        val service = SecurityAuditService(publisher, currentUserFacade)

        service.recordScalePackageExported(1, "export-1", "scale-hash", "release-hash", 1, 4, 6, 2)

        val captor = argumentCaptor<AuditEvent>()
        verify(publisher).publish(captor.capture())
        assertEquals("PSY_SCALE_PACKAGE_EXPORTED", captor.firstValue.type)
        assertEquals(7L, captor.firstValue.detail["tenantId"])
        assertEquals("release-hash", captor.firstValue.detail["releaseFingerprint"])
        assertEquals(4, captor.firstValue.detail["caseRevisionCount"])
    }

    @Test
    fun `scale package export fails when its required security audit cannot be persisted`() {
        whenever(currentUserFacade.requireCurrentUser()).thenReturn(user)
        doThrow(IllegalStateException("audit unavailable")).whenever(publisher).publish(any())
        val service = SecurityAuditService(publisher, currentUserFacade)

        val error = assertThrows<IllegalStateException> {
            service.recordScalePackageExported(1, "export-1", "scale-hash", "release-hash", 1, 1, 1, 1)
        }

        assertEquals("audit unavailable", error.message)
    }

    @Test
    fun `notification retry audit is required and excludes callback payloads`() {
        whenever(currentUserFacade.requireCurrentUser()).thenReturn(user)
        val service = SecurityAuditService(publisher, currentUserFacade)

        service.recordNotificationDeliveriesRetried(listOf(12L, 11L, 12L), "PUSH", 2)

        val captor = argumentCaptor<AuditEvent>()
        verify(publisher).publish(captor.capture())
        assertEquals("PSY_NOTIFICATION_DELIVERIES_RETRIED", captor.firstValue.type)
        assertEquals(listOf(11L, 12L), captor.firstValue.detail["notificationIds"])
        assertEquals(2, captor.firstValue.detail["retriedCount"])
        assertEquals(7L, captor.firstValue.detail["tenantId"])
    }

    @Test
    fun `export job lifecycle audits carry identifiers without file names or content`() {
        whenever(currentUserFacade.requireCurrentUser()).thenReturn(user)
        val service = SecurityAuditService(publisher, currentUserFacade)

        service.recordExportJobSubmitted("job-1", 10L, 11L, "PDF", true)
        service.recordExportJobReplayed("job-1", 10L, 11L, "DEAD_LETTER", 3)
        service.recordExportJobDownloaded("job-1", 10L, 11L, "PDF", 128L)

        val captor = argumentCaptor<AuditEvent>()
        verify(publisher, org.mockito.kotlin.times(3)).publish(captor.capture())
        assertEquals(
            listOf("PSY_EXPORT_JOB_SUBMITTED", "PSY_EXPORT_JOB_REPLAYED", "PSY_EXPORT_JOB_DOWNLOADED"),
            captor.allValues.map { it.type }
        )
        assertEquals(3, captor.allValues[1].detail["previousRetryCount"])
        assertEquals(128L, captor.allValues[2].detail["fileSize"])
        assertEquals(null, captor.allValues[2].detail["fileName"])
        assertEquals(null, captor.allValues[2].detail["content"])
    }

    private val user = UserPrincipal(
        userId = 10,
        username = "admin",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        tenantId = 7,
        groupId = null,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )
}
