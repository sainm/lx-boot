package org.sainm.psy.appointment.api

import jakarta.validation.Valid
import org.sainm.psy.appointment.domain.AppointmentSummary
import org.sainm.psy.appointment.domain.AppointmentActionResult
import org.sainm.psy.appointment.service.AppointmentService
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1")
class AppointmentController(
    private val appointmentService: AppointmentService
) {

    @GetMapping("/counselors")
    @PreAuthorize("isAuthenticated()")
    fun findCounselors(): ApiResponse<List<CounselorOptionResponse>> =
        ApiResponse.ok(appointmentService.findBookableCounselors())

    @GetMapping("/counselors/{id}/schedules")
    @PreAuthorize("isAuthenticated()")
    fun findSchedules(@PathVariable id: Long): ApiResponse<List<org.sainm.psy.appointment.domain.CounselorScheduleSummary>> =
        ApiResponse.ok(appointmentService.findSchedulesByCounselorId(id))

    @PostMapping("/counselors/me/schedules")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun createSchedule(@Valid @RequestBody request: CreateScheduleRequest): ApiResponse<CreateScheduleResponse> =
        ApiResponse.ok(appointmentService.createSchedule(request))

    @PostMapping("/appointments")
    @PreAuthorize("hasAnyRole('USER', 'COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody request: CreateAppointmentRequest): ApiResponse<AppointmentCreateResponse> =
        ApiResponse.ok(appointmentService.create(request))

    @PostMapping("/appointments/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    fun cancel(@PathVariable id: Long): ApiResponse<AppointmentActionResult> =
        ApiResponse.ok(appointmentService.cancel(id))

    @GetMapping("/appointments/my")
    @PreAuthorize("isAuthenticated()")
    fun findMyAppointments(): ApiResponse<List<AppointmentSummary>> =
        ApiResponse.ok(appointmentService.findMyAppointments())

    /**
     * Tenant-wide appointment register used by the staff "预约管理" view
     * (booked-for respondents, filters, pagination).
     */
    @GetMapping("/appointments")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findPage(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) counselorUserId: Long?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<AppointmentSummary>> =
        ApiResponse.ok(
            appointmentService.findPage(
                AppointmentListQuery(
                    status = status,
                    userId = userId,
                    counselorUserId = counselorUserId,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    page = page,
                    size = size
                )
            )
        )
}
