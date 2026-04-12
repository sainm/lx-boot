package org.sainm.psy.report.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
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
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.report.domain.MyReportSummary
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.repository.ReportRepository
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ReportServiceTest {

    @Mock private lateinit var reportRepository: ReportRepository
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var messages: LocalizedMessages

    @InjectMocks
    private lateinit var reportService: ReportService

    private val mockUser = CurrentUser(
        userId = 5L,
        username = "user01",
        displayName = "User",
        tenantId = 1L,
        groupId = null,
        roles = setOf("USER"),
        permissions = emptySet()
    )

    private val orgManager = mockUser.copy(
        userId = 99L,
        username = "manager01",
        roles = setOf("ORG_MANAGER")
    )

    private fun makeDetail(reportId: Long = 10L, resultId: Long = 20L, userId: Long? = 5L) = ReportDetail(
        reportId = reportId,
        resultId = resultId,
        userId = userId,
        reportType = "SYSTEM",
        totalScore = BigDecimal("15"),
        riskLevel = "MODERATE",
        content = "report content"
    )

    @Test
    fun `findDetail throws REPORT_NOT_FOUND when repository returns null`() {
        `when`(reportRepository.findDetailById(99L)).thenReturn(null)

        val ex = assertThrows<BizException> { reportService.findDetail(99L) }

        assertEquals("REPORT_NOT_FOUND", ex.code)
        verify(securityAuditService, never()).recordReportViewed(anyLong(), anyLong(), anyString(), anyString(), anyString())
    }

    @Test
    fun `findDetail returns own detail and records audit`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L, userId = 5L)
        `when`(reportRepository.findDetailById(10L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)

        val result = reportService.findDetail(10L)

        assertEquals(10L, result.reportId)
        assertEquals("MODERATE", result.riskLevel)
        verify(securityAuditService).recordReportViewed(
            reportId = 10L,
            resultId = 20L,
            reportType = "SYSTEM",
            riskLevel = "MODERATE",
            accessPath = "REPORT_ID"
        )
    }

    @Test
    fun `findDetail allows privileged role to read another user's report`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L, userId = 5L)
        `when`(reportRepository.findDetailById(10L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(orgManager)

        val result = reportService.findDetail(10L)

        assertEquals(10L, result.reportId)
        verify(securityAuditService).recordReportViewed(
            reportId = 10L,
            resultId = 20L,
            reportType = "SYSTEM",
            riskLevel = "MODERATE",
            accessPath = "REPORT_ID"
        )
    }

    @Test
    fun `findDetail blocks normal user from another user's report`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L, userId = 7L)
        `when`(reportRepository.findDetailById(10L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)

        val ex = assertThrows<BizException> { reportService.findDetail(10L) }

        assertEquals("REPORT_FORBIDDEN", ex.code)
        verify(securityAuditService, never()).recordReportViewed(anyLong(), anyLong(), anyString(), anyString(), anyString())
    }

    @Test
    fun `findDetail with audit=false still checks access and skips audit recording`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L, userId = 5L)
        `when`(reportRepository.findDetailById(10L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)

        val result = reportService.findDetail(10L, audit = false)

        assertEquals(10L, result.reportId)
        verify(securityAuditService, never()).recordReportViewed(anyLong(), anyLong(), anyString(), anyString(), anyString())
    }

    @Test
    fun `findDetailByResultId throws REPORT_NOT_FOUND when repository returns null`() {
        `when`(reportRepository.findDetailByResultId(99L)).thenReturn(null)

        val ex = assertThrows<BizException> { reportService.findDetailByResultId(99L) }

        assertEquals("REPORT_NOT_FOUND", ex.code)
    }

    @Test
    fun `findDetailByResultId returns own detail and records audit with RESULT_ID accessPath`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L, userId = 5L)
        `when`(reportRepository.findDetailByResultId(20L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)

        val result = reportService.findDetailByResultId(20L)

        assertEquals(10L, result.reportId)
        verify(securityAuditService).recordReportViewed(
            reportId = 10L,
            resultId = 20L,
            reportType = "SYSTEM",
            riskLevel = "MODERATE",
            accessPath = "RESULT_ID"
        )
    }

    @Test
    fun `findDetailByResultId blocks normal user from another user's report`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L, userId = 7L)
        `when`(reportRepository.findDetailByResultId(20L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)

        val ex = assertThrows<BizException> { reportService.findDetailByResultId(20L) }

        assertEquals("REPORT_FORBIDDEN", ex.code)
        verify(securityAuditService, never()).recordReportViewed(anyLong(), anyLong(), anyString(), anyString(), anyString())
    }

    @Test
    fun `findDetailByResultId with audit=false skips audit recording`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L, userId = 5L)
        `when`(reportRepository.findDetailByResultId(20L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)

        reportService.findDetailByResultId(20L, audit = false)

        verify(securityAuditService, never()).recordReportViewed(anyLong(), anyLong(), anyString(), anyString(), anyString())
    }

    @Test
    fun `findMyReports returns reports for current user`() {
        val summary = MyReportSummary(
            reportId = 10L,
            resultId = 20L,
            taskId = 1L,
            taskName = "Spring screening",
            scaleName = "PHQ-9",
            reportType = "SYSTEM",
            totalScore = BigDecimal("15"),
            riskLevel = "MODERATE",
            createdAt = LocalDateTime.now()
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(reportRepository.findMyReports(5L)).thenReturn(listOf(summary))

        val result = reportService.findMyReports()

        assertEquals(1, result.size)
        assertEquals(10L, result[0].reportId)
        verify(reportRepository).findMyReports(5L)
    }

    @Test
    fun `findMyReports returns empty list when user has no reports`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(reportRepository.findMyReports(5L)).thenReturn(emptyList())

        val result = reportService.findMyReports()

        assertEquals(0, result.size)
    }

    @Test
    fun `regenerate creates a new system report version and records audit`() {
        val oldDetail = makeDetail(reportId = 10L, resultId = 20L, userId = 5L)
        val newDetail = oldDetail.copy(reportId = 11L, content = "new content")
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(orgManager)
        `when`(reportRepository.findDetailById(10L)).thenReturn(oldDetail)
        `when`(messages.get("report.system.title")).thenReturn("System Report")
        `when`(messages.get("report.auto.header")).thenReturn("System Auto Report")
        `when`(messages.get("report.auto.score", "15")).thenReturn("Total Score: 15")
        `when`(messages.get("report.auto.risk", "MODERATE")).thenReturn("Risk Level: MODERATE")
        `when`(messages.get("report.regenerated.source", 10L)).thenReturn("Regenerated from report #10.")
        `when`(
            reportRepository.createSystemReportVersion(
                resultId = 20L,
                authorUserId = 99L,
                title = "System Report",
                content = "System Auto Report\nTotal Score: 15\nRisk Level: MODERATE\nRegenerated from report #10."
            )
        ).thenReturn(11L)
        `when`(reportRepository.findDetailById(11L)).thenReturn(newDetail)

        val result = reportService.regenerate(10L)

        assertEquals(11L, result.reportId)
        verify(securityAuditService).recordReportRegenerated(
            oldReportId = 10L,
            newReportId = 11L,
            resultId = 20L,
            riskLevel = "MODERATE"
        )
    }

    @Test
    fun `regenerate blocks normal user from another user's report`() {
        val oldDetail = makeDetail(reportId = 10L, resultId = 20L, userId = 7L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(reportRepository.findDetailById(10L)).thenReturn(oldDetail)

        val ex = assertThrows<BizException> { reportService.regenerate(10L) }

        assertEquals("REPORT_FORBIDDEN", ex.code)
        verify(reportRepository, never()).createSystemReportVersion(anyLong(), anyLong(), anyString(), anyString())
        verify(securityAuditService, never()).recordReportRegenerated(anyLong(), anyLong(), anyLong(), anyString())
    }
}
