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
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val warningRepository: WarningRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService
) {

    fun findSchedulesByCounselorId(counselorUserId: Long): List<CounselorScheduleSummary> =
        appointmentRepository.findSchedulesByCounselorId(counselorUserId)

    @Transactional
    fun createSchedule(request: CreateScheduleRequest): CreateScheduleResponse {
        require(request.endTime.isAfter(request.startTime)) { "结束时间必须晚于开始时间" }
        val counselorUserId = currentUserFacade.requireCurrentUserId()
        val id = appointmentRepository.createSchedule(request, counselorUserId)
        return CreateScheduleResponse(id = id)
    }

    @Transactional
    fun create(request: CreateAppointmentRequest): AppointmentCreateResponse {
        val currentUser = currentUserFacade.requireCurrentUser()
        val schedule = appointmentRepository.findScheduleById(request.scheduleId)
            ?: throw BizException("SCHEDULE_NOT_FOUND", "Counselor schedule not found")
        if (schedule.counselorUserId != request.counselorUserId) {
            throw BizException("SCHEDULE_CONFLICT", "Schedule does not belong to the specified counselor")
        }
        if (schedule.status != "AVAILABLE") {
            throw BizException("SCHEDULE_UNAVAILABLE", "Current schedule is not available")
        }
        if (appointmentRepository.countActiveAppointmentsByScheduleId(request.scheduleId) >= schedule.quotaCount) {
            throw BizException("SCHEDULE_FULL", "Current schedule is full")
        }
        request.warningId?.let {
            if (!warningRepository.existsById(it)) {
                throw BizException("WARNING_NOT_FOUND", "Warning not found")
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
        notificationDispatchService.notifyUsers(
            notificationType = "APPOINTMENT_CREATED",
            title = "收到新的咨询预约",
            content = "预约 #$appointmentId 已创建，请按排班时间准备咨询。",
            bizType = "APPOINTMENT",
            bizId = appointmentId,
            targetPath = "/appointments",
            payloadJson = null,
            receiverUserIds = listOf(request.counselorUserId)
        )
        return AppointmentCreateResponse(appointmentId = appointmentId, status = "CONFIRMED")
    }

    fun findMyAppointments(): List<AppointmentSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return appointmentRepository.findMyAppointments(currentUser.userId)
    }
}
