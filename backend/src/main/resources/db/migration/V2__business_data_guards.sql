-- Enforce valid values for new and changed rows immediately, without forcing a
-- full-table validation scan during the deployment transaction. Existing data
-- must be checked by the preflight report before these constraints are later
-- validated in a dedicated migration.

alter table psy_scale
    add constraint ck_psy_scale_status
    check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')) not valid;

alter table psy_assessment_task
    add constraint ck_psy_assessment_task_status
    check (status in ('DRAFT', 'IN_PROGRESS', 'OVERDUE', 'CLOSED')) not valid;
alter table psy_assessment_task
    add constraint ck_psy_assessment_task_time_range
    check (end_time > start_time) not valid;

alter table psy_assessment_answer_sheet
    add constraint ck_psy_answer_sheet_status
    check (answer_status in ('DRAFT', 'SUBMITTED')) not valid;
alter table psy_assessment_answer_sheet
    add constraint ck_psy_answer_sheet_duration
    check (duration_seconds is null or duration_seconds >= 0) not valid;

alter table psy_warning_record
    add constraint ck_psy_warning_status
    check (status in ('PENDING', 'ASSIGNED', 'PROCESSING', 'CLOSED')) not valid;
alter table psy_warning_record
    add constraint ck_psy_warning_escalation_count
    check (escalation_count >= 0) not valid;

alter table psy_intervention_record
    add constraint ck_psy_intervention_status
    check (current_status in ('PROCESSING', 'CLOSED')) not valid;

alter table psy_counselor_schedule
    add constraint ck_psy_counselor_schedule_status
    check (status in ('AVAILABLE', 'CLOSED')) not valid;
alter table psy_counselor_schedule
    add constraint ck_psy_counselor_schedule_quota
    check (quota_count > 0) not valid;
alter table psy_counselor_schedule
    add constraint ck_psy_counselor_schedule_time_range
    check (end_time > start_time) not valid;

alter table psy_appointment_record
    add constraint ck_psy_appointment_status
    check (appointment_status in ('CREATED', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')) not valid;
alter table psy_appointment_record
    add constraint ck_psy_appointment_source
    check (source_type in ('USER', 'ADMIN')) not valid;

alter table psy_notification_policy
    add constraint ck_psy_notification_policy_cooldown
    check (cooldown_minutes >= 0) not valid;

alter table psy_notification_delivery
    add constraint ck_psy_notification_delivery_status
    check (delivery_status in ('PENDING', 'PROCESSING', 'SENT', 'DELIVERED', 'FAILED', 'CLICKED')) not valid;

alter table psy_export_job
    add constraint ck_psy_export_job_status
    check (status in ('PENDING', 'PROCESSING', 'DONE', 'FAILED')) not valid;
alter table psy_export_job
    add constraint ck_psy_export_job_file_size
    check (file_size is null or file_size >= 0) not valid;
