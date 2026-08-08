-- Read-only checks before applying V5-V7 to an existing PostgreSQL database.
-- Every returned count must be reviewed. Duplicate/invalid/conflict counts must
-- be zero. Unmapped tenant counts require an explicit quarantine or backfill
-- decision before migration.

select 'duplicate_active_user_drafts' as check_name, count(*) as issue_count
from (
    select task_id, user_id from psy_assessment_answer_sheet
    where answer_status = 'DRAFT' and user_id is not null
    group by task_id, user_id having count(*) > 1
) x
union all
select 'duplicate_active_anonymous_drafts', count(*) from (
    select task_id, anonymous_token from psy_assessment_answer_sheet
    where answer_status = 'DRAFT' and user_id is null and anonymous_token is not null
    group by task_id, anonymous_token having count(*) > 1
) x
union all
select 'invalid_answer_identity', count(*) from psy_assessment_answer_sheet
where not ((user_id is not null and anonymous_token is null) or (user_id is null and anonymous_token is not null))
union all
select 'duplicate_task_targets', count(*) from (
    select task_id, target_type, target_id from psy_assessment_task_assignment
    group by task_id, target_type, target_id having count(*) > 1
) x
union all
select 'duplicate_warning_results', count(*) from (
    select result_id from psy_warning_record group by result_id having count(*) > 1
) x
union all
select 'duplicate_report_versions', count(*) from (
    select result_id, version_no from psy_report group by result_id, version_no having count(*) > 1
) x
union all
select 'unsupported_score_methods', count(*) from psy_scale
where score_method not in ('SIMPLE_SUM', 'REVERSE_SUM', 'WEIGHTED_SUM', 'AVERAGE', 'WEIGHTED_AVERAGE');

select 'scale_unmapped' as check_name, count(*) as issue_count
from psy_scale s left join sys_user u on u.id = s.created_by
where u.tenant_id is null
union all
select 'task_unmapped', count(*) from psy_assessment_task t
left join sys_user creator on creator.id = t.created_by
left join psy_scale s on s.id = t.scale_id
left join sys_user scale_creator on scale_creator.id = s.created_by
where coalesce(creator.tenant_id, scale_creator.tenant_id) is null
union all
select 'answer_unmapped', count(*) from psy_assessment_answer_sheet a
left join sys_user subject on subject.id = a.user_id
left join psy_assessment_task t on t.id = a.task_id
left join sys_user task_creator on task_creator.id = t.created_by
left join psy_scale s on s.id = t.scale_id
left join sys_user scale_creator on scale_creator.id = s.created_by
where coalesce(subject.tenant_id, task_creator.tenant_id, scale_creator.tenant_id) is null
union all
select 'warning_unmapped', count(*) from psy_warning_record w
join psy_assessment_result r on r.id = w.result_id
join psy_assessment_answer_sheet a on a.id = r.answer_sheet_id
left join sys_user subject on subject.id = a.user_id
left join psy_assessment_task t on t.id = a.task_id
left join sys_user task_creator on task_creator.id = t.created_by
where coalesce(subject.tenant_id, task_creator.tenant_id) is null
union all
select 'appointment_unmapped', count(*) from psy_appointment_record a
left join sys_user u on u.id = a.user_id
left join sys_user c on c.id = a.counselor_user_id
left join psy_counselor_schedule s on s.id = a.schedule_id
left join sys_user schedule_counselor on schedule_counselor.id = s.counselor_user_id
where coalesce(u.tenant_id, c.tenant_id, schedule_counselor.tenant_id) is null
union all
select 'notification_delivery_unmapped', count(*) from psy_notification_delivery d
left join sys_user u on u.id = d.receiver_user_id
where u.tenant_id is null;

select 'task_creator_scale_tenant_conflict' as check_name, count(*) as issue_count
from psy_assessment_task t
join sys_user u on u.id = t.created_by
join psy_scale s on s.id = t.scale_id
join sys_user scale_creator on scale_creator.id = s.created_by
where u.tenant_id is not null and scale_creator.tenant_id is not null and u.tenant_id <> scale_creator.tenant_id
union all
select 'answer_user_task_tenant_conflict', count(*)
from psy_assessment_answer_sheet a
join sys_user u on u.id = a.user_id
join psy_assessment_task t on t.id = a.task_id
join sys_user task_creator on task_creator.id = t.created_by
where u.tenant_id is not null and task_creator.tenant_id is not null and u.tenant_id <> task_creator.tenant_id
union all
select 'appointment_participant_tenant_conflict', count(*)
from psy_appointment_record a
join sys_user u on u.id = a.user_id
join sys_user c on c.id = a.counselor_user_id
where u.tenant_id is not null and c.tenant_id is not null and u.tenant_id <> c.tenant_id;
