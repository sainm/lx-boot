package org.sainm.psy.visualization.repository

import org.sainm.psy.visualization.domain.ChartPoint
import org.sainm.psy.visualization.domain.ScaleVisualizationConfig
import org.sainm.psy.visualization.domain.ScaleVisualizationConfigDraft
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class VisualizationRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findConfigs(scaleId: Long, viewScope: String? = null, enabledOnly: Boolean = false): List<ScaleVisualizationConfig> {
        val conditions = buildList {
            add("scale_id = :scaleId")
            if (viewScope != null) add("view_scope = :viewScope")
            if (enabledOnly) add("enabled = true")
        }
        val sql = """
            select id, scale_id, chart_type, chart_title, view_scope, data_source,
                   config_json::text as config_json, enabled, sort_no
            from psy_scale_visualization_config
            where ${conditions.joinToString(" and ")}
            order by sort_no asc, id asc
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("scaleId", scaleId)
            .addValue("viewScope", viewScope)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            ScaleVisualizationConfig(
                id = rs.getLong("id"),
                scaleId = rs.getLong("scale_id"),
                chartType = rs.getString("chart_type"),
                chartTitle = rs.getString("chart_title"),
                viewScope = rs.getString("view_scope"),
                dataSource = rs.getString("data_source"),
                configJson = rs.getString("config_json") ?: "{}",
                enabled = rs.getBoolean("enabled"),
                sortNo = rs.getInt("sort_no")
            )
        }
    }

    fun replaceConfigs(scaleId: Long, drafts: List<ScaleVisualizationConfigDraft>) {
        jdbcTemplate.update(
            "delete from psy_scale_visualization_config where scale_id = :scaleId",
            mapOf("scaleId" to scaleId)
        )
        if (drafts.isEmpty()) return
        val sql = """
            insert into psy_scale_visualization_config (
                scale_id, chart_type, chart_title, view_scope, data_source, config_json, enabled, sort_no
            ) values (
                :scaleId, :chartType, :chartTitle, :viewScope, :dataSource, cast(:configJson as jsonb), :enabled, :sortNo
            )
        """.trimIndent()
        jdbcTemplate.batchUpdate(
            sql,
            drafts.map { draft ->
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("chartType", draft.chartType.trim().uppercase())
                    .addValue("chartTitle", draft.chartTitle.trim())
                    .addValue("viewScope", draft.viewScope.trim().uppercase())
                    .addValue("dataSource", draft.dataSource.trim().uppercase())
                    .addValue("configJson", draft.configJson.ifBlank { "{}" })
                    .addValue("enabled", draft.enabled)
                    .addValue("sortNo", draft.sortNo)
            }.toTypedArray()
        )
    }

    fun copyConfigs(sourceScaleId: Long, newScaleId: Long) {
        jdbcTemplate.update(
            """
            insert into psy_scale_visualization_config (
                scale_id, chart_type, chart_title, view_scope, data_source, config_json, enabled, sort_no, created_at, updated_at
            )
            select :newScaleId, chart_type, chart_title, view_scope, data_source, config_json, enabled, sort_no, current_timestamp, current_timestamp
            from psy_scale_visualization_config
            where scale_id = :sourceScaleId
            """.trimIndent(),
            mapOf("sourceScaleId" to sourceScaleId, "newScaleId" to newScaleId)
        )
    }

    fun findReportDimensionPoints(resultId: Long): List<ChartPoint> {
        val sql = """
            select d.id as dimension_id, d.dimension_code, d.dimension_name, rd.dimension_score
            from psy_assessment_result_dimension rd
            join psy_scale_dimension d on d.id = rd.dimension_id
            where rd.result_id = :resultId
            order by d.sort_no asc, d.id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("resultId" to resultId)) { rs, _ ->
            ChartPoint(
                key = rs.getString("dimension_code") ?: rs.getLong("dimension_id").toString(),
                label = rs.getString("dimension_name"),
                value = rs.getBigDecimal("dimension_score")
            )
        }
    }

    fun findNormComparePoints(resultId: Long, scaleId: Long): List<ChartPoint> {
        val sql = """
            select d.dimension_code, d.dimension_name, rd.dimension_score, n.mean_score
            from psy_assessment_result_dimension rd
            join psy_scale_dimension d on d.id = rd.dimension_id
            left join psy_scale_norm n on n.scale_id = :scaleId and n.dimension_id = d.id
            where rd.result_id = :resultId
              and n.mean_score is not null
            order by d.sort_no asc, d.id asc, n.sort_no asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("resultId" to resultId, "scaleId" to scaleId)) { rs, _ ->
            listOf(
                ChartPoint(
                    key = "${rs.getString("dimension_code")}:USER",
                    label = rs.getString("dimension_name"),
                    value = rs.getBigDecimal("dimension_score"),
                    series = "USER"
                ),
                ChartPoint(
                    key = "${rs.getString("dimension_code")}:NORM",
                    label = rs.getString("dimension_name"),
                    value = rs.getBigDecimal("mean_score"),
                    series = "NORM"
                )
            )
        }.flatten()
    }

    fun hasTable(): Boolean =
        runCatching {
            jdbcTemplate.queryForObject(
                "select count(1) from information_schema.tables where table_name = 'psy_scale_visualization_config'",
                emptyMap<String, Any>(),
                Long::class.java
            )
        }.getOrDefault(0L) == 1L

    fun one(value: Number): BigDecimal = BigDecimal.valueOf(value.toLong())
}
