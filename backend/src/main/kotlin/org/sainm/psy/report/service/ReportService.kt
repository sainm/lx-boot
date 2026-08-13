package org.sainm.psy.report.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.report.domain.MyReportSummary
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.domain.ReportSearchQuery
import org.sainm.psy.report.domain.StaffReportSummary
import org.sainm.psy.report.repository.ReportRepository
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val securityAuditService: SecurityAuditService,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val visualizationService: VisualizationService,
    private val tenantAccessPolicy: TenantAccessPolicy
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
        return detail.withPresentation().withVisualizations()
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
        return detail.withPresentation().withVisualizations()
    }

    fun findDetailForSystemExport(reportId: Long?, resultId: Long?): ReportDetail =
        (when {
            reportId != null && resultId != null -> {
                val detail = reportRepository.findDetailById(reportId)
                    ?: throw BizException("REPORT_NOT_FOUND", "Report not found")
                if (detail.resultId != resultId) {
                    throw BizException("EXPORT_REPORT_MISMATCH", messages.get("export.report_mismatch"))
                }
                detail
            }
            reportId != null -> reportRepository.findDetailById(reportId)
                ?: throw BizException("REPORT_NOT_FOUND", "Report not found")
            resultId != null -> reportRepository.findDetailByResultId(resultId)
                ?: throw BizException("REPORT_NOT_FOUND", "Report not found")
            else -> throw BizException("EXPORT_PARAM_REQUIRED", messages.get("export.param_required"))
        }).withPresentation()

    fun findMyReports(): List<MyReportSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return reportRepository.findMyReports(currentUser.userId)
    }

    fun findUserReports(userId: Long): List<MyReportSummary> {
        val currentUser = requirePrivilegedReportAccess()
        val tenantId = tenantAccessPolicy.currentTenantFilter("REPORT", "LIST_BY_USER")
        return reportRepository.findReportsByUserId(userId, tenantId)
    }

    fun searchReports(query: ReportSearchQuery): PageResponse<StaffReportSummary> {
        require(query.page > 0) { "page must be greater than 0" }
        require(query.size in 1..100) { "size must be between 1 and 100" }
        val currentUser = requirePrivilegedReportAccess()
        val tenantId = tenantAccessPolicy.currentTenantFilter("REPORT", "SEARCH")
        return PageResponse(
            list = reportRepository.searchReports(query, tenantId),
            page = query.page,
            size = query.size,
            total = reportRepository.countSearchReports(query, tenantId)
        )
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
            title = messages.getForLocale(oldDetail.localeCode, "report.system.title"),
            content = buildRegeneratedReportContent(oldDetail)
        )
        securityAuditService.recordReportRegenerated(
            oldReportId = reportId,
            newReportId = newReportId,
            resultId = oldDetail.resultId,
            riskLevel = oldDetail.riskLevel
        )
        return reportRepository.findDetailById(newReportId)
            ?.withPresentation()
            ?.withVisualizations()
            ?: throw BizException("REPORT_NOT_FOUND", "Report not found")
    }

    /**
     * Split the immutable scale-specific report snapshot into the sections
     * consumed by Web and export renderers. The parser only understands the
     * system's localized section markers; unrecognised/manual reports remain
     * available through `content` and are never replaced with a generic
     * recommendation.
     */
    private fun ReportDetail.withPresentation(): ReportDetail {
        val normalized = content.replace("\r\n", "\n")
        fun localizedMarker(key: String): String? = runCatching {
            messages.getForLocale(localeCode, key)
        }.getOrNull()?.takeIf { it.isNotBlank() }

        // Presentation enrichment is best effort.  A missing translation must
        // never make an otherwise readable immutable report unavailable.
        val interpretationMarker = localizedMarker("report.auto.section.interpretation")
        val dimensionMarker = localizedMarker("report.auto.section.dimensions")
        val suggestionMarker = localizedMarker("report.auto.section.suggestion")
        val disclaimerMarker = localizedMarker("report.auto.disclaimer")

        fun sectionAfter(marker: String, endMarkers: List<String>): String? {
            val markerIndex = normalized.indexOf(marker)
            if (markerIndex < 0) return null
            val start = markerIndex + marker.length
            val end = endMarkers.mapNotNull { candidate ->
                normalized.indexOf(candidate, start).takeIf { it >= 0 }
            }.minOrNull() ?: normalized.length
            return normalized.substring(start, end).trim().takeIf { it.isNotBlank() }
        }

        val resultDescription = interpretationMarker?.let {
            sectionAfter(it, listOfNotNull(dimensionMarker, suggestionMarker, disclaimerMarker))
        }
        val suggestionText = suggestionMarker?.let {
            sectionAfter(it, listOfNotNull(disclaimerMarker))
        }
        return copy(resultDescription = resultDescription, suggestionText = suggestionText)
    }

    private fun ReportDetail.withVisualizations(): ReportDetail =
        copy(visualizations = runCatching { visualizationService.buildReportVisualizations(this) }.getOrNull().orEmpty())

    private fun requireReportAccess(detail: ReportDetail) {
        val currentUser = currentUserFacade.requireCurrentUser()
        requireReportAccess(detail, currentUser)
    }

    private fun requireReportAccess(detail: ReportDetail, currentUser: UserPrincipal) {
        if (!tenantAccessPolicy.canAccess(detail.tenantId, "REPORT", detail.reportId, "READ_OR_MUTATE")) {
            throw BizException("REPORT_NOT_FOUND", "Report not found")
        }
        if (detail.userId == currentUser.userId || currentUser.roles.any { it in REPORT_DETAIL_PRIVILEGED_ROLES }) {
            return
        }
        throw BizException("REPORT_FORBIDDEN", "You are not allowed to access this report")
    }

    private fun requirePrivilegedReportAccess(): UserPrincipal {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (currentUser.roles.any { it in REPORT_DETAIL_PRIVILEGED_ROLES }) {
            return currentUser
        }
        throw BizException("REPORT_FORBIDDEN", "You are not allowed to access this report")
    }

    private fun buildRegeneratedReportContent(detail: ReportDetail): String {
        // Reports created before report_template was governed used the legacy
        // generic regeneration layout. Preserve that compatibility path while
        // retaining the complete immutable scale-specific snapshot for any
        // scale that declares a presentation code.
        if (detail.reportTemplate.isNullOrBlank()) {
            val scoreText = detail.totalScore.stripTrailingZeros().toPlainString()
            return buildString {
                append(messages.getForLocale(detail.localeCode, "report.auto.header")).append("\n")
                append(messages.getForLocale(detail.localeCode, "report.auto.score", scoreText)).append("\n")
                append(messages.getForLocale(detail.localeCode, "report.auto.risk", detail.riskLevel)).append("\n")
                detail.standardScore?.let {
                    append(messages.getForLocale(detail.localeCode, "report.auto.standard", detail.scoreSource, it.stripTrailingZeros().toPlainString())).append("\n")
                }
                append(messages.getForLocale(detail.localeCode, "report.regenerated.source", detail.reportId))
            }
        }
        return buildString {
            append(detail.content.trim())
            if (detail.content.isNotBlank()) append("\n\n")
            append(messages.getForLocale(detail.localeCode, "report.regenerated.source", detail.reportId))
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
