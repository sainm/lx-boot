-- Reusable PostgreSQL evidence for the GENERIC_SINGLE_CHOICE closure profile.
--
-- Expected values are supplied from the immutable registry/source package by
-- scripts/run_scale_adaptation_registry.py.  Runtime values are recomputed
-- from persisted questions, options, answers and scoring trace; a new scale
-- must not add a scale-code branch to this file.

select set_config('psy.registry.scale_code', :'scale_code', false);
select set_config('psy.registry.version_no', :'version_no', false);
select set_config('psy.registry.task_prefix', :'task_prefix', false);
select set_config('psy.registry.expected_total', :'expected_total', false);
select set_config('psy.registry.expected_score_method', :'expected_score_method', false);
select set_config('psy.registry.expected_dimension_aggregation', :'expected_dimension_aggregation', false);
select set_config('psy.registry.expected_score_coefficient', :'expected_score_coefficient', false);
select set_config('psy.registry.expected_skip_rules_json', :'expected_skip_rules_json', false);
select set_config('psy.registry.expected_result_rule_signatures_json', :'expected_result_rule_signatures_json', false);
select set_config('psy.registry.expected_high_risk_rule_codes_json', :'expected_high_risk_rule_codes_json', false);
select set_config('psy.registry.expected_derived_metric_codes_json', :'expected_derived_metric_codes_json', false);
select set_config('psy.registry.expected_norm_status', :'expected_norm_status', false);
select set_config('psy.registry.expected_norm_codes_json', :'expected_norm_codes_json', false);
select set_config('psy.registry.expected_risk', :'expected_risk', false);
select set_config('psy.registry.expected_high_risk_rule', :'expected_high_risk_rule', false);
select set_config('psy.registry.expected_metrics_json', :'expected_metrics_json', false);
select set_config('psy.registry.question_count', :'question_count', false);
select set_config('psy.registry.dimension_count', :'dimension_count', false);
select set_config('psy.registry.golden_count', :'golden_count', false);

do $$
declare
    code text := current_setting('psy.registry.scale_code');
    expected_version text := current_setting('psy.registry.version_no');
    task_prefix text := current_setting('psy.registry.task_prefix');
    expected_total numeric := current_setting('psy.registry.expected_total')::numeric;
    expected_score_method text := current_setting('psy.registry.expected_score_method');
    expected_dimension_aggregation text := current_setting('psy.registry.expected_dimension_aggregation');
    expected_score_coefficient numeric := current_setting('psy.registry.expected_score_coefficient')::numeric;
    expected_skip_rules jsonb := current_setting('psy.registry.expected_skip_rules_json')::jsonb;
    expected_result_rule_signatures jsonb := current_setting('psy.registry.expected_result_rule_signatures_json')::jsonb;
    expected_high_risk_rule_codes jsonb := current_setting('psy.registry.expected_high_risk_rule_codes_json')::jsonb;
    expected_derived_metric_codes jsonb := current_setting('psy.registry.expected_derived_metric_codes_json')::jsonb;
    expected_norm_status text := current_setting('psy.registry.expected_norm_status');
    expected_norm_codes jsonb := current_setting('psy.registry.expected_norm_codes_json')::jsonb;
    expected_risk text := current_setting('psy.registry.expected_risk');
    expected_high_risk_rule text := current_setting('psy.registry.expected_high_risk_rule');
    expected_metrics jsonb := current_setting('psy.registry.expected_metrics_json')::jsonb;
    expected_questions integer := current_setting('psy.registry.question_count')::integer;
    expected_dimensions integer := current_setting('psy.registry.dimension_count')::integer;
    expected_golden integer := current_setting('psy.registry.golden_count')::integer;
    target_scale_id bigint;
    target_task_id bigint;
    target_sheet_id bigint;
    target_result_id bigint;
    current_result_id bigint;
    target_report_id bigint;
    concurrent_task_id bigint;
    actual_count bigint;
    actual_codes jsonb;
begin
    select count(*), min(id)
      into actual_count, target_scale_id
    from psy_scale
    where scale_code = code
      and version_no = expected_version
      and status = 'PUBLISHED'
      and published_content_hash ~ '^[0-9a-f]{64}$';
    if actual_count <> 1 then
        raise exception 'registry generic scale expected one published % @ %, found %', code, expected_version, actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_question
    where scale_id = target_scale_id
      and question_type = 'SINGLE_CHOICE'
      and required_flag = true;
    if actual_count <> expected_questions then
        raise exception 'registry generic scale expected % required single-choice questions, found %', expected_questions, actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_dimension
    where scale_id = target_scale_id;
    if actual_count <> expected_dimensions then
        raise exception 'registry generic scale expected % dimensions, found %', expected_dimensions, actual_count;
    end if;

    -- The respondent-visible item set and declaration-only branch path are
    -- immutable input, not merely a count inferred from the scoring trace.
    select count(*) into actual_count
    from psy_scale_question question
    where question.scale_id = target_scale_id;
    if actual_count <> expected_questions then
        raise exception 'registry generic effective question set expected % rows, found %', expected_questions, actual_count;
    end if;
    select count(*) into actual_count
    from generate_series(1, expected_questions) series(question_no)
    where not exists (
        select 1 from psy_scale_question question
        where question.scale_id = target_scale_id
          and question.question_no = series.question_no
          and question.sort_no = series.question_no
    );
    if actual_count <> 0 then
        raise exception 'registry generic effective question set has % missing/order-mismatched rows', actual_count;
    end if;

    select count(*), min(task.id)
      into actual_count, target_task_id
    from psy_assessment_task task
    where task.scale_id = target_scale_id
      and task.task_name like task_prefix || ' technical closure %';
    if actual_count <> 1 then
        raise exception 'registry generic scale expected one technical task for prefix %, found %', task_prefix, actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_task task
    join psy_scale scale on scale.id = task.scale_id
    where task.id = target_task_id
      and coalesce(scale.skip_rules_json, '[]'::jsonb) = expected_skip_rules;
    if actual_count <> 1 then
        raise exception 'registry generic task skip path does not match source declaration';
    end if;

    select count(*), min(sheet.id)
      into actual_count, target_sheet_id
    from psy_assessment_answer_sheet sheet
    where sheet.task_id = target_task_id
      and sheet.answer_status = 'SUBMITTED';
    if actual_count <> 1 then
        raise exception 'registry generic scale expected one idempotent submitted sheet, found %', actual_count;
    end if;

    select count(*), min(result.id)
      into actual_count, target_result_id
    from psy_assessment_result result
    where result.answer_sheet_id = target_sheet_id
      and result.calculation_version = 1
      and result.supersedes_result_id is null;
    if actual_count <> 1 then
        raise exception 'registry generic scale expected one original result, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_result result
    where result.id = target_result_id
      and result.total_score = expected_total
      and result.risk_level = expected_risk
      and result.high_risk_flag = (expected_high_risk_rule <> '')
      and coalesce(result.high_risk_rule_code, '') = expected_high_risk_rule
      and result.calculation_version = 1
      and result.is_current = false
      and result.supersedes_result_id is null
      and result.scale_content_hash = (select published_content_hash from psy_scale where id = target_scale_id)
      and result.scoring_engine_version <> ''
      and result.scoring_trace_json ->> 'algorithmCode' = 'GENERIC_SCORE_CALCULATOR'
      and result.scoring_trace_json ->> 'algorithmVersion' = '1'
      and result.scoring_trace_json ->> 'scoreMethod' = expected_score_method
      and result.scoring_trace_json ->> 'scoreSource' = 'RAW_SCORE'
      and result.scoring_trace_json ->> 'missingAnswerPolicy' = 'REJECT'
      and (result.scoring_trace_json ->> 'prorateFactor')::numeric = 1
      and (result.scoring_trace_json ->> 'scoreCoefficient')::numeric = expected_score_coefficient
      and result.scoring_trace_json -> 'derivedMetrics' = expected_metrics
      and jsonb_array_length(result.scoring_trace_json -> 'questions') = expected_questions
      and jsonb_array_length(result.scoring_trace_json -> 'dimensions') = expected_dimensions
      and result.scoring_trace_json ? 'resultRuleMatched';
    if actual_count <> 1 then
        raise exception 'registry generic score/result invariant failed for result %', target_result_id;
    end if;

    -- Compare the persisted result-rule, derived-metric and norm declarations
    -- to the immutable source package.  For the current active packages the
    -- norm set is deliberately empty; no selection reason may be invented.
    select coalesce(jsonb_agg(jsonb_build_object(
        'riskLevel', rule.risk_level,
        'scoreMin', rule.score_min,
        'scoreMax', rule.score_max,
        'scoreSource', rule.score_source,
        'normCode', rule.norm_code
    ) order by rule.risk_level, rule.score_min, rule.score_max, rule.score_source, rule.norm_code), '[]'::jsonb)
      into actual_codes
    from psy_scale_result_rule rule
    where rule.scale_id = target_scale_id;
    if actual_codes <> expected_result_rule_signatures then
        raise exception 'registry generic result-rule set mismatch: expected %, actual %', expected_result_rule_signatures, actual_codes;
    end if;

    select coalesce(jsonb_agg(rule.rule_code order by rule.rule_code), '[]'::jsonb)
      into actual_codes
    from psy_scale_high_risk_rule rule
    where rule.scale_id = target_scale_id;
    if actual_codes <> expected_high_risk_rule_codes then
        raise exception 'registry generic high-risk rule set mismatch: expected %, actual %', expected_high_risk_rule_codes, actual_codes;
    end if;

    select coalesce(jsonb_agg(metric.key order by metric.key), '[]'::jsonb)
      into actual_codes
    from jsonb_object_keys(coalesce((select scoring_trace_json -> 'derivedMetrics' from psy_assessment_result where id = target_result_id), '{}'::jsonb)) metric(key);
    if actual_codes <> expected_derived_metric_codes then
        raise exception 'registry generic derived-metric set mismatch: expected %, actual %', expected_derived_metric_codes, actual_codes;
    end if;

    select coalesce(jsonb_agg(norm.norm_code order by norm.norm_code), '[]'::jsonb)
      into actual_codes
    from psy_scale_norm norm
    where norm.scale_id = target_scale_id;
    if actual_codes <> expected_norm_codes then
        raise exception 'registry generic norm set mismatch for status %: expected %, actual %', expected_norm_status, expected_norm_codes, actual_codes;
    end if;
    if jsonb_array_length(expected_norm_codes) = 0 then
        select count(*) into actual_count
        from psy_assessment_result result
        where result.id = target_result_id
          and (result.norm_code is not null
               or nullif(result.scoring_trace_json ->> 'normCode', '') is not null
               or nullif(result.scoring_trace_json ->> 'normSelectionReason', '') is not null);
        if actual_count <> 0 then
            raise exception 'registry generic result unexpectedly selected a norm for status %', expected_norm_status;
        end if;
    end if;

    select count(distinct review.release_fingerprint) into actual_count
    from psy_scale_publication_review review
    where review.scale_id = target_scale_id
      and review.decision = 'APPROVED'
      and review.review_type in ('PROFESSIONAL', 'BUSINESS')
      and review.scale_content_hash = (select published_content_hash from psy_scale where id = target_scale_id)
      and review.release_fingerprint ~ '^[0-9a-f]{64}$';
    if actual_count <> 1 then
        raise exception 'registry generic release fingerprint must be one shared immutable value, found %', actual_count;
    end if;

    -- Quality outcomes are persisted evidence, not inferred only from a
    -- complete answer list.  The closure task uses a zero-missing, valid
    -- submission, so both the answer sheet and result must record that exact
    -- quality decision and no issue codes.
    select count(*) into actual_count
    from psy_assessment_answer_sheet sheet
    join psy_assessment_result result on result.id = target_result_id
    where sheet.id = target_sheet_id
      and result.answer_sheet_id = sheet.id
      and sheet.quality_status = 'VALID'
      and coalesce(sheet.quality_missing_ratio, -1) = 0
      and coalesce(sheet.quality_issue_codes, '') = ''
      and result.quality_status = 'VALID'
      and coalesce(result.quality_missing_ratio, -1) = 0
      and coalesce(result.quality_issue_codes, '') = '';
    if actual_count <> 1 then
        raise exception 'registry generic quality outcome invariant failed for sheet/result %/%', target_sheet_id, target_result_id;
    end if;

    -- Recompute every item trace from the persisted selected option, reverse
    -- convention and weight.  This avoids a scale-specific expected-value SQL
    -- branch while still comparing all scoring intermediates.
    select count(*) into actual_count
    from jsonb_array_elements((select scoring_trace_json -> 'questions' from psy_assessment_result where id = target_result_id)) trace_question
    join psy_scale_question question
      on question.id = (trace_question ->> 'questionId')::bigint
     and question.scale_id = target_scale_id
    join psy_assessment_answer_item answer
      on answer.answer_sheet_id = target_sheet_id
     and answer.question_id = question.id
    join psy_scale_option selected_option on selected_option.id = answer.option_id
    join lateral (
        select min(score_value) min_score, max(score_value) max_score
        from psy_scale_option
        where question_id = question.id
    ) option_range on true
    where (trace_question ->> 'rawScore')::numeric = selected_option.score_value
      and (trace_question ->> 'reverseScore')::numeric = case
              when question.reverse_score_flag then option_range.min_score + option_range.max_score - selected_option.score_value
              else selected_option.score_value
          end
      and (trace_question ->> 'weightValue')::numeric = question.weight_value
      and (trace_question ->> 'weightedScore')::numeric = (
              case
                  when question.reverse_score_flag then option_range.min_score + option_range.max_score - selected_option.score_value
                  else selected_option.score_value
              end
          ) * question.weight_value
      and (trace_question ->> 'effectiveScore')::numeric = case
              when expected_score_method in ('WEIGHTED_SUM', 'WEIGHTED_AVERAGE')
                  then (trace_question ->> 'weightedScore')::numeric
              else (trace_question ->> 'reverseScore')::numeric
          end;
    if actual_count <> expected_questions then
        raise exception 'registry generic full item trace expected % verified rows, found %', expected_questions, actual_count;
    end if;

    -- Recompute the persisted global total from the item trace.  SUM-based
    -- packages retain their sum; AVERAGE and WEIGHTED_AVERAGE packages retain
    -- the calculator's four-decimal mean.  This is deliberately driven by
    -- registry metadata, not by a scale-code branch.
    select count(*) into actual_count
    from psy_assessment_result result
    where result.id = target_result_id
      and result.total_score = case
          when expected_score_method = 'AVERAGE' then round(
              (select sum((trace_question ->> 'effectiveScore')::numeric)
               from jsonb_array_elements(result.scoring_trace_json -> 'questions') trace_question)
              / expected_questions * expected_score_coefficient,
              4
          )
          when expected_score_method = 'WEIGHTED_AVERAGE' then round(
              (select sum((trace_question ->> 'effectiveScore')::numeric)
               from jsonb_array_elements(result.scoring_trace_json -> 'questions') trace_question)
              / (select sum(question.weight_value)
                 from psy_scale_question question
                 where question.scale_id = target_scale_id)
              * expected_score_coefficient,
              4
          )
          else round(
              (select sum((trace_question ->> 'effectiveScore')::numeric)
               from jsonb_array_elements(result.scoring_trace_json -> 'questions') trace_question)
              * expected_score_coefficient,
              4
          )
      end;
    if actual_count <> 1 then
        raise exception 'registry generic global total recomputation failed for result %', target_result_id;
    end if;

    select count(*) into actual_count
    from jsonb_array_elements((select scoring_trace_json -> 'questions' from psy_assessment_result where id = target_result_id)) trace_question
    where not (
        trace_question ? 'questionId'
        and trace_question ? 'rawScore'
        and trace_question ? 'reverseScore'
        and trace_question ? 'weightValue'
        and trace_question ? 'weightedScore'
        and trace_question ? 'effectiveScore'
    );
    if actual_count <> 0 then
        raise exception 'registry generic item trace has % structurally incomplete rows', actual_count;
    end if;

    select count(*) into actual_count
    from jsonb_array_elements((select scoring_trace_json -> 'dimensions' from psy_assessment_result where id = target_result_id)) trace_dimension
    join psy_scale_dimension dimension
      on dimension.id = (trace_dimension ->> 'dimensionId')::bigint
     and dimension.scale_id = target_scale_id
    where trace_dimension ->> 'aggregation' = case
          when expected_dimension_aggregation in ('SIMPLE_SUM', 'REVERSE_SUM', 'WEIGHTED_SUM') then 'SUM'
          else expected_dimension_aggregation
      end
      and jsonb_array_length(trace_dimension -> 'questionIds') = (
          select count(*) from psy_scale_question where dimension_id = dimension.id
      )
      and (trace_dimension ->> 'score')::numeric = case
          when expected_dimension_aggregation = 'AVERAGE' then round((
              select sum((trace_question ->> 'effectiveScore')::numeric)
              from jsonb_array_elements((select scoring_trace_json -> 'questions' from psy_assessment_result where id = target_result_id)) trace_question
              join psy_scale_question question on question.id = (trace_question ->> 'questionId')::bigint
              where question.dimension_id = dimension.id
          ) / (
              select count(*) from psy_scale_question where dimension_id = dimension.id
          ), 4)
          when expected_dimension_aggregation = 'WEIGHTED_AVERAGE' then round((
              select sum((trace_question ->> 'weightedScore')::numeric)
              from jsonb_array_elements((select scoring_trace_json -> 'questions' from psy_assessment_result where id = target_result_id)) trace_question
              join psy_scale_question question on question.id = (trace_question ->> 'questionId')::bigint
              where question.dimension_id = dimension.id
          ) / (
              select sum(question.weight_value) from psy_scale_question question where question.dimension_id = dimension.id
          ), 4)
          when expected_dimension_aggregation = 'WEIGHTED_SUM' then (
              select sum((trace_question ->> 'weightedScore')::numeric)
              from jsonb_array_elements((select scoring_trace_json -> 'questions' from psy_assessment_result where id = target_result_id)) trace_question
              join psy_scale_question question on question.id = (trace_question ->> 'questionId')::bigint
              where question.dimension_id = dimension.id
          )
          else (
              select sum((trace_question ->> 'effectiveScore')::numeric)
              from jsonb_array_elements((select scoring_trace_json -> 'questions' from psy_assessment_result where id = target_result_id)) trace_question
              join psy_scale_question question on question.id = (trace_question ->> 'questionId')::bigint
              where question.dimension_id = dimension.id
          )
      end;
    if actual_count <> expected_dimensions then
        raise exception 'registry generic full dimension trace expected % verified rows, found %', expected_dimensions, actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_translation translation
    where translation.scale_id = target_scale_id
      and translation.locale_code in ('zh-CN', 'ja-JP', 'en')
      and translation.review_status = 'APPROVED'
      and translation.non_diagnostic_text <> '';
    if actual_count <> 3 then
        raise exception 'registry generic scale expected three approved non-diagnostic translations, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_question_translation translation
    join psy_scale_question question on question.id = translation.question_id
    where question.scale_id = target_scale_id
      and translation.locale_code in ('zh-CN', 'ja-JP', 'en')
      and translation.review_status = 'APPROVED'
      and translation.question_title <> '';
    if actual_count <> expected_questions * 3 then
        raise exception 'registry generic scale expected % approved question translations, found %', expected_questions * 3, actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_option_translation translation
    join psy_scale_option option_row on option_row.id = translation.option_id
    join psy_scale_question question on question.id = option_row.question_id
    where question.scale_id = target_scale_id
      and translation.locale_code in ('zh-CN', 'ja-JP', 'en')
      and translation.review_status = 'APPROVED'
      and translation.option_label <> '';
    if actual_count <> (select count(*) * 3 from psy_scale_option option_row join psy_scale_question question on question.id = option_row.question_id where question.scale_id = target_scale_id) then
        raise exception 'registry generic scale option translation matrix is incomplete';
    end if;

    select count(*) into actual_count
    from psy_scale_result_rule_translation translation
    join psy_scale_result_rule rule on rule.id = translation.result_rule_id
    where rule.scale_id = target_scale_id
      and translation.locale_code in ('zh-CN', 'ja-JP', 'en')
      and translation.review_status = 'APPROVED'
      and translation.result_title <> ''
      and translation.result_description <> ''
      and translation.suggestion_text <> '';
    if actual_count <> (select count(*) * 3 from psy_scale_result_rule where scale_id = target_scale_id) then
        raise exception 'registry generic scale result translation matrix is incomplete';
    end if;

    select count(*) into actual_count
    from psy_scale_high_risk_rule_translation translation
    join psy_scale_high_risk_rule rule on rule.id = translation.high_risk_rule_id
    where rule.scale_id = target_scale_id
      and translation.locale_code in ('zh-CN', 'ja-JP', 'en')
      and translation.review_status = 'APPROVED'
      and translation.result_title <> ''
      and translation.result_description <> ''
      and translation.suggestion_text <> '';
    if actual_count <> (select count(*) * 3 from psy_scale_high_risk_rule where scale_id = target_scale_id) then
        raise exception 'registry generic scale high-risk translation matrix is incomplete';
    end if;

    select count(*) into actual_count
    from psy_scale_algorithm_binding binding
    where binding.scale_id = target_scale_id
      and binding.algorithm_code = 'GENERIC_SCORE_CALCULATOR'
      and binding.algorithm_version = '1'
      and binding.review_status = 'APPROVED';
    if actual_count <> 1 then
        raise exception 'registry generic algorithm binding invariant failed';
    end if;

    select count(*) into actual_count
    from (
        select distinct on (golden_case.case_code) golden_case.id, golden_case.case_code
        from psy_scale_golden_case golden_case
        where golden_case.scale_id = target_scale_id
        order by golden_case.case_code, golden_case.revision_no desc, golden_case.id desc
    ) latest_case
    join lateral (
        select run.passed
        from psy_scale_golden_case_run run
        where run.golden_case_id = latest_case.id
        order by run.executed_at desc, run.id desc
        limit 1
    ) latest_run on true
    where latest_run.passed = true;
    if actual_count <> expected_golden then
        raise exception 'registry generic scale expected % passing latest Golden Cases, found %', expected_golden, actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_publication_review review
    where review.scale_id = target_scale_id
      and review.decision = 'APPROVED'
      and review.review_type in ('PROFESSIONAL', 'BUSINESS')
      and review.scale_content_hash = (select published_content_hash from psy_scale where id = target_scale_id)
      and review.release_fingerprint ~ '^[0-9a-f]{64}$';
    if actual_count <> 2 then
        raise exception 'registry generic scale expected two synthetic workflow approvals bound to the published hash, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_task task
    join psy_scale scale on scale.id = task.scale_id
    where task.id = target_task_id
      and task.scale_version_no = expected_version
      and task.scale_content_hash = scale.published_content_hash;
    if actual_count <> 1 then
        raise exception 'registry generic task version lock failed for task %', target_task_id;
    end if;

    select count(*), min(newer.id)
      into actual_count, current_result_id
    from psy_assessment_result original
    join psy_assessment_result newer
      on newer.supersedes_result_id = original.id
     and newer.answer_sheet_id = original.answer_sheet_id
    where original.id = target_result_id
      and original.calculation_version = 1
      and original.is_current = false
      and newer.calculation_version = 2
      and newer.is_current = true
      and newer.total_score = original.total_score
      and newer.risk_level = original.risk_level
      and newer.scale_content_hash = original.scale_content_hash
      and newer.scoring_engine_version = original.scoring_engine_version
      and newer.scoring_trace_json = original.scoring_trace_json;
    if actual_count <> 1 then
        raise exception 'registry generic rescore history failed for result %', target_result_id;
    end if;

    select count(*) into actual_count
    from psy_report report
    where report.result_id in (target_result_id, current_result_id)
      and report.report_type = 'SYSTEM'
      and report.report_title <> ''
      and report.report_content <> '';
    if actual_count <> 2 then
        raise exception 'registry generic scale expected immutable original and rescored reports, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_report report
    where report.result_id = target_result_id
      and report.locale_code = 'ja-JP'
      and report.report_title = case
          when expected_high_risk_rule <> '' then (
              select translation.result_title
              from psy_scale_high_risk_rule_translation translation
              join psy_scale_high_risk_rule rule on rule.id = translation.high_risk_rule_id
              where rule.scale_id = target_scale_id
                and rule.rule_code = expected_high_risk_rule
                and translation.locale_code = 'ja-JP'
                and translation.review_status = 'APPROVED'
          )
          else (
              select translation.result_title
              from psy_scale_result_rule_translation translation
              join psy_scale_result_rule rule on rule.id = translation.result_rule_id
              where rule.scale_id = target_scale_id
                and rule.risk_level = expected_risk
                and expected_total between rule.score_min and rule.score_max
                and translation.locale_code = 'ja-JP'
                and translation.review_status = 'APPROVED'
          )
      end
      and report.report_content <> '';
    if actual_count <> 1 then
        raise exception 'registry generic Japanese report semantics failed for result %', target_result_id;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_sheet sheet
    where sheet.id = target_sheet_id
      and sheet.task_id = target_task_id
      and sheet.answer_status = 'SUBMITTED'
      and sheet.submit_token like lower(task_prefix) || '-cutoff-%';
    if actual_count <> 1 then
        raise exception 'registry generic idempotent submit token invariant failed';
    end if;

    select count(*), min(task.id)
      into actual_count, concurrent_task_id
    from psy_assessment_task task
    where task.scale_id = target_scale_id
      and task.task_name like task_prefix || ' concurrent submission %';
    if actual_count <> 1 then
        raise exception 'registry generic scale expected one concurrency task, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_sheet sheet
    where sheet.task_id = concurrent_task_id
      and sheet.answer_status = 'SUBMITTED';
    if actual_count <> 1 then
        raise exception 'registry generic concurrent submit created % submitted sheets', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_result result
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.task_id = concurrent_task_id;
    if actual_count <> 1 then
        raise exception 'registry generic concurrent submit created % results', actual_count;
    end if;

    -- Security evidence is part of every registered scale closure, not only
    -- the shared HTTP selector.  Tie audit events and warning routing to this
    -- scale/task/result so a global synthetic fixture cannot satisfy the check.
    select count(*), min(report.id)
      into actual_count, target_report_id
    from psy_report report
    where report.result_id = target_result_id
      and report.report_type = 'SYSTEM';
    if actual_count < 1 then
        raise exception 'registry generic security audit needs an original system report';
    end if;

    select count(*) into actual_count
    from sys_security_event event
    where event.event_type in ('PSY_SCALE_PACKAGE_IMPORTED', 'PSY_SCALE_SOURCE_PACKAGE_IMPORTED')
      and event.detail_json ->> 'scaleId' = target_scale_id::text;
    if actual_count < 1 then
        raise exception 'registry generic security audit missing package import event for scale %', target_scale_id;
    end if;

    select count(distinct event.detail_json ->> 'reviewType') into actual_count
    from sys_security_event event
    where event.event_type = 'PSY_SCALE_PUBLICATION_REVIEWED'
      and event.detail_json ->> 'scaleId' = target_scale_id::text
      and event.detail_json ->> 'decision' = 'APPROVED'
      and event.detail_json ->> 'reviewType' in ('PROFESSIONAL', 'BUSINESS');
    if actual_count <> 2 then
        raise exception 'registry generic security audit expected professional/business review events, found %', actual_count;
    end if;

    select count(*) into actual_count
    from sys_security_event event
    where event.event_type = 'PSY_ASSESSMENT_RESULT_RESCORED'
      and event.detail_json ->> 'answerSheetId' = target_sheet_id::text
      and event.detail_json ->> 'previousResultId' = target_result_id::text;
    if actual_count < 1 then
        raise exception 'registry generic security audit missing rescore event for sheet %', target_sheet_id;
    end if;

    select count(*) into actual_count
    from sys_security_event event
    where event.event_type = 'PSY_REPORT_VIEWED'
      and event.detail_json ->> 'reportId' = target_report_id::text;
    if actual_count < 1 then
        raise exception 'registry generic security audit missing report-view event for report %', target_report_id;
    end if;

    select count(distinct event.detail_json ->> 'exportFormat') into actual_count
    from sys_security_event event
    where event.event_type = 'PSY_REPORT_EXPORTED'
      and event.detail_json ->> 'reportId' = target_report_id::text
      and event.detail_json ->> 'exportFormat' in ('TEXT', 'PDF', 'WORD');
    if actual_count <> 3 then
        raise exception 'registry generic security audit expected TEXT/PDF/WORD export events, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_task task
    join psy_scale scale on scale.id = task.scale_id
    join psy_assessment_answer_sheet sheet on sheet.task_id = task.id
    where task.id = target_task_id
      and (task.tenant_id is distinct from scale.tenant_id
           or sheet.tenant_id is distinct from task.tenant_id);
    if actual_count <> 0 then
        raise exception 'registry generic security tenant chain has % mismatched rows', actual_count;
    end if;

    if expected_high_risk_rule <> '' then
        select count(*) into actual_count
        from psy_warning_record warning
        join psy_assessment_result result on result.id = warning.result_id
        join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
        join psy_assessment_task task on task.id = sheet.task_id
        where warning.result_id = target_result_id
          and warning.status = 'PENDING'
          and warning.tenant_id = task.tenant_id
          and warning.warning_level = (
              select rule.warning_level
              from psy_scale_high_risk_rule rule
              where rule.scale_id = target_scale_id
                and rule.rule_code = expected_high_risk_rule
          );
        if actual_count <> 1 then
            raise exception 'registry generic high-risk warning routing expected one row for %, found %', expected_high_risk_rule, actual_count;
        end if;
    end if;

    raise notice 'REGISTRY_CHECK|source_package_integrity|PASS';
    raise notice 'REGISTRY_CHECK|golden_case_scores|PASS';
    raise notice 'REGISTRY_CHECK|scoring_trace|PASS';
    raise notice 'REGISTRY_CHECK|question_set_path|PASS';
    raise notice 'REGISTRY_CHECK|normative_semantics|PASS';
    raise notice 'REGISTRY_CHECK|trilingual_result_content|PASS';
    raise notice 'REGISTRY_CHECK|report_semantics|PASS';
    raise notice 'REGISTRY_CHECK|task_version_lock|PASS';
    raise notice 'REGISTRY_CHECK|historical_result_immutability|PASS';
    raise notice 'REGISTRY_CHECK|idempotent_submission|PASS';
    raise notice 'REGISTRY_CHECK|concurrent_submission|PASS';
    raise notice 'REGISTRY_CHECK|rescore_history|PASS';
    raise notice 'REGISTRY_CHECK|quality_outcome|PASS';
    raise notice 'REGISTRY_CHECK|security_audit|PASS';
end $$;
