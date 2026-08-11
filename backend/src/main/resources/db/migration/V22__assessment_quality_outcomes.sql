-- Persist the quality decision made at answer submission time.
-- This is additive: existing answer sheets/results remain readable and keep
-- the neutral NOT_EVALUATED value until a new submission or rescore occurs.

alter table psy_assessment_answer_sheet
    add column if not exists quality_status varchar(32) not null default 'NOT_EVALUATED';
alter table psy_assessment_answer_sheet
    add column if not exists quality_issue_codes varchar(1000);
alter table psy_assessment_answer_sheet
    add column if not exists quality_missing_ratio numeric(6,5);
alter table psy_assessment_answer_sheet
    add column if not exists quality_duration_seconds int;

alter table psy_assessment_answer_sheet drop constraint if exists ck_psy_answer_sheet_quality_status;
alter table psy_assessment_answer_sheet
    add constraint ck_psy_answer_sheet_quality_status
    check (quality_status in ('NOT_EVALUATED', 'VALID', 'WARNING', 'REVIEW_REQUIRED', 'INVALID')) not valid;
alter table psy_assessment_answer_sheet validate constraint ck_psy_answer_sheet_quality_status;

alter table psy_assessment_answer_sheet drop constraint if exists ck_psy_answer_sheet_quality_ratio;
alter table psy_assessment_answer_sheet
    add constraint ck_psy_answer_sheet_quality_ratio
    check (quality_missing_ratio is null or (quality_missing_ratio >= 0 and quality_missing_ratio <= 1)) not valid;
alter table psy_assessment_answer_sheet validate constraint ck_psy_answer_sheet_quality_ratio;

alter table psy_assessment_answer_sheet drop constraint if exists ck_psy_answer_sheet_quality_duration;
alter table psy_assessment_answer_sheet
    add constraint ck_psy_answer_sheet_quality_duration
    check (quality_duration_seconds is null or quality_duration_seconds >= 0) not valid;
alter table psy_assessment_answer_sheet validate constraint ck_psy_answer_sheet_quality_duration;

alter table psy_assessment_result
    add column if not exists quality_status varchar(32) not null default 'NOT_EVALUATED';
alter table psy_assessment_result
    add column if not exists quality_issue_codes varchar(1000);
alter table psy_assessment_result
    add column if not exists quality_missing_ratio numeric(6,5);
alter table psy_assessment_result
    add column if not exists quality_duration_seconds int;

alter table psy_assessment_result drop constraint if exists ck_psy_result_quality_status;
alter table psy_assessment_result
    add constraint ck_psy_result_quality_status
    check (quality_status in ('NOT_EVALUATED', 'VALID', 'WARNING', 'REVIEW_REQUIRED', 'INVALID')) not valid;
alter table psy_assessment_result validate constraint ck_psy_result_quality_status;

alter table psy_assessment_result drop constraint if exists ck_psy_result_quality_ratio;
alter table psy_assessment_result
    add constraint ck_psy_result_quality_ratio
    check (quality_missing_ratio is null or (quality_missing_ratio >= 0 and quality_missing_ratio <= 1)) not valid;
alter table psy_assessment_result validate constraint ck_psy_result_quality_ratio;

alter table psy_assessment_result drop constraint if exists ck_psy_result_quality_duration;
alter table psy_assessment_result
    add constraint ck_psy_result_quality_duration
    check (quality_duration_seconds is null or quality_duration_seconds >= 0) not valid;
alter table psy_assessment_result validate constraint ck_psy_result_quality_duration;
