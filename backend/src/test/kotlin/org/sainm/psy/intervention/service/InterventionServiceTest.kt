package org.sainm.psy.intervention.service

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
import org.sainm.psy.intervention.api.CloseInterventionRequest
import org.sainm.psy.intervention.api.CreateInterventionRequest
import org.sainm.psy.intervention.domain.InterventionDetail
import org.sainm.psy.intervention.repository.InterventionRepository
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class InterventionServiceTest {

    @Mock private lateinit var interventionRepository: InterventionRepository
    @Mock private lateinit var warningRepository: WarningRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService
    @Mock private lateinit var securityAuditService: SecurityAuditService

    @InjectMocks
    private lateinit var interventionService: InterventionService

    private val mockUser = CurrentUser(
        userId = 10L,
        username = "counselor01",
        displayName = "Counselor",
        tenantId = 1L,
        groupId = null,
        roles = setOf("COUNSELOR"),
        permissions = emptySet()
    )

    private fun makeDetail(
        id: Long = 1L,
        warningId: Long = 100L,
        counselorUserId: Long? = 20L
    ) = InterventionDetail(
        id = id,
        warningId = warningId,
        counselorUserId = counselorUserId,
        currentStatus = "PROCESSING",
        planText = "plan",
        closeSummary = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create throws BizException when warning not found`() {
        `when`(warningRepository.existsById(99L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            interventionService.create(CreateInterventionRequest(warningId = 99L, planText = "plan"))
        }
        assertEquals("WARNING_NOT_FOUND", ex.code)
        verify(interventionRepository, never()).createIntervention(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong()
        )
    }

    @Test
    fun `create uses request counselorUserId when provided`() {
        `when`(warningRepository.existsById(1L)).thenReturn(true)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.createIntervention(1L, 5L, "plan", 10L)).thenReturn(42L)

        val result = interventionService.create(
            CreateInterventionRequest(warningId = 1L, counselorUserId = 5L, planText = "plan")
        )

        assertEquals(42L, result.interventionId)
        assertEquals(1L, result.warningId)
        assertEquals("PROCESSING", result.status)
        verify(warningRepository).markProcessing(1L)
        verify(securityAuditService).recordInterventionCreated(42L, 1L, 5L)
        verify(notificationDispatchService).notifyUsers(
            notificationType = "INTERVENTION_CREATED",
            title = "新的干预记录已创建",
            content = "预警 #1 已进入干预流程，请及时处理。",
            bizType = "INTERVENTION",
            bizId = 42L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(5L)
        )
    }

    @Test
    fun `create falls back to currentUser when counselorUserId is null`() {
        `when`(warningRepository.existsById(1L)).thenReturn(true)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.createIntervention(1L, 10L, "plan", 10L)).thenReturn(7L)

        val result = interventionService.create(
            CreateInterventionRequest(warningId = 1L, counselorUserId = null, planText = "plan")
        )

        assertEquals(7L, result.interventionId)
        verify(securityAuditService).recordInterventionCreated(7L, 1L, 10L)
    }

    // ── close ─────────────────────────────────────────────────────────────────

    @Test
    fun `close throws BizException when intervention not found by findDetailById`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.findDetailById(99L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            interventionService.close(99L, CloseInterventionRequest(closeSummary = "done"))
        }
        assertEquals("INTERVENTION_NOT_FOUND", ex.code)
    }

    @Test
    fun `close throws BizException when closeIntervention returns false`() {
        val detail = makeDetail(id = 1L, warningId = 100L, counselorUserId = 20L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.findDetailById(1L)).thenReturn(detail)
        `when`(interventionRepository.closeIntervention(1L, "done", 10L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            interventionService.close(1L, CloseInterventionRequest(closeSummary = "done"))
        }
        assertEquals("INTERVENTION_NOT_FOUND", ex.code)
    }

    @Test
    fun `close succeeds and closes warning + records audit + sends notification`() {
        val detail = makeDetail(id = 1L, warningId = 100L, counselorUserId = 20L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.findDetailById(1L)).thenReturn(detail)
        `when`(interventionRepository.closeIntervention(1L, "done", 10L)).thenReturn(true)

        val result = interventionService.close(1L, CloseInterventionRequest(closeSummary = "done"))

        assertEquals("CLOSED", result.status)
        assertEquals(1L, result.interventionId)
        assertEquals(100L, result.warningId)
        verify(warningRepository).closeWarning(100L)
        verify(securityAuditService).recordInterventionClosed(1L, 100L, 20L)
        verify(notificationDispatchService).notifyUsers(
            notificationType = "INTERVENTION_CLOSED",
            title = "干预记录已结案",
            content = "干预 #1 已结案，预警 #100 已关闭。",
            bizType = "INTERVENTION",
            bizId = 1L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(20L)
        )
    }

    @Test
    fun `close uses currentUser as counselor fallback when detail counselorUserId is null`() {
        val detail = makeDetail(id = 1L, warningId = 100L, counselorUserId = null)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.findDetailById(1L)).thenReturn(detail)
        `when`(interventionRepository.closeIntervention(1L, "done", 10L)).thenReturn(true)

        interventionService.close(1L, CloseInterventionRequest(closeSummary = "done"))

        verify(securityAuditService).recordInterventionClosed(1L, 100L, 10L)
        // counselorUserId is null → listOfNotNull(null) = emptyList, so no notification
        verify(notificationDispatchService).notifyUsers(
            notificationType = "INTERVENTION_CLOSED",
            title = "干预记录已结案",
            content = "干预 #1 已结案，预警 #100 已关闭。",
            bizType = "INTERVENTION",
            bizId = 1L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = emptyList()
        )
    }
}
