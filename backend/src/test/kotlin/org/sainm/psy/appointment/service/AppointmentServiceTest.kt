package org.sainm.psy.appointment.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.appointment.api.CreateAppointmentRequest
import org.sainm.psy.appointment.domain.CounselorScheduleSummary
import org.sainm.psy.appointment.repository.AppointmentRepository
import org.sainm.psy.auth.CurrentUser
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AppointmentServiceTest {

    @Mock private lateinit var appointmentRepository: AppointmentRepository
    @Mock private lateinit var warningRepository: WarningRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService

    @InjectMocks
    private lateinit var appointmentService: AppointmentService

    private val counselorUser = CurrentUser(
        userId = 10L,
        username = "user01",
        displayName = "User",
        tenantId = 1L,
        groupId = null,
        roles = setOf("USER"),
        permissions = emptySet()
    )

    private val adminUser = CurrentUser(
        userId = 99L,
        username = "admin01",
        displayName = "Admin",
        tenantId = 1L,
        groupId = null,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )

    private fun availableSchedule(counselorUserId: Long = 5L, quota: Int = 3, booked: Int = 0) =
        CounselorScheduleSummary(
            id = 100L,
            counselorUserId = counselorUserId,
            scheduleDate = LocalDate.now(),
            startTime = LocalDateTime.now(),
            endTime = LocalDateTime.now().plusHours(1),
            quotaCount = quota,
            bookedCount = booked,
            availableCount = (quota - booked).coerceAtLeast(0),
            status = "AVAILABLE"
        )

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create throws BizException when schedule not found`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findScheduleById(100L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L))
        }
        assertEquals("SCHEDULE_NOT_FOUND", ex.code)
    }

    @Test
    fun `create throws BizException when schedule belongs to different counselor`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findScheduleById(100L)).thenReturn(availableSchedule(counselorUserId = 999L))

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L))
        }
        assertEquals("SCHEDULE_CONFLICT", ex.code)
    }

    @Test
    fun `create throws BizException when schedule is not available`() {
        val unavailable = availableSchedule().copy(status = "CLOSED")
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findScheduleById(100L)).thenReturn(unavailable)

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L))
        }
        assertEquals("SCHEDULE_UNAVAILABLE", ex.code)
    }

    @Test
    fun `create throws BizException when schedule is full`() {
        val fullSchedule = availableSchedule(quota = 2, booked = 0)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findScheduleById(100L)).thenReturn(fullSchedule)
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(2)

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L))
        }
        assertEquals("SCHEDULE_FULL", ex.code)
    }

    @Test
    fun `create throws BizException when warningId provided but warning not found`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findScheduleById(100L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(0)
        `when`(warningRepository.existsById(77L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            appointmentService.create(
                CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L, warningId = 77L)
            )
        }
        assertEquals("WARNING_NOT_FOUND", ex.code)
        verify(appointmentRepository, never()).createAppointment(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString()
        )
    }

    @Test
    fun `create succeeds with USER sourceType for regular user`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findScheduleById(100L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(0)
        val request = CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L)
        `when`(appointmentRepository.createAppointment(request, 10L, "USER")).thenReturn(200L)

        val result = appointmentService.create(request)

        assertEquals(200L, result.appointmentId)
        assertEquals("CONFIRMED", result.status)
        verify(notificationDispatchService).notifyUsers(
            notificationType = "APPOINTMENT_CREATED",
            title = "收到新的咨询预约",
            content = "预约 #200 已创建，请按排班时间准备咨询。",
            bizType = "APPOINTMENT",
            bizId = 200L,
            targetPath = "/appointments",
            payloadJson = null,
            receiverUserIds = listOf(5L)
        )
    }

    @Test
    fun `create uses ADMIN sourceType for admin roles`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(adminUser)
        `when`(appointmentRepository.findScheduleById(100L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(1)
        val request = CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L)
        `when`(appointmentRepository.createAppointment(request, 99L, "ADMIN")).thenReturn(201L)

        val result = appointmentService.create(request)

        assertEquals(201L, result.appointmentId)
        assertEquals("CONFIRMED", result.status)
    }

    @Test
    fun `create succeeds with warningId when warning exists`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findScheduleById(100L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(0)
        `when`(warningRepository.existsById(50L)).thenReturn(true)
        val request = CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L, warningId = 50L)
        `when`(appointmentRepository.createAppointment(request, 10L, "USER")).thenReturn(202L)

        val result = appointmentService.create(request)

        assertEquals(202L, result.appointmentId)
    }
}
