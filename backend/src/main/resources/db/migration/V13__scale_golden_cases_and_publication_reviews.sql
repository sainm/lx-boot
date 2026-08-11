-- Versioned Golden Cases and publication reviews.
-- No case, expected score, reviewer identity, or approval is synthesized here.

create table if not exists psy_scale_golden_case (
    id bigserial primary key,
    tenant_id bigint references sys_tenant(id),
    scale_id bigint not null references psy_scale(id) on delete cascade,
    case_code varchar(64) not null,
    revision_no int not null,
    case_type varchar(32) not null,
    source_reference text not null,
    scale_content_hash varchar(64) not null,
    case_content_hash varchar(64) not null,
    input_json jsonb not null,
    expected_json jsonb not null,
    created_by bigint not null references sys_user(id),
    created_at timestamp not null default current_timestamp,
    approved_by bigint references sys_user(id),
    approved_at timestamp,
    unique (scale_id, case_code, revision_no)
);

alter table psy_scale_golden_case add constraint ck_psy_scale_golden_case_revision
    check (revision_no > 0) not valid;
alter table psy_scale_golden_case add constraint ck_psy_scale_golden_case_type
    check (case_type in ('NORMAL', 'BOUNDARY', 'REVERSE', 'MISSING', 'INVALID', 'HIGH_RISK')) not valid;
alter table psy_scale_golden_case add constraint ck_psy_scale_golden_case_hashes
    check (scale_content_hash ~ '^[0-9a-f]{64}$' and case_content_hash ~ '^[0-9a-f]{64}$') not valid;
alter table psy_scale_golden_case add constraint ck_psy_scale_golden_case_json
    check (jsonb_typeof(input_json) = 'object' and jsonb_typeof(expected_json) = 'object') not valid;
alter table psy_scale_golden_case add constraint ck_psy_scale_golden_case_approval
    check ((approved_by is null and approved_at is null) or (approved_by is not null and approved_at is not null)) not valid;

create index if not exists idx_psy_scale_golden_case_latest
    on psy_scale_golden_case(scale_id, case_code, revision_no desc);
create index if not exists idx_psy_scale_golden_case_tenant
    on psy_scale_golden_case(tenant_id, scale_id);

create table if not exists psy_scale_golden_case_run (
    id bigserial primary key,
    tenant_id bigint references sys_tenant(id),
    scale_id bigint not null references psy_scale(id) on delete cascade,
    golden_case_id bigint not null references psy_scale_golden_case(id) on delete cascade,
    scale_content_hash varchar(64) not null,
    case_content_hash varchar(64) not null,
    algorithm_code varchar(64),
    algorithm_version varchar(32),
    passed boolean not null,
    actual_json jsonb not null,
    differences_json jsonb not null default '[]'::jsonb,
    executed_by bigint not null references sys_user(id),
    executed_at timestamp not null default current_timestamp
);

alter table psy_scale_golden_case_run add constraint ck_psy_scale_golden_run_hashes
    check (scale_content_hash ~ '^[0-9a-f]{64}$' and case_content_hash ~ '^[0-9a-f]{64}$') not valid;
alter table psy_scale_golden_case_run add constraint ck_psy_scale_golden_run_json
    check (jsonb_typeof(actual_json) = 'object' and jsonb_typeof(differences_json) = 'array') not valid;

create index if not exists idx_psy_scale_golden_run_latest
    on psy_scale_golden_case_run(golden_case_id, executed_at desc, id desc);
create index if not exists idx_psy_scale_golden_run_tenant
    on psy_scale_golden_case_run(tenant_id, scale_id, executed_at desc);

create table if not exists psy_scale_publication_review (
    id bigserial primary key,
    tenant_id bigint references sys_tenant(id),
    scale_id bigint not null references psy_scale(id) on delete cascade,
    review_type varchar(32) not null,
    decision varchar(32) not null,
    reviewer_id bigint not null references sys_user(id),
    reviewer_role_snapshot varchar(64) not null,
    scale_content_hash varchar(64) not null,
    release_fingerprint varchar(64) not null,
    review_token varchar(128) not null,
    comment_text text,
    created_at timestamp not null default current_timestamp,
    unique (scale_id, review_type, review_token)
);

alter table psy_scale_publication_review add constraint ck_psy_scale_publication_review_type
    check (review_type in ('PROFESSIONAL', 'BUSINESS')) not valid;
alter table psy_scale_publication_review add constraint ck_psy_scale_publication_decision
    check (decision in ('APPROVED', 'REJECTED')) not valid;
alter table psy_scale_publication_review add constraint ck_psy_scale_publication_hashes
    check (scale_content_hash ~ '^[0-9a-f]{64}$' and release_fingerprint ~ '^[0-9a-f]{64}$') not valid;

create index if not exists idx_psy_scale_publication_review_latest
    on psy_scale_publication_review(scale_id, release_fingerprint, review_type, created_at desc, id desc);
create index if not exists idx_psy_scale_publication_review_tenant
    on psy_scale_publication_review(tenant_id, scale_id, created_at desc);
