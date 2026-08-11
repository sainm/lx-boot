package org.sainm.psy.migration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.core.io.ClassPathResource
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.AbstractDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.repository.NotificationRepository
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.psy.assessment.repository.AnswerSheetRepository
import org.sainm.psy.report.repository.ReportRepository
import org.sainm.psy.scale.api.CreateScaleVersionRequest
import org.sainm.psy.scale.api.ScalePackageExportDocument
import org.sainm.psy.scale.api.UpdateScalePackageRequest
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.core.spi.AuditEvent
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.notification.service.NotificationOpsService
import org.sainm.psy.export.api.ExportReportRequest
import org.sainm.psy.export.service.ExportJobOpsService
import org.sainm.psy.export.service.ExportJobStatus
import org.sainm.psy.export.service.ExportJobStore
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCase
import org.sainm.psy.scale.domain.ScaleGoldenCaseHistory
import org.sainm.psy.scale.domain.ScaleGoldenCaseRun
import org.sainm.psy.scale.domain.ScalePackageAlgorithmBinding
import org.sainm.psy.scale.domain.ScalePackageGovernance
import org.sainm.psy.scale.domain.ScalePackageHighRiskRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageQualityPolicy
import org.sainm.psy.scale.domain.ScalePackageTranslation
import org.sainm.psy.scale.domain.ScalePackageValidityRule
import org.sainm.psy.scale.domain.ScalePublicationReview
import org.sainm.psy.scale.domain.ScaleQuestion
import org.sainm.psy.scale.domain.ScaleQuestionOption
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScalePublicationRepository
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.sainm.psy.scale.service.ScaleContentFingerprintService
import org.sainm.psy.scale.service.ScalePackageExportIntegrityService
import org.sainm.psy.scale.service.ScalePackageImportService
import org.sainm.psy.visualization.service.VisualizationService
import org.sainm.psy.warning.repository.SafetyResponsePolicyRepository
import org.sainm.psy.warning.repository.WarningRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.`when`
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.sql.DriverManager
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@EnabledIfEnvironmentVariable(named = "PSY_POSTGRES_INTEGRATION", matches = "(?i)true")
class FlywayMigrationPostgresTest {

    private val jdbcUrl = requireEnvironment("PSY_TEST_DB_URL")
    private val username = requireEnvironment("PSY_TEST_DB_USERNAME")
    private val password = System.getenv("PSY_TEST_DB_PASSWORD").orEmpty()
    private lateinit var schema: String

    @BeforeEach
    fun createIsolatedSchema() {
        schema = "psy_migration_${UUID.randomUUID().toString().replace("-", "")}"
        connection().use { connection ->
            connection.createStatement().use { it.execute("create schema \"$schema\"") }
        }
    }

    @AfterEach
    fun dropIsolatedSchema() {
        if (::schema.isInitialized) {
            connection().use { connection ->
                connection.createStatement().use { it.execute("drop schema if exists \"$schema\" cascade") }
            }
        }
    }

    @Test
    fun `empty PostgreSQL schema applies every immutable migration`() {
        val result = flyway().migrate()

        assertEquals(22, result.migrationsExecuted)
        assertApplicationSchema()
        assertNewRowsAreProtectedByCheckConstraints()
    }

    @Test
    fun `legacy V1 structure can be explicitly baselined and upgraded`() {
        connection().use { connection ->
            connection.schema = schema
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V1__application_baseline.sql")
            )
            PsyDatabaseMigrationCli.executeBaselinePreflight(connection)
        }

        val flyway = flyway()
        flyway.baseline()
        val result = flyway.migrate()

        assertEquals(21, result.migrationsExecuted)
        assertApplicationSchema()
        assertNewRowsAreProtectedByCheckConstraints()
    }

    @Test
    fun `development seed is repeatable and keeps every business chain tenant consistent`() {
        flyway().migrate()

        repeat(2) {
            connection().use { connection ->
                connection.schema = schema
                ScriptUtils.executeSqlScript(connection, ClassPathResource("data-psy.sql"))
            }
        }

        val namedJdbc = scopedJdbcTemplate()
        val jdbc = namedJdbc.jdbcOperations
        assertEquals(3, jdbc.queryForObject("select count(*) from psy_scale where scale_code = 'STRESS_DEMO'", Int::class.java))
        assertEquals(3, jdbc.queryForObject("select count(distinct tenant_id) from psy_scale where scale_code = 'STRESS_DEMO'", Int::class.java))
        assertEquals(3, jdbc.queryForObject("select count(*) from psy_assessment_task where task_name like '% Mental Health Screening (Demo)'", Int::class.java))
        assertEquals(3, jdbc.queryForObject("select count(*) from psy_assessment_answer_sheet where submit_token like 'seed-submit-%'", Int::class.java))
        assertEquals(
            mapOf("status" to "DRAFT", "current_version_flag" to false),
            jdbc.queryForMap("select status, current_version_flag from psy_scale where scale_code = 'SCL90_TECH_DEMO'")
        )
        assertEquals(0, jdbc.queryForObject("select count(*) from psy_scale where scale_code = 'SCL90'", Int::class.java))
        val defaultTenantId = jdbc.queryForObject(
            "select tenant_id from sys_user where username = 'respondent'",
            Long::class.java
        )!!
        val respondentId = jdbc.queryForObject(
            "select id from sys_user where username = 'respondent'",
            Long::class.java
        )!!
        val counselorId = jdbc.queryForObject(
            "select id from sys_user where username = 'counselor'",
            Long::class.java
        )!!
        val defaultGroupId = jdbc.queryForObject(
            "select group_id from sys_user where username = 'respondent'",
            Long::class.java
        )!!
        val taskRepository = AssessmentTaskRepository(namedJdbc)
        assertEquals(1, taskRepository.countAccessibleUsers(listOf(respondentId), defaultTenantId))
        assertTrue(taskRepository.findActiveUserIdsByGroupIds(listOf(defaultGroupId), defaultTenantId).contains(respondentId))
        val defaultTaskId = jdbc.queryForObject(
            "select id from psy_assessment_task where task_name = 'Default Mental Health Screening (Demo)'",
            Long::class.java
        )!!
        val answerSheetRepository = AnswerSheetRepository(namedJdbc)
        assertTrue(answerSheetRepository.isAssignedToUser(defaultTaskId, respondentId, null))
        assertTrue(taskRepository.findMyTasks(respondentId, null).any { it.taskId == defaultTaskId })
        val lowRiskResultId = jdbc.queryForObject(
            """
            select result.id
            from psy_assessment_result result
            join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
            where sheet.submit_token = 'seed-submit-respondent-low'
            """.trimIndent(),
            Long::class.java
        )!!
        assertEquals("v1", ReportRepository(namedJdbc).findDetailByResultId(lowRiskResultId)?.scaleVersionNo)
        val createdWarningId = answerSheetRepository.createWarningIfNeeded(
            lowRiskResultId,
            "HIGH",
            "HIGH",
            "PostgreSQL warning creation regression fixture."
        )!!
        assertEquals(
            "MISSING",
            jdbc.queryForObject(
                "select policy_resolution_status from psy_warning_record where id = ?",
                String::class.java,
                createdWarningId
            )
        )
        assertEquals(
            0,
            jdbc.queryForObject(
                "select count(*) from psy_warning_record where id = ? and deadline_time is not null",
                Int::class.java,
                createdWarningId
            )
        )
        jdbc.update(
            "update psy_warning_record set deadline_time = timestamp '2026-08-08 11:00:00' where id = ?",
            createdWarningId
        )
        val warningRepository = WarningRepository(namedJdbc, localizedMessages())
        assertTrue(warningRepository.isActiveUserInTenant(counselorId, defaultTenantId))
        val warningQueue = warningRepository.findWarningQueueState(LocalDateTime.of(2026, 8, 8, 12, 0))!!
        assertTrue(warningQueue.openCount >= 1)
        assertTrue(warningQueue.overdueCount >= 1)
        assertTrue(warningQueue.oldestOpenAgeSeconds >= 0)
        assertTrue(SafetyResponsePolicyRepository(namedJdbc, Clock.systemUTC()).isCounselorInTenant(counselorId, defaultTenantId))
        assertEquals(
            0,
            jdbc.queryForObject(
                """
                select count(*) from (
                    select scale.id
                    from psy_scale scale
                    join sys_user creator on creator.id = scale.created_by
                    where scale.scale_code in ('STRESS_DEMO', 'SCL90_TECH_DEMO')
                      and scale.tenant_id is distinct from creator.tenant_id
                    union all
                    select import_job.id
                    from psy_scale_import_job import_job
                    join sys_user operator on operator.id = import_job.operator_user_id
                    join psy_scale scale on scale.id = import_job.created_scale_id
                    where import_job.file_name like 'seed-%'
                      and (import_job.tenant_id is distinct from operator.tenant_id
                           or import_job.tenant_id is distinct from scale.tenant_id)
                    union all
                    select task.id
                    from psy_assessment_task task
                    join psy_scale scale on scale.id = task.scale_id
                    join sys_user creator on creator.id = task.created_by
                    where task.task_name like '% Mental Health Screening (Demo)'
                      and (task.tenant_id is distinct from scale.tenant_id
                           or task.tenant_id is distinct from creator.tenant_id)
                    union all
                    select sheet.id
                    from psy_assessment_answer_sheet sheet
                    join psy_assessment_task task on task.id = sheet.task_id
                    join psy_scale scale on scale.id = sheet.scale_id
                    join sys_user respondent on respondent.id = sheet.user_id
                    where sheet.submit_token like 'seed-submit-%'
                      and (sheet.tenant_id is distinct from task.tenant_id
                           or sheet.tenant_id is distinct from scale.tenant_id
                           or sheet.tenant_id is distinct from respondent.tenant_id)
                    union all
                    select warning.id
                    from psy_warning_record warning
                    join psy_assessment_result result on result.id = warning.result_id
                    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
                    where sheet.submit_token like 'seed-submit-%'
                      and warning.tenant_id is distinct from sheet.tenant_id
                    union all
                    select assignment.id
                    from psy_warning_assignment assignment
                    join psy_warning_record warning on warning.id = assignment.warning_id
                    join sys_user assignee on assignee.id = assignment.assignee_user_id
                    join sys_user assigner on assigner.id = assignment.assigned_by
                    where assignment.tenant_id is distinct from warning.tenant_id
                       or assignment.tenant_id is distinct from assignee.tenant_id
                       or assignment.tenant_id is distinct from assigner.tenant_id
                    union all
                    select intervention.id
                    from psy_intervention_record intervention
                    join psy_warning_record warning on warning.id = intervention.warning_id
                    join sys_user counselor on counselor.id = intervention.counselor_user_id
                    where intervention.tenant_id is distinct from warning.tenant_id
                       or intervention.tenant_id is distinct from counselor.tenant_id
                    union all
                    select status_log.id
                    from psy_intervention_status_log status_log
                    join psy_intervention_record intervention on intervention.id = status_log.intervention_id
                    join sys_user changer on changer.id = status_log.changed_by
                    where status_log.tenant_id is distinct from intervention.tenant_id
                       or status_log.tenant_id is distinct from changer.tenant_id
                    union all
                    select schedule.id
                    from psy_counselor_schedule schedule
                    join sys_user counselor on counselor.id = schedule.counselor_user_id
                    where schedule.tenant_id is distinct from counselor.tenant_id
                    union all
                    select appointment.id
                    from psy_appointment_record appointment
                    join sys_user patient on patient.id = appointment.user_id
                    join sys_user counselor on counselor.id = appointment.counselor_user_id
                    join psy_counselor_schedule schedule on schedule.id = appointment.schedule_id
                    where appointment.remark like '[seed-appointment]%'
                      and (appointment.tenant_id is distinct from patient.tenant_id
                           or appointment.tenant_id is distinct from counselor.tenant_id
                           or appointment.tenant_id is distinct from schedule.tenant_id)
                    union all
                    select counseling.id
                    from psy_counseling_record counseling
                    join psy_appointment_record appointment on appointment.id = counseling.appointment_id
                    join sys_user counselor on counselor.id = counseling.counselor_user_id
                    where appointment.remark like '[seed-appointment]%'
                      and (counseling.tenant_id is distinct from appointment.tenant_id
                           or counseling.tenant_id is distinct from counselor.tenant_id)
                    union all
                    select delivery.id
                    from psy_notification_delivery delivery
                    join psy_notification notification on notification.id = delivery.notification_id
                    join sys_user receiver on receiver.id = delivery.receiver_user_id
                    where notification.title like '[seed-notification]%'
                      and delivery.tenant_id is distinct from receiver.tenant_id
                    union all
                    select export_job.report_id
                    from psy_export_job export_job
                    join psy_report report on report.id = export_job.report_id
                    join psy_assessment_result result on result.id = report.result_id
                    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
                    join sys_user creator on creator.id = export_job.created_by
                    where export_job.id like 'seed-report-export-%'
                      and (export_job.tenant_id is distinct from sheet.tenant_id
                           or export_job.tenant_id is distinct from creator.tenant_id)
                ) tenant_conflict
                """.trimIndent(),
                Int::class.java
            )
        )
    }

    @Test
    fun `tenant ownership hardening preflight covers every psychology table and reports no seeded conflicts`() {
        flyway().migrate()
        connection().use { connection ->
            connection.schema = schema
            ScriptUtils.executeSqlScript(connection, ClassPathResource("data-psy.sql"))
        }

        val sql = ClassPathResource("db/preflight/V16__tenant_ownership_hardening_preflight.sql")
            .inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val jdbc = scopedJdbcTemplate().jdbcOperations
        val report = jdbc.queryForList(sql)
        val tableRows = report.filter { it["record_type"] == "TABLE" }
        val actualTables = jdbc.queryForList(
            """
            select table_name
            from information_schema.tables
            where table_schema = current_schema()
              and table_name like 'psy!_%' escape '!'
            order by table_name
            """.trimIndent(),
            String::class.java
        )

        assertEquals(46, actualTables.size)
        assertEquals(actualTables, tableRows.map { it["check_name"] as String }.sorted())
        assertEquals(
            emptyList<String>(),
            report.filter { (it["issue_count"] as Number).toLong() != 0L }
                .map { "${it["record_type"]}:${it["check_name"]}=${it["issue_count"]}" }
        )
    }

    @Test
    fun `draft creation and optimistic version claims are atomic on PostgreSQL`() {
        flyway().migrate()
        val dataSource = scopedDataSource()
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        jdbc.jdbcOperations.execute("insert into sys_tenant (id, tenant_code, tenant_name) values (121, 'DRAFT_LOCK', 'Draft lock')")
        jdbc.jdbcOperations.execute("insert into sys_user (id, username, tenant_id) values (121, 'draft-owner', 121)")
        jdbc.jdbcOperations.execute(
            "insert into psy_scale (id, tenant_id, scale_code, scale_name, version_no, status, created_by) values (121, 121, 'DRAFT_LOCK', 'Draft lock', '1', 'PUBLISHED', 121)"
        )
        jdbc.jdbcOperations.execute(
            """insert into psy_assessment_task (
                   id, tenant_id, task_name, scale_id, task_mode, start_time, end_time, status, created_by
               ) values (
                   121, 121, 'Concurrent draft task', 121, 'ONLINE',
                   current_timestamp - interval '1 hour', current_timestamp + interval '1 day', 'IN_PROGRESS', 121
               )""".trimIndent()
        )

        val transactionManager = DataSourceTransactionManager(dataSource)
        val transaction = TransactionTemplate(transactionManager)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val createStart = CountDownLatch(1)
            val createCalls = (1..2).map {
                executor.submit<Long?> {
                    createStart.await(10, TimeUnit.SECONDS)
                    transaction.execute {
                        val repository = AnswerSheetRepository(NamedParameterJdbcTemplate(dataSource))
                        repository.lockRespondentWrite(121, 121)
                        repository.createDraftAnswerSheetIfAbsent(121, 121, 121)
                    }
                }
            }
            createStart.countDown()
            val created = createCalls.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, created.count { it != null })
            val answerSheetId = created.single { it != null }!!
            assertEquals(
                1,
                jdbc.jdbcOperations.queryForObject(
                    "select count(*) from psy_assessment_answer_sheet where task_id = 121 and user_id = 121 and answer_status = 'DRAFT'",
                    Int::class.java
                )
            )

            val versionStart = CountDownLatch(1)
            val versionClaims = (1..2).map {
                executor.submit<Int> {
                    versionStart.await(10, TimeUnit.SECONDS)
                    transaction.execute {
                        AnswerSheetRepository(NamedParameterJdbcTemplate(dataSource))
                            .incrementDraftVersion(answerSheetId, 1)
                    } ?: 0
                }
            }
            versionStart.countDown()
            assertEquals(listOf(0, 2), versionClaims.map { it.get(10, TimeUnit.SECONDS) }.sorted())
            assertEquals(
                2,
                jdbc.jdbcOperations.queryForObject(
                    "select version_no from psy_assessment_answer_sheet where id = ?",
                    Int::class.java,
                    answerSheetId
                )
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `notification delivery retry lifecycle uses PostgreSQL state guards`() {
        flyway().migrate()
        val dataSource = scopedDataSource()
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        jdbc.jdbcOperations.execute("insert into sys_tenant (id, tenant_code, tenant_name) values (1, 'NOTIFY', 'Notification')")
        jdbc.jdbcOperations.execute("insert into sys_user (id, username, tenant_id) values (1, 'migration-user', 1)")
        jdbc.jdbcOperations.execute(
            """
            insert into psy_notification (id, notification_type, title, content)
            values
                (1, 'TEST', 'failure lifecycle', 'content'),
                (2, 'TEST', 'worker crash recovery', 'content')
            """.trimIndent()
        )
        jdbc.jdbcOperations.execute(
            """
            insert into psy_notification_delivery (
                id, tenant_id, notification_id, receiver_user_id, delivery_channel, delivery_status,
                retry_count, processing_started_at, processing_token, updated_at
            ) values
                (1, 1, 1, 1, 'PUSH', 'PENDING', 0, null, null, current_timestamp),
                (2, 1, 2, 1, 'PUSH', 'PROCESSING', 0, timestamp '2026-08-08 11:30:00', 'legacy-test-lease', timestamp '2026-08-08 11:30:00')
            """.trimIndent()
        )
        val repository = NotificationRepository(jdbc, localizedMessages())
        val now = java.time.LocalDateTime.of(2026, 8, 8, 12, 0)

        val pending = repository.findPendingPushDeliveries(10, now)
        assertEquals(1, pending.size)
        val firstLease = repository.markDeliveryProcessing(1, now)
        assertTrue(firstLease != null)
        assertTrue(repository.markDeliveryProcessing(1, now) == null, "a second worker must not claim the same delivery")
        assertEquals(
            "PENDING",
            repository.markDeliveryAttemptFailed(
                deliveryId = 1,
                processingToken = firstLease!!,
                previousRetryCount = 0,
                maxAttempts = 2,
                nextRetryAt = now.plusMinutes(1),
                errorMessage = "VENDOR_UNAVAILABLE",
                now = now
            )
        )
        assertTrue(repository.findPendingPushDeliveries(10, now).isEmpty())
        assertEquals(1, repository.findPendingPushDeliveries(10, now.plusMinutes(1)).size)
        val secondLease = repository.markDeliveryProcessing(1, now.plusMinutes(1))!!
        assertEquals(
            "DEAD_LETTER",
            repository.markDeliveryAttemptFailed(
                deliveryId = 1,
                processingToken = secondLease,
                previousRetryCount = 1,
                maxAttempts = 2,
                nextRetryAt = null,
                errorMessage = "VENDOR_UNAVAILABLE",
                now = now.plusMinutes(1)
            )
        )
        assertEquals(1, repository.retryFailedDeliveries(1, "PUSH").retriedCount)
        assertEquals(1, repository.findPendingPushDeliveries(10, now.plusMinutes(1)).size)

        assertEquals(
            1,
            repository.recoverStaleProcessingDeliveries(
                cutoff = now.minusMinutes(10),
                now = now,
                maxAttempts = 2
            )
        )
        assertEquals(
            mapOf(
                "delivery_status" to "PENDING",
                "retry_count" to 1,
                "error_message" to "PROCESSING_TIMEOUT",
                "processing_token" to null
            ),
            jdbc.jdbcOperations.queryForMap(
                "select delivery_status, retry_count, error_message, processing_token from psy_notification_delivery where id = 2"
            )
        )
        assertFalse(repository.markDeliverySent(2, "legacy-test-lease"), "an expired worker must be fenced")
        val recoveredLease = repository.markDeliveryProcessing(2, now)!!
        jdbc.jdbcOperations.update(
            "update psy_notification_delivery set processing_started_at = ? where id = 2",
            java.sql.Timestamp.valueOf(now.minusMinutes(30))
        )
        assertEquals(
            1,
            repository.recoverStaleProcessingDeliveries(
                cutoff = now.minusMinutes(10),
                now = now.plusMinutes(1),
                maxAttempts = 2
            )
        )
        assertFalse(repository.markDeliverySent(2, recoveredLease), "a recovered lease must not complete late")
        assertEquals(
            mapOf(
                "delivery_status" to "DEAD_LETTER",
                "retry_count" to 2,
                "next_retry_at" to null
            ),
            jdbc.jdbcOperations.queryForMap(
                "select delivery_status, retry_count, next_retry_at from psy_notification_delivery where id = 2"
            )
        )
        assertEquals(1, repository.retryFailedDeliveries(2, "PUSH").retriedCount)
        assertEquals(
            mapOf(
                "delivery_status" to "PENDING",
                "retry_count" to 0,
                "dead_letter_at" to null,
                "processing_token" to null
            ),
            jdbc.jdbcOperations.queryForMap(
                "select delivery_status, retry_count, dead_letter_at, processing_token from psy_notification_delivery where id = 2"
            )
        )
    }

    @Test
    fun `notification callbacks cannot bypass leases or tenant-scoped retry`() {
        flyway().migrate()
        val dataSource = scopedDataSource()
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        jdbc.jdbcOperations.execute(
            "insert into sys_tenant (id, tenant_code, tenant_name) values (1, 'CALLBACK_A', 'Callback A'), (2, 'CALLBACK_B', 'Callback B')"
        )
        jdbc.jdbcOperations.execute(
            "insert into sys_user (id, username, tenant_id) values (1, 'callback-a', 1), (2, 'callback-b', 2)"
        )
        jdbc.jdbcOperations.execute(
            """
            insert into psy_notification (id, notification_type, title, content) values
                (1, 'TEST', 'processing', 'content'),
                (2, 'TEST', 'sent', 'content'),
                (3, 'TEST', 'retry tenant a', 'content'),
                (4, 'TEST', 'retry tenant b', 'content')
            """.trimIndent()
        )
        jdbc.jdbcOperations.execute(
            """
            insert into psy_notification_delivery (
                id, tenant_id, notification_id, receiver_user_id, delivery_channel, delivery_status,
                retry_count, processing_started_at, processing_token, updated_at
            ) values
                (1, 1, 1, 1, 'PUSH', 'PROCESSING', 0, timestamp '2026-08-08 12:00:00', 'active-lease', timestamp '2026-08-08 12:00:00'),
                (2, 1, 2, 1, 'PUSH', 'SENT', 0, null, null, timestamp '2026-08-08 12:00:00'),
                (3, 1, 3, 1, 'PUSH', 'FAILED', 2, null, null, timestamp '2026-08-08 12:00:00'),
                (4, 2, 4, 2, 'PUSH', 'FAILED', 2, null, null, timestamp '2026-08-08 12:00:00')
            """.trimIndent()
        )
        val repository = NotificationRepository(jdbc, localizedMessages())
        val occurredAt = LocalDateTime.of(2026, 8, 8, 12, 1)

        val callbackError = assertThrows(BizException::class.java) {
            repository.applyPushDeliveryCallback(
                deliveryId = 1,
                deliveryStatus = "DELIVERED",
                providerName = "provider",
                providerMessageId = "message-1",
                errorMessage = null,
                callbackPayloadJson = null,
                deliveredAt = occurredAt,
                clickedAt = null,
                readAt = null,
                tenantId = 1
            )
        }
        assertEquals("NOTIFICATION_DELIVERY_STATE_INVALID", callbackError.code)
        val receiptError = assertThrows(BizException::class.java) {
            repository.markPushDeliveryDelivered(1, 1, occurredAt)
        }
        assertEquals("NOTIFICATION_DELIVERY_STATE_INVALID", receiptError.code)
        assertEquals(
            mapOf("delivery_status" to "PROCESSING", "processing_token" to "active-lease"),
            jdbc.jdbcOperations.queryForMap(
                "select delivery_status, processing_token from psy_notification_delivery where id = 1"
            )
        )

        val delivered = repository.applyPushDeliveryCallback(
            deliveryId = 2,
            deliveryStatus = "DELIVERED",
            providerName = "provider",
            providerMessageId = "message-2",
            errorMessage = null,
            callbackPayloadJson = "{}",
            deliveredAt = occurredAt,
            clickedAt = null,
            readAt = null,
            tenantId = 1
        )
        assertEquals("DELIVERED", delivered.deliveryStatus)
        assertEquals("NOTIFICATION_NOT_FOUND", assertThrows(BizException::class.java) {
            repository.applyPushDeliveryCallback(2, "CLICKED", null, null, null, null, null, occurredAt, occurredAt, 2)
        }.code)

        val retryResult = repository.retryFailedDeliveriesBatch(listOf(3L, 4L), "PUSH", tenantId = 1)
        assertEquals(1, retryResult.retriedCount)
        assertEquals(
            listOf(
                mapOf("id" to 3L, "delivery_status" to "PENDING", "retry_count" to 0),
                mapOf("id" to 4L, "delivery_status" to "FAILED", "retry_count" to 2)
            ),
            jdbc.jdbcOperations.queryForList(
                "select id, delivery_status, retry_count from psy_notification_delivery where id in (3, 4) order by id"
            )
        )

        val tenantAccessPolicy = mock(TenantAccessPolicy::class.java)
        `when`(tenantAccessPolicy.currentTenantFilter("NOTIFICATION", "OPERATIONS")).thenReturn(2L)
        val securityAuditService = mock(SecurityAuditService::class.java)
        doThrow(IllegalStateException("audit unavailable"))
            .`when`(securityAuditService)
            .recordNotificationDeliveriesRetried(listOf(4L), "PUSH", 1)
        val opsService = NotificationOpsService(repository, tenantAccessPolicy, securityAuditService)
        val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

        val auditFailure = assertThrows(IllegalStateException::class.java) {
            transaction.executeWithoutResult { opsService.retryFailedDeliveries(4, "PUSH") }
        }
        assertEquals("audit unavailable", auditFailure.message)
        assertEquals(
            mapOf("delivery_status" to "FAILED", "retry_count" to 2),
            jdbc.jdbcOperations.queryForMap(
                "select delivery_status, retry_count from psy_notification_delivery where id = 4"
            )
        )
    }

    @Test
    fun `export replay and download are tenant scoped, audited, and rollback safe`() {
        flyway().migrate()
        val dataSource = scopedDataSource()
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        jdbc.jdbcOperations.execute(
            "insert into sys_tenant (id, tenant_code, tenant_name) values (9011, 'EXPORT_A', 'Export A'), (9012, 'EXPORT_B', 'Export B')"
        )
        jdbc.jdbcOperations.execute(
            """
            insert into psy_export_job (
                id, tenant_id, status, report_id, export_format, locale_tag, retry_count,
                file_name, content_type, file_bytes, file_size, error_message, dead_letter_at,
                created_at, updated_at, completed_at
            ) values
                ('export-replay-a', 9011, 'DEAD_LETTER', 501, 'TEXT', 'zh-CN', 3,
                 'old.txt', 'text/plain', decode('6f6c64', 'hex'), 3, 'failed', current_timestamp,
                 current_timestamp, current_timestamp, current_timestamp),
                ('export-replay-b', 9012, 'DEAD_LETTER', 502, 'TEXT', 'zh-CN', 4,
                 null, null, null, null, 'failed', current_timestamp,
                 current_timestamp, current_timestamp, current_timestamp),
                ('export-download-a', 9011, 'DONE', 503, 'TEXT', 'ja-JP', 0,
                 'report.txt', 'text/plain', decode('e697a5e69cace8aa9e', 'hex'), 9, null, null,
                 current_timestamp, current_timestamp, current_timestamp),
                ('export-audit-rollback', 9011, 'DEAD_LETTER', 504, 'TEXT', 'en-US', 3,
                 'rollback.txt', 'text/plain', decode('6f6c64', 'hex'), 3, 'failed', current_timestamp,
                 current_timestamp, current_timestamp, current_timestamp)
            """.trimIndent()
        )

        val currentUser = mock(CurrentUserFacade::class.java)
        `when`(currentUser.requireCurrentUser()).thenReturn(
            UserPrincipal(90101, "export-admin", "Export Admin", UserStatus.ENABLED, 9011, null, setOf("ADMIN"), emptySet())
        )
        val policy = mock(TenantAccessPolicy::class.java)
        `when`(policy.canAccess(9011L, "EXPORT_JOB", "export-replay-a", "REPLAY")).thenReturn(true)
        `when`(policy.canAccess(9011L, "EXPORT_JOB", "export-download-a", "DOWNLOAD")).thenReturn(true)
        `when`(policy.canAccess(9011L, "EXPORT_JOB", "export-audit-rollback", "REPLAY")).thenReturn(true)
        val publisher = object : AuditEventPublisher {
            override fun publish(event: AuditEvent) = Unit
        }
        val audit = SecurityAuditService(publisher, currentUser)
        val store = ExportJobStore(
            jdbcTemplate = jdbc,
            fileStorageEnabled = false
        )
        val ops = ExportJobOpsService(
            exportService = mock(org.sainm.psy.export.service.ExportService::class.java),
            exportJobStore = store,
            currentUserFacade = currentUser,
            tenantAccessPolicy = policy,
            securityAuditService = audit
        )
        val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

        val replayed = transaction.execute { ops.replayJob("export-replay-a") }!!
        assertEquals(ExportJobStatus.PENDING, replayed.status)
        assertEquals(
            mapOf("status" to "PENDING", "retry_count" to 0, "file_bytes" to null),
            jdbc.jdbcOperations.queryForMap("select status, retry_count, file_bytes from psy_export_job where id = 'export-replay-a'")
        )
        assertEquals(
            "JOB_NOT_FOUND",
            assertThrows(BizException::class.java) { transaction.execute { ops.replayJob("export-replay-b") } }.code
        )
        assertEquals("DEAD_LETTER", jdbc.jdbcOperations.queryForObject("select status from psy_export_job where id = 'export-replay-b'", String::class.java))

        val download = ops.requireDownloadableJob("export-download-a")
        assertEquals("ja-JP", download.localeTag)
        assertEquals("日本語", download.bytes!!.toString(Charsets.UTF_8))

        val throwingPublisher = object : AuditEventPublisher {
            override fun publish(event: AuditEvent) = error("audit unavailable")
        }
        val failingOps = ExportJobOpsService(
            exportService = mock(org.sainm.psy.export.service.ExportService::class.java),
            exportJobStore = store,
            currentUserFacade = currentUser,
            tenantAccessPolicy = policy,
            securityAuditService = SecurityAuditService(throwingPublisher, currentUser)
        )
        val failure = assertThrows(IllegalStateException::class.java) {
            transaction.execute { failingOps.replayJob("export-audit-rollback") }
        }
        assertEquals("audit unavailable", failure.message)
        val rolledBack = jdbc.jdbcOperations.queryForMap(
            "select status, retry_count, file_bytes from psy_export_job where id = 'export-audit-rollback'"
        )
        assertEquals("DEAD_LETTER", rolledBack["status"])
        assertEquals(3, rolledBack["retry_count"])
        assertTrue((rolledBack["file_bytes"] as ByteArray).contentEquals(byteArrayOf(0x6f, 0x6c, 0x64)))
    }

    @Test
    fun `rescoring appends a version and database keeps one current result`() {
        flyway().migrate()
        val jdbc = scopedJdbcTemplate().jdbcOperations
        jdbc.execute("insert into sys_tenant (id, tenant_code, tenant_name) values (1, 'RESULT', 'Result')")
        jdbc.execute("insert into sys_user (id, username, tenant_id) values (1, 'result-owner', 1)")
        jdbc.execute("insert into psy_scale (id, tenant_id, scale_code, scale_name, version_no, status, created_by) values (1, 1, 'TEST', 'Test', '1.0', 'PUBLISHED', 1)")
        jdbc.execute(
            """
            insert into psy_assessment_task (
                id, tenant_id, task_name, scale_id, task_mode, start_time, end_time, status, created_by
            ) values (1, 1, 'Task', 1, 'ONLINE', current_timestamp, current_timestamp + interval '1 day', 'IN_PROGRESS', 1)
            """.trimIndent()
        )
        jdbc.execute(
            """
            insert into psy_assessment_answer_sheet (id, tenant_id, task_id, scale_id, user_id, answer_status)
            values (1, 1, 1, 1, 1, 'SUBMITTED')
            """.trimIndent()
        )
        jdbc.execute(
            """
            insert into psy_assessment_result (id, answer_sheet_id, total_score, risk_level)
            values (1, 1, 5, 'NORMAL')
            """.trimIndent()
        )

        assertEquals(1, jdbc.update("update psy_assessment_result set is_current = false where id = 1 and is_current = true"))
        assertEquals(
            1,
            jdbc.update(
                """
                insert into psy_assessment_result (
                    id, answer_sheet_id, total_score, risk_level,
                    calculation_version, is_current, supersedes_result_id
                )
                select 2, answer_sheet_id, 8, 'ATTENTION', calculation_version + 1, true, id
                from psy_assessment_result where id = 1
                """.trimIndent()
            )
        )
        assertEquals(2, jdbc.queryForObject("select count(*) from psy_assessment_result where answer_sheet_id = 1", Int::class.java))
        assertEquals(1, jdbc.queryForObject("select count(*) from psy_assessment_result where answer_sheet_id = 1 and is_current", Int::class.java))
        assertEquals(2, jdbc.queryForObject("select calculation_version from psy_assessment_result where is_current", Int::class.java))
        assertThrows(SQLException::class.java) {
            connection().use { connection ->
                connection.schema = schema
                connection.prepareStatement(
                    "insert into psy_assessment_result (answer_sheet_id, total_score, risk_level, calculation_version, is_current) values (1, 9, 'HIGH', 3, true)"
                ).use { it.executeUpdate() }
            }
        }
    }

    @Test
    fun `creating a scale version preserves high risk rules with remapped ids`() {
        flyway().migrate()
        val jdbc = scopedJdbcTemplate().jdbcOperations
        jdbc.execute("insert into sys_tenant (id, tenant_code, tenant_name) values (99, 'RISK', 'Risk')")
        jdbc.execute("insert into sys_user (id, username, tenant_id) values (99, 'risk-owner', 99)")
        jdbc.execute("insert into psy_scale (id, tenant_id, scale_code, scale_name, version_no, status, high_risk_warning_enabled, created_by) values (10, 99, 'RISK', 'Risk', 'v1', 'PUBLISHED', true, 99)")
        jdbc.execute("insert into psy_scale_question (id, scale_id, question_no, question_title, question_type) values (20, 10, 1, 'Risk question', 'SINGLE_CHOICE')")
        jdbc.execute("insert into psy_scale_option (id, question_id, option_code, option_label, score_value) values (30, 20, 'YES', 'Yes', 1)")
        jdbc.execute(
            """
            insert into psy_scale_high_risk_rule (
                id, scale_id, rule_code, question_id, option_id, warning_level, sort_no
            ) values (40, 10, 'SELF_HARM', 20, 30, 'HIGH', 1)
            """.trimIndent()
        )

        val newScaleId = ScaleRepository(scopedJdbcTemplate()).createVersionFrom(
            10,
            CreateScaleVersionRequest(versionNo = "v2"),
            99
        )

        val copied = jdbc.queryForMap(
            """
            select rule.rule_code, rule.question_id, rule.option_id, question.scale_id
            from psy_scale_high_risk_rule rule
            join psy_scale_question question on question.id = rule.question_id
            where rule.scale_id = ?
            """.trimIndent(),
            newScaleId
        )
        assertEquals("SELF_HARM", copied["rule_code"])
        assertTrue((copied["question_id"] as Number).toLong() != 20L)
        assertTrue((copied["option_id"] as Number).toLong() != 30L)
        assertEquals(newScaleId, (copied["scale_id"] as Number).toLong())
    }

    @Test
    fun `scale package round trip and version copy preserve content but reset review`() {
        flyway().migrate()
        val jdbc = scopedJdbcTemplate()
        jdbc.jdbcOperations.execute("insert into sys_tenant (id, tenant_code, tenant_name) values (7, 'PACKAGE', 'Package')")
        jdbc.jdbcOperations.execute("insert into sys_user (id, username, tenant_id) values (7, 'package-owner', 7)")
        jdbc.jdbcOperations.execute(
            """insert into psy_scale (id, tenant_id, scale_code, scale_name, version_no, version_group_id, status)
               values (10, 7, 'PACKAGE', 'Package', 'v1', 10, 'DRAFT'),
                      (11, 7, 'PACKAGE', 'Package', 'v2', 10, 'DRAFT')"""
        )
        jdbc.jdbcOperations.execute(
            """insert into psy_scale_question (id, scale_id, question_no, question_title, question_type)
               values (20, 10, 1, 'Risk question', 'SLIDER'), (21, 11, 1, 'Risk question', 'SLIDER')"""
        )
        jdbc.jdbcOperations.execute(
            """insert into psy_scale_high_risk_rule (id, scale_id, rule_code, question_id, score_threshold, warning_level, result_title)
               values (40, 10, 'RISK_ONE', 20, 3, 'HIGH', 'Base risk'),
                      (41, 11, 'RISK_ONE', 21, 3, 'HIGH', 'Base risk')"""
        )
        val repository = ScalePackageRepository(jdbc, Clock.systemUTC())
        val request = UpdateScalePackageRequest(
            governance = ScalePackageGovernance(
                sourceTitle = "Reviewed manual",
                copyrightStatus = "AUTHORIZED",
                authorizationStatus = "AUTHORIZED",
                governanceStatus = "APPROVED"
            ),
            translations = listOf(
                ScalePackageTranslation("zh-CN", "量表", reviewStatus = "APPROVED"),
                ScalePackageTranslation("ja-JP", "尺度", reviewStatus = "APPROVED"),
                ScalePackageTranslation("en", "Scale", reviewStatus = "APPROVED")
            ),
            highRiskRuleTranslations = listOf(
                ScalePackageHighRiskRuleTranslation(40, "zh-CN", "高风险", reviewStatus = "APPROVED"),
                ScalePackageHighRiskRuleTranslation(40, "ja-JP", "高リスク", reviewStatus = "APPROVED"),
                ScalePackageHighRiskRuleTranslation(40, "en", "High risk", reviewStatus = "APPROVED")
            ),
            qualityPolicy = ScalePackageQualityPolicy(),
            validityRules = listOf(ScalePackageValidityRule("CONSISTENCY", "CONSISTENCY", "1", "{\"max\":1}", "APPROVED")),
            algorithmBinding = ScalePackageAlgorithmBinding("BUILTIN_SUM", "1", "BUILTIN", reviewStatus = "APPROVED")
        )

        repository.replace(10L, request, 7L)
        val source = repository.find(10L)
        assertEquals(3, source.translations.size)
        assertEquals("AUTHORIZED", source.governance?.authorizationStatus)
        assertEquals(3, source.highRiskRuleTranslations.size)
        assertTrue(repository.canonicalValues(10L).isNotEmpty())

        repository.copyPackage(10L, 11L)
        val copied = repository.find(11L)
        assertEquals("DRAFT", copied.governance?.governanceStatus)
        assertTrue(copied.translations.all { it.reviewStatus == "DRAFT" })
        assertEquals(setOf(41L), copied.highRiskRuleTranslations.map { it.highRiskRuleId }.toSet())
        assertTrue(copied.highRiskRuleTranslations.all { it.reviewStatus == "DRAFT" })
        assertTrue(copied.validityRules.all { it.reviewStatus == "DRAFT" })
        assertEquals("DRAFT", copied.algorithmBinding?.reviewStatus)
    }

    @Test
    fun `golden case runs and publication reviews are append only and idempotent`() {
        flyway().migrate()
        val jdbc = scopedJdbcTemplate()
        jdbc.jdbcOperations.execute("insert into sys_tenant (id, tenant_code, tenant_name) values (71, 'GOLDEN', 'Golden')")
        jdbc.jdbcOperations.execute(
            "insert into sys_user (id, username, tenant_id) values (71, 'author', 71), (72, 'professional', 71), (73, 'business', 71)"
        )
        jdbc.jdbcOperations.execute(
            "insert into psy_scale (id, tenant_id, scale_code, scale_name, version_no, created_by) values (71, 71, 'GOLDEN', 'Golden', '1', 71)"
        )
        val repository = ScalePublicationRepository(jdbc, Clock.systemUTC())
        val scaleHash = "a".repeat(64)
        val caseHash = "b".repeat(64)
        val releaseHash = "c".repeat(64)

        val goldenCase = repository.saveCaseRevision(
            71, "NORMAL-1", "NORMAL", "manual", scaleHash, caseHash, "{}", "{}", 71
        )
        val sameRevision = repository.saveCaseRevision(
            71, "NORMAL-1", "NORMAL", "manual", scaleHash, caseHash, "{}", "{}", 71
        )
        assertEquals(goldenCase.id, sameRevision.id)
        assertEquals(1, sameRevision.revisionNo)

        val run = repository.saveRun(goldenCase, "GENERIC_SCORE_CALCULATOR", "1", true, "{}", "[]", 71)
        assertTrue(repository.findLatestRun(goldenCase.id)?.passed == true)
        assertEquals(listOf(run.id), repository.findAllRuns(71).map { it.id })
        assertEquals("GENERIC_SCORE_CALCULATOR", repository.findAllRuns(71).single().algorithmCode)
        assertEquals(listOf(goldenCase.id), repository.findAllCases(71).map { it.id })
        assertTrue(repository.approveCase(goldenCase.id, 72))
        assertEquals(72L, repository.findCase(goldenCase.id)?.approvedBy)
        assertTrue(run.id > 0)

        val review = repository.saveReview(
            71, "PROFESSIONAL", "APPROVED", 72, "COUNSELOR", scaleHash, releaseHash, "review-1", null
        )
        val replay = repository.saveReview(
            71, "PROFESSIONAL", "APPROVED", 72, "COUNSELOR", scaleHash, releaseHash, "review-1", null
        )
        assertEquals(review.id, replay.id)
        assertEquals("APPROVED", repository.findLatestReviews(71, releaseHash)["PROFESSIONAL"]?.decision)
        assertEquals(listOf(review.id), repository.findAllReviews(71).map { it.id })

        val secondCase = repository.saveCaseRevision(
            71, "NORMAL-2", "NORMAL", "manual", scaleHash, "d".repeat(64), "{}", "{}", 71
        )
        val thirdCase = repository.saveCaseRevision(
            71, "BOUNDARY-1", "BOUNDARY", "manual", scaleHash, "e".repeat(64), "{}", "{}", 71
        )
        val secondRun = repository.saveRun(secondCase, "GENERIC_SCORE_CALCULATOR", "1", true, "{}", "[]", 71)
        val thirdRun = repository.saveRun(thirdCase, "GENERIC_SCORE_CALCULATOR", "1", false, "{}", "[\"mismatch\"]", 71)
        val businessReview = repository.saveReview(
            71, "BUSINESS", "REJECTED", 73, "ASSESSMENT_ADMIN", scaleHash, releaseHash, "review-2", "needs review"
        )

        val casePage = repository.findCaseHistoryPage(71, null, 2)
        assertEquals(2, casePage.list.size)
        assertEquals(2, casePage.limit)
        assertTrue(casePage.nextCursor != null)
        val caseTail = repository.findCaseHistoryPage(71, casePage.nextCursor, 2)
        assertEquals(
            listOf(thirdCase.id, secondCase.id, goldenCase.id),
            casePage.list.plus(caseTail.list).map { it.id }
        )
        assertTrue(caseTail.nextCursor == null)

        val runPage = repository.findRunHistoryPage(71, null, 1)
        assertEquals(listOf(thirdRun.id), runPage.list.map { it.id })
        val runTail = repository.findRunHistoryPage(71, runPage.nextCursor, 2)
        assertEquals(listOf(secondRun.id, run.id), runTail.list.map { it.id })
        assertTrue(runTail.nextCursor == null)

        val reviewPage = repository.findReviewHistoryPage(71, null, 1)
        assertEquals(listOf(businessReview.id), reviewPage.list.map { it.id })
        val reviewTail = repository.findReviewHistoryPage(71, reviewPage.nextCursor, 1)
        assertEquals(listOf(review.id), reviewTail.list.map { it.id })
        assertTrue(reviewTail.nextCursor == null)
        assertEquals(100, repository.findCaseHistoryPage(71, null, 500).limit)
    }

    @Test
    fun `publishing a version leaves exactly one current published version`() {
        flyway().migrate()
        val jdbc = scopedJdbcTemplate()
        jdbc.jdbcOperations.execute("insert into sys_tenant (id, tenant_code, tenant_name) values (81, 'VERSIONED', 'Versioned')")
        jdbc.jdbcOperations.execute("insert into sys_user (id, username, tenant_id) values (81, 'publisher', 81)")
        jdbc.jdbcOperations.execute(
            """insert into psy_scale (
                id, tenant_id, scale_code, scale_name, version_no, version_group_id, status, current_version_flag, created_by
            ) values
                (81, 81, 'VERSIONED', 'Version 1', '1', 81, 'PUBLISHED', true, 81),
                (82, 81, 'VERSIONED', 'Version 2', '2', 81, 'DRAFT', false, 81)""".trimIndent()
        )
        val repository = ScaleRepository(jdbc)

        assertTrue(repository.publishVersion(82, 81, 81, "d".repeat(64)))
        assertEquals(false, repository.publishVersion(82, 81, 81, "d".repeat(64)))
        assertEquals(
            1,
            jdbc.queryForObject(
                """select count(*) from psy_scale
                   where version_group_id=81 and status='PUBLISHED' and current_version_flag=true""".trimIndent(),
                emptyMap<String, Any>(),
                Int::class.java
            )
        )
        assertThrows(Exception::class.java) {
            jdbc.jdbcOperations.execute("update psy_scale set current_version_flag=true where id=81")
        }
    }

    @Test
    fun `scale import confirmation is tenant scoped and can be claimed only once`() {
        flyway().migrate()
        val jdbc = scopedJdbcTemplate()
        jdbc.jdbcOperations.execute(
            "insert into sys_tenant (id, tenant_code, tenant_name) values (91, 'IMPORT_A', 'Import A'), (92, 'IMPORT_B', 'Import B')"
        )
        jdbc.jdbcOperations.execute(
            "insert into sys_user (id, username, tenant_id) values (91, 'import-admin', 91)"
        )
        val repository = ScaleImportRepository(jdbc)
        val importId = repository.createJob("package.json", "PACKAGE_CREATE_ONLY", true, 91, 91)
        repository.updateParsedResult(importId, "PARSED", "{}", "{}", 0, 0)

        assertEquals(false, repository.claimForConfirmation(importId, 92, "PACKAGE_CREATE_ONLY"))
        assertTrue(repository.claimForConfirmation(importId, 91, "PACKAGE_CREATE_ONLY"))
        assertEquals(false, repository.claimForConfirmation(importId, 91, "PACKAGE_CREATE_ONLY"))
        assertEquals("CONFIRMED", repository.findJobById(importId, 91)?.status)
        assertEquals(null, repository.findJobById(importId, 92))
    }

    @Test
    fun `scale package confirmation persists a tenant draft and resets external evidence`() {
        flyway().migrate()
        val dataSource = scopedDataSource()
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        jdbc.jdbcOperations.execute("insert into sys_tenant (id, tenant_code, tenant_name) values (101, 'PACKAGE_IMPORT', 'Package Import')")
        jdbc.jdbcOperations.execute("insert into sys_user (id, username, tenant_id) values (101, 'package-import-admin', 101)")

        val scaleRepository = ScaleRepository(jdbc)
        val packageRepository = ScalePackageRepository(jdbc, Clock.systemUTC())
        val publicationRepository = ScalePublicationRepository(jdbc, Clock.systemUTC())
        val importRepository = ScaleImportRepository(jdbc)
        val visualizationService = mock(VisualizationService::class.java)
        `when`(visualizationService.findConfigs(org.mockito.ArgumentMatchers.anyLong())).thenReturn(emptyList())
        val fingerprintService = ScaleContentFingerprintService(packageRepository, visualizationService)
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val integrityService = ScalePackageExportIntegrityService(objectMapper, fingerprintService)
        val userFacade = mock(CurrentUserFacade::class.java)
        `when`(userFacade.requireCurrentUser()).thenReturn(
            UserPrincipal(101, "package-import-admin", "Package Import Admin", UserStatus.ENABLED, null, 101, setOf("ASSESSMENT_ADMIN"), emptySet())
        )
        val securityAuditService = mock(SecurityAuditService::class.java)
        val tenantAccessPolicy = mock(TenantAccessPolicy::class.java)
        `when`(tenantAccessPolicy.requireTenantId()).thenReturn(101)
        val service = ScalePackageImportService(
            scaleRepository, packageRepository, publicationRepository, importRepository, visualizationService,
            fingerprintService, integrityService, userFacade, localizedMessages(), objectMapper,
            TransactionTemplate(DataSourceTransactionManager(dataSource)), securityAuditService, tenantAccessPolicy
        )

        val scaleHash = "a".repeat(64)
        val caseHash = fingerprintService.sha256("NORMAL|NORMAL|manual|{}|{\"valid\":true}")
        val goldenCase = ScaleGoldenCase(
            501, 500, "NORMAL", 1, "NORMAL", "manual", scaleHash, caseHash, "{}", "{\"valid\":true}",
            201, LocalDateTime.of(2026, 8, 8, 10, 0), 202, LocalDateTime.of(2026, 8, 8, 11, 0)
        )
        val run = ScaleGoldenCaseRun(
            601, 501, scaleHash, caseHash, "GENERIC_SCORE_CALCULATOR", "1", true, "{}", "[]", 201,
            LocalDateTime.of(2026, 8, 8, 10, 30)
        )
        val sourceQuestion = ScaleQuestion(
            id = 510, scaleId = 500, dimensionId = null, questionNo = 1, questionTitle = "Risk item",
            questionType = "SINGLE_CHOICE", requiredFlag = true, reverseScoreFlag = false,
            weightValue = BigDecimal.ONE, optionSelectionLimit = null, sliderMin = null, sliderMax = null,
            sliderStep = null, textInputEnabled = false, textInputPlaceholder = null, matrixGroupCode = null,
            rowCode = null, columnCode = null, sortNo = 1,
            options = listOf(ScaleQuestionOption(511, 510, "YES", "Yes", BigDecimal.ONE, false, null, 1))
        )
        val sourceHighRiskRule = org.sainm.psy.scale.domain.ScaleHighRiskRule(
            520, 500, "IMPORT_RISK", 510, 1, 511, "YES", null, "HIGH",
            "Base risk", "Base risk description", "Base risk suggestion", 1
        )
        val sourceScale = ScaleDetail(
            id = 500, scaleCode = "IMPORTED", scaleName = "Imported", description = null, applicableTarget = null,
            versionNo = "v1", versionGroupId = 500, currentVersionFlag = true, status = "PUBLISHED",
            scoreMethod = "SIMPLE_SUM", scoreCoefficient = BigDecimal.ONE, normStrategy = "RAW_SCORE",
            normDefaultGroup = null, highRiskWarningEnabled = true, anonymousSupported = false, reportTemplate = null,
            createdBy = 201, createdAt = LocalDateTime.of(2026, 8, 8, 9, 0), updatedBy = 201,
            updatedAt = LocalDateTime.of(2026, 8, 8, 9, 0), dimensions = emptyList(), questions = listOf(sourceQuestion),
            resultRules = emptyList(), norms = emptyList(), tenantId = 200, highRiskRules = listOf(sourceHighRiskRule)
        )
        val sourcePackage = org.sainm.psy.scale.domain.ScalePackageSnapshot(
            scaleId = 500,
            governance = ScalePackageGovernance(sourceTitle = "Reviewed manual", copyrightStatus = "AUTHORIZED", authorizationStatus = "AUTHORIZED", governanceStatus = "APPROVED"),
            translations = listOf(
                ScalePackageTranslation("zh-CN", "导入量表", reviewStatus = "APPROVED"),
                ScalePackageTranslation("ja-JP", "輸入尺度", reviewStatus = "APPROVED"),
                ScalePackageTranslation("en", "Imported scale", reviewStatus = "APPROVED")
            ),
            highRiskRuleTranslations = listOf(
                ScalePackageHighRiskRuleTranslation(520, "zh-CN", "导入高风险", reviewStatus = "APPROVED"),
                ScalePackageHighRiskRuleTranslation(520, "ja-JP", "輸入高リスク", reviewStatus = "APPROVED"),
                ScalePackageHighRiskRuleTranslation(520, "en", "Imported high risk", reviewStatus = "APPROVED")
            )
        )
        val history = listOf(ScaleGoldenCaseHistory(goldenCase, listOf(run)))
        val releaseHash = fingerprintService.calculateReleaseFingerprint(scaleHash, listOf(goldenCase))
        val reviews = listOf(ScalePublicationReview(701, "PROFESSIONAL", "APPROVED", 202, "COUNSELOR", scaleHash, releaseHash, "approved", LocalDateTime.of(2026, 8, 8, 11, 30)))
        val payloadHash = integrityService.calculate(scaleHash, releaseHash, sourceScale, sourcePackage, history, reviews)
        val document = ScalePackageExportDocument(
            exportId = "export-500", exportedAt = Instant.parse("2026-08-08T12:00:00Z"), exportedBy = 201,
            scaleContentHash = scaleHash, releaseFingerprint = releaseHash, payloadHash = payloadHash,
            scale = sourceScale, scalePackage = sourcePackage, goldenCases = history, publicationReviews = reviews
        )
        val importId = importRepository.createJob("package.json", "PACKAGE_CREATE_ONLY", true, 101, 101)
        importRepository.updateParsedResult(importId, "PARSED", "{}", objectMapper.writeValueAsString(document), 0, 0)

        val result = service.confirm(importId)

        assertEquals("SUCCESS", result.status)
        assertEquals(1, result.importedGoldenCaseRevisionCount)
        assertEquals(1, result.discardedGoldenCaseRunCount)
        assertEquals(1, result.discardedPublicationReviewCount)
        val importedScale = scaleRepository.findDetailById(result.scaleId)!!
        assertEquals(101L, importedScale.tenantId)
        assertEquals("DRAFT", importedScale.status)
        val importedPackage = packageRepository.find(result.scaleId)
        assertEquals("PENDING_REVIEW", importedPackage.governance?.authorizationStatus)
        assertTrue(importedPackage.translations.all { it.reviewStatus == "DRAFT" })
        assertEquals(3, importedPackage.highRiskRuleTranslations.size)
        assertEquals(importedScale.highRiskRules.single().id, importedPackage.highRiskRuleTranslations.single { it.localeCode == "ja-JP" }.highRiskRuleId)
        assertTrue(importedPackage.highRiskRuleTranslations.all { it.reviewStatus == "DRAFT" })
        assertEquals(1, publicationRepository.findAllCases(result.scaleId).size)
        assertTrue(publicationRepository.findAllRuns(result.scaleId).isEmpty())
        assertTrue(publicationRepository.findAllReviews(result.scaleId).isEmpty())
        assertEquals("SUCCESS", importRepository.findJobById(importId, 101)?.status)
    }

    private fun assertApplicationSchema() {
        connection().use { connection ->
            connection.prepareStatement(
                """
                select count(*)
                from information_schema.tables
                where table_schema = ?
                  and (table_name like 'sys!_%' escape '!' or table_name like 'psy!_%' escape '!')
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(61, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select data_type
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'psy_warning_response_event'
                  and column_name = 'contact_outcome'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals("text", result.getString(1))
                }
            }
            connection.prepareStatement(
                """select count(*) from pg_indexes
                   where schemaname = ? and indexname = 'uk_psy_scale_version_group_current'""".trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(1, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select count(*)
                from pg_constraint c
                join pg_namespace n on n.oid = c.connamespace
                where n.nspname = ?
                  and c.conname like 'ck_psy_%'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(98, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select count(*)
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'psy_notification_delivery'
                  and column_name = 'processing_token'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(1, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select count(*)
                from information_schema.columns
                where table_schema = ?
                  and table_name = 'psy_export_job'
                  and column_name in (
                      'retry_count', 'next_retry_at', 'processing_started_at',
                      'processing_token', 'dead_letter_at'
                  )
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(5, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """select count(*) from pg_indexes
                   where schemaname = ? and indexname = 'idx_psy_export_job_pending_retry'""".trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(1, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select count(*)
                from information_schema.columns
                where table_schema = ?
                  and column_name = 'tenant_id'
                  and table_name in (
                      'psy_appointment_record', 'psy_assessment_answer_sheet', 'psy_assessment_task',
                      'psy_counseling_record', 'psy_counselor_schedule', 'psy_export_job',
                      'psy_intervention_record', 'psy_intervention_status_log', 'psy_notification_delivery',
                      'psy_scale', 'psy_scale_golden_case', 'psy_scale_golden_case_run',
                      'psy_scale_import_job', 'psy_scale_publication_review',
                      'psy_warning_assignment', 'psy_warning_record'
                  )
                  and is_nullable = 'NO'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(16, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select count(*)
                from pg_constraint c
                join pg_namespace n on n.oid = c.connamespace
                where n.nspname = ?
                  and c.contype = 'f'
                  and c.conname in (
                      'fk_psy_appointment_tenant', 'fk_psy_answer_tenant', 'fk_psy_task_tenant',
                      'fk_psy_counseling_tenant', 'fk_psy_schedule_tenant',
                      'fk_psy_export_job_tenant', 'fk_psy_intervention_tenant',
                      'fk_psy_intervention_log_tenant', 'fk_psy_notification_delivery_tenant',
                      'fk_psy_scale_tenant', 'fk_psy_scale_import_job_tenant',
                      'fk_psy_warning_assignment_tenant', 'fk_psy_warning_tenant'
                  )
                  and c.convalidated = true
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(13, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """select count(*) from information_schema.columns
                   where table_schema = ? and (
                       (table_name = 'psy_assessment_answer_sheet' and column_name = 'response_locale_code')
                       or (table_name = 'psy_report' and column_name = 'locale_code')
                   )""".trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(2, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select count(*)
                from pg_indexes
                where schemaname = ?
                  and indexname in (
                      'idx_psy_notification_delivery_pending_retry',
                      'idx_psy_notification_delivery_processing_started',
                      'idx_psy_notification_delivery_dead_letter'
                  )
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(3, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select count(*)
                from pg_indexes
                where schemaname = ?
                  and indexname in (
                      'idx_psy_scale_golden_case_history_cursor',
                      'idx_psy_scale_golden_run_history_cursor',
                      'idx_psy_scale_publication_review_history_cursor'
                  )
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(3, result.getInt(1))
                }
            }
            connection.prepareStatement(
                """
                select count(*)
                from pg_indexes
                where schemaname = ?
                  and indexname in (
                      'uk_psy_scale_tenant_code_version',
                      'uk_psy_scale_global_code_version',
                      'idx_psy_scale_import_job_tenant_created'
                  )
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(3, result.getInt(1))
                }
            }
        }
    }

    private fun assertNewRowsAreProtectedByCheckConstraints() {
        connection().use { connection ->
            connection.schema = schema
            assertThrows(SQLException::class.java) {
                connection.prepareStatement(
                    "insert into psy_export_job (id, status) values (?, ?)"
                ).use { statement ->
                    statement.setString(1, "invalid-status")
                    statement.setString(2, "UNKNOWN")
                    statement.executeUpdate()
                }
            }
            connection.createStatement().use {
                it.executeUpdate("insert into sys_tenant (id, tenant_code, tenant_name) values (9001, 'V18-CHECK', 'V18 check')")
                it.executeUpdate("insert into sys_user (id, username, tenant_id) values (9001, 'V13-check', 9001)")
                it.executeUpdate("insert into psy_scale (id, scale_code, scale_name, tenant_id, created_by) values (9001, 'V12-CHECK', 'V12 check', 9001, 9001)")
            }
            assertThrows(SQLException::class.java) {
                connection.prepareStatement(
                    "insert into psy_scale_translation (scale_id, locale_code, scale_name) values (?, ?, ?)"
                ).use { statement ->
                    statement.setLong(1, 9001L)
                    statement.setString(2, "fr-FR")
                    statement.setString(3, "Unsupported locale")
                    statement.executeUpdate()
                }
            }
            assertThrows(SQLException::class.java) {
                connection.prepareStatement(
                    "insert into psy_scale_quality_policy (scale_id, max_missing_ratio) values (?, ?)"
                ).use { statement ->
                    statement.setLong(1, 9001L)
                    statement.setBigDecimal(2, java.math.BigDecimal("1.1"))
                    statement.executeUpdate()
                }
            }
            assertThrows(SQLException::class.java) {
                connection.prepareStatement(
                    """insert into psy_scale_golden_case (
                        scale_id, case_code, revision_no, case_type, source_reference,
                        scale_content_hash, case_content_hash, input_json, expected_json, created_by
                    ) values (?, 'BAD', 1, 'UNSUPPORTED', 'manual', ?, ?, '{}'::jsonb, '{}'::jsonb, ?)""".trimIndent()
                ).use { statement ->
                    statement.setLong(1, 9001L)
                    statement.setString(2, "a".repeat(64))
                    statement.setString(3, "b".repeat(64))
                    statement.setLong(4, 9001L)
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun flyway(): Flyway = Flyway.configure()
        // Keep Flyway serialization, but use a session advisory lock so V14's
        // CREATE INDEX CONCURRENTLY does not wait on Flyway's own transaction snapshot.
        .configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
        .dataSource(jdbcUrl, username, password)
        .schemas(schema)
        .defaultSchema(schema)
        .locations("classpath:db/migration")
        .baselineVersion(MigrationVersion.fromVersion("1"))
        .baselineOnMigrate(false)
        .validateOnMigrate(true)
        .cleanDisabled(true)
        .load()

    private fun connection() = DriverManager.getConnection(jdbcUrl, username, password)

    private fun scopedJdbcTemplate(): NamedParameterJdbcTemplate = NamedParameterJdbcTemplate(scopedDataSource())

    private fun scopedDataSource(): AbstractDataSource = object : AbstractDataSource() {
            override fun getConnection(): Connection =
                DriverManager.getConnection(jdbcUrl, username, password).apply { schema = this@FlywayMigrationPostgresTest.schema }

            override fun getConnection(username: String, password: String): Connection =
                DriverManager.getConnection(jdbcUrl, username, password).apply { schema = this@FlywayMigrationPostgresTest.schema }
        }

    private fun localizedMessages(): LocalizedMessages {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        return LocalizedMessages(messageSource)
    }

    private fun requireEnvironment(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("$name is required when PSY_POSTGRES_INTEGRATION=true")
}
