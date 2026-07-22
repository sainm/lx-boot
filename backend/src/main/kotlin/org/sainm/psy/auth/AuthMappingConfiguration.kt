package org.sainm.psy.auth

import org.sainm.auth.core.spi.SocialAccountService
import org.sainm.auth.core.spi.UserLookupService
import org.sainm.auth.persistence.JdbcSocialAccountService
import org.sainm.auth.persistence.JdbcUserRegistrationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Overrides the starter's default [SocialAccountService] with lx-boot's
 * SSO-aware mapping. Because this bean is declared, the starter's
 * `@ConditionalOnMissingBean(SocialAccountService)` default does not register,
 * so we build the default explicitly and wrap it as the delegate for non-SSO
 * providers (WeChat).
 */
@Configuration
class AuthMappingConfiguration {

    @Bean
    fun socialAccountService(
        jdbcTemplate: JdbcTemplate,
        userLookupService: UserLookupService,
        userRegistrationService: JdbcUserRegistrationService
    ): SocialAccountService {
        val default = JdbcSocialAccountService(
            jdbcTemplate = jdbcTemplate,
            userLookupService = userLookupService,
            userRegistrationService = userRegistrationService
        )
        return SsoAccountMappingService(
            jdbcTemplate = jdbcTemplate,
            userLookupService = userLookupService,
            delegate = default
        )
    }
}
