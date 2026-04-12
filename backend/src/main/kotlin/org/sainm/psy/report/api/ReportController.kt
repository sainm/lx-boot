package org.sainm.psy.report.api

import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.report.domain.MyReportSummary
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.service.ReportService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reports")
class ReportController(
    private val reportService: ReportService
) {

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun findDetail(@PathVariable id: Long): ApiResponse<ReportDetail> =
        ApiResponse.ok(reportService.findDetail(id))

    @GetMapping("/by-result/{resultId}")
    @PreAuthorize("isAuthenticated()")
    fun findDetailByResultId(@PathVariable resultId: Long): ApiResponse<ReportDetail> =
        ApiResponse.ok(reportService.findDetailByResultId(resultId))

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    fun findMyReports(): ApiResponse<List<MyReportSummary>> =
        ApiResponse.ok(reportService.findMyReports())

    @PostMapping("/{id}/regenerate")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun regenerate(@PathVariable id: Long): ApiResponse<ReportDetail> =
        ApiResponse.ok(reportService.regenerate(id))
}
