package org.sainm.psy.scale.api

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tenant-neutral source package used for controlled first import of a real
 * instrument. It intentionally has no database IDs, release fingerprint or
 * approval claims; confirmation materializes a DRAFT scale and keeps every
 * governance review in the database.
 */
data class ScaleSourcePackageDocument(
    val format: String = "PSY_SCALE_SOURCE_PACKAGE",
    val schemaVersion: Int = 1,
    val scale: SourceScale,
    val governance: SourceGovernance = SourceGovernance(),
    val translations: Map<String, SourceScaleTranslation> = emptyMap(),
    val dimensions: List<SourceDimension> = emptyList(),
    val questions: List<SourceQuestion> = emptyList(),
    val scoring: SourceScoring = SourceScoring(),
    val norms: SourceNorms = SourceNorms(),
    val resultRules: List<SourceResultRule> = emptyList(),
    val highRiskRules: List<SourceHighRiskRule> = emptyList(),
    val goldenCases: List<SourceGoldenCase> = emptyList(),
    val sourceReferences: List<SourceReference> = emptyList(),
    val publicationBlockers: List<String> = emptyList()
)

data class SourceScale(
    val scaleCode: String,
    val scaleName: String,
    val versionNo: String? = null,
    val applicableTarget: String? = null,
    val scoreMethod: String = "SIMPLE_SUM",
    val scoreCoefficient: BigDecimal = BigDecimal.ONE,
    val responseScale: SourceResponseScale = SourceResponseScale(),
    val qualityPolicy: SourceQualityPolicy = SourceQualityPolicy(),
    val reportTemplate: String? = null,
    val algorithmBinding: SourceAlgorithmBinding? = null,
    val instruction: Map<String, String> = emptyMap()
)

data class SourceResponseScale(
    val min: Int = 0,
    val max: Int = 4,
    val labels: List<String> = emptyList()
)

data class SourceQualityPolicy(
    val missingAnswerPolicy: String = "REJECT",
    val maxMissingRatio: BigDecimal = BigDecimal.ZERO,
    val minimumDurationSeconds: Int? = null,
    val maximumDurationSeconds: Int? = null,
    val invalidResultAction: String = "INVALIDATE",
    val requireAllRequiredAnswers: Boolean = true
)

data class SourceAlgorithmBinding(
    val algorithmCode: String,
    val algorithmVersion: String,
    val implementationType: String = "RESTRICTED_EXTENSION"
)

data class SourceGovernance(
    val sourceTitle: String? = null,
    val publisherName: String? = null,
    val copyrightStatus: String = "PENDING_REVIEW",
    val rightsHolder: String? = null,
    val authorizationStatus: String = "PENDING_REVIEW",
    val authorizationType: String? = null,
    val authorizationScope: String? = null,
    val authorizedLanguages: String? = null,
    val governanceStatus: String = "DRAFT",
    val targetPopulation: String? = null,
    val nonDiagnosticStatement: String? = null,
    val reviewStatus: String = "PENDING_REVIEW"
)

data class SourceScaleTranslation(
    val scaleName: String,
    val reviewStatus: String = "DRAFT"
)

data class SourceDimension(
    val dimensionCode: String,
    val questionNos: List<Int> = emptyList(),
    val translations: Map<String, SourceDimensionTranslation> = emptyMap(),
    val recode: SourceDimensionRecode? = null
)

data class SourceDimensionTranslation(
    val name: String,
    val description: String? = null,
    val reviewStatus: String = "DRAFT"
)

/**
 * A restricted, declaration-only recoding applied after the dimension's raw
 * aggregate score. Only rules on the runtime whitelist are accepted; arbitrary
 * expressions or scripts are rejected.
 */
data class SourceDimensionRecode(
    val rule: String,
    val bands: List<SourceRecodeBand> = emptyList(),
    val startQuestionNo: Int? = null,
    val endQuestionNo: Int? = null,
    val sleepQuestionNo: Int? = null
)

data class SourceRecodeBand(
    val min: BigDecimal,
    val max: BigDecimal,
    val value: BigDecimal
)

data class SourceQuestion(
    val questionNo: Int,
    val dimensionCode: String,
    val questionType: String = "SINGLE_CHOICE",
    val required: Boolean = true,
    val reverseScore: Boolean = false,
    val weightValue: BigDecimal = BigDecimal.ONE,
    val translations: Map<String, SourceQuestionTranslation> = emptyMap(),
    val options: List<SourceOption> = emptyList()
)

data class SourceQuestionTranslation(
    val text: String,
    val reviewStatus: String = "DRAFT"
)

data class SourceOption(
    val code: String,
    val score: BigDecimal,
    val translations: Map<String, String> = emptyMap()
)

data class SourceScoring(
    val canonicalConvention: String? = null,
    val positiveSymptomRule: String? = null,
    val indices: Map<String, String> = emptyMap(),
    val dimensionRule: String? = null
)

/**
 * A source-package result band.  The score range is evaluated by the existing
 * ScoreCalculator; localized text is materialized as reviewed result-rule
 * translations and is never executed as code.
 */
data class SourceResultRule(
    val ruleCode: String,
    val dimensionCode: String? = null,
    val riskLevel: String,
    val scoreMin: BigDecimal,
    val scoreMax: BigDecimal,
    val scoreSource: String = "RAW_SCORE",
    val normCode: String? = null,
    val translations: Map<String, SourceResultRuleTranslation> = emptyMap()
)

data class SourceResultRuleTranslation(
    val resultTitle: String,
    val resultDescription: String? = null,
    val suggestionText: String? = null,
    val reviewStatus: String = "DRAFT"
)

data class SourceNorms(
    val status: String = "PENDING_REVIEW",
    val sourceReference: String? = null,
    val factorReferenceFromUserText: Map<String, SourceNormFactor> = emptyMap(),
    val interpretation: String? = null
)

data class SourceNormFactor(
    val mean: BigDecimal? = null,
    val sd: BigDecimal? = null,
    val tScoreMean: BigDecimal? = null,
    val tScoreStdDeviation: BigDecimal? = null,
    val ageMin: Int? = null,
    val ageMax: Int? = null,
    val gender: String? = null,
    val orgType: String? = null,
    val sampleSize: Int? = null,
    val sourceReference: String? = null,
    val normVersion: String? = null,
    val regionCode: String? = null,
    val languageCode: String? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null
)

data class SourceHighRiskRule(
    val ruleCode: String,
    val questionNo: Int,
    val optionCode: String? = null,
    val scoreThreshold: BigDecimal? = null,
    val warningLevel: String = "HIGH",
    val reviewStatus: String = "PENDING_PROFESSIONAL_REVIEW",
    val translations: Map<String, SourceHighRiskTranslation> = emptyMap()
)

data class SourceHighRiskTranslation(
    val resultTitle: String,
    val resultDescription: String? = null,
    val suggestionText: String? = null,
    val reviewStatus: String = "DRAFT"
)

data class SourceGoldenCase(
    val caseCode: String,
    val caseType: String,
    val sourceReference: String? = null,
    val input: JsonNode,
    val expected: JsonNode
)

data class SourceReference(
    val title: String,
    val url: String,
    val use: String? = null
)
