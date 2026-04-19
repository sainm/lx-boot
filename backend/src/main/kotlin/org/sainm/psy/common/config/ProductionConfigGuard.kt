package org.sainm.psy.common.config

import jakarta.annotation.PostConstruct
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

@Component
class ProductionConfigGuard(
    private val environment: Environment
) {

    @PostConstruct
    fun validate() {
        if (!environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            return
        }
        val insecureKeys = buildList {
            if (environment.getProperty("auth-module.security.jwt.secret") == DEFAULT_JWT_SECRET) {
                add("auth-module.security.jwt.secret")
            }
            if (environment.getProperty("spring.datasource.password") in DEFAULT_DB_PASSWORDS) {
                add("spring.datasource.password")
            }
            if (environment.getProperty("spring.datasource.url") == DEFAULT_DB_URL) {
                add("spring.datasource.url")
            }
        }
        check(insecureKeys.isEmpty()) {
            "Production profile must override insecure default configuration: ${insecureKeys.joinToString(", ")}"
        }
    }

    companion object {
        private const val DEFAULT_JWT_SECRET = "change-me-change-me-change-me-change-me"
        private val DEFAULT_DB_PASSWORDS = setOf("PleaseChangeThisPassword", "AuthStarter@2026")
        private const val DEFAULT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/auth_starter"
    }
}
