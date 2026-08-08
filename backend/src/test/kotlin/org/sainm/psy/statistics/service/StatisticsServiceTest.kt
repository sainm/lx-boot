package org.sainm.psy.statistics.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.statistics.api.GroupReportListQuery
import org.sainm.psy.statistics.domain.DashboardStatisticsResponse
import org.sainm.psy.statistics.domain.GroupDimensionStat
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.statistics.domain.GroupUserComparison
import org.sainm.psy.statistics.domain.KeyValueCount
import org.sainm.psy.statistics.repository.StatisticsRepository
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class StatisticsServiceTest {

    @Mock private lateinit var statisticsRepository: StatisticsRepository
    @Mock private lateinit var visualizationService: VisualizationService
    @Mock private lateinit var currentUserFacade: CurrentUserFacade

    private lateinit var statisticsService: StatisticsService

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        statisticsService = StatisticsService(
            statisticsRepository = statisticsRepository,
            messages = LocalizedMessages(messageSource),
            metricPolicy = StatisticsMetricPolicy(),
            visualizationService = visualizationService,
            currentUserFacade = currentUserFacade
        )
        org.mockito.Mockito.lenient().`when`(currentUserFacade.requireCurrentUser()).thenReturn(
            UserPrincipal(
                userId = 9L,
                username = "manager",
                displayName = "Manager",
                status = UserStatus.ENABLED,
                tenantId = 7L,
                groupId = null,
                roles = setOf("ORG_MANAGER"),
                permissions = emptySet()
            )
        )
    }

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
        taskName = "Spring Survey",
        scaleId = 2L,
        scaleName = "PHQ-9",
        groupId = groupId,
        groupName = "Group A",
        memberCount = 50L,
        submittedCount = 40L,
        completionRate = BigDecimal("80.00"),
        averageScore = averageScore,
        highRiskCount = 3L,
        warningCount = 2L,
        riskDistribution = listOf(KeyValueCount("NORMAL", 37L), KeyValueCount("MODERATE", 3L)),
        compareUserResult = compareUserResult,
        dimensionStats = emptyList(),
        latestSubmittedAt = LocalDateTime.now()
    )

    @Test
    fun `dashboard delegates to repository`() {
        `when`(statisticsRepository.loadDashboard(7L)).thenReturn(emptyDashboard)

        val result = statisticsService.dashboard()

        assertEquals(emptyDashboard, result)
        verify(statisticsRepository).loadDashboard(7L)
    }

    @Test
    fun `groupReports throws when page is 0`() {
        assertThrows<IllegalArgumentException> {
            statisticsService.groupReports(GroupReportListQuery(page = 0, size = 20))
        }
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

    @Test
    fun `groupReports enriches summaries with dimensionStats`() {
        val query = GroupReportListQuery(page = 1, size = 20)
        val summary = makeSummary()
        val dimStats = listOf(
            GroupDimensionStat(dimensionId = 1L, dimensionName = "Anxiety", averageScore = BigDecimal("5"), answerCount = 40L)
        )
        `when`(statisticsRepository.findGroupReportPage(query, 7L)).thenReturn(listOf(summary) to 1L)
        `when`(statisticsRepository.findDimensionStats(1L, 10L, 7L)).thenReturn(dimStats)

        val result = statisticsService.groupReports(query)

        assertEquals(dimStats, result.list[0].dimensionStats)
    }

    @Test
    fun `groupReports computes scoreGapToAverage when compareUserResult and averageScore are present`() {
        val comparison = GroupUserComparison(
            userId = 5L,
            displayName = "Alice",
            totalScore = BigDecimal("16"),
            riskLevel = "MODERATE",
            scoreGapToAverage = null
        )
        val summary = makeSummary(compareUserResult = comparison)
        val query = GroupReportListQuery(page = 1, size = 20)

        `when`(statisticsRepository.findGroupReportPage(query, 7L)).thenReturn(listOf(summary) to 1L)
        `when`(statisticsRepository.findDimensionStats(1L, 10L, 7L)).thenReturn(emptyList())

        val result = statisticsService.groupReports(query)

        assertEquals(BigDecimal("4.0000"), result.list[0].compareUserResult!!.scoreGapToAverage)
    }

    @Test
    fun `groupReports leaves scoreGapToAverage null when averageScore is null`() {
        val comparison = GroupUserComparison(
            userId = 5L,
            displayName = "Alice",
            totalScore = BigDecimal("16"),
            riskLevel = "MODERATE",
            scoreGapToAverage = null
        )
        val summary = makeSummary(averageScore = null, compareUserResult = comparison)
        val query = GroupReportListQuery(page = 1, size = 20)

        `when`(statisticsRepository.findGroupReportPage(query, 7L)).thenReturn(listOf(summary) to 1L)
        `when`(statisticsRepository.findDimensionStats(1L, 10L, 7L)).thenReturn(emptyList())

        val result = statisticsService.groupReports(query)

        assertNull(result.list[0].compareUserResult!!.scoreGapToAverage)
    }

    @Test
    fun `groupReports uses compareUserId from query to override comparison userId when provided`() {
        val comparison = GroupUserComparison(
            userId = 99L,
            displayName = "Alice",
            totalScore = BigDecimal("10"),
            riskLevel = "NORMAL",
            scoreGapToAverage = null
        )
        val summary = makeSummary(averageScore = BigDecimal("8"), compareUserResult = comparison)
        val query = GroupReportListQuery(page = 1, size = 20, compareUserId = 5L)

        `when`(statisticsRepository.findGroupReportPage(query, 7L)).thenReturn(listOf(summary) to 1L)
        `when`(statisticsRepository.findDimensionStats(1L, 10L, 7L)).thenReturn(emptyList())

        val result = statisticsService.groupReports(query)

        assertEquals(5L, result.list[0].compareUserResult!!.userId)
    }

    @Test
    fun `groupReports returns empty list when repository returns no rows`() {
        val query = GroupReportListQuery(page = 1, size = 20)
        `when`(statisticsRepository.findGroupReportPage(query, 7L)).thenReturn(emptyList<GroupReportSummary>() to 0L)

        val result = statisticsService.groupReports(query)

        assertEquals(0, result.list.size)
        assertEquals(0L, result.total)
        verify(statisticsRepository, never()).findDimensionStats(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong()
        )
    }
}
