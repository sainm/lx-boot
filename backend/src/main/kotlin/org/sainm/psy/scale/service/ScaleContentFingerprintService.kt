package org.sainm.psy.scale.service

import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCase
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Service
class ScaleContentFingerprintService(
    private val packageRepository: ScalePackageRepository,
    private val visualizationService: VisualizationService
) {
    fun calculate(scale: ScaleDetail): String {
        val completeScale = scale.copy(visualizationConfigs = visualizationService.findConfigs(scale.id))
        val canonical = buildString {
            token(completeScale.scaleCode)
            token(completeScale.scaleName)
            token(completeScale.description)
            token(completeScale.applicableTarget)
            token(completeScale.versionNo)
            token(completeScale.scoreMethod)
            token(completeScale.scoreCoefficient)
            token(completeScale.normStrategy)
            token(completeScale.normDefaultGroup)
            token(completeScale.highRiskWarningEnabled)
            token(completeScale.anonymousSupported)
            token(completeScale.reportTemplate)
            // Conditional branching changes the respondent-visible form and the
            // set of items entering scoring, so it is part of the immutable hash.
            // Keep legacy fingerprints stable for scales that have no rules.
            completeScale.skipRulesJson?.let {
                token("skip-rules")
                token(it)
            }
            completeScale.dimensions.sortedWith(compareBy({ it.sortNo }, { it.dimensionCode })).forEach { dimension ->
                token("dimension")
                token(dimension.dimensionCode)
                token(dimension.dimensionName)
                token(dimension.description)
                token(dimension.sortNo)
            }
            val dimensionCodes = completeScale.dimensions.associate { it.id to it.dimensionCode }
            completeScale.questions.sortedWith(compareBy({ it.sortNo }, { it.questionNo })).forEach { question ->
                token("question")
                token(question.questionNo)
                token(question.dimensionId?.let(dimensionCodes::get))
                token(question.questionTitle)
                token(question.questionType)
                token(question.requiredFlag)
                token(question.reverseScoreFlag)
                token(question.weightValue)
                token(question.optionSelectionLimit)
                token(question.sliderMin)
                token(question.sliderMax)
                token(question.sliderStep)
                token(question.textInputEnabled)
                token(question.textInputPlaceholder)
                token(question.matrixGroupCode)
                token(question.rowCode)
                token(question.columnCode)
                token(question.sortNo)
                question.options.sortedWith(compareBy({ it.sortNo }, { it.optionCode })).forEach { option ->
                    token("option")
                    token(option.optionCode)
                    token(option.optionLabel)
                    token(option.scoreValue)
                    token(option.exclusiveFlag)
                    token(option.optionGroupCode)
                    token(option.sortNo)
                }
            }
            completeScale.resultRules.sortedWith(
                compareBy({ it.dimensionId?.let(dimensionCodes::get) }, { it.scoreMin }, { it.scoreMax })
            ).forEach { rule ->
                token("result-rule")
                token(rule.dimensionId?.let(dimensionCodes::get))
                token(rule.riskLevel)
                token(rule.scoreMin)
                token(rule.scoreMax)
                token(rule.scoreSource)
                token(rule.normCode)
                token(rule.resultTitle)
                token(rule.resultDescription)
                token(rule.suggestionText)
            }
            completeScale.norms.sortedWith(
                compareBy({ it.dimensionId?.let(dimensionCodes::get) }, { it.sortNo }, { it.normCode })
            ).forEach { norm ->
                token("norm")
                token(norm.normCode)
                token(norm.normName)
                token(norm.dimensionId?.let(dimensionCodes::get))
                token(norm.applicableTarget)
                token(norm.ageMin)
                token(norm.ageMax)
                token(norm.gender)
                token(norm.orgType)
                token(norm.meanScore)
                token(norm.stdDeviation)
                token(norm.tScoreMean)
                token(norm.tScoreStdDeviation)
                token(norm.sortNo)
            }
            completeScale.highRiskRules.sortedWith(compareBy({ it.sortNo }, { it.ruleCode })).forEach { rule ->
                token("high-risk-rule")
                token(rule.ruleCode)
                token(rule.questionNo)
                token(rule.optionCode)
                token(rule.scoreThreshold)
                token(rule.warningLevel)
                token(rule.resultTitle)
                token(rule.resultDescription)
                token(rule.suggestionText)
                token(rule.sortNo)
            }
            completeScale.visualizationConfigs.sortedWith(compareBy({ it.sortNo }, { it.chartType }, { it.viewScope })).forEach { config ->
                token("visualization")
                token(config.chartType)
                token(config.chartTitle)
                token(config.viewScope)
                token(config.dataSource)
                token(config.configJson)
                token(config.enabled)
                token(config.sortNo)
            }
            packageRepository.canonicalValues(completeScale.id).forEach { value ->
                token("scale-package")
                token(value)
            }
        }
        return sha256(canonical)
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun calculateReleaseFingerprint(scaleContentHash: String, latestCases: List<ScaleGoldenCase>): String = sha256(
        buildString {
            append(scaleContentHash)
            latestCases.sortedBy { it.caseCode }.forEach { append('|').append(it.caseCode).append(':').append(it.caseContentHash) }
        }
    )

    private fun StringBuilder.token(value: Any?) {
        val text = when (value) {
            null -> "<null>"
            is BigDecimal -> value.stripTrailingZeros().toPlainString()
            else -> value.toString()
        }
        append(text.length).append(':').append(text).append('|')
    }
}
