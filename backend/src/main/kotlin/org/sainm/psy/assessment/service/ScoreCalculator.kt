package org.sainm.psy.assessment.service

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

data class QuestionScoreContext(
    val questionId: Long,
    val dimensionId: Long?,
    val reverseScoreFlag: Boolean,
    val weightValue: BigDecimal,
    val rawScore: BigDecimal
)

data class DimensionScoreResult(
    val dimensionId: Long,
    val score: BigDecimal,
    val riskLevel: String?,
    val resultTitle: String?
)

data class ScoreResult(
    val totalScore: BigDecimal,
    val riskLevel: String,
    val resultTitle: String?,
    val resultDescription: String?,
    val suggestionText: String?,
    val dimensionScores: List<DimensionScoreResult>
)

@Component
class ScoreCalculator(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    /**
     * Calculate scores for a submitted answer sheet.
     *
     * score_method controls per-question effective score:
     *   SIMPLE_SUM   – use raw option score_value as-is
     *   REVERSE_SUM  – flip reversed questions: min + max - raw
     *   WEIGHTED_SUM – multiply by question weight_value
     *
     * Dimension scores are always computed for questions that belong to a dimension.
     * Global total score is always computed for all questions regardless of dimension.
     */
    fun calculate(
        scaleId: Long,
        scoreMethod: String,
        scoreCoefficient: BigDecimal,
        items: List<QuestionScoreContext>
    ): ScoreResult {
        val effectiveItems = applyScoreMethod(scoreMethod, items)
        val rawTotal = effectiveItems.fold(BigDecimal.ZERO) { acc, it -> acc + it.effectiveScore }
        val totalScore = (rawTotal * scoreCoefficient).setScale(4, RoundingMode.HALF_UP)
        val dimensionScores = computeDimensionScores(scaleId, effectiveItems)
        val globalRisk = resolveGlobalRisk(scaleId, totalScore)
        return globalRisk.copy(dimensionScores = dimensionScores)
    }

    // ── per-question effective score ─────────────────────────────────────────

    private data class EffectiveItem(val questionId: Long, val dimensionId: Long?, val effectiveScore: BigDecimal)

    private fun applyScoreMethod(scoreMethod: String, items: List<QuestionScoreContext>): List<EffectiveItem> {
        return when (scoreMethod) {
            "REVERSE_SUM" -> {
                val reversedIds = items.filter { it.reverseScoreFlag }.map { it.questionId }
                val minMaxMap = if (reversedIds.isEmpty()) emptyMap() else loadOptionMinMax(reversedIds)
                items.map { item ->
                    val score = if (item.reverseScoreFlag) {
                        val (min, max) = minMaxMap[item.questionId] ?: (item.rawScore to item.rawScore)
                        min + max - item.rawScore
                    } else {
                        item.rawScore
                    }
                    EffectiveItem(item.questionId, item.dimensionId, score)
                }
            }
            "WEIGHTED_SUM" -> items.map {
                EffectiveItem(it.questionId, it.dimensionId, it.rawScore * it.weightValue)
            }
            else -> items.map { EffectiveItem(it.questionId, it.dimensionId, it.rawScore) }
        }
    }

    // ── dimension scores ─────────────────────────────────────────────────────

    private fun computeDimensionScores(scaleId: Long, items: List<EffectiveItem>): List<DimensionScoreResult> {
        val byDimension = items.filter { it.dimensionId != null }.groupBy { it.dimensionId!! }
        if (byDimension.isEmpty()) return emptyList()
        return byDimension.map { (dimId, dimItems) ->
            val dimScore = dimItems.fold(BigDecimal.ZERO) { acc, it -> acc + it.effectiveScore }
                .divide(BigDecimal(dimItems.size), 4, RoundingMode.HALF_UP)
            val risk = resolveDimensionRisk(scaleId, dimId, dimScore)
            DimensionScoreResult(
                dimensionId = dimId,
                score = dimScore,
                riskLevel = risk.first,
                resultTitle = risk.second
            )
        }
    }

    // ── risk resolution ──────────────────────────────────────────────────────

    private fun resolveGlobalRisk(scaleId: Long, totalScore: BigDecimal): ScoreResult {
        val sql = """
            select risk_level, result_title, result_description, suggestion_text
            from psy_scale_result_rule
            where scale_id = :scaleId
              and dimension_id is null
              and :totalScore between score_min and score_max
            order by score_min asc
            limit 1
        """.trimIndent()
        val rows = jdbcTemplate.query(sql, mapOf("scaleId" to scaleId, "totalScore" to totalScore)) { rs, _ ->
            ScoreResult(
                totalScore = totalScore,
                riskLevel = rs.getString("risk_level"),
                resultTitle = rs.getString("result_title"),
                resultDescription = rs.getString("result_description"),
                suggestionText = rs.getString("suggestion_text"),
                dimensionScores = emptyList()
            )
        }
        return rows.firstOrNull()
            ?: ScoreResult(totalScore, "NORMAL", "系统报告", "当前未命中风险规则，按正常状态处理。", null, emptyList())
    }

    private fun resolveDimensionRisk(scaleId: Long, dimensionId: Long, score: BigDecimal): Pair<String?, String?> {
        val sql = """
            select risk_level, result_title
            from psy_scale_result_rule
            where scale_id = :scaleId
              and dimension_id = :dimensionId
              and :score between score_min and score_max
            order by score_min asc
            limit 1
        """.trimIndent()
        val rows = jdbcTemplate.query(
            sql,
            mapOf("scaleId" to scaleId, "dimensionId" to dimensionId, "score" to score)
        ) { rs, _ -> rs.getString("risk_level") to rs.getString("result_title") }
        return rows.firstOrNull() ?: (null to null)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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
}
