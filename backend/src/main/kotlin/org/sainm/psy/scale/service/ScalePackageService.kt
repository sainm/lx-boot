package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.exception.NotFoundBizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.scale.api.UpdateScalePackageRequest
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ScalePackageService(
    private val scaleRepository: ScaleRepository,
    private val packageRepository: ScalePackageRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val objectMapper: ObjectMapper,
    private val securityAuditService: SecurityAuditService,
    private val tenantAccessPolicy: TenantAccessPolicy
) {
    fun find(scaleId: Long): ScalePackageSnapshot {
        requireOwnedScale(scaleId)
        return packageRepository.find(scaleId)
    }

    @Transactional
    fun replace(scaleId: Long, request: UpdateScalePackageRequest): ScalePackageSnapshot {
        val scale = requireOwnedScaleForUpdate(scaleId)
        if (scale.status != "DRAFT") {
            throw BizException("SCALE_NOT_DRAFT", messages.get("scale.publish.draft_required"))
        }
        validate(request, scale)
        packageRepository.replace(scaleId, request, currentUserFacade.requireCurrentUserId())
        securityAuditService.recordScalePackageUpdated(
            scaleId,
            request.translations.map { it.localeCode },
            request.validityRules.size
        )
        return packageRepository.find(scaleId)
    }

    private fun requireOwnedScale(scaleId: Long): ScaleDetail {
        val scale = scaleRepository.findDetailById(scaleId)
            ?: throw NotFoundBizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        if (!tenantAccessPolicy.canAccess(scale.tenantId, "SCALE_PACKAGE", scaleId, "READ_OR_MUTATE")) {
            throw NotFoundBizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        return scale
    }

    private fun requireOwnedScaleForUpdate(scaleId: Long): ScaleDetail {
        if (!scaleRepository.lockById(scaleId)) {
            throw NotFoundBizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        return requireOwnedScale(scaleId)
    }

    private fun validate(request: UpdateScalePackageRequest, scale: ScaleDetail) {
        val localeLists = listOf(
            request.translations.map { it.localeCode },
            request.dimensionTranslations.map { "${it.dimensionId}:${it.localeCode}" },
            request.questionTranslations.map { "${it.questionId}:${it.localeCode}" },
            request.optionTranslations.map { "${it.optionId}:${it.localeCode}" },
            request.resultRuleTranslations.map { "${it.resultRuleId}:${it.localeCode}" }
        )
        if (localeLists.any { values -> values.size != values.toSet().size } ||
            request.validityRules.map { "${it.ruleCode}:${it.ruleVersion}" }.let { it.size != it.toSet().size } ||
            request.normGovernance.map { it.normId }.let { it.size != it.toSet().size }
        ) {
            throw BizException("SCALE_PACKAGE_TRANSLATION_DUPLICATED", messages.get("error.scale_package_translation_duplicated"))
        }
        val locales = request.translations.map { it.localeCode } +
            request.dimensionTranslations.map { it.localeCode } + request.questionTranslations.map { it.localeCode } +
            request.optionTranslations.map { it.localeCode } + request.resultRuleTranslations.map { it.localeCode }
        if (locales.any { it !in supportedLocales }) {
            throw BizException("SCALE_PACKAGE_LOCALE_UNSUPPORTED", messages.get("error.scale_package_locale_unsupported"))
        }
        val reviewStatuses = request.translations.map { it.reviewStatus } + request.dimensionTranslations.map { it.reviewStatus } +
            request.questionTranslations.map { it.reviewStatus } + request.optionTranslations.map { it.reviewStatus } +
            request.resultRuleTranslations.map { it.reviewStatus } + request.validityRules.map { it.reviewStatus } +
            listOfNotNull(request.algorithmBinding?.reviewStatus)
        val blankRequiredText = request.translations.any { it.scaleName.isBlank() } ||
            request.dimensionTranslations.any { it.dimensionName.isBlank() } ||
            request.questionTranslations.any { it.questionTitle.isBlank() } ||
            request.optionTranslations.any { it.optionLabel.isBlank() } ||
            request.resultRuleTranslations.any { it.resultTitle.isBlank() } ||
            request.validityRules.any { it.ruleCode.isBlank() || it.ruleVersion.isBlank() } ||
            request.algorithmBinding?.let { it.algorithmCode.isBlank() || it.algorithmVersion.isBlank() } == true
        val unsupportedValue = reviewStatuses.any { it !in supportedReviewStatuses } ||
            request.normGovernance.any { it.reviewStatus !in normReviewStatuses } || blankRequiredText ||
            request.governance?.let {
                it.copyrightStatus !in copyrightStatuses || it.authorizationStatus !in authorizationStatuses ||
                    it.governanceStatus !in supportedReviewStatuses || (it.estimatedMinutes != null && it.estimatedMinutes <= 0)
            } == true ||
            request.qualityPolicy?.let {
                it.missingAnswerPolicy !in missingPolicies || it.invalidResultAction !in invalidActions ||
                    (it.minimumDurationSeconds != null && it.minimumDurationSeconds <= 0) ||
                    (it.maximumDurationSeconds != null && it.maximumDurationSeconds <= 0)
            } == true ||
            request.validityRules.any { it.ruleType !in validityTypes } ||
            request.algorithmBinding?.implementationType?.let { it !in implementationTypes } == true
        if (unsupportedValue) {
            throw BizException("SCALE_PACKAGE_VALUE_INVALID", messages.get("error.scale_package_value_invalid"))
        }
        val dimensionIds = scale.dimensions.map { it.id }.toSet()
        val questionIds = scale.questions.map { it.id }.toSet()
        val optionIds = scale.questions.flatMap { it.options }.map { it.id }.toSet()
        val resultRuleIds = scale.resultRules.map { it.id }.toSet()
        val normIds = scale.norms.map { it.id }.toSet()
        if (request.dimensionTranslations.any { it.dimensionId !in dimensionIds } ||
            request.questionTranslations.any { it.questionId !in questionIds } ||
            request.optionTranslations.any { it.optionId !in optionIds } ||
            request.resultRuleTranslations.any { it.resultRuleId !in resultRuleIds } ||
            request.normGovernance.any { it.normId !in normIds }
        ) {
            throw BizException("SCALE_PACKAGE_REFERENCE_INVALID", messages.get("error.scale_package_reference_invalid"))
        }
        val jsonValues = request.validityRules.map { it.configJson } + listOfNotNull(
            request.algorithmBinding?.inputSchemaJson,
            request.algorithmBinding?.outputSchemaJson
        )
        if (jsonValues.any { runCatching { objectMapper.readTree(it) }.isFailure }) {
            throw BizException("SCALE_PACKAGE_JSON_INVALID", messages.get("error.scale_package_json_invalid"))
        }
        request.governance?.let {
            if (it.authorizationValidFrom != null && it.authorizationValidTo != null && it.authorizationValidTo < it.authorizationValidFrom) {
                throw BizException("SCALE_PACKAGE_AUTHORIZATION_DATES_INVALID", messages.get("error.scale_package_authorization_dates_invalid"))
            }
        }
        request.qualityPolicy?.let {
            if (it.maxMissingRatio < java.math.BigDecimal.ZERO || it.maxMissingRatio > java.math.BigDecimal.ONE ||
                (it.minimumDurationSeconds != null && it.maximumDurationSeconds != null && it.maximumDurationSeconds < it.minimumDurationSeconds)
            ) {
                throw BizException("SCALE_PACKAGE_QUALITY_POLICY_INVALID", messages.get("error.scale_package_quality_policy_invalid"))
            }
        }
        if (request.normGovernance.any {
                (it.sampleSize != null && it.sampleSize <= 0) ||
                    (it.validFrom != null && it.validTo != null && it.validTo < it.validFrom)
            }
        ) {
            throw BizException("SCALE_PACKAGE_NORM_GOVERNANCE_INVALID", messages.get("error.scale_package_norm_governance_invalid"))
        }
    }

    private companion object {
        val supportedLocales = setOf("zh-CN", "ja-JP", "en")
        val supportedReviewStatuses = setOf("DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED")
        val copyrightStatuses = setOf("PENDING_REVIEW", "AUTHORIZED", "PUBLIC_DOMAIN", "RESTRICTED", "EXPIRED", "REJECTED")
        val authorizationStatuses = setOf("PENDING_REVIEW", "AUTHORIZED", "NOT_REQUIRED", "RESTRICTED", "EXPIRED", "REJECTED")
        val missingPolicies = setOf("REJECT", "ALLOW", "PRORATE", "PENDING_PROFESSIONAL_REVIEW")
        val invalidActions = setOf("INVALIDATE", "REQUIRE_REVIEW", "ALLOW_WITH_WARNING")
        val validityTypes = setOf("CONSISTENCY", "CONTRADICTION", "DURATION", "RESPONSE_PATTERN", "CUSTOM_EXTENSION")
        val implementationTypes = setOf("BUILTIN", "RESTRICTED_EXTENSION")
        val normReviewStatuses = setOf("PENDING_REVIEW", "APPROVED", "REJECTED", "EXPIRED")
    }
}
