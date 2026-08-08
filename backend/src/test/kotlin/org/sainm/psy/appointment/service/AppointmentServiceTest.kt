package org.sainm.psy.appointment.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.appointment.api.CreateAppointmentRequest
import org.sainm.psy.appointment.domain.CounselorScheduleSummary
import org.sainm.psy.appointment.repository.AppointmentRepository
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AppointmentServiceTest {

    @Mock private lateinit var appointmentRepository: AppointmentRepository
    @Mock private lateinit var warningRepository: WarningRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService

    private lateinit var appointmentService: AppointmentService

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        appointmentService = AppointmentService(
            appointmentRepository = appointmentRepository,
            warningRepository = warningRepository,
            currentUserFacade = currentUserFacade,
            notificationDispatchService = notificationDispatchService,
            messages = LocalizedMessages(messageSource)
        )
    }

    private val user = UserPrincipal(
        userId = 10L,
        username = "user01",
        displayName = "User",
        status = UserStatus.ENABLED,
        tenantId = 1L,
        groupId = null,
        roles = setOf("USER"),
        permissions = emptySet()
    )

    private val admin = UserPrincipal(
        userId = 99L,
        username = "admin01",
        displayName = "Admin",
        status = UserStatus.ENABLED,
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
            status = "AVAILABLE",
            tenantId = 1L
        )

    @Test
    fun `create throws BizException when schedule not found`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L))
        }
        assertEquals("SCHEDULE_NOT_FOUND", ex.code)
    }

    @Test
    fun `create throws BizException when schedule belongs to different counselor`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(availableSchedule(counselorUserId = 999L))

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L))
        }
        assertEquals("SCHEDULE_CONFLICT", ex.code)
    }

    @Test
    fun `create throws BizException when schedule is not available`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(availableSchedule().copy(status = "CLOSED"))

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L))
        }
        assertEquals("SCHEDULE_UNAVAILABLE", ex.code)
    }

    @Test
    fun `create throws BizException when schedule is full`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(availableSchedule(quota = 2))
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(2)

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L))
        }
        assertEquals("SCHEDULE_FULL", ex.code)
    }

    @Test
    fun `create throws BizException when warningId provided but warning not found`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(0)
        `when`(warningRepository.existsById(77L, 1L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            appointmentService.create(CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L, warningId = 77L))
        }
        assertEquals("WARNING_NOT_FOUND", ex.code)
    }

    @Test
    fun `create succeeds with USER sourceType for regular user`() {
        val request = CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(0)
        `when`(appointmentRepository.createAppointment(request, 10L, "USER")).thenReturn(200L)

        val result = appointmentService.create(request)

        assertEquals(200L, result.appointmentId)
        assertEquals("CONFIRMED", result.status)
        verify(notificationDispatchService).notifyAppointmentCreated(200L, listOf(5L))
    }

    @Test
    fun `create uses ADMIN sourceType for admin roles`() {
        val request = CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(admin)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(1)
        `when`(appointmentRepository.createAppointment(request, 99L, "ADMIN")).thenReturn(201L)

        val result = appointmentService.create(request)

        assertEquals(201L, result.appointmentId)
        assertEquals("CONFIRMED", result.status)
        verify(notificationDispatchService).notifyAppointmentCreated(201L, listOf(5L))
    }

    @Test
    fun `create succeeds with warningId when warning exists`() {
        val request = CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L, warningId = 50L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(0)
        `when`(warningRepository.existsById(50L, 1L)).thenReturn(true)
        `when`(appointmentRepository.createAppointment(request, 10L, "USER")).thenReturn(202L)

        val result = appointmentService.create(request)

        assertEquals(202L, result.appointmentId)
        verify(notificationDispatchService).notifyAppointmentCreated(202L, listOf(5L))
    }

    @Test
    fun `create locks schedule row before checking quota`() {
        val request = CreateAppointmentRequest(counselorUserId = 5L, scheduleId = 100L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(appointmentRepository.findScheduleByIdForUpdate(100L, 1L)).thenReturn(availableSchedule())
        `when`(appointmentRepository.countActiveAppointmentsByScheduleId(100L)).thenReturn(0)
        `when`(appointmentRepository.createAppointment(request, 10L, "USER")).thenReturn(203L)

        appointmentService.create(request)

        verify(appointmentRepository).findScheduleByIdForUpdate(100L, 1L)
    }
}
