package org.sainm.psy.warning.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.warning.domain.SafetyResponsePolicy
import org.sainm.psy.warning.service.SafetyResponsePolicyService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/safety-response-policies")
class SafetyResponsePolicyController(
    private val service: SafetyResponsePolicyService
) {
    @GetMapping
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'COUNSELOR', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findAll(): ApiResponse<List<SafetyResponsePolicy>> = ApiResponse.ok(service.findAll())

    @PostMapping
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody request: CreateSafetyResponsePolicyRequest): ApiResponse<SafetyResponsePolicy> =
        ApiResponse.ok(service.create(request))

    @PostMapping("/{id}/professional-review")
    @PreAuthorize("hasRole('COUNSELOR')")
    fun professionalReview(@PathVariable id: Long): ApiResponse<SafetyResponsePolicy> =
        ApiResponse.ok(service.professionalReview(id))

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun approve(@PathVariable id: Long): ApiResponse<SafetyResponsePolicy> =
        ApiResponse.ok(service.approve(id))
}
