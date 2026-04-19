package org.sainm.psy.notification.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.notification.domain.NotificationDeliveryReceiptResult
import org.sainm.psy.notification.service.NotificationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(NotificationController::class)
@Import(AuthSecurityConfiguration::class)
class NotificationControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var notificationService: NotificationService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `reportPushDeliveryReceived rejects anonymous request`() {
        mockMvc.post("/api/v1/my/notifications/deliveries/11/received") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"occurredAt":"2026-04-17T10:30:00"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_401002") }
        }

        verifyNoInteractions(notificationService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `reportPushDeliveryReceived allows authenticated user`() {
        `when`(
            notificationService.reportPushDeliveryReceived(
                11L,
                LocalDateTime.of(2026, 4, 17, 10, 30)
            )
        ).thenReturn(
            NotificationDeliveryReceiptResult(
                deliveryId = 11L,
                notificationId = 1001L,
                deliveryStatus = "DELIVERED",
                readFlag = false,
                readTime = null,
                deliveredTime = LocalDateTime.of(2026, 4, 17, 10, 30),
                clickedTime = null
            )
        )

        mockMvc.post("/api/v1/my/notifications/deliveries/11/received") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"occurredAt":"2026-04-17T10:30:00"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.deliveryStatus") { value("DELIVERED") }
        }
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `reportPushDeliveryClicked allows authenticated user`() {
        `when`(
            notificationService.reportPushDeliveryClicked(
                11L,
                LocalDateTime.of(2026, 4, 17, 10, 35)
            )
        ).thenReturn(
            NotificationDeliveryReceiptResult(
                deliveryId = 11L,
                notificationId = 1001L,
                deliveryStatus = "CLICKED",
                readFlag = true,
                readTime = LocalDateTime.of(2026, 4, 17, 10, 35),
                deliveredTime = LocalDateTime.of(2026, 4, 17, 10, 35),
                clickedTime = LocalDateTime.of(2026, 4, 17, 10, 35)
            )
        )

        mockMvc.post("/api/v1/my/notifications/deliveries/11/clicked") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"occurredAt":"2026-04-17T10:35:00"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.deliveryStatus") { value("CLICKED") }
            jsonPath("$.data.readFlag") { value(true) }
        }
    }
}
