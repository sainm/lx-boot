package org.sainm.psy.statistics.repository

import org.sainm.psy.statistics.api.GroupReportListQuery
import org.sainm.psy.statistics.domain.DashboardMetricCard
import org.sainm.psy.statistics.domain.DashboardRecentReportItem
import org.sainm.psy.statistics.domain.DashboardRecentWarningItem
import org.sainm.psy.statistics.domain.DashboardStatisticsResponse
import org.sainm.psy.statistics.domain.DashboardTrendPoint
import org.sainm.psy.statistics.domain.GroupDimensionStat
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.statistics.domain.GroupUserComparison
import org.sainm.psy.statistics.domain.KeyValueCount
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class StatisticsRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun loadDashboard(): DashboardStatisticsResponse {
        val submittedSheetCount = count(
            """
                select count(1)
                from psy_assessment_answer_sheet
                where answer_status = 'SUBMITTED'
            """.trimIndent()
        )
        val assignedParticipantCount = count(
            """
                select coalesce(sum(case
                    when a.target_type = 'USER' then 1
                    when a.target_type = 'GROUP' then coalesce((
                        select count(1)
                        from sys_user u
                        where u.group_id = a.target_id
                          and coalesce(u.deleted, 0) = 0
                          and coalesce(u.status, 1) = 1
                    ), 0)
                    else 0
                end), 0)
                from psy_assessment_task_assignment a
            """.trimIndent()
        )
        val completionRate = if (assignedParticipantCount == 0L) {
            BigDecimal.ZERO
        } else {
            BigDecimal.valueOf(submittedSheetCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(assignedParticipantCount), 2, RoundingMode.HALF_UP)
        }

        val startTime = Timestamp.valueOf(LocalDate.now().minusDays(6).atStartOfDay())
        val trendParams = MapSqlParameterSource().addValue("startTime", startTime)

        return DashboardStatisticsResponse(
            generatedAt = LocalDateTime.now(),
            overviewCards = listOf(
                DashboardMetricCard("totalScales", "量表总数", BigDecimal.valueOf(count("select count(1) from psy_scale"))),
                DashboardMetricCard("totalTasks", "测评任务总数", BigDecimal.valueOf(count("select count(1) from psy_assessment_task"))),
                DashboardMetricCard("submittedSheets", "已提交答卷", BigDecimal.valueOf(submittedSheetCount)),
                DashboardMetricCard("completionRate", "任务完成率", completionRate, suffix = "%"),
                DashboardMetricCard(
                    "highRiskWarnings",
                    "高风险预警",
                    BigDecimal.valueOf(
                        count(
                            """
                                select count(1)
                                from psy_warning_record
                                where warning_level = 'HIGH'
                                  and status <> 'CLOSED'
                            """.trimIndent()
                        )
                    )
                ),
                DashboardMetricCard(
                    "pendingWarnings",
                    "待处理预警",
                    BigDecimal.valueOf(
                        count(
                            """
                                select count(1)
                                from psy_warning_record
                                where status in ('PENDING', 'ASSIGNED', 'PROCESSING')
                            """.trimIndent()
                        )
                    )
                )
            ),
            taskStatusDistribution = queryKeyValueCounts(
                """
                    select status as key_name, count(1) as total_count
                    from psy_assessment_task
                    group by status
                    order by status
                """.trimIndent()
            ),
            riskDistribution = queryKeyValueCounts(
                """
                    select risk_level as key_name, count(1) as total_count
                    from psy_assessment_result
                    group by risk_level
                    order by risk_level
                """.trimIndent()
            ),
            submissionTrend = queryDailyTrend(
                """
                    select cast(submit_time as date) as stat_day, count(1) as total_count
                    from psy_assessment_answer_sheet
                    where answer_status = 'SUBMITTED'
                      and submit_time >= :startTime
                    group by cast(submit_time as date)
                    order by stat_day
                """.trimIndent(),
                trendParams
            ),
            warningTrend = queryDailyTrend(
                """
                    select cast(created_at as date) as stat_day, count(1) as total_count
                    from psy_warning_record
                    where created_at >= :startTime
                    group by cast(created_at as date)
                    order by stat_day
                """.trimIndent(),
                trendParams
            ),
            recentWarnings = queryRecentWarnings(),
            recentReports = queryRecentReports()
        )
    }

    fun findGroupReportPage(query: GroupReportListQuery): Pair<List<GroupReportSummary>, Long> {
        val offset = (query.page - 1).coerceAtLeast(0) * query.size
        val params = MapSqlParameterSource()
            .addValue("taskId", query.taskId)
            .addValue("groupId", query.groupId)
            .addValue("scaleId", query.scaleId)
            .addValue("compareUserId", query.compareUserId)
            .addValue("limit", query.size)
            .addValue("offset", offset)

        val whereClause = buildString {
            append(" where a.target_type = 'GROUP' ")
            if (params.hasValue("taskId")) append(" and t.id = :taskId ")
            if (params.hasValue("groupId")) append(" and a.target_id = :groupId ")
            if (params.hasValue("scaleId")) append(" and t.scale_id = :scaleId ")
        }

        val listSql = """
            select
                t.id as task_id,
                t.task_name,
                t.scale_id,
                s.scale_name,
                a.target_id as group_id,
                coalesce(g.group_name, ('Group-' || cast(a.target_id as varchar(32)))) as group_name,
                (
                    select count(1)
                    from sys_user u
                    where u.group_id = a.target_id
                      and coalesce(u.deleted, 0) = 0
                      and coalesce(u.status, 1) = 1
                ) as member_count,
                (
                    select count(distinct sh.user_id)
                    from psy_assessment_answer_sheet sh
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and sh.answer_status = 'SUBMITTED'
                      and u.group_id = a.target_id
                      and coalesce(u.deleted, 0) = 0
                      and coalesce(u.status, 1) = 1
                ) as submitted_count,
                (
                    select avg(ar.total_score)
                    from psy_assessment_answer_sheet sh
                    join psy_assessment_result ar on ar.answer_sheet_id = sh.id
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and sh.answer_status = 'SUBMITTED'
                      and u.group_id = a.target_id
                      and coalesce(u.deleted, 0) = 0
                      and coalesce(u.status, 1) = 1
                ) as average_score,
                (
                    select count(1)
                    from psy_assessment_answer_sheet sh
                    join psy_assessment_result ar on ar.answer_sheet_id = sh.id
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and sh.answer_status = 'SUBMITTED'
                      and u.group_id = a.target_id
                      and coalesce(u.deleted, 0) = 0
                      and coalesce(u.status, 1) = 1
                      and (ar.warning_flag = true or ar.risk_level = 'HIGH')
                ) as high_risk_count,
                (
                    select count(1)
                    from psy_warning_record w
                    join psy_assessment_result ar on ar.id = w.result_id
                    join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and u.group_id = a.target_id
                      and w.status <> 'CLOSED'
                ) as warning_count,
                (
                    select count(1)
                    from psy_assessment_answer_sheet sh
                    join psy_assessment_result ar on ar.answer_sheet_id = sh.id
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and sh.answer_status = 'SUBMITTED'
                      and u.group_id = a.target_id
                      and coalesce(u.deleted, 0) = 0
                      and coalesce(u.status, 1) = 1
                      and ar.risk_level = 'NORMAL'
                ) as normal_count,
                (
                    select count(1)
                    from psy_assessment_answer_sheet sh
                    join psy_assessment_result ar on ar.answer_sheet_id = sh.id
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and sh.answer_status = 'SUBMITTED'
                      and u.group_id = a.target_id
                      and coalesce(u.deleted, 0) = 0
                      and coalesce(u.status, 1) = 1
                      and ar.risk_level = 'ATTENTION'
                ) as attention_count,
                (
                    select count(1)
                    from psy_assessment_answer_sheet sh
                    join psy_assessment_result ar on ar.answer_sheet_id = sh.id
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and sh.answer_status = 'SUBMITTED'
                      and u.group_id = a.target_id
                      and coalesce(u.deleted, 0) = 0
                      and coalesce(u.status, 1) = 1
                      and ar.risk_level = 'HIGH'
                ) as high_count,
                (
                    select max(sh.submit_time)
                    from psy_assessment_answer_sheet sh
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and sh.answer_status = 'SUBMITTED'
                      and u.group_id = a.target_id
                      and coalesce(u.deleted, 0) = 0
                      and coalesce(u.status, 1) = 1
                ) as latest_submitted_at,
                (
                    select ar.total_score
                    from psy_assessment_answer_sheet sh
                    join psy_assessment_result ar on ar.answer_sheet_id = sh.id
                    where sh.task_id = t.id
                      and sh.user_id = :compareUserId
                      and sh.answer_status = 'SUBMITTED'
                    order by ar.scored_at desc
                    limit 1
                ) as compare_total_score,
                (
                    select ar.risk_level
                    from psy_assessment_answer_sheet sh
                    join psy_assessment_result ar on ar.answer_sheet_id = sh.id
                    where sh.task_id = t.id
                      and sh.user_id = :compareUserId
                      and sh.answer_status = 'SUBMITTED'
                    order by ar.scored_at desc
                    limit 1
                ) as compare_risk_level,
                (
                    select u.display_name
                    from psy_assessment_answer_sheet sh
                    join sys_user u on u.id = sh.user_id
                    where sh.task_id = t.id
                      and sh.user_id = :compareUserId
                      and sh.answer_status = 'SUBMITTED'
                    order by sh.submit_time desc
                    limit 1
                ) as compare_display_name
            from psy_assessment_task_assignment a
            join psy_assessment_task t on t.id = a.task_id
            join psy_scale s on s.id = t.scale_id
            left join sys_group g on g.id = a.target_id
            $whereClause
            group by t.id, t.task_name, t.scale_id, s.scale_name, a.target_id, g.group_name
            order by t.id desc, a.target_id asc
            limit :limit offset :offset
        """.trimIndent()

        val countSql = """
            select count(1)
            from psy_assessment_task_assignment a
            join psy_assessment_task t on t.id = a.task_id
            $whereClause
        """.trimIndent()

        val list = jdbcTemplate.query(listSql, params, groupReportRowMapper)
        val total = jdbcTemplate.queryForObject(countSql, params, Long::class.java) ?: 0L
        return list to total
    }

    fun findDimensionStats(taskId: Long, groupId: Long): List<GroupDimensionStat> {
        val sql = """
            select
                d.id as dimension_id,
                coalesce(d.dimension_name, '整体') as dimension_name,
                avg(coalesce(ai.score_value, 0)) as average_score,
                count(1) as answer_count
            from psy_assessment_answer_sheet sh
            join psy_assessment_answer_item ai on ai.answer_sheet_id = sh.id
            join psy_scale_question q on q.id = ai.question_id
            left join psy_scale_dimension d on d.id = q.dimension_id
            join sys_user u on u.id = sh.user_id
            where sh.task_id = :taskId
              and sh.answer_status = 'SUBMITTED'
              and u.group_id = :groupId
              and coalesce(u.deleted, 0) = 0
              and coalesce(u.status, 1) = 1
            group by d.id, d.dimension_name, d.sort_no
            order by coalesce(d.sort_no, 999999), d.dimension_name
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("taskId" to taskId, "groupId" to groupId)) { rs, _ ->
            GroupDimensionStat(
                dimensionId = rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
                dimensionName = rs.getString("dimension_name"),
                averageScore = rs.getBigDecimal("average_score") ?: BigDecimal.ZERO,
                answerCount = rs.getLong("answer_count")
            )
        }
    }

    private fun queryDailyTrend(sql: String, params: MapSqlParameterSource): List<DashboardTrendPoint> {
        val raw = jdbcTemplate.query(sql, params) { rs, _ ->
            DashboardTrendPoint(
                day = rs.getDate("stat_day").toLocalDate(),
                count = rs.getLong("total_count")
            )
        }
        val startDay = (params.getValue("startTime") as Timestamp).toLocalDateTime().toLocalDate()
        val byDay = raw.associateBy { it.day }
        return (0..6).map { offset ->
            val day = startDay.plusDays(offset.toLong())
            byDay[day] ?: DashboardTrendPoint(day, 0L)
        }
    }

    private fun queryKeyValueCounts(sql: String): List<KeyValueCount> =
        jdbcTemplate.query(sql) { rs, _ ->
            KeyValueCount(
                key = rs.getString("key_name"),
                value = rs.getLong("total_count")
            )
        }

    private fun queryRecentWarnings(): List<DashboardRecentWarningItem> {
        val sql = """
            select
                w.id as warning_id,
                w.result_id,
                t.task_name,
                s.scale_name,
                w.warning_level,
                w.warning_priority,
                w.status,
                ar.total_score,
                w.created_at
            from psy_warning_record w
            join psy_assessment_result ar on ar.id = w.result_id
            join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
            join psy_assessment_task t on t.id = sh.task_id
            join psy_scale s on s.id = t.scale_id
            order by w.created_at desc
            limit 5
        """.trimIndent()
        return jdbcTemplate.query(sql) { rs, _ ->
            DashboardRecentWarningItem(
                warningId = rs.getLong("warning_id"),
                resultId = rs.getLong("result_id"),
                taskName = rs.getString("task_name"),
                scaleName = rs.getString("scale_name"),
                warningLevel = rs.getString("warning_level"),
                warningPriority = rs.getString("warning_priority"),
                status = rs.getString("status"),
                totalScore = rs.getBigDecimal("total_score"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime()
            )
        }
    }

    private fun queryRecentReports(): List<DashboardRecentReportItem> {
        val sql = """
            select
                r.id as report_id,
                r.result_id,
                t.task_name,
                s.scale_name,
                r.report_type,
                ar.risk_level,
                ar.total_score,
                r.created_at
            from psy_report r
            join psy_assessment_result ar on ar.id = r.result_id
            join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
            join psy_assessment_task t on t.id = sh.task_id
            join psy_scale s on s.id = t.scale_id
            order by r.created_at desc
            limit 5
        """.trimIndent()
        return jdbcTemplate.query(sql) { rs, _ ->
            DashboardRecentReportItem(
                reportId = rs.getLong("report_id"),
                resultId = rs.getLong("result_id"),
                taskName = rs.getString("task_name"),
                scaleName = rs.getString("scale_name"),
                reportType = rs.getString("report_type"),
                riskLevel = rs.getString("risk_level"),
                totalScore = rs.getBigDecimal("total_score"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime()
            )
        }
    }

    private fun count(sql: String): Long =
        jdbcTemplate.queryForObject(sql, emptyMap<String, Any>(), Long::class.java) ?: 0L

    private val groupReportRowMapper = RowMapper { rs, _ ->
        val averageScore = rs.getBigDecimal("average_score")
        val compareTotalScore = rs.getBigDecimal("compare_total_score")
        val compareRiskLevel = rs.getString("compare_risk_level")
        val compareDisplayName = rs.getString("compare_display_name")
        GroupReportSummary(
            taskId = rs.getLong("task_id"),
            taskName = rs.getString("task_name"),
            scaleId = rs.getLong("scale_id"),
            scaleName = rs.getString("scale_name"),
            groupId = rs.getLong("group_id"),
            groupName = rs.getString("group_name"),
            memberCount = rs.getLong("member_count"),
            submittedCount = rs.getLong("submitted_count"),
            completionRate = completionRate(rs.getLong("member_count"), rs.getLong("submitted_count")),
            averageScore = averageScore,
            highRiskCount = rs.getLong("high_risk_count"),
            warningCount = rs.getLong("warning_count"),
            riskDistribution = listOf(
                KeyValueCount("NORMAL", rs.getLong("normal_count")),
                KeyValueCount("ATTENTION", rs.getLong("attention_count")),
                KeyValueCount("HIGH", rs.getLong("high_count"))
            ).filter { it.value > 0 },
            latestSubmittedAt = rs.getTimestamp("latest_submitted_at")?.toLocalDateTime(),
            compareUserResult = if (compareTotalScore != null && compareRiskLevel != null) {
                GroupUserComparison(
                    userId = 0L,
                    displayName = compareDisplayName,
                    totalScore = compareTotalScore,
                    riskLevel = compareRiskLevel
                )
            } else {
                null
            }
        )
    }

    private fun completionRate(memberCount: Long, submittedCount: Long): BigDecimal =
        if (memberCount <= 0) {
            BigDecimal.ZERO
        } else {
            BigDecimal.valueOf(submittedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP)
        }
}
