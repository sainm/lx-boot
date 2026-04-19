package org.sainm.psy.notification.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.notification.domain.AdminNotificationOpsItem
import org.sainm.psy.notification.domain.NotificationBatchRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryReceiptResult
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
    fun `findAdminNotifications rejects USER role`() {
        mockMvc.get("/api/v1/notifications/ops/feed")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
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
    @WithMockUser(roles = ["USER"])
    fun `applyPushDeliveryCallback rejects USER role`() {
        mockMvc.post("/api/v1/notifications/deliveries/11/callbacks") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"deliveryStatus":"DELIVERED"}"""
        }.andExpect {
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
    fun `findAdminNotifications allows admin role`() {
        `when`(notificationOpsService.findAdminNotifications(null, null, null, 20)).thenReturn(
            listOf(
                AdminNotificationOpsItem(
                    id = 10L,
                    notificationType = "WARNING_REMINDER",
                    title = "warning reminder",
                    bizType = "WARNING",
                    bizId = 88L,
                    targetPath = "/warnings",
                    createdAt = LocalDateTime.now(),
                    totalDeliveries = 3,
                    pendingDeliveries = 1,
                    processingDeliveries = 0,
                    failedDeliveries = 2,
                    sentDeliveries = 0,
                    latestErrorMessage = "provider unavailable"
                )
            )
        )

        mockMvc.get("/api/v1/notifications/ops/feed")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data[0].id") { value(10) }
                jsonPath("$.data[0].failedDeliveries") { value(2) }
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
                    providerName = "fcm",
                    providerMessageId = "msg-1",
                    deliveredTime = null,
                    clickedTime = null,
                    errorMessage = "provider missing",
                    callbackPayloadJson = null,
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

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `retryFailedDeliveriesBatch allows admin role`() {
        `when`(notificationOpsService.retryFailedDeliveriesBatch(listOf(10L, 11L), "PUSH")).thenReturn(
            NotificationBatchRetryResult(notificationIds = listOf(10L, 11L), deliveryChannel = "PUSH", retriedCount = 4)
        )

        mockMvc.post("/api/v1/notifications/deliveries/retry-batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"notificationIds":[10,11],"deliveryChannel":"PUSH"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.retriedCount") { value(4) }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `applyPushDeliveryCallback allows admin role`() {
        val occurredAt = LocalDateTime.of(2026, 4, 17, 11, 0)
        `when`(
            notificationOpsService.applyPushDeliveryCallback(
                11L,
                "CLICKED",
                "fcm",
                "msg-1",
                null,
                "{\"source\":\"vendor\"}",
                occurredAt,
                occurredAt,
                occurredAt
            )
        ).thenReturn(
            NotificationDeliveryReceiptResult(
                deliveryId = 11L,
                notificationId = 10L,
                deliveryStatus = "CLICKED",
                readFlag = true,
                readTime = occurredAt,
                deliveredTime = occurredAt,
                clickedTime = occurredAt
            )
        )

        mockMvc.post("/api/v1/notifications/deliveries/11/callbacks") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"deliveryStatus":"CLICKED","providerName":"fcm","providerMessageId":"msg-1","callbackPayloadJson":"{\"source\":\"vendor\"}","deliveredAt":"2026-04-17T11:00:00","clickedAt":"2026-04-17T11:00:00","readAt":"2026-04-17T11:00:00"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.deliveryStatus") { value("CLICKED") }
            jsonPath("$.data.readFlag") { value(true) }
        }
    }
}
