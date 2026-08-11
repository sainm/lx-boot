package org.sainm.psy.common.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

class ApplicationSqlInitConfigurationTest {

    @Test
    fun `application configuration lets Flyway exclusively manage database structure`() {
        val propertySources = YamlPropertySourceLoader()
            .load("application", ClassPathResource("application.yml"))

        assertNull(
            propertySources.firstNotNullOfOrNull {
                it.getProperty("spring.sql.init.schema-locations")
            },
            "The frozen schema-psy.sql must not run after versioned Flyway migrations",
        )
        assertEquals(
            "classpath:data-psy.sql",
            propertySources.firstNotNullOfOrNull {
                it.getProperty("spring.sql.init.data-locations")
            },
        )
    }
}
