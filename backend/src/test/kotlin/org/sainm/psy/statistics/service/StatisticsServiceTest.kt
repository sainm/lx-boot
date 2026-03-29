package org.sainm.psy.statistics.service

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
import org.sainm.psy.statistics.api.GroupReportListQuery
import org.sainm.psy.statistics.domain.DashboardStatisticsResponse
import org.sainm.psy.statistics.domain.GroupDimensionStat
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.statistics.domain.GroupUserComparison
import org.sainm.psy.statistics.domain.KeyValueCount
import org.sainm.psy.statistics.repository.StatisticsRepository
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class StatisticsServiceTest {

    @Mock private lateinit var statisticsRepository: StatisticsRepository

    @InjectMocks
    private lateinit var statisticsService: StatisticsService

    private val emptyDashboard = DashboardStatisticsResponse(
        generatedAt = LocalDateTime.now(),
        overviewCards = emptyList(),
        taskStatusDistribution = emptyList(),
        riskDistribution = emptyList(),
        submissionTrend = emptyList(),
        warningTrend = emptyList(),
        recentWarnings = emptyList(),
        recentReports = emptyList()
    )

    private fun makeSummary(
        taskId: Long = 1L,
        groupId: Long = 10L,
        averageScore: BigDecimal? = BigDecimal("12"),
        compareUserResult: GroupUserComparison? = null
    ) = GroupReportSummary(
        taskId = taskId,
        taskName = "春季普查",
        scaleId = 2L,
        scaleName = "PHQ-9",
        groupId = groupId,
        groupName = "Group A",
        memberCount = 50L,
        submittedCount = 40L,
        completionRate = BigDecimal("0.8"),
        averageScore = averageScore,
        highRiskCount = 3L,
        warningCount = 2L,
        riskDistribution = listOf(KeyValueCount("NORMAL", 37L), KeyValueCount("MODERATE", 3L)),
        latestSubmittedAt = LocalDateTime.now()
    )

    // ── dashboard ─────────────────────────────────────────────────────────────

    @Test
    fun `dashboard delegates to repository`() {
        `when`(statisticsRepository.loadDashboard()).thenReturn(emptyDashboard)

        val result = statisticsService.dashboard()

        assertEquals(emptyDashboard, result)
        verify(statisticsRepository).loadDashboard()
    }

    // ── groupReports validation ───────────────────────────────────────────────

    @Test
    fun `groupReports throws when page is 0`() {
        assertThrows<IllegalArgumentException> {
            statisticsService.groupReports(GroupReportListQuery(page = 0, size = 20))
        }
        verify(statisticsRepository, never()).findGroupReportPage(
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `groupReports throws when size is out of range`() {
        assertThrows<IllegalArgumentException> {
            statisticsService.groupReports(GroupReportListQuery(page = 1, size = 0))
        }
        assertThrows<IllegalArgumentException> {
            statisticsService.groupReports(GroupReportListQuery(page = 1, size = 201))
        }
    }

    // ── groupReports enrichment ───────────────────────────────────────────────

    @Test
    fun `groupReports enriches summaries with dimensionStats`() {
        val query = GroupReportListQuery(page = 1, size = 20)
        val summary = makeSummary(taskId = 1L, groupId = 10L)
        val dimStats = listOf(
            GroupDimensionStat(dimensionId = 1L, dimensionName = "Anxiety", averageScore = BigDecimal("5"), answerCount = 40L)
        )
        `when`(statisticsRepository.findGroupReportPage(query)).thenReturn(listOf(summary) to 1L)
        `when`(statisticsRepository.findDimensionStats(1L, 10L)).thenReturn(dimStats)

        val result = statisticsService.groupReports(query)

        assertEquals(1, result.list.size)
        assertEquals(1L, result.total)
        assertEquals(dimStats, result.list[0].dimensionStats)
        verify(statisticsRepository).findDimensionStats(1L, 10L)
    }

    @Test
    fun `groupReports computes scoreGapToAverage when compareUserResult and averageScore are present`() {
        val comparison = GroupUserComparison(
            userId = 5L,
            displayName = "Alice",
            totalScore = BigDecimal("16"),
            riskLevel = "MODERATE"
        )
        val summary = makeSummary(taskId = 1L, groupId = 10L, averageScore = BigDecimal("12"), compareUserResult = comparison)
        val query = GroupReportListQuery(page = 1, size = 20)

        `when`(statisticsRepository.findGroupReportPage(query)).thenReturn(listOf(summary) to 1L)
        `when`(statisticsRepository.findDimensionStats(1L, 10L)).thenReturn(emptyList())

        val result = statisticsService.groupReports(query)

        val enrichedComparison = result.list[0].compareUserResult!!
        assertEquals(BigDecimal("4"), enrichedComparison.scoreGapToAverage) // 16 - 12
    }

    @Test
    fun `groupReports leaves scoreGapToAverage null when averageScore is null`() {
        val comparison = GroupUserComparison(
            userId = 5L,
            displayName = "Alice",
            totalScore = BigDecimal("16"),
            riskLevel = "MODERATE"
        )
        val summary = makeSummary(taskId = 1L, groupId = 10L, averageScore = null, compareUserResult = comparison)
        val query = GroupReportListQuery(page = 1, size = 20)

        `when`(statisticsRepository.findGroupReportPage(query)).thenReturn(listOf(summary) to 1L)
        `when`(statisticsRepository.findDimensionStats(1L, 10L)).thenReturn(emptyList())

        val result = statisticsService.groupReports(query)

        val enrichedComparison = result.list[0].compareUserResult!!
        assertEquals(null, enrichedComparison.scoreGapToAverage)
    }

    @Test
    fun `groupReports uses compareUserId from query to override comparison userId when provided`() {
        val comparison = GroupUserComparison(
            userId = 99L, // original userId in the comparison
            displayName = "Alice",
            totalScore = BigDecimal("10"),
            riskLevel = "NORMAL"
        )
        val summary = makeSummary(taskId = 1L, groupId = 10L, averageScore = BigDecimal("8"), compareUserResult = comparison)
        val query = GroupReportListQuery(page = 1, size = 20, compareUserId = 5L) // explicit override

        `when`(statisticsRepository.findGroupReportPage(query)).thenReturn(listOf(summary) to 1L)
        `when`(statisticsRepository.findDimensionStats(1L, 10L)).thenReturn(emptyList())

        val result = statisticsService.groupReports(query)

        val enrichedComparison = result.list[0].compareUserResult!!
        assertEquals(5L, enrichedComparison.userId) // overridden by query.compareUserId
    }

    @Test
    fun `groupReports returns empty list when repository returns no rows`() {
        val query = GroupReportListQuery(page = 1, size = 20)
        `when`(statisticsRepository.findGroupReportPage(query)).thenReturn(emptyList<GroupReportSummary>() to 0L)

        val result = statisticsService.groupReports(query)

        assertEquals(0, result.list.size)
        assertEquals(0L, result.total)
        verify(statisticsRepository, never()).findDimensionStats(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong()
        )
    }
}
