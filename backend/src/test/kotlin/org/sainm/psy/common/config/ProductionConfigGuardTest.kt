package org.sainm.psy.common.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

class ProductionConfigGuardTest {

    @Test
    fun `validate allows local profile to use development defaults`() {
        val environment = MockEnvironment()
            .withProperty("auth-module.security.jwt.secret", "local-dev-only-change-this-jwt-secret")
            .withProperty("spring.datasource.password", "lx")
            .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/lx")
            .withProperty("spring.sql.init.mode", "always")

        assertDoesNotThrow {
            ProductionConfigGuard(environment).validate()
        }
    }

    @Test
    fun `validate rejects production profile with insecure defaults`() {
        val environment = MockEnvironment()
            .withProperty("spring.profiles.active", "prod")
            .withProperty("auth-module.security.jwt.secret", "local-dev-only-change-this-jwt-secret")
            .withProperty("spring.datasource.password", "lx")
            .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/lx")
            .withProperty("spring.sql.init.mode", "always")

        val ex = assertThrows<IllegalStateException> {
            ProductionConfigGuard(environment).validate()
        }

        assertTrue(ex.message!!.contains("auth-module.security.jwt.secret"))
        assertTrue(ex.message!!.contains("spring.datasource.password"))
        assertTrue(ex.message!!.contains("spring.datasource.url"))
        assertTrue(ex.message!!.contains("spring.sql.init.mode"))
    }

    @Test
    fun `validate allows production profile with explicit secure values`() {
        val environment = MockEnvironment()
            .withProperty("spring.profiles.active", "production")
            .withProperty("auth-module.security.jwt.secret", "prod-secret-that-is-not-the-default")
            .withProperty("spring.datasource.password", "prod-db-password")
            .withProperty("spring.datasource.url", "jdbc:postgresql://10.0.0.5:5432/psy_prod")
            .withProperty("spring.sql.init.mode", "never")

        assertDoesNotThrow {
            ProductionConfigGuard(environment).validate()
        }
    }
}
