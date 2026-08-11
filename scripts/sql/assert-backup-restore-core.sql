\set ON_ERROR_STOP on

do $$
declare
    missing_tables text;
    migration_count integer;
    invalid_core_rows bigint;
begin
    select string_agg(required.table_name, ', ' order by required.table_name)
    into missing_tables
    from (
        values
            ('flyway_schema_history'),
            ('sys_login_log'),
            ('sys_security_event'),
            ('psy_assessment_task'),
            ('psy_assessment_answer_sheet'),
            ('psy_assessment_result'),
            ('psy_report'),
            ('psy_warning_record'),
            ('psy_intervention_record'),
            ('psy_notification_delivery'),
            ('psy_export_job')
    ) as required(table_name)
    where to_regclass('public.' || required.table_name) is null;

    if missing_tables is not null then
        raise exception 'backup rehearsal is missing required tables: %', missing_tables;
    end if;

    select count(*) into migration_count
    from flyway_schema_history
    where success;
    if migration_count <> 22 then
        raise exception 'expected 22 successful Flyway migrations, found %', migration_count;
    end if;

    if not exists (select 1 from sys_login_log where result = 'SUCCESS') then
        raise exception 'successful login audit was not preserved';
    end if;
    if not exists (select 1 from sys_security_event) then
        raise exception 'security audit event was not preserved';
    end if;
    if not exists (select 1 from psy_assessment_task) then
        raise exception 'assessment task data was not preserved';
    end if;
    if not exists (select 1 from psy_assessment_answer_sheet where answer_status = 'SUBMITTED') then
        raise exception 'submitted answer sheet data was not preserved';
    end if;
    if not exists (select 1 from psy_assessment_result) then
        raise exception 'assessment result data was not preserved';
    end if;
    if not exists (select 1 from psy_report) then
        raise exception 'report data was not preserved';
    end if;
    if not exists (select 1 from psy_warning_record) then
        raise exception 'warning data was not preserved';
    end if;
    if not exists (select 1 from psy_intervention_record) then
        raise exception 'intervention data was not preserved';
    end if;
    if not exists (select 1 from psy_notification_delivery) then
        raise exception 'notification delivery data was not preserved';
    end if;
    if not exists (
        select 1 from psy_export_job
        where status = 'DONE'
          and file_bytes is not null
          and file_size = octet_length(file_bytes)
    ) then
        raise exception 'completed database-backed export artifact was not preserved';
    end if;

    select count(*) into invalid_core_rows
    from psy_assessment_answer_sheet sheet
    join psy_assessment_task task on task.id = sheet.task_id
    join psy_scale scale on scale.id = sheet.scale_id
    where sheet.tenant_id is distinct from task.tenant_id
       or sheet.tenant_id is distinct from scale.tenant_id
       or task.scale_id <> sheet.scale_id;
    if invalid_core_rows <> 0 then
        raise exception 'found % answer sheets with an invalid tenant or scale parent chain', invalid_core_rows;
    end if;

    select count(*) into invalid_core_rows
    from psy_assessment_result result
    left join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.id is null;
    if invalid_core_rows <> 0 then
        raise exception 'found % orphan assessment results', invalid_core_rows;
    end if;

    select count(*) into invalid_core_rows
    from psy_report report
    left join psy_assessment_result result on result.id = report.result_id
    where result.id is null;
    if invalid_core_rows <> 0 then
        raise exception 'found % orphan reports', invalid_core_rows;
    end if;

    select count(*) into invalid_core_rows
    from psy_warning_record warning
    join psy_assessment_result result on result.id = warning.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where warning.tenant_id is distinct from sheet.tenant_id;
    if invalid_core_rows <> 0 then
        raise exception 'found % warnings with an invalid tenant parent chain', invalid_core_rows;
    end if;

    select count(*) into invalid_core_rows
    from psy_intervention_record intervention
    join psy_warning_record warning on warning.id = intervention.warning_id
    where intervention.tenant_id is distinct from warning.tenant_id;
    if invalid_core_rows <> 0 then
        raise exception 'found % interventions with an invalid tenant parent chain', invalid_core_rows;
    end if;
end
$$;

select format(
    'backup restore core verified: migrations=%s, tasks=%s, sheets=%s, results=%s, reports=%s, warnings=%s, interventions=%s, audit_events=%s, exports=%s',
    (select count(*) from flyway_schema_history where success),
    (select count(*) from psy_assessment_task),
    (select count(*) from psy_assessment_answer_sheet),
    (select count(*) from psy_assessment_result),
    (select count(*) from psy_report),
    (select count(*) from psy_warning_record),
    (select count(*) from psy_intervention_record),
    (select count(*) from sys_security_event),
    (select count(*) from psy_export_job)
) as verification_summary;
