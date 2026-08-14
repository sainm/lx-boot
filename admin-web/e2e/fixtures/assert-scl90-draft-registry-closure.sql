-- SCL-90 draft-only PostgreSQL evidence for the registry runner.
-- This remains separate because SCL90_PROFILE is a restricted algorithm and
-- the package is intentionally not published while external governance inputs
-- are missing.

select set_config('psy.registry.scale_code', :'scale_code', false);

do $$
declare
    code text := current_setting('psy.registry.scale_code');
    target_scale_id bigint;
    actual_count bigint;
begin
    if code <> 'SCL90_USER_DRAFT' then
        raise exception 'unsupported draft evidence scale code %', code;
    end if;
        select count(*), min(id)
          into actual_count, target_scale_id
        from psy_scale
        where scale_code = code
          and version_no = 'v1'
          and status = 'DRAFT';
        if actual_count <> 1 then
            raise exception 'registry SCL-90 expected one draft version, found %', actual_count;
        end if;

        select count(*) into actual_count
        from psy_scale_translation
        where scale_id = target_scale_id
          and locale_code in ('zh-CN', 'ja-JP', 'en');
        if actual_count <> 3 then
            raise exception 'registry SCL-90 expected three scale locales, found %', actual_count;
        end if;

        select count(*) into actual_count
        from psy_scale_dimension
        where scale_id = target_scale_id;
        if actual_count <> 10 then
            raise exception 'registry SCL-90 expected ten dimensions, found %', actual_count;
        end if;

        select count(*) into actual_count
        from psy_scale_question question
        where question.scale_id = target_scale_id;
        if actual_count <> 90 then
            raise exception 'registry SCL-90 expected ninety questions, found %', actual_count;
        end if;

        select count(*) into actual_count
        from psy_scale_question_translation translation
        join psy_scale_question question on question.id = translation.question_id
        where question.scale_id = target_scale_id
          and translation.locale_code in ('zh-CN', 'ja-JP', 'en');
        if actual_count <> 270 then
            raise exception 'registry SCL-90 expected 270 question translations, found %', actual_count;
        end if;

        select count(*) into actual_count
        from psy_scale_option_translation translation
        join psy_scale_option option on option.id = translation.option_id
        join psy_scale_question question on question.id = option.question_id
        where question.scale_id = target_scale_id
          and translation.locale_code in ('zh-CN', 'ja-JP', 'en');
        if actual_count <> 1350 then
            raise exception 'registry SCL-90 expected 1350 option translations, found %', actual_count;
        end if;

        select count(*) into actual_count
        from (
            select distinct on (golden_case.case_code)
                   golden_case.id,
                   golden_case.case_code
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
        if actual_count <> 5 then
            raise exception 'registry SCL-90 expected five passing latest Golden Cases, found %', actual_count;
        end if;

        select count(*) into actual_count
        from psy_scale_algorithm_binding
        where scale_id = target_scale_id
          and algorithm_code = 'SCL90_PROFILE'
          and algorithm_version = '1';
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
            select run.actual_json
            from psy_scale_golden_case_run run
            where run.golden_case_id = latest_case.id
            order by run.executed_at desc, run.id desc
            limit 1
        ) latest_run on true
        where latest_run.actual_json -> 'trace' ->> 'algorithmCode' = 'SCL90_PROFILE'
          and latest_run.actual_json -> 'trace' ->> 'algorithmVersion' = '1'
          and latest_run.actual_json -> 'trace' ->> 'scoreMethod' = 'SIMPLE_SUM'
          and jsonb_array_length(latest_run.actual_json -> 'trace' -> 'questions') = 90
          and jsonb_array_length(latest_run.actual_json -> 'trace' -> 'dimensions') = 10
          and latest_run.actual_json -> 'metrics' ? 'GSI'
          and latest_run.actual_json -> 'metrics' ? 'PST'
          and latest_run.actual_json -> 'metrics' ? 'PSDI';
        if actual_count <> 3 then
            raise exception 'registry SCL-90 expected three valid scoring traces with GSI/PST/PSDI, found %', actual_count;
        end if;

        select count(*) into actual_count
        from (
            select distinct on (golden_case.case_code) golden_case.case_code, golden_case.id
            from psy_scale_golden_case golden_case
            where golden_case.scale_id = target_scale_id
              and golden_case.case_code in ('SCL90_ALL_ZERO', 'SCL90_ALL_FOUR', 'SCL90_SELF_HARM_SIGNAL')
            order by golden_case.case_code, golden_case.revision_no desc, golden_case.id desc
        ) latest_case
        join lateral (
            select run.actual_json
            from psy_scale_golden_case_run run
            where run.golden_case_id = latest_case.id
            order by run.executed_at desc, run.id desc
            limit 1
        ) latest_run on true
        where latest_run.actual_json -> 'trace' ->> 'algorithmCode' = 'SCL90_PROFILE'
          and latest_run.actual_json -> 'trace' ->> 'algorithmVersion' = '1'
          and latest_run.actual_json -> 'trace' ->> 'scoreMethod' = 'SIMPLE_SUM'
          and jsonb_array_length(latest_run.actual_json -> 'trace' -> 'questions') = 90
          and jsonb_array_length(latest_run.actual_json -> 'trace' -> 'dimensions') = 10
          and latest_run.actual_json -> 'trace' -> 'derivedMetrics' ? 'GSI'
          and latest_run.actual_json -> 'trace' -> 'derivedMetrics' ? 'PST'
          and latest_run.actual_json -> 'trace' -> 'derivedMetrics' ? 'PSDI';
        if actual_count <> 3 then
            raise exception 'registry SCL-90 trace shape/derived metrics invariant failed, found %', actual_count;
        end if;

        select count(*) into actual_count
        from (
            select distinct on (golden_case.case_code) golden_case.id
            from psy_scale_golden_case golden_case
            where golden_case.scale_id = target_scale_id
              and golden_case.case_code = 'SCL90_ALL_ZERO'
            order by golden_case.case_code, golden_case.revision_no desc, golden_case.id desc
        ) latest_case
        join lateral (
            select run.actual_json
            from psy_scale_golden_case_run run
            where run.golden_case_id = latest_case.id
            order by run.executed_at desc, run.id desc
            limit 1
        ) latest_run on true
        where (latest_run.actual_json ->> 'totalScore')::numeric = 0
          and (latest_run.actual_json -> 'metrics' ->> 'GSI')::numeric = 0
          and (latest_run.actual_json -> 'metrics' ->> 'PST')::numeric = 0
          and (latest_run.actual_json -> 'metrics' ->> 'PSDI')::numeric = 0
          and not exists (
              select 1
              from jsonb_array_elements(latest_run.actual_json -> 'trace' -> 'questions') question
              where (question ->> 'rawScore')::numeric <> 0
                 or (question ->> 'reverseScore')::numeric <> 0
                 or (question ->> 'weightedScore')::numeric <> 0
                 or (question ->> 'effectiveScore')::numeric <> 0
          )
          and not exists (
              select 1
              from jsonb_array_elements(latest_run.actual_json -> 'trace' -> 'dimensions') dimension
              where (dimension ->> 'score')::numeric <> 0
          );
        if actual_count <> 1 then
            raise exception 'registry SCL-90 all-zero scoring trace invariant failed';
        end if;

        select count(*) into actual_count
        from (
            select distinct on (golden_case.case_code) golden_case.id
            from psy_scale_golden_case golden_case
            where golden_case.scale_id = target_scale_id
              and golden_case.case_code = 'SCL90_ALL_FOUR'
            order by golden_case.case_code, golden_case.revision_no desc, golden_case.id desc
        ) latest_case
        join lateral (
            select run.actual_json
            from psy_scale_golden_case_run run
            where run.golden_case_id = latest_case.id
            order by run.executed_at desc, run.id desc
            limit 1
        ) latest_run on true
        where (latest_run.actual_json ->> 'totalScore')::numeric = 360
          and (latest_run.actual_json -> 'metrics' ->> 'GSI')::numeric = 4
          and (latest_run.actual_json -> 'metrics' ->> 'PST')::numeric = 90
          and (latest_run.actual_json -> 'metrics' ->> 'PSDI')::numeric = 4
          and not exists (
              select 1
              from jsonb_array_elements(latest_run.actual_json -> 'trace' -> 'questions') question
              where (question ->> 'rawScore')::numeric <> 4
                 or (question ->> 'reverseScore')::numeric <> 4
                 or (question ->> 'weightedScore')::numeric <> 4
                 or (question ->> 'effectiveScore')::numeric <> 4
          )
          and not exists (
              select 1
              from jsonb_array_elements(latest_run.actual_json -> 'trace' -> 'dimensions') dimension
              where (dimension ->> 'score')::numeric <> 4
          );
        if actual_count <> 1 then
            raise exception 'registry SCL-90 all-four scoring trace invariant failed';
        end if;

        select count(*) into actual_count
        from (
            select distinct on (golden_case.case_code) golden_case.id
            from psy_scale_golden_case golden_case
            where golden_case.scale_id = target_scale_id
              and golden_case.case_code = 'SCL90_SELF_HARM_SIGNAL'
            order by golden_case.case_code, golden_case.revision_no desc, golden_case.id desc
        ) latest_case
        join lateral (
            select run.actual_json
            from psy_scale_golden_case_run run
            where run.golden_case_id = latest_case.id
            order by run.executed_at desc, run.id desc
            limit 1
        ) latest_run on true
        where (latest_run.actual_json ->> 'totalScore')::numeric = 4
          and (latest_run.actual_json -> 'metrics' ->> 'GSI')::numeric = 0.0444
          and (latest_run.actual_json -> 'metrics' ->> 'PST')::numeric = 1
          and (latest_run.actual_json -> 'metrics' ->> 'PSDI')::numeric = 4
          and (
              select count(*)
              from jsonb_array_elements(latest_run.actual_json -> 'trace' -> 'questions') question
              join psy_scale_question scale_question
                on scale_question.id = (question ->> 'questionId')::bigint
              where scale_question.question_no = 15
                and (question ->> 'effectiveScore')::numeric = 4
          ) = 1
          and (
              select count(*)
              from jsonb_array_elements(latest_run.actual_json -> 'trace' -> 'questions') question
              where (question ->> 'effectiveScore')::numeric = 4
          ) = 1
          and not exists (
              select 1
              from jsonb_array_elements(latest_run.actual_json -> 'trace' -> 'dimensions') dimension
              where (dimension ->> 'score')::numeric < 0
                 or (dimension ->> 'score')::numeric > 4
          );
        if actual_count <> 1 then
            raise exception 'registry SCL-90 self-harm scoring trace invariant failed';
        end if;

        raise notice 'REGISTRY_CHECK|source_package_integrity|PASS';
        raise notice 'REGISTRY_CHECK|golden_case_scores|PASS';
        raise notice 'REGISTRY_CHECK|scoring_trace|PASS';
        raise notice 'REGISTRY_CHECK|trilingual_result_content|NOT_APPLICABLE_DRAFT_ONLY';
        raise notice 'REGISTRY_CHECK|idempotent_submission|NOT_APPLICABLE_DRAFT_ONLY';
        raise notice 'REGISTRY_CHECK|concurrent_submission|NOT_APPLICABLE_DRAFT_ONLY';
        raise notice 'REGISTRY_CHECK|rescore_history|NOT_APPLICABLE_DRAFT_ONLY';
end $$;

