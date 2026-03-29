package org.sainm.psy.statistics.service

import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.statistics.api.GroupReportListQuery
import org.sainm.psy.statistics.domain.DashboardStatisticsResponse
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.statistics.repository.StatisticsRepository
import org.springframework.stereotype.Service

@Service
class StatisticsService(
    private val statisticsRepository: StatisticsRepository
) {

    fun dashboard(): DashboardStatisticsResponse =
        statisticsRepository.loadDashboard()

    fun groupReports(query: GroupReportListQuery): PageResponse<GroupReportSummary> {
        require(query.page > 0) { "page 必须大于 0" }
        require(query.size in 1..200) { "size 必须在 1 到 200 之间" }
        val (list, total) = statisticsRepository.findGroupReportPage(query)
        val enriched = list.map { summary ->
            val compareUserResult = summary.compareUserResult?.let { comparison ->
                comparison.copy(
                    userId = query.compareUserId ?: comparison.userId,
                    scoreGapToAverage = summary.averageScore?.let { average ->
                        comparison.totalScore.subtract(average)
                    }
                )
            }
            summary.copy(
                compareUserResult = compareUserResult,
                dimensionStats = statisticsRepository.findDimensionStats(summary.taskId, summary.groupId)
            )
        }
        return PageResponse(
            list = enriched,
            page = query.page,
            size = query.size,
            total = total
        )
    }
}
