-- Safety response is organization governance. This migration intentionally does
-- not seed invented SLA values or emergency contacts. Approved policy records
-- must be created by authorized organization and mental-health reviewers.

create table if not exists psy_safety_response_policy (
    id bigserial primary key,
    tenant_id bigint references sys_tenant(id),
    policy_code varchar(64) not null,
    version_no int not null,
    risk_category varchar(16) not null,
    first_response_minutes int not null,
    escalation_minutes int not null,
    follow_up_minutes int,
    responsible_role varchar(64) not null,
    backup_role varchar(64) not null,
    emergency_contact_text text not null,
    status varchar(16) not null default 'DRAFT',
    active_flag boolean not null default false,
    approved_by bigint references sys_user(id),
    professional_reviewer_id bigint references sys_user(id),
    approved_at timestamp,
    created_by bigint references sys_user(id),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (tenant_id, policy_code, version_no)
);

create unique index if not exists uk_psy_safety_policy_global_version
    on psy_safety_response_policy(policy_code, version_no)
    where tenant_id is null;
create unique index if not exists uk_psy_safety_policy_active_tenant_risk
    on psy_safety_response_policy(tenant_id, risk_category)
    where active_flag = true and tenant_id is not null;
create unique index if not exists uk_psy_safety_policy_active_global_risk
    on psy_safety_response_policy(risk_category)
    where active_flag = true and tenant_id is null;

alter table psy_safety_response_policy add constraint ck_psy_safety_policy_risk
    check (risk_category in ('P0', 'P1', 'P2', 'P3')) not valid;
alter table psy_safety_response_policy add constraint ck_psy_safety_policy_status
    check (status in ('DRAFT', 'APPROVED', 'RETIRED')) not valid;
alter table psy_safety_response_policy add constraint ck_psy_safety_policy_minutes
    check (
        first_response_minutes > 0
        and escalation_minutes >= first_response_minutes
        and (follow_up_minutes is null or follow_up_minutes > 0)
    ) not valid;
alter table psy_safety_response_policy add constraint ck_psy_safety_policy_activation
    check (
        active_flag = false
        or (
            status = 'APPROVED'
            and approved_by is not null
            and professional_reviewer_id is not null
            and approved_at is not null
        )
    ) not valid;

alter table psy_warning_record add column if not exists safety_policy_id bigint;
alter table psy_warning_record add column if not exists safety_policy_version int;
alter table psy_warning_record add column if not exists policy_resolution_status varchar(16) not null default 'MISSING';
alter table psy_warning_record add column if not exists safety_policy_snapshot jsonb;
alter table psy_warning_record add constraint fk_psy_warning_safety_policy
    foreign key (safety_policy_id) references psy_safety_response_policy(id) not valid;
alter table psy_warning_record add constraint ck_psy_warning_policy_resolution
    check (policy_resolution_status in ('RESOLVED', 'MISSING')) not valid;

update psy_warning_record
set warning_priority = case
    when upper(warning_level) in ('CRITICAL', 'P0') then 'P0'
    when upper(warning_level) in ('HIGH', 'P1') then 'P1'
    when upper(warning_level) in ('MODERATE', 'MEDIUM', 'ATTENTION', 'P2') then 'P2'
    else 'P3'
end
where warning_priority not in ('P0', 'P1', 'P2', 'P3');

create index if not exists idx_psy_warning_policy_resolution
    on psy_warning_record(policy_resolution_status, status, created_at)
    where status <> 'CLOSED';
create index if not exists idx_psy_warning_deadline_open
    on psy_warning_record(deadline_time, warning_priority)
    where status <> 'CLOSED';

create table if not exists psy_warning_response_event (
    id bigserial primary key,
    tenant_id bigint not null references sys_tenant(id),
    warning_id bigint not null references psy_warning_record(id),
    event_type varchar(32) not null,
    contact_channel varchar(32),
    contact_outcome varchar(64),
    imminent_danger_flag boolean,
    summary text not null,
    next_action text,
    performed_by bigint not null references sys_user(id),
    performed_at timestamp not null default current_timestamp,
    created_at timestamp not null default current_timestamp
);

alter table psy_warning_response_event add constraint ck_psy_warning_response_event_type
    check (event_type in (
        'CONTACT_ATTEMPT', 'SAFETY_ASSESSMENT', 'RESPONSIBLE_HANDOFF',
        'ESCALATION', 'FOLLOW_UP', 'CLOSURE_REVIEW'
    )) not valid;
create index if not exists idx_psy_warning_response_event_warning
    on psy_warning_response_event(warning_id, performed_at, id);

create table if not exists psy_warning_follow_up (
    id bigserial primary key,
    tenant_id bigint not null references sys_tenant(id),
    warning_id bigint not null references psy_warning_record(id),
    due_time timestamp not null,
    status varchar(16) not null default 'PENDING',
    follow_up_summary text,
    completed_by bigint references sys_user(id),
    completed_at timestamp,
    created_by bigint not null references sys_user(id),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

alter table psy_warning_follow_up add constraint ck_psy_warning_follow_up_status
    check (status in ('PENDING', 'COMPLETED', 'CANCELLED')) not valid;
alter table psy_warning_follow_up add constraint ck_psy_warning_follow_up_completion
    check (
        status <> 'COMPLETED'
        or (completed_by is not null and completed_at is not null and follow_up_summary is not null)
    ) not valid;
create index if not exists idx_psy_warning_follow_up_due
    on psy_warning_follow_up(status, due_time);

create table if not exists psy_warning_close_checklist (
    id bigserial primary key,
    tenant_id bigint not null references sys_tenant(id),
    warning_id bigint not null unique references psy_warning_record(id),
    contact_attempt_recorded boolean not null,
    safety_assessment_completed boolean not null,
    responsible_handoff_completed boolean not null,
    follow_up_arranged boolean not null,
    closure_reason text not null,
    completed_by bigint not null references sys_user(id),
    completed_at timestamp not null default current_timestamp
);

alter table psy_warning_close_checklist add constraint ck_psy_warning_close_checklist_complete
    check (
        contact_attempt_recorded
        and safety_assessment_completed
        and responsible_handoff_completed
        and follow_up_arranged
        and length(trim(closure_reason)) > 0
    ) not valid;
