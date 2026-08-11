package org.sainm.psy.warning.api

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateSafetyResponsePolicyRequest(
    @field:NotBlank val policyCode: String,
    @field:Min(1) val versionNo: Int,
    @field:NotBlank val riskCategory: String,
    @field:Min(1) val firstResponseMinutes: Int,
    @field:Min(1) val escalationMinutes: Int,
    @field:Min(1) val followUpMinutes: Int? = null,
    @field:NotBlank val responsibleRole: String,
    @field:NotBlank val backupRole: String,
    @field:NotBlank val emergencyContactText: String
)

data class ApproveSafetyResponsePolicyRequest(
    @field:NotNull val professionalReviewerId: Long
)
