package org.sainm.psy.intervention.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.intervention.api.CloseInterventionRequest
import org.sainm.psy.intervention.api.CreateInterventionRequest
import org.sainm.psy.intervention.domain.InterventionDetail
import org.sainm.psy.intervention.repository.InterventionRepository
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class InterventionServiceTest {

    @Mock private lateinit var interventionRepository: InterventionRepository
    @Mock private lateinit var assessmentTaskRepository: AssessmentTaskRepository
    @Mock private lateinit var warningRepository: WarningRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private lateinit var messages: LocalizedMessages
    private lateinit var interventionService: InterventionService

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        messages = LocalizedMessages(messageSource)
        interventionService = InterventionService(
            interventionRepository = interventionRepository,
            assessmentTaskRepository = assessmentTaskRepository,
            warningRepository = warningRepository,
            currentUserFacade = currentUserFacade,
            notificationDispatchService = notificationDispatchService,
            securityAuditService = securityAuditService,
            messages = messages,
            tenantAccessPolicy = tenantAccessPolicy
        )
        lenient().`when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        lenient().`when`(
            tenantAccessPolicy.currentTenantFilter(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
            )
        ).thenReturn(1L)
        lenient().`when`(
            tenantAccessPolicy.canAccess(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
            )
        ).thenReturn(true)
        lenient().`when`(
            warningRepository.findRiskCategory(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.nullable(Long::class.java)
            )
        ).thenReturn("P2")
    }

    private val mockUser = UserPrincipal(
        userId = 10L,
        username = "counselor01",
        displayName = "Counselor",
        status = UserStatus.ENABLED,
        tenantId = 1L,
        groupId = null,
        roles = setOf("COUNSELOR"),
        permissions = emptySet()
    )

    private fun anyCreateAssessmentTaskRequest(): CreateAssessmentTaskRequest {
        org.mockito.ArgumentMatchers.any(CreateAssessmentTaskRequest::class.java)
        return CreateAssessmentTaskRequest(
            taskName = "mock",
            scaleId = 1L,
            taskMode = "RETEST",
            startTime = LocalDateTime.of(2026, 1, 1, 0, 0),
            endTime = LocalDateTime.of(2026, 1, 2, 0, 0)
        )
    }

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
        needRetestFlag = false,
        retestTaskId = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        tenantId = 1L
    )

    @Test
    fun `create throws BizException when warning not found`() {
        `when`(warningRepository.existsById(99L, 1L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            interventionService.create(CreateInterventionRequest(warningId = 99L, planText = "plan"))
        }
        assertEquals("WARNING_NOT_FOUND", ex.code)
        verifyNoInteractions(interventionRepository)
    }

    @Test
    fun `create throws BizException when active intervention already exists`() {
        `when`(warningRepository.existsById(1L, 1L)).thenReturn(true)
        `when`(interventionRepository.findByWarningId(1L)).thenReturn(makeDetail(id = 9L, warningId = 1L))

        val ex = assertThrows<BizException> {
            interventionService.create(CreateInterventionRequest(warningId = 1L, planText = "plan"))
        }

        assertEquals("INTERVENTION_ALREADY_EXISTS", ex.code)
    }

    @Test
    fun `create uses request counselorUserId when provided`() {
        `when`(warningRepository.existsById(1L, 1L)).thenReturn(true)
        `when`(warningRepository.isActiveUserInTenant(5L, 1L)).thenReturn(true)
        `when`(interventionRepository.createIntervention(1L, 5L, "plan", 10L)).thenReturn(42L)

        val result = interventionService.create(
            CreateInterventionRequest(warningId = 1L, counselorUserId = 5L, planText = "plan")
        )

        assertEquals(42L, result.interventionId)
        assertEquals(1L, result.warningId)
        assertEquals("PROCESSING", result.status)
        verify(warningRepository).markProcessing(1L)
        verify(securityAuditService).recordInterventionCreated(42L, 1L, 5L)
        verify(notificationDispatchService).notifyInterventionCreated(42L, 1L, listOf(5L))
    }

    @Test
    fun `create falls back to currentUser when counselorUserId is null`() {
        `when`(warningRepository.existsById(1L, 1L)).thenReturn(true)
        `when`(warningRepository.isActiveUserInTenant(10L, 1L)).thenReturn(true)
        `when`(interventionRepository.createIntervention(1L, 10L, "plan", 10L)).thenReturn(7L)

        val result = interventionService.create(
            CreateInterventionRequest(warningId = 1L, counselorUserId = null, planText = "plan")
        )

        assertEquals(7L, result.interventionId)
        verify(securityAuditService).recordInterventionCreated(7L, 1L, 10L)
    }

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
        `when`(interventionRepository.closeIntervention(1L, "done", false, 10L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            interventionService.close(1L, CloseInterventionRequest(closeSummary = "done"))
        }
        assertEquals("INTERVENTION_NOT_FOUND", ex.code)
    }

    @Test
    fun `close succeeds and closes warning records audit and sends notification`() {
        val detail = makeDetail(id = 1L, warningId = 100L, counselorUserId = 20L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.findDetailById(1L)).thenReturn(detail)
        `when`(interventionRepository.closeIntervention(1L, "done", false, 10L)).thenReturn(true)

        val result = interventionService.close(1L, CloseInterventionRequest(closeSummary = "done"))

        assertEquals("CLOSED", result.status)
        assertEquals(1L, result.interventionId)
        assertEquals(100L, result.warningId)
        verify(warningRepository).closeWarning(100L)
        verify(securityAuditService).recordInterventionClosed(1L, 100L, 20L)
        verify(notificationDispatchService).notifyInterventionClosed(1L, 100L, listOf(20L))
    }

    @Test
    fun `close rejects high risk warning without complete safety evidence`() {
        val detail = makeDetail(id = 1L, warningId = 100L, counselorUserId = 20L)
        `when`(interventionRepository.findDetailById(1L)).thenReturn(detail)
        `when`(warningRepository.findRiskCategory(100L, 1L)).thenReturn("P1")

        val ex = assertThrows<BizException> {
            interventionService.close(1L, CloseInterventionRequest(closeSummary = "done"))
        }

        assertEquals("WARNING_CLOSE_CHECKLIST_REQUIRED", ex.code)
        verify(interventionRepository, org.mockito.Mockito.never())
            .closeIntervention(1L, "done", false, 10L)
    }

    @Test
    fun `close high risk warning stores safety evidence and follow up before closure`() {
        val detail = makeDetail(id = 1L, warningId = 100L, counselorUserId = 20L)
        val followUp = LocalDateTime.now().plusDays(1)
        `when`(interventionRepository.findDetailById(1L)).thenReturn(detail)
        `when`(warningRepository.findRiskCategory(100L, 1L)).thenReturn("P1")
        `when`(warningRepository.findTenantId(100L)).thenReturn(1L)
        `when`(interventionRepository.closeIntervention(1L, "review complete", false, 10L)).thenReturn(true)

        val result = interventionService.close(
            1L,
            CloseInterventionRequest(
                closeSummary = "review complete",
                contactChannel = "PHONE",
                contactOutcome = "Reached respondent and guardian",
                safetyAssessmentSummary = "No imminent danger after professional review",
                imminentDangerFlag = false,
                responsibleHandoffSummary = "Handed to counselor on duty",
                followUpDueTime = followUp
            )
        )

        assertEquals("CLOSED", result.status)
        verify(warningRepository).recordClosureEvidenceAndClose(
            warningId = 100L,
            tenantId = 1L,
            performedBy = 10L,
            contactChannel = "PHONE",
            contactOutcome = "Reached respondent and guardian",
            safetyAssessmentSummary = "No imminent danger after professional review",
            imminentDangerFlag = false,
            responsibleHandoffSummary = "Handed to counselor on duty",
            followUpDueTime = followUp,
            closureReason = "review complete"
        )
    }

    @Test
    fun `close uses currentUser as counselor fallback when detail counselorUserId is null`() {
        val detail = makeDetail(id = 1L, warningId = 100L, counselorUserId = null)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.findDetailById(1L)).thenReturn(detail)
        `when`(interventionRepository.closeIntervention(1L, "done", false, 10L)).thenReturn(true)

        interventionService.close(1L, CloseInterventionRequest(closeSummary = "done"))

        verify(securityAuditService).recordInterventionClosed(1L, 100L, 10L)
        verify(notificationDispatchService).notifyInterventionClosed(1L, 100L, emptyList())
    }

    @Test
    fun `close creates retest task when requested`() {
        val detail = makeDetail(id = 1L, warningId = 100L, counselorUserId = 20L)
        val seed = InterventionRepository.RetestTaskSeed(
            warningId = 100L,
            userId = 30L,
            scaleId = 2L,
            sourceTaskId = 9L,
            sourceTaskName = "Spring Survey"
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(interventionRepository.findDetailById(1L)).thenReturn(detail)
        `when`(interventionRepository.closeIntervention(1L, "done", true, 10L)).thenReturn(true)
        `when`(interventionRepository.findRetestTaskSeed(100L)).thenReturn(seed)
        `when`(
            assessmentTaskRepository.create(
                anyCreateAssessmentTaskRequest(),
                org.mockito.ArgumentMatchers.eq(10L)
            )
        ).thenReturn(501L)

        val result = interventionService.close(1L, CloseInterventionRequest(closeSummary = "done", needRetest = true))

        assertEquals("CLOSED", result.status)
        assertEquals(501L, result.retestTaskId)
        verify(assessmentTaskRepository).assignTargets(501L, "USER", listOf(30L), 10L)
        verify(interventionRepository).markRetestTaskCreated(1L, 501L)
        verify(notificationDispatchService).notifyRetestTaskCreated(
            501L,
            messages.get("intervention.retest.task_name", "Spring Survey"),
            100L,
            1L,
            listOf(30L)
        )
        verify(securityAuditService).recordRetestTaskCreated(1L, 100L, 501L, 30L)
    }
}
