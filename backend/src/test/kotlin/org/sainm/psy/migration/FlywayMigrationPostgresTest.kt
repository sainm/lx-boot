package org.sainm.psy.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.core.io.ClassPathResource
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.repository.NotificationRepository
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

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

        assertEquals(8, result.migrationsExecuted)
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
        }

        val flyway = flyway()
        flyway.baseline()
        val result = flyway.migrate()

        assertEquals(7, result.migrationsExecuted)
        assertApplicationSchema()
        assertNewRowsAreProtectedByCheckConstraints()
    }

    @Test
    fun `notification delivery retry lifecycle uses PostgreSQL state guards`() {
        flyway().migrate()
        val jdbc = scopedJdbcTemplate()
        jdbc.jdbcOperations.execute(
            "insert into sys_user (id, username) values (1, 'migration-user')"
        )
        jdbc.jdbcOperations.execute(
            """
            insert into psy_notification (id, notification_type, title, content)
            values (1, 'TEST', 'title', 'content')
            """.trimIndent()
        )
        jdbc.jdbcOperations.execute(
            """
            insert into psy_notification_delivery (
                id, notification_id, receiver_user_id, delivery_channel, delivery_status
            ) values (1, 1, 1, 'PUSH', 'PENDING')
            """.trimIndent()
        )
        val repository = NotificationRepository(jdbc, localizedMessages())
        val now = java.time.LocalDateTime.of(2026, 8, 8, 12, 0)

        val pending = repository.findPendingPushDeliveries(10, now)
        assertEquals(1, pending.size)
        assertTrue(repository.markDeliveryProcessing(1))
        assertEquals(
            "PENDING",
            repository.markDeliveryAttemptFailed(
                deliveryId = 1,
                previousRetryCount = 0,
                maxAttempts = 2,
                nextRetryAt = now.plusMinutes(1),
                errorMessage = "VENDOR_UNAVAILABLE",
                now = now
            )
        )
        assertTrue(repository.findPendingPushDeliveries(10, now).isEmpty())
        assertEquals(1, repository.findPendingPushDeliveries(10, now.plusMinutes(1)).size)
        assertTrue(repository.markDeliveryProcessing(1))
        assertEquals(
            "DEAD_LETTER",
            repository.markDeliveryAttemptFailed(
                deliveryId = 1,
                previousRetryCount = 1,
                maxAttempts = 2,
                nextRetryAt = null,
                errorMessage = "VENDOR_UNAVAILABLE",
                now = now.plusMinutes(1)
            )
        )
        assertEquals(1, repository.retryFailedDeliveries(1, "PUSH").retriedCount)
        assertEquals(1, repository.findPendingPushDeliveries(10, now.plusMinutes(1)).size)
    }

    private fun assertApplicationSchema() {
        connection().use { connection ->
            connection.prepareStatement(
                """
                select count(*)
                from information_schema.tables
                where table_schema = ?
                  and (table_name like 'sys\_%' escape '\\' or table_name like 'psy\_%' escape '\\')
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    result.next()
                    assertEquals(44, result.getInt(1))
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
                    assertEquals(23, result.getInt(1))
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
        }
    }

    private fun flyway(): Flyway = Flyway.configure()
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

    private fun scopedJdbcTemplate(): NamedParameterJdbcTemplate {
        val separator = if (jdbcUrl.contains('?')) '&' else '?'
        return NamedParameterJdbcTemplate(
            DriverManagerDataSource().apply {
                setDriverClassName("org.postgresql.Driver")
                url = "$jdbcUrl${separator}currentSchema=$schema"
                this.username = this@FlywayMigrationPostgresTest.username
                this.password = this@FlywayMigrationPostgresTest.password
            }
        )
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
