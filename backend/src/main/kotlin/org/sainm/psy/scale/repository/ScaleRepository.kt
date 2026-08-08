package org.sainm.psy.scale.repository

import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.CreateScaleVersionRequest
import org.sainm.psy.scale.api.BatchCreateResponse
import org.sainm.psy.scale.api.ScaleListQuery
import org.sainm.psy.scale.api.UpdateScaleBasicRequest
import org.sainm.psy.scale.api.UpdateScaleDimensionRequest
import org.sainm.psy.scale.api.UpdateScaleOptionRequest
import org.sainm.psy.scale.api.UpdateScaleQuestionRequest
import org.sainm.psy.common.jdbc.addIfNotNull
import org.sainm.psy.common.jdbc.params
import org.sainm.psy.common.jdbc.whereClause
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleDimension
import org.sainm.psy.scale.domain.ScaleDimensionDraft
import org.sainm.psy.scale.domain.ScaleQuestion
import org.sainm.psy.scale.domain.ScaleQuestionOption
import org.sainm.psy.scale.domain.ScaleQuestionDraft
import org.sainm.psy.scale.domain.ScaleNorm
import org.sainm.psy.scale.domain.ScaleNormDraft
import org.sainm.psy.scale.domain.ScaleResultRule
import org.sainm.psy.scale.domain.ScaleResultRuleDraft
import org.sainm.psy.scale.domain.ScaleHighRiskRuleDraft
import org.sainm.psy.scale.domain.ScaleSummary
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class ScaleRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findPage(query: ScaleListQuery, tenantId: Long? = null): Pair<List<ScaleSummary>, Long> {
        val offset = (query.page - 1).coerceAtLeast(0) * query.size
        val scaleName = query.scaleName?.trim()?.takeIf(String::isNotEmpty)?.let { "%$it%" }
        val status = query.status?.trim()?.takeIf(String::isNotEmpty)
        val params = params {
            addValue("limit", query.size)
            addValue("offset", offset)
            addIfNotNull("scaleName", scaleName)
            addIfNotNull("status", status)
            addIfNotNull("tenantId", tenantId)
        }

        val whereClause = whereClause(
            scaleName?.let { "scale_name like :scaleName" },
            status?.let { "status = :status" },
            tenantId?.let { "tenant_id = :tenantId" }
        )

        val listSql = """
            select id, scale_code, scale_name, applicable_target, version_no,
                   version_group_id, current_version_flag, status,
                   score_method, score_coefficient, norm_strategy, norm_default_group,
                   high_risk_warning_enabled, anonymous_supported, created_at
            from psy_scale
            $whereClause
            order by id desc
            limit :limit offset :offset
        """.trimIndent()

        val countSql = """
            select count(1)
            from psy_scale
            $whereClause
        """.trimIndent()

        val list = jdbcTemplate.query(listSql, params, scaleSummaryRowMapper)
        val total = jdbcTemplate.queryForObject(countSql, params, Long::class.java) ?: 0L
        return list to total
    }

    fun findVersionsByGroupId(versionGroupId: Long): List<ScaleSummary> {
        val sql = """
            select id, scale_code, scale_name, applicable_target, version_no,
                   version_group_id, current_version_flag, status,
                   score_method, score_coefficient, norm_strategy, norm_default_group,
                   high_risk_warning_enabled, anonymous_supported, created_at
            from psy_scale
            where coalesce(version_group_id, id) = :versionGroupId
            order by created_at desc, id desc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("versionGroupId" to versionGroupId), scaleSummaryRowMapper)
    }

    fun create(request: CreateScaleRequest, createdBy: Long): Long {
        val now = LocalDateTime.now()
        val sql = """
            insert into psy_scale (
                tenant_id,
                scale_code,
                scale_name,
                description,
                applicable_target,
                version_no,
                version_group_id,
                current_version_flag,
                status,
                score_method,
                score_coefficient,
                norm_strategy,
                norm_default_group,
                high_risk_warning_enabled,
                anonymous_supported,
                report_template,
                created_by,
                created_at,
                updated_by,
                updated_at
            ) values (
                (select tenant_id from sys_user where id = :createdBy),
                :scaleCode,
                :scaleName,
                :description,
                :applicableTarget,
                :versionNo,
                null,
                true,
                :status,
                :scoreMethod,
                :scoreCoefficient,
                :normStrategy,
                :normDefaultGroup,
                :highRiskWarningEnabled,
                :anonymousSupported,
                :reportTemplate,
                :createdBy,
                :createdAt,
                :updatedBy,
                :updatedAt
            )
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("scaleCode", request.scaleCode.trim())
            .addValue("scaleName", request.scaleName.trim())
            .addValue("description", request.description)
            .addValue("applicableTarget", request.applicableTarget)
            .addValue("versionNo", request.versionNo)
            .addValue("status", "DRAFT")
            .addValue("scoreMethod", request.scoreMethod.trim().uppercase())
            .addValue("scoreCoefficient", request.scoreCoefficient)
            .addValue("normStrategy", "RAW_SCORE")
            .addValue("normDefaultGroup", null)
            .addValue("highRiskWarningEnabled", false)
            .addValue("anonymousSupported", request.anonymousSupported)
            .addValue("reportTemplate", request.reportTemplate)
            .addValue("createdBy", createdBy)
            .addValue("createdAt", Timestamp.valueOf(now))
            .addValue("updatedBy", createdBy)
            .addValue("updatedAt", Timestamp.valueOf(now))

        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(sql, params, keyHolder, arrayOf("id"))
        val scaleId = keyHolder.key?.toLong() ?: error("failed to create scale")
        jdbcTemplate.update(
            """
            update psy_scale
            set version_group_id = :scaleId
            where id = :scaleId
            """.trimIndent(),
            mapOf("scaleId" to scaleId)
        )
        return scaleId
    }

    fun createVersionFrom(sourceScaleId: Long, request: CreateScaleVersionRequest, createdBy: Long): Long {
        val source = findDetailById(sourceScaleId) ?: error("source scale not found")
        val now = Timestamp.valueOf(LocalDateTime.now())
        val versionGroupId = source.versionGroupId ?: source.id
        val insertScaleSql = """
            insert into psy_scale (
                tenant_id,
                scale_code,
                scale_name,
                description,
                applicable_target,
                version_no,
                version_group_id,
                current_version_flag,
                status,
                score_method,
                score_coefficient,
                norm_strategy,
                norm_default_group,
                high_risk_warning_enabled,
                anonymous_supported,
                report_template,
                created_by,
                created_at,
                updated_by,
                updated_at
            ) values (
                (select tenant_id from psy_scale where id = :sourceScaleId),
                :scaleCode,
                :scaleName,
                :description,
                :applicableTarget,
                :versionNo,
                :versionGroupId,
                false,
                'DRAFT',
                :scoreMethod,
                :scoreCoefficient,
                :normStrategy,
                :normDefaultGroup,
                :highRiskWarningEnabled,
                :anonymousSupported,
                :reportTemplate,
                :createdBy,
                :createdAt,
                :updatedBy,
                :updatedAt
            )
        """.trimIndent()
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            insertScaleSql,
            MapSqlParameterSource()
                .addValue("sourceScaleId", sourceScaleId)
                .addValue("scaleCode", source.scaleCode)
                .addValue("scaleName", request.scaleName?.trim()?.takeIf { it.isNotBlank() } ?: source.scaleName)
                .addValue("description", request.description ?: source.description)
                .addValue("applicableTarget", source.applicableTarget)
                .addValue("versionNo", request.versionNo)
                .addValue("versionGroupId", versionGroupId)
                .addValue("scoreMethod", source.scoreMethod)
                .addValue("scoreCoefficient", source.scoreCoefficient)
                .addValue("normStrategy", source.normStrategy)
                .addValue("normDefaultGroup", source.normDefaultGroup)
                .addValue("highRiskWarningEnabled", source.highRiskWarningEnabled)
                .addValue("anonymousSupported", source.anonymousSupported)
                .addValue("reportTemplate", source.reportTemplate)
                .addValue("createdBy", createdBy)
                .addValue("createdAt", now)
                .addValue("updatedBy", createdBy)
                .addValue("updatedAt", now),
            keyHolder,
            arrayOf("id")
        )
        val newScaleId = keyHolder.key?.toLong() ?: error("failed to create scale version")
        copyScaleStructure(source.id, newScaleId, now)
        return newScaleId
    }

    fun existsByScaleCode(scaleCode: String, tenantId: Long? = null): Boolean {
        val sql = """
            select count(1)
            from psy_scale
            where scale_code = :scaleCode
              and ${if (tenantId == null) "tenant_id is null" else "tenant_id = :tenantId"}
        """.trimIndent()
        val params = mapOf("scaleCode" to scaleCode, "tenantId" to tenantId)
        return (jdbcTemplate.queryForObject(sql, params, Long::class.java) ?: 0L) > 0
    }

    fun existsByVersionGroupAndVersion(versionGroupId: Long, versionNo: String): Boolean {
        val sql = """
            select count(1)
            from psy_scale
            where coalesce(version_group_id, id) = :versionGroupId
              and version_no = :versionNo
        """.trimIndent()
        return (jdbcTemplate.queryForObject(sql, mapOf("versionGroupId" to versionGroupId, "versionNo" to versionNo), Long::class.java) ?: 0L) > 0
    }

    fun existsById(id: Long, tenantId: Long? = null): Boolean {
        val sql = """
            select count(1) from psy_scale
            where id = :id
              ${if (tenantId == null) "" else "and tenant_id = :tenantId"}
        """.trimIndent()
        return (jdbcTemplate.queryForObject(sql, mapOf("id" to id, "tenantId" to tenantId), Long::class.java) ?: 0L) > 0
    }

    fun isInUse(scaleId: Long): Boolean {
        val sql = """
            select exists (
                select 1 from psy_assessment_task where scale_id = :scaleId
                union all
                select 1 from psy_assessment_answer_sheet where scale_id = :scaleId
                union all
                select 1 from psy_scale_import_job where created_scale_id = :scaleId
            )
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, mapOf("scaleId" to scaleId), Boolean::class.java) ?: false
    }

    fun deleteDraft(scaleId: Long): Int {
        val params = mapOf("scaleId" to scaleId)
        jdbcTemplate.update("delete from psy_scale_high_risk_rule where scale_id = :scaleId", params)
        jdbcTemplate.update(
            """
            delete from psy_scale_option
            where question_id in (
                select id from psy_scale_question where scale_id = :scaleId
            )
            """.trimIndent(),
            params
        )
        jdbcTemplate.update("delete from psy_scale_result_rule where scale_id = :scaleId", params)
        jdbcTemplate.update("delete from psy_scale_norm where scale_id = :scaleId", params)
        jdbcTemplate.update("delete from psy_scale_visualization_config where scale_id = :scaleId", params)
        jdbcTemplate.update("delete from psy_scale_question where scale_id = :scaleId", params)
        jdbcTemplate.update("delete from psy_scale_dimension where scale_id = :scaleId", params)
        return jdbcTemplate.update(
            "delete from psy_scale where id = :scaleId and status = 'DRAFT'",
            params
        )
    }

    fun publishVersion(scaleId: Long, versionGroupId: Long, updatedBy: Long): Boolean {
        val now = Timestamp.valueOf(LocalDateTime.now())
        jdbcTemplate.update(
            """
            update psy_scale
            set current_version_flag = false,
                updated_by = :updatedBy,
                updated_at = :updatedAt
            where coalesce(version_group_id, id) = :versionGroupId
              and id <> :scaleId
            """.trimIndent(),
            mapOf(
                "scaleId" to scaleId,
                "versionGroupId" to versionGroupId,
                "updatedBy" to updatedBy,
                "updatedAt" to now
            )
        )
        val updated = jdbcTemplate.update(
            """
            update psy_scale
            set status = 'PUBLISHED',
                version_group_id = coalesce(version_group_id, id),
                current_version_flag = true,
                updated_by = :updatedBy,
                updated_at = :updatedAt
            where id = :scaleId
              and coalesce(version_group_id, id) = :versionGroupId
            """.trimIndent(),
            mapOf(
                "scaleId" to scaleId,
                "versionGroupId" to versionGroupId,
                "updatedBy" to updatedBy,
                "updatedAt" to now
            )
        )
        return updated > 0
    }

    fun updateBasic(scaleId: Long, request: UpdateScaleBasicRequest, updatedBy: Long): Boolean {
        val updated = jdbcTemplate.update(
            """
            update psy_scale
            set scale_name = :scaleName,
                description = :description,
                applicable_target = :applicableTarget,
                anonymous_supported = :anonymousSupported,
                report_template = :reportTemplate,
                updated_by = :updatedBy,
                updated_at = :updatedAt
            where id = :scaleId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scaleId", scaleId)
                .addValue("scaleName", request.scaleName.trim())
                .addValue("description", request.description?.trim()?.takeIf { it.isNotBlank() })
                .addValue("applicableTarget", request.applicableTarget?.trim()?.takeIf { it.isNotBlank() })
                .addValue("anonymousSupported", request.anonymousSupported)
                .addValue("reportTemplate", request.reportTemplate?.trim()?.takeIf { it.isNotBlank() })
                .addValue("updatedBy", updatedBy)
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
        return updated > 0
    }

    fun updateDimension(scaleId: Long, dimensionId: Long, request: UpdateScaleDimensionRequest): Boolean {
        val updated = jdbcTemplate.update(
            """
            update psy_scale_dimension
            set dimension_name = :dimensionName,
                description = :description,
                sort_no = :sortNo,
                updated_at = :updatedAt
            where id = :dimensionId
              and scale_id = :scaleId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scaleId", scaleId)
                .addValue("dimensionId", dimensionId)
                .addValue("dimensionName", request.dimensionName.trim())
                .addValue("description", request.description?.trim()?.takeIf { it.isNotBlank() })
                .addValue("sortNo", request.sortNo)
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
        return updated > 0
    }

    fun updateQuestion(scaleId: Long, questionId: Long, request: UpdateScaleQuestionRequest): Boolean {
        val updated = jdbcTemplate.update(
            """
            update psy_scale_question
            set dimension_id = :dimensionId,
                question_title = :questionTitle,
                required_flag = :requiredFlag,
                reverse_score_flag = :reverseScoreFlag,
                weight_value = :weightValue,
                sort_no = :sortNo,
                updated_at = :updatedAt
            where id = :questionId
              and scale_id = :scaleId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scaleId", scaleId)
                .addValue("questionId", questionId)
                .addValue("dimensionId", request.dimensionId)
                .addValue("questionTitle", request.questionTitle.trim())
                .addValue("requiredFlag", request.requiredFlag)
                .addValue("reverseScoreFlag", request.reverseScoreFlag)
                .addValue("weightValue", request.weightValue)
                .addValue("sortNo", request.sortNo)
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
        return updated > 0
    }

    fun updateOption(scaleId: Long, optionId: Long, request: UpdateScaleOptionRequest): Boolean {
        val updated = jdbcTemplate.update(
            """
            update psy_scale_option option
            set option_label = :optionLabel,
                score_value = :scoreValue,
                exclusive_flag = :exclusiveFlag,
                option_group_code = :optionGroupCode,
                sort_no = :sortNo,
                updated_at = :updatedAt
            from psy_scale_question question
            where option.id = :optionId
              and option.question_id = question.id
              and question.scale_id = :scaleId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scaleId", scaleId)
                .addValue("optionId", optionId)
                .addValue("optionLabel", request.optionLabel.trim())
                .addValue("scoreValue", request.scoreValue)
                .addValue("exclusiveFlag", request.exclusiveFlag)
                .addValue("optionGroupCode", request.optionGroupCode?.trim()?.takeIf { it.isNotBlank() })
                .addValue("sortNo", request.sortNo)
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
        return updated > 0
    }

    fun findDimensionIdsByScaleId(scaleId: Long): Set<Long> {
        val sql = "select id from psy_scale_dimension where scale_id = :scaleId"
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ -> rs.getLong("id") }.toSet()
    }

    fun findDimensionCodeIdMapByScaleId(scaleId: Long): Map<String, Long> {
        val sql = "select dimension_code, id from psy_scale_dimension where scale_id = :scaleId"
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            rs.getString("dimension_code") to rs.getLong("id")
        }.toMap()
    }

    fun createDimensions(scaleId: Long, dimensions: List<ScaleDimensionDraft>): BatchCreateResponse {
        val createdIds = dimensions.map { dimension ->
            val sql = """
                insert into psy_scale_dimension (
                    scale_id, dimension_code, dimension_name, description, sort_no, created_at, updated_at
                ) values (
                    :scaleId, :dimensionCode, :dimensionName, :description, :sortNo, :createdAt, :updatedAt
                )
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            val keyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                sql,
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("dimensionCode", dimension.dimensionCode.trim())
                    .addValue("dimensionName", dimension.dimensionName.trim())
                    .addValue("description", dimension.description)
                    .addValue("sortNo", dimension.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                keyHolder,
                arrayOf("id")
            )
            keyHolder.key?.toLong() ?: error("failed to create scale dimension")
        }
        return BatchCreateResponse(createdIds = createdIds)
    }

    fun updateScaleAdvancedConfig(scaleId: Long, normStrategy: String, normDefaultGroup: String?, highRiskWarningEnabled: Boolean) {
        val now = Timestamp.valueOf(LocalDateTime.now())
        jdbcTemplate.update(
            """
            update psy_scale
            set norm_strategy = :normStrategy,
                norm_default_group = :normDefaultGroup,
                high_risk_warning_enabled = :highRiskWarningEnabled,
                updated_at = :updatedAt
            where id = :scaleId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scaleId", scaleId)
                .addValue("normStrategy", normStrategy)
                .addValue("normDefaultGroup", normDefaultGroup)
                .addValue("highRiskWarningEnabled", highRiskWarningEnabled)
                .addValue("updatedAt", now)
        )
    }

    fun createQuestions(scaleId: Long, questions: List<ScaleQuestionDraft>): BatchCreateResponse {
        val createdIds = mutableListOf<Long>()
        questions.forEach { question ->
            val questionSql = """
                insert into psy_scale_question (
                    scale_id, dimension_id, question_no, question_title, question_type,
                    required_flag, reverse_score_flag, weight_value, option_selection_limit,
                    slider_min, slider_max, slider_step, text_input_enabled, text_input_placeholder,
                    matrix_group_code, row_code, column_code, sort_no, created_at, updated_at
                ) values (
                    :scaleId, :dimensionId, :questionNo, :questionTitle, :questionType,
                    :requiredFlag, :reverseScoreFlag, :weightValue, :optionSelectionLimit,
                    :sliderMin, :sliderMax, :sliderStep, :textInputEnabled, :textInputPlaceholder,
                    :matrixGroupCode, :rowCode, :columnCode, :sortNo, :createdAt, :updatedAt
                )
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            val questionKeyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                questionSql,
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("dimensionId", question.dimensionId)
                    .addValue("questionNo", question.questionNo)
                    .addValue("questionTitle", question.questionTitle.trim())
                    .addValue("questionType", question.questionType.trim().uppercase())
                    .addValue("requiredFlag", question.requiredFlag)
                    .addValue("reverseScoreFlag", question.reverseScoreFlag)
                    .addValue("weightValue", question.weightValue)
                    .addValue("optionSelectionLimit", question.optionSelectionLimit)
                    .addValue("sliderMin", question.sliderMin)
                    .addValue("sliderMax", question.sliderMax)
                    .addValue("sliderStep", question.sliderStep)
                    .addValue("textInputEnabled", question.textInputEnabled)
                    .addValue("textInputPlaceholder", question.textInputPlaceholder)
                    .addValue("matrixGroupCode", question.matrixGroupCode)
                    .addValue("rowCode", question.rowCode)
                    .addValue("columnCode", question.columnCode)
                    .addValue("sortNo", question.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                questionKeyHolder,
                arrayOf("id")
            )
            val questionId = questionKeyHolder.key?.toLong() ?: error("failed to create scale question")
            createdIds.add(questionId)
            if (question.options.isNotEmpty()) {
                val optionSql = """
                    insert into psy_scale_option (
                        question_id, option_code, option_label, score_value, exclusive_flag, option_group_code, sort_no, created_at, updated_at
                    ) values (
                        :questionId, :optionCode, :optionLabel, :scoreValue, :exclusiveFlag, :optionGroupCode, :sortNo, :createdAt, :updatedAt
                    )
                """.trimIndent()
                val optionNow = Timestamp.valueOf(LocalDateTime.now())
                val optionParams = question.options.map { option ->
                    MapSqlParameterSource()
                        .addValue("questionId", questionId)
                        .addValue("optionCode", option.optionCode.trim())
                        .addValue("optionLabel", option.optionLabel.trim())
                        .addValue("scoreValue", option.scoreValue)
                        .addValue("exclusiveFlag", option.exclusiveFlag)
                        .addValue("optionGroupCode", option.optionGroupCode)
                        .addValue("sortNo", option.sortNo)
                        .addValue("createdAt", optionNow)
                        .addValue("updatedAt", optionNow)
                }.toTypedArray()
                jdbcTemplate.batchUpdate(optionSql, optionParams)
            }
        }
        return BatchCreateResponse(createdIds = createdIds)
    }

    fun createResultRules(scaleId: Long, resultRules: List<ScaleResultRuleDraft>): BatchCreateResponse {
        val createdIds = resultRules.map { rule ->
            val sql = """
                insert into psy_scale_result_rule (
                    scale_id, dimension_id, risk_level, score_min, score_max,
                    score_source, norm_code, result_title, result_description, suggestion_text, created_at, updated_at
                ) values (
                    :scaleId, :dimensionId, :riskLevel, :scoreMin, :scoreMax,
                    :scoreSource, :normCode, :resultTitle, :resultDescription, :suggestionText, :createdAt, :updatedAt
                )
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            val keyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                sql,
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("dimensionId", rule.dimensionId)
                    .addValue("riskLevel", rule.riskLevel.trim().uppercase())
                    .addValue("scoreMin", rule.scoreMin)
                    .addValue("scoreMax", rule.scoreMax)
                    .addValue("scoreSource", rule.scoreSource.trim().uppercase())
                    .addValue("normCode", rule.normCode)
                    .addValue("resultTitle", rule.resultTitle)
                    .addValue("resultDescription", rule.resultDescription)
                    .addValue("suggestionText", rule.suggestionText)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                keyHolder,
                arrayOf("id")
            )
            keyHolder.key?.toLong() ?: error("failed to create scale result rule")
        }
        return BatchCreateResponse(createdIds = createdIds)
    }

    fun createNorms(scaleId: Long, norms: List<ScaleNormDraft>): BatchCreateResponse {
        val createdIds = norms.map { norm ->
            val sql = """
                insert into psy_scale_norm (
                    scale_id, norm_code, norm_name, dimension_id, applicable_target, age_min, age_max,
                    gender, org_type, mean_score, std_deviation, t_score_mean, t_score_std_deviation,
                    sort_no, created_at, updated_at
                ) values (
                    :scaleId, :normCode, :normName, :dimensionId, :applicableTarget, :ageMin, :ageMax,
                    :gender, :orgType, :meanScore, :stdDeviation, :tScoreMean, :tScoreStdDeviation,
                    :sortNo, :createdAt, :updatedAt
                )
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            val keyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                sql,
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("normCode", norm.normCode)
                    .addValue("normName", norm.normName)
                    .addValue("dimensionId", norm.dimensionId)
                    .addValue("applicableTarget", norm.applicableTarget)
                    .addValue("ageMin", norm.ageMin)
                    .addValue("ageMax", norm.ageMax)
                    .addValue("gender", norm.gender)
                    .addValue("orgType", norm.orgType)
                    .addValue("meanScore", norm.meanScore)
                    .addValue("stdDeviation", norm.stdDeviation)
                    .addValue("tScoreMean", norm.tScoreMean)
                    .addValue("tScoreStdDeviation", norm.tScoreStdDeviation)
                    .addValue("sortNo", norm.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                keyHolder,
                arrayOf("id")
            )
            keyHolder.key?.toLong() ?: error("failed to create scale norm")
        }
        return BatchCreateResponse(createdIds = createdIds)
    }

    fun createHighRiskRules(scaleId: Long, rules: List<ScaleHighRiskRuleDraft>): BatchCreateResponse {
        val createdIds = rules.map { rule ->
            val sql = """
                insert into psy_scale_high_risk_rule (
                    scale_id, rule_code, question_id, option_id, score_threshold, warning_level,
                    result_title, result_description, suggestion_text, sort_no, created_at, updated_at
                ) values (
                    :scaleId, :ruleCode, :questionId, :optionId, :scoreThreshold, :warningLevel,
                    :resultTitle, :resultDescription, :suggestionText, :sortNo, :createdAt, :updatedAt
                )
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            val keyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                sql,
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("ruleCode", rule.ruleCode)
                    .addValue("questionId", rule.questionId)
                    .addValue("optionId", rule.optionId)
                    .addValue("scoreThreshold", rule.scoreThreshold)
                    .addValue("warningLevel", rule.warningLevel)
                    .addValue("resultTitle", rule.resultTitle)
                    .addValue("resultDescription", rule.resultDescription)
                    .addValue("suggestionText", rule.suggestionText)
                    .addValue("sortNo", rule.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                keyHolder,
                arrayOf("id")
            )
            keyHolder.key?.toLong() ?: error("failed to create scale high risk rule")
        }
        return BatchCreateResponse(createdIds = createdIds)
    }

    fun findQuestionNoIdMapByScaleId(scaleId: Long): Map<Int, Long> =
        jdbcTemplate.query(
            "select question_no, id from psy_scale_question where scale_id = :scaleId",
            mapOf("scaleId" to scaleId)
        ) { rs, _ -> rs.getInt("question_no") to rs.getLong("id") }.toMap()

    fun findOptionIdMapByScaleId(scaleId: Long): Map<Pair<Int, String>, Long> {
        val sql = """
            select q.question_no, o.option_code, o.id
            from psy_scale_question q
            join psy_scale_option o on o.question_id = q.id
            where q.scale_id = :scaleId
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            (rs.getInt("question_no") to rs.getString("option_code")) to rs.getLong("id")
        }.toMap()
    }

    fun findDetailById(id: Long): ScaleDetail? {
        val sql = """
            select id, scale_code, scale_name, description, applicable_target, version_no,
                   version_group_id, current_version_flag, status,
                   score_method, score_coefficient, norm_strategy, norm_default_group,
                   high_risk_warning_enabled, anonymous_supported, report_template,
                   created_by, created_at, updated_by, updated_at, tenant_id
            from psy_scale
            where id = :id
        """.trimIndent()
        val rows = jdbcTemplate.query(sql, mapOf("id" to id)) { rs, _ ->
            ScaleDetail(
                id = rs.getLong("id"),
                scaleCode = rs.getString("scale_code"),
                scaleName = rs.getString("scale_name"),
                description = rs.getString("description"),
                applicableTarget = rs.getString("applicable_target"),
                versionNo = rs.getString("version_no"),
                versionGroupId = rs.getObject("version_group_id", java.lang.Long::class.java)?.toLong(),
                currentVersionFlag = rs.getBoolean("current_version_flag"),
                status = rs.getString("status"),
                scoreMethod = rs.getString("score_method"),
                scoreCoefficient = rs.getBigDecimal("score_coefficient"),
                normStrategy = rs.getString("norm_strategy"),
                normDefaultGroup = rs.getString("norm_default_group"),
                highRiskWarningEnabled = rs.getBoolean("high_risk_warning_enabled"),
                anonymousSupported = rs.getBoolean("anonymous_supported"),
                reportTemplate = rs.getString("report_template"),
                createdBy = rs.getObject("created_by", java.lang.Long::class.java)?.toLong(),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedBy = rs.getObject("updated_by", java.lang.Long::class.java)?.toLong(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
                tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong(),
                dimensions = emptyList(),
                questions = emptyList(),
                resultRules = emptyList(),
                norms = emptyList()
            )
        }
        val detail = rows.firstOrNull() ?: return null
        return detail.copy(
            dimensions = findDimensionsByScaleId(id),
            questions = findQuestionsByScaleId(id),
            resultRules = findResultRulesByScaleId(id),
            norms = findNormsByScaleId(id)
        )
    }

    private fun copyScaleStructure(sourceScaleId: Long, newScaleId: Long, now: Timestamp) {
        val dimensionIdMap = mutableMapOf<Long, Long>()
        findDimensionsByScaleId(sourceScaleId).forEach { dimension ->
            val keyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                """
                insert into psy_scale_dimension (
                    scale_id, dimension_code, dimension_name, description, sort_no, created_at, updated_at
                ) values (
                    :scaleId, :dimensionCode, :dimensionName, :description, :sortNo, :createdAt, :updatedAt
                )
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("scaleId", newScaleId)
                    .addValue("dimensionCode", dimension.dimensionCode)
                    .addValue("dimensionName", dimension.dimensionName)
                    .addValue("description", dimension.description)
                    .addValue("sortNo", dimension.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                keyHolder,
                arrayOf("id")
            )
            dimensionIdMap[dimension.id] = keyHolder.key?.toLong() ?: error("failed to copy scale dimension")
        }

        findQuestionsByScaleId(sourceScaleId).forEach { question ->
            val questionKeyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                """
                insert into psy_scale_question (
                    scale_id, dimension_id, question_no, question_title, question_type,
                    required_flag, reverse_score_flag, weight_value, option_selection_limit,
                    slider_min, slider_max, slider_step, text_input_enabled, text_input_placeholder,
                    matrix_group_code, row_code, column_code, sort_no, created_at, updated_at
                ) values (
                    :scaleId, :dimensionId, :questionNo, :questionTitle, :questionType,
                    :requiredFlag, :reverseScoreFlag, :weightValue, :optionSelectionLimit,
                    :sliderMin, :sliderMax, :sliderStep, :textInputEnabled, :textInputPlaceholder,
                    :matrixGroupCode, :rowCode, :columnCode, :sortNo, :createdAt, :updatedAt
                )
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("scaleId", newScaleId)
                    .addValue("dimensionId", question.dimensionId?.let { dimensionIdMap[it] })
                    .addValue("questionNo", question.questionNo)
                    .addValue("questionTitle", question.questionTitle)
                    .addValue("questionType", question.questionType)
                    .addValue("requiredFlag", question.requiredFlag)
                    .addValue("reverseScoreFlag", question.reverseScoreFlag)
                    .addValue("weightValue", question.weightValue)
                    .addValue("optionSelectionLimit", question.optionSelectionLimit)
                    .addValue("sliderMin", question.sliderMin)
                    .addValue("sliderMax", question.sliderMax)
                    .addValue("sliderStep", question.sliderStep)
                    .addValue("textInputEnabled", question.textInputEnabled)
                    .addValue("textInputPlaceholder", question.textInputPlaceholder)
                    .addValue("matrixGroupCode", question.matrixGroupCode)
                    .addValue("rowCode", question.rowCode)
                    .addValue("columnCode", question.columnCode)
                    .addValue("sortNo", question.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                questionKeyHolder,
                arrayOf("id")
            )
            val newQuestionId = questionKeyHolder.key?.toLong() ?: error("failed to copy scale question")
            if (question.options.isNotEmpty()) {
                val optionSql = """
                    insert into psy_scale_option (
                        question_id, option_code, option_label, score_value, exclusive_flag, option_group_code, sort_no, created_at, updated_at
                    ) values (
                        :questionId, :optionCode, :optionLabel, :scoreValue, :exclusiveFlag, :optionGroupCode, :sortNo, :createdAt, :updatedAt
                    )
                """.trimIndent()
                val optionParams = question.options.map { option ->
                    MapSqlParameterSource()
                        .addValue("questionId", newQuestionId)
                        .addValue("optionCode", option.optionCode)
                        .addValue("optionLabel", option.optionLabel)
                        .addValue("scoreValue", option.scoreValue)
                        .addValue("exclusiveFlag", option.exclusiveFlag)
                        .addValue("optionGroupCode", option.optionGroupCode)
                        .addValue("sortNo", option.sortNo)
                        .addValue("createdAt", now)
                        .addValue("updatedAt", now)
                }.toTypedArray()
                jdbcTemplate.batchUpdate(optionSql, optionParams)
            }
        }

        findResultRulesByScaleId(sourceScaleId).forEach { rule ->
            jdbcTemplate.update(
                """
                insert into psy_scale_result_rule (
                    scale_id, dimension_id, risk_level, score_min, score_max,
                    score_source, norm_code, result_title, result_description, suggestion_text, created_at, updated_at
                ) values (
                    :scaleId, :dimensionId, :riskLevel, :scoreMin, :scoreMax,
                    :scoreSource, :normCode, :resultTitle, :resultDescription, :suggestionText, :createdAt, :updatedAt
                )
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("scaleId", newScaleId)
                    .addValue("dimensionId", rule.dimensionId?.let { dimensionIdMap[it] })
                    .addValue("riskLevel", rule.riskLevel)
                    .addValue("scoreMin", rule.scoreMin)
                    .addValue("scoreMax", rule.scoreMax)
                    .addValue("scoreSource", rule.scoreSource)
                    .addValue("normCode", rule.normCode)
                    .addValue("resultTitle", rule.resultTitle)
                    .addValue("resultDescription", rule.resultDescription)
                    .addValue("suggestionText", rule.suggestionText)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now)
            )
        }

        findNormsByScaleId(sourceScaleId).forEach { norm ->
            jdbcTemplate.update(
                """
                insert into psy_scale_norm (
                    scale_id, norm_code, norm_name, dimension_id, applicable_target, age_min, age_max,
                    gender, org_type, mean_score, std_deviation, t_score_mean, t_score_std_deviation,
                    sort_no, created_at, updated_at
                ) values (
                    :scaleId, :normCode, :normName, :dimensionId, :applicableTarget, :ageMin, :ageMax,
                    :gender, :orgType, :meanScore, :stdDeviation, :tScoreMean, :tScoreStdDeviation,
                    :sortNo, :createdAt, :updatedAt
                )
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("scaleId", newScaleId)
                    .addValue("normCode", norm.normCode)
                    .addValue("normName", norm.normName)
                    .addValue("dimensionId", norm.dimensionId?.let { dimensionIdMap[it] })
                    .addValue("applicableTarget", norm.applicableTarget)
                    .addValue("ageMin", norm.ageMin)
                    .addValue("ageMax", norm.ageMax)
                    .addValue("gender", norm.gender)
                    .addValue("orgType", norm.orgType)
                    .addValue("meanScore", norm.meanScore)
                    .addValue("stdDeviation", norm.stdDeviation)
                    .addValue("tScoreMean", norm.tScoreMean)
                    .addValue("tScoreStdDeviation", norm.tScoreStdDeviation)
                    .addValue("sortNo", norm.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now)
            )
        }
    }

    private fun findQuestionsByScaleId(scaleId: Long): List<ScaleQuestion> {
        val questionSql = """
            select id, scale_id, dimension_id, question_no, question_title, question_type,
                   required_flag, reverse_score_flag, weight_value, option_selection_limit,
                   slider_min, slider_max, slider_step, text_input_enabled, text_input_placeholder,
                   matrix_group_code, row_code, column_code, sort_no
            from psy_scale_question
            where scale_id = :scaleId
            order by sort_no asc, question_no asc
        """.trimIndent()
        val questions = jdbcTemplate.query(questionSql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleQuestion(
                id = rs.getLong("id"),
                scaleId = rs.getLong("scale_id"),
                dimensionId = rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
                questionNo = rs.getInt("question_no"),
                questionTitle = rs.getString("question_title"),
                questionType = rs.getString("question_type"),
                requiredFlag = rs.getBoolean("required_flag"),
                reverseScoreFlag = rs.getBoolean("reverse_score_flag"),
                weightValue = rs.getBigDecimal("weight_value"),
                optionSelectionLimit = rs.getObject("option_selection_limit", java.lang.Integer::class.java)?.toInt(),
                sliderMin = rs.getBigDecimal("slider_min"),
                sliderMax = rs.getBigDecimal("slider_max"),
                sliderStep = rs.getBigDecimal("slider_step"),
                textInputEnabled = rs.getBoolean("text_input_enabled"),
                textInputPlaceholder = rs.getString("text_input_placeholder"),
                matrixGroupCode = rs.getString("matrix_group_code"),
                rowCode = rs.getString("row_code"),
                columnCode = rs.getString("column_code"),
                sortNo = rs.getInt("sort_no"),
                options = emptyList()
            )
        }
        if (questions.isEmpty()) return emptyList()
        val questionIds = questions.map { it.id }
        val optionSql = """
            select id, question_id, option_code, option_label, score_value, exclusive_flag, option_group_code, sort_no
            from psy_scale_option
            where question_id in (:questionIds)
            order by sort_no asc
        """.trimIndent()
        val options = jdbcTemplate.query(optionSql, mapOf("questionIds" to questionIds)) { rs, _ ->
            ScaleQuestionOption(
                id = rs.getLong("id"),
                questionId = rs.getLong("question_id"),
                optionCode = rs.getString("option_code"),
                optionLabel = rs.getString("option_label"),
                scoreValue = rs.getBigDecimal("score_value"),
                exclusiveFlag = rs.getBoolean("exclusive_flag"),
                optionGroupCode = rs.getString("option_group_code"),
                sortNo = rs.getInt("sort_no")
            )
        }
        val optionsByQuestionId = options.groupBy { it.questionId }
        return questions.map { q -> q.copy(options = optionsByQuestionId[q.id] ?: emptyList()) }
    }

    private fun findResultRulesByScaleId(scaleId: Long): List<ScaleResultRule> {
        val sql = """
            select id, scale_id, dimension_id, risk_level, score_min, score_max,
                   score_source, norm_code, result_title, result_description, suggestion_text
            from psy_scale_result_rule
            where scale_id = :scaleId
            order by id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleResultRule(
                id = rs.getLong("id"),
                scaleId = rs.getLong("scale_id"),
                dimensionId = rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
                riskLevel = rs.getString("risk_level"),
                scoreMin = rs.getBigDecimal("score_min"),
                scoreMax = rs.getBigDecimal("score_max"),
                scoreSource = rs.getString("score_source"),
                normCode = rs.getString("norm_code"),
                resultTitle = rs.getString("result_title"),
                resultDescription = rs.getString("result_description"),
                suggestionText = rs.getString("suggestion_text")
            )
        }
    }

    private fun findNormsByScaleId(scaleId: Long): List<ScaleNorm> {
        val sql = """
            select id, scale_id, norm_code, norm_name, dimension_id, applicable_target, age_min, age_max,
                   gender, org_type, mean_score, std_deviation, t_score_mean, t_score_std_deviation, sort_no
            from psy_scale_norm
            where scale_id = :scaleId
            order by sort_no asc, id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleNorm(
                id = rs.getLong("id"),
                scaleId = rs.getLong("scale_id"),
                normCode = rs.getString("norm_code"),
                normName = rs.getString("norm_name"),
                dimensionId = rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
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
    }

    private fun findDimensionsByScaleId(scaleId: Long): List<ScaleDimension> {
        val sql = """
            select id, scale_id, dimension_code, dimension_name, description, sort_no
            from psy_scale_dimension
            where scale_id = :scaleId
            order by sort_no asc, id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleDimension(
                id = rs.getLong("id"),
                scaleId = rs.getLong("scale_id"),
                dimensionCode = rs.getString("dimension_code"),
                dimensionName = rs.getString("dimension_name"),
                description = rs.getString("description"),
                sortNo = rs.getInt("sort_no")
            )
        }
    }

    private val scaleSummaryRowMapper = RowMapper { rs, _ ->
        ScaleSummary(
            id = rs.getLong("id"),
            scaleCode = rs.getString("scale_code"),
            scaleName = rs.getString("scale_name"),
            applicableTarget = rs.getString("applicable_target"),
            versionNo = rs.getString("version_no"),
            versionGroupId = rs.getObject("version_group_id", java.lang.Long::class.java)?.toLong(),
            currentVersionFlag = rs.getBoolean("current_version_flag"),
            status = rs.getString("status"),
            scoreMethod = rs.getString("score_method"),
            scoreCoefficient = rs.getBigDecimal("score_coefficient"),
            normStrategy = rs.getString("norm_strategy"),
            normDefaultGroup = rs.getString("norm_default_group"),
            highRiskWarningEnabled = rs.getBoolean("high_risk_warning_enabled"),
            anonymousSupported = rs.getBoolean("anonymous_supported"),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime()
        )
    }
}
