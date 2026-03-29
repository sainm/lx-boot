package org.sainm.psy.warning.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.auth.CurrentUser
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.api.AssignWarningRequest
import org.sainm.psy.warning.api.WarningListQuery
import org.sainm.psy.warning.domain.WarningActionResult
import org.sainm.psy.warning.domain.WarningSummary
import org.sainm.psy.warning.repository.WarningRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class WarningServiceTest {

    @Mock private lateinit var warningRepository: WarningRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService
    @Mock private lateinit var securityAuditService: SecurityAuditService

    @InjectMocks
    private lateinit var warningService: WarningService

    private val mockUser = CurrentUser(
        userId = 10L,
        username = "counselor01",
        displayName = "Counselor",
        tenantId = 1L,
        groupId = null,
        roles = setOf("COUNSELOR"),
        permissions = emptySet()
    )

    // ── findPage ────────────────────────────────────────────────────────────

    @Test
    fun `findPage throws when page is zero`() {
        assertThrows<IllegalArgumentException> {
            warningService.findPage(WarningListQuery(page = 0))
        }
    }

    @Test
    fun `findPage throws when size exceeds 200`() {
        assertThrows<IllegalArgumentException> {
            warningService.findPage(WarningListQuery(page = 1, size = 201))
        }
    }

    @Test
    fun `findPage returns paged response`() {
        val items = listOf(WarningSummary(1L, 100L, "HIGH", "P1", null, "PENDING", LocalDateTime.now()))
        `when`(warningRepository.findPage(WarningListQuery(page = 1, size = 20))).thenReturn(Pair(items, 1L))

        val result = warningService.findPage(WarningListQuery(page = 1, size = 20))

        assertEquals(1, result.list.size)
        assertEquals(1L, result.total)
    }

    // ── claim ────────────────────────────────────────────────────────────────

    @Test
    fun `claim throws BizException when warning not found`() {
        `when`(warningRepository.existsById(99L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            warningService.claim(99L)
        }
        assertEquals("WARNING_NOT_FOUND", ex.code)
        verify(warningRepository, never()).claimWarning(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `claim succeeds and records audit + notification`() {
        val expected = WarningActionResult(warningId = 1L, status = "CLAIMED")
        `when`(warningRepository.existsById(1L)).thenReturn(true)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(warningRepository.claimWarning(1L, 10L, 10L)).thenReturn(expected)

        val result = warningService.claim(1L)

        assertEquals("CLAIMED", result.status)
        verify(securityAuditService).recordWarningClaimed(1L)
        verify(notificationDispatchService).notifyUsers(
            notificationType = "WARNING_CLAIMED",
            title = "预警已接单",
            content = "预警 #1 已由当前处理人接单，请及时跟进。",
            bizType = "WARNING",
            bizId = 1L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(10L)
        )
    }

    // ── assign ───────────────────────────────────────────────────────────────

    @Test
    fun `assign throws BizException when warning not found`() {
        `when`(warningRepository.existsById(99L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            warningService.assign(99L, AssignWarningRequest(assigneeUserId = 5L))
        }
        assertEquals("WARNING_NOT_FOUND", ex.code)
    }

    @Test
    fun `assign succeeds and sends notification to assignee`() {
        val expected = WarningActionResult(warningId = 2L, status = "ASSIGNED", assigneeUserId = 5L)
        `when`(warningRepository.existsById(2L)).thenReturn(true)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(warningRepository.assignWarning(2L, 5L, 10L)).thenReturn(expected)

        val result = warningService.assign(2L, AssignWarningRequest(assigneeUserId = 5L))

        assertEquals("ASSIGNED", result.status)
        assertEquals(5L, result.assigneeUserId)
        verify(securityAuditService).recordWarningAssigned(2L, 5L)
        verify(notificationDispatchService).notifyUsers(
            notificationType = "WARNING_ASSIGNED",
            title = "收到新的预警指派",
            content = "预警 #2 已指派给你，请尽快查看报告并开始跟进。",
            bizType = "WARNING",
            bizId = 2L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(5L)
        )
    }
}
