package org.sainm.psy.counseling.service

import org.sainm.psy.appointment.repository.AppointmentRepository
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.counseling.api.CreateCounselingRecordRequest
import org.sainm.psy.counseling.domain.CounselingRecordActionResult
import org.sainm.psy.counseling.repository.CounselingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CounselingService(
    private val counselingRepository: CounselingRepository,
    private val appointmentRepository: AppointmentRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val securityAuditService: SecurityAuditService,
    private val messages: LocalizedMessages
) {

    @Transactional
    fun create(request: CreateCounselingRecordRequest): CounselingRecordActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val appointment = appointmentRepository.findAppointmentByIdForUpdate(request.appointmentId)
            ?: throw BizException("APPOINTMENT_NOT_FOUND", messages.get("error.appointment_not_found"))
        val globalAdmin = currentUser.tenantId == null && currentUser.roles.any {
            it in setOf("ADMIN", "SYS_ADMIN", "SUPER_ADMIN")
        }
        if (!globalAdmin && !appointmentRepository.isUserInTenant(appointment.userId, currentUser.tenantId)) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        if (
            appointment.counselorUserId != currentUser.userId &&
            "ORG_MANAGER" !in currentUser.roles &&
            "SYS_ADMIN" !in currentUser.roles &&
            "SUPER_ADMIN" !in currentUser.roles &&
            "ADMIN" !in currentUser.roles &&
            "ASSESSMENT_ADMIN" !in currentUser.roles
        ) {
            throw BizException("APPOINTMENT_FORBIDDEN", messages.get("error.appointment_forbidden"))
        }
        if (appointment.appointmentStatus == "CANCELLED" || appointment.appointmentStatus == "NO_SHOW") {
            throw BizException("APPOINTMENT_INVALID", messages.get("error.appointment_invalid"))
        }

        val existing = counselingRepository.findByAppointmentId(request.appointmentId)
        val recordId = if (existing == null) {
            counselingRepository.createRecord(
                appointmentId = request.appointmentId,
                counselorUserId = currentUser.userId,
                summaryText = request.summaryText,
                suggestionText = request.suggestionText,
                needRetestFlag = request.needRetestFlag,
                needTransferFlag = request.needTransferFlag
            )
        } else {
            counselingRepository.updateRecord(
                recordId = existing.id,
                summaryText = request.summaryText,
                suggestionText = request.suggestionText,
                needRetestFlag = request.needRetestFlag,
                needTransferFlag = request.needTransferFlag
            )
            existing.id
        }
        if (appointment.appointmentStatus != "COMPLETED") {
            appointmentRepository.updateAppointmentStatus(request.appointmentId, "COMPLETED")
            appointmentRepository.createStatusLog(
                appointmentId = request.appointmentId,
                fromStatus = appointment.appointmentStatus,
                toStatus = "COMPLETED",
                actionType = "COMPLETED",
                operatorUserId = currentUser.userId,
                fromScheduleId = appointment.scheduleId,
                toScheduleId = appointment.scheduleId
            )
            securityAuditService.recordAppointmentTransition(
                request.appointmentId,
                appointment.appointmentStatus,
                "COMPLETED",
                "COMPLETED",
                appointment.scheduleId
            )
        }
        return CounselingRecordActionResult(
            recordId = recordId,
            appointmentId = request.appointmentId,
            appointmentStatus = "COMPLETED"
        )
    }
}
