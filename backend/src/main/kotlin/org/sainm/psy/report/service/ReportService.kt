package org.sainm.psy.report.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.report.domain.MyReportSummary
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.repository.ReportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val securityAuditService: SecurityAuditService,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages
) {

    fun findDetail(reportId: Long): ReportDetail = findDetail(reportId, audit = true)

    fun findDetail(reportId: Long, audit: Boolean): ReportDetail {
        val detail = reportRepository.findDetailById(reportId)
            ?: throw BizException("REPORT_NOT_FOUND", "Report not found")
        requireReportAccess(detail)
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
            ?: throw BizException("REPORT_NOT_FOUND", "Report not found")
        requireReportAccess(detail)
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

    @Transactional
    fun regenerate(reportId: Long): ReportDetail {
        val currentUser = currentUserFacade.requireCurrentUser()
        val oldDetail = reportRepository.findDetailById(reportId)
            ?: throw BizException("REPORT_NOT_FOUND", "Report not found")
        requireReportAccess(oldDetail, currentUser)

        val newReportId = reportRepository.createSystemReportVersion(
            resultId = oldDetail.resultId,
            authorUserId = currentUser.userId,
            title = messages.get("report.system.title"),
            content = buildRegeneratedReportContent(oldDetail)
        )
        securityAuditService.recordReportRegenerated(
            oldReportId = reportId,
            newReportId = newReportId,
            resultId = oldDetail.resultId,
            riskLevel = oldDetail.riskLevel
        )
        return reportRepository.findDetailById(newReportId)
            ?: throw BizException("REPORT_NOT_FOUND", "Report not found")
    }

    private fun requireReportAccess(detail: ReportDetail) {
        val currentUser = currentUserFacade.requireCurrentUser()
        requireReportAccess(detail, currentUser)
    }

    private fun requireReportAccess(detail: ReportDetail, currentUser: UserPrincipal) {
        if (detail.userId == currentUser.userId || currentUser.roles.any { it in REPORT_DETAIL_PRIVILEGED_ROLES }) {
            return
        }
        throw BizException("REPORT_FORBIDDEN", "You are not allowed to access this report")
    }

    private fun buildRegeneratedReportContent(detail: ReportDetail): String {
        val scoreText = detail.totalScore.stripTrailingZeros().toPlainString()
        return buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", scoreText)).append("\n")
            append(messages.get("report.auto.risk", detail.riskLevel)).append("\n")
            detail.standardScore?.let {
                append(messages.get("report.auto.standard", detail.scoreSource, it.stripTrailingZeros().toPlainString())).append("\n")
            }
            append(messages.get("report.regenerated.source", detail.reportId))
        }
    }

    companion object {
        private val REPORT_DETAIL_PRIVILEGED_ROLES = setOf(
            "COUNSELOR",
            "ASSESSMENT_ADMIN",
            "ORG_MANAGER",
            "ADMIN",
            "SYS_ADMIN",
            "SUPER_ADMIN"
        )
    }
}
