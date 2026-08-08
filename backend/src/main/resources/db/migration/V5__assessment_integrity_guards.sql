-- Prevent duplicate drafts, assignments, results, warnings, and report versions
-- at the database boundary. Preflight queries in the deployment guide must be
-- clean before applying this migration to an existing installation.

create unique index if not exists uk_psy_answer_sheet_active_draft_user
    on psy_assessment_answer_sheet(task_id, user_id)
    where answer_status = 'DRAFT' and user_id is not null;

create unique index if not exists uk_psy_answer_sheet_active_draft_anonymous
    on psy_assessment_answer_sheet(task_id, anonymous_token)
    where answer_status = 'DRAFT' and user_id is null and anonymous_token is not null;

create unique index if not exists uk_psy_answer_sheet_submit_token_anonymous
    on psy_assessment_answer_sheet(task_id, anonymous_token, submit_token)
    where answer_status = 'SUBMITTED' and user_id is null and anonymous_token is not null and submit_token is not null;

alter table psy_assessment_answer_sheet
    add constraint ck_psy_answer_sheet_identity
    check (
        (user_id is not null and anonymous_token is null)
        or (user_id is null and anonymous_token is not null)
    ) not valid;

create unique index if not exists uk_psy_task_assignment_target
    on psy_assessment_task_assignment(task_id, target_type, target_id);

create unique index if not exists uk_psy_warning_result
    on psy_warning_record(result_id);

create unique index if not exists uk_psy_report_result_version
    on psy_report(result_id, version_no);

alter table psy_assessment_task_assignment
    add constraint ck_psy_task_assignment_target_type
    check (target_type in ('USER', 'GROUP')) not valid;

alter table psy_scale
    add constraint ck_psy_scale_score_method
    check (score_method in ('SIMPLE_SUM', 'REVERSE_SUM', 'WEIGHTED_SUM')) not valid;

alter table psy_scale_question
    add constraint ck_psy_scale_question_type
    check (question_type in ('SINGLE_CHOICE', 'MULTI_SELECT', 'SLIDER', 'TEXT', 'TEXT_WITH_OPTION', 'MATRIX')) not valid;

alter table psy_scale_result_rule
    add constraint ck_psy_scale_result_score_source
    check (score_source in ('RAW_SCORE', 'Z_SCORE', 'T_SCORE')) not valid;
