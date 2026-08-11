-- Disposable technical fixture for run-performance-baseline.sh.
-- This file is intentionally not a Flyway migration and must only be run in
-- a psy_perf_* schema created by that script. It contains no licensed or
-- clinical scale content.

\set ON_ERROR_STOP on

create table if not exists psy_perf_task_context (
    task_no integer primary key,
    task_id bigint not null,
    answer_sheet_id bigint,
    result_id bigint,
    report_id bigint,
    warning_id bigint,
    schedule_id bigint,
    appointment_id bigint,
    notification_id bigint,
    export_job_id varchar(128)
);

-- Keep a real group assignment in the fixture so the group-report query has
-- the same parent/child fan-out as the business path.
insert into sys_group (tenant_id, group_code, group_name, is_default)
select tenant.id, 'PERF_TECHNICAL_GROUP', 'Performance technical fixture group', 0
from sys_tenant tenant
where tenant.tenant_code = 'DEFAULT'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    updated_at = current_timestamp;

update sys_user
set group_id = (select id from sys_group where group_code = 'PERF_TECHNICAL_GROUP')
where username in ('respondent', 'counselor')
  and tenant_id = (select id from sys_tenant where tenant_code = 'DEFAULT');

-- Target rows are added incrementally. The script invokes this file first for
-- 1x and then for 10x, so the second invocation only creates the missing range.
with context as (
    select
        tenant.id as tenant_id,
        administrator.id as administrator_id,
        respondent.id as respondent_id,
        scale.id as scale_id,
        scale.version_no,
        scale.version_group_id,
        scale.published_content_hash
    from sys_tenant tenant
    join sys_user administrator on administrator.username = 'e2e_admin'
    join sys_user respondent on respondent.username = 'respondent'
    join psy_scale scale
      on scale.tenant_id = tenant.id
     and scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
     and scale.version_no = 'v1'
    where tenant.tenant_code = 'DEFAULT'
), new_tasks as (
    insert into psy_assessment_task (
        tenant_id, task_name, scale_id, scale_version_no, scale_version_group_id,
        scale_content_hash, task_mode, anonymous_flag, allow_save_flag,
        allow_timeout_submit_flag, allow_retake_flag, start_time, end_time,
        status, created_by, created_at, updated_at
    )
    select
        context.tenant_id,
        'PERF-TASK-' || series.value,
        context.scale_id,
        context.version_no,
        context.version_group_id,
        context.published_content_hash,
        'SCREENING',
        false,
        true,
        false,
        false,
        current_timestamp - ((series.value % 12) * interval '1 hour'),
        current_timestamp + interval '30 days',
        case when series.value % 17 = 0 then 'CLOSED' else 'IN_PROGRESS' end,
        context.administrator_id,
        current_timestamp - ((series.value % 12) * interval '1 hour'),
        current_timestamp
    from context
    cross join generate_series(:start_no, :target_no) as series(value)
    where not exists (
        select 1
        from psy_assessment_task existing
        where existing.task_name = 'PERF-TASK-' || series.value
          and existing.tenant_id = context.tenant_id
    )
    returning id, task_name
)
insert into psy_perf_task_context (task_no, task_id)
select regexp_replace(task_name, '^PERF-TASK-', '')::integer, id
from new_tasks
on conflict (task_no) do update set task_id = excluded.task_id;

-- Live tasks are deliberately separate from the completed rows. The HTTP
-- client uses them for actual save/submit/scoring measurements at each scale.
with context as (
    select
        tenant.id as tenant_id,
        administrator.id as administrator_id,
        scale.id as scale_id,
        scale.version_no,
        scale.version_group_id,
        scale.published_content_hash
    from sys_tenant tenant
    join sys_user administrator on administrator.username = 'e2e_admin'
    join psy_scale scale
      on scale.tenant_id = tenant.id
     and scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
     and scale.version_no = 'v1'
    where tenant.tenant_code = 'DEFAULT'
), new_live_tasks as (
    insert into psy_assessment_task (
        tenant_id, task_name, scale_id, scale_version_no, scale_version_group_id,
        scale_content_hash, task_mode, anonymous_flag, allow_save_flag,
        allow_timeout_submit_flag, allow_retake_flag, start_time, end_time,
        status, created_by, created_at, updated_at
    )
    select
        context.tenant_id,
        'PERF-LIVE-TASK-' || series.value,
        context.scale_id,
        context.version_no,
        context.version_group_id,
        context.published_content_hash,
        'SCREENING',
        false,
        true,
        false,
        false,
        current_timestamp,
        current_timestamp + interval '30 days',
        'IN_PROGRESS',
        context.administrator_id,
        current_timestamp,
        current_timestamp
    from context
    cross join generate_series(:live_start_no, :live_end_no) as series(value)
    where not exists (
        select 1
        from psy_assessment_task existing
        where existing.task_name = 'PERF-LIVE-TASK-' || series.value
          and existing.tenant_id = context.tenant_id
    )
    returning id
)
select count(*) from new_live_tasks;

-- USER and GROUP assignments exercise both the respondent path and the
-- expensive correlated group-statistics path.
insert into psy_assessment_task_assignment (task_id, target_type, target_id, assigned_by, assigned_at)
select context.task_id, 'USER', respondent.id, administrator.id, current_timestamp
from psy_perf_task_context context
join sys_user respondent on respondent.username = 'respondent'
join sys_user administrator on administrator.username = 'e2e_admin'
where context.task_no between :start_no and :target_no
  and not exists (
      select 1 from psy_assessment_task_assignment existing
      where existing.task_id = context.task_id
        and existing.target_type = 'USER'
        and existing.target_id = respondent.id
  );

insert into psy_assessment_task_assignment (task_id, target_type, target_id, assigned_by, assigned_at)
select context.task_id, 'GROUP', group_record.id, administrator.id, current_timestamp
from psy_perf_task_context context
join sys_group group_record on group_record.group_code = 'PERF_TECHNICAL_GROUP'
join sys_user administrator on administrator.username = 'e2e_admin'
where context.task_no between :start_no and :target_no
  and not exists (
      select 1 from psy_assessment_task_assignment existing
      where existing.task_id = context.task_id
        and existing.target_type = 'GROUP'
        and existing.target_id = group_record.id
  );

insert into psy_assessment_task_assignment (task_id, target_type, target_id, assigned_by, assigned_at)
select task.id, 'USER', respondent.id, administrator.id, current_timestamp
from psy_assessment_task task
join sys_user respondent on respondent.username = 'respondent'
join sys_user administrator on administrator.username = 'e2e_admin'
where task.task_name like 'PERF-LIVE-TASK-%'
  and task.tenant_id = (select id from sys_tenant where tenant_code = 'DEFAULT')
  and not exists (
      select 1 from psy_assessment_task_assignment existing
      where existing.task_id = task.id
        and existing.target_type = 'USER'
        and existing.target_id = respondent.id
  );

-- One submitted sheet, result and report per target task. All values are
-- technical fixture values and intentionally do not represent a clinical case.
with new_sheets as (
    insert into psy_assessment_answer_sheet (
        tenant_id, task_id, scale_id, user_id, answer_status, version_no,
        start_time, submit_time, duration_seconds, submit_token, response_locale_code,
        created_at, updated_at
    )
    select task.tenant_id, task.id, task.scale_id, respondent.id, 'SUBMITTED', 1,
           current_timestamp - interval '10 minutes', current_timestamp - interval '5 minutes',
           300, 'PERF-SUBMIT-' || context.task_no, 'en', current_timestamp, current_timestamp
    from psy_perf_task_context context
    join psy_assessment_task task on task.id = context.task_id
    join sys_user respondent on respondent.username = 'respondent'
    where context.task_no between :start_no and :target_no
      and context.answer_sheet_id is null
    returning id, task_id
), updated_context as (
    update psy_perf_task_context context
    set answer_sheet_id = new_sheets.id
    from new_sheets
    where context.task_id = new_sheets.task_id
    returning context.task_no, context.answer_sheet_id
)
select count(*) from updated_context;

insert into psy_assessment_answer_item (
    answer_sheet_id, question_id, option_id, answer_text, answer_value, score_value
)
select context.answer_sheet_id, question.id, option.id, option.option_label,
       option.score_value, option.score_value
from psy_perf_task_context context
join psy_scale_question question
  on question.scale_id = (select scale_id from psy_assessment_task where id = context.task_id)
join lateral (
    select candidate.id, candidate.option_label, candidate.score_value
    from psy_scale_option candidate
    where candidate.question_id = question.id
    order by candidate.sort_no, candidate.id
    limit 1
) option on true
where context.task_no between :start_no and :target_no
  and context.answer_sheet_id is not null
  and not exists (
      select 1 from psy_assessment_answer_item existing
      where existing.answer_sheet_id = context.answer_sheet_id
  );

with new_results as (
    insert into psy_assessment_result (
        answer_sheet_id, total_score, risk_level, warning_flag, result_summary,
        score_source, standard_score, z_score, t_score, norm_code,
        high_risk_flag, high_risk_rule_code, calculation_version, is_current,
        scoring_engine_version, created_at, scored_at
    )
    select context.answer_sheet_id,
           case when context.task_no % 10 = 0 then 12.0 else 3.0 end,
           case when context.task_no % 10 = 0 then 'HIGH' else 'NORMAL' end,
           context.task_no % 10 = 0,
           'Performance technical fixture; not clinical content.',
           'RAW_SCORE',
           case when context.task_no % 10 = 0 then 12.0 else 3.0 end,
           0.0,
           case when context.task_no % 10 = 0 then 70.0 else 50.0 end,
           'PERF_TECHNICAL',
           context.task_no % 10 = 0,
           case when context.task_no % 10 = 0 then 'E2E_HIGH_OPTION' else null end,
           1,
           true,
           'performance-fixture-v1',
           current_timestamp,
           current_timestamp
    from psy_perf_task_context context
    where context.task_no between :start_no and :target_no
      and context.answer_sheet_id is not null
      and context.result_id is null
    returning id, answer_sheet_id
), updated_context as (
    update psy_perf_task_context context
    set result_id = new_results.id
    from new_results
    where context.answer_sheet_id = new_results.answer_sheet_id
    returning context.task_no, context.result_id
)
select count(*) from updated_context;

with new_reports as (
    insert into psy_report (
        result_id, report_type, author_user_id, report_title, report_content,
        locale_code, version_no, created_at, updated_at
    )
    select context.result_id, 'PERSONAL', administrator.id,
           'Performance technical report',
           'Performance technical fixture; not clinical content.',
           'en', 1, current_timestamp, current_timestamp
    from psy_perf_task_context context
    join sys_user administrator on administrator.username = 'e2e_admin'
    where context.task_no between :start_no and :target_no
      and context.result_id is not null
      and context.report_id is null
    returning id, result_id
), updated_context as (
    update psy_perf_task_context context
    set report_id = new_reports.id
    from new_reports
    where context.result_id = new_reports.result_id
    returning context.task_no, context.report_id
)
select count(*) from updated_context;

with new_warnings as (
    insert into psy_warning_record (
        tenant_id, result_id, warning_level, warning_priority, warning_reason,
        status, deadline_time, policy_resolution_status, created_at, updated_at
    )
    select task.tenant_id, context.result_id, 'HIGH', 'P1',
           'Performance technical fixture warning; not clinical content.',
           'PENDING', current_timestamp + interval '1 day', 'MISSING',
           current_timestamp, current_timestamp
    from psy_perf_task_context context
    join psy_assessment_task task on task.id = context.task_id
    where context.task_no between :start_no and :target_no
      and context.task_no % 10 = 0
      and context.result_id is not null
      and context.warning_id is null
    returning id, result_id
), updated_context as (
    update psy_perf_task_context context
    set warning_id = new_warnings.id
    from new_warnings
    where context.result_id = new_warnings.result_id
    returning context.task_no, context.warning_id
)
select count(*) from updated_context;

-- Appointment/schedule rows exercise quota and tenant joins without creating
-- any real counseling appointment.
with new_schedules as (
    insert into psy_counselor_schedule (
        tenant_id, counselor_user_id, schedule_date, start_time, end_time,
        quota_count, status, created_at
    )
    select task.tenant_id, counselor.id,
           current_date + context.task_no,
           current_timestamp + (context.task_no * interval '1 day'),
           current_timestamp + (context.task_no * interval '1 day') + interval '1 hour',
           1000, 'AVAILABLE', current_timestamp
    from psy_perf_task_context context
    join psy_assessment_task task on task.id = context.task_id
    join sys_user counselor on counselor.username = 'counselor'
    where context.task_no between :start_no and :target_no
      and context.task_no % 20 = 0
      and context.schedule_id is null
    returning id, schedule_date
)
select count(*) from new_schedules;

update psy_perf_task_context context
set schedule_id = schedule.id
from psy_counselor_schedule schedule
where context.task_no between :start_no and :target_no
  and context.task_no % 20 = 0
  and context.schedule_id is null
  and schedule.counselor_user_id = (select id from sys_user where username = 'counselor')
  and schedule.schedule_date = current_date + context.task_no;

with new_appointments as (
    insert into psy_appointment_record (
        tenant_id, user_id, counselor_user_id, warning_id, schedule_id,
        appointment_status, source_type, remark, created_at, updated_at
    )
    select task.tenant_id, respondent.id, counselor.id, context.warning_id, context.schedule_id,
           'CONFIRMED', 'USER', 'Performance technical fixture appointment.',
           current_timestamp, current_timestamp
    from psy_perf_task_context context
    join psy_assessment_task task on task.id = context.task_id
    join sys_user respondent on respondent.username = 'respondent'
    join sys_user counselor on counselor.username = 'counselor'
    where context.task_no between :start_no and :target_no
      and context.schedule_id is not null
      and context.appointment_id is null
    returning id, schedule_id
), updated_context as (
    update psy_perf_task_context context
    set appointment_id = new_appointments.id
    from new_appointments
    where context.schedule_id = new_appointments.schedule_id
    returning context.task_no, context.appointment_id
)
select count(*) from updated_context;

with new_notifications as (
    insert into psy_notification (
        notification_type, title, content, biz_type, biz_id,
        target_path, target_type, target_id, deep_link, payload_json, created_at
    )
    select 'REPORT_GENERATED', 'Performance technical notification',
           'Performance technical fixture notification; not clinical content.',
           'REPORT', context.report_id, '/reports/' || context.report_id,
           'REPORT', context.report_id, '/reports/' || context.report_id,
           '{"fixture":"performance"}', current_timestamp
    from psy_perf_task_context context
    where context.task_no between :start_no and :target_no
      and context.report_id is not null
      and context.notification_id is null
    returning id, biz_id
), updated_context as (
    update psy_perf_task_context context
    set notification_id = new_notifications.id
    from new_notifications
    where context.report_id = new_notifications.biz_id
    returning context.task_no, context.notification_id
)
select count(*) from updated_context;

insert into psy_notification_delivery (
    tenant_id, notification_id, receiver_user_id, read_flag, delivery_channel,
    delivery_status, created_at, updated_at
)
select tenant.id, context.notification_id, respondent.id, false, 'IN_APP', 'SENT',
       current_timestamp, current_timestamp
from psy_perf_task_context context
join sys_tenant tenant on tenant.tenant_code = 'DEFAULT'
join sys_user respondent on respondent.username = 'respondent'
where context.task_no between :start_no and :target_no
  and context.notification_id is not null
  and not exists (
      select 1 from psy_notification_delivery existing
      where existing.notification_id = context.notification_id
        and existing.receiver_user_id = respondent.id
        and existing.delivery_channel = 'IN_APP'
  );

insert into psy_export_job (
    id, tenant_id, created_by, status, report_id, result_id, export_format,
    locale_tag, desensitized_flag, file_name, content_type, file_size,
    created_at, completed_at, updated_at
)
select
    'perf-export-' || context.task_no,
    task.tenant_id,
    administrator.id,
    'DONE',
    context.report_id,
    context.result_id,
    'TEXT',
    'en-US',
    true,
    'performance-' || context.task_no || '.txt',
    'text/plain',
    64,
    current_timestamp,
    current_timestamp,
    current_timestamp
from psy_perf_task_context context
join psy_assessment_task task on task.id = context.task_id
join sys_user administrator on administrator.username = 'e2e_admin'
where context.task_no between :start_no and :target_no
  and context.report_id is not null
  and not exists (
      select 1 from psy_export_job existing where existing.id = 'perf-export-' || context.task_no
  );

update psy_perf_task_context context
set export_job_id = 'perf-export-' || context.task_no
where context.task_no between :start_no and :target_no;

analyze psy_assessment_task;
analyze psy_assessment_task_assignment;
analyze psy_assessment_answer_sheet;
analyze psy_assessment_answer_item;
analyze psy_assessment_result;
analyze psy_report;
analyze psy_warning_record;
analyze psy_counselor_schedule;
analyze psy_appointment_record;
analyze psy_notification;
analyze psy_notification_delivery;
analyze psy_export_job;
