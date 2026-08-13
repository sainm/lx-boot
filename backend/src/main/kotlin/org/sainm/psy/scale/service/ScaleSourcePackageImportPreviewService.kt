package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.PreviewScalePackageImportResponse
import org.sainm.psy.scale.api.ScaleImportIssueResponse
import org.sainm.psy.scale.api.ScaleSourcePackageDocument
import org.sainm.psy.scale.domain.ScaleImportIssue
import org.sainm.psy.scale.domain.ScaleImportSummary
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ScaleSourcePackageImportPreviewService(
    private val scaleRepository: ScaleRepository,
    private val scaleImportRepository: ScaleImportRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val objectMapper: ObjectMapper,
    private val securityAuditService: SecurityAuditService,
    private val tenantAccessPolicy: TenantAccessPolicy
) {
    fun preview(file: MultipartFile, bytes: ByteArray = file.bytes): PreviewScalePackageImportResponse {
        val fileName = file.originalFilename?.takeIf(String::isNotBlank) ?: "scale-source-package.json"
        val user = currentUserFacade.requireCurrentUser()
        val tenantId = tenantAccessPolicy.requireTenantId()
        val importId = scaleImportRepository.createJob(
            fileName,
            ScaleSourcePackageValidation.IMPORT_MODE,
            true,
            user.userId,
            tenantId
        )
        val document = runCatching {
            objectMapper.readValue(bytes, ScaleSourcePackageDocument::class.java)
        }.getOrElse {
            val response = response(
                importId,
                fileName,
                null,
                listOf(issue("ERROR", "$", "PACKAGE_JSON_INVALID", "scale.package_import.invalid_json")),
                emptyList()
            )
            persist(importId, response, null, bytes)
            return response.also(::auditPreview)
        }

        val errors = ScaleSourcePackageValidation.validate(document).map { problem ->
            issue("ERROR", problem.path, problem.code, messageKey(problem.code))
        }.toMutableList()
        if (scaleRepository.existsByScaleCode(document.scale.scaleCode, tenantId)) {
            errors += issue("ERROR", "scale.scaleCode", "SCALE_CODE_CONFLICT", "scale.package_import.scale_code_conflict")
        }
        val warnings = buildList {
            if (document.governance.authorizationStatus !in setOf("AUTHORIZED", "NOT_REQUIRED") ||
                document.governance.copyrightStatus !in setOf("AUTHORIZED", "PUBLIC_DOMAIN")
            ) {
                add(issue("WARNING", "governance", "PACKAGE_AUTHORIZATION_REVIEW_REQUIRED", "scale.package_import.authorization_review_required"))
            }
            if (document.translations.values.any { it.reviewStatus != "APPROVED" } ||
                document.dimensions.any { dimension -> dimension.translations.values.any { it.reviewStatus != "APPROVED" } } ||
                document.questions.any { question ->
                    question.translations.values.any { it.reviewStatus != "APPROVED" } ||
                        question.options.any { option -> option.translations.keys.any { optionLocale -> optionLocale !in ScaleSourcePackageValidation.REQUIRED_LOCALES } }
                } ||
                document.resultRules.any { rule -> rule.translations.values.any { it.reviewStatus != "APPROVED" } } ||
                document.highRiskRules.any { rule -> rule.translations.values.any { it.reviewStatus != "APPROVED" } }
            ) {
                add(issue("WARNING", "translations", "PACKAGE_EXTERNAL_APPROVAL_NOT_TRANSFERRED", "scale.package_import.external_approval_not_transferred"))
            }
            if (document.norms.status != "APPROVED") {
                add(issue("WARNING", "norms", "SOURCE_PACKAGE_NORM_REVIEW_REQUIRED", "scale.source_package.norm_review_required"))
            }
            document.publicationBlockers.forEach { blocker ->
                add(issue("WARNING", "publicationBlockers", "SOURCE_PACKAGE_BLOCKER:$blocker", "scale.source_package.external_blocker", blocker))
            }
        }
        val response = response(importId, fileName, document, errors, warnings)
        persist(importId, response, document, bytes)
        return response.also(::auditPreview)
    }

    private fun response(
        importId: Long,
        fileName: String,
        document: ScaleSourcePackageDocument?,
        errors: List<ScaleImportIssueResponse>,
        warnings: List<ScaleImportIssueResponse>
    ) = PreviewScalePackageImportResponse(
        importId = importId,
        fileName = fileName,
        format = document?.format ?: ScaleSourcePackageValidation.FORMAT,
        schemaVersion = document?.schemaVersion,
        sourceScaleId = null,
        scaleCode = document?.scale?.scaleCode,
        versionNo = document?.scale?.versionNo,
        dimensionCount = document?.dimensions?.size ?: 0,
        questionCount = document?.questions?.size ?: 0,
        optionCount = document?.questions?.sumOf { question -> question.options.size } ?: 0,
        resultRuleCount = document?.resultRules?.size ?: 0,
        goldenCaseRevisionCount = document?.goldenCases?.size ?: 0,
        publicationReviewCount = 0,
        readyForControlledImport = errors.isEmpty(),
        confirmationSupported = errors.isEmpty(),
        errorCount = errors.size,
        warningCount = warnings.size,
        errors = errors,
        warnings = warnings
    )

    private fun persist(importId: Long, response: PreviewScalePackageImportResponse, document: ScaleSourcePackageDocument?, bytes: ByteArray) {
        val summary = ScaleImportSummary(
            scaleCode = response.scaleCode,
            scaleName = document?.scale?.scaleName,
            dimensionCount = response.dimensionCount,
            questionCount = response.questionCount,
            optionCount = response.optionCount,
            resultRuleCount = response.resultRuleCount
        )
        scaleImportRepository.updateParsedResult(
            jobId = importId,
            status = if (response.errorCount == 0) "PARSED" else "PARSE_FAILED",
            summaryJson = objectMapper.writeValueAsString(summary),
            previewJson = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull(),
            errorCount = response.errorCount,
            warningCount = response.warningCount
        )
        scaleImportRepository.replaceIssues(importId, (response.errors + response.warnings).map {
            ScaleImportIssue(it.severity, it.sheetName, it.rowNo, it.columnName, it.errorCode, it.message)
        })
    }

    private fun issue(severity: String, path: String, code: String, key: String, vararg args: Any?) =
        ScaleImportIssueResponse(severity, "scale-source-package", null, path, code, messages.get(key, *args))

    private fun messageKey(code: String): String = when (code) {
        "PACKAGE_FORMAT_UNSUPPORTED" -> "scale.package_import.format_unsupported"
        "PACKAGE_SCHEMA_UNSUPPORTED" -> "scale.package_import.schema_unsupported"
        "PACKAGE_TRANSLATION_MISSING" -> "scale.package_import.translation_missing"
        "SOURCE_PACKAGE_GOVERNANCE_INVALID", "SOURCE_PACKAGE_TRANSLATION_INVALID" -> "scale.source_package.metadata_invalid"
        "SOURCE_PACKAGE_REFERENCE_MISSING" -> "scale.source_package.reference_missing"
        "SOURCE_PACKAGE_ALGORITHM_UNSUPPORTED" -> "scale.source_package.algorithm_unsupported"
        "SOURCE_PACKAGE_SCORE_METHOD_UNSUPPORTED" -> "scale.source_package.score_method_unsupported"
        "SOURCE_PACKAGE_GOLDEN_CASE_MISSING", "SOURCE_PACKAGE_GOLDEN_CASE_INVALID" -> "scale.source_package.golden_case_invalid"
        "SOURCE_PACKAGE_SCALE_INVALID" -> "scale.source_package.scale_invalid"
        "SOURCE_PACKAGE_RESPONSE_SCALE_INVALID" -> "scale.source_package.response_scale_invalid"
        "SOURCE_PACKAGE_QUESTION_SET_INVALID" -> "scale.source_package.question_set_invalid"
        "SOURCE_PACKAGE_DIMENSION_INVALID" -> "scale.source_package.dimension_invalid"
        "SOURCE_PACKAGE_OPTION_INVALID" -> "scale.source_package.option_invalid"
        "SOURCE_PACKAGE_RESULT_RULE_INVALID", "SOURCE_PACKAGE_RESULT_RULE_OVERLAP" -> "scale.source_package.result_rule_invalid"
        "SOURCE_PACKAGE_REFERENCE_INVALID" -> "scale.source_package.reference_invalid"
        "SOURCE_PACKAGE_QUALITY_POLICY_INVALID" -> "scale.source_package.quality_policy_invalid"
        "SOURCE_PACKAGE_NORM_INVALID" -> "scale.source_package.norm_invalid"
        "SOURCE_PACKAGE_INDICES_UNSUPPORTED" -> "scale.source_package.indices_unsupported"
        "SOURCE_PACKAGE_RECODE_UNSUPPORTED" -> "scale.source_package.recode_unsupported"
        "SOURCE_PACKAGE_RECODE_INVALID" -> "scale.source_package.recode_invalid"
        "SOURCE_PACKAGE_QUESTION_TYPE_UNSUPPORTED" -> "scale.source_package.question_type_unsupported"
        "SOURCE_PACKAGE_SKIP_RULE_INVALID" -> "scale.source_package.skip_rule_invalid"
        "SOURCE_PACKAGE_ASSESSMENT_MODE_INVALID" -> "scale.source_package.assessment_mode_invalid"
        "SOURCE_PACKAGE_ASSESSMENT_MODE_UNSUPPORTED" -> "scale.source_package.assessment_mode_unsupported"
        else -> "scale.package_import.reference_invalid"
    }

    private fun auditPreview(response: PreviewScalePackageImportResponse) {
        securityAuditService.runCatchingAudit(
            "PSY_SCALE_SOURCE_PACKAGE_IMPORT_PREVIEWED",
            mapOf(
                "fileName" to response.fileName,
                "format" to response.format,
                "schemaVersion" to response.schemaVersion,
                "scaleCode" to response.scaleCode,
                "errorCount" to response.errorCount,
                "warningCount" to response.warningCount,
                "readyForControlledImport" to response.readyForControlledImport
            )
        )
    }
}
