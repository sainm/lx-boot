-- Run before explicitly baselining an existing installation at V1. The target
-- is current_schema(), so both the production public schema and isolated
-- PostgreSQL upgrade tests exercise the same checks. This script is read-only.
do $$
declare
    required_table text;
    required_column text;
    required_index text;
    required_tables constant text[] := array[
        'sys_user', 'sys_auth', 'sys_tenant', 'sys_group', 'sys_role',
        'sys_permission', 'sys_user_role', 'sys_group_role',
        'sys_role_permission', 'sys_login_log', 'sys_security_event',
        'sys_token_blacklist', 'sys_user_session_policy', 'sys_user_session',
        'sys_qr_scene', 'psy_scale', 'psy_scale_dimension',
        'psy_scale_question', 'psy_scale_option', 'psy_scale_result_rule',
        'psy_scale_norm', 'psy_scale_visualization_config',
        'psy_scale_high_risk_rule', 'psy_scale_import_job',
        'psy_scale_import_issue', 'psy_assessment_task',
        'psy_assessment_task_assignment', 'psy_assessment_answer_sheet',
        'psy_assessment_answer_item', 'psy_assessment_result', 'psy_report',
        'psy_warning_record', 'psy_warning_assignment',
        'psy_intervention_record', 'psy_intervention_status_log',
        'psy_counselor_schedule', 'psy_appointment_record',
        'psy_counseling_record', 'psy_notification',
        'psy_assessment_result_dimension', 'psy_notification_policy',
        'psy_notification_delivery', 'psy_user_device', 'psy_export_job'
    ];
    required_columns constant text[] := array[
        'sys_user.tenant_id', 'sys_user.password_version',
        'sys_user_session.device_id', 'psy_scale.version_group_id',
        'psy_scale.norm_strategy', 'psy_assessment_task.scale_version_no',
        'psy_assessment_task.closed_at', 'psy_assessment_answer_sheet.submit_token',
        'psy_assessment_result.score_source', 'psy_warning_record.escalation_count',
        'psy_intervention_record.retest_task_id', 'psy_notification.deep_link',
        'psy_notification_delivery.delivery_status', 'psy_notification_delivery.updated_at',
        'psy_export_job.file_path', 'psy_export_job.file_size'
    ];
    required_indexes constant text[] := array[
        'uk_sys_user_username', 'uk_sys_auth_identity_principal',
        'uk_sys_user_role', 'uk_psy_scale_code_version',
        'uk_psy_scale_question_no', 'uk_psy_answer_sheet_submit_token_user_task',
        'uk_psy_notification_policy_type', 'uk_psy_user_device_user_device',
        'idx_psy_export_job_status'
    ];
begin
    foreach required_table in array required_tables loop
        if to_regclass(format('%I.%I', current_schema(), required_table)) is null then
            raise exception 'Flyway baseline blocked: required table %.% is missing', current_schema(), required_table;
        end if;
    end loop;

    foreach required_column in array required_columns loop
        if not exists (
            select 1
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = split_part(required_column, '.', 1)
              and column_name = split_part(required_column, '.', 2)
        ) then
            raise exception 'Flyway baseline blocked: required column %.% is missing', current_schema(), required_column;
        end if;
    end loop;

    foreach required_index in array required_indexes loop
        if to_regclass(format('%I.%I', current_schema(), required_index)) is null then
            raise exception 'Flyway baseline blocked: required index %.% is missing', current_schema(), required_index;
        end if;
    end loop;

    if to_regclass(format('%I.flyway_schema_history', current_schema())) is not null then
        raise exception 'Flyway baseline blocked: %.flyway_schema_history already exists; inspect it instead of baselining again', current_schema();
    end if;

    if exists (select 1 from psy_scale where status not in ('DRAFT', 'PUBLISHED', 'ARCHIVED')) then
        raise exception 'Flyway baseline blocked: psy_scale contains unsupported status values';
    end if;
    if exists (select 1 from psy_assessment_task where status not in ('DRAFT', 'IN_PROGRESS', 'OVERDUE', 'CLOSED') or end_time <= start_time) then
        raise exception 'Flyway baseline blocked: psy_assessment_task contains invalid status or time range';
    end if;
    if exists (select 1 from psy_assessment_answer_sheet where answer_status not in ('DRAFT', 'SUBMITTED') or duration_seconds < 0) then
        raise exception 'Flyway baseline blocked: psy_assessment_answer_sheet contains invalid status or duration';
    end if;
    if exists (select 1 from psy_warning_record where status not in ('PENDING', 'ASSIGNED', 'PROCESSING', 'CLOSED') or escalation_count < 0) then
        raise exception 'Flyway baseline blocked: psy_warning_record contains invalid status or escalation count';
    end if;
    if exists (select 1 from psy_counselor_schedule where status not in ('AVAILABLE', 'CLOSED') or quota_count <= 0 or end_time <= start_time) then
        raise exception 'Flyway baseline blocked: psy_counselor_schedule contains invalid status, quota, or time range';
    end if;
    if exists (select 1 from psy_appointment_record where appointment_status not in ('CREATED', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW') or source_type not in ('USER', 'ADMIN')) then
        raise exception 'Flyway baseline blocked: psy_appointment_record contains invalid status or source';
    end if;
    if exists (select 1 from psy_notification_delivery where delivery_status not in ('PENDING', 'PROCESSING', 'SENT', 'DELIVERED', 'FAILED', 'CLICKED', 'SKIPPED', 'DEAD_LETTER')) then
        raise exception 'Flyway baseline blocked: psy_notification_delivery contains unsupported status values';
    end if;
    if exists (select 1 from psy_export_job where status not in ('PENDING', 'PROCESSING', 'DONE', 'FAILED') or file_size < 0) then
        raise exception 'Flyway baseline blocked: psy_export_job contains invalid status or file size';
    end if;
end
$$;

select 'baseline_preflight_ok' as result,
       current_database() as database_name,
       current_user as database_user,
       count(*) filter (where table_name like 'psy\_%' escape '\') as psychology_table_count
from information_schema.tables
where table_schema = current_schema()
  and table_type = 'BASE TABLE';
