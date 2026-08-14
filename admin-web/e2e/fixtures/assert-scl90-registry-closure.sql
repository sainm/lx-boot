-- PostgreSQL evidence for the restricted SCL90_PROFILE technical closure.
-- The browser selector verifies the HTTP/Web/export semantics; this script
-- recomputes the persisted item and dimension trace and checks the immutable
-- task/result/report history in the isolated schema.

select set_config('psy.registry.scale_code', :'scale_code', false);
select set_config('psy.registry.version_no', :'version_no', false);
select set_config('psy.registry.task_prefix', :'task_prefix', false);
select set_config('psy.registry.expected_total', :'expected_total', false);
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
    concurrent_task_id bigint;
    actual_count bigint;
begin
    if code <> 'SCL90_USER_AUTHORIZED' then
        raise exception 'unsupported SCL-90 technical scale code %', code;
    end if;

    select count(*), min(id)
      into actual_count, target_scale_id
    from psy_scale
    where scale_code = code
      and version_no = expected_version
      and status = 'PUBLISHED'
      and published_content_hash ~ '^[0-9a-f]{64}$';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 expected one published % @ %, found %', code, expected_version, actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_question
    where scale_id = target_scale_id
      and question_type = 'SINGLE_CHOICE'
      and required_flag = true;
    if actual_count <> expected_questions then
        raise exception 'registry SCL-90 expected % required single-choice questions, found %', expected_questions, actual_count;
    end if;

    select count(*) into actual_count from psy_scale_dimension where scale_id = target_scale_id;
    if actual_count <> expected_dimensions then
        raise exception 'registry SCL-90 expected % dimensions, found %', expected_dimensions, actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_translation
    where scale_id = target_scale_id
      and locale_code in ('zh-CN', 'ja-JP', 'en')
      and review_status = 'APPROVED'
      and non_diagnostic_text <> '';
    if actual_count <> 3 then
        raise exception 'registry SCL-90 expected three approved non-diagnostic translations, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_question_translation translation
    join psy_scale_question question on question.id = translation.question_id
    where question.scale_id = target_scale_id
      and translation.locale_code in ('zh-CN', 'ja-JP', 'en')
      and translation.review_status = 'APPROVED'
      and translation.question_title <> '';
    if actual_count <> expected_questions * 3 then
        raise exception 'registry SCL-90 question translation matrix incomplete: %', actual_count;
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
        raise exception 'registry SCL-90 option translation matrix incomplete';
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
        raise exception 'registry SCL-90 result translation matrix incomplete';
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
        raise exception 'registry SCL-90 high-risk translation matrix incomplete';
    end if;

    select count(*) into actual_count
    from psy_scale_algorithm_binding binding
    where binding.scale_id = target_scale_id
      and binding.algorithm_code = 'SCL90_PROFILE'
      and binding.algorithm_version = '1'
      and binding.review_status = 'APPROVED';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 algorithm binding invariant failed';
    end if;

    select count(*) into actual_count
    from (
        select distinct on (golden_case.case_code) golden_case.id
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
        raise exception 'registry SCL-90 expected % passing latest Golden Cases, found %', expected_golden, actual_count;
    end if;

    select count(*), min(task.id)
      into actual_count, target_task_id
    from psy_assessment_task task
    where task.scale_id = target_scale_id
      and task.task_name like task_prefix || ' technical closure %';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 expected one technical task for prefix %, found %', task_prefix, actual_count;
    end if;

    select count(*), min(sheet.id)
      into actual_count, target_sheet_id
    from psy_assessment_answer_sheet sheet
    where sheet.task_id = target_task_id
      and sheet.answer_status = 'SUBMITTED';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 expected one idempotent submitted sheet, found %', actual_count;
    end if;

    select count(*), min(result.id)
      into actual_count, target_result_id
    from psy_assessment_result result
    where result.answer_sheet_id = target_sheet_id
      and result.calculation_version = 1
      and result.supersedes_result_id is null;
    if actual_count <> 1 then
        raise exception 'registry SCL-90 expected one original result, found %', actual_count;
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
      and result.scoring_trace_json ->> 'algorithmCode' = 'SCL90_PROFILE'
      and result.scoring_trace_json ->> 'scoreMethod' = 'SIMPLE_SUM'
      and result.scoring_trace_json ->> 'scoreSource' = 'RAW_SCORE'
      and result.scoring_trace_json ->> 'missingAnswerPolicy' = 'REJECT'
      and (result.scoring_trace_json ->> 'prorateFactor')::numeric = 1
      and (result.scoring_trace_json ->> 'scoreCoefficient')::numeric = 1
      and (result.scoring_trace_json -> 'derivedMetrics' ->> 'GSI')::numeric = (expected_metrics ->> 'GSI')::numeric
      and (result.scoring_trace_json -> 'derivedMetrics' ->> 'PST')::numeric = (expected_metrics ->> 'PST')::numeric
      and (result.scoring_trace_json -> 'derivedMetrics' ->> 'PSDI')::numeric = (expected_metrics ->> 'PSDI')::numeric
      and (result.scoring_trace_json -> 'derivedMetrics' ->> 'POSITIVE_SYMPTOM_COUNT')::numeric = (expected_metrics ->> 'POSITIVE_SYMPTOM_COUNT')::numeric
      and (result.scoring_trace_json -> 'derivedMetrics' ->> 'ANSWERED_ITEM_COUNT')::numeric = (expected_metrics ->> 'ANSWERED_ITEM_COUNT')::numeric
      and jsonb_array_length(result.scoring_trace_json -> 'questions') = expected_questions
      and jsonb_array_length(result.scoring_trace_json -> 'dimensions') = expected_dimensions
      and result.scoring_trace_json ->> 'resultRuleMatched' = 'true';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 score/result invariant failed for result %', target_result_id;
    end if;

    select count(*) into actual_count
    from jsonb_array_elements((select scoring_trace_json -> 'questions' from psy_assessment_result where id = target_result_id)) trace_question
    join psy_scale_question question
      on question.id = (trace_question ->> 'questionId')::bigint
     and question.scale_id = target_scale_id
    join psy_assessment_answer_item answer
      on answer.answer_sheet_id = target_sheet_id
     and answer.question_id = question.id
    join psy_scale_option selected_option on selected_option.id = answer.option_id
    where (trace_question ->> 'rawScore')::numeric = selected_option.score_value
      and (trace_question ->> 'reverseScore')::numeric = selected_option.score_value
      and (trace_question ->> 'weightValue')::numeric = question.weight_value
      and (trace_question ->> 'weightedScore')::numeric = selected_option.score_value * question.weight_value
      and (trace_question ->> 'effectiveScore')::numeric = (trace_question ->> 'weightedScore')::numeric;
    if actual_count <> expected_questions then
        raise exception 'registry SCL-90 item trace expected % verified rows, found %', expected_questions, actual_count;
    end if;

    select count(*) into actual_count
    from jsonb_array_elements((select scoring_trace_json -> 'dimensions' from psy_assessment_result where id = target_result_id)) trace_dimension
    join psy_scale_dimension dimension
      on dimension.id = (trace_dimension ->> 'dimensionId')::bigint
     and dimension.scale_id = target_scale_id
    where trace_dimension ->> 'aggregation' = 'AVERAGE'
      and jsonb_array_length(trace_dimension -> 'questionIds') = (select count(*) from psy_scale_question where dimension_id = dimension.id)
      and (trace_dimension ->> 'score')::numeric = (
          select avg((trace_question ->> 'effectiveScore')::numeric)
          from jsonb_array_elements((select scoring_trace_json -> 'questions' from psy_assessment_result where id = target_result_id)) trace_question
          join psy_scale_question question on question.id = (trace_question ->> 'questionId')::bigint
          where question.dimension_id = dimension.id
      );
    if actual_count <> expected_dimensions then
        raise exception 'registry SCL-90 dimension trace expected % verified rows, found %', expected_dimensions, actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_publication_review review
    where review.scale_id = target_scale_id
      and review.decision = 'APPROVED'
      and review.review_type in ('PROFESSIONAL', 'BUSINESS')
      and review.scale_content_hash = (select published_content_hash from psy_scale where id = target_scale_id)
      and review.release_fingerprint ~ '^[0-9a-f]{64}$';
    if actual_count <> 2 then
        raise exception 'registry SCL-90 expected two synthetic workflow approvals, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_task task
    join psy_scale scale on scale.id = task.scale_id
    where task.id = target_task_id
      and task.scale_version_no = expected_version
      and task.scale_content_hash = scale.published_content_hash;
    if actual_count <> 1 then
        raise exception 'registry SCL-90 task version lock failed for task %', target_task_id;
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
        raise exception 'registry SCL-90 rescore history failed for result %', target_result_id;
    end if;

    select count(*) into actual_count
    from psy_report report
    where report.result_id in (target_result_id, current_result_id)
      and report.report_type = 'SYSTEM'
      and report.report_title <> ''
      and report.report_content <> '';
    if actual_count <> 2 then
        raise exception 'registry SCL-90 expected immutable original and rescored reports, found %', actual_count;
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
                and expected_total between rule.score_min and rule.score_max
                and translation.locale_code = 'ja-JP'
                and translation.review_status = 'APPROVED'
          )
      end
      and report.report_content <> '';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 Japanese report semantics failed for result %', target_result_id;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_sheet sheet
    where sheet.id = target_sheet_id
      and sheet.task_id = target_task_id
      and sheet.answer_status = 'SUBMITTED'
      and sheet.submit_token like lower(task_prefix) || '-cutoff-%';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 idempotent submit token invariant failed';
    end if;

    select count(*), min(task.id)
      into actual_count, concurrent_task_id
    from psy_assessment_task task
    where task.scale_id = target_scale_id
      and task.task_name like task_prefix || ' concurrent submission %';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 expected one concurrency task, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_sheet sheet
    where sheet.task_id = concurrent_task_id
      and sheet.answer_status = 'SUBMITTED';
    if actual_count <> 1 then
        raise exception 'registry SCL-90 concurrent submit created % submitted sheets', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_result result
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.task_id = concurrent_task_id;
    if actual_count <> 1 then
        raise exception 'registry SCL-90 concurrent submit created % results', actual_count;
    end if;

    raise notice 'REGISTRY_CHECK|source_package_integrity|PASS';
    raise notice 'REGISTRY_CHECK|golden_case_scores|PASS';
    raise notice 'REGISTRY_CHECK|scoring_trace|PASS';
    raise notice 'REGISTRY_CHECK|trilingual_result_content|PASS';
    raise notice 'REGISTRY_CHECK|report_semantics|PASS';
    raise notice 'REGISTRY_CHECK|task_version_lock|PASS';
    raise notice 'REGISTRY_CHECK|historical_result_immutability|PASS';
    raise notice 'REGISTRY_CHECK|idempotent_submission|PASS';
    raise notice 'REGISTRY_CHECK|concurrent_submission|PASS';
    raise notice 'REGISTRY_CHECK|rescore_history|PASS';
end $$;
