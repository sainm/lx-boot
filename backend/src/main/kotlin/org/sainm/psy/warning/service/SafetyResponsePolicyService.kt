package org.sainm.psy.warning.service

import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.warning.api.ApproveSafetyResponsePolicyRequest
import org.sainm.psy.warning.api.CreateSafetyResponsePolicyRequest
import org.sainm.psy.warning.domain.SafetyResponsePolicy
import org.sainm.psy.warning.repository.SafetyResponsePolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SafetyResponsePolicyService(
    private val repository: SafetyResponsePolicyRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val tenantAccessPolicy: TenantAccessPolicy
) {
    fun findAll(): List<SafetyResponsePolicy> {
        return repository.findAll(tenantAccessPolicy.requireTenantId())
    }

    @Transactional
    fun create(request: CreateSafetyResponsePolicyRequest): SafetyResponsePolicy {
        val currentUser = currentUserFacade.requireCurrentUser()
        val tenantId = tenantAccessPolicy.requireTenantId()
        val riskCategory = request.riskCategory.trim().uppercase()
        if (riskCategory !in setOf("P0", "P1", "P2", "P3")) {
            throw BizException("SAFETY_POLICY_RISK_INVALID", messages.get("error.safety_policy_risk_invalid"))
        }
        if (request.escalationMinutes < request.firstResponseMinutes) {
            throw BizException("SAFETY_POLICY_SLA_INVALID", messages.get("error.safety_policy_sla_invalid"))
        }
        val id = repository.create(tenantId, request, currentUser.userId)
        return repository.findById(id, tenantId)
            ?: error("created safety response policy cannot be loaded")
    }

    @Transactional
    fun approve(id: Long, request: ApproveSafetyResponsePolicyRequest): SafetyResponsePolicy {
        val currentUser = currentUserFacade.requireCurrentUser()
        val tenantId = tenantAccessPolicy.requireTenantId()
        if (request.professionalReviewerId == currentUser.userId) {
            throw BizException("SAFETY_POLICY_DUAL_REVIEW_REQUIRED", messages.get("error.safety_policy_dual_review_required"))
        }
        if (!repository.isCounselorInTenant(request.professionalReviewerId, tenantId)) {
            throw BizException("SAFETY_POLICY_REVIEWER_INVALID", messages.get("error.safety_policy_reviewer_invalid"))
        }
        if (!repository.approveAndActivate(id, tenantId, currentUser.userId, request.professionalReviewerId)) {
            throw BizException("SAFETY_POLICY_NOT_DRAFT", messages.get("error.safety_policy_not_draft"))
        }
        return repository.findById(id, tenantId)
            ?: error("approved safety response policy cannot be loaded")
    }
}
