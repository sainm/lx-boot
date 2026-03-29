package org.sainm.psy.report.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.report.domain.MyReportSummary
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.repository.ReportRepository
import org.springframework.stereotype.Service

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val securityAuditService: SecurityAuditService,
    private val currentUserFacade: CurrentUserFacade
) {

    fun findDetail(reportId: Long): ReportDetail = findDetail(reportId, audit = true)

    fun findDetail(reportId: Long, audit: Boolean): ReportDetail {
        val detail = reportRepository.findDetailById(reportId)
            ?: throw BizException("REPORT_NOT_FOUND", "报告不存在")
        if (audit) {
            securityAuditService.recordReportViewed(
                reportId = detail.reportId,
                resultId = detail.resultId,
                reportType = detail.reportType,
                riskLevel = detail.riskLevel,
                accessPath = "REPORT_ID"
            )
        }
        return detail
    }

    fun findDetailByResultId(resultId: Long): ReportDetail = findDetailByResultId(resultId, audit = true)

    fun findDetailByResultId(resultId: Long, audit: Boolean): ReportDetail {
        val detail = reportRepository.findDetailByResultId(resultId)
            ?: throw BizException("REPORT_NOT_FOUND", "报告不存在")
        if (audit) {
            securityAuditService.recordReportViewed(
                reportId = detail.reportId,
                resultId = detail.resultId,
                reportType = detail.reportType,
                riskLevel = detail.riskLevel,
                accessPath = "RESULT_ID"
            )
        }
        return detail
    }

    fun findMyReports(): List<MyReportSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return reportRepository.findMyReports(currentUser.userId)
    }
}
