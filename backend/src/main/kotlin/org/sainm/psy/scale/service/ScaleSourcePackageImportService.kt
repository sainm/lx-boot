package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.scale.api.ConfirmScalePackageImportResponse
import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.ScaleSourcePackageDocument
import org.sainm.psy.scale.domain.ScaleDimensionDraft
import org.sainm.psy.scale.domain.ScaleHighRiskRuleDraft
import org.sainm.psy.scale.domain.ScaleImportJobRecord
import org.sainm.psy.scale.domain.ScaleNormDraft
import org.sainm.psy.scale.domain.ScalePackageDimensionTranslation
import org.sainm.psy.scale.domain.ScalePackageGovernance
import org.sainm.psy.scale.domain.ScalePackageHighRiskRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageNormGovernance
import org.sainm.psy.scale.domain.ScalePackageOptionTranslation
import org.sainm.psy.scale.domain.ScalePackageQuestionTranslation
import org.sainm.psy.scale.domain.ScalePackageResultRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageQualityPolicy
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePackageTranslation
import org.sainm.psy.scale.domain.ScalePackageValidityRule
import org.sainm.psy.scale.domain.ScaleQuestionDraft
import org.sainm.psy.scale.domain.ScaleQuestionOptionDraft
import org.sainm.psy.scale.domain.ScaleResultRuleDraft
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScalePublicationRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

/** Materializes a tenant-neutral source package as an immutable-input DRAFT. */
@Service
class ScaleSourcePackageImportService(
    private val scaleRepository: ScaleRepository,
    private val packageRepository: ScalePackageRepository,
    private val publicationRepository: ScalePublicationRepository,
    private val importRepository: ScaleImportRepository,
    private val fingerprintService: ScaleContentFingerprintService,
    private val currentUserMessages: LocalizedMessages,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
    private val securityAuditService: SecurityAuditService
) {
    fun confirm(job: ScaleImportJobRecord, userId: Long, tenantId: Long): ConfirmScalePackageImportResponse {
        if (job.importMode != ScaleSourcePackageValidation.IMPORT_MODE ||
            job.status != "PARSED" || job.errorCount > 0 || job.previewJson.isNullOrBlank()
        ) {
            throw BizException("SCALE_PACKAGE_IMPORT_NOT_CONFIRMABLE", currentUserMessages.get("scale.package_import.not_confirmable"))
        }
        val document = runCatching {
            objectMapper.readValue(job.previewJson, ScaleSourcePackageDocument::class.java)
        }.getOrElse {
            throw BizException("SCALE_PACKAGE_IMPORT_NOT_CONFIRMABLE", currentUserMessages.get("scale.package_import.not_confirmable"))
        }
        val problems = ScaleSourcePackageValidation.validate(document)
        if (problems.isNotEmpty()) {
            throw BizException("SCALE_PACKAGE_IMPORT_NOT_CONFIRMABLE", currentUserMessages.get("scale.package_import.not_confirmable"))
        }
        var claimed = false
        return try {
            transactionTemplate.execute {
                if (!importRepository.claimForConfirmation(job.id, tenantId, ScaleSourcePackageValidation.IMPORT_MODE)) {
                    throw BizException("SCALE_PACKAGE_IMPORT_NOT_CONFIRMABLE", currentUserMessages.get("scale.package_import.not_confirmable"))
                }
                claimed = true
                if (scaleRepository.existsByScaleCode(document.scale.scaleCode, tenantId)) {
                    throw BizException("SCALE_CODE_CONFLICT", currentUserMessages.get("scale.package_import.scale_code_conflict"))
                }
                val scaleId = createScale(document, userId)
                val importedCases = importGoldenCases(document, scaleId, userId)
                val response = ConfirmScalePackageImportResponse(
                    importId = job.id,
                    status = "SUCCESS",
                    scaleId = scaleId,
                    createdDimensionCount = document.dimensions.size,
                    createdQuestionCount = document.questions.size,
                    createdOptionCount = document.questions.sumOf { it.options.size },
                    createdResultRuleCount = document.resultRules.size,
                    importedGoldenCaseRevisionCount = importedCases,
                    discardedGoldenCaseRunCount = 0,
                    discardedPublicationReviewCount = 0
                )
                importRepository.markSuccess(job.id, scaleId)
                securityAuditService.runCatchingAudit(
                    "PSY_SCALE_SOURCE_PACKAGE_IMPORTED",
                    mapOf(
                        "importId" to job.id,
                        "scaleId" to scaleId,
                        "scaleCode" to document.scale.scaleCode,
                        "goldenCaseCount" to importedCases,
                        "governanceStatus" to "DRAFT",
                        "authorizationStatus" to "PENDING_REVIEW"
                    )
                )
                response
            } ?: error("source package import transaction did not return a result")
        } catch (error: Exception) {
            if (claimed) importRepository.markFailed(job.id)
            throw error
        }
    }

    private fun createScale(document: ScaleSourcePackageDocument, userId: Long): Long {
        val source = document.scale
        val scaleId = scaleRepository.create(
            CreateScaleRequest(
                scaleCode = source.scaleCode,
                scaleName = source.scaleName,
                description = document.governance.nonDiagnosticStatement,
                applicableTarget = source.applicableTarget,
                versionNo = source.versionNo,
                scoreMethod = source.scoreMethod,
                scoreCoefficient = source.scoreCoefficient,
                anonymousSupported = false,
                reportTemplate = source.reportTemplate
            ),
            userId
        )
        scaleRepository.updateScaleAdvancedConfig(
            scaleId,
            normStrategy = "RAW_SCORE",
            normDefaultGroup = null,
            highRiskWarningEnabled = document.highRiskRules.isNotEmpty()
        )
        scaleRepository.updateSkipRules(
            scaleId,
            if (document.skipRules.isEmpty()) null else objectMapper.writeValueAsString(
                document.skipRules.map { rule ->
                    mapOf(
                        "whenQuestionNo" to rule.whenQuestionNo,
                        "whenOptionCode" to rule.whenOptionCode,
                        "skipQuestionNos" to rule.skipQuestionNos
                    )
                }
            )
        )
        scaleRepository.createDimensions(
            scaleId,
            document.dimensions.mapIndexed { index, dimension ->
                val zh = dimension.translations["zh-CN"]
                ScaleDimensionDraft(dimension.dimensionCode, zh?.name ?: dimension.dimensionCode, zh?.description, index)
            }
        )
        val dimensionIds = scaleRepository.findDimensionCodeIdMapByScaleId(scaleId)
        scaleRepository.createQuestions(
            scaleId,
            document.questions.map { question ->
                val zh = question.translations.getValue("zh-CN")
                ScaleQuestionDraft(
                    questionNo = question.questionNo,
                    questionTitle = zh.text,
                    questionType = question.questionType,
                    dimensionId = dimensionIds.getValue(question.dimensionCode),
                    requiredFlag = question.required,
                    reverseScoreFlag = question.reverseScore,
                    weightValue = question.weightValue,
                    sortNo = question.questionNo,
                    options = question.options.mapIndexed { index, option ->
                        ScaleQuestionOptionDraft(
                            optionCode = option.code,
                            optionLabel = option.translations["zh-CN"] ?: option.code,
                            scoreValue = option.score,
                            sortNo = index
                        )
                    }
                )
            }
        )
        val questionIds = scaleRepository.findQuestionNoIdMapByScaleId(scaleId)
        val optionIds = scaleRepository.findOptionIdMapByScaleId(scaleId)
        val norms = document.norms.factorReferenceFromUserText.entries.mapIndexed { index, (code, factor) ->
            ScaleNormDraft(
                normCode = "${source.scaleCode.uppercase()}_USER_TEXT_$code",
                normName = "$code factor reference (pending review)",
                dimensionId = dimensionIds[code],
                applicableTarget = source.applicableTarget,
                ageMin = factor.ageMin,
                ageMax = factor.ageMax,
                gender = factor.gender,
                orgType = factor.orgType,
                meanScore = factor.mean,
                stdDeviation = factor.sd,
                tScoreMean = factor.tScoreMean,
                tScoreStdDeviation = factor.tScoreStdDeviation,
                sortNo = index
            )
        }
        val createdNorms = scaleRepository.createNorms(scaleId, norms)
        val createdResultRules = scaleRepository.createResultRules(
            scaleId,
            document.resultRules.map { rule ->
                val zh = rule.translations["zh-CN"]
                ScaleResultRuleDraft(
                    dimensionId = rule.dimensionCode?.let { dimensionIds.getValue(it) },
                    riskLevel = rule.riskLevel,
                    scoreMin = rule.scoreMin,
                    scoreMax = rule.scoreMax,
                    scoreSource = rule.scoreSource,
                    normCode = rule.normCode,
                    resultTitle = zh?.resultTitle,
                    resultDescription = zh?.resultDescription,
                    suggestionText = zh?.suggestionText
                )
            }
        )
        val createdHighRiskRules = scaleRepository.createHighRiskRules(
            scaleId,
            document.highRiskRules.mapIndexed { index, rule ->
                val zh = rule.translations["zh-CN"]
                ScaleHighRiskRuleDraft(
                    ruleCode = rule.ruleCode,
                    questionId = questionIds.getValue(rule.questionNo),
                    optionId = rule.optionCode?.let { optionIds[rule.questionNo to it] },
                    scoreThreshold = rule.scoreThreshold,
                    warningLevel = rule.warningLevel,
                    resultTitle = zh?.resultTitle,
                    resultDescription = zh?.resultDescription,
                    suggestionText = zh?.suggestionText,
                    sortNo = index
                )
            }
        )
        packageRepository.replace(
            scaleId,
            buildPackage(
                document,
                scaleId,
                dimensionIds,
                questionIds,
                optionIds,
                createdNorms.createdIds,
                createdResultRules.createdIds,
                createdHighRiskRules.createdIds
            ),
            userId
        )
        return scaleId
    }

    private fun buildPackage(
        document: ScaleSourcePackageDocument,
        scaleId: Long,
        dimensionIds: Map<String, Long>,
        questionIds: Map<Int, Long>,
        optionIds: Map<Pair<Int, String>, Long>,
        normIds: List<Long>,
        resultRuleIds: List<Long>,
        highRiskIds: List<Long>
    ): org.sainm.psy.scale.api.UpdateScalePackageRequest {
        val source = document.scale
        val citation = document.sourceReferences.joinToString("\n") { reference ->
            "${reference.title}: ${reference.url}${reference.use?.let { " ($it)" } ?: ""}"
        }.takeIf { it.isNotBlank() }
        val governance = ScalePackageGovernance(
            sourceTitle = document.governance.sourceTitle ?: "Source package import",
            publisherName = document.governance.publisherName,
            manualVersion = source.versionNo,
            citationText = citation,
            sourceUrl = document.sourceReferences.firstOrNull()?.url,
            copyrightStatus = document.governance.copyrightStatus,
            rightsHolder = document.governance.rightsHolder,
            authorizationStatus = document.governance.authorizationStatus,
            authorizationType = document.governance.authorizationType,
            authorizationScope = document.governance.authorizationScope,
            authorizedLanguages = document.governance.authorizedLanguages,
            targetPopulation = document.governance.targetPopulation,
            nonDiagnosticStatement = document.governance.nonDiagnosticStatement,
            governanceStatus = document.governance.governanceStatus
        )
        val translationData = document.translations
        val translations = ScaleSourcePackageValidation.REQUIRED_LOCALES.map { locale ->
            val sourceTranslation = translationData[locale]
            ScalePackageTranslation(
                localeCode = locale,
                scaleName = sourceTranslation?.scaleName ?: source.scaleName,
                instructionText = source.instruction[locale],
                nonDiagnosticText = document.governance.nonDiagnosticStatement,
                highRiskActionText = highRiskAction(locale),
                reviewStatus = "DRAFT"
            )
        }
        val dimensionTranslations = document.dimensions.flatMap { dimension ->
            ScaleSourcePackageValidation.REQUIRED_LOCALES.map { locale ->
                val translation = dimension.translations.getValue(locale)
                ScalePackageDimensionTranslation(
                    dimensionId = dimensionIds.getValue(dimension.dimensionCode),
                    localeCode = locale,
                    dimensionName = translation.name,
                    description = translation.description,
                    reviewStatus = "DRAFT"
                )
            }
        }
        val questionTranslations = document.questions.flatMap { question ->
            ScaleSourcePackageValidation.REQUIRED_LOCALES.map { locale ->
                ScalePackageQuestionTranslation(
                    questionId = questionIds.getValue(question.questionNo),
                    localeCode = locale,
                    questionTitle = question.translations.getValue(locale).text,
                    reviewStatus = "DRAFT"
                )
            }
        }
        val optionTranslations = document.questions.flatMap { question ->
            question.options.flatMap { option ->
                ScaleSourcePackageValidation.REQUIRED_LOCALES.map { locale ->
                    ScalePackageOptionTranslation(
                        optionId = optionIds.getValue(question.questionNo to option.code),
                        localeCode = locale,
                        optionLabel = option.translations.getValue(locale),
                        reviewStatus = "DRAFT"
                    )
                }
            }
        }
        val highRiskTranslations = document.highRiskRules.flatMapIndexed { index, rule ->
            ScaleSourcePackageValidation.REQUIRED_LOCALES.map { locale ->
                val translation = rule.translations.getValue(locale)
                ScalePackageHighRiskRuleTranslation(
                    highRiskRuleId = highRiskIds[index],
                    localeCode = locale,
                    resultTitle = translation.resultTitle,
                    resultDescription = translation.resultDescription,
                    suggestionText = translation.suggestionText,
                    reviewStatus = "DRAFT"
                )
            }
        }
        val resultRuleTranslations = document.resultRules.flatMapIndexed { index, rule ->
            ScaleSourcePackageValidation.REQUIRED_LOCALES.map { locale ->
                val translation = rule.translations.getValue(locale)
                ScalePackageResultRuleTranslation(
                    resultRuleId = resultRuleIds[index],
                    localeCode = locale,
                    resultTitle = translation.resultTitle,
                    resultDescription = translation.resultDescription,
                    suggestionText = translation.suggestionText,
                    reviewStatus = "DRAFT"
                )
            }
        }
        val normGovernance = document.norms.factorReferenceFromUserText.entries.mapIndexed { index, (_, factor) ->
            ScalePackageNormGovernance(
                normId = normIds[index],
                sourceReference = factor.sourceReference
                    ?: document.norms.sourceReference
                    ?: document.sourceReferences.firstOrNull()?.url,
                normVersion = factor.normVersion ?: "USER_TEXT_PENDING_REVIEW",
                sampleSize = factor.sampleSize,
                regionCode = factor.regionCode,
                languageCode = factor.languageCode ?: "zh-CN",
                validFrom = factor.validFrom,
                validTo = factor.validTo,
                reviewStatus = "PENDING_REVIEW"
            )
        }
        val outputSchemaJson = objectMapper.writeValueAsString(
            mapOf("metrics" to document.scoring.indices.keys.toList())
        )
        val inputSchemaJson = objectMapper.writeValueAsString(
            linkedMapOf<String, Any?>(
                "questionCount" to document.questions.size,
                "min" to source.responseScale.min,
                "max" to source.responseScale.max,
                "dimensionRecodes" to document.dimensions.mapNotNull { dimension ->
                    dimension.recode?.let { recode ->
                        dimension.dimensionCode to mapOf(
                            "rule" to recode.rule,
                            "bands" to recode.bands.map { band ->
                                mapOf("min" to band.min, "max" to band.max, "value" to band.value)
                            },
                            "startQuestionId" to recode.startQuestionNo?.let { questionIds[it] },
                            "endQuestionId" to recode.endQuestionNo?.let { questionIds[it] },
                            "sleepQuestionId" to recode.sleepQuestionNo?.let { questionIds[it] }
                        )
                    }
                }.toMap()
            )
        )
        return org.sainm.psy.scale.api.UpdateScalePackageRequest(
            governance = governance,
            translations = translations,
            dimensionTranslations = dimensionTranslations,
            questionTranslations = questionTranslations,
            optionTranslations = optionTranslations,
            resultRuleTranslations = resultRuleTranslations,
            highRiskRuleTranslations = highRiskTranslations,
            qualityPolicy = ScalePackageQualityPolicy(
                missingAnswerPolicy = source.qualityPolicy.missingAnswerPolicy,
                maxMissingRatio = source.qualityPolicy.maxMissingRatio,
                minimumDurationSeconds = source.qualityPolicy.minimumDurationSeconds,
                maximumDurationSeconds = source.qualityPolicy.maximumDurationSeconds,
                invalidResultAction = source.qualityPolicy.invalidResultAction,
                requireAllRequiredAnswers = source.qualityPolicy.requireAllRequiredAnswers
            ),
            validityRules = emptyList(),
            algorithmBinding = source.algorithmBinding?.let { binding ->
                org.sainm.psy.scale.domain.ScalePackageAlgorithmBinding(
                    algorithmCode = binding.algorithmCode,
                    algorithmVersion = binding.algorithmVersion,
                    implementationType = binding.implementationType,
                    inputSchemaJson = inputSchemaJson,
                    outputSchemaJson = outputSchemaJson,
                    reviewStatus = "DRAFT"
                )
            },
            normGovernance = normGovernance
        )
    }

    private fun importGoldenCases(document: ScaleSourcePackageDocument, scaleId: Long, userId: Long): Int {
        val target = requireNotNull(scaleRepository.findDetailById(scaleId))
        val scaleHash = fingerprintService.calculate(target)
        document.goldenCases.forEach { sourceCase ->
            val inputJson = objectMapper.writeValueAsString(sourceCase.input)
            val expectedJson = objectMapper.writeValueAsString(sourceCase.expected)
            val sourceReference = sourceCase.sourceReference?.takeIf { it.isNotBlank() }
                ?: "${document.scale.scaleCode} source package"
            val caseHash = fingerprintService.sha256(
                listOf(sourceCase.caseCode.trim().uppercase(), sourceCase.caseType.trim().uppercase(), sourceReference, inputJson, expectedJson).joinToString("|")
            )
            publicationRepository.saveCaseRevision(
                scaleId,
                sourceCase.caseCode.trim().uppercase(),
                sourceCase.caseType.trim().uppercase(),
                sourceReference,
                scaleHash,
                caseHash,
                inputJson,
                expectedJson,
                userId
            )
        }
        return document.goldenCases.size
    }

    private fun highRiskAction(locale: String): String = when (locale) {
        "zh-CN" -> "高风险信号必须由指定专业人员人工复核、联系和升级，并记录处置。"
        "ja-JP" -> "高リスクシグナルは指定された専門職が確認・連絡・エスカレーションし、対応を記録します。"
        else -> "A designated professional must review, contact, escalate, and document every high-risk signal."
    }
}
