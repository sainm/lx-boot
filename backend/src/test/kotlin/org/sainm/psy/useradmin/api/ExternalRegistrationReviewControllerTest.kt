package org.sainm.psy.useradmin.api

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.core.spi.MailSenderService
import org.sainm.auth.core.spi.UserRegistrationService
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.context.i18n.LocaleContextHolder
import java.util.Locale

@ExtendWith(MockitoExtension::class)
class ExternalRegistrationReviewControllerTest {

    @Mock private lateinit var userRegistrationService: UserRegistrationService
    @Mock private lateinit var mailSenderService: MailSenderService
    @Mock private lateinit var currentUserFacade: CurrentUserFacade

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var controller: ExternalRegistrationReviewController

    @BeforeEach
    fun setUp() {
        LocaleContextHolder.setLocale(Locale.US)
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:registration-review-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute(
            """
            create table sys_user(
                id bigint primary key,
                username varchar(100),
                display_name varchar(100),
                email varchar(320),
                register_source varchar(32),
                created_at timestamp,
                status integer,
                deleted integer,
                tenant_id bigint
            )
            """.trimIndent()
        )
        jdbcTemplate.update(
            "insert into sys_user values (1, 'tenant-one', 'One', 'one@example.com', 'EXTERNAL', current_timestamp, 4, 0, 10)"
        )
        jdbcTemplate.update(
            "insert into sys_user values (2, 'tenant-two', 'Two', 'two@example.com', 'EXTERNAL', current_timestamp, 4, 0, 20)"
        )
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
        }
        controller = ExternalRegistrationReviewController(
            jdbcTemplate,
            userRegistrationService,
            mailSenderService,
            currentUserFacade,
            LocalizedMessages(messageSource)
        )
    }

    @Test
    fun `listPending only returns current tenant registrations`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(tenantAdmin(10L))

        val rows = controller.listPending().body.orEmpty()

        assertEquals(1, rows.size)
    }

    @Test
    fun `approve rejects registration from another tenant`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(tenantAdmin(10L))

        val error = assertThrows<BizException> { controller.approve(2L) }

        assertEquals("REGISTRATION_FORBIDDEN", error.code)
    }

    @Test
    fun `approve advances accessible registration and sends localized mail`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(tenantAdmin(10L))

        controller.approve(1L)

        verify(userRegistrationService).advanceUserStatus(1L, 4, 1)
        verify(mailSenderService).send(
            "one@example.com",
            "Your account has been approved",
            "<html><body><p>Your account registration has been approved. You may now sign in.</p><p>Go to the assessment system to begin.</p></body></html>"
        )
    }

    private fun tenantAdmin(tenantId: Long) = UserPrincipal(
        userId = 99L,
        username = "admin",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        tenantId = tenantId,
        groupId = null,
        roles = setOf("ORG_MANAGER"),
        permissions = emptySet()
    )
}
