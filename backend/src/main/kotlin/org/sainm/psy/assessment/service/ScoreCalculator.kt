package org.sainm.psy.assessment.service

import org.sainm.psy.common.i18n.LocalizedMessages
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
    val answerValue: BigDecimal? = null
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
    val normCode: String? = null
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
    val highRiskTriggered: Boolean = false,
    val highRiskRuleCode: String? = null,
    val highRiskWarningLevel: String? = null
)

@Component
class ScoreCalculator(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val messages: LocalizedMessages
) {

    private data class EffectiveItem(val questionId: Long, val dimensionId: Long?, val effectiveScore: BigDecimal)
    private data class NormScore(val normCode: String, val zScore: BigDecimal, val tScore: BigDecimal)
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
        val normCode: String?
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
        normContext: NormMatchingContext? = null
    ): ScoreResult {
        val effectiveItems = applyScoreMethod(scoreMethod, items)
        val scoreSum = effectiveItems.fold(BigDecimal.ZERO) { acc, it -> acc + it.effectiveScore }
        val rawTotal = when (scoreMethod) {
            "AVERAGE" -> scoreSum.divide(BigDecimal(items.size.coerceAtLeast(1)), 8, RoundingMode.HALF_UP)
            "WEIGHTED_AVERAGE" -> {
                val weightSum = items.fold(BigDecimal.ZERO) { acc, it -> acc + it.weightValue }
                if (weightSum.compareTo(BigDecimal.ZERO) == 0) {
                    throw IllegalArgumentException("Weighted average requires a positive total weight")
                }
                scoreSum.divide(weightSum, 8, RoundingMode.HALF_UP)
            }
            else -> scoreSum
        }
        val totalScore = (rawTotal * scoreCoefficient).setScale(4, RoundingMode.HALF_UP)
        val dimensionScores = computeDimensionScores(scaleId, effectiveItems, normContext)
        val globalRisk = resolveGlobalRisk(scaleId, totalScore, normContext)
        val highRiskMatch = resolveHighRisk(scaleId, items)
        val finalRiskLevel = maxRiskLevel(globalRisk.riskLevel, highRiskMatch?.warningLevel)
        return globalRisk.copy(
            riskLevel = finalRiskLevel,
            resultTitle = highRiskMatch?.resultTitle ?: globalRisk.resultTitle,
            resultDescription = highRiskMatch?.resultDescription ?: globalRisk.resultDescription,
            suggestionText = highRiskMatch?.suggestionText ?: globalRisk.suggestionText,
            dimensionScores = dimensionScores,
            highRiskTriggered = highRiskMatch != null,
            highRiskRuleCode = highRiskMatch?.ruleCode,
            highRiskWarningLevel = highRiskMatch?.warningLevel
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
            EffectiveItem(item.questionId, item.dimensionId, effectiveScore)
        }
    }

    private fun computeDimensionScores(scaleId: Long, items: List<EffectiveItem>, normContext: NormMatchingContext?): List<DimensionScoreResult> {
        val byDimension = items.filter { it.dimensionId != null }.groupBy { it.dimensionId!! }
        if (byDimension.isEmpty()) return emptyList()
        return byDimension.map { (dimId, dimItems) ->
            val dimScore = dimItems.fold(BigDecimal.ZERO) { acc, it -> acc + it.effectiveScore }
                .divide(BigDecimal(dimItems.size), 4, RoundingMode.HALF_UP)
            val risk = resolveDimensionRisk(scaleId, dimId, dimScore, normContext)
            DimensionScoreResult(
                dimensionId = dimId,
                score = dimScore,
                riskLevel = risk.riskLevel,
                resultTitle = risk.resultTitle,
                scoreSource = risk.scoreSource,
                standardScore = risk.standardScore,
                zScore = risk.zScore,
                tScore = risk.tScore,
                normCode = risk.normCode
            )
        }
    }

    private fun resolveGlobalRisk(scaleId: Long, totalScore: BigDecimal, normContext: NormMatchingContext?): ScoreResult {
        val rule = resolveRiskRule(scaleId, null, totalScore, normContext)
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
                    normCode = it.normCode
                )
            }
            ?: ScoreResult(
                totalScore = totalScore,
                riskLevel = "NORMAL",
                resultTitle = messages.get("score.default.title"),
                resultDescription = messages.get("score.default.description"),
                suggestionText = null,
                dimensionScores = emptyList()
            )
    }

    private fun resolveDimensionRisk(scaleId: Long, dimensionId: Long, score: BigDecimal, normContext: NormMatchingContext?): RiskRuleMatch {
        return resolveRiskRule(scaleId, dimensionId, score, normContext)
            ?: RiskRuleMatch(
                riskLevel = null,
                resultTitle = null,
                resultDescription = null,
                suggestionText = null,
                scoreSource = "RAW_SCORE",
                standardScore = null,
                zScore = null,
                tScore = null,
                normCode = null
            )
    }

    private fun resolveRiskRule(scaleId: Long, dimensionId: Long?, rawScore: BigDecimal, normContext: NormMatchingContext?): RiskRuleMatch? {
        val sql: String
        val params: Map<String, Any?>
        if (dimensionId == null) {
            sql = """
                select risk_level, result_title, result_description, suggestion_text, score_source, norm_code, score_min, score_max
                from psy_scale_result_rule
                where scale_id = :scaleId
                  and dimension_id is null
                order by score_min asc
            """.trimIndent()
            params = mapOf("scaleId" to scaleId)
        } else {
            sql = """
                select risk_level, result_title, result_description, suggestion_text, score_source, norm_code, score_min, score_max
                from psy_scale_result_rule
                where scale_id = :scaleId
                  and dimension_id = :dimensionId
                order by score_min asc
            """.trimIndent()
            params = mapOf("scaleId" to scaleId, "dimensionId" to dimensionId)
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
                normCode = normScore?.normCode ?: normCode
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
            tScore = tScore
        )
    }

    private fun resolveHighRisk(scaleId: Long, items: List<QuestionScoreContext>): HighRiskMatch? {
        if (items.isEmpty()) {
            return null
        }
        val sql = """
            select rule_code, question_id, option_id, score_threshold, warning_level, result_title, result_description, suggestion_text
            from psy_scale_high_risk_rule
            where scale_id = :scaleId
              and question_id in (:questionIds)
            order by sort_no asc, id asc
        """.trimIndent()
        val itemByQuestionId = items.associateBy { it.questionId }
        val rows = jdbcTemplate.query(sql, mapOf("scaleId" to scaleId, "questionIds" to items.map { it.questionId }.distinct())) { rs, _ ->
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
            "MODERATE", "MEDIUM", "P2" -> 2
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
}
