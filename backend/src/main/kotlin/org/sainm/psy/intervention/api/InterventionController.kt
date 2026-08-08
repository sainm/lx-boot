package org.sainm.psy.intervention.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.intervention.service.InterventionService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/interventions")
class InterventionController(
    private val interventionService: InterventionService
) {

    @GetMapping("/by-warning/{warningId}")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findByWarningId(@PathVariable warningId: Long): ApiResponse<org.sainm.psy.intervention.domain.InterventionDetail?> =
        ApiResponse.ok(interventionService.findByWarningId(warningId))

    @PostMapping
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody request: CreateInterventionRequest): ApiResponse<InterventionActionResult> =
        ApiResponse.ok(interventionService.create(request))

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun close(
        @PathVariable id: Long,
        @Valid @RequestBody request: CloseInterventionRequest
    ): ApiResponse<InterventionActionResult> =
        ApiResponse.ok(interventionService.close(id, request))
}
