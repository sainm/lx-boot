package org.sainm.psy.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.sql.DriverManager

object PsyDatabaseMigrationCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val operation = environment("PSY_FLYWAY_OPERATION", "info").lowercase()
        val jdbcUrl = environment("PSY_DB_URL", "jdbc:postgresql://127.0.0.1:5432/lx")
        val username = environment("PSY_DB_USERNAME", "lx")
        val password = environment("PSY_DB_PASSWORD", "")
        val flyway = Flyway.configure()
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
                    ScriptUtils.executeSqlScript(
                        connection,
                        ClassPathResource("db/preflight/existing-database-baseline.sql")
                    )
                }
                val result = flyway.baseline()
                println("Flyway baseline succeeded at version ${result.baselineVersion}")
            }
            else -> error("Unsupported PSY_FLYWAY_OPERATION=$operation; use info, validate, baseline, or migrate")
        }
    }

    private fun environment(name: String, defaultValue: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: defaultValue
}
