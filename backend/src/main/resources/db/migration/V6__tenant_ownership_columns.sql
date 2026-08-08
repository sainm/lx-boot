-- Add explicit tenant ownership without guessing ambiguous historical data.
-- Nullable rows are intentionally retained for preflight review; application
-- queries must not expose them to tenant-scoped users.

alter table psy_scale add column if not exists tenant_id bigint;
alter table psy_scale_import_job add column if not exists tenant_id bigint;
alter table psy_assessment_task add column if not exists tenant_id bigint;
alter table psy_assessment_answer_sheet add column if not exists tenant_id bigint;
alter table psy_warning_record add column if not exists tenant_id bigint;
alter table psy_warning_assignment add column if not exists tenant_id bigint;
alter table psy_intervention_record add column if not exists tenant_id bigint;
alter table psy_intervention_status_log add column if not exists tenant_id bigint;
alter table psy_counselor_schedule add column if not exists tenant_id bigint;
alter table psy_appointment_record add column if not exists tenant_id bigint;
alter table psy_counseling_record add column if not exists tenant_id bigint;
alter table psy_notification_delivery add column if not exists tenant_id bigint;
alter table psy_export_job add column if not exists tenant_id bigint;
alter table psy_export_job add column if not exists created_by bigint;

update psy_scale s
set tenant_id = u.tenant_id
from sys_user u
where s.tenant_id is null and s.created_by = u.id and u.tenant_id is not null;

update psy_scale_import_job j
set tenant_id = u.tenant_id
from sys_user u
where j.tenant_id is null and j.operator_user_id = u.id and u.tenant_id is not null;

update psy_assessment_task t
set tenant_id = coalesce(
    (select u.tenant_id from sys_user u where u.id = t.created_by),
    (select s.tenant_id from psy_scale s where s.id = t.scale_id)
)
where t.tenant_id is null
  and (
      (select u.tenant_id from sys_user u where u.id = t.created_by) is null
      or (select s.tenant_id from psy_scale s where s.id = t.scale_id) is null
      or (select u.tenant_id from sys_user u where u.id = t.created_by)
         = (select s.tenant_id from psy_scale s where s.id = t.scale_id)
  );

update psy_assessment_answer_sheet a
set tenant_id = coalesce(
    (select u.tenant_id from sys_user u where u.id = a.user_id),
    (select t.tenant_id from psy_assessment_task t where t.id = a.task_id)
)
where a.tenant_id is null
  and (
      (select u.tenant_id from sys_user u where u.id = a.user_id) is null
      or (select t.tenant_id from psy_assessment_task t where t.id = a.task_id) is null
      or (select u.tenant_id from sys_user u where u.id = a.user_id)
         = (select t.tenant_id from psy_assessment_task t where t.id = a.task_id)
  );

update psy_warning_record w
set tenant_id = a.tenant_id
from psy_assessment_result r
join psy_assessment_answer_sheet a on a.id = r.answer_sheet_id
where w.result_id = r.id and w.tenant_id is null;

update psy_warning_assignment a
set tenant_id = w.tenant_id
from psy_warning_record w
where a.warning_id = w.id and a.tenant_id is null;

update psy_intervention_record i
set tenant_id = w.tenant_id
from psy_warning_record w
where i.warning_id = w.id and i.tenant_id is null;

update psy_intervention_status_log l
set tenant_id = i.tenant_id
from psy_intervention_record i
where l.intervention_id = i.id and l.tenant_id is null;

update psy_counselor_schedule s
set tenant_id = u.tenant_id
from sys_user u
where s.counselor_user_id = u.id and s.tenant_id is null;

update psy_appointment_record a
set tenant_id = coalesce(
    (select u.tenant_id from sys_user u where u.id = a.user_id),
    (select u.tenant_id from sys_user u where u.id = a.counselor_user_id),
    (select s.tenant_id from psy_counselor_schedule s where s.id = a.schedule_id)
)
where a.tenant_id is null
  and (
      (select u.tenant_id from sys_user u where u.id = a.user_id) is null
      or (select u.tenant_id from sys_user u where u.id = a.counselor_user_id) is null
      or (select u.tenant_id from sys_user u where u.id = a.user_id)
         = (select u.tenant_id from sys_user u where u.id = a.counselor_user_id)
  )
  and (
      (select u.tenant_id from sys_user u where u.id = a.user_id) is null
      or (select s.tenant_id from psy_counselor_schedule s where s.id = a.schedule_id) is null
      or (select u.tenant_id from sys_user u where u.id = a.user_id)
         = (select s.tenant_id from psy_counselor_schedule s where s.id = a.schedule_id)
  );

update psy_counseling_record c
set tenant_id = a.tenant_id
from psy_appointment_record a
where c.appointment_id = a.id and c.tenant_id is null;

update psy_notification_delivery d
set tenant_id = u.tenant_id
from sys_user u
where d.receiver_user_id = u.id and d.tenant_id is null;

update psy_export_job e
set tenant_id = a.tenant_id
from psy_report p
join psy_assessment_result r on r.id = p.result_id
join psy_assessment_answer_sheet a on a.id = r.answer_sheet_id
where e.report_id = p.id and e.tenant_id is null;

create index if not exists idx_psy_scale_tenant on psy_scale(tenant_id, id);
create index if not exists idx_psy_task_tenant_status on psy_assessment_task(tenant_id, status, id);
create index if not exists idx_psy_answer_tenant_task on psy_assessment_answer_sheet(tenant_id, task_id, id);
create index if not exists idx_psy_warning_tenant_status on psy_warning_record(tenant_id, status, id);
create index if not exists idx_psy_intervention_tenant_status on psy_intervention_record(tenant_id, current_status, id);
create index if not exists idx_psy_schedule_tenant_date on psy_counselor_schedule(tenant_id, schedule_date, id);
create index if not exists idx_psy_appointment_tenant_status on psy_appointment_record(tenant_id, appointment_status, id);
create index if not exists idx_psy_notification_delivery_tenant on psy_notification_delivery(tenant_id, receiver_user_id, id);
create index if not exists idx_psy_export_job_tenant on psy_export_job(tenant_id, created_at, id);

alter table psy_scale add constraint fk_psy_scale_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_assessment_task add constraint fk_psy_task_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_assessment_answer_sheet add constraint fk_psy_answer_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_warning_record add constraint fk_psy_warning_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_intervention_record add constraint fk_psy_intervention_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_counselor_schedule add constraint fk_psy_schedule_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_appointment_record add constraint fk_psy_appointment_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_counseling_record add constraint fk_psy_counseling_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_notification_delivery add constraint fk_psy_notification_delivery_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_export_job add constraint fk_psy_export_job_tenant foreign key (tenant_id) references sys_tenant(id) not valid;
alter table psy_export_job add constraint fk_psy_export_job_creator foreign key (created_by) references sys_user(id) not valid;
