package org.sainm.psy.appointment.service

import org.sainm.psy.appointment.api.AppointmentCreateResponse
import org.sainm.psy.appointment.domain.AppointmentActionResult
import org.sainm.psy.appointment.api.CounselorOptionResponse
import org.sainm.psy.appointment.api.CreateAppointmentRequest
import org.sainm.psy.appointment.api.CreateScheduleRequest
import org.sainm.psy.appointment.api.CreateScheduleResponse
import org.sainm.psy.appointment.api.RescheduleAppointmentRequest
import org.sainm.psy.appointment.domain.AppointmentSummary
import org.sainm.psy.appointment.domain.CounselorScheduleSummary
import org.sainm.psy.appointment.domain.AppointmentDetail
import org.sainm.psy.appointment.domain.AppointmentStatusLog
import org.sainm.psy.appointment.repository.AppointmentRepository
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@Service
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val warningRepository: WarningRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val securityAuditService: SecurityAuditService,
    private val messages: LocalizedMessages
) {
    private val logger = LoggerFactory.getLogger(AppointmentService::class.java)

    fun findBookableCounselors(): List<CounselorOptionResponse> {
        val currentUser = currentUserFacade.requireCurrentUser()
        val tenantId = if (currentUser.isGlobalAdmin()) null else currentUser.tenantId
            ?: throw BizException("APPOINTMENT_TENANT_REQUIRED", messages.get("error.appointment_tenant_required"))
        return appointmentRepository.findBookableCounselors(tenantId).map {
            CounselorOptionResponse(
                userId = it.userId,
                username = it.username,
                displayName = it.displayName
            )
        }
    }

    fun findSchedulesByCounselorId(counselorUserId: Long): List<CounselorScheduleSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!currentUser.isGlobalAdmin() && !appointmentRepository.isUserInTenant(counselorUserId, currentUser.tenantId)) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        return appointmentRepository.findSchedulesByCounselorId(counselorUserId)
    }

    @Transactional
    fun createSchedule(request: CreateScheduleRequest): CreateScheduleResponse {
        require(request.endTime.isAfter(request.startTime)) { messages.get("error.end_time_after_start") }
        require(request.startTime.isAfter(LocalDateTime.now())) { messages.get("error.schedule_must_be_future") }
        require(request.scheduleDate == request.startTime.toLocalDate() && request.scheduleDate == request.endTime.toLocalDate()) {
            messages.get("error.schedule_date_mismatch")
        }
        val counselorUserId = currentUserFacade.requireCurrentUserId()
        appointmentRepository.lockCounselorScheduleScope(counselorUserId)
        if (appointmentRepository.hasOverlappingSchedule(counselorUserId, request.startTime, request.endTime)) {
            throw BizException("SCHEDULE_OVERLAP", messages.get("error.schedule_overlap"))
        }
        val id = appointmentRepository.createSchedule(request, counselorUserId)
        return CreateScheduleResponse(id = id)
    }

    @Transactional
    fun create(request: CreateAppointmentRequest): AppointmentCreateResponse {
        val currentUser = currentUserFacade.requireCurrentUser()
        val schedule = appointmentRepository.findScheduleByIdForUpdate(request.scheduleId)
            ?: throw BizException("SCHEDULE_NOT_FOUND", messages.get("error.schedule_not_found"))
        if (schedule.counselorUserId != request.counselorUserId) {
            throw BizException("SCHEDULE_CONFLICT", messages.get("error.schedule_conflict"))
        }
        if (!currentUser.isGlobalAdmin() && !appointmentRepository.isUserInTenant(schedule.counselorUserId, currentUser.tenantId)) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        if (schedule.status != "AVAILABLE") {
            throw BizException("SCHEDULE_UNAVAILABLE", messages.get("error.schedule_unavailable"))
        }
        if (!schedule.startTime.isAfter(LocalDateTime.now())) {
            throw BizException("SCHEDULE_UNAVAILABLE", messages.get("error.schedule_unavailable"))
        }
        if (appointmentRepository.countActiveAppointmentsByScheduleId(request.scheduleId) >= schedule.quotaCount) {
            throw BizException("SCHEDULE_FULL", messages.get("error.schedule_full"))
        }
        val staffRoles = setOf("COUNSELOR", "ASSESSMENT_ADMIN", "ORG_MANAGER", "ADMIN", "SYS_ADMIN", "SUPER_ADMIN")
        val isStaff = currentUser.roles.any(staffRoles::contains)
        val targetUserId = request.userId ?: currentUser.userId
        if (!isStaff && targetUserId != currentUser.userId) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        if (targetUserId != currentUser.userId && !currentUser.isGlobalAdmin() && !appointmentRepository.isUserInTenant(targetUserId, currentUser.tenantId)) {
            throw BizException("APPOINTMENT_TARGET_OUT_OF_SCOPE", messages.get("error.appointment_target_out_of_scope"))
        }
        request.warningId?.let {
            if (!warningRepository.existsById(it)) {
                throw BizException("WARNING_NOT_FOUND", messages.get("error.warning_not_found"))
            }
            if (!currentUser.isGlobalAdmin() && currentUser.tenantId != warningRepository.findTenantId(it)) {
                throw BizException("WARNING_FORBIDDEN", messages.get("error.warning_forbidden"))
            }
            if (warningRepository.findSubjectUserId(it) != targetUserId) {
                throw BizException("WARNING_SUBJECT_MISMATCH", messages.get("error.warning_subject_mismatch"))
            }
        }
        val sourceType = if (isStaff) {
            "ADMIN"
        } else {
            "USER"
        }
        val appointmentId = appointmentRepository.createAppointment(
            request = request,
            userId = targetUserId,
            sourceType = sourceType
        )
        appointmentRepository.createStatusLog(
            appointmentId = appointmentId,
            fromStatus = null,
            toStatus = "CONFIRMED",
            actionType = "CREATED",
            operatorUserId = currentUser.userId,
            fromScheduleId = null,
            toScheduleId = request.scheduleId,
            remark = request.remark
        )
        securityAuditService.recordAppointmentTransition(appointmentId, null, "CONFIRMED", "CREATED", request.scheduleId)
        runCatching {
            notificationDispatchService.notifyAppointmentCreated(appointmentId, setOf(targetUserId, request.counselorUserId))
        }.onFailure { logger.error("Failed to dispatch appointment-created notification. appointmentId={}", appointmentId, it) }
        return AppointmentCreateResponse(appointmentId = appointmentId, status = "CONFIRMED")
    }

    fun findMyAppointments(): List<AppointmentSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return when {
            currentUser.roles.any { it in setOf("ADMIN", "SYS_ADMIN", "SUPER_ADMIN") } && currentUser.tenantId == null ->
                appointmentRepository.findAllAppointments()
            currentUser.roles.any { it in setOf("ASSESSMENT_ADMIN", "ORG_MANAGER", "ADMIN", "SYS_ADMIN", "SUPER_ADMIN") } && currentUser.tenantId != null ->
                appointmentRepository.findTenantAppointments(requireNotNull(currentUser.tenantId))
            "COUNSELOR" in currentUser.roles -> appointmentRepository.findCounselorAppointments(currentUser.userId)
            else -> appointmentRepository.findMyAppointments(currentUser.userId)
        }
    }

    @Transactional
    fun cancel(appointmentId: Long): AppointmentActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val appointment = appointmentRepository.findAppointmentByIdForUpdate(appointmentId)
            ?: throw BizException("APPOINTMENT_NOT_FOUND", messages.get("error.appointment_not_found"))
        requireAppointmentAccess(appointment, currentUser)
        if (appointment.appointmentStatus in setOf("CANCELLED", "COMPLETED", "NO_SHOW")) {
            throw BizException("APPOINTMENT_CANNOT_CANCEL", messages.get("error.appointment_cannot_cancel"))
        }
        appointmentRepository.updateAppointmentStatus(appointmentId, "CANCELLED")
        appointmentRepository.createStatusLog(
            appointmentId, appointment.appointmentStatus, "CANCELLED", "CANCELLED",
            currentUser.userId, appointment.scheduleId, appointment.scheduleId
        )
        securityAuditService.recordAppointmentTransition(
            appointmentId, appointment.appointmentStatus, "CANCELLED", "CANCELLED", appointment.scheduleId
        )
        runCatching {
            notificationDispatchService.notifyAppointmentCancelled(
                appointmentId,
                setOf(appointment.userId, appointment.counselorUserId)
            )
        }.onFailure { logger.error("Failed to dispatch appointment-cancelled notification. appointmentId={}", appointmentId, it) }
        return AppointmentActionResult(
            appointmentId = appointmentId,
            status = "CANCELLED"
        )
    }

    @Transactional
    fun reschedule(appointmentId: Long, request: RescheduleAppointmentRequest): AppointmentActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val appointment = appointmentRepository.findAppointmentByIdForUpdate(appointmentId)
            ?: throw BizException("APPOINTMENT_NOT_FOUND", messages.get("error.appointment_not_found"))
        requireAppointmentAccess(appointment, currentUser)
        if (appointment.appointmentStatus !in setOf("CREATED", "CONFIRMED")) {
            throw BizException("APPOINTMENT_CANNOT_RESCHEDULE", messages.get("error.appointment_cannot_reschedule"))
        }
        if (appointment.scheduleId == request.scheduleId) {
            throw BizException("APPOINTMENT_SCHEDULE_UNCHANGED", messages.get("error.appointment_schedule_unchanged"))
        }
        val schedule = appointmentRepository.findScheduleByIdForUpdate(request.scheduleId)
            ?: throw BizException("SCHEDULE_NOT_FOUND", messages.get("error.schedule_not_found"))
        if (schedule.counselorUserId != request.counselorUserId) {
            throw BizException("SCHEDULE_CONFLICT", messages.get("error.schedule_conflict"))
        }
        if (schedule.status != "AVAILABLE" || !schedule.startTime.isAfter(LocalDateTime.now())) {
            throw BizException("SCHEDULE_UNAVAILABLE", messages.get("error.schedule_unavailable"))
        }
        if (!currentUser.isGlobalAdmin() && !appointmentRepository.isUserInTenant(schedule.counselorUserId, currentUser.tenantId)) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        if (appointmentRepository.countActiveAppointmentsByScheduleId(request.scheduleId) >= schedule.quotaCount) {
            throw BizException("SCHEDULE_FULL", messages.get("error.schedule_full"))
        }
        appointmentRepository.rescheduleAppointment(
            appointmentId = appointmentId,
            counselorUserId = request.counselorUserId,
            scheduleId = request.scheduleId,
            remark = request.remark
        )
        appointmentRepository.createStatusLog(
            appointmentId, appointment.appointmentStatus, "CONFIRMED", "RESCHEDULED",
            currentUser.userId, appointment.scheduleId, request.scheduleId, request.remark
        )
        securityAuditService.recordAppointmentTransition(
            appointmentId, appointment.appointmentStatus, "CONFIRMED", "RESCHEDULED", request.scheduleId
        )
        runCatching {
            notificationDispatchService.notifyAppointmentRescheduled(
                appointmentId,
                setOf(appointment.userId, appointment.counselorUserId, request.counselorUserId)
            )
        }.onFailure { logger.error("Failed to dispatch appointment-rescheduled notification. appointmentId={}", appointmentId, it) }
        return AppointmentActionResult(appointmentId, "CONFIRMED")
    }

    fun history(appointmentId: Long): List<AppointmentStatusLog> {
        val currentUser = currentUserFacade.requireCurrentUser()
        val appointment = appointmentRepository.findAppointmentById(appointmentId)
            ?: throw BizException("APPOINTMENT_NOT_FOUND", messages.get("error.appointment_not_found"))
        requireAppointmentAccess(appointment, currentUser)
        return appointmentRepository.findStatusLogs(appointmentId)
    }

    @Transactional
    fun markNoShow(appointmentId: Long): AppointmentActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val appointment = appointmentRepository.findAppointmentByIdForUpdate(appointmentId)
            ?: throw BizException("APPOINTMENT_NOT_FOUND", messages.get("error.appointment_not_found"))
        val privileged = currentUser.roles.any { it in setOf("ASSESSMENT_ADMIN", "ORG_MANAGER", "ADMIN", "SYS_ADMIN", "SUPER_ADMIN") }
        if (appointment.counselorUserId != currentUser.userId && !privileged) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        if (!currentUser.isGlobalAdmin() && !appointmentRepository.isUserInTenant(appointment.userId, currentUser.tenantId)) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        if (appointment.appointmentStatus != "CONFIRMED" || !appointmentRepository.isAppointmentPastEnd(appointmentId, LocalDateTime.now())) {
            throw BizException("APPOINTMENT_NO_SHOW_NOT_ALLOWED", messages.get("error.appointment_no_show_not_allowed"))
        }
        appointmentRepository.updateAppointmentStatus(appointmentId, "NO_SHOW")
        appointmentRepository.createStatusLog(
            appointmentId, appointment.appointmentStatus, "NO_SHOW", "NO_SHOW",
            currentUser.userId, appointment.scheduleId, appointment.scheduleId
        )
        securityAuditService.recordAppointmentTransition(
            appointmentId, appointment.appointmentStatus, "NO_SHOW", "NO_SHOW", appointment.scheduleId
        )
        runCatching { notificationDispatchService.notifyAppointmentNoShow(appointmentId, listOf(appointment.userId)) }
            .onFailure { logger.error("Failed to dispatch appointment-no-show notification. appointmentId={}", appointmentId, it) }
        return AppointmentActionResult(appointmentId, "NO_SHOW")
    }

    private fun requireAppointmentAccess(appointment: AppointmentDetail, currentUser: UserPrincipal) {
        val privileged = currentUser.roles.any {
            it in setOf("ASSESSMENT_ADMIN", "ORG_MANAGER", "ADMIN", "SYS_ADMIN", "SUPER_ADMIN")
        }
        if (appointment.userId != currentUser.userId && appointment.counselorUserId != currentUser.userId && !privileged) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        if (!currentUser.isGlobalAdmin() && !appointmentRepository.isUserInTenant(appointment.userId, currentUser.tenantId)) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
    }

    private fun UserPrincipal.isGlobalAdmin(): Boolean =
        tenantId == null && roles.any { it in setOf("ADMIN", "SYS_ADMIN", "SUPER_ADMIN") }
}
