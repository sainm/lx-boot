package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.PreviewScalePackageImportResponse
import org.sainm.psy.scale.api.ScaleImportIssueResponse
import org.sainm.psy.scale.api.ScalePackageExportDocument
import org.sainm.psy.scale.domain.ScaleImportIssue
import org.sainm.psy.scale.domain.ScaleImportSummary
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ScalePackageImportPreviewService(
    private val scaleRepository: ScaleRepository,
    private val scaleImportRepository: ScaleImportRepository,
    private val fingerprintService: ScaleContentFingerprintService,
    private val integrityService: ScalePackageExportIntegrityService,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val objectMapper: ObjectMapper,
    private val securityAuditService: SecurityAuditService,
    private val tenantAccessPolicy: TenantAccessPolicy,
    private val sourcePackageImportPreviewService: ScaleSourcePackageImportPreviewService? = null
) {
    fun preview(file: MultipartFile): PreviewScalePackageImportResponse {
        val fileName = file.originalFilename?.takeIf(String::isNotBlank) ?: "scale-package.json"
        if (file.isEmpty || !fileName.lowercase().endsWith(".json") || file.size > MAX_PACKAGE_BYTES) {
            throw BizException("SCALE_PACKAGE_IMPORT_INVALID_FILE", messages.get("scale.package_import.invalid_file"))
        }
        val bytes = file.bytes
        val sourceFormat = runCatching { objectMapper.readTree(bytes).path("format").asText() }.getOrNull()
        if (sourceFormat == ScaleSourcePackageValidation.FORMAT) {
            return requireNotNull(sourcePackageImportPreviewService).preview(file, bytes)
        }
        val currentUser = currentUserFacade.requireCurrentUser()
        val tenantId = tenantAccessPolicy.requireTenantId()
        val importId = scaleImportRepository.createJob(fileName, IMPORT_MODE, true, currentUser.userId, tenantId)
        val (document, root) = runCatching {
            // Parse the typed document directly from the original bytes. Going
            // through JsonNode first normalizes decimal lexical scale (for
            // example 1.0000 -> 1), which changes the schema-v1 payload hash.
            objectMapper.readValue(bytes, ScalePackageExportDocument::class.java) to objectMapper.readTree(bytes)
        }
            .getOrElse {
                val response = invalidJson(importId, fileName)
                persist(importId, response, null)
                return response.also(::auditPreview)
            }
        val errors = mutableListOf<ScaleImportIssueResponse>()
        val warnings = mutableListOf<ScaleImportIssueResponse>()
        fun error(path: String, code: String, messageKey: String) {
            errors += issue("ERROR", path, code, messageKey)
        }
        fun warning(path: String, code: String, messageKey: String) {
            warnings += issue("WARNING", path, code, messageKey)
        }

        if (!root.path("format").isTextual || document.format != FORMAT) error("format", "PACKAGE_FORMAT_UNSUPPORTED", "scale.package_import.format_unsupported")
        if (!root.path("schemaVersion").isIntegralNumber || document.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) error("schemaVersion", "PACKAGE_SCHEMA_UNSUPPORTED", "scale.package_import.schema_unsupported")
        if (document.scalePackage.scaleId != document.scale.id) error("scalePackage.scaleId", "PACKAGE_SCALE_ID_MISMATCH", "scale.package_import.scale_id_mismatch")
        if (scaleRepository.existsByScaleCode(document.scale.scaleCode, tenantId)) {
            error("scale.scaleCode", "SCALE_CODE_CONFLICT", "scale.package_import.scale_code_conflict")
        }
        if (!document.scaleContentHash.matches(SHA_256)) error("scaleContentHash", "PACKAGE_SCALE_HASH_INVALID", "scale.package_import.scale_hash_invalid")

        val dimensions = document.scale.dimensions.map { it.id }.toSet()
        val questions = document.scale.questions.map { it.id }.toSet()
        val options = document.scale.questions.flatMap { it.options }.map { it.id }.toSet()
        val resultRules = document.scale.resultRules.map { it.id }.toSet()
        val highRiskRules = document.scale.highRiskRules.map { it.id }.toSet()
        val norms = document.scale.norms.map { it.id }.toSet()
        document.scalePackage.dimensionTranslations.filter { it.dimensionId !in dimensions }
            .forEach { error("scalePackage.dimensionTranslations", "PACKAGE_DIMENSION_REFERENCE_INVALID", "scale.package_import.reference_invalid") }
        document.scalePackage.questionTranslations.filter { it.questionId !in questions }
            .forEach { error("scalePackage.questionTranslations", "PACKAGE_QUESTION_REFERENCE_INVALID", "scale.package_import.reference_invalid") }
        document.scalePackage.optionTranslations.filter { it.optionId !in options }
            .forEach { error("scalePackage.optionTranslations", "PACKAGE_OPTION_REFERENCE_INVALID", "scale.package_import.reference_invalid") }
        document.scalePackage.resultRuleTranslations.filter { it.resultRuleId !in resultRules }
            .forEach { error("scalePackage.resultRuleTranslations", "PACKAGE_RESULT_RULE_REFERENCE_INVALID", "scale.package_import.reference_invalid") }
        document.scalePackage.highRiskRuleTranslations.filter { it.highRiskRuleId !in highRiskRules }
            .forEach { error("scalePackage.highRiskRuleTranslations", "PACKAGE_HIGH_RISK_RULE_REFERENCE_INVALID", "scale.package_import.reference_invalid") }
        document.scalePackage.normGovernance.filter { it.normId !in norms }
            .forEach { error("scalePackage.normGovernance", "PACKAGE_NORM_REFERENCE_INVALID", "scale.package_import.reference_invalid") }

        val latestCases = document.goldenCases.map { it.goldenCase }.groupBy { it.caseCode }.values.map { revisions -> revisions.maxBy { it.revisionNo } }
        if (document.goldenCases.any { history -> history.goldenCase.scaleId != document.scale.id || history.runs.any { it.goldenCaseId != history.goldenCase.id } }) {
            error("goldenCases", "PACKAGE_GOLDEN_CASE_REFERENCE_INVALID", "scale.package_import.reference_invalid")
        }
        if (fingerprintService.calculateReleaseFingerprint(document.scaleContentHash, latestCases) != document.releaseFingerprint) {
            error("releaseFingerprint", "PACKAGE_RELEASE_FINGERPRINT_MISMATCH", "scale.package_import.release_fingerprint_mismatch")
        }
        val actualPayloadHash = integrityService.calculate(
            document.scaleContentHash, document.releaseFingerprint, document.scale, document.scalePackage,
            document.goldenCases, document.publicationReviews
        )
        if (actualPayloadHash != document.payloadHash) {
            error("payloadHash", "PACKAGE_PAYLOAD_HASH_MISMATCH", "scale.package_import.payload_hash_mismatch")
        }

        REQUIRED_LOCALES.filterNot { locale -> document.scalePackage.translations.any { it.localeCode == locale } }
            .forEach { warning("scalePackage.translations.$it", "PACKAGE_TRANSLATION_MISSING", "scale.package_import.translation_missing") }
        if (document.scalePackage.governance == null || document.scalePackage.governance.sourceTitle.isNullOrBlank() ||
            document.scalePackage.governance.authorizationStatus != "AUTHORIZED"
        ) warning("scalePackage.governance", "PACKAGE_AUTHORIZATION_REVIEW_REQUIRED", "scale.package_import.authorization_review_required")
        if (document.publicationReviews.isNotEmpty() || document.scalePackage.governance?.governanceStatus == "APPROVED" ||
            document.scalePackage.translations.any { it.reviewStatus == "APPROVED" }
        ) warning("publicationReviews", "PACKAGE_EXTERNAL_APPROVAL_NOT_TRANSFERRED", "scale.package_import.external_approval_not_transferred")

        val response = response(importId, fileName, document, errors, warnings)
        persist(importId, response, document)
        return response.also(::auditPreview)
    }

    private fun invalidJson(importId: Long, fileName: String): PreviewScalePackageImportResponse {
        val error = issue("ERROR", "$", "PACKAGE_JSON_INVALID", "scale.package_import.invalid_json")
        return PreviewScalePackageImportResponse(importId = importId, fileName = fileName, errorCount = 1, warningCount = 0, errors = listOf(error), warnings = emptyList())
    }

    private fun response(importId: Long, fileName: String, document: ScalePackageExportDocument, errors: List<ScaleImportIssueResponse>, warnings: List<ScaleImportIssueResponse>) =
        PreviewScalePackageImportResponse(
            importId = importId, fileName = fileName, format = document.format, schemaVersion = document.schemaVersion,
            sourceScaleId = document.scale.id, scaleCode = document.scale.scaleCode, versionNo = document.scale.versionNo,
            dimensionCount = document.scale.dimensions.size, questionCount = document.scale.questions.size,
            optionCount = document.scale.questions.sumOf { it.options.size }, resultRuleCount = document.scale.resultRules.size,
            goldenCaseRevisionCount = document.goldenCases.size, publicationReviewCount = document.publicationReviews.size,
            readyForControlledImport = errors.isEmpty(), confirmationSupported = errors.isEmpty(),
            errorCount = errors.size, warningCount = warnings.size, errors = errors, warnings = warnings
        )

    private fun persist(importId: Long, response: PreviewScalePackageImportResponse, document: ScalePackageExportDocument?) {
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
            previewJson = document?.let(objectMapper::writeValueAsString),
            errorCount = response.errorCount,
            warningCount = response.warningCount
        )
        scaleImportRepository.replaceIssues(importId, (response.errors + response.warnings).map {
            ScaleImportIssue(it.severity, it.sheetName, it.rowNo, it.columnName, it.errorCode, it.message)
        })
    }

    private fun issue(severity: String, path: String, code: String, messageKey: String) = ScaleImportIssueResponse(
        severity, "scale-package", null, path, code, messages.get(messageKey)
    )

    private fun auditPreview(response: PreviewScalePackageImportResponse) {
        securityAuditService.runCatchingAudit(
            "PSY_SCALE_PACKAGE_IMPORT_PREVIEWED",
            mapOf("fileName" to response.fileName, "format" to response.format, "schemaVersion" to response.schemaVersion,
                "sourceScaleId" to response.sourceScaleId, "scaleCode" to response.scaleCode,
                "errorCount" to response.errorCount, "warningCount" to response.warningCount,
                "readyForControlledImport" to response.readyForControlledImport)
        )
    }

    private companion object {
        const val FORMAT = "PSY_SCALE_PACKAGE"
        val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2)
        const val IMPORT_MODE = "PACKAGE_CREATE_ONLY"
        const val MAX_PACKAGE_BYTES = 10L * 1024 * 1024
        val REQUIRED_LOCALES = setOf("zh-CN", "ja-JP", "en")
        val SHA_256 = Regex("[a-f0-9]{64}")
    }
}
