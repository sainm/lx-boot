package org.sainm.psy.counseling.service

import org.sainm.psy.appointment.repository.AppointmentRepository
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.counseling.api.CreateCounselingRecordRequest
import org.sainm.psy.counseling.domain.CounselingRecordActionResult
import org.sainm.psy.counseling.repository.CounselingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CounselingService(
    private val counselingRepository: CounselingRepository,
    private val appointmentRepository: AppointmentRepository,
    private val currentUserFacade: CurrentUserFacade
) {

    @Transactional
    fun create(request: CreateCounselingRecordRequest): CounselingRecordActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val appointment = appointmentRepository.findAppointmentById(request.appointmentId)
            ?: throw BizException("APPOINTMENT_NOT_FOUND", "Appointment not found")
        if (
            appointment.counselorUserId != currentUser.userId &&
            "SUPER_ADMIN" !in currentUser.roles &&
            "ADMIN" !in currentUser.roles &&
            "ASSESSMENT_ADMIN" !in currentUser.roles
        ) {
            throw BizException("APPOINTMENT_FORBIDDEN", "You are not allowed to handle this appointment")
        }
        if (appointment.appointmentStatus == "CANCELLED" || appointment.appointmentStatus == "NO_SHOW") {
            throw BizException("APPOINTMENT_INVALID", "Current appointment status does not allow counseling record creation")
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
        appointmentRepository.updateAppointmentStatus(request.appointmentId, "COMPLETED")
        return CounselingRecordActionResult(
            recordId = recordId,
            appointmentId = request.appointmentId,
            appointmentStatus = "COMPLETED"
        )
    }
}
