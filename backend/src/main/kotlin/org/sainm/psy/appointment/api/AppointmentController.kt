package org.sainm.psy.appointment.api

import jakarta.validation.Valid
import org.sainm.psy.appointment.domain.AppointmentSummary
import org.sainm.psy.appointment.service.AppointmentService
import org.sainm.psy.common.api.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AppointmentController(
    private val appointmentService: AppointmentService
) {

    @GetMapping("/counselors/{id}/schedules")
    @PreAuthorize("isAuthenticated()")
    fun findSchedules(@PathVariable id: Long): ApiResponse<List<org.sainm.psy.appointment.domain.CounselorScheduleSummary>> =
        ApiResponse.ok(appointmentService.findSchedulesByCounselorId(id))

    @PostMapping("/appointments")
    @PreAuthorize("hasAnyRole('USER', 'ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody request: CreateAppointmentRequest): ApiResponse<AppointmentCreateResponse> =
        ApiResponse.ok(appointmentService.create(request))

    @GetMapping("/appointments/my")
    @PreAuthorize("isAuthenticated()")
    fun findMyAppointments(): ApiResponse<List<AppointmentSummary>> =
        ApiResponse.ok(appointmentService.findMyAppointments())
}
