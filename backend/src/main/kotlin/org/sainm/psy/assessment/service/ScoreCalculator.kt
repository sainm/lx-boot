package org.sainm.psy.assessment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.i18n.SupportedContentLocale
import org.sainm.psy.scale.domain.ScalePackageQualityPolicy
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class QuestionScoreContext(
    val questionId: Long,
    val dimensionId: Long?,
    val reverseScoreFlag: Boolean,
    val weightValue: BigDecimal,
    val rawScore: BigDecimal,
    val selectedOptionIds: List<Long> = emptyList(),
    val answerValue: BigDecimal? = null,
    val answerText: String? = null,
    /** Total number of questions in the same dimension, when known. */
    val dimensionQuestionCount: Int? = null,
    /** Total declared weight of questions in the same dimension, when known. */
    val dimensionWeightTotal: BigDecimal? = null
)

data class ScoreCalculationOptions(
    val qualityPolicy: ScalePackageQualityPolicy = ScalePackageQualityPolicy(),
    val totalQuestionCount: Int? = null,
    val answeredQuestionCount: Int? = null,
    val totalWeight: BigDecimal? = null,
    val answeredWeight: BigDecimal? = null
)

/**
 * Audit-only scoring evidence. It contains derived numeric values, never free
 * text answers. The immutable answer rows and scale content hash remain the
 * source of truth; this trace makes the calculation path inspectable without
 * exposing sensitive answer text in reports.
 */
data class ScoringTraceQuestion(
    val questionId: Long,
    val rawScore: BigDecimal,
    val reverseScore: BigDecimal,
    val weightValue: BigDecimal,
    val weightedScore: BigDecimal,
    val effectiveScore: BigDecimal,
    val dimensionId: Long?
)

data class ScoringTraceDimension(
    val dimensionId: Long,
    val questionIds: List<Long>,
    val score: BigDecimal,
    val aggregation: String
)

data class ScoringTrace(
    val algorithmCode: String = "GENERIC_SCORE_CALCULATOR",
    val algorithmVersion: String = "1",
    val scoreMethod: String,
    val scoreCoefficient: BigDecimal,
    val missingAnswerPolicy: String,
    val prorateFactor: BigDecimal,
    val questions: List<ScoringTraceQuestion>,
    val dimensions: List<ScoringTraceDimension>,
    val normCode: String?,
    val normSelectionReason: String?,
    val scoreSource: String,
    val standardScore: BigDecimal?,
    val zScore: BigDecimal?,
    val tScore: BigDecimal?,
    val resultRuleMatched: Boolean,
    val highRiskRuleCode: String?,
    val highRiskTriggered: Boolean,
    val totalScore: BigDecimal,
    /** Scale-specific derived indices, kept numeric and audit-friendly. */
    val derivedMetrics: Map<String, BigDecimal> = emptyMap(),
    /** Named restricted-profile semantics; arbitrary expressions are never executed. */
    val restrictedProfile: Map<String, String> = emptyMap()
)

data class NormMatchingContext(
    val age: Int? = null,
    val gender: String? = null,
    val orgType: String? = null,
    val applicableTarget: String? = null,
    val preferredNormCode: String? = null
)

data class DimensionScoreResult(
    val dimensionId: Long,
    val score: BigDecimal,
    val riskLevel: String?,
    val resultTitle: String?,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val normSelectionReason: String? = null
)

data class ScoreResult(
    val totalScore: BigDecimal,
    val riskLevel: String,
    val resultTitle: String?,
    val resultDescription: String?,
    val suggestionText: String?,
    val dimensionScores: List<DimensionScoreResult>,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val normSelectionReason: String? = null,
    val resultRuleMatched: Boolean = false,
    val highRiskTriggered: Boolean = false,
    val highRiskRuleCode: String? = null,
    val highRiskWarningLevel: String? = null,
    val scoringTrace: ScoringTrace? = null,
    /** Numeric metrics rendered by the scale-specific report template. */
    val metrics: Map<String, BigDecimal> = emptyMap()
)

@Component
class ScoreCalculator(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val messages: LocalizedMessages,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    private data class EffectiveItem(
        val questionId: Long,
        val dimensionId: Long?,
        val rawScore: BigDecimal,
        val reverseScore: BigDecimal,
        val weightValue: BigDecimal,
        val weightedScore: BigDecimal,
        val effectiveScore: BigDecimal,
        val answerText: String? = null,
        val answerValue: BigDecimal? = null,
        val dimensionQuestionCount: Int?,
        val dimensionWeightTotal: BigDecimal?
    )
    private data class AlgorithmBinding(
        val algorithmCode: String?,
        val dimensionAggregation: String?,
        val dimensionRecodes: Map<String, DimensionRecode>,
        val derivedMetrics: Set<String>,
        val canonicalConvention: String?,
        val positiveSymptomRule: String?,
        val dimensionRule: String?
    )
    private data class RecodeBand(
        val min: BigDecimal,
        val max: BigDecimal,
        val value: BigDecimal
    )
    private data class DimensionRecode(
        val rule: String,
        val bands: List<RecodeBand>,
        val startQuestionId: Long?,
        val endQuestionId: Long?,
        val sleepQuestionId: Long?
    )
    private data class NormScore(
        val normCode: String,
        val zScore: BigDecimal,
        val tScore: BigDecimal,
        val selectionReason: String
    )
    private data class NormCandidate(
        val normCode: String,
        val applicableTarget: String?,
        val ageMin: Int?,
        val ageMax: Int?,
        val gender: String?,
        val orgType: String?,
        val meanScore: BigDecimal?,
        val stdDeviation: BigDecimal?,
        val tScoreMean: BigDecimal?,
        val tScoreStdDeviation: BigDecimal?,
        val sortNo: Int
    )
    private data class RiskRuleMatch(
        val riskLevel: String?,
        val resultTitle: String?,
        val resultDescription: String?,
        val suggestionText: String?,
        val scoreSource: String,
        val standardScore: BigDecimal?,
        val zScore: BigDecimal?,
        val tScore: BigDecimal?,
        val normCode: String?,
        val normSelectionReason: String? = null,
        val matchedRule: Boolean = false
    )
    private data class HighRiskMatch(
        val ruleCode: String,
        val warningLevel: String,
        val resultTitle: String?,
        val resultDescription: String?,
        val suggestionText: String?
    )

    fun calculate(
        scaleId: Long,
        scoreMethod: String,
        scoreCoefficient: BigDecimal,
        items: List<QuestionScoreContext>,
        normContext: NormMatchingContext? = null,
        highRiskWarningEnabled: Boolean = true,
        localeCode: String = SupportedContentLocale.currentCode(),
        options: ScoreCalculationOptions = ScoreCalculationOptions()
    ): ScoreResult {
        val normalizedScoreMethod = scoreMethod.trim().uppercase()
        val normalizedMissingAnswerPolicy = options.qualityPolicy.missingAnswerPolicy.trim().uppercase()
        if (scoreCoefficient <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Score coefficient must be positive")
        }
        if (items.map { it.questionId }.toSet().size != items.size) {
            throw IllegalArgumentException("Duplicate question contexts are not supported")
        }
        if (items.any { it.weightValue <= BigDecimal.ZERO }) {
            throw IllegalArgumentException("Question weights must be positive")
        }
        if (normalizedMissingAnswerPolicy !in SUPPORTED_MISSING_POLICIES) {
            throw IllegalArgumentException("Unsupported missing answer policy: ${options.qualityPolicy.missingAnswerPolicy}")
        }
        val algorithmBinding = loadAlgorithmBinding(scaleId)
        val algorithmCode = algorithmBinding.algorithmCode
        if (algorithmCode != null && algorithmCode !in SUPPORTED_ALGORITHMS) {
            throw IllegalArgumentException("Unsupported scoring algorithm: $algorithmCode")
        }
        val effectiveItems = applyScoreMethod(normalizedScoreMethod, items)
        val scoreSum = effectiveItems.fold(BigDecimal.ZERO) { acc, it -> acc + it.effectiveScore }
        val totalQuestionCount = options.totalQuestionCount?.coerceAtLeast(items.size) ?: items.size
        val answeredQuestionCount = (options.answeredQuestionCount ?: items.size)
            .coerceIn(0, totalQuestionCount)
        val questionProrateFactor = if (answeredQuestionCount > 0 && totalQuestionCount > answeredQuestionCount) {
            BigDecimal(totalQuestionCount).divide(BigDecimal(answeredQuestionCount), 8, RoundingMode.HALF_UP)
        } else BigDecimal.ONE
        val weightedProrateFactor = if (options.totalWeight != null && options.answeredWeight != null &&
            options.answeredWeight > BigDecimal.ZERO && options.totalWeight > options.answeredWeight
        ) {
            options.totalWeight.divide(options.answeredWeight, 8, RoundingMode.HALF_UP)
        } else questionProrateFactor
        // Sum-based methods need a scale-up when PRORATE is selected.  An
        // average is already normalized by the answered item count/weight;
        // applying another factor would double-count missing answers and make
        // the persisted trace disagree with the result semantics.
        val prorateFactor = if (
            normalizedMissingAnswerPolicy == "PRORATE" &&
            normalizedScoreMethod !in AVERAGE_SCORE_METHODS
        ) {
            if (normalizedScoreMethod in WEIGHTED_SCORE_METHODS) weightedProrateFactor else questionProrateFactor
        } else BigDecimal.ONE
        val rawTotal = when (normalizedScoreMethod) {
            "AVERAGE" -> scoreSum.divide(BigDecimal(answeredQuestionCount.coerceAtLeast(1)), 8, RoundingMode.HALF_UP)
            "WEIGHTED_AVERAGE" -> {
                val weightSum = items.fold(BigDecimal.ZERO) { acc, it -> acc + it.weightValue }
                if (weightSum.compareTo(BigDecimal.ZERO) == 0) {
                    throw IllegalArgumentException("Weighted average requires a positive total weight")
                }
                scoreSum.divide(weightSum, 8, RoundingMode.HALF_UP)
            }
            else -> scoreSum * prorateFactor
        }
        val totalScore = (rawTotal * scoreCoefficient).setScale(4, RoundingMode.HALF_UP)
        val dimensionScores = computeDimensionScores(
            scaleId,
            normalizedScoreMethod,
            effectiveItems,
            normContext,
            localeCode,
            normalizedMissingAnswerPolicy,
            algorithmBinding.dimensionAggregation ?: normalizedScoreMethod,
            algorithmBinding.dimensionRecodes
        )
        val globalRisk = resolveGlobalRisk(scaleId, totalScore, normContext, localeCode)
        val highRiskMatch = if (highRiskWarningEnabled) resolveHighRisk(scaleId, items, localeCode) else null
        val finalRiskLevel = maxRiskLevel(globalRisk.riskLevel, highRiskMatch?.warningLevel)
        val derivedMetrics = deriveMetrics(
            algorithmBinding,
            normalizedScoreMethod,
            effectiveItems,
            totalQuestionCount,
            answeredQuestionCount,
            totalScore
        )
        val trace = ScoringTrace(
            algorithmCode = algorithmCode ?: "GENERIC_SCORE_CALCULATOR",
            scoreMethod = normalizedScoreMethod,
            scoreCoefficient = scoreCoefficient,
            missingAnswerPolicy = normalizedMissingAnswerPolicy,
            prorateFactor = prorateFactor.setScale(8, RoundingMode.HALF_UP),
            questions = effectiveItems.map {
                ScoringTraceQuestion(
                    questionId = it.questionId,
                    rawScore = it.rawScore,
                    reverseScore = it.reverseScore,
                    weightValue = it.weightValue,
                    weightedScore = it.weightedScore,
                    effectiveScore = it.effectiveScore,
                    dimensionId = it.dimensionId
                )
            },
            dimensions = dimensionScores.map { dimension ->
                ScoringTraceDimension(
                    dimensionId = dimension.dimensionId,
                    questionIds = effectiveItems.filter { it.dimensionId == dimension.dimensionId }.map { it.questionId },
                    score = dimension.score,
                    aggregation = dimensionAggregationLabel(
                        algorithmBinding.dimensionAggregation ?: normalizedScoreMethod,
                        normalizedMissingAnswerPolicy
                    )
                )
            },
            normCode = globalRisk.normCode ?: dimensionScores.firstOrNull { it.normCode != null }?.normCode,
            normSelectionReason = globalRisk.normSelectionReason
                ?: dimensionScores.firstOrNull { it.normSelectionReason != null }?.normSelectionReason,
            scoreSource = globalRisk.scoreSource,
            standardScore = globalRisk.standardScore,
            zScore = globalRisk.zScore,
            tScore = globalRisk.tScore,
            resultRuleMatched = globalRisk.resultRuleMatched,
            highRiskRuleCode = highRiskMatch?.ruleCode,
            highRiskTriggered = highRiskMatch != null,
            totalScore = totalScore,
            derivedMetrics = derivedMetrics,
            restrictedProfile = mapOf(
                "canonicalConvention" to algorithmBinding.canonicalConvention,
                "positiveSymptomRule" to algorithmBinding.positiveSymptomRule,
                "dimensionRule" to algorithmBinding.dimensionRule
            ).filterValues { !it.isNullOrBlank() }.mapValues { it.value!! }
        )
        return globalRisk.copy(
            riskLevel = finalRiskLevel,
            resultTitle = highRiskMatch?.resultTitle ?: globalRisk.resultTitle,
            resultDescription = highRiskMatch?.resultDescription ?: globalRisk.resultDescription,
            suggestionText = highRiskMatch?.suggestionText ?: globalRisk.suggestionText,
            dimensionScores = dimensionScores,
            highRiskTriggered = highRiskMatch != null,
            highRiskRuleCode = highRiskMatch?.ruleCode,
            highRiskWarningLevel = highRiskMatch?.warningLevel,
            resultRuleMatched = globalRisk.resultRuleMatched,
            scoringTrace = trace,
            metrics = derivedMetrics
        )
    }

    private fun loadAlgorithmBinding(scaleId: Long): AlgorithmBinding {
        val row = jdbcTemplate.query(
            "select algorithm_code, input_schema_json from psy_scale_algorithm_binding where scale_id = :scaleId",
            mapOf("scaleId" to scaleId)
        ) { rs, _ ->
            rs.getString("algorithm_code") to rs.getString("input_schema_json")
        }.firstOrNull()
        return AlgorithmBinding(
            algorithmCode = row?.first?.trim()?.takeIf { it.isNotEmpty() },
            dimensionAggregation = parseDimensionAggregation(row?.second),
            dimensionRecodes = parseDimensionRecodes(row?.second),
            derivedMetrics = parseDerivedMetrics(row?.second),
            canonicalConvention = parseRestrictedProfileField(row?.second, "canonicalConvention")?.uppercase(),
            positiveSymptomRule = parseRestrictedProfileField(row?.second, "positiveSymptomRule"),
            dimensionRule = parseRestrictedProfileField(row?.second, "dimensionRule")
        )
    }

    private fun parseRestrictedProfileField(inputSchemaJson: String?, field: String): String? {
        if (inputSchemaJson.isNullOrBlank()) return null
        return runCatching {
            objectMapper.readTree(inputSchemaJson).path("restrictedProfile").path(field)
                .asText(null)?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun parseDimensionAggregation(inputSchemaJson: String?): String? {
        if (inputSchemaJson.isNullOrBlank()) return null
        return runCatching {
            objectMapper.readTree(inputSchemaJson).path("dimensionAggregation").asText().trim().uppercase()
                .takeIf { it in SUPPORTED_SCORE_METHODS }
        }.getOrNull()
    }

    private fun parseDimensionRecodes(inputSchemaJson: String?): Map<String, DimensionRecode> {
        if (inputSchemaJson.isNullOrBlank()) return emptyMap()
        return runCatching {
            val node = objectMapper.readTree(inputSchemaJson)
            node.path("dimensionRecodes").takeIf { it.isObject }
                ?.fields()
                ?.asSequence()
                ?.mapNotNull { (dimensionCode, recodeNode) ->
                    val rule = recodeNode.path("rule").asText()
                    if (rule !in setOf("RECODE_SUM_TO_0_3", "SLEEP_DURATION_RECODE_0_3", "SLEEP_EFFICIENCY_RECODE_0_3")) return@mapNotNull null
                    val bands = recodeNode.path("bands").mapNotNull { bandNode ->
                        if (bandNode.hasNonNull("min") && bandNode.hasNonNull("max") && bandNode.hasNonNull("value")) {
                            RecodeBand(
                                min = bandNode.path("min").decimalValue(),
                                max = bandNode.path("max").decimalValue(),
                                value = bandNode.path("value").decimalValue()
                            )
                        } else {
                            null
                        }
                    }
                    if (bands.isEmpty()) return@mapNotNull null
                    dimensionCode to DimensionRecode(
                        rule = rule,
                        bands = bands,
                        startQuestionId = recodeNode.path("startQuestionId").takeIf { it.isNumber }?.longValue(),
                        endQuestionId = recodeNode.path("endQuestionId").takeIf { it.isNumber }?.longValue(),
                        sleepQuestionId = recodeNode.path("sleepQuestionId").takeIf { it.isNumber }?.longValue()
                    )
                }
                ?.toMap()
                .orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun parseDerivedMetrics(inputSchemaJson: String?): Set<String> {
        if (inputSchemaJson.isNullOrBlank()) return emptySet()
        return runCatching {
            objectMapper.readTree(inputSchemaJson).path("derivedMetrics")
                .takeIf { it.isArray }
                ?.mapNotNull { it.asText(null)?.trim()?.uppercase()?.takeIf { metric -> metric in SUPPORTED_DERIVED_METRICS } }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
    }

    private fun loadDimensionCodes(scaleId: Long): Map<Long, String> = jdbcTemplate.query(
        "select id, dimension_code from psy_scale_dimension where scale_id = :scaleId",
        mapOf("scaleId" to scaleId)
    ) { rs, _ -> rs.getLong("id") to rs.getString("dimension_code") }
        .toMap()

    /**
     * SCL-90/R has three global indices in addition to its dimensions.  The
     * implementation is deliberately a named, restricted extension rather
     * than an arbitrary expression/script.  The canonical 0..4 scoring
     * convention is used: a positive symptom is an answered item > 0.
     */
    private fun deriveMetrics(
        binding: AlgorithmBinding,
        scoreMethod: String,
        items: List<EffectiveItem>,
        totalQuestionCount: Int,
        answeredQuestionCount: Int,
        totalScore: BigDecimal
    ): Map<String, BigDecimal> {
        if (binding.algorithmCode == "GENERIC_SCORE_CALCULATOR" &&
            "WHO5_PERCENTAGE_SCORE" in binding.derivedMetrics
        ) {
            // WHO-5's declared technical convention is a five-item 0..5 raw
            // sum mapped to a 0..100 percentage.  The source-package validator
            // enforces this shape before import; runtime guards keep malformed
            // bindings from silently producing a misleading metric.
            if (scoreMethod != "SIMPLE_SUM" || totalQuestionCount != 5 ||
                items.size != answeredQuestionCount ||
                items.any { it.effectiveScore < BigDecimal.ZERO || it.effectiveScore > BigDecimal(5) }
            ) {
                return emptyMap()
            }
            return linkedMapOf(
                "WHO5_PERCENTAGE_SCORE" to totalScore.multiply(BigDecimal(4)).setScale(4, RoundingMode.HALF_UP)
            )
        }
        if (binding.algorithmCode != "SCL90_PROFILE") return emptyMap()
        // Degrade to no derived indices instead of crashing the scoring run when
        // a scale is mislabelled as SCL-90 or carries partial/out-of-range data.
        // The golden-case governance path still surfaces the mismatch as a
        // metric difference, so the misconfiguration is not silently accepted.
        if (binding.canonicalConvention != "0_TO_4" ||
            binding.positiveSymptomRule != "score > 0" ||
            binding.dimensionRule != "sum(dimension item scores) / answered item count in dimension"
        ) {
            return emptyMap()
        }
        if (scoreMethod != "SIMPLE_SUM") return emptyMap()
        if (totalQuestionCount != 90) return emptyMap()
        if (items.any { it.effectiveScore < BigDecimal.ZERO || it.effectiveScore > BigDecimal(4) }) return emptyMap()
        val answeredCount = answeredQuestionCount.coerceAtLeast(1)
        val positiveCount = items.count { it.effectiveScore > BigDecimal.ZERO }
        val gsi = totalScore.divide(BigDecimal(totalQuestionCount.coerceAtLeast(1)), 4, RoundingMode.HALF_UP)
        val psdi = if (positiveCount == 0) BigDecimal.ZERO else {
            items.fold(BigDecimal.ZERO) { sum, item -> sum + item.effectiveScore }
                .divide(BigDecimal(positiveCount), 4, RoundingMode.HALF_UP)
        }
        return linkedMapOf(
            "GSI" to gsi,
            "PST" to BigDecimal(positiveCount),
            "PSDI" to psdi,
            "POSITIVE_SYMPTOM_COUNT" to BigDecimal(positiveCount),
            "POSITIVE_SYMPTOM_AVERAGE" to psdi,
            "ANSWERED_ITEM_COUNT" to BigDecimal(answeredCount)
        )
    }

    private fun applyScoreMethod(scoreMethod: String, items: List<QuestionScoreContext>): List<EffectiveItem> {
        val reversedIds = items.filter { it.reverseScoreFlag }.map { it.questionId }
        val minMaxMap = if (reversedIds.isEmpty()) emptyMap() else loadOptionMinMax(reversedIds)
        return items.map { item ->
            val recodedScore = if (item.reverseScoreFlag) {
                val range = minMaxMap[item.questionId]
                    ?: throw IllegalStateException("Reverse-scored question ${item.questionId} has no option score range")
                range.first + range.second - item.rawScore
            } else {
                item.rawScore
            }
            val effectiveScore = when (scoreMethod) {
                "SIMPLE_SUM", "REVERSE_SUM", "AVERAGE" -> recodedScore
                "WEIGHTED_SUM", "WEIGHTED_AVERAGE" -> recodedScore * item.weightValue
                else -> throw IllegalArgumentException("Unsupported score method: $scoreMethod")
            }
            EffectiveItem(
                questionId = item.questionId,
                dimensionId = item.dimensionId,
                rawScore = item.rawScore,
                reverseScore = recodedScore,
                weightValue = item.weightValue,
                weightedScore = recodedScore * item.weightValue,
                effectiveScore = effectiveScore,
                answerText = item.answerText,
                answerValue = item.answerValue,
                dimensionQuestionCount = item.dimensionQuestionCount,
                dimensionWeightTotal = item.dimensionWeightTotal
            )
        }
    }

    private fun computeDimensionScores(
        scaleId: Long,
        scoreMethod: String,
        items: List<EffectiveItem>,
        normContext: NormMatchingContext?,
        localeCode: String,
        missingAnswerPolicy: String,
        dimensionAggregation: String,
        dimensionRecodes: Map<String, DimensionRecode>
    ): List<DimensionScoreResult> {
        val byDimension = items.filter { it.dimensionId != null }.groupBy { it.dimensionId!! }
        if (byDimension.isEmpty()) return emptyList()
        val dimensionCodes = if (dimensionRecodes.isEmpty()) emptyMap() else loadDimensionCodes(scaleId)
        return byDimension.map { (dimId, dimItems) ->
            val sum = dimItems.fold(BigDecimal.ZERO) { acc, item ->
                acc + if (dimensionAggregation in setOf("WEIGHTED_SUM", "WEIGHTED_AVERAGE")) {
                    item.weightedScore
                } else {
                    item.reverseScore
                }
            }
            val totalDimensionCount = dimItems.mapNotNull { it.dimensionQuestionCount }
                .maxOrNull()
                ?.coerceAtLeast(dimItems.size)
                ?: dimItems.size
            val answeredDimensionWeight = dimItems.fold(BigDecimal.ZERO) { acc, item -> acc + item.weightValue }
            val totalDimensionWeight = dimItems.mapNotNull { it.dimensionWeightTotal }
                .maxOrNull()
                ?.coerceAtLeast(answeredDimensionWeight)
                ?: answeredDimensionWeight
            val averageBased = dimensionAggregation in AVERAGE_SCORE_METHODS
            val dimensionProrateFactor = if (
                missingAnswerPolicy == "PRORATE" && !averageBased && dimItems.isNotEmpty()
            ) {
                if (dimensionAggregation in WEIGHTED_SCORE_METHODS && totalDimensionWeight > answeredDimensionWeight) {
                    totalDimensionWeight.divide(answeredDimensionWeight, 8, RoundingMode.HALF_UP)
                } else if (totalDimensionCount > dimItems.size) {
                    BigDecimal(totalDimensionCount).divide(BigDecimal(dimItems.size), 8, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ONE
                }
            } else {
                BigDecimal.ONE
            }
            val rawDimScore = when (dimensionAggregation) {
                "AVERAGE" -> sum.divide(BigDecimal(dimItems.size.coerceAtLeast(1)), 4, RoundingMode.HALF_UP)
                "WEIGHTED_AVERAGE" -> {
                    val weightSum = dimItems.fold(BigDecimal.ZERO) { acc, it -> acc + it.weightValue }
                    if (weightSum <= BigDecimal.ZERO) {
                        throw IllegalArgumentException("Weighted average requires a positive total weight")
                    }
                    sum.divide(weightSum, 4, RoundingMode.HALF_UP)
                }
                "SIMPLE_SUM", "REVERSE_SUM", "WEIGHTED_SUM" -> (sum * dimensionProrateFactor).setScale(4, RoundingMode.HALF_UP)
                else -> throw IllegalArgumentException("Unsupported dimension aggregation: $dimensionAggregation")
            }
            val dimScore = dimensionCodes[dimId]
                ?.let { dimensionRecodes[it] }
                ?.let { recode -> applyDimensionRecode(recode, rawDimScore, dimItems) }
                ?: rawDimScore
            val risk = resolveDimensionRisk(scaleId, dimId, dimScore, normContext, localeCode)
            DimensionScoreResult(
                dimensionId = dimId,
                score = dimScore,
                riskLevel = risk.riskLevel,
                resultTitle = risk.resultTitle,
                scoreSource = risk.scoreSource,
                standardScore = risk.standardScore,
                zScore = risk.zScore,
                tScore = risk.tScore,
                normCode = risk.normCode,
                normSelectionReason = risk.normSelectionReason
            )
        }
    }

    private fun applyDimensionRecode(recode: DimensionRecode, rawDimScore: BigDecimal, dimItems: List<EffectiveItem>): BigDecimal =
        when (recode.rule) {
            "SLEEP_DURATION_RECODE_0_3" -> {
                val bedMinutes = bedMinutes(recode, dimItems)
                bedMinutes?.let { applyRecode(BigDecimal(it), recode.bands) } ?: rawDimScore
            }
            "SLEEP_EFFICIENCY_RECODE_0_3" -> {
                val bedMinutes = bedMinutes(recode, dimItems)
                val sleepMinutes = recode.sleepQuestionId?.let { questionId ->
                    dimItems.firstOrNull { it.questionId == questionId }
                        ?.let { item -> item.answerValue ?: item.rawScore }
                        ?.toInt()
                }
                if (bedMinutes != null && sleepMinutes != null) {
                    val efficiency = if (bedMinutes > 0) {
                        BigDecimal(sleepMinutes).multiply(BigDecimal(100))
                            .divide(BigDecimal(bedMinutes), 4, RoundingMode.HALF_UP)
                    } else {
                        BigDecimal.ZERO
                    }
                    applyRecode(efficiency, recode.bands)
                } else {
                    rawDimScore
                }
            }
            else -> applyRecode(rawDimScore, recode.bands)
        }

    private fun bedMinutes(recode: DimensionRecode, dimItems: List<EffectiveItem>): Int? {
        val startMinutes = recode.startQuestionId?.let { questionId ->
            dimItems.firstOrNull { it.questionId == questionId }?.answerText?.let(::parseTimeMinutes)
        }
        val endMinutes = recode.endQuestionId?.let { questionId ->
            dimItems.firstOrNull { it.questionId == questionId }?.answerText?.let(::parseTimeMinutes)
        }
        if (startMinutes == null || endMinutes == null) return null
        return if (endMinutes >= startMinutes) endMinutes - startMinutes else endMinutes - startMinutes + 24 * 60
    }

    private fun applyRecode(score: BigDecimal, bands: List<RecodeBand>): BigDecimal =
        bands.firstOrNull { band -> score >= band.min && score <= band.max }
            ?.value
            ?: score

    private fun parseTimeMinutes(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun dimensionAggregationLabel(dimensionAggregation: String, missingAnswerPolicy: String): String {
        val averageBased = dimensionAggregation in setOf("AVERAGE", "WEIGHTED_AVERAGE")
        val weightedAverage = dimensionAggregation == "WEIGHTED_AVERAGE"
        return when {
            missingAnswerPolicy == "PRORATE" && weightedAverage -> "PRORATED_WEIGHTED_AVERAGE"
            missingAnswerPolicy == "PRORATE" && averageBased -> "PRORATED_AVERAGE"
            missingAnswerPolicy == "PRORATE" -> "PRORATED_SUM"
            weightedAverage -> "WEIGHTED_AVERAGE"
            averageBased -> "AVERAGE"
            else -> "SUM"
        }
    }

    private fun resolveGlobalRisk(scaleId: Long, totalScore: BigDecimal, normContext: NormMatchingContext?, localeCode: String): ScoreResult {
        val rule = resolveRiskRule(scaleId, null, totalScore, normContext, localeCode)
        return rule
            ?.let {
                ScoreResult(
                    totalScore = totalScore,
                    riskLevel = it.riskLevel ?: "NORMAL",
                    resultTitle = it.resultTitle,
                    resultDescription = it.resultDescription,
                    suggestionText = it.suggestionText,
                    dimensionScores = emptyList(),
                    scoreSource = it.scoreSource,
                    standardScore = it.standardScore,
                    zScore = it.zScore,
                    tScore = it.tScore,
                    normCode = it.normCode,
                    normSelectionReason = it.normSelectionReason,
                    resultRuleMatched = it.matchedRule
                )
            }
            ?: ScoreResult(
                totalScore = totalScore,
                riskLevel = "NORMAL",
                resultTitle = messages.getForLocale(localeCode, "score.default.title"),
                resultDescription = messages.getForLocale(localeCode, "score.default.description"),
                suggestionText = null,
                dimensionScores = emptyList()
            )
    }

    private fun resolveDimensionRisk(scaleId: Long, dimensionId: Long, score: BigDecimal, normContext: NormMatchingContext?, localeCode: String): RiskRuleMatch {
        return resolveRiskRule(scaleId, dimensionId, score, normContext, localeCode)
            ?: RiskRuleMatch(
                riskLevel = null,
                resultTitle = null,
                resultDescription = null,
                suggestionText = null,
                scoreSource = "RAW_SCORE",
                standardScore = null,
                zScore = null,
                tScore = null,
                normCode = null,
                normSelectionReason = null,
                matchedRule = false
            )
    }

    private fun resolveRiskRule(scaleId: Long, dimensionId: Long?, rawScore: BigDecimal, normContext: NormMatchingContext?, localeCode: String): RiskRuleMatch? {
        val sql: String
        val params: Map<String, Any?>
        if (dimensionId == null) {
            sql = """
                select rule.risk_level,
                       coalesce(translation.result_title, rule.result_title) as result_title,
                       coalesce(translation.result_description, rule.result_description) as result_description,
                       coalesce(translation.suggestion_text, rule.suggestion_text) as suggestion_text,
                       rule.score_source, rule.norm_code, rule.score_min, rule.score_max
                from psy_scale_result_rule rule
                left join psy_scale_result_rule_translation translation
                  on translation.result_rule_id = rule.id
                 and translation.locale_code = :localeCode
                 and translation.review_status = 'APPROVED'
                where rule.scale_id = :scaleId
                  and rule.dimension_id is null
                order by rule.score_min asc
            """.trimIndent()
            params = mapOf("scaleId" to scaleId, "localeCode" to localeCode)
        } else {
            sql = """
                select rule.risk_level,
                       coalesce(translation.result_title, rule.result_title) as result_title,
                       coalesce(translation.result_description, rule.result_description) as result_description,
                       coalesce(translation.suggestion_text, rule.suggestion_text) as suggestion_text,
                       rule.score_source, rule.norm_code, rule.score_min, rule.score_max
                from psy_scale_result_rule rule
                left join psy_scale_result_rule_translation translation
                  on translation.result_rule_id = rule.id
                 and translation.locale_code = :localeCode
                 and translation.review_status = 'APPROVED'
                where rule.scale_id = :scaleId
                  and rule.dimension_id = :dimensionId
                order by rule.score_min asc
            """.trimIndent()
            params = mapOf("scaleId" to scaleId, "dimensionId" to dimensionId, "localeCode" to localeCode)
        }
        val rows = jdbcTemplate.query(
            sql,
            params
        ) { rs, _ ->
            val scoreSource = rs.getString("score_source") ?: "RAW_SCORE"
            val normCode = rs.getString("norm_code")
            val normScore = when (scoreSource) {
                "Z_SCORE", "T_SCORE" -> loadNormScore(scaleId, dimensionId, normCode, rawScore, normContext)
                else -> null
            }
            val comparedScore = when (scoreSource) {
                "Z_SCORE" -> normScore?.zScore
                "T_SCORE" -> normScore?.tScore
                else -> rawScore
            } ?: return@query null
            if (comparedScore < rs.getBigDecimal("score_min") || comparedScore > rs.getBigDecimal("score_max")) {
                return@query null
            }
            RiskRuleMatch(
                riskLevel = rs.getString("risk_level"),
                resultTitle = rs.getString("result_title"),
                resultDescription = rs.getString("result_description"),
                suggestionText = rs.getString("suggestion_text"),
                scoreSource = scoreSource,
                standardScore = comparedScore.setScale(4, RoundingMode.HALF_UP),
                zScore = normScore?.zScore,
                tScore = normScore?.tScore,
                normCode = normScore?.normCode ?: normCode,
                normSelectionReason = normScore?.selectionReason,
                matchedRule = true
            )
        }.filterNotNull()
        return rows.firstOrNull()
    }

    private fun loadOptionMinMax(questionIds: List<Long>): Map<Long, Pair<BigDecimal, BigDecimal>> {
        val sql = """
            select question_id, min(score_value) as min_score, max(score_value) as max_score
            from psy_scale_option
            where question_id in (:questionIds)
            group by question_id
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("questionIds" to questionIds)) { rs, _ ->
            rs.getLong("question_id") to (rs.getBigDecimal("min_score") to rs.getBigDecimal("max_score"))
        }.toMap()
    }

    private fun loadNormScore(
        scaleId: Long,
        dimensionId: Long?,
        normCode: String?,
        rawScore: BigDecimal,
        normContext: NormMatchingContext?
    ): NormScore? {
        val preferredNormCode = normCode?.takeIf { it.isNotBlank() } ?: normContext?.preferredNormCode?.takeIf { it.isNotBlank() }
        val sql: String
        val params: Map<String, Any?>
        if (dimensionId == null) {
            sql = """
                select norm_code, applicable_target, age_min, age_max, gender, org_type,
                       mean_score, std_deviation, t_score_mean, t_score_std_deviation, sort_no
                from psy_scale_norm
                where scale_id = :scaleId
                  and dimension_id is null
                  and review_status = 'APPROVED'
                order by sort_no asc, id asc
            """.trimIndent()
            params = mapOf("scaleId" to scaleId)
        } else {
            sql = """
                select norm_code, applicable_target, age_min, age_max, gender, org_type,
                       mean_score, std_deviation, t_score_mean, t_score_std_deviation, sort_no
                from psy_scale_norm
                where scale_id = :scaleId
                  and dimension_id = :dimensionId
                  and review_status = 'APPROVED'
                order by sort_no asc, id asc
            """.trimIndent()
            params = mapOf("scaleId" to scaleId, "dimensionId" to dimensionId)
        }
        val candidates = jdbcTemplate.query(sql, params) { rs, _ ->
            NormCandidate(
                normCode = rs.getString("norm_code"),
                applicableTarget = rs.getString("applicable_target"),
                ageMin = rs.getObject("age_min", java.lang.Integer::class.java)?.toInt(),
                ageMax = rs.getObject("age_max", java.lang.Integer::class.java)?.toInt(),
                gender = rs.getString("gender"),
                orgType = rs.getString("org_type"),
                meanScore = rs.getBigDecimal("mean_score"),
                stdDeviation = rs.getBigDecimal("std_deviation"),
                tScoreMean = rs.getBigDecimal("t_score_mean"),
                tScoreStdDeviation = rs.getBigDecimal("t_score_std_deviation"),
                sortNo = rs.getInt("sort_no")
            )
        }
        val selected = candidates
            .asSequence()
            .filter { candidate -> preferredNormCode == null || candidate.normCode == preferredNormCode }
            .filter { candidate -> matchesNorm(candidate, normContext) }
            .sortedWith(
                compareByDescending<NormCandidate> { if (preferredNormCode != null && it.normCode == preferredNormCode) 1 else 0 }
                    .thenByDescending { normSpecificity(it) }
                    .thenBy { it.sortNo }
            )
            .firstOrNull()
            ?: return null
        val mean = selected.meanScore ?: return null
        val std = selected.stdDeviation ?: return null
        if (std.compareTo(BigDecimal.ZERO) == 0) {
            return null
        }
        val zScore = rawScore.subtract(mean).divide(std, 4, RoundingMode.HALF_UP)
        val tMean = selected.tScoreMean ?: BigDecimal("50")
        val tStd = selected.tScoreStdDeviation ?: BigDecimal("10")
        val tScore = tMean.add(zScore.multiply(tStd)).setScale(4, RoundingMode.HALF_UP)
        return NormScore(
            normCode = selected.normCode,
            zScore = zScore,
            tScore = tScore,
            selectionReason = buildString {
                append("preferred=").append(preferredNormCode ?: "none")
                append(";specificity=").append(normSpecificity(selected))
                append(";sortNo=").append(selected.sortNo)
            }
        )
    }

    private fun resolveHighRisk(scaleId: Long, items: List<QuestionScoreContext>, localeCode: String): HighRiskMatch? {
        if (items.isEmpty()) {
            return null
        }
        val sql = """
            select rule.rule_code, rule.question_id, rule.option_id, rule.score_threshold, rule.warning_level,
                   coalesce(translation.result_title, rule.result_title) as result_title,
                   coalesce(translation.result_description, rule.result_description) as result_description,
                   coalesce(translation.suggestion_text, rule.suggestion_text) as suggestion_text
            from psy_scale_high_risk_rule rule
            left join psy_scale_high_risk_rule_translation translation
              on translation.high_risk_rule_id = rule.id
             and translation.locale_code = :localeCode
             and translation.review_status = 'APPROVED'
            where rule.scale_id = :scaleId
              and rule.question_id in (:questionIds)
            order by rule.sort_no asc, rule.id asc
        """.trimIndent()
        val itemByQuestionId = items.associateBy { it.questionId }
        val rows = jdbcTemplate.query(sql, mapOf("scaleId" to scaleId, "questionIds" to items.map { it.questionId }.distinct(), "localeCode" to localeCode)) { rs, _ ->
            val questionId = rs.getLong("question_id")
            val item = itemByQuestionId[questionId] ?: return@query null
            val optionId = rs.getObject("option_id", java.lang.Long::class.java)?.toLong()
            val scoreThreshold = rs.getBigDecimal("score_threshold")
            val matched = when {
                optionId != null -> optionId in item.selectedOptionIds
                scoreThreshold != null -> item.answerValue?.let { it >= scoreThreshold } ?: (item.rawScore >= scoreThreshold)
                else -> false
            }
            if (!matched) {
                return@query null
            }
            HighRiskMatch(
                ruleCode = rs.getString("rule_code"),
                warningLevel = rs.getString("warning_level"),
                resultTitle = rs.getString("result_title"),
                resultDescription = rs.getString("result_description"),
                suggestionText = rs.getString("suggestion_text")
            )
        }.filterNotNull()
        return rows.maxByOrNull { riskLevelRank(it.warningLevel) }
    }

    private fun maxRiskLevel(left: String, right: String?): String =
        if (right == null || riskLevelRank(left) >= riskLevelRank(right)) left else right

    private fun riskLevelRank(level: String?): Int =
        when (level?.uppercase()) {
            "CRITICAL", "P0" -> 4
            "HIGH", "P1" -> 3
            "MODERATE", "MEDIUM", "ATTENTION", "P2" -> 2
            "LOW" -> 1
            else -> 0
        }

    private fun matchesNorm(candidate: NormCandidate, context: NormMatchingContext?): Boolean {
        if (context == null) {
            return candidate.applicableTarget.isNullOrBlank() &&
                candidate.ageMin == null &&
                candidate.ageMax == null &&
                candidate.gender.isNullOrBlank() &&
                candidate.orgType.isNullOrBlank()
        }
        val targetMatched = candidate.applicableTarget.isNullOrBlank() ||
            candidate.applicableTarget.equals(context.applicableTarget, ignoreCase = true)
        val ageMatched = when {
            candidate.ageMin == null && candidate.ageMax == null -> true
            context.age == null -> false
            candidate.ageMin != null && context.age < candidate.ageMin -> false
            candidate.ageMax != null && context.age > candidate.ageMax -> false
            else -> true
        }
        val genderMatched = candidate.gender.isNullOrBlank() ||
            (!context.gender.isNullOrBlank() && candidate.gender.equals(context.gender, ignoreCase = true))
        val orgMatched = candidate.orgType.isNullOrBlank() ||
            (!context.orgType.isNullOrBlank() && candidate.orgType.equals(context.orgType, ignoreCase = true))
        return targetMatched && ageMatched && genderMatched && orgMatched
    }

    private fun normSpecificity(candidate: NormCandidate): Int =
        listOf(candidate.applicableTarget, candidate.gender, candidate.orgType).count { !it.isNullOrBlank() } +
            listOf(candidate.ageMin, candidate.ageMax).count { it != null }

    private companion object {
        val SUPPORTED_ALGORITHMS = setOf("GENERIC_SCORE_CALCULATOR", "SCL90_PROFILE")
        val SUPPORTED_SCORE_METHODS = setOf("SIMPLE_SUM", "REVERSE_SUM", "WEIGHTED_SUM", "AVERAGE", "WEIGHTED_AVERAGE")
        val SUPPORTED_MISSING_POLICIES = setOf("REJECT", "ALLOW", "PRORATE")
        val AVERAGE_SCORE_METHODS = setOf("AVERAGE", "WEIGHTED_AVERAGE")
        val WEIGHTED_SCORE_METHODS = setOf("WEIGHTED_SUM", "WEIGHTED_AVERAGE")
        val SUPPORTED_DERIVED_METRICS = setOf("WHO5_PERCENTAGE_SCORE")
    }
}
