package org.sainm.psy.statistics.api

import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.statistics.domain.DashboardStatisticsResponse
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.statistics.service.StatisticsService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/statistics")
class StatisticsController(
    private val statisticsService: StatisticsService
) {

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun dashboard(): ApiResponse<DashboardStatisticsResponse> =
        ApiResponse.ok(statisticsService.dashboard())

    @GetMapping("/group-reports")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun groupReports(
        @RequestParam(required = false) taskId: Long?,
        @RequestParam(required = false) groupId: Long?,
        @RequestParam(required = false) scaleId: Long?,
        @RequestParam(required = false) compareUserId: Long?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<GroupReportSummary>> =
        ApiResponse.ok(
            statisticsService.groupReports(
                GroupReportListQuery(
                    taskId = taskId,
                    groupId = groupId,
                    scaleId = scaleId,
                    compareUserId = compareUserId,
                    page = page,
                    size = size
                )
            )
        )
}
