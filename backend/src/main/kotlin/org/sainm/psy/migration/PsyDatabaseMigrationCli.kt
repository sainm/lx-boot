package org.sainm.psy.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.core.io.ClassPathResource
import java.sql.Connection
import java.sql.DriverManager

object PsyDatabaseMigrationCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val operation = environment("PSY_FLYWAY_OPERATION", "info").lowercase()
        val jdbcUrl = environment("PSY_DB_URL", "jdbc:postgresql://127.0.0.1:5432/lx")
        val username = environment("PSY_DB_USERNAME", "lx")
        val password = environment("PSY_DB_PASSWORD", "")
        val flyway = Flyway.configure()
            // The standalone CLI does not load Spring's
            // FlywayPostgresConfiguration bean. Keep a session advisory lock so
            // non-transactional concurrent-index migrations cannot wait on the
            // CLI runner's own transaction snapshot.
            .configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
            .dataSource(jdbcUrl, username, password)
            .locations("classpath:db/migration")
            .baselineVersion(MigrationVersion.fromVersion("1"))
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .load()

        when (operation) {
            "info" -> flyway.info().all().forEach {
                println("${it.version ?: "repeatable"}|${it.state}|${it.description}")
            }
            "validate" -> {
                flyway.validate()
                println("Flyway validation succeeded")
            }
            "migrate" -> {
                val result = flyway.migrate()
                println("Flyway migration succeeded: ${result.migrationsExecuted} migration(s) executed")
            }
            "baseline" -> {
                check(environment("PSY_FLYWAY_BASELINE_APPROVED", "") == "YES") {
                    "Baseline blocked: set PSY_FLYWAY_BASELINE_APPROVED=YES only after backup and maintenance approval"
                }
                DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                    executeBaselinePreflight(connection)
                }
                val result = flyway.baseline()
                println("Flyway baseline succeeded at version ${result.baselineVersion}")
            }
            else -> error("Unsupported PSY_FLYWAY_OPERATION=$operation; use info, validate, baseline, or migrate")
        }
    }

    private fun environment(name: String, defaultValue: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: defaultValue

    internal fun executeBaselinePreflight(connection: Connection) {
        check(connection.autoCommit) {
            "Baseline preflight requires a new connection without an active transaction"
        }
        val resource = ClassPathResource("db/preflight/existing-database-baseline.sql")
        val sql = resource.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val originalReadOnly = connection.isReadOnly
        try {
            connection.isReadOnly = true
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                // PostgreSQL dollar-quoted DO blocks contain internal semicolons.
                // Sending the preflight as one batch delegates parsing to PostgreSQL
                // instead of Spring's generic semicolon splitter.
                statement.execute(sql)
            }
            connection.rollback()
        } finally {
            if (!connection.autoCommit) {
                runCatching { connection.rollback() }
                connection.autoCommit = true
            }
            connection.isReadOnly = originalReadOnly
        }
    }
}
