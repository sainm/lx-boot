-- Phase 1 of tenant ownership hardening. Run the V16 read-only ownership
-- preflight first. NOT VALID keeps constraint creation from scanning tables;
-- explicit validation then performs the controlled historical-data scan.
set local lock_timeout = '5s';
set local statement_timeout = '10min';

alter table psy_intervention_status_log
    add constraint fk_psy_intervention_log_tenant
    foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_warning_assignment
    add constraint fk_psy_warning_assignment_tenant
    foreign key (tenant_id) references sys_tenant(id) not valid;

alter table psy_appointment_record add constraint ck_psy_appointment_tenant_required check (tenant_id is not null) not valid;
alter table psy_assessment_answer_sheet add constraint ck_psy_answer_tenant_required check (tenant_id is not null) not valid;
alter table psy_assessment_task add constraint ck_psy_task_tenant_required check (tenant_id is not null) not valid;
alter table psy_counseling_record add constraint ck_psy_counseling_tenant_required check (tenant_id is not null) not valid;
alter table psy_counselor_schedule add constraint ck_psy_schedule_tenant_required check (tenant_id is not null) not valid;
alter table psy_export_job add constraint ck_psy_export_tenant_required check (tenant_id is not null) not valid;
alter table psy_intervention_record add constraint ck_psy_intervention_tenant_required check (tenant_id is not null) not valid;
alter table psy_intervention_status_log add constraint ck_psy_intervention_log_tenant_required check (tenant_id is not null) not valid;
alter table psy_notification_delivery add constraint ck_psy_notification_delivery_tenant_required check (tenant_id is not null) not valid;
alter table psy_scale add constraint ck_psy_scale_tenant_required check (tenant_id is not null) not valid;
alter table psy_scale_golden_case add constraint ck_psy_golden_case_tenant_required check (tenant_id is not null) not valid;
alter table psy_scale_golden_case_run add constraint ck_psy_golden_run_tenant_required check (tenant_id is not null) not valid;
alter table psy_scale_import_job add constraint ck_psy_scale_import_tenant_required check (tenant_id is not null) not valid;
alter table psy_scale_publication_review add constraint ck_psy_publication_review_tenant_required check (tenant_id is not null) not valid;
alter table psy_warning_assignment add constraint ck_psy_warning_assignment_tenant_required check (tenant_id is not null) not valid;
alter table psy_warning_record add constraint ck_psy_warning_tenant_required check (tenant_id is not null) not valid;

alter table psy_appointment_record validate constraint fk_psy_appointment_tenant;
alter table psy_assessment_answer_sheet validate constraint fk_psy_answer_tenant;
alter table psy_assessment_task validate constraint fk_psy_task_tenant;
alter table psy_counseling_record validate constraint fk_psy_counseling_tenant;
alter table psy_counselor_schedule validate constraint fk_psy_schedule_tenant;
alter table psy_export_job validate constraint fk_psy_export_job_tenant;
alter table psy_intervention_record validate constraint fk_psy_intervention_tenant;
alter table psy_intervention_status_log validate constraint fk_psy_intervention_log_tenant;
alter table psy_notification_delivery validate constraint fk_psy_notification_delivery_tenant;
alter table psy_scale validate constraint fk_psy_scale_tenant;
alter table psy_scale_import_job validate constraint fk_psy_scale_import_job_tenant;
alter table psy_warning_assignment validate constraint fk_psy_warning_assignment_tenant;
alter table psy_warning_record validate constraint fk_psy_warning_tenant;

alter table psy_appointment_record validate constraint ck_psy_appointment_tenant_required;
alter table psy_assessment_answer_sheet validate constraint ck_psy_answer_tenant_required;
alter table psy_assessment_task validate constraint ck_psy_task_tenant_required;
alter table psy_counseling_record validate constraint ck_psy_counseling_tenant_required;
alter table psy_counselor_schedule validate constraint ck_psy_schedule_tenant_required;
alter table psy_export_job validate constraint ck_psy_export_tenant_required;
alter table psy_intervention_record validate constraint ck_psy_intervention_tenant_required;
alter table psy_intervention_status_log validate constraint ck_psy_intervention_log_tenant_required;
alter table psy_notification_delivery validate constraint ck_psy_notification_delivery_tenant_required;
alter table psy_scale validate constraint ck_psy_scale_tenant_required;
alter table psy_scale_golden_case validate constraint ck_psy_golden_case_tenant_required;
alter table psy_scale_golden_case_run validate constraint ck_psy_golden_run_tenant_required;
alter table psy_scale_import_job validate constraint ck_psy_scale_import_tenant_required;
alter table psy_scale_publication_review validate constraint ck_psy_publication_review_tenant_required;
alter table psy_warning_assignment validate constraint ck_psy_warning_assignment_tenant_required;
alter table psy_warning_record validate constraint ck_psy_warning_tenant_required;
