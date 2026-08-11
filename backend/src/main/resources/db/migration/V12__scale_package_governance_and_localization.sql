-- ScalePackage governance and localization foundation.
-- This migration deliberately does not invent source, copyright, authorization,
-- translation, norm provenance, validity, or professional review data.

create table if not exists psy_scale_governance (
    id bigserial primary key,
    scale_id bigint not null unique references psy_scale(id) on delete cascade,
    source_title varchar(255),
    publisher_name varchar(255),
    manual_version varchar(64),
    citation_text text,
    source_url text,
    copyright_status varchar(32) not null default 'PENDING_REVIEW',
    rights_holder varchar(255),
    authorization_status varchar(32) not null default 'PENDING_REVIEW',
    authorization_type varchar(64),
    authorization_scope text,
    authorized_territories text,
    authorized_languages varchar(255),
    authorization_valid_from date,
    authorization_valid_to date,
    target_population text,
    exclusion_criteria text,
    estimated_minutes int,
    result_visibility text,
    data_usage_statement text,
    non_diagnostic_statement text,
    help_resource_text text,
    governance_status varchar(32) not null default 'DRAFT',
    created_by bigint references sys_user(id),
    created_at timestamp not null default current_timestamp,
    updated_by bigint references sys_user(id),
    updated_at timestamp not null default current_timestamp
);

alter table psy_scale_governance add constraint ck_psy_scale_governance_copyright
    check (copyright_status in ('PENDING_REVIEW', 'AUTHORIZED', 'PUBLIC_DOMAIN', 'RESTRICTED', 'EXPIRED', 'REJECTED')) not valid;
alter table psy_scale_governance add constraint ck_psy_scale_governance_authorization
    check (authorization_status in ('PENDING_REVIEW', 'AUTHORIZED', 'NOT_REQUIRED', 'RESTRICTED', 'EXPIRED', 'REJECTED')) not valid;
alter table psy_scale_governance add constraint ck_psy_scale_governance_status
    check (governance_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;
alter table psy_scale_governance add constraint ck_psy_scale_governance_dates
    check (authorization_valid_to is null or authorization_valid_from is null or authorization_valid_to >= authorization_valid_from) not valid;
alter table psy_scale_governance add constraint ck_psy_scale_governance_duration
    check (estimated_minutes is null or estimated_minutes > 0) not valid;

create table if not exists psy_scale_translation (
    id bigserial primary key,
    scale_id bigint not null references psy_scale(id) on delete cascade,
    locale_code varchar(16) not null,
    scale_name varchar(255) not null,
    description text,
    instruction_text text,
    purpose_text text,
    data_usage_text text,
    result_visibility_text text,
    non_diagnostic_text text,
    high_risk_action_text text,
    help_resource_text text,
    review_status varchar(32) not null default 'DRAFT',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (scale_id, locale_code)
);

alter table psy_scale_translation add constraint ck_psy_scale_translation_locale
    check (locale_code in ('zh-CN', 'ja-JP', 'en')) not valid;
alter table psy_scale_translation add constraint ck_psy_scale_translation_review
    check (review_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;

create table if not exists psy_scale_dimension_translation (
    id bigserial primary key,
    dimension_id bigint not null references psy_scale_dimension(id) on delete cascade,
    locale_code varchar(16) not null,
    dimension_name varchar(255) not null,
    description text,
    review_status varchar(32) not null default 'DRAFT',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (dimension_id, locale_code)
);

alter table psy_scale_dimension_translation add constraint ck_psy_scale_dimension_translation_locale
    check (locale_code in ('zh-CN', 'ja-JP', 'en')) not valid;
alter table psy_scale_dimension_translation add constraint ck_psy_scale_dimension_translation_review
    check (review_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;

create table if not exists psy_scale_question_translation (
    id bigserial primary key,
    question_id bigint not null references psy_scale_question(id) on delete cascade,
    locale_code varchar(16) not null,
    question_title text not null,
    text_input_placeholder varchar(255),
    review_status varchar(32) not null default 'DRAFT',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (question_id, locale_code)
);

alter table psy_scale_question_translation add constraint ck_psy_scale_question_translation_locale
    check (locale_code in ('zh-CN', 'ja-JP', 'en')) not valid;
alter table psy_scale_question_translation add constraint ck_psy_scale_question_translation_review
    check (review_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;

create table if not exists psy_scale_option_translation (
    id bigserial primary key,
    option_id bigint not null references psy_scale_option(id) on delete cascade,
    locale_code varchar(16) not null,
    option_label varchar(255) not null,
    review_status varchar(32) not null default 'DRAFT',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (option_id, locale_code)
);

alter table psy_scale_option_translation add constraint ck_psy_scale_option_translation_locale
    check (locale_code in ('zh-CN', 'ja-JP', 'en')) not valid;
alter table psy_scale_option_translation add constraint ck_psy_scale_option_translation_review
    check (review_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;

create table if not exists psy_scale_result_rule_translation (
    id bigserial primary key,
    result_rule_id bigint not null references psy_scale_result_rule(id) on delete cascade,
    locale_code varchar(16) not null,
    result_title varchar(255) not null,
    result_description text,
    suggestion_text text,
    review_status varchar(32) not null default 'DRAFT',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (result_rule_id, locale_code)
);

alter table psy_scale_result_rule_translation add constraint ck_psy_scale_result_translation_locale
    check (locale_code in ('zh-CN', 'ja-JP', 'en')) not valid;
alter table psy_scale_result_rule_translation add constraint ck_psy_scale_result_translation_review
    check (review_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;

create table if not exists psy_scale_quality_policy (
    id bigserial primary key,
    scale_id bigint not null unique references psy_scale(id) on delete cascade,
    missing_answer_policy varchar(32) not null default 'REJECT',
    max_missing_ratio numeric(6,5) not null default 0,
    minimum_duration_seconds int,
    maximum_duration_seconds int,
    invalid_result_action varchar(32) not null default 'INVALIDATE',
    require_all_required_answers boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

alter table psy_scale_quality_policy add constraint ck_psy_scale_quality_missing_policy
    check (missing_answer_policy in ('REJECT', 'ALLOW', 'PRORATE', 'PENDING_PROFESSIONAL_REVIEW')) not valid;
alter table psy_scale_quality_policy add constraint ck_psy_scale_quality_missing_ratio
    check (max_missing_ratio >= 0 and max_missing_ratio <= 1) not valid;
alter table psy_scale_quality_policy add constraint ck_psy_scale_quality_duration
    check (
        (minimum_duration_seconds is null or minimum_duration_seconds > 0)
        and (maximum_duration_seconds is null or maximum_duration_seconds > 0)
        and (minimum_duration_seconds is null or maximum_duration_seconds is null or maximum_duration_seconds >= minimum_duration_seconds)
    ) not valid;
alter table psy_scale_quality_policy add constraint ck_psy_scale_quality_invalid_action
    check (invalid_result_action in ('INVALIDATE', 'REQUIRE_REVIEW', 'ALLOW_WITH_WARNING')) not valid;

create table if not exists psy_scale_validity_rule (
    id bigserial primary key,
    scale_id bigint not null references psy_scale(id) on delete cascade,
    rule_code varchar(64) not null,
    rule_type varchar(32) not null,
    rule_version varchar(32) not null,
    config_json jsonb not null default '{}'::jsonb,
    review_status varchar(32) not null default 'DRAFT',
    enabled boolean not null default true,
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (scale_id, rule_code, rule_version)
);

alter table psy_scale_validity_rule add constraint ck_psy_scale_validity_rule_type
    check (rule_type in ('CONSISTENCY', 'CONTRADICTION', 'DURATION', 'RESPONSE_PATTERN', 'CUSTOM_EXTENSION')) not valid;
alter table psy_scale_validity_rule add constraint ck_psy_scale_validity_rule_review
    check (review_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;

create table if not exists psy_scale_algorithm_binding (
    id bigserial primary key,
    scale_id bigint not null unique references psy_scale(id) on delete cascade,
    algorithm_code varchar(64) not null,
    algorithm_version varchar(32) not null,
    implementation_type varchar(32) not null,
    input_schema_json jsonb not null default '{}'::jsonb,
    output_schema_json jsonb not null default '{}'::jsonb,
    implementation_checksum varchar(64),
    review_status varchar(32) not null default 'DRAFT',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

alter table psy_scale_algorithm_binding add constraint ck_psy_scale_algorithm_implementation
    check (implementation_type in ('BUILTIN', 'RESTRICTED_EXTENSION')) not valid;
alter table psy_scale_algorithm_binding add constraint ck_psy_scale_algorithm_review
    check (review_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;

alter table psy_scale_norm add column if not exists source_reference text;
alter table psy_scale_norm add column if not exists norm_version varchar(64);
alter table psy_scale_norm add column if not exists sample_size int;
alter table psy_scale_norm add column if not exists region_code varchar(64);
alter table psy_scale_norm add column if not exists language_code varchar(16);
alter table psy_scale_norm add column if not exists valid_from date;
alter table psy_scale_norm add column if not exists valid_to date;
alter table psy_scale_norm add column if not exists review_status varchar(32) not null default 'PENDING_REVIEW';

alter table psy_scale_norm add constraint ck_psy_scale_norm_sample_size
    check (sample_size is null or sample_size > 0) not valid;
alter table psy_scale_norm add constraint ck_psy_scale_norm_valid_dates
    check (valid_to is null or valid_from is null or valid_to >= valid_from) not valid;
alter table psy_scale_norm add constraint ck_psy_scale_norm_review_status
    check (review_status in ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED')) not valid;

create index if not exists idx_psy_scale_translation_locale on psy_scale_translation(locale_code, review_status);
create index if not exists idx_psy_scale_dimension_translation_locale on psy_scale_dimension_translation(locale_code, review_status);
create index if not exists idx_psy_scale_question_translation_locale on psy_scale_question_translation(locale_code, review_status);
create index if not exists idx_psy_scale_option_translation_locale on psy_scale_option_translation(locale_code, review_status);
create index if not exists idx_psy_scale_result_translation_locale on psy_scale_result_rule_translation(locale_code, review_status);
create index if not exists idx_psy_scale_validity_rule_scale on psy_scale_validity_rule(scale_id, enabled, sort_no);

