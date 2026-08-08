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
        assertTrue(ex.message!!.contains("spring.data.redis.password"))
        assertTrue(ex.message!!.contains("psy.external-registration.rate-limit.require-redis"))
        assertTrue(ex.message!!.contains("spring.mail.host"))
        assertTrue(ex.message!!.contains("auth-module.email.activation-base-url"))
        assertTrue(ex.message!!.contains("psy.assessment.anonymous-identity-secret"))
    }

    @Test
    fun `validate allows production profile with explicit secure values`() {
        val environment = MockEnvironment()
            .withProperty("spring.profiles.active", "production")
            .withProperty("auth-module.security.jwt.secret", "prod-secret-that-is-not-the-default")
            .withProperty("spring.datasource.password", "prod-db-password")
            .withProperty("spring.datasource.url", "jdbc:postgresql://10.0.0.5:5432/psy_prod")
            .withProperty("spring.sql.init.mode", "never")
            .withProperty("psy.scheduler.lock.enabled", "true")
            .withProperty("psy.scheduler.lock.fail-open", "false")
            .withProperty("psy.external-registration.rate-limit.require-redis", "true")
            .withProperty("spring.data.redis.password", "prod-redis-password")
            .withProperty("spring.mail.host", "smtp.example.org")
            .withProperty("auth-module.email.activation-base-url", "https://api.example.org")
            .withProperty("psy.assessment.anonymous-identity-secret", "prod-stable-anonymous-identity-secret")

        assertDoesNotThrow {
            ProductionConfigGuard(environment).validate()
        }
    }
}
