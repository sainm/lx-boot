create table if not exists psy_scale (
    id bigserial primary key,
    scale_code varchar(64) not null,
    scale_name varchar(255) not null,
    description text,
    applicable_target varchar(128),
    version_no varchar(32),
    version_group_id bigint,
    current_version_flag boolean not null default true,
    status varchar(32) not null default 'DRAFT',
    score_method varchar(32) not null default 'SIMPLE_SUM',
    score_coefficient decimal(6, 4) not null default 1.0,
    anonymous_supported boolean not null default false,
    report_template text,
    created_by bigint,
    created_at timestamp not null default current_timestamp,
    updated_by bigint,
    updated_at timestamp not null default current_timestamp
);

drop index if exists uk_psy_scale_code;
create unique index if not exists uk_psy_scale_code_version on psy_scale(scale_code, version_no);
create index if not exists idx_psy_scale_version_group on psy_scale(version_group_id);
create index if not exists idx_psy_scale_status on psy_scale(status);

create table if not exists psy_scale_dimension (
    id bigserial primary key,
    scale_id bigint not null references psy_scale(id),
    dimension_code varchar(64) not null,
    dimension_name varchar(255) not null,
    description text,
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_scale_dimension_scale_id on psy_scale_dimension(scale_id);
create unique index if not exists uk_psy_scale_dimension_code on psy_scale_dimension(scale_id, dimension_code);

create table if not exists psy_scale_question (
    id bigserial primary key,
    scale_id bigint not null references psy_scale(id),
    dimension_id bigint references psy_scale_dimension(id),
    question_no int not null,
    question_title text not null,
    question_type varchar(32) not null,
    required_flag boolean not null default true,
    reverse_score_flag boolean not null default false,
    weight_value numeric(10,2) not null default 1.00,
    option_selection_limit int,
    slider_min numeric(10,2),
    slider_max numeric(10,2),
    slider_step numeric(10,2),
    text_input_enabled boolean not null default false,
    text_input_placeholder varchar(255),
    matrix_group_code varchar(64),
    row_code varchar(64),
    column_code varchar(64),
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_scale_question_scale_id on psy_scale_question(scale_id);
create unique index if not exists uk_psy_scale_question_no on psy_scale_question(scale_id, question_no);

create table if not exists psy_scale_option (
    id bigserial primary key,
    question_id bigint not null references psy_scale_question(id),
    option_code varchar(64) not null,
    option_label varchar(255) not null,
    score_value numeric(10,2) not null,
    exclusive_flag boolean not null default false,
    option_group_code varchar(64),
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_scale_option_question_id on psy_scale_option(question_id);
create unique index if not exists uk_psy_scale_option_code on psy_scale_option(question_id, option_code);

create table if not exists psy_scale_result_rule (
    id bigserial primary key,
    scale_id bigint not null references psy_scale(id),
    dimension_id bigint references psy_scale_dimension(id),
    risk_level varchar(32) not null,
    score_min numeric(10,2) not null,
    score_max numeric(10,2) not null,
    score_source varchar(32) not null default 'RAW_SCORE',
    norm_code varchar(64),
    result_title varchar(255),
    result_description text,
    suggestion_text text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_scale_result_rule_scale_id on psy_scale_result_rule(scale_id);

create table if not exists psy_scale_norm (
    id bigserial primary key,
    scale_id bigint not null references psy_scale(id),
    norm_code varchar(64) not null,
    norm_name varchar(255),
    dimension_id bigint references psy_scale_dimension(id),
    applicable_target varchar(128),
    age_min int,
    age_max int,
    gender varchar(32),
    org_type varchar(64),
    mean_score numeric(10,4),
    std_deviation numeric(10,4),
    t_score_mean numeric(10,4),
    t_score_std_deviation numeric(10,4),
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create unique index if not exists uk_psy_scale_norm_code on psy_scale_norm(scale_id, norm_code, coalesce(dimension_id, 0));
create index if not exists idx_psy_scale_norm_scale_id on psy_scale_norm(scale_id);

create table if not exists psy_scale_high_risk_rule (
    id bigserial primary key,
    scale_id bigint not null references psy_scale(id),
    rule_code varchar(64) not null,
    question_id bigint not null references psy_scale_question(id),
    option_id bigint references psy_scale_option(id),
    score_threshold numeric(10,2),
    warning_level varchar(32) not null,
    result_title varchar(255),
    result_description text,
    suggestion_text text,
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create unique index if not exists uk_psy_scale_high_risk_rule_code on psy_scale_high_risk_rule(scale_id, rule_code);
create index if not exists idx_psy_scale_high_risk_rule_scale_id on psy_scale_high_risk_rule(scale_id);

create table if not exists psy_scale_import_job (
    id bigserial primary key,
    file_name varchar(255) not null,
    file_hash varchar(128),
    import_mode varchar(32) not null default 'CREATE_ONLY',
    draft_flag boolean not null default true,
    status varchar(32) not null,
    summary_json text,
    preview_json text,
    error_count int not null default 0,
    warning_count int not null default 0,
    created_scale_id bigint references psy_scale(id),
    operator_user_id bigint not null references sys_user(id),
    parsed_at timestamp,
    confirmed_at timestamp,
    finished_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_scale_import_job_status on psy_scale_import_job(status);
create index if not exists idx_psy_scale_import_job_operator on psy_scale_import_job(operator_user_id);

create table if not exists psy_scale_import_issue (
    id bigserial primary key,
    import_job_id bigint not null references psy_scale_import_job(id) on delete cascade,
    severity varchar(16) not null,
    sheet_name varchar(64) not null,
    row_no int,
    column_name varchar(64),
    error_code varchar(64) not null,
    message varchar(500) not null,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_scale_import_issue_job_id on psy_scale_import_issue(import_job_id);

create table if not exists psy_assessment_task (
    id bigserial primary key,
    task_name varchar(255) not null,
    scale_id bigint not null references psy_scale(id),
    scale_version_no varchar(32),
    scale_version_group_id bigint,
    task_mode varchar(32) not null,
    anonymous_flag boolean not null default false,
    allow_save_flag boolean not null default true,
    allow_timeout_submit_flag boolean not null default false,
    allow_retake_flag boolean not null default false,
    start_time timestamp not null,
    end_time timestamp not null,
    status varchar(32) not null default 'DRAFT',
    created_by bigint,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

alter table psy_assessment_task add column if not exists overdue_notified_at timestamp;
alter table psy_assessment_task add column if not exists scale_version_no varchar(32);
alter table psy_assessment_task add column if not exists scale_version_group_id bigint;
alter table psy_assessment_task add column if not exists closed_at timestamp;
alter table psy_assessment_task add column if not exists closed_by bigint;
alter table psy_assessment_task add column if not exists close_reason varchar(500);

create index if not exists idx_psy_assessment_task_scale_id on psy_assessment_task(scale_id);
create index if not exists idx_psy_assessment_task_scale_version_group on psy_assessment_task(scale_version_group_id);
create index if not exists idx_psy_assessment_task_status on psy_assessment_task(status);

create table if not exists psy_assessment_task_assignment (
    id bigserial primary key,
    task_id bigint not null references psy_assessment_task(id),
    target_type varchar(32) not null,
    target_id bigint not null,
    assigned_by bigint,
    assigned_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_assessment_task_assignment_task_id on psy_assessment_task_assignment(task_id);
create index if not exists idx_psy_assessment_task_assignment_target on psy_assessment_task_assignment(target_type, target_id);

create table if not exists psy_assessment_answer_sheet (
    id bigserial primary key,
    task_id bigint not null references psy_assessment_task(id),
    scale_id bigint not null references psy_scale(id),
    user_id bigint,
    answer_status varchar(32) not null,
    version_no int not null default 1,
    start_time timestamp,
    submit_time timestamp,
    duration_seconds int,
    anonymous_token varchar(128),
    submit_token varchar(128),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_assessment_answer_sheet_task_id on psy_assessment_answer_sheet(task_id);
create index if not exists idx_psy_assessment_answer_sheet_user_id on psy_assessment_answer_sheet(user_id);
create index if not exists idx_psy_assessment_answer_sheet_submit_token on psy_assessment_answer_sheet(submit_token);
create unique index if not exists uk_psy_answer_sheet_submit_token_user_task
    on psy_assessment_answer_sheet(task_id, user_id, submit_token)
    where answer_status = 'SUBMITTED' and user_id is not null and submit_token is not null;

create table if not exists psy_assessment_answer_item (
    id bigserial primary key,
    answer_sheet_id bigint not null references psy_assessment_answer_sheet(id) on delete cascade,
    question_id bigint not null references psy_scale_question(id),
    option_id bigint references psy_scale_option(id),
    answer_text text,
    answer_value numeric(10,2),
    score_value numeric(10,2),
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_assessment_answer_item_sheet_id on psy_assessment_answer_item(answer_sheet_id);

create table if not exists psy_assessment_result (
    id bigserial primary key,
    answer_sheet_id bigint not null unique references psy_assessment_answer_sheet(id),
    total_score numeric(10,2) not null,
    risk_level varchar(32) not null,
    warning_flag boolean not null default false,
    result_summary text,
    score_source varchar(32) not null default 'RAW_SCORE',
    standard_score numeric(10,4),
    z_score numeric(10,4),
    t_score numeric(10,4),
    norm_code varchar(64),
    high_risk_flag boolean not null default false,
    high_risk_rule_code varchar(64),
    scored_at timestamp not null default current_timestamp,
    created_at timestamp not null default current_timestamp
);

create table if not exists psy_report (
    id bigserial primary key,
    result_id bigint not null references psy_assessment_result(id),
    report_type varchar(32) not null,
    author_user_id bigint,
    report_title varchar(255),
    report_content text not null,
    version_no int not null default 1,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table if not exists psy_warning_record (
    id bigserial primary key,
    result_id bigint not null references psy_assessment_result(id),
    warning_level varchar(32) not null,
    warning_priority varchar(32) not null,
    warning_reason text,
    status varchar(32) not null,
    deadline_time timestamp,
    first_response_time timestamp,
    escalated_at timestamp,
    last_reminded_at timestamp,
    escalation_count int not null default 0,
    closed_time timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_warning_record_result_id on psy_warning_record(result_id);
create index if not exists idx_psy_warning_record_status on psy_warning_record(status);
create index if not exists idx_psy_warning_record_escalation on psy_warning_record(warning_level, status, escalated_at);
alter table psy_warning_record add column if not exists escalated_at timestamp;
alter table psy_warning_record add column if not exists last_reminded_at timestamp;
alter table psy_warning_record add column if not exists escalation_count int not null default 0;

create table if not exists psy_warning_assignment (
    id bigserial primary key,
    warning_id bigint not null references psy_warning_record(id),
    assignee_user_id bigint not null references sys_user(id),
    assigned_by bigint,
    assigned_at timestamp not null default current_timestamp,
    claim_time timestamp
);

create index if not exists idx_psy_warning_assignment_warning_id on psy_warning_assignment(warning_id);
create index if not exists idx_psy_warning_assignment_assignee_user_id on psy_warning_assignment(assignee_user_id);

create table if not exists psy_intervention_record (
    id bigserial primary key,
    warning_id bigint not null references psy_warning_record(id),
    counselor_user_id bigint references sys_user(id),
    current_status varchar(32) not null,
    plan_text text,
    close_summary text,
    need_retest_flag boolean not null default false,
    retest_task_id bigint references psy_assessment_task(id),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

alter table psy_intervention_record add column if not exists need_retest_flag boolean not null default false;
alter table psy_intervention_record add column if not exists retest_task_id bigint references psy_assessment_task(id);

create index if not exists idx_psy_intervention_record_warning_id on psy_intervention_record(warning_id);
create index if not exists idx_psy_intervention_record_status on psy_intervention_record(current_status);

create table if not exists psy_intervention_status_log (
    id bigserial primary key,
    intervention_id bigint not null references psy_intervention_record(id),
    from_status varchar(32),
    to_status varchar(32) not null,
    remark text,
    changed_by bigint references sys_user(id),
    changed_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_intervention_status_log_intervention_id on psy_intervention_status_log(intervention_id);

create table if not exists psy_counselor_schedule (
    id bigserial primary key,
    counselor_user_id bigint not null references sys_user(id),
    schedule_date date not null,
    start_time timestamp not null,
    end_time timestamp not null,
    quota_count int not null default 1,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_counselor_schedule_counselor_id on psy_counselor_schedule(counselor_user_id);
create index if not exists idx_psy_counselor_schedule_status on psy_counselor_schedule(status);

create table if not exists psy_appointment_record (
    id bigserial primary key,
    user_id bigint not null references sys_user(id),
    counselor_user_id bigint not null references sys_user(id),
    warning_id bigint references psy_warning_record(id),
    schedule_id bigint references psy_counselor_schedule(id),
    appointment_status varchar(32) not null default 'CONFIRMED',
    source_type varchar(32) not null,
    remark text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_appointment_record_user_id on psy_appointment_record(user_id);
create index if not exists idx_psy_appointment_record_counselor_id on psy_appointment_record(counselor_user_id);
create index if not exists idx_psy_appointment_record_schedule_id on psy_appointment_record(schedule_id);

create table if not exists psy_counseling_record (
    id bigserial primary key,
    appointment_id bigint not null references psy_appointment_record(id),
    counselor_user_id bigint not null references sys_user(id),
    summary_text text,
    suggestion_text text,
    need_retest_flag boolean not null default false,
    need_transfer_flag boolean not null default false,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_counseling_record_appointment_id on psy_counseling_record(appointment_id);

create table if not exists psy_notification (
    id bigserial primary key,
    notification_type varchar(32) not null,
    title varchar(255) not null,
    content text not null,
    biz_type varchar(32),
    biz_id bigint,
    created_at timestamp not null default current_timestamp
);

alter table psy_notification add column if not exists target_path varchar(512);
alter table psy_notification add column if not exists target_type varchar(64);
alter table psy_notification add column if not exists target_id bigint;
alter table psy_notification add column if not exists deep_link varchar(512);
alter table psy_notification add column if not exists payload_json text;
alter table psy_scale add column if not exists score_method varchar(32) not null default 'SIMPLE_SUM';
alter table psy_scale add column if not exists score_coefficient decimal(6, 4) not null default 1.0;
alter table psy_scale add column if not exists version_group_id bigint;
alter table psy_scale add column if not exists current_version_flag boolean not null default true;
alter table psy_scale add column if not exists norm_strategy varchar(32) not null default 'RAW_SCORE';
alter table psy_scale add column if not exists norm_default_group varchar(64);
alter table psy_scale add column if not exists high_risk_warning_enabled boolean not null default false;
update psy_scale set version_group_id = id where version_group_id is null;
alter table psy_scale_question add column if not exists option_selection_limit int;
alter table psy_scale_question add column if not exists slider_min numeric(10,2);
alter table psy_scale_question add column if not exists slider_max numeric(10,2);
alter table psy_scale_question add column if not exists slider_step numeric(10,2);
alter table psy_scale_question add column if not exists text_input_enabled boolean not null default false;
alter table psy_scale_question add column if not exists text_input_placeholder varchar(255);
alter table psy_scale_question add column if not exists matrix_group_code varchar(64);
alter table psy_scale_question add column if not exists row_code varchar(64);
alter table psy_scale_question add column if not exists column_code varchar(64);
alter table psy_assessment_answer_item add column if not exists answer_value numeric(10,2);
alter table psy_assessment_result add column if not exists score_source varchar(32) not null default 'RAW_SCORE';
alter table psy_assessment_result add column if not exists standard_score numeric(10,4);
alter table psy_assessment_result add column if not exists z_score numeric(10,4);
alter table psy_assessment_result add column if not exists t_score numeric(10,4);
alter table psy_assessment_result add column if not exists norm_code varchar(64);
alter table psy_assessment_result add column if not exists high_risk_flag boolean not null default false;
alter table psy_assessment_result add column if not exists high_risk_rule_code varchar(64);
alter table psy_scale_option add column if not exists exclusive_flag boolean not null default false;
alter table psy_scale_option add column if not exists option_group_code varchar(64);
alter table psy_scale_result_rule add column if not exists score_source varchar(32) not null default 'RAW_SCORE';
alter table psy_scale_result_rule add column if not exists norm_code varchar(64);

create table if not exists psy_assessment_result_dimension (
    id bigserial primary key,
    result_id bigint not null references psy_assessment_result(id),
    dimension_id bigint not null,
    dimension_score decimal(10, 4) not null,
    risk_level varchar(32),
    result_title varchar(255),
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_result_dimension_result_id on psy_assessment_result_dimension(result_id);

create index if not exists idx_psy_notification_type on psy_notification(notification_type);
create index if not exists idx_psy_notification_biz on psy_notification(biz_type, biz_id);

create table if not exists psy_notification_policy (
    id bigserial primary key,
    notification_type varchar(64) not null,
    in_app_enabled boolean not null default true,
    push_enabled boolean not null default true,
    cooldown_minutes int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create unique index if not exists uk_psy_notification_policy_type on psy_notification_policy(notification_type);

create table if not exists psy_notification_delivery (
    id bigserial primary key,
    notification_id bigint not null references psy_notification(id),
    receiver_user_id bigint not null references sys_user(id),
    read_flag boolean not null default false,
    read_time timestamp,
    delivery_channel varchar(32) not null default 'IN_APP',
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_notification_delivery_receiver on psy_notification_delivery(receiver_user_id);
create index if not exists idx_psy_notification_delivery_read_flag on psy_notification_delivery(receiver_user_id, read_flag);
alter table psy_notification_delivery add column if not exists device_id bigint;
alter table psy_notification_delivery add column if not exists push_token_snapshot varchar(512);
alter table psy_notification_delivery add column if not exists delivery_status varchar(32) not null default 'PENDING';
alter table psy_notification_delivery add column if not exists provider_name varchar(64);
alter table psy_notification_delivery add column if not exists provider_message_id varchar(255);
alter table psy_notification_delivery add column if not exists delivered_time timestamp;
alter table psy_notification_delivery add column if not exists clicked_time timestamp;
alter table psy_notification_delivery add column if not exists error_message text;
alter table psy_notification_delivery add column if not exists callback_payload_json text;
alter table psy_notification_delivery add column if not exists updated_at timestamp not null default current_timestamp;
create index if not exists idx_psy_notification_delivery_status on psy_notification_delivery(delivery_channel, delivery_status);
create index if not exists idx_psy_notification_delivery_provider_message on psy_notification_delivery(provider_name, provider_message_id);

create table if not exists psy_user_device (
    id bigserial primary key,
    user_id bigint not null references sys_user(id),
    device_type varchar(32) not null,
    device_id varchar(128) not null,
    push_token varchar(512),
    app_version varchar(64),
    active_flag boolean not null default true,
    last_active_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_psy_user_device_user_device unique (user_id, device_id)
);

create index if not exists idx_psy_user_device_user_active on psy_user_device(user_id, active_flag);
create index if not exists idx_psy_user_device_push_token on psy_user_device(push_token);

create table if not exists psy_export_job (
    id varchar(64) primary key,
    status varchar(32) not null,
    report_id bigint,
    result_id bigint,
    export_format varchar(32),
    locale_tag varchar(64),
    desensitized_flag boolean not null default true,
    file_name varchar(255),
    content_type varchar(128),
    file_path varchar(1024),
    file_size bigint,
    file_bytes bytea,
    error_message text,
    created_at timestamp not null default current_timestamp,
    completed_at timestamp,
    updated_at timestamp not null default current_timestamp
);

alter table psy_export_job add column if not exists report_id bigint;
alter table psy_export_job add column if not exists result_id bigint;
alter table psy_export_job add column if not exists export_format varchar(32);
alter table psy_export_job add column if not exists locale_tag varchar(64);
alter table psy_export_job add column if not exists desensitized_flag boolean not null default true;
alter table psy_export_job add column if not exists file_path varchar(1024);
alter table psy_export_job add column if not exists file_size bigint;
create index if not exists idx_psy_export_job_status on psy_export_job(status);
create index if not exists idx_psy_export_job_created_at on psy_export_job(created_at);
create index if not exists idx_psy_export_job_report on psy_export_job(report_id);
create index if not exists idx_psy_export_job_result on psy_export_job(result_id);
