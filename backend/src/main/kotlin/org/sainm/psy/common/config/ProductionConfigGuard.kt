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
        val insecureKeys = listOfNotNull(
            "auth-module.security.jwt.secret".takeIf { environment.getProperty(it) == DEFAULT_JWT_SECRET },
            "spring.datasource.password".takeIf { environment.getProperty(it) in DEFAULT_DB_PASSWORDS },
            "spring.datasource.url".takeIf { environment.getProperty(it) == DEFAULT_DB_URL },
            "spring.sql.init.mode".takeIf {
                environment.getProperty(it, "never").equals("always", ignoreCase = true)
            }
        )
        check(insecureKeys.isEmpty()) {
            "Production profile must override insecure default configuration: ${insecureKeys.joinToString(", ")}"
        }
    }

    companion object {
        private const val DEFAULT_JWT_SECRET = "local-dev-only-change-this-jwt-secret"
        private val DEFAULT_DB_PASSWORDS = setOf("lx", "PleaseChangeThisPassword", "AuthStarter@2026")
        private const val DEFAULT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/lx"
    }
}
