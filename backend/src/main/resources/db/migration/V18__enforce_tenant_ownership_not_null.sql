-- Phase 2 of tenant ownership hardening. V17's validated CHECK constraints let
-- PostgreSQL prove NOT NULL without a second full-table scan. ACCESS EXCLUSIVE
-- locks are still required briefly, so fail fast when a busy table cannot lock.
set local lock_timeout = '5s';
set local statement_timeout = '2min';

alter table psy_appointment_record alter column tenant_id set not null;
alter table psy_assessment_answer_sheet alter column tenant_id set not null;
alter table psy_assessment_task alter column tenant_id set not null;
alter table psy_counseling_record alter column tenant_id set not null;
alter table psy_counselor_schedule alter column tenant_id set not null;
alter table psy_export_job alter column tenant_id set not null;
alter table psy_intervention_record alter column tenant_id set not null;
alter table psy_intervention_status_log alter column tenant_id set not null;
alter table psy_notification_delivery alter column tenant_id set not null;
alter table psy_scale alter column tenant_id set not null;
alter table psy_scale_golden_case alter column tenant_id set not null;
alter table psy_scale_golden_case_run alter column tenant_id set not null;
alter table psy_scale_import_job alter column tenant_id set not null;
alter table psy_scale_publication_review alter column tenant_id set not null;
alter table psy_warning_assignment alter column tenant_id set not null;
alter table psy_warning_record alter column tenant_id set not null;
