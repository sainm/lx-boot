-- Complete the ScalePackage localization model for high-risk rules and retain
-- the language used for each response/report. Existing rows remain NULL: the
-- migration must not guess historical request locale.

create table if not exists psy_scale_high_risk_rule_translation (
    id bigserial primary key,
    high_risk_rule_id bigint not null references psy_scale_high_risk_rule(id) on delete cascade,
    locale_code varchar(16) not null,
    result_title varchar(255) not null,
    result_description text,
    suggestion_text text,
    review_status varchar(32) not null default 'DRAFT',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (high_risk_rule_id, locale_code)
);

alter table psy_scale_high_risk_rule_translation
    add constraint ck_psy_scale_high_risk_translation_locale
    check (locale_code in ('zh-CN', 'ja-JP', 'en')) not valid;

alter table psy_scale_high_risk_rule_translation
    add constraint ck_psy_scale_high_risk_translation_review
    check (review_status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')) not valid;

create index if not exists idx_psy_scale_high_risk_translation_locale
    on psy_scale_high_risk_rule_translation(locale_code, review_status);

alter table psy_assessment_answer_sheet
    add column if not exists response_locale_code varchar(16);

alter table psy_assessment_answer_sheet
    add constraint ck_psy_answer_sheet_response_locale
    check (response_locale_code is null or response_locale_code in ('zh-CN', 'ja-JP', 'en')) not valid;

comment on column psy_assessment_answer_sheet.response_locale_code is
    'Canonical ScalePackage locale used by the respondent; NULL means historical locale is unknown.';

alter table psy_report
    add column if not exists locale_code varchar(16);

alter table psy_report
    add constraint ck_psy_report_locale
    check (locale_code is null or locale_code in ('zh-CN', 'ja-JP', 'en')) not valid;

comment on column psy_report.locale_code is
    'Canonical locale used to render this immutable report version; NULL means historical locale is unknown.';
