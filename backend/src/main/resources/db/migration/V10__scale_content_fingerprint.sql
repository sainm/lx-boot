-- New publications receive a SHA-256 fingerprint in application code.
-- Legacy published rows intentionally remain null: their complete historical content
-- cannot be proven from a migration without inventing provenance.
alter table psy_scale add column if not exists published_content_hash varchar(64);
alter table psy_scale add column if not exists published_at timestamp;
alter table psy_assessment_task add column if not exists scale_content_hash varchar(64);
alter table psy_assessment_result add column if not exists scale_content_hash varchar(64);
alter table psy_assessment_result add column if not exists scoring_engine_version varchar(64) not null default 'generic-v1';

update psy_assessment_task task
set scale_content_hash = scale.published_content_hash
from psy_scale scale
where task.scale_id = scale.id
  and task.scale_content_hash is null
  and scale.published_content_hash is not null;

update psy_assessment_result result
set scale_content_hash = task.scale_content_hash
from psy_assessment_answer_sheet sheet
join psy_assessment_task task on task.id = sheet.task_id
where result.answer_sheet_id = sheet.id
  and result.scale_content_hash is null
  and task.scale_content_hash is not null;

create index if not exists idx_psy_scale_published_content_hash
    on psy_scale(published_content_hash) where published_content_hash is not null;
create index if not exists idx_psy_task_scale_content_hash
    on psy_assessment_task(scale_content_hash) where scale_content_hash is not null;

