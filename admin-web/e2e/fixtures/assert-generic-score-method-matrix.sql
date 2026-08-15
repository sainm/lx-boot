-- PostgreSQL-only evidence for the synthetic generic scoring-method matrix.
--
-- These rows are created by generic-score-method-matrix.spec.ts in the
-- disposable E2E schema.  They contain no instrument questions and are never
-- added to the formal ScalePackage registry or presented as scale support.

do $$
declare
    expected record;
    actual_count bigint;
    target_scale_id bigint;
    target_task_id bigint;
    target_sheet_id bigint;
    target_result_id bigint;
begin
    for expected in
        select * from (values
            ('SIMPLE_SUM', 'E2E_METHOD_MATRIX_SIMPLE_SUM', 5.00::numeric, 5.0000::numeric, 5.00::numeric, 'SUM', 1.00::numeric),
            ('REVERSE_SUM', 'E2E_METHOD_MATRIX_REVERSE_SUM', 6.25::numeric, 5.0000::numeric, 5.00::numeric, 'SUM', 1.25::numeric),
            ('WEIGHTED_SUM', 'E2E_METHOD_MATRIX_WEIGHTED_SUM', 8.00::numeric, 8.0000::numeric, 8.00::numeric, 'SUM', 1.00::numeric),
            ('AVERAGE', 'E2E_METHOD_MATRIX_AVERAGE', 2.50::numeric, 2.5000::numeric, 5.00::numeric, 'AVERAGE', 1.00::numeric),
            ('WEIGHTED_AVERAGE', 'E2E_METHOD_MATRIX_WEIGHTED_AVERAGE', 2.67::numeric, 2.6667::numeric, 8.00::numeric, 'WEIGHTED_AVERAGE', 1.00::numeric)
        ) as matrix(score_method, scale_code, persisted_total, dimension_score, effective_sum, dimension_aggregation, score_coefficient)
    loop
        select count(*), min(id)
          into actual_count, target_scale_id
        from psy_scale
        where scale_code = expected.scale_code
          and status = 'PUBLISHED'
          and score_method = expected.score_method
          and published_content_hash ~ '^[0-9a-f]{64}$';
        if actual_count <> 1 then
            raise exception 'method matrix expected one published scale %, found %', expected.scale_code, actual_count;
        end if;

        select count(*), min(task.id)
          into actual_count, target_task_id
        from psy_assessment_task task
        where task.scale_id = target_scale_id
          and task.task_name like 'E2E method matrix ' || expected.score_method || ' %'
          and task.scale_version_no = (select version_no from psy_scale where id = target_scale_id)
          and task.scale_version_group_id = (select version_group_id from psy_scale where id = target_scale_id);
        if actual_count <> 1 then
            raise exception 'method matrix expected one locked task for %, found %', expected.score_method, actual_count;
        end if;

        select count(*), min(sheet.id)
          into actual_count, target_sheet_id
        from psy_assessment_answer_sheet sheet
        where sheet.task_id = target_task_id
          and sheet.answer_status = 'SUBMITTED'
          and sheet.submit_token like 'method-matrix-' || expected.score_method || '-%';
        if actual_count <> 1 then
            raise exception 'method matrix expected one submitted sheet for %, found %', expected.score_method, actual_count;
        end if;

        select count(*), min(result.id)
          into actual_count, target_result_id
        from psy_assessment_result result
        where result.answer_sheet_id = target_sheet_id
          and result.calculation_version = 1
          and result.supersedes_result_id is null
          and result.total_score = expected.persisted_total
          and result.risk_level = 'NORMAL'
          and result.scoring_trace_json ->> 'algorithmCode' = 'GENERIC_SCORE_CALCULATOR'
          and result.scoring_trace_json ->> 'algorithmVersion' = '1'
          and result.scoring_trace_json ->> 'scoreMethod' = expected.score_method
          and (result.scoring_trace_json ->> 'scoreCoefficient')::numeric = expected.score_coefficient
          and jsonb_array_length(result.scoring_trace_json -> 'questions') = 2
          and jsonb_array_length(result.scoring_trace_json -> 'dimensions') = 1
          and round((select sum((trace_question ->> 'effectiveScore')::numeric)
                     from jsonb_array_elements(result.scoring_trace_json -> 'questions') trace_question), 4) = expected.effective_sum
          and round((result.scoring_trace_json -> 'dimensions' -> 0 ->> 'score')::numeric, 4) = expected.dimension_score;
        if actual_count <> 1 then
            raise exception 'method matrix result/trace invariant failed for %', expected.score_method;
        end if;

        select count(*) into actual_count
        from psy_assessment_result_dimension dimension_result
        where dimension_result.result_id = target_result_id
          and round(dimension_result.dimension_score, 4) = expected.dimension_score;
        if actual_count <> 1 then
            raise exception 'method matrix persisted dimension score invariant failed for %', expected.score_method;
        end if;

        select count(*) into actual_count
        from psy_report report
        where report.result_id = target_result_id;
        if actual_count < 1 then
            raise exception 'method matrix expected a report for %', expected.score_method;
        end if;

        raise notice 'METHOD_MATRIX_CHECK|method_%|PASS', expected.score_method;
    end loop;

    raise notice 'METHOD_MATRIX_CHECK|all_five_methods|PASS';
end $$;
