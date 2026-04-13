package org.sainm.psy.notification.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.notification.domain.NotificationDeliveryRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryOpsBucket
import org.sainm.psy.notification.domain.NotificationDeliveryOpsSummary
import org.sainm.psy.notification.domain.NotificationDeliverySummary
import org.sainm.psy.notification.service.NotificationOpsService
import org.sainm.psy.notification.service.NotificationPolicyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(NotificationOpsController::class)
@Import(AuthSecurityConfiguration::class)
class NotificationOpsControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var notificationOpsService: NotificationOpsService
    @MockitoBean private lateinit var notificationPolicyService: NotificationPolicyService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `findDeliveryOpsSummary rejects anonymous request`() {
        mockMvc.get("/api/v1/notifications/deliveries/summary")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTH_401002") }
            }

        verifyNoInteractions(notificationOpsService)
    }

    @Test
    fun `findDeliveries rejects anonymous request`() {
        mockMvc.get("/api/v1/notifications/10/deliveries")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTH_401002") }
            }

        verifyNoInteractions(notificationOpsService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `retryFailedDeliveries rejects USER role`() {
        mockMvc.post("/api/v1/notifications/10/deliveries/retry")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
            }

        verifyNoInteractions(notificationOpsService)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `findDeliveryOpsSummary allows admin role`() {
        `when`(notificationOpsService.findDeliveryOpsSummary()).thenReturn(
            NotificationDeliveryOpsSummary(
                totalPending = 3,
                totalProcessing = 1,
                totalFailed = 2,
                oldestPendingCreatedAt = LocalDateTime.now(),
                buckets = listOf(
                    NotificationDeliveryOpsBucket("PUSH", "PENDING", 3),
                    NotificationDeliveryOpsBucket("PUSH", "FAILED", 2)
                )
            )
        )

        mockMvc.get("/api/v1/notifications/deliveries/summary")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data.totalPending") { value(3) }
                jsonPath("$.data.buckets[0].deliveryChannel") { value("PUSH") }
            }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `findDeliveries allows admin role`() {
        `when`(notificationOpsService.findDeliveries(10L)).thenReturn(
            listOf(
                NotificationDeliverySummary(
                    id = 1L,
                    notificationId = 10L,
                    receiverUserId = 5L,
                    deliveryChannel = "PUSH",
                    deliveryStatus = "FAILED",
                    readFlag = false,
                    readTime = null,
                    deviceId = 20L,
                    errorMessage = "provider missing",
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )
        )

        mockMvc.get("/api/v1/notifications/10/deliveries")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data[0].notificationId") { value(10) }
            }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `retryFailedDeliveries allows admin role`() {
        `when`(notificationOpsService.retryFailedDeliveries(10L, "PUSH")).thenReturn(
            NotificationDeliveryRetryResult(notificationId = 10L, deliveryChannel = "PUSH", retriedCount = 1)
        )

        mockMvc.post("/api/v1/notifications/10/deliveries/retry?deliveryChannel=PUSH")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data.retriedCount") { value(1) }
            }
    }
}
