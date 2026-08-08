package org.sainm.psy.statistics.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.sainm.psy.statistics.api.GroupReportListQuery
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "LX_POSTGRES_INTEGRATION", matches = "true")
class StatisticsRepositoryPostgresIntegrationTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val repository: StatisticsRepository
) {

    @Test
    fun `anonymous submissions are aggregated without storing user identity`() {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val tenantId = insertId(
            "insert into sys_tenant(tenant_code, tenant_name) values (?, ?) returning id",
            "tenant-$suffix",
            "Tenant $suffix"
        )
        val groupId = insertId(
            "insert into sys_group(tenant_id, group_code, group_name) values (?, ?, ?) returning id",
            tenantId,
            "group-$suffix",
            "Group $suffix"
        )
        repeat(4) { index ->
            insertId(
                "insert into sys_user(username, display_name, tenant_id, group_id) values (?, ?, ?, ?) returning id",
                "user-$index-$suffix",
                "User $index",
                tenantId,
                groupId
            )
        }
        val scaleId = insertId(
            """
            insert into psy_scale(scale_code, scale_name, version_no, status, anonymous_supported)
            values (?, ?, '1', 'PUBLISHED', true) returning id
            """.trimIndent(),
            "scale-$suffix",
            "Scale $suffix"
        )
        val now = LocalDateTime.now()
        val taskId = insertId(
            """
            insert into psy_assessment_task(
                task_name, scale_id, scale_version_no, task_mode, anonymous_flag,
                start_time, end_time, status
            ) values (?, ?, '1', 'ONLINE', true, ?, ?, 'PUBLISHED') returning id
            """.trimIndent(),
            "Task $suffix",
            scaleId,
            Timestamp.valueOf(now.minusHours(1)),
            Timestamp.valueOf(now.plusHours(1))
        )
        jdbc.update(
            "insert into psy_assessment_task_assignment(task_id, target_type, target_id) values (?, 'GROUP', ?)",
            taskId,
            groupId
        )
        createAnonymousResult(taskId, scaleId, tenantId, groupId, "anon-a-$suffix", BigDecimal("10"), "NORMAL")
        createAnonymousResult(taskId, scaleId, tenantId, groupId, "anon-b-$suffix", BigDecimal("20"), "HIGH")

        val (rows, total) = repository.findGroupReportPage(
            GroupReportListQuery(taskId = taskId, groupId = groupId, page = 1, size = 20),
            tenantId
        )

        val summary = rows.single()
        assertEquals(1L, total)
        assertTrue(summary.anonymousFlag)
        assertEquals(4L, summary.memberCount)
        assertEquals(2L, summary.submittedCount)
        assertEquals(0, BigDecimal("15").compareTo(summary.averageScore))
        assertEquals(1L, summary.highRiskCount)
        assertEquals(1L, summary.riskDistribution.first { it.key == "NORMAL" }.value)
        assertEquals(1L, summary.riskDistribution.first { it.key == "HIGH" }.value)
    }

    private fun createAnonymousResult(
        taskId: Long,
        scaleId: Long,
        tenantId: Long,
        groupId: Long,
        anonymousToken: String,
        score: BigDecimal,
        riskLevel: String
    ) {
        val sheetId = insertId(
            """
            insert into psy_assessment_answer_sheet(
                task_id, scale_id, user_id, anonymous_token, aggregate_tenant_id, aggregate_group_id,
                answer_status, submit_time
            ) values (?, ?, null, ?, ?, ?, 'SUBMITTED', current_timestamp) returning id
            """.trimIndent(),
            taskId,
            scaleId,
            anonymousToken,
            tenantId,
            groupId
        )
        jdbc.update(
            """
            insert into psy_assessment_result(answer_sheet_id, total_score, risk_level, warning_flag)
            values (?, ?, ?, ?)
            """.trimIndent(),
            sheetId,
            score,
            riskLevel,
            riskLevel == "HIGH"
        )
    }

    private fun insertId(sql: String, vararg args: Any): Long =
        requireNotNull(jdbc.queryForObject(sql, Long::class.java, *args))

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                System.getenv("LX_POSTGRES_URL") ?: "jdbc:postgresql://localhost:5432/lx"
            }
            registry.add("spring.datasource.username") {
                System.getenv("LX_POSTGRES_USERNAME") ?: System.getProperty("user.name")
            }
            registry.add("spring.datasource.password") { System.getenv("LX_POSTGRES_PASSWORD") ?: "" }
            registry.add("spring.sql.init.mode") { "never" }
            registry.add("psy.scheduler.lock.enabled") { "false" }
            registry.add("spring.task.scheduling.enabled") { "false" }
        }
    }
}
