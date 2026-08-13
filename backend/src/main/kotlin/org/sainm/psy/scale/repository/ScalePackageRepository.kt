package org.sainm.psy.scale.repository

import org.sainm.psy.scale.api.UpdateScalePackageRequest
import org.sainm.psy.scale.domain.ScalePackageAlgorithmBinding
import org.sainm.psy.scale.domain.ScalePackageDimensionTranslation
import org.sainm.psy.scale.domain.ScalePackageGovernance
import org.sainm.psy.scale.domain.ScalePackageHighRiskRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageOptionTranslation
import org.sainm.psy.scale.domain.ScalePackageNormGovernance
import org.sainm.psy.scale.domain.ScalePackageQualityPolicy
import org.sainm.psy.scale.domain.ScalePackageQuestionTranslation
import org.sainm.psy.scale.domain.ScalePackageResultRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePackageTranslation
import org.sainm.psy.scale.domain.ScalePackageValidityRule
import org.springframework.jdbc.core.DataClassRowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDateTime

@Repository
class ScalePackageRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock
) {
    fun find(scaleId: Long): ScalePackageSnapshot = ScalePackageSnapshot(
        scaleId = scaleId,
        governance = one("select ${governanceColumns} from psy_scale_governance where scale_id = :scaleId", scaleId, ScalePackageGovernance::class.java),
        translations = many("select ${translationColumns} from psy_scale_translation where scale_id = :scaleId order by locale_code", scaleId, ScalePackageTranslation::class.java),
        dimensionTranslations = many(
            """select t.dimension_id, t.locale_code, t.dimension_name, t.description, t.review_status
               from psy_scale_dimension_translation t join psy_scale_dimension d on d.id=t.dimension_id
               where d.scale_id=:scaleId order by d.sort_no, d.id, t.locale_code""", scaleId, ScalePackageDimensionTranslation::class.java
        ),
        questionTranslations = many(
            """select t.question_id, t.locale_code, t.question_title, t.text_input_placeholder, t.review_status
               from psy_scale_question_translation t join psy_scale_question q on q.id=t.question_id
               where q.scale_id=:scaleId order by q.sort_no, q.id, t.locale_code""", scaleId, ScalePackageQuestionTranslation::class.java
        ),
        optionTranslations = many(
            """select t.option_id, t.locale_code, t.option_label, t.review_status
               from psy_scale_option_translation t join psy_scale_option o on o.id=t.option_id
               join psy_scale_question q on q.id=o.question_id
               where q.scale_id=:scaleId order by q.sort_no, o.sort_no, o.id, t.locale_code""", scaleId, ScalePackageOptionTranslation::class.java
        ),
        resultRuleTranslations = many(
            """select t.result_rule_id, t.locale_code, t.result_title, t.result_description, t.suggestion_text, t.review_status
               from psy_scale_result_rule_translation t join psy_scale_result_rule r on r.id=t.result_rule_id
               where r.scale_id=:scaleId order by r.id, t.locale_code""", scaleId, ScalePackageResultRuleTranslation::class.java
        ),
        highRiskRuleTranslations = many(
            """select t.high_risk_rule_id, t.locale_code, t.result_title, t.result_description, t.suggestion_text, t.review_status
               from psy_scale_high_risk_rule_translation t join psy_scale_high_risk_rule r on r.id=t.high_risk_rule_id
               where r.scale_id=:scaleId order by r.sort_no, r.id, t.locale_code""", scaleId, ScalePackageHighRiskRuleTranslation::class.java
        ),
        qualityPolicy = one("select ${qualityColumns} from psy_scale_quality_policy where scale_id=:scaleId", scaleId, ScalePackageQualityPolicy::class.java),
        validityRules = many("select ${validityColumns} from psy_scale_validity_rule where scale_id=:scaleId order by sort_no, rule_code, rule_version", scaleId, ScalePackageValidityRule::class.java),
        algorithmBinding = one("select ${algorithmColumns} from psy_scale_algorithm_binding where scale_id=:scaleId", scaleId, ScalePackageAlgorithmBinding::class.java),
        normGovernance = many("select id norm_id, source_reference, norm_version, sample_size, region_code, language_code, valid_from, valid_to, review_status from psy_scale_norm where scale_id=:scaleId order by sort_no, id", scaleId, ScalePackageNormGovernance::class.java)
    )

    fun replace(scaleId: Long, request: UpdateScalePackageRequest, userId: Long) {
        val now = Timestamp.valueOf(LocalDateTime.now(clock))
        deleteExisting(scaleId)
        request.governance?.let { g ->
            jdbc.update(
                """insert into psy_scale_governance (
                    scale_id, source_title, publisher_name, manual_version, citation_text, source_url,
                    copyright_status, rights_holder, authorization_status, authorization_type,
                    authorization_scope, authorized_territories, authorized_languages,
                    authorization_valid_from, authorization_valid_to, target_population, exclusion_criteria,
                    estimated_minutes, result_visibility, data_usage_statement, non_diagnostic_statement,
                    help_resource_text, governance_status, created_by, created_at, updated_by, updated_at
                ) values (
                    :scaleId, :sourceTitle, :publisherName, :manualVersion, :citationText, :sourceUrl,
                    :copyrightStatus, :rightsHolder, :authorizationStatus, :authorizationType,
                    :authorizationScope, :authorizedTerritories, :authorizedLanguages,
                    :authorizationValidFrom, :authorizationValidTo, :targetPopulation, :exclusionCriteria,
                    :estimatedMinutes, :resultVisibility, :dataUsageStatement, :nonDiagnosticStatement,
                    :helpResourceText, :governanceStatus, :userId, :now, :userId, :now
                )""",
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId).addValue("userId", userId).addValue("now", now)
                    .addValues(beanValues(g))
            )
        }
        batch(
            """insert into psy_scale_translation (
                scale_id, locale_code, scale_name, description, instruction_text, purpose_text,
                data_usage_text, result_visibility_text, non_diagnostic_text, high_risk_action_text,
                help_resource_text, review_status, created_at, updated_at
            ) values (:scaleId, :localeCode, :scaleName, :description, :instructionText, :purposeText,
                :dataUsageText, :resultVisibilityText, :nonDiagnosticText, :highRiskActionText,
                :helpResourceText, :reviewStatus, :now, :now)""",
            scaleId, now, request.translations.map(::beanValues)
        )
        batch("""insert into psy_scale_dimension_translation (dimension_id, locale_code, dimension_name, description, review_status, created_at, updated_at)
                 values (:dimensionId, :localeCode, :dimensionName, :description, :reviewStatus, :now, :now)""", scaleId, now, request.dimensionTranslations.map(::beanValues))
        batch("""insert into psy_scale_question_translation (question_id, locale_code, question_title, text_input_placeholder, review_status, created_at, updated_at)
                 values (:questionId, :localeCode, :questionTitle, :textInputPlaceholder, :reviewStatus, :now, :now)""", scaleId, now, request.questionTranslations.map(::beanValues))
        batch("""insert into psy_scale_option_translation (option_id, locale_code, option_label, review_status, created_at, updated_at)
                 values (:optionId, :localeCode, :optionLabel, :reviewStatus, :now, :now)""", scaleId, now, request.optionTranslations.map(::beanValues))
        batch("""insert into psy_scale_result_rule_translation (result_rule_id, locale_code, result_title, result_description, suggestion_text, review_status, created_at, updated_at)
                 values (:resultRuleId, :localeCode, :resultTitle, :resultDescription, :suggestionText, :reviewStatus, :now, :now)""", scaleId, now, request.resultRuleTranslations.map(::beanValues))
        batch("""insert into psy_scale_high_risk_rule_translation (high_risk_rule_id, locale_code, result_title, result_description, suggestion_text, review_status, created_at, updated_at)
                 values (:highRiskRuleId, :localeCode, :resultTitle, :resultDescription, :suggestionText, :reviewStatus, :now, :now)""", scaleId, now, request.highRiskRuleTranslations.map(::beanValues))
        request.qualityPolicy?.let { q ->
            jdbc.update(
                """insert into psy_scale_quality_policy (scale_id, missing_answer_policy, max_missing_ratio,
                    minimum_duration_seconds, maximum_duration_seconds, invalid_result_action,
                    require_all_required_answers, created_at, updated_at)
                values (:scaleId, :missingAnswerPolicy, :maxMissingRatio, :minimumDurationSeconds,
                    :maximumDurationSeconds, :invalidResultAction, :requireAllRequiredAnswers, :now, :now)""",
                MapSqlParameterSource().addValue("scaleId", scaleId).addValue("now", now).addValues(beanValues(q))
            )
        }
        batch(
            """insert into psy_scale_validity_rule (scale_id, rule_code, rule_type, rule_version, config_json,
                review_status, enabled, sort_no, created_at, updated_at)
            values (:scaleId, :ruleCode, :ruleType, :ruleVersion, cast(:configJson as jsonb),
                :reviewStatus, :enabled, :sortNo, :now, :now)""",
            scaleId, now, request.validityRules.map(::beanValues)
        )
        request.algorithmBinding?.let { a ->
            jdbc.update(
                """insert into psy_scale_algorithm_binding (scale_id, algorithm_code, algorithm_version,
                    implementation_type, input_schema_json, output_schema_json, implementation_checksum,
                    review_status, created_at, updated_at)
                values (:scaleId, :algorithmCode, :algorithmVersion, :implementationType,
                    cast(:inputSchemaJson as jsonb), cast(:outputSchemaJson as jsonb), :implementationChecksum,
                    :reviewStatus, :now, :now)""",
                MapSqlParameterSource().addValue("scaleId", scaleId).addValue("now", now).addValues(beanValues(a))
            )
        }
        jdbc.update(
            """update psy_scale_norm set source_reference=null, norm_version=null, sample_size=null,
                region_code=null, language_code=null, valid_from=null, valid_to=null, review_status='PENDING_REVIEW', updated_at=:now
               where scale_id=:scaleId""",
            mapOf("scaleId" to scaleId, "now" to now)
        )
        batch(
            """update psy_scale_norm set source_reference=:sourceReference, norm_version=:normVersion,
                sample_size=:sampleSize, region_code=:regionCode, language_code=:languageCode,
                valid_from=:validFrom, valid_to=:validTo, review_status=:reviewStatus, updated_at=:now
               where id=:normId and scale_id=:scaleId""",
            scaleId, now, request.normGovernance.map(::beanValues)
        )
    }

    fun canonicalValues(scaleId: Long): List<String> = jdbc.queryForList(
        """
        select payload from (
            select '00-skip-rules' sort_key,
                skip_rules_json::text payload
            from psy_scale where id=:scaleId and skip_rules_json is not null
            union all
            select '01-governance' sort_key, (to_jsonb(g) - 'id' - 'scale_id' - 'created_by' - 'created_at' - 'updated_by' - 'updated_at')::text payload
            from psy_scale_governance g where scale_id=:scaleId
            union all select '02-scale-' || locale_code, (to_jsonb(t) - 'id' - 'scale_id' - 'created_at' - 'updated_at')::text
            from psy_scale_translation t where scale_id=:scaleId
            union all select '03-dimension-' || d.dimension_code || '-' || t.locale_code, (to_jsonb(t) - 'id' - 'dimension_id' - 'created_at' - 'updated_at')::text
            from psy_scale_dimension_translation t join psy_scale_dimension d on d.id=t.dimension_id where d.scale_id=:scaleId
            union all select '04-question-' || q.question_no || '-' || t.locale_code, (to_jsonb(t) - 'id' - 'question_id' - 'created_at' - 'updated_at')::text
            from psy_scale_question_translation t join psy_scale_question q on q.id=t.question_id where q.scale_id=:scaleId
            union all select '05-option-' || q.question_no || '-' || o.option_code || '-' || t.locale_code, (to_jsonb(t) - 'id' - 'option_id' - 'created_at' - 'updated_at')::text
            from psy_scale_option_translation t join psy_scale_option o on o.id=t.option_id join psy_scale_question q on q.id=o.question_id where q.scale_id=:scaleId
            union all select '06-result-' || r.id || '-' || t.locale_code, (to_jsonb(t) - 'id' - 'result_rule_id' - 'created_at' - 'updated_at')::text
            from psy_scale_result_rule_translation t join psy_scale_result_rule r on r.id=t.result_rule_id where r.scale_id=:scaleId
            union all select '06a-high-risk-' || r.rule_code || '-' || t.locale_code, (to_jsonb(t) - 'id' - 'high_risk_rule_id' - 'created_at' - 'updated_at')::text
            from psy_scale_high_risk_rule_translation t join psy_scale_high_risk_rule r on r.id=t.high_risk_rule_id where r.scale_id=:scaleId
            union all select '07-quality', (to_jsonb(q) - 'id' - 'scale_id' - 'created_at' - 'updated_at')::text
            from psy_scale_quality_policy q where scale_id=:scaleId
            union all select '08-validity-' || rule_code || '-' || rule_version, (to_jsonb(v) - 'id' - 'scale_id' - 'created_at' - 'updated_at')::text
            from psy_scale_validity_rule v where scale_id=:scaleId
            union all select '09-algorithm', (to_jsonb(a) - 'id' - 'scale_id' - 'created_at' - 'updated_at')::text
            from psy_scale_algorithm_binding a where scale_id=:scaleId
            union all select '10-norm-' || norm_code || '-' || id,
                jsonb_build_object('sourceReference',source_reference,'normVersion',norm_version,'sampleSize',sample_size,
                    'regionCode',region_code,'languageCode',language_code,'validFrom',valid_from,'validTo',valid_to,'reviewStatus',review_status)::text
            from psy_scale_norm where scale_id=:scaleId
        ) canonical order by sort_key
        """.trimIndent(),
        mapOf("scaleId" to scaleId),
        String::class.java
    )

    fun copyPackage(sourceScaleId: Long, targetScaleId: Long) {
        val params = mapOf("sourceScaleId" to sourceScaleId, "targetScaleId" to targetScaleId)
        listOf(
            """insert into psy_scale_governance (scale_id, source_title, publisher_name, manual_version, citation_text, source_url,
                copyright_status, rights_holder, authorization_status, authorization_type, authorization_scope,
                authorized_territories, authorized_languages, authorization_valid_from, authorization_valid_to,
                target_population, exclusion_criteria, estimated_minutes, result_visibility, data_usage_statement,
                non_diagnostic_statement, help_resource_text, governance_status, created_by, created_at, updated_by, updated_at)
            select :targetScaleId, source_title, publisher_name, manual_version, citation_text, source_url,
                copyright_status, rights_holder, authorization_status, authorization_type, authorization_scope,
                authorized_territories, authorized_languages, authorization_valid_from, authorization_valid_to,
                target_population, exclusion_criteria, estimated_minutes, result_visibility, data_usage_statement,
                non_diagnostic_statement, help_resource_text, 'DRAFT', created_by, current_timestamp, updated_by, current_timestamp
            from psy_scale_governance where scale_id=:sourceScaleId""",
            """insert into psy_scale_translation (scale_id, locale_code, scale_name, description, instruction_text, purpose_text,
                data_usage_text, result_visibility_text, non_diagnostic_text, high_risk_action_text, help_resource_text,
                review_status, created_at, updated_at)
            select :targetScaleId, locale_code, scale_name, description, instruction_text, purpose_text, data_usage_text,
                result_visibility_text, non_diagnostic_text, high_risk_action_text, help_resource_text, 'DRAFT', current_timestamp, current_timestamp
            from psy_scale_translation where scale_id=:sourceScaleId""",
            """insert into psy_scale_dimension_translation (dimension_id, locale_code, dimension_name, description, review_status, created_at, updated_at)
            select target.id, translation.locale_code, translation.dimension_name, translation.description, 'DRAFT', current_timestamp, current_timestamp
            from psy_scale_dimension_translation translation
            join psy_scale_dimension source on source.id=translation.dimension_id
            join psy_scale_dimension target on target.scale_id=:targetScaleId and target.dimension_code=source.dimension_code
            where source.scale_id=:sourceScaleId""",
            """insert into psy_scale_question_translation (question_id, locale_code, question_title, text_input_placeholder, review_status, created_at, updated_at)
            select target.id, translation.locale_code, translation.question_title, translation.text_input_placeholder, 'DRAFT', current_timestamp, current_timestamp
            from psy_scale_question_translation translation
            join psy_scale_question source on source.id=translation.question_id
            join psy_scale_question target on target.scale_id=:targetScaleId and target.question_no=source.question_no
            where source.scale_id=:sourceScaleId""",
            """insert into psy_scale_option_translation (option_id, locale_code, option_label, review_status, created_at, updated_at)
            select target_option.id, translation.locale_code, translation.option_label, 'DRAFT', current_timestamp, current_timestamp
            from psy_scale_option_translation translation
            join psy_scale_option source_option on source_option.id=translation.option_id
            join psy_scale_question source_question on source_question.id=source_option.question_id
            join psy_scale_question target_question on target_question.scale_id=:targetScaleId and target_question.question_no=source_question.question_no
            join psy_scale_option target_option on target_option.question_id=target_question.id and target_option.option_code=source_option.option_code
            where source_question.scale_id=:sourceScaleId""",
            """with source_rules as (
                select r.id, row_number() over(order by coalesce(d.dimension_code,''), r.score_min, r.score_max, r.risk_level, r.id) rn
                from psy_scale_result_rule r left join psy_scale_dimension d on d.id=r.dimension_id where r.scale_id=:sourceScaleId
            ), target_rules as (
                select r.id, row_number() over(order by coalesce(d.dimension_code,''), r.score_min, r.score_max, r.risk_level, r.id) rn
                from psy_scale_result_rule r left join psy_scale_dimension d on d.id=r.dimension_id where r.scale_id=:targetScaleId
            )
            insert into psy_scale_result_rule_translation (result_rule_id, locale_code, result_title, result_description, suggestion_text, review_status, created_at, updated_at)
            select target_rules.id, translation.locale_code, translation.result_title, translation.result_description,
                translation.suggestion_text, 'DRAFT', current_timestamp, current_timestamp
            from psy_scale_result_rule_translation translation join source_rules on source_rules.id=translation.result_rule_id
            join target_rules on target_rules.rn=source_rules.rn""",
            """insert into psy_scale_high_risk_rule_translation (high_risk_rule_id, locale_code, result_title, result_description, suggestion_text, review_status, created_at, updated_at)
            select target.id, translation.locale_code, translation.result_title, translation.result_description,
                translation.suggestion_text, 'DRAFT', current_timestamp, current_timestamp
            from psy_scale_high_risk_rule_translation translation
            join psy_scale_high_risk_rule source on source.id=translation.high_risk_rule_id
            join psy_scale_high_risk_rule target on target.scale_id=:targetScaleId and target.rule_code=source.rule_code
            where source.scale_id=:sourceScaleId""",
            """insert into psy_scale_quality_policy (scale_id, missing_answer_policy, max_missing_ratio, minimum_duration_seconds,
                maximum_duration_seconds, invalid_result_action, require_all_required_answers, created_at, updated_at)
            select :targetScaleId, missing_answer_policy, max_missing_ratio, minimum_duration_seconds, maximum_duration_seconds,
                invalid_result_action, require_all_required_answers, current_timestamp, current_timestamp
            from psy_scale_quality_policy where scale_id=:sourceScaleId""",
            """insert into psy_scale_validity_rule (scale_id, rule_code, rule_type, rule_version, config_json, review_status, enabled, sort_no, created_at, updated_at)
            select :targetScaleId, rule_code, rule_type, rule_version, config_json, 'DRAFT', enabled, sort_no, current_timestamp, current_timestamp
            from psy_scale_validity_rule where scale_id=:sourceScaleId""",
            """insert into psy_scale_algorithm_binding (scale_id, algorithm_code, algorithm_version, implementation_type,
                input_schema_json, output_schema_json, implementation_checksum, review_status, created_at, updated_at)
            select :targetScaleId, algorithm_code, algorithm_version, implementation_type, input_schema_json,
                output_schema_json, implementation_checksum, 'DRAFT', current_timestamp, current_timestamp
            from psy_scale_algorithm_binding where scale_id=:sourceScaleId"""
        ).forEach { jdbc.update(it, params) }
    }

    private fun deleteExisting(scaleId: Long) {
        listOf(
            "delete from psy_scale_high_risk_rule_translation where high_risk_rule_id in (select id from psy_scale_high_risk_rule where scale_id=:scaleId)",
            "delete from psy_scale_result_rule_translation where result_rule_id in (select id from psy_scale_result_rule where scale_id=:scaleId)",
            "delete from psy_scale_option_translation where option_id in (select o.id from psy_scale_option o join psy_scale_question q on q.id=o.question_id where q.scale_id=:scaleId)",
            "delete from psy_scale_question_translation where question_id in (select id from psy_scale_question where scale_id=:scaleId)",
            "delete from psy_scale_dimension_translation where dimension_id in (select id from psy_scale_dimension where scale_id=:scaleId)",
            "delete from psy_scale_translation where scale_id=:scaleId",
            "delete from psy_scale_validity_rule where scale_id=:scaleId",
            "delete from psy_scale_algorithm_binding where scale_id=:scaleId",
            "delete from psy_scale_quality_policy where scale_id=:scaleId",
            "delete from psy_scale_governance where scale_id=:scaleId"
        ).forEach { jdbc.update(it, mapOf("scaleId" to scaleId)) }
    }

    private fun batch(sql: String, scaleId: Long, now: Timestamp, rows: List<Map<String, Any?>>) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(sql, rows.map { MapSqlParameterSource(it).addValue("scaleId", scaleId).addValue("now", now) }.toTypedArray())
    }

    private fun beanValues(value: Any): Map<String, Any?> = value.javaClass.declaredFields
        .filterNot { it.isSynthetic }
        .associate { field -> field.isAccessible = true; field.name to field.get(value) }

    private fun <T : Any> one(sql: String, scaleId: Long, type: Class<T>): T? =
        jdbc.query(sql, mapOf("scaleId" to scaleId), DataClassRowMapper.newInstance(type)).firstOrNull()

    private fun <T : Any> many(sql: String, scaleId: Long, type: Class<T>): List<T> =
        jdbc.query(sql, mapOf("scaleId" to scaleId), DataClassRowMapper.newInstance(type))

    private companion object {
        const val governanceColumns = "source_title, publisher_name, manual_version, citation_text, source_url, copyright_status, rights_holder, authorization_status, authorization_type, authorization_scope, authorized_territories, authorized_languages, authorization_valid_from, authorization_valid_to, target_population, exclusion_criteria, estimated_minutes, result_visibility, data_usage_statement, non_diagnostic_statement, help_resource_text, governance_status"
        const val translationColumns = "locale_code, scale_name, description, instruction_text, purpose_text, data_usage_text, result_visibility_text, non_diagnostic_text, high_risk_action_text, help_resource_text, review_status"
        const val qualityColumns = "missing_answer_policy, max_missing_ratio, minimum_duration_seconds, maximum_duration_seconds, invalid_result_action, require_all_required_answers"
        const val validityColumns = "rule_code, rule_type, rule_version, config_json::text config_json, review_status, enabled, sort_no"
        const val algorithmColumns = "algorithm_code, algorithm_version, implementation_type, input_schema_json::text input_schema_json, output_schema_json::text output_schema_json, implementation_checksum, review_status"
    }
}
