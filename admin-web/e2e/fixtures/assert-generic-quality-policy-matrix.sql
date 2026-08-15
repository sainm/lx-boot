-- PostgreSQL-only evidence for the source-independent generic missing-answer
-- policy matrix. These rows are disposable synthetic fixtures and contain no
-- instrument questions or formal scale support claim.

do $$
declare
    expected record;
    reject_expected record;
    actual_count bigint;
    target_scale_id bigint;
    target_task_id bigint;
    target_sheet_id bigint;
    target_result_id bigint;
begin
    for expected in
        select * from (values
            ('SIMPLE_SUM',       'ALLOW',   'E2E_QP_SIMPLE_SUM_ALLOW',        2.00::numeric, 2.0000::numeric, 1.00::numeric, 'ATTENTION', 'SUM'),
            ('SIMPLE_SUM',       'PRORATE', 'E2E_QP_SIMPLE_SUM_PRORATE',      4.00::numeric, 4.0000::numeric, 2.00::numeric, 'NORMAL',    'PRORATED_SUM'),
            ('REVERSE_SUM',      'ALLOW',   'E2E_QP_REVERSE_SUM_ALLOW',       2.50::numeric, 2.0000::numeric, 1.00::numeric, 'NORMAL',    'SUM'),
            ('REVERSE_SUM',      'PRORATE', 'E2E_QP_REVERSE_SUM_PRORATE',     5.00::numeric, 4.0000::numeric, 2.00::numeric, 'NORMAL',    'PRORATED_SUM'),
            ('WEIGHTED_SUM',     'ALLOW',   'E2E_QP_WEIGHTED_SUM_ALLOW',      2.00::numeric, 2.0000::numeric, 1.00::numeric, 'ATTENTION', 'SUM'),
            ('WEIGHTED_SUM',     'PRORATE', 'E2E_QP_WEIGHTED_SUM_PRORATE',    6.00::numeric, 6.0000::numeric, 3.00::numeric, 'NORMAL',    'PRORATED_SUM'),
            ('AVERAGE',          'ALLOW',   'E2E_QP_AVERAGE_ALLOW',           2.00::numeric, 2.0000::numeric, 1.00::numeric, 'ATTENTION', 'AVERAGE'),
            ('AVERAGE',          'PRORATE', 'E2E_QP_AVERAGE_PRORATE',         2.00::numeric, 2.0000::numeric, 1.00::numeric, 'ATTENTION', 'PRORATED_AVERAGE'),
            ('WEIGHTED_AVERAGE', 'ALLOW',   'E2E_QP_WEIGHTED_AVERAGE_ALLOW',  2.00::numeric, 2.0000::numeric, 1.00::numeric, 'ATTENTION', 'WEIGHTED_AVERAGE'),
            ('WEIGHTED_AVERAGE', 'PRORATE', 'E2E_QP_WEIGHTED_AVERAGE_PRORATE',2.00::numeric, 2.0000::numeric, 1.00::numeric, 'ATTENTION', 'PRORATED_WEIGHTED_AVERAGE')
        ) as policies(score_method, missing_policy, scale_code, persisted_total, dimension_score, prorate_factor, risk_level, aggregation)
    loop
        select count(*), min(scale.id)
          into actual_count, target_scale_id
        from psy_scale scale
        join psy_scale_quality_policy quality on quality.scale_id = scale.id
        where scale.scale_code = expected.scale_code
          and scale.status = 'PUBLISHED'
          and scale.score_method = expected.score_method
          and quality.missing_answer_policy = expected.missing_policy
          and quality.max_missing_ratio = 0.5
          and quality.require_all_required_answers = false
          and scale.published_content_hash ~ '^[0-9a-f]{64}$';
        if actual_count <> 1 then
            raise exception 'quality policy matrix expected one published scale for %/%, found %', expected.score_method, expected.missing_policy, actual_count;
        end if;

        select count(*), min(task.id)
          into actual_count, target_task_id
        from psy_assessment_task task
        where task.scale_id = target_scale_id
          and task.task_name like 'E2E method matrix ' || expected.score_method || ' ' || expected.missing_policy || ' %'
          and task.scale_version_no = (select version_no from psy_scale where id = target_scale_id)
          and task.scale_version_group_id = (select version_group_id from psy_scale where id = target_scale_id);
        if actual_count <> 1 then
            raise exception 'quality policy matrix expected one locked task for %/%, found %', expected.score_method, expected.missing_policy, actual_count;
        end if;

        select count(*), min(sheet.id)
          into actual_count, target_sheet_id
        from psy_assessment_answer_sheet sheet
        where sheet.task_id = target_task_id
          and sheet.answer_status = 'SUBMITTED'
          and sheet.submit_token like 'quality-policy-' || expected.score_method || '-' || expected.missing_policy || '-%';
        if actual_count <> 1 then
            raise exception 'quality policy matrix expected one partial submitted sheet for %/%, found %', expected.score_method, expected.missing_policy, actual_count;
        end if;

        select count(*), min(result.id)
          into actual_count, target_result_id
        from psy_assessment_result result
        where result.answer_sheet_id = target_sheet_id
          and result.calculation_version = 1
          and result.supersedes_result_id is null
          and result.total_score = expected.persisted_total
          and result.risk_level = expected.risk_level
          and result.quality_status = 'VALID'
          and result.quality_missing_ratio = 0.5
          and coalesce(result.quality_issue_codes, '') = ''
          and result.scoring_trace_json ->> 'algorithmCode' = 'GENERIC_SCORE_CALCULATOR'
          and result.scoring_trace_json ->> 'algorithmVersion' = '1'
          and result.scoring_trace_json ->> 'scoreMethod' = expected.score_method
          and result.scoring_trace_json ->> 'missingAnswerPolicy' = expected.missing_policy
          and (result.scoring_trace_json ->> 'prorateFactor')::numeric = expected.prorate_factor
          and jsonb_array_length(result.scoring_trace_json -> 'questions') = 1
          and jsonb_array_length(result.scoring_trace_json -> 'dimensions') = 1
          and (result.scoring_trace_json -> 'questions' -> 0 ->> 'effectiveScore')::numeric = 2
          and (result.scoring_trace_json -> 'dimensions' -> 0 ->> 'score')::numeric = expected.dimension_score
          and result.scoring_trace_json -> 'dimensions' -> 0 ->> 'aggregation' = expected.aggregation;
        if actual_count <> 1 then
            raise exception 'quality policy matrix result/trace invariant failed for %/%', expected.score_method, expected.missing_policy;
        end if;

        select count(*) into actual_count
        from psy_assessment_result_dimension dimension_result
        where dimension_result.result_id = target_result_id
          and round(dimension_result.dimension_score, 4) = expected.dimension_score;
        if actual_count <> 1 then
            raise exception 'quality policy matrix persisted dimension score invariant failed for %/%', expected.score_method, expected.missing_policy;
        end if;

        select count(*) into actual_count
        from psy_assessment_answer_sheet sheet
        where sheet.id = target_sheet_id
          and sheet.quality_status = 'VALID'
          and sheet.quality_missing_ratio = 0.5
          and coalesce(sheet.quality_issue_codes, '') = '';
        if actual_count <> 1 then
            raise exception 'quality policy matrix answer-sheet quality invariant failed for %/%', expected.score_method, expected.missing_policy;
        end if;

        select count(*) into actual_count
        from psy_report report
        where report.result_id = target_result_id;
        if actual_count < 1 then
            raise exception 'quality policy matrix expected a report for %/%', expected.score_method, expected.missing_policy;
        end if;

        raise notice 'QUALITY_POLICY_MATRIX_CHECK|method_%_policy_%|PASS', expected.score_method, expected.missing_policy;
    end loop;

    for reject_expected in
        select * from (values
            ('SIMPLE_SUM',       'REJECT', 'E2E_QP_SIMPLE_SUM_REJECT'),
            ('REVERSE_SUM',      'REJECT', 'E2E_QP_REVERSE_SUM_REJECT'),
            ('WEIGHTED_SUM',     'REJECT', 'E2E_QP_WEIGHTED_SUM_REJECT'),
            ('AVERAGE',          'REJECT', 'E2E_QP_AVERAGE_REJECT'),
            ('WEIGHTED_AVERAGE', 'REJECT', 'E2E_QP_WEIGHTED_AVERAGE_REJECT')
        ) as policies(score_method, missing_policy, scale_code)
    loop
        select count(*), min(scale.id)
          into actual_count, target_scale_id
        from psy_scale scale
        join psy_scale_quality_policy quality on quality.scale_id = scale.id
        where scale.scale_code = reject_expected.scale_code
          and scale.status = 'PUBLISHED'
          and scale.score_method = reject_expected.score_method
          and quality.missing_answer_policy = reject_expected.missing_policy
          and quality.max_missing_ratio = 0
          and quality.require_all_required_answers = true
          and scale.published_content_hash ~ '^[0-9a-f]{64}$';
        if actual_count <> 1 then
            raise exception 'quality policy REJECT expected one published scale for %, found %', reject_expected.score_method, actual_count;
        end if;

        select count(*), min(task.id)
          into actual_count, target_task_id
        from psy_assessment_task task
        where task.scale_id = target_scale_id
          and task.task_name like 'E2E method matrix ' || reject_expected.score_method || ' ' || reject_expected.missing_policy || ' %'
          and task.scale_version_no = (select version_no from psy_scale where id = target_scale_id)
          and task.scale_version_group_id = (select version_group_id from psy_scale where id = target_scale_id);
        if actual_count <> 1 then
            raise exception 'quality policy REJECT expected one locked task for %, found %', reject_expected.score_method, actual_count;
        end if;

        select count(*) into actual_count
        from psy_assessment_answer_sheet sheet
        where sheet.task_id = target_task_id
          and sheet.answer_status = 'SUBMITTED';
        if actual_count <> 0 then
            raise exception 'quality policy REJECT must not persist a submitted sheet for %, found %', reject_expected.score_method, actual_count;
        end if;

        select count(*) into actual_count
        from psy_assessment_result result
        join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
        where sheet.task_id = target_task_id;
        if actual_count <> 0 then
            raise exception 'quality policy REJECT must not persist a result for %, found %', reject_expected.score_method, actual_count;
        end if;

        raise notice 'QUALITY_POLICY_MATRIX_CHECK|method_%_policy_%|PASS', reject_expected.score_method, reject_expected.missing_policy;
    end loop;

    raise notice 'QUALITY_POLICY_MATRIX_CHECK|policy_REJECT|PASS';
    raise notice 'QUALITY_POLICY_MATRIX_CHECK|policy_ALLOW|PASS';
    raise notice 'QUALITY_POLICY_MATRIX_CHECK|policy_PRORATE|PASS';
    raise notice 'QUALITY_POLICY_MATRIX_CHECK|all_policies|PASS';
    raise notice 'QUALITY_POLICY_MATRIX_CHECK|all_methods_policies|PASS';
end $$;
