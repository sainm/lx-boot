package org.sainm.psy.appointment.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.appointment.service.AppointmentService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(AppointmentController::class)
@Import(AuthSecurityConfiguration::class)
class AppointmentControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var appointmentService: AppointmentService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `createSchedule rejects anonymous request`() {
        mockMvc.post("/api/v1/counselors/me/schedules") {
            contentType = MediaType.APPLICATION_JSON
            content = scheduleRequestJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_401002") }
        }

        verifyNoInteractions(appointmentService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `createSchedule rejects USER role`() {
        mockMvc.post("/api/v1/counselors/me/schedules") {
            contentType = MediaType.APPLICATION_JSON
            content = scheduleRequestJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_403001") }
        }

        verifyNoInteractions(appointmentService)
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `createSchedule allows counselor role`() {
        val request = CreateScheduleRequest(
            scheduleDate = java.time.LocalDate.parse("2026-04-12"),
            startTime = java.time.LocalDateTime.parse("2026-04-12T09:00:00"),
            endTime = java.time.LocalDateTime.parse("2026-04-12T10:00:00"),
            quotaCount = 2
        )
        `when`(appointmentService.createSchedule(request)).thenReturn(CreateScheduleResponse(id = 100L))

        mockMvc.post("/api/v1/counselors/me/schedules") {
            contentType = MediaType.APPLICATION_JSON
            content = scheduleRequestJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.id") { value(100) }
        }
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `create appointment rejects counselor role`() {
        mockMvc.post("/api/v1/appointments") {
            contentType = MediaType.APPLICATION_JSON
            content = appointmentRequestJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_403001") }
        }

        verifyNoInteractions(appointmentService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `create appointment allows USER role`() {
        val request = CreateAppointmentRequest(counselorUserId = 20L, scheduleId = 30L, remark = "Need support")
        `when`(appointmentService.create(request)).thenReturn(
            AppointmentCreateResponse(appointmentId = 200L, status = "CONFIRMED")
        )

        mockMvc.post("/api/v1/appointments") {
            contentType = MediaType.APPLICATION_JSON
            content = appointmentRequestJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.appointmentId") { value(200) }
            jsonPath("$.data.status") { value("CONFIRMED") }
        }
    }

    private fun scheduleRequestJson(): String =
        """
            {
              "scheduleDate": "2026-04-12",
              "startTime": "2026-04-12T09:00:00",
              "endTime": "2026-04-12T10:00:00",
              "quotaCount": 2
            }
        """.trimIndent()

    private fun appointmentRequestJson(): String =
        """
            {
              "counselorUserId": 20,
              "scheduleId": 30,
              "remark": "Need support"
            }
        """.trimIndent()
}
