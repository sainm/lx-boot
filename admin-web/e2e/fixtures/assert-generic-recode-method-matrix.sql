-- PostgreSQL-only evidence for the source-independent dimension/time recode
-- matrix and the generic question-type input paths. Rows are created by
-- generic-recode-method-matrix.spec.ts in the disposable schema and are never
-- formal instrument support.

do $$
declare
    target_scale_id bigint;
    target_task_id bigint;
    target_sheet_id bigint;
    target_branch_task_id bigint;
    target_branch_sheet_id bigint;
    target_result_id bigint;
    actual_count bigint;
    recode_schema jsonb;
begin
    select count(*), min(id)
      into actual_count, target_scale_id
    from psy_scale
    where scale_code = 'E2E_RECODE_MATRIX'
      and version_no = 'synthetic-recode-v1'
      and status = 'PUBLISHED'
      and report_template = 'DIMENSION_PROFILE'
      and published_content_hash ~ '^[0-9a-f]{64}$';
    if actual_count <> 1 then
        raise exception 'recode matrix expected one published scale, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_scale_question
    where scale_id = target_scale_id
      and question_type = 'TIME';
    if actual_count <> 4 then
        raise exception 'recode matrix expected four TIME questions, found %', actual_count;
    end if;
    select count(*) into actual_count
    from psy_scale_question
    where scale_id = target_scale_id
      and question_type = 'SLIDER'
      and slider_min = 0
      and slider_max = 600
      and slider_step = 1;
    if actual_count <> 1 then
        raise exception 'recode matrix expected one bounded slider, found %', actual_count;
    end if;
    select count(*) into actual_count
    from psy_scale_question
    where scale_id = target_scale_id
      and question_type = 'SINGLE_CHOICE';
    if actual_count <> 2 then
        raise exception 'recode matrix expected two single-choice questions, found %', actual_count;
    end if;
    select count(*) into actual_count
    from psy_scale_question
    where scale_id = target_scale_id
      and question_type in ('MULTI_SELECT', 'MATRIX', 'TEXT_WITH_OPTION', 'TEXT');
    if actual_count <> 4 then
        raise exception 'recode matrix expected one question for each advanced type, found %', actual_count;
    end if;

    select input_schema_json into recode_schema
    from psy_scale_algorithm_binding
    where scale_id = target_scale_id
      and algorithm_code = 'GENERIC_SCORE_CALCULATOR';
    if recode_schema is null then
        raise exception 'recode matrix algorithm binding is missing';
    end if;
    if recode_schema #>> '{dimensionRecodes,RECODE_SUM,rule}' <> 'RECODE_SUM_TO_0_3'
       or recode_schema #>> '{dimensionRecodes,SLEEP_DURATION,rule}' <> 'SLEEP_DURATION_RECODE_0_3'
       or recode_schema #>> '{dimensionRecodes,SLEEP_EFFICIENCY,rule}' <> 'SLEEP_EFFICIENCY_RECODE_0_3'
    then
        raise exception 'recode matrix algorithm binding does not preserve all three rules: %', recode_schema;
    end if;
    select count(*) into actual_count
    from psy_scale_dimension dimension
    where dimension.scale_id = target_scale_id
      and exists (
          select 1 from jsonb_object_keys(recode_schema -> 'dimensionRecodes') key
          where key = dimension.dimension_code
      );
    if actual_count <> 3 then
        raise exception 'recode matrix expected three recoded dimensions, found %', actual_count;
    end if;

    select count(*), min(task.id)
      into actual_count, target_task_id
    from psy_assessment_task task
    where task.scale_id = target_scale_id
      and task.task_name like 'E2E recode matrix %'
      and task.task_name not like '%-skip-branch'
      and task.scale_version_no = (select version_no from psy_scale where id = target_scale_id)
      and task.scale_version_group_id = (select version_group_id from psy_scale where id = target_scale_id);
    if actual_count <> 1 then
        raise exception 'recode matrix expected one locked task, found %', actual_count;
    end if;

    select count(*), min(sheet.id)
      into actual_count, target_sheet_id
    from psy_assessment_answer_sheet sheet
    where sheet.task_id = target_task_id
      and sheet.answer_status = 'SUBMITTED'
      and sheet.submit_token like 'recode-matrix-%';
    if actual_count <> 1 then
        raise exception 'recode matrix expected one submitted sheet, found %', actual_count;
    end if;

    -- The branch task is deliberately separate from the main all-question
    -- task so the invariant below proves the submit-time active-question
    -- filter, not merely the browser's visible list.
    select count(*), min(task.id)
      into actual_count, target_branch_task_id
    from psy_assessment_task task
    where task.scale_id = target_scale_id
      and task.task_name like 'E2E recode matrix %-skip-branch'
      and task.scale_version_no = (select version_no from psy_scale where id = target_scale_id)
      and task.scale_version_group_id = (select version_group_id from psy_scale where id = target_scale_id);
    if actual_count <> 1 then
        raise exception 'recode matrix expected one skip branch task, found %', actual_count;
    end if;

    select count(*), min(sheet.id)
      into actual_count, target_branch_sheet_id
    from psy_assessment_answer_sheet sheet
    where sheet.task_id = target_branch_task_id
      and sheet.answer_status = 'SUBMITTED'
      and sheet.submit_token like 'recode-skip-%';
    if actual_count <> 1 then
        raise exception 'recode matrix expected one skip branch submission, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_item answer
    join psy_scale_question question on question.id = answer.question_id
    where answer.answer_sheet_id = target_branch_sheet_id
      and question.question_no = 2;
    if actual_count <> 0 then
        raise exception 'recode matrix skip branch persisted skipped q2, found %', actual_count;
    end if;

    select count(*), min(result.id)
      into actual_count, target_result_id
    from psy_assessment_result result
    where result.answer_sheet_id = target_sheet_id
      and result.calculation_version = 1
      and result.is_current = true
      and result.supersedes_result_id is null
      and result.total_score = 6.60
      and result.risk_level = 'NORMAL'
      and result.scale_content_hash = (select published_content_hash from psy_scale where id = target_scale_id)
      and result.scoring_engine_version <> ''
      and result.scoring_trace_json ->> 'algorithmCode' = 'GENERIC_SCORE_CALCULATOR'
      and result.scoring_trace_json ->> 'algorithmVersion' = '1'
      and result.scoring_trace_json ->> 'scoreMethod' = 'WEIGHTED_SUM'
      and result.scoring_trace_json ->> 'missingAnswerPolicy' = 'REJECT'
      and (result.scoring_trace_json ->> 'prorateFactor')::numeric = 1
      and jsonb_array_length(result.scoring_trace_json -> 'questions') = 11
      and jsonb_array_length(result.scoring_trace_json -> 'dimensions') = 4;
    if actual_count <> 1 then
        raise exception 'recode matrix result/scoring trace invariant failed';
    end if;

    -- TIME answers must be persisted as text and the numeric slider as a value.
    select count(*) into actual_count
    from psy_assessment_answer_item answer
    join psy_scale_question question on question.id = answer.question_id
    where answer.answer_sheet_id = target_sheet_id
      and question.scale_id = target_scale_id
      and ((question.question_no = 3 and answer.answer_text = '22:30')
        or (question.question_no = 4 and answer.answer_text = '06:30')
        or (question.question_no = 5 and answer.answer_text = '23:00')
        or (question.question_no = 6 and answer.answer_text = '07:00')
        or (question.question_no = 7 and answer.answer_value = 360));
    if actual_count <> 5 then
        raise exception 'recode matrix TIME/SLIDER answer persistence invariant failed, found %', actual_count;
    end if;

    select count(*) into actual_count
    from psy_assessment_answer_item answer
    join psy_scale_question question on question.id = answer.question_id
    where answer.answer_sheet_id = target_sheet_id
      and question.scale_id = target_scale_id
      and ((question.question_no = 8 and question.question_type = 'MULTI_SELECT' and answer.option_id is not null)
        or (question.question_no = 9 and question.question_type = 'MATRIX' and answer.option_id is not null)
        or (question.question_no = 10 and question.question_type = 'TEXT_WITH_OPTION' and answer.option_id is not null and answer.answer_text is null)
        or (question.question_no = 11 and question.question_type = 'TEXT' and answer.answer_text = 'synthetic free text'));
    if actual_count <> 5 then
        raise exception 'recode matrix advanced question answer persistence invariant failed, found %', actual_count;
    end if;

    -- The three recodes are independent of the global total and must be
    -- visible in both the persisted dimension rows and the scoring trace.
    select count(*) into actual_count
    from psy_assessment_result_dimension result_dimension
    join psy_scale_dimension dimension on dimension.id = result_dimension.dimension_id
    where result_dimension.result_id = target_result_id
      and dimension.scale_id = target_scale_id
      and result_dimension.dimension_score = 1
      and dimension.dimension_code in ('RECODE_SUM', 'SLEEP_DURATION', 'SLEEP_EFFICIENCY');
    if actual_count <> 3 then
        raise exception 'recode matrix persisted dimension scores failed, found %', actual_count;
    end if;
    select count(*) into actual_count
    from jsonb_array_elements((select scoring_trace_json -> 'dimensions' from psy_assessment_result where id = target_result_id)) trace_dimension
    join psy_scale_dimension dimension on dimension.id = (trace_dimension ->> 'dimensionId')::bigint
    where dimension.scale_id = target_scale_id
      and dimension.dimension_code in ('RECODE_SUM', 'SLEEP_DURATION', 'SLEEP_EFFICIENCY')
      and (trace_dimension ->> 'score')::numeric = 1
      and trace_dimension ->> 'aggregation' = 'SUM';
    if actual_count <> 3 then
        raise exception 'recode matrix dimension trace scores failed, found %', actual_count;
    end if;
    select count(*) into actual_count
    from psy_assessment_result_dimension result_dimension
    join psy_scale_dimension dimension on dimension.id = result_dimension.dimension_id
    where result_dimension.result_id = target_result_id
      and dimension.scale_id = target_scale_id
      and dimension.dimension_code = 'INPUT_TYPES'
      and result_dimension.dimension_score = 0;
    if actual_count <> 1 then
        raise exception 'recode matrix advanced question dimension score failed, found %', actual_count;
    end if;

    -- Recompute the weighted global total from the item trace to prove the
    -- generic path did not use a scale-code branch.
    select count(*) into actual_count
    from psy_assessment_result result
    where result.id = target_result_id
      and round((select sum((trace_question ->> 'effectiveScore')::numeric)
                 from jsonb_array_elements(result.scoring_trace_json -> 'questions') trace_question), 4) = 6.60;
    if actual_count <> 1 then
        raise exception 'recode matrix global weighted total recomputation failed';
    end if;

    select count(*) into actual_count
    from psy_report report
    where report.result_id = target_result_id
      and coalesce(report.report_content, '') <> '';
    if actual_count < 1 then
        raise exception 'recode matrix expected a report snapshot';
    end if;

    raise notice 'RECODE_MATRIX_CHECK|rule_RECODE_SUM_TO_0_3|PASS';
    raise notice 'RECODE_MATRIX_CHECK|rule_SLEEP_DURATION_RECODE_0_3|PASS';
    raise notice 'RECODE_MATRIX_CHECK|rule_SLEEP_EFFICIENCY_RECODE_0_3|PASS';
    raise notice 'RECODE_MATRIX_CHECK|question_types|PASS';
    raise notice 'RECODE_MATRIX_CHECK|skip_branch|PASS';
    raise notice 'RECODE_MATRIX_CHECK|all_recode_rules|PASS';
end $$;
