create table if not exists psy_scale (
    id bigserial primary key,
    scale_code varchar(64) not null,
    scale_name varchar(255) not null,
    description text,
    applicable_target varchar(128),
    version_no varchar(32),
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

create unique index if not exists uk_psy_scale_code on psy_scale(scale_code);
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
    result_title varchar(255),
    result_description text,
    suggestion_text text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_scale_result_rule_scale_id on psy_scale_result_rule(scale_id);

create table if not exists psy_assessment_task (
    id bigserial primary key,
    task_name varchar(255) not null,
    scale_id bigint not null references psy_scale(id),
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

create index if not exists idx_psy_assessment_task_scale_id on psy_assessment_task(scale_id);
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
    start_time timestamp,
    submit_time timestamp,
    duration_seconds int,
    anonymous_token varchar(128),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_assessment_answer_sheet_task_id on psy_assessment_answer_sheet(task_id);
create index if not exists idx_psy_assessment_answer_sheet_user_id on psy_assessment_answer_sheet(user_id);

create table if not exists psy_assessment_answer_item (
    id bigserial primary key,
    answer_sheet_id bigint not null references psy_assessment_answer_sheet(id) on delete cascade,
    question_id bigint not null references psy_scale_question(id),
    option_id bigint references psy_scale_option(id),
    answer_text text,
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
    closed_time timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_psy_warning_record_result_id on psy_warning_record(result_id);
create index if not exists idx_psy_warning_record_status on psy_warning_record(status);

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
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

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
alter table psy_scale add column if not exists score_method varchar(32) not null default 'SIMPLE_SUM';
alter table psy_scale add column if not exists score_coefficient decimal(6, 4) not null default 1.0;

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
