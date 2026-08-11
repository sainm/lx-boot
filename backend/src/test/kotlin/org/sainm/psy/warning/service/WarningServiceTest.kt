package org.sainm.psy.warning.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.api.AssignWarningRequest
import org.sainm.psy.warning.api.WarningListQuery
import org.sainm.psy.warning.domain.WarningActionResult
import org.sainm.psy.warning.domain.WarningAutomationCandidate
import org.sainm.psy.warning.domain.WarningSummary
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class WarningServiceTest {

    @Mock private lateinit var warningRepository: WarningRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var transactionTemplate: TransactionTemplate
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private lateinit var messages: LocalizedMessages
    private lateinit var warningService: WarningService

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        messages = LocalizedMessages(messageSource)
        lenient().doAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<Any?>>(0)
            callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus::class.java))
        }.`when`(transactionTemplate).execute<Any?>(org.mockito.ArgumentMatchers.any())
        warningService = WarningService(
            warningRepository = warningRepository,
            currentUserFacade = currentUserFacade,
            notificationDispatchService = notificationDispatchService,
            securityAuditService = securityAuditService,
            messages = messages,
            transactionTemplate = transactionTemplate,
            tenantAccessPolicy = tenantAccessPolicy
        )
        lenient().`when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        lenient().`when`(
            tenantAccessPolicy.currentTenantFilter(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
            )
        ).thenReturn(1L)
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
        `when`(warningRepository.findPage(WarningListQuery(page = 1, size = 20), 1L)).thenReturn(items to 1L)

        val result = warningService.findPage(WarningListQuery(page = 1, size = 20))

        assertEquals(1, result.list.size)
        assertEquals(1L, result.total)
    }

    @Test
    fun `claim throws BizException when warning not found`() {
        `when`(warningRepository.existsById(99L, 1L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            warningService.claim(99L)
        }
        assertEquals("WARNING_NOT_FOUND", ex.code)
    }

    @Test
    fun `claim succeeds and records audit plus notification`() {
        val expected = WarningActionResult(warningId = 1L, status = "CLAIMED")
        `when`(warningRepository.existsById(1L, 1L)).thenReturn(true)
        `when`(warningRepository.isActiveUserInTenant(10L, 1L)).thenReturn(true)
        `when`(warningRepository.claimWarning(1L, 10L, 10L)).thenReturn(expected)

        val result = warningService.claim(1L)

        assertEquals("CLAIMED", result.status)
        verify(securityAuditService).recordWarningClaimed(1L)
        verify(notificationDispatchService).notifyWarningClaimed(1L, listOf(10L))
    }

    @Test
    fun `assign throws BizException when warning not found`() {
        `when`(warningRepository.existsById(99L, 1L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            warningService.assign(99L, AssignWarningRequest(assigneeUserId = 5L))
        }
        assertEquals("WARNING_NOT_FOUND", ex.code)
    }

    @Test
    fun `assign succeeds and sends notification to assignee`() {
        val expected = WarningActionResult(warningId = 2L, status = "ASSIGNED", assigneeUserId = 5L)
        `when`(warningRepository.existsById(2L, 1L)).thenReturn(true)
        `when`(warningRepository.isActiveUserInTenant(5L, 1L)).thenReturn(true)
        `when`(warningRepository.assignWarning(2L, 5L, 10L)).thenReturn(expected)

        val result = warningService.assign(2L, AssignWarningRequest(assigneeUserId = 5L))

        assertEquals("ASSIGNED", result.status)
        assertEquals(5L, result.assigneeUserId)
        verify(securityAuditService).recordWarningAssigned(2L, 5L)
        verify(notificationDispatchService).notifyWarningAssigned(2L, listOf(5L))
    }

    @Test
    fun `assign rejects assignee outside current tenant`() {
        `when`(warningRepository.existsById(2L, 1L)).thenReturn(true)
        `when`(warningRepository.isActiveUserInTenant(5L, 1L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            warningService.assign(2L, AssignWarningRequest(assigneeUserId = 5L))
        }

        assertEquals("WARNING_ASSIGNEE_FORBIDDEN", ex.code)
        verify(warningRepository, org.mockito.Mockito.never()).assignWarning(2L, 5L, 10L)
    }

    @Test
    fun `processWarningEscalations escalates high risk warnings and reminds assignees`() {
        val scanTime = LocalDateTime.of(2026, 4, 12, 10, 0)
        val escalationCandidates = listOf(WarningAutomationCandidate(1L, listOf(20L)))
        val reminderCandidates = listOf(WarningAutomationCandidate(2L, listOf(30L)))
        `when`(warningRepository.findHighRiskWarningsNeedingEscalation(scanTime.minusHours(24), scanTime)).thenReturn(escalationCandidates)
        `when`(warningRepository.markWarningsEscalated(listOf(1L), scanTime)).thenReturn(1)
        `when`(warningRepository.findWarningsNeedingReminder(scanTime.minusHours(24))).thenReturn(reminderCandidates)
        `when`(warningRepository.markWarningsReminded(listOf(2L), scanTime)).thenReturn(1)

        val result = warningService.processWarningEscalations(scanTime)

        assertEquals(1, result.escalatedCount)
        assertEquals(1, result.remindedCount)
        verify(notificationDispatchService).notifyWarningEscalated(1L, listOf(20L))
        verify(notificationDispatchService).notifyWarningReminder(2L, listOf(30L))
    }

    @Test
    fun `processWarningEscalations keeps escalation side effects before reminder failure`() {
        val scanTime = LocalDateTime.of(2026, 4, 12, 10, 0)
        val escalationCandidates = listOf(WarningAutomationCandidate(1L, listOf(20L)))
        val reminderCandidates = listOf(WarningAutomationCandidate(2L, listOf(30L)))
        `when`(warningRepository.findHighRiskWarningsNeedingEscalation(scanTime.minusHours(24), scanTime)).thenReturn(escalationCandidates)
        `when`(warningRepository.markWarningsEscalated(listOf(1L), scanTime)).thenReturn(1)
        `when`(warningRepository.findWarningsNeedingReminder(scanTime.minusHours(24))).thenReturn(reminderCandidates)
        `when`(warningRepository.markWarningsReminded(listOf(2L), scanTime)).thenReturn(1)
        doThrow(RuntimeException("push failed"))
            .`when`(notificationDispatchService)
            .notifyWarningReminder(2L, listOf(30L))

        assertThrows<RuntimeException> {
            warningService.processWarningEscalations(scanTime)
        }

        verify(notificationDispatchService).notifyWarningEscalated(1L, listOf(20L))
        verify(notificationDispatchService).notifyWarningReminder(2L, listOf(30L))
    }
}
