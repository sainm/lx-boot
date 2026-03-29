package org.sainm.psy.report.service

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

    private fun makeDetail(reportId: Long = 10L, resultId: Long = 20L) = ReportDetail(
        reportId = reportId,
        resultId = resultId,
        reportType = "SYSTEM",
        totalScore = BigDecimal("15"),
        riskLevel = "MODERATE",
        content = "report content"
    )

    // ── findDetail(reportId) ──────────────────────────────────────────────────

    @Test
    fun `findDetail throws REPORT_NOT_FOUND when repository returns null`() {
        `when`(reportRepository.findDetailById(99L)).thenReturn(null)

        val ex = assertThrows<BizException> { reportService.findDetail(99L) }
        assertEquals("REPORT_NOT_FOUND", ex.code)
        verify(securityAuditService, never()).recordReportViewed(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        )
    }

    @Test
    fun `findDetail returns detail and records audit`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L)
        `when`(reportRepository.findDetailById(10L)).thenReturn(detail)

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
    fun `findDetail with audit=false skips audit recording`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L)
        `when`(reportRepository.findDetailById(10L)).thenReturn(detail)

        val result = reportService.findDetail(10L, audit = false)

        assertEquals(10L, result.reportId)
        verify(securityAuditService, never()).recordReportViewed(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        )
    }

    // ── findDetailByResultId ──────────────────────────────────────────────────

    @Test
    fun `findDetailByResultId throws REPORT_NOT_FOUND when repository returns null`() {
        `when`(reportRepository.findDetailByResultId(99L)).thenReturn(null)

        val ex = assertThrows<BizException> { reportService.findDetailByResultId(99L) }
        assertEquals("REPORT_NOT_FOUND", ex.code)
    }

    @Test
    fun `findDetailByResultId returns detail and records audit with RESULT_ID accessPath`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L)
        `when`(reportRepository.findDetailByResultId(20L)).thenReturn(detail)

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
    fun `findDetailByResultId with audit=false skips audit recording`() {
        val detail = makeDetail(reportId = 10L, resultId = 20L)
        `when`(reportRepository.findDetailByResultId(20L)).thenReturn(detail)

        reportService.findDetailByResultId(20L, audit = false)

        verify(securityAuditService, never()).recordReportViewed(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        )
    }

    // ── findMyReports ─────────────────────────────────────────────────────────

    @Test
    fun `findMyReports returns reports for current user`() {
        val summary = MyReportSummary(
            reportId = 10L, resultId = 20L, taskId = 1L, taskName = "春季普查",
            scaleName = "PHQ-9", reportType = "SYSTEM",
            totalScore = BigDecimal("15"), riskLevel = "MODERATE",
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
}
