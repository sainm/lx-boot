package org.sainm.psy.appointment.service

import org.sainm.psy.appointment.api.AppointmentCreateResponse
import org.sainm.psy.appointment.api.CreateAppointmentRequest
import org.sainm.psy.appointment.api.CreateScheduleRequest
import org.sainm.psy.appointment.api.CreateScheduleResponse
import org.sainm.psy.appointment.domain.AppointmentSummary
import org.sainm.psy.appointment.domain.CounselorScheduleSummary
import org.sainm.psy.appointment.repository.AppointmentRepository
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val warningRepository: WarningRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val messages: LocalizedMessages
) {

    fun findSchedulesByCounselorId(counselorUserId: Long): List<CounselorScheduleSummary> =
        appointmentRepository.findSchedulesByCounselorId(counselorUserId)

    @Transactional
    fun createSchedule(request: CreateScheduleRequest): CreateScheduleResponse {
        require(request.endTime.isAfter(request.startTime)) { messages.get("error.end_time_after_start") }
        val counselorUserId = currentUserFacade.requireCurrentUserId()
        val id = appointmentRepository.createSchedule(request, counselorUserId)
        return CreateScheduleResponse(id = id)
    }

    @Transactional
    fun create(request: CreateAppointmentRequest): AppointmentCreateResponse {
        val currentUser = currentUserFacade.requireCurrentUser()
        val schedule = appointmentRepository.findScheduleById(request.scheduleId)
            ?: throw BizException("SCHEDULE_NOT_FOUND", messages.get("error.schedule_not_found"))
        if (schedule.counselorUserId != request.counselorUserId) {
            throw BizException("SCHEDULE_CONFLICT", messages.get("error.schedule_conflict"))
        }
        if (schedule.status != "AVAILABLE") {
            throw BizException("SCHEDULE_UNAVAILABLE", messages.get("error.schedule_unavailable"))
        }
        if (appointmentRepository.countActiveAppointmentsByScheduleId(request.scheduleId) >= schedule.quotaCount) {
            throw BizException("SCHEDULE_FULL", messages.get("error.schedule_full"))
        }
        request.warningId?.let {
            if (!warningRepository.existsById(it)) {
                throw BizException("WARNING_NOT_FOUND", messages.get("error.warning_not_found"))
            }
        }
        val sourceType = if ("ASSESSMENT_ADMIN" in currentUser.roles || "SYS_ADMIN" in currentUser.roles || "SUPER_ADMIN" in currentUser.roles) {
            "ADMIN"
        } else {
            "USER"
        }
        val appointmentId = appointmentRepository.createAppointment(
            request = request,
            userId = currentUser.userId,
            sourceType = sourceType
        )
        notificationDispatchService.notifyAppointmentCreated(appointmentId, listOf(request.counselorUserId))
        return AppointmentCreateResponse(appointmentId = appointmentId, status = "CONFIRMED")
    }

    fun findMyAppointments(): List<AppointmentSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return appointmentRepository.findMyAppointments(currentUser.userId)
    }
}
