-- Durable export-job leases and retry state. Existing rows keep their current
-- status; no report content or artifact is rewritten by this migration.

set local lock_timeout = '5s';
set local statement_timeout = '2min';

alter table psy_export_job add column if not exists retry_count integer not null default 0;
alter table psy_export_job add column if not exists next_retry_at timestamp;
alter table psy_export_job add column if not exists processing_started_at timestamp;
alter table psy_export_job add column if not exists processing_token varchar(64);
alter table psy_export_job add column if not exists dead_letter_at timestamp;

do $$
begin
    if exists (select 1 from psy_export_job where status = 'PROCESSING') then
        raise exception using
            message = 'V19 requires all legacy PROCESSING export jobs to be drained or explicitly failed before migration',
            hint = 'Stop old export workers, inspect PROCESSING rows, then retry the migration without deleting job history.';
    end if;
end
$$;

alter table psy_export_job drop constraint if exists ck_psy_export_job_status;
alter table psy_export_job
    add constraint ck_psy_export_job_status
    check (status in ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'DEAD_LETTER')) not valid;
alter table psy_export_job validate constraint ck_psy_export_job_status;

alter table psy_export_job
    add constraint ck_psy_export_job_retry_count
    check (retry_count >= 0) not valid;
alter table psy_export_job validate constraint ck_psy_export_job_retry_count;

alter table psy_export_job
    add constraint ck_psy_export_job_processing_lease
    check (
        (status = 'PROCESSING' and processing_started_at is not null and processing_token is not null)
        or status <> 'PROCESSING'
    ) not valid;
alter table psy_export_job validate constraint ck_psy_export_job_processing_lease;

create index if not exists idx_psy_export_job_pending_retry
    on psy_export_job(next_retry_at, created_at, id)
    where status = 'PENDING';
