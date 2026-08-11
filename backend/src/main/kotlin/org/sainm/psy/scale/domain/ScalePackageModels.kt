package org.sainm.psy.scale.domain

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.time.LocalDate

data class ScalePackageGovernance(
    val sourceTitle: String? = null,
    val publisherName: String? = null,
    val manualVersion: String? = null,
    val citationText: String? = null,
    val sourceUrl: String? = null,
    val copyrightStatus: String = "PENDING_REVIEW",
    val rightsHolder: String? = null,
    val authorizationStatus: String = "PENDING_REVIEW",
    val authorizationType: String? = null,
    val authorizationScope: String? = null,
    val authorizedTerritories: String? = null,
    val authorizedLanguages: String? = null,
    val authorizationValidFrom: LocalDate? = null,
    val authorizationValidTo: LocalDate? = null,
    val targetPopulation: String? = null,
    val exclusionCriteria: String? = null,
    val estimatedMinutes: Int? = null,
    val resultVisibility: String? = null,
    val dataUsageStatement: String? = null,
    val nonDiagnosticStatement: String? = null,
    val helpResourceText: String? = null,
    val governanceStatus: String = "DRAFT"
)

data class ScalePackageTranslation(
    val localeCode: String,
    val scaleName: String,
    val description: String? = null,
    val instructionText: String? = null,
    val purposeText: String? = null,
    val dataUsageText: String? = null,
    val resultVisibilityText: String? = null,
    val nonDiagnosticText: String? = null,
    val highRiskActionText: String? = null,
    val helpResourceText: String? = null,
    val reviewStatus: String = "DRAFT"
)

data class ScalePackageDimensionTranslation(val dimensionId: Long, val localeCode: String, val dimensionName: String, val description: String? = null, val reviewStatus: String = "DRAFT")
data class ScalePackageQuestionTranslation(val questionId: Long, val localeCode: String, val questionTitle: String, val textInputPlaceholder: String? = null, val reviewStatus: String = "DRAFT")
data class ScalePackageOptionTranslation(val optionId: Long, val localeCode: String, val optionLabel: String, val reviewStatus: String = "DRAFT")
data class ScalePackageResultRuleTranslation(val resultRuleId: Long, val localeCode: String, val resultTitle: String, val resultDescription: String? = null, val suggestionText: String? = null, val reviewStatus: String = "DRAFT")
data class ScalePackageHighRiskRuleTranslation(val highRiskRuleId: Long, val localeCode: String, val resultTitle: String, val resultDescription: String? = null, val suggestionText: String? = null, val reviewStatus: String = "DRAFT")

data class ScalePackageQualityPolicy(
    val missingAnswerPolicy: String = "REJECT",
    val maxMissingRatio: BigDecimal = BigDecimal.ZERO,
    val minimumDurationSeconds: Int? = null,
    val maximumDurationSeconds: Int? = null,
    val invalidResultAction: String = "INVALIDATE",
    val requireAllRequiredAnswers: Boolean = true
)

data class ScalePackageValidityRule(
    val ruleCode: String,
    val ruleType: String,
    val ruleVersion: String,
    val configJson: String = "{}",
    val reviewStatus: String = "DRAFT",
    val enabled: Boolean = true,
    val sortNo: Int = 0
)

data class ScalePackageAlgorithmBinding(
    val algorithmCode: String,
    val algorithmVersion: String,
    val implementationType: String,
    val inputSchemaJson: String = "{}",
    val outputSchemaJson: String = "{}",
    val implementationChecksum: String? = null,
    val reviewStatus: String = "DRAFT"
)

data class ScalePackageNormGovernance(
    val normId: Long,
    val sourceReference: String? = null,
    val normVersion: String? = null,
    val sampleSize: Int? = null,
    val regionCode: String? = null,
    val languageCode: String? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val reviewStatus: String = "PENDING_REVIEW"
)

data class ScalePackageSnapshot(
    val scaleId: Long,
    val governance: ScalePackageGovernance? = null,
    val translations: List<ScalePackageTranslation> = emptyList(),
    val dimensionTranslations: List<ScalePackageDimensionTranslation> = emptyList(),
    val questionTranslations: List<ScalePackageQuestionTranslation> = emptyList(),
    val optionTranslations: List<ScalePackageOptionTranslation> = emptyList(),
    val resultRuleTranslations: List<ScalePackageResultRuleTranslation> = emptyList(),
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val highRiskRuleTranslations: List<ScalePackageHighRiskRuleTranslation> = emptyList(),
    val qualityPolicy: ScalePackageQualityPolicy? = null,
    val validityRules: List<ScalePackageValidityRule> = emptyList(),
    val algorithmBinding: ScalePackageAlgorithmBinding? = null,
    val normGovernance: List<ScalePackageNormGovernance> = emptyList()
)
