package org.sainm.psy.migration

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * V14 creates a PostgreSQL index concurrently outside a transaction. Flyway's default
 * transaction-scoped advisory lock keeps an old transaction snapshot open and can make
 * CREATE INDEX CONCURRENTLY wait for the migration runner itself. A session-scoped
 * advisory lock preserves single-runner serialization without holding that snapshot.
 */
@Configuration(proxyBeanMethods = false)
class FlywayPostgresConfiguration {
    @Bean
    fun postgresSessionAdvisoryLock(): FlywayConfigurationCustomizer = FlywayConfigurationCustomizer { configuration ->
        configuration.configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
    }
}
