-- 高校/企业心理测评与预警系统数据库建表草案
-- 说明：
-- 1. 本脚本基于 PostgreSQL 设计
-- 2. 认证与权限基础表复用 auth-starter，以下仅创建心理业务相关表
-- 3. 如需正式落库，建议结合真实字段长度、索引策略和迁移工具进一步调整

create table if not exists psy_user_profile (
    id bigserial primary key,
    user_id bigint not null,
    user_type varchar(32) not null,
    student_no varchar(64),
    employee_no varchar(64),
    college_name varchar(128),
    class_name varchar(128),
    department_name varchar(128),
    position_name varchar(128),
    focus_flag boolean not null default false,
    latest_assessment_time timestamp,
    latest_risk_level varchar(32),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_psy_user_profile_user unique (user_id),
    constraint fk_psy_user_profile_user foreign key (user_id) references sys_user(id)
);

create table if not exists psy_scale (
    id bigserial primary key,
    scale_code varchar(64) not null,
    scale_name varchar(255) not null,
    description text,
    applicable_target varchar(128),
    version_no varchar(32) not null,
    status varchar(32) not null,
    anonymous_supported boolean not null default false,
    report_template text,
    created_by bigint,
    created_at timestamp not null default current_timestamp,
    updated_by bigint,
    updated_at timestamp not null default current_timestamp,
    constraint uq_psy_scale_code_version unique (scale_code, version_no)
);

create table if not exists psy_scale_dimension (
    id bigserial primary key,
    scale_id bigint not null,
    dimension_code varchar(64) not null,
    dimension_name varchar(255) not null,
    description text,
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_psy_scale_dimension_code unique (scale_id, dimension_code),
    constraint fk_psy_scale_dimension_scale foreign key (scale_id) references psy_scale(id)
);

create table if not exists psy_scale_question (
    id bigserial primary key,
    scale_id bigint not null,
    dimension_id bigint,
    question_no int not null,
    question_title text not null,
    question_type varchar(32) not null,
    required_flag boolean not null default true,
    reverse_score_flag boolean not null default false,
    weight_value numeric(10,2) not null default 1.00,
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_psy_scale_question_no unique (scale_id, question_no),
    constraint fk_psy_scale_question_scale foreign key (scale_id) references psy_scale(id),
    constraint fk_psy_scale_question_dimension foreign key (dimension_id) references psy_scale_dimension(id)
);

create table if not exists psy_scale_option (
    id bigserial primary key,
    question_id bigint not null,
    option_code varchar(64) not null,
    option_label varchar(255) not null,
    score_value numeric(10,2) not null,
    sort_no int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_psy_scale_option_code unique (question_id, option_code),
    constraint fk_psy_scale_option_question foreign key (question_id) references psy_scale_question(id)
);

create table if not exists psy_scale_scoring_rule (
    id bigserial primary key,
    scale_id bigint not null,
    rule_type varchar(64) not null,
    dimension_id bigint,
    expression text,
    weight_value numeric(10,2),
    enabled_flag boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_scale_scoring_rule_scale foreign key (scale_id) references psy_scale(id),
    constraint fk_psy_scale_scoring_rule_dimension foreign key (dimension_id) references psy_scale_dimension(id)
);

create table if not exists psy_scale_result_rule (
    id bigserial primary key,
    scale_id bigint not null,
    dimension_id bigint,
    risk_level varchar(32) not null,
    score_min numeric(10,2) not null,
    score_max numeric(10,2) not null,
    result_title varchar(255),
    result_description text,
    suggestion_text text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_scale_result_rule_scale foreign key (scale_id) references psy_scale(id),
    constraint fk_psy_scale_result_rule_dimension foreign key (dimension_id) references psy_scale_dimension(id)
);

create table if not exists psy_assessment_task (
    id bigserial primary key,
    task_name varchar(255) not null,
    scale_id bigint not null,
    task_mode varchar(32) not null,
    anonymous_flag boolean not null default false,
    allow_save_flag boolean not null default true,
    allow_timeout_submit_flag boolean not null default false,
    allow_retake_flag boolean not null default false,
    start_time timestamp not null,
    end_time timestamp not null,
    status varchar(32) not null,
    created_by bigint,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_assessment_task_scale foreign key (scale_id) references psy_scale(id)
);

create table if not exists psy_assessment_task_assignment (
    id bigserial primary key,
    task_id bigint not null,
    target_type varchar(32) not null,
    target_id bigint not null,
    assigned_by bigint,
    assigned_at timestamp not null default current_timestamp,
    constraint fk_psy_assessment_task_assignment_task foreign key (task_id) references psy_assessment_task(id)
);

create table if not exists psy_assessment_answer_sheet (
    id bigserial primary key,
    task_id bigint not null,
    scale_id bigint not null,
    user_id bigint,
    answer_status varchar(32) not null,
    version_no int not null default 1,
    start_time timestamp,
    submit_time timestamp,
    duration_seconds int,
    anonymous_token varchar(128),
    submit_token varchar(128),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_assessment_answer_sheet_task foreign key (task_id) references psy_assessment_task(id),
    constraint fk_psy_assessment_answer_sheet_scale foreign key (scale_id) references psy_scale(id),
    constraint fk_psy_assessment_answer_sheet_user foreign key (user_id) references sys_user(id)
);
create unique index if not exists uk_psy_answer_sheet_submit_token_user_task
    on psy_assessment_answer_sheet(task_id, user_id, submit_token)
    where answer_status = 'SUBMITTED' and user_id is not null and submit_token is not null;

create table if not exists psy_assessment_answer_item (
    id bigserial primary key,
    answer_sheet_id bigint not null,
    question_id bigint not null,
    option_id bigint,
    answer_text text,
    score_value numeric(10,2),
    created_at timestamp not null default current_timestamp,
    constraint fk_psy_assessment_answer_item_sheet foreign key (answer_sheet_id) references psy_assessment_answer_sheet(id),
    constraint fk_psy_assessment_answer_item_question foreign key (question_id) references psy_scale_question(id),
    constraint fk_psy_assessment_answer_item_option foreign key (option_id) references psy_scale_option(id)
);

create table if not exists psy_assessment_result (
    id bigserial primary key,
    answer_sheet_id bigint not null,
    total_score numeric(10,2) not null,
    risk_level varchar(32) not null,
    warning_flag boolean not null default false,
    result_summary text,
    scored_at timestamp not null default current_timestamp,
    created_at timestamp not null default current_timestamp,
    constraint uq_psy_assessment_result_sheet unique (answer_sheet_id),
    constraint fk_psy_assessment_result_sheet foreign key (answer_sheet_id) references psy_assessment_answer_sheet(id)
);

create table if not exists psy_assessment_result_dimension (
    id bigserial primary key,
    result_id bigint not null,
    dimension_id bigint not null,
    dimension_score numeric(10,2) not null,
    risk_level varchar(32),
    summary_text text,
    constraint fk_psy_assessment_result_dimension_result foreign key (result_id) references psy_assessment_result(id),
    constraint fk_psy_assessment_result_dimension_dimension foreign key (dimension_id) references psy_scale_dimension(id)
);

create table if not exists psy_report (
    id bigserial primary key,
    result_id bigint not null,
    report_type varchar(32) not null,
    author_user_id bigint,
    report_title varchar(255),
    report_content text not null,
    version_no int not null default 1,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_report_result foreign key (result_id) references psy_assessment_result(id),
    constraint fk_psy_report_author foreign key (author_user_id) references sys_user(id)
);

create table if not exists psy_warning_record (
    id bigserial primary key,
    result_id bigint not null,
    warning_level varchar(32) not null,
    warning_priority varchar(32) not null,
    warning_reason text,
    status varchar(32) not null,
    deadline_time timestamp,
    first_response_time timestamp,
    closed_time timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_warning_record_result foreign key (result_id) references psy_assessment_result(id)
);

create table if not exists psy_warning_assignment (
    id bigserial primary key,
    warning_id bigint not null,
    assignee_user_id bigint not null,
    assigned_by bigint,
    assigned_at timestamp not null default current_timestamp,
    claim_time timestamp,
    constraint fk_psy_warning_assignment_warning foreign key (warning_id) references psy_warning_record(id),
    constraint fk_psy_warning_assignment_assignee foreign key (assignee_user_id) references sys_user(id)
);

create table if not exists psy_intervention_record (
    id bigserial primary key,
    warning_id bigint not null,
    counselor_user_id bigint,
    current_status varchar(32) not null,
    plan_text text,
    close_summary text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_intervention_record_warning foreign key (warning_id) references psy_warning_record(id),
    constraint fk_psy_intervention_record_counselor foreign key (counselor_user_id) references sys_user(id)
);

create table if not exists psy_intervention_status_log (
    id bigserial primary key,
    intervention_id bigint not null,
    from_status varchar(32),
    to_status varchar(32) not null,
    remark text,
    changed_by bigint,
    changed_at timestamp not null default current_timestamp,
    constraint fk_psy_intervention_status_log_intervention foreign key (intervention_id) references psy_intervention_record(id),
    constraint fk_psy_intervention_status_log_changed_by foreign key (changed_by) references sys_user(id)
);

create table if not exists psy_counselor_schedule (
    id bigserial primary key,
    counselor_user_id bigint not null,
    schedule_date date not null,
    start_time timestamp not null,
    end_time timestamp not null,
    quota_count int not null default 1,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    constraint fk_psy_counselor_schedule_counselor foreign key (counselor_user_id) references sys_user(id)
);

create table if not exists psy_appointment_record (
    id bigserial primary key,
    user_id bigint not null,
    counselor_user_id bigint not null,
    warning_id bigint,
    schedule_id bigint,
    appointment_status varchar(32) not null,
    source_type varchar(32) not null,
    remark text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_appointment_record_user foreign key (user_id) references sys_user(id),
    constraint fk_psy_appointment_record_counselor foreign key (counselor_user_id) references sys_user(id),
    constraint fk_psy_appointment_record_warning foreign key (warning_id) references psy_warning_record(id),
    constraint fk_psy_appointment_record_schedule foreign key (schedule_id) references psy_counselor_schedule(id)
);

create table if not exists psy_counseling_record (
    id bigserial primary key,
    appointment_id bigint not null,
    counselor_user_id bigint not null,
    summary_text text,
    suggestion_text text,
    need_retest_flag boolean not null default false,
    need_transfer_flag boolean not null default false,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_psy_counseling_record_appointment foreign key (appointment_id) references psy_appointment_record(id),
    constraint fk_psy_counseling_record_counselor foreign key (counselor_user_id) references sys_user(id)
);

create table if not exists psy_notification (
    id bigserial primary key,
    notification_type varchar(32) not null,
    title varchar(255) not null,
    content text not null,
    biz_type varchar(32),
    biz_id bigint,
    created_at timestamp not null default current_timestamp
);

create table if not exists psy_notification_delivery (
    id bigserial primary key,
    notification_id bigint not null,
    receiver_user_id bigint not null,
    read_flag boolean not null default false,
    read_time timestamp,
    delivery_channel varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    constraint fk_psy_notification_delivery_notification foreign key (notification_id) references psy_notification(id),
    constraint fk_psy_notification_delivery_receiver foreign key (receiver_user_id) references sys_user(id)
);

create table if not exists psy_audit_log (
    id bigserial primary key,
    operator_user_id bigint,
    action_type varchar(64) not null,
    biz_type varchar(64),
    biz_id bigint,
    action_detail text,
    ip_address varchar(64),
    created_at timestamp not null default current_timestamp,
    constraint fk_psy_audit_log_operator foreign key (operator_user_id) references sys_user(id)
);

create table if not exists psy_operation_record (
    id bigserial primary key,
    operator_user_id bigint,
    biz_type varchar(64),
    biz_id bigint,
    operation_type varchar(64) not null,
    operation_result varchar(32) not null,
    remark text,
    created_at timestamp not null default current_timestamp,
    constraint fk_psy_operation_record_operator foreign key (operator_user_id) references sys_user(id)
);

create index if not exists idx_psy_scale_code on psy_scale(scale_code);
create index if not exists idx_psy_assessment_task_scale on psy_assessment_task(scale_id);
create index if not exists idx_psy_assessment_task_assignment_task on psy_assessment_task_assignment(task_id);
create index if not exists idx_psy_answer_sheet_task on psy_assessment_answer_sheet(task_id);
create index if not exists idx_psy_answer_sheet_user on psy_assessment_answer_sheet(user_id);
create index if not exists idx_psy_result_sheet on psy_assessment_result(answer_sheet_id);
create index if not exists idx_psy_warning_result on psy_warning_record(result_id);
create index if not exists idx_psy_warning_status on psy_warning_record(status);
create index if not exists idx_psy_appointment_user on psy_appointment_record(user_id);
create index if not exists idx_psy_appointment_counselor on psy_appointment_record(counselor_user_id);
create index if not exists idx_psy_notification_receiver on psy_notification_delivery(receiver_user_id);
-- Historical draft only. Do not use this file to initialize a new environment.
-- Use backend/src/main/resources/schema-psy.sql and auth-starter/doc/schema-postgresql.sql instead.
