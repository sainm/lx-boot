package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.exception.NotFoundBizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.ConfirmScalePackageImportResponse
import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.ScalePackageExportDocument
import org.sainm.psy.scale.api.UpdateScalePackageRequest
import org.sainm.psy.scale.domain.ScaleDimensionDraft
import org.sainm.psy.scale.domain.ScaleHighRiskRuleDraft
import org.sainm.psy.scale.domain.ScaleNormDraft
import org.sainm.psy.scale.domain.ScalePackageDimensionTranslation
import org.sainm.psy.scale.domain.ScalePackageHighRiskRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageNormGovernance
import org.sainm.psy.scale.domain.ScalePackageOptionTranslation
import org.sainm.psy.scale.domain.ScalePackageQuestionTranslation
import org.sainm.psy.scale.domain.ScalePackageResultRuleTranslation
import org.sainm.psy.scale.domain.ScaleQuestionDraft
import org.sainm.psy.scale.domain.ScaleQuestionOptionDraft
import org.sainm.psy.scale.domain.ScaleResultRuleDraft
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScalePublicationRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.sainm.psy.visualization.domain.ScaleVisualizationConfigDraft
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class ScalePackageImportService(
    private val scaleRepository: ScaleRepository,
    private val packageRepository: ScalePackageRepository,
    private val publicationRepository: ScalePublicationRepository,
    private val importRepository: ScaleImportRepository,
    private val visualizationService: VisualizationService,
    private val fingerprintService: ScaleContentFingerprintService,
    private val integrityService: ScalePackageExportIntegrityService,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
    private val securityAuditService: SecurityAuditService,
    private val tenantAccessPolicy: TenantAccessPolicy
) {
    fun confirm(importId: Long): ConfirmScalePackageImportResponse {
        val user = currentUserFacade.requireCurrentUser()
        val tenantId = tenantAccessPolicy.requireTenantId()
        val job = importRepository.findJobById(importId, tenantId)
            ?: throw NotFoundBizException("SCALE_IMPORT_JOB_NOT_FOUND", messages.get("scale.import.job_not_found"))
        if (job.importMode != IMPORT_MODE || job.status != "PARSED" || job.errorCount > 0 || job.previewJson.isNullOrBlank()) {
            throw BizException("SCALE_PACKAGE_IMPORT_NOT_CONFIRMABLE", messages.get("scale.package_import.not_confirmable"))
        }
        val document = runCatching { objectMapper.readValue(job.previewJson, ScalePackageExportDocument::class.java) }
            .getOrElse { throw BizException("SCALE_PACKAGE_IMPORT_NOT_CONFIRMABLE", messages.get("scale.package_import.not_confirmable")) }
        validateStoredDocument(document)
        var claimed = false
        return try {
            transactionTemplate.execute {
                if (!importRepository.claimForConfirmation(importId, tenantId, IMPORT_MODE)) {
                    throw BizException("SCALE_PACKAGE_IMPORT_NOT_CONFIRMABLE", messages.get("scale.package_import.not_confirmable"))
                }
                claimed = true
                if (scaleRepository.existsByScaleCode(document.scale.scaleCode, tenantId)) {
                    throw BizException("SCALE_CODE_CONFLICT", messages.get("scale.package_import.scale_code_conflict"))
                }
                val scaleId = createScale(document, user.userId)
                val importedCases = importGoldenCases(document, scaleId, user.userId)
                val response = ConfirmScalePackageImportResponse(
                    importId = importId, status = "SUCCESS", scaleId = scaleId,
                    createdDimensionCount = document.scale.dimensions.size,
                    createdQuestionCount = document.scale.questions.size,
                    createdOptionCount = document.scale.questions.sumOf { it.options.size },
                    createdResultRuleCount = document.scale.resultRules.size,
                    importedGoldenCaseRevisionCount = importedCases,
                    discardedGoldenCaseRunCount = document.goldenCases.sumOf { it.runs.size },
                    discardedPublicationReviewCount = document.publicationReviews.size
                )
                importRepository.markSuccess(importId, scaleId)
                securityAuditService.recordScalePackageImported(
                    importId, scaleId, document.payloadHash, importedCases,
                    response.discardedGoldenCaseRunCount, response.discardedPublicationReviewCount
                )
                response
            } ?: error("scale package import transaction did not return a result")
        } catch (error: Exception) {
            if (claimed) importRepository.markFailed(importId)
            throw error
        }
    }

    private fun createScale(document: ScalePackageExportDocument, userId: Long): Long {
        val source = document.scale
        val scaleId = scaleRepository.create(
            CreateScaleRequest(source.scaleCode, source.scaleName, source.description, source.applicableTarget, source.versionNo,
                source.scoreMethod, source.scoreCoefficient, source.anonymousSupported, source.reportTemplate), userId
        )
        scaleRepository.updateScaleAdvancedConfig(scaleId, source.normStrategy, source.normDefaultGroup, source.highRiskWarningEnabled)
        scaleRepository.createDimensions(scaleId, source.dimensions.map { ScaleDimensionDraft(it.dimensionCode, it.dimensionName, it.description, it.sortNo) })
        val dimensionIds = scaleRepository.findDimensionCodeIdMapByScaleId(scaleId)
        scaleRepository.createQuestions(scaleId, source.questions.map { question ->
            ScaleQuestionDraft(
                question.questionNo, question.questionTitle, question.questionType,
                question.dimensionId?.let { sourceId -> source.dimensions.first { it.id == sourceId }.dimensionCode }?.let(dimensionIds::get),
                question.requiredFlag, question.reverseScoreFlag, question.weightValue, question.optionSelectionLimit,
                question.sliderMin, question.sliderMax, question.sliderStep, question.textInputEnabled,
                question.textInputPlaceholder, question.matrixGroupCode, question.rowCode, question.columnCode, question.sortNo,
                question.options.map { ScaleQuestionOptionDraft(it.optionCode, it.optionLabel, it.scoreValue, it.exclusiveFlag, it.optionGroupCode, it.sortNo) }
            )
        })
        val targetQuestionIds = scaleRepository.findQuestionNoIdMapByScaleId(scaleId)
        val targetOptionIds = scaleRepository.findOptionIdMapByScaleId(scaleId)
        val createdRules = scaleRepository.createResultRules(scaleId, source.resultRules.map { rule ->
            ScaleResultRuleDraft(
                rule.dimensionId?.let { sourceId -> source.dimensions.first { it.id == sourceId }.dimensionCode }?.let(dimensionIds::get),
                rule.riskLevel, rule.scoreMin, rule.scoreMax, rule.scoreSource, rule.normCode,
                rule.resultTitle, rule.resultDescription, rule.suggestionText
            )
        })
        val createdNorms = scaleRepository.createNorms(scaleId, source.norms.map { norm ->
            ScaleNormDraft(
                norm.normCode, norm.normName,
                norm.dimensionId?.let { sourceId -> source.dimensions.first { it.id == sourceId }.dimensionCode }?.let(dimensionIds::get),
                norm.applicableTarget, norm.ageMin, norm.ageMax, norm.gender, norm.orgType,
                norm.meanScore, norm.stdDeviation, norm.tScoreMean, norm.tScoreStdDeviation, norm.sortNo
            )
        })
        val createdHighRiskRules = scaleRepository.createHighRiskRules(scaleId, source.highRiskRules.map { rule ->
            ScaleHighRiskRuleDraft(
                rule.ruleCode, targetQuestionIds.getValue(rule.questionNo),
                rule.optionCode?.let { targetOptionIds[rule.questionNo to it] }, rule.scoreThreshold, rule.warningLevel,
                rule.resultTitle, rule.resultDescription, rule.suggestionText, rule.sortNo
            )
        })
        visualizationService.replaceConfigs(scaleId, source.visualizationConfigs.map {
            ScaleVisualizationConfigDraft(it.chartType, it.chartTitle, it.viewScope, it.dataSource, it.configJson, it.enabled, it.sortNo)
        })
        val sourceQuestionNo = source.questions.associate { it.id to it.questionNo }
        val sourceOptionKey = source.questions.flatMap { question -> question.options.map { it.id to (question.questionNo to it.optionCode) } }.toMap()
        val request = document.scalePackage.let { pkg ->
            UpdateScalePackageRequest(
                governance = pkg.governance?.copy(copyrightStatus = "PENDING_REVIEW", authorizationStatus = "PENDING_REVIEW", governanceStatus = "DRAFT"),
                translations = pkg.translations.map { it.copy(reviewStatus = "DRAFT") },
                dimensionTranslations = pkg.dimensionTranslations.map { t -> ScalePackageDimensionTranslation(dimensionIds.getValue(source.dimensions.first { it.id == t.dimensionId }.dimensionCode), t.localeCode, t.dimensionName, t.description, "DRAFT") },
                questionTranslations = pkg.questionTranslations.map { t -> ScalePackageQuestionTranslation(targetQuestionIds.getValue(sourceQuestionNo.getValue(t.questionId)), t.localeCode, t.questionTitle, t.textInputPlaceholder, "DRAFT") },
                optionTranslations = pkg.optionTranslations.map { t -> ScalePackageOptionTranslation(targetOptionIds.getValue(sourceOptionKey.getValue(t.optionId)), t.localeCode, t.optionLabel, "DRAFT") },
                resultRuleTranslations = pkg.resultRuleTranslations.map { t -> ScalePackageResultRuleTranslation(createdRules.createdIds[source.resultRules.indexOfFirst { it.id == t.resultRuleId }], t.localeCode, t.resultTitle, t.resultDescription, t.suggestionText, "DRAFT") },
                highRiskRuleTranslations = pkg.highRiskRuleTranslations.map { t ->
                    ScalePackageHighRiskRuleTranslation(
                        createdHighRiskRules.createdIds[source.highRiskRules.indexOfFirst { it.id == t.highRiskRuleId }],
                        t.localeCode, t.resultTitle, t.resultDescription, t.suggestionText, "DRAFT"
                    )
                },
                qualityPolicy = pkg.qualityPolicy,
                validityRules = pkg.validityRules.map { it.copy(reviewStatus = "DRAFT") },
                algorithmBinding = pkg.algorithmBinding?.copy(reviewStatus = "DRAFT"),
                normGovernance = pkg.normGovernance.map { n -> ScalePackageNormGovernance(createdNorms.createdIds[source.norms.indexOfFirst { it.id == n.normId }], n.sourceReference, n.normVersion, n.sampleSize, n.regionCode, n.languageCode, n.validFrom, n.validTo, "PENDING_REVIEW") }
            )
        }
        packageRepository.replace(scaleId, request, userId)
        return scaleId
    }

    private fun importGoldenCases(document: ScalePackageExportDocument, scaleId: Long, userId: Long): Int {
        if (document.goldenCases.isEmpty()) return 0
        val target = requireNotNull(scaleRepository.findDetailById(scaleId))
        val scaleHash = fingerprintService.calculate(target)
        document.goldenCases.map { it.goldenCase }.sortedWith(compareBy({ it.caseCode }, { it.revisionNo })).forEach { sourceCase ->
            val caseHash = fingerprintService.sha256(
                listOf(sourceCase.caseCode.trim().uppercase(), sourceCase.caseType.trim().uppercase(), sourceCase.sourceReference.trim(), sourceCase.inputJson, sourceCase.expectedJson).joinToString("|")
            )
            publicationRepository.saveCaseRevision(
                scaleId, sourceCase.caseCode.trim().uppercase(), sourceCase.caseType.trim().uppercase(),
                sourceCase.sourceReference.trim(), scaleHash, caseHash, sourceCase.inputJson, sourceCase.expectedJson, userId
            )
        }
        return document.goldenCases.size
    }

    private fun validateStoredDocument(document: ScalePackageExportDocument) {
        val latestCases = document.goldenCases.map { it.goldenCase }.groupBy { it.caseCode }.values.map { it.maxBy { case -> case.revisionNo } }
        if (fingerprintService.calculateReleaseFingerprint(document.scaleContentHash, latestCases) != document.releaseFingerprint ||
            integrityService.calculate(document.scaleContentHash, document.releaseFingerprint, document.scale, document.scalePackage,
                document.goldenCases, document.publicationReviews) != document.payloadHash
        ) throw BizException("SCALE_PACKAGE_IMPORT_INTEGRITY_FAILED", messages.get("scale.package_import.integrity_failed"))
    }

    private companion object {
        const val IMPORT_MODE = "PACKAGE_CREATE_ONLY"
    }
}
