-- Minimal, isolated browser-E2E identities and one technical-only scale.
-- Nothing in this file is production, licensed, normative, or clinical content;
-- it is executed only inside disposable psy_e2e_* schemas.

insert into sys_tenant (tenant_code, tenant_name, is_default)
values
    ('DEFAULT', 'E2E Default Tenant', 1),
    ('CAMPUS_DEMO', 'E2E Campus Tenant', 0)
on conflict (tenant_code) do update
set tenant_name = excluded.tenant_name,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select tenant.id, role.role_code, role.role_name, role.data_scope, 1
from sys_tenant tenant
cross join (
    values
        ('SYS_ADMIN', 'System Admin', 'TENANT'),
        ('ASSESSMENT_ADMIN', 'Assessment Admin', 'TENANT'),
        ('COUNSELOR', 'Counselor', 'TENANT'),
        ('USER', 'Respondent', 'SELF')
) as role(role_code, role_name, data_scope)
where tenant.tenant_code in ('DEFAULT', 'CAMPUS_DEMO')
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

-- Global roles are deliberately tenantless. Their use is accepted only when
-- the authenticated principal is also tenantless; tenant-bound administrators
-- remain tenant-scoped in application code.
insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select null, seed.role_code, seed.role_name, 'ALL', 1
from (
    values
        ('SUPER_ADMIN', 'Global E2E Administrator'),
        ('ASSESSMENT_ADMIN', 'Tenantless E2E Assessment Administrator')
) as seed(role_code, role_name)
where not exists (
    select 1 from sys_role existing
    where existing.tenant_id is null
      and existing.role_code = seed.role_code
);

insert into sys_user (
    username, display_name, status, tenant_id, register_source,
    password_version, failed_login_attempts, deleted
)
select seed.username, seed.display_name, 1, tenant.id, 'E2E', 1, 0, 0
from (
    values
        ('e2e_admin', 'Default E2E Administrator', 'DEFAULT'),
        ('assessor', 'Default E2E Assessor', 'DEFAULT'),
        ('counselor', 'Default E2E Counselor', 'DEFAULT'),
        ('respondent', 'Default E2E Respondent', 'DEFAULT'),
        ('anonymous_respondent', 'Anonymous E2E Respondent', 'DEFAULT'),
        ('anonymous_peer', 'Anonymous E2E Peer', 'DEFAULT'),
        ('campus_assessor', 'Campus E2E Assessor', 'CAMPUS_DEMO')
) as seed(username, display_name, tenant_code)
join sys_tenant tenant on tenant.tenant_code = seed.tenant_code
on conflict (username) do update
set display_name = excluded.display_name,
    status = excluded.status,
    tenant_id = excluded.tenant_id,
    register_source = excluded.register_source,
    password_version = excluded.password_version,
    failed_login_attempts = 0,
    locked_until = null,
    deleted = 0,
    updated_at = current_timestamp;

insert into sys_user (
    username, display_name, status, tenant_id, register_source,
    password_version, failed_login_attempts, deleted
)
values
    ('global_admin', 'Global E2E Administrator', 1, null, 'E2E', 1, 0, 0),
    ('tenantless_assessor', 'Tenantless E2E Assessor', 1, null, 'E2E', 1, 0, 0)
on conflict (username) do update
set display_name = excluded.display_name,
    status = excluded.status,
    tenant_id = null,
    register_source = excluded.register_source,
    password_version = excluded.password_version,
    failed_login_attempts = 0,
    locked_until = null,
    deleted = 0,
    updated_at = current_timestamp;

insert into sys_auth (user_id, identity_type, principal_key, credential_hash, metadata_json, enabled)
select user_record.id, 'PASSWORD', user_record.username, '{noop}ChangeMe123', '{}'::jsonb, 1
from sys_user user_record
where user_record.username in (
    'e2e_admin', 'assessor', 'counselor', 'respondent',
    'anonymous_respondent', 'anonymous_peer', 'campus_assessor',
    'global_admin', 'tenantless_assessor'
)
on conflict (identity_type, principal_key) do update
set user_id = excluded.user_id,
    credential_hash = excluded.credential_hash,
    metadata_json = excluded.metadata_json,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

insert into sys_user_role (user_id, role_id)
select user_record.id, role.id
from sys_user user_record
join sys_role role on role.tenant_id = user_record.tenant_id
where (user_record.username in ('assessor', 'campus_assessor') and role.role_code = 'ASSESSMENT_ADMIN')
   or (user_record.username = 'e2e_admin' and role.role_code = 'SYS_ADMIN')
   or (user_record.username = 'counselor' and role.role_code = 'COUNSELOR')
   or (user_record.username in ('respondent', 'anonymous_respondent', 'anonymous_peer') and role.role_code = 'USER')
on conflict (user_id, role_id) do nothing;

insert into sys_user_role (user_id, role_id)
select user_record.id, role.id
from sys_user user_record
join sys_role role on role.tenant_id is null
where (user_record.username = 'global_admin' and role.role_code = 'SUPER_ADMIN')
   or (user_record.username = 'tenantless_assessor' and role.role_code = 'ASSESSMENT_ADMIN')
on conflict (user_id, role_id) do nothing;

insert into psy_user_device (
    user_id, device_type, device_id, push_token, app_version,
    active_flag, last_active_at, created_at, updated_at
)
select respondent.id, 'WEB', 'e2e-respondent-push-device', 'e2e-respondent-push-token', 'e2e',
       true, current_timestamp, current_timestamp, current_timestamp
from sys_user respondent
where respondent.username = 'respondent'
on conflict (user_id, device_id) do update
set push_token = excluded.push_token,
    active_flag = true,
    last_active_at = excluded.last_active_at,
    updated_at = excluded.updated_at;

insert into psy_notification_policy (
    notification_type, in_app_enabled, push_enabled, cooldown_minutes, created_at, updated_at
)
values ('REPORT_GENERATED', true, true, 0, current_timestamp, current_timestamp)
on conflict (notification_type) do update
set in_app_enabled = true,
    push_enabled = true,
    cooldown_minutes = 0,
    updated_at = excluded.updated_at;

insert into psy_scale (
    tenant_id, scale_code, scale_name, description, applicable_target,
    version_no, version_group_id, current_version_flag, status,
    score_method, score_coefficient, high_risk_warning_enabled, anonymous_supported,
    published_content_hash, created_by
)
select
    administrator.tenant_id,
    'E2E_CORE_TECH_FIXTURE',
    'E2E Core Risk Technical Fixture',
    'Browser automation only. This is not a validated psychological scale.',
    'E2E_ONLY',
    'v1',
    880001,
    true,
    'PUBLISHED',
    'SIMPLE_SUM',
    1.0,
    true,
    true,
    repeat('a', 64),
    administrator.id
from sys_user administrator
where administrator.username = 'e2e_admin'
on conflict (tenant_id, scale_code, version_no) where tenant_id is not null do update
set scale_name = excluded.scale_name,
    description = excluded.description,
    applicable_target = excluded.applicable_target,
    version_group_id = excluded.version_group_id,
    current_version_flag = excluded.current_version_flag,
    status = excluded.status,
    score_method = excluded.score_method,
    score_coefficient = excluded.score_coefficient,
    high_risk_warning_enabled = excluded.high_risk_warning_enabled,
    anonymous_supported = excluded.anonymous_supported,
    published_content_hash = excluded.published_content_hash,
    created_by = excluded.created_by,
    updated_at = current_timestamp;

insert into psy_scale_dimension (scale_id, dimension_code, dimension_name, description, sort_no)
select scale.id, 'E2E_RISK', 'E2E Risk', 'Technical-only E2E scoring dimension.', 1
from psy_scale scale
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1'
on conflict (scale_id, dimension_code) do update
set dimension_name = excluded.dimension_name,
    description = excluded.description,
    sort_no = excluded.sort_no,
    updated_at = current_timestamp;

insert into psy_scale_question (
    scale_id, dimension_id, question_no, question_title, question_type,
    required_flag, reverse_score_flag, weight_value, sort_no
)
select
    scale.id,
    dimension.id,
    question.question_no,
    question.question_title,
    'SINGLE_CHOICE',
    true,
    false,
    1.0,
    question.question_no
from psy_scale scale
join psy_scale_dimension dimension
  on dimension.scale_id = scale.id and dimension.dimension_code = 'E2E_RISK'
cross join (
    values
        (1, 'E2E technical question one'),
        (2, 'E2E technical question two'),
        (3, 'E2E technical question three')
) as question(question_no, question_title)
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1'
on conflict (scale_id, question_no) do update
set dimension_id = excluded.dimension_id,
    question_title = excluded.question_title,
    question_type = excluded.question_type,
    required_flag = excluded.required_flag,
    reverse_score_flag = excluded.reverse_score_flag,
    weight_value = excluded.weight_value,
    sort_no = excluded.sort_no,
    updated_at = current_timestamp;

insert into psy_scale_option (
    question_id, option_code, option_label, score_value, exclusive_flag, sort_no
)
select
    question.id,
    option.option_code,
    option.option_label,
    option.score_value,
    false,
    option.sort_no
from psy_scale scale
join psy_scale_question question on question.scale_id = scale.id
cross join (
    values
        ('A', 'Technical low', 1.0, 1),
        ('B', 'Technical medium', 2.0, 2),
        ('C', 'Technical elevated', 3.0, 3),
        ('D', 'Technical high', 4.0, 4)
) as option(option_code, option_label, score_value, sort_no)
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1'
on conflict (question_id, option_code) do update
set option_label = excluded.option_label,
    score_value = excluded.score_value,
    exclusive_flag = excluded.exclusive_flag,
    sort_no = excluded.sort_no,
    updated_at = current_timestamp;

insert into psy_scale_translation (
    scale_id, locale_code, scale_name, description, instruction_text,
    non_diagnostic_text, high_risk_action_text, review_status
)
select
    scale.id,
    translation.locale_code,
    translation.scale_name,
    translation.description,
    translation.instruction_text,
    translation.non_diagnostic_text,
    translation.high_risk_action_text,
    'APPROVED'
from psy_scale scale
cross join (
    values
        (
            'en',
            'E2E Core Risk Technical Fixture',
            'Browser automation only. This is not a validated psychological scale.',
            'Choose one technical option for every question.',
            'This technical screening fixture is not a clinical diagnosis.',
            'Use only the controlled E2E response workflow.'
        ),
        (
            'zh-CN',
            'E2E 核心风险技术测试量表',
            '仅用于浏览器自动化，不是经过验证的心理量表。',
            '请为每个问题选择一个技术测试选项。',
            '此技术筛查夹具不构成临床诊断。',
            '仅使用受控的 E2E 响应流程。'
        ),
        (
            'ja-JP',
            'E2E コアリスク技術テスト尺度',
            'ブラウザー自動化専用であり、検証済みの心理尺度ではありません。',
            '各質問で技術テスト用の選択肢を一つ選んでください。',
            'この技術スクリーニング用フィクスチャは臨床診断ではありません。',
            '管理された E2E 対応フローのみを使用してください。'
        )
) as translation(
    locale_code, scale_name, description, instruction_text,
    non_diagnostic_text, high_risk_action_text
)
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1'
on conflict (scale_id, locale_code) do update
set scale_name = excluded.scale_name,
    description = excluded.description,
    instruction_text = excluded.instruction_text,
    non_diagnostic_text = excluded.non_diagnostic_text,
    high_risk_action_text = excluded.high_risk_action_text,
    review_status = excluded.review_status,
    updated_at = current_timestamp;

insert into psy_scale_dimension_translation (
    dimension_id, locale_code, dimension_name, description, review_status
)
select
    dimension.id,
    translation.locale_code,
    translation.dimension_name,
    translation.description,
    'APPROVED'
from psy_scale scale
join psy_scale_dimension dimension on dimension.scale_id = scale.id and dimension.dimension_code = 'E2E_RISK'
cross join (
    values
        ('en', 'E2E Risk', 'Technical-only E2E scoring dimension.'),
        ('zh-CN', 'E2E 风险', '仅用于 E2E 的技术计分维度。'),
        ('ja-JP', 'E2E リスク', 'E2E 専用の技術採点ディメンション。')
) as translation(locale_code, dimension_name, description)
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1'
on conflict (dimension_id, locale_code) do update
set dimension_name = excluded.dimension_name,
    description = excluded.description,
    review_status = excluded.review_status,
    updated_at = current_timestamp;

insert into psy_scale_question_translation (
    question_id, locale_code, question_title, review_status
)
select
    question.id,
    translation.locale_code,
    translation.question_title,
    'APPROVED'
from psy_scale scale
join psy_scale_question question on question.scale_id = scale.id
join (
    values
        (1, 'en', 'E2E technical question one'),
        (2, 'en', 'E2E technical question two'),
        (3, 'en', 'E2E technical question three'),
        (1, 'zh-CN', 'E2E 技术问题一'),
        (2, 'zh-CN', 'E2E 技术问题二'),
        (3, 'zh-CN', 'E2E 技术问题三'),
        (1, 'ja-JP', 'E2E 技術質問一'),
        (2, 'ja-JP', 'E2E 技術質問二'),
        (3, 'ja-JP', 'E2E 技術質問三')
) as translation(question_no, locale_code, question_title)
  on translation.question_no = question.question_no
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1'
on conflict (question_id, locale_code) do update
set question_title = excluded.question_title,
    review_status = excluded.review_status,
    updated_at = current_timestamp;

insert into psy_scale_option_translation (
    option_id, locale_code, option_label, review_status
)
select
    option.id,
    translation.locale_code,
    translation.option_label,
    'APPROVED'
from psy_scale scale
join psy_scale_question question on question.scale_id = scale.id
join psy_scale_option option on option.question_id = question.id
join (
    values
        ('A', 'en', 'Technical low'),
        ('B', 'en', 'Technical medium'),
        ('C', 'en', 'Technical elevated'),
        ('D', 'en', 'Technical high'),
        ('A', 'zh-CN', '技术低值'),
        ('B', 'zh-CN', '技术中值'),
        ('C', 'zh-CN', '技术升高'),
        ('D', 'zh-CN', '技术高值'),
        ('A', 'ja-JP', '技術低値'),
        ('B', 'ja-JP', '技術中値'),
        ('C', 'ja-JP', '技術上昇'),
        ('D', 'ja-JP', '技術高値')
) as translation(option_code, locale_code, option_label)
  on translation.option_code = option.option_code
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1'
on conflict (option_id, locale_code) do update
set option_label = excluded.option_label,
    review_status = excluded.review_status,
    updated_at = current_timestamp;

delete from psy_scale_result_rule
where scale_id in (
    select id from psy_scale
    where scale_code = 'E2E_CORE_TECH_FIXTURE' and version_no = 'v1'
);

insert into psy_scale_result_rule (
    scale_id, risk_level, score_min, score_max, score_source,
    result_title, result_description, suggestion_text
)
select
    scale.id,
    result_rule.risk_level,
    result_rule.score_min,
    result_rule.score_max,
    'RAW_SCORE',
    result_rule.result_title,
    result_rule.result_description,
    result_rule.suggestion_text
from psy_scale scale
cross join (
    values
        ('NORMAL', 3.0, 5.99, 'E2E normal', 'Technical E2E normal result.', 'No clinical meaning.'),
        ('MEDIUM', 6.0, 8.99, 'E2E medium', 'Technical E2E medium result.', 'No clinical meaning.'),
        ('HIGH', 9.0, 12.0, 'E2E high', 'Technical E2E high result.', 'Exercise the warning workflow only.')
) as result_rule(risk_level, score_min, score_max, result_title, result_description, suggestion_text)
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1';

insert into psy_scale_result_rule_translation (
    result_rule_id, locale_code, result_title, result_description, suggestion_text, review_status
)
select
    result_rule.id,
    translation.locale_code,
    translation.result_title,
    translation.result_description,
    translation.suggestion_text,
    'APPROVED'
from psy_scale scale
join psy_scale_result_rule result_rule on result_rule.scale_id = scale.id
join (
    values
        ('NORMAL', 'en', 'E2E normal', 'Technical E2E normal result.', 'No clinical meaning.'),
        ('MEDIUM', 'en', 'E2E medium', 'Technical E2E medium result.', 'No clinical meaning.'),
        ('HIGH', 'en', 'E2E high', 'Technical E2E high result.', 'Exercise the warning workflow only.'),
        ('NORMAL', 'zh-CN', 'E2E 正常结果', 'E2E 技术正常结果。', '不具有临床含义。'),
        ('MEDIUM', 'zh-CN', 'E2E 中等结果', 'E2E 技术中等结果。', '不具有临床含义。'),
        ('HIGH', 'zh-CN', 'E2E 高值结果', 'E2E 技术高值结果。', '仅用于演练受控预警流程。'),
        ('NORMAL', 'ja-JP', 'E2E 正常結果', 'E2E 技術テストの正常結果です。', '臨床的な意味はありません。'),
        ('MEDIUM', 'ja-JP', 'E2E 中等結果', 'E2E 技術テストの中等結果です。', '臨床的な意味はありません。'),
        ('HIGH', 'ja-JP', 'E2E 高値結果', 'E2E 技術テストの高値結果です。', '管理されたアラートフローの確認専用です。')
) as translation(risk_level, locale_code, result_title, result_description, suggestion_text)
  on translation.risk_level = result_rule.risk_level
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1'
on conflict (result_rule_id, locale_code) do update
set result_title = excluded.result_title,
    result_description = excluded.result_description,
    suggestion_text = excluded.suggestion_text,
    review_status = excluded.review_status,
    updated_at = current_timestamp;

-- A non-clinical, pre-seeded report lets the browser case exercise manual
-- DEAD_LETTER replay and artifact download without adding a test-only API.
delete from psy_export_job where id = 'e2e-dead-export-default';
delete from psy_report where id = 990004;
delete from psy_assessment_result where id = 990003;
delete from psy_assessment_answer_sheet where id = 990002;
delete from psy_assessment_task where id = 990001;

insert into psy_assessment_task (
    id, tenant_id, task_name, scale_id, scale_version_no, scale_version_group_id,
    task_mode, anonymous_flag, allow_save_flag, start_time, end_time, status, created_by
)
select 990001, tenant.id, 'E2E Export Replay Fixture', scale.id, scale.version_no,
       scale.version_group_id, 'SCREENING', false, true,
       current_timestamp - interval '1 hour', current_timestamp + interval '1 day', 'CLOSED', administrator.id
from sys_tenant tenant
join psy_scale scale on scale.tenant_id = tenant.id
join sys_user administrator on administrator.username = 'e2e_admin'
where tenant.tenant_code = 'DEFAULT'
  and scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1';

insert into psy_assessment_answer_sheet (
    id, tenant_id, task_id, scale_id, user_id, answer_status, version_no,
    submit_time, submit_token
)
select 990002, task.tenant_id, task.id, task.scale_id, respondent.id, 'SUBMITTED', 1,
       current_timestamp, 'e2e-export-replay-submit'
from psy_assessment_task task
join sys_user respondent on respondent.username = 'respondent'
where task.id = 990001;

insert into psy_assessment_answer_item (
    id, answer_sheet_id, question_id, option_id, answer_text, answer_value, score_value
)
select 990010 + question.question_no, 990002, question.id, option.id,
       option.option_label, option.score_value, option.score_value
from psy_scale_question question
join psy_scale_option option on option.question_id = question.id and option.option_code = 'A'
join psy_scale scale on scale.id = question.scale_id
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1';

insert into psy_assessment_result (
    id, answer_sheet_id, total_score, risk_level, warning_flag, result_summary, high_risk_flag
) values (990003, 990002, 3.0, 'NORMAL', false, 'Technical export replay fixture.', false);

insert into psy_report (
    id, result_id, report_type, author_user_id, report_title, report_content, version_no
)
select 990004, 990003, 'PERSONAL', administrator.id,
       'E2E Export Replay Fixture', 'Technical-only export replay fixture. Not clinical content.', 1
from sys_user administrator
where administrator.username = 'e2e_admin';

insert into psy_export_job (
    id, tenant_id, created_by, status, report_id, export_format, locale_tag,
    desensitized_flag, retry_count, error_message, dead_letter_at
)
select 'e2e-dead-export-default', tenant.id, administrator.id, 'DEAD_LETTER', 990004,
       'TEXT', 'en-US', true, 3, 'E2E seeded dead letter', current_timestamp
from sys_tenant tenant
join sys_user administrator on administrator.username = 'e2e_admin'
where tenant.tenant_code = 'DEFAULT';

delete from psy_scale_high_risk_rule
where scale_id in (
    select id from psy_scale
    where scale_code = 'E2E_CORE_TECH_FIXTURE' and version_no = 'v1'
);

insert into psy_scale_high_risk_rule (
    scale_id, rule_code, question_id, option_id, warning_level,
    result_title, result_description, suggestion_text, sort_no
)
select
    scale.id,
    'E2E_HIGH_OPTION',
    question.id,
    option.id,
    'HIGH',
    'E2E technical high-risk trigger',
    'Technical automation trigger without clinical meaning.',
    'Exercise the controlled warning and intervention workflow.',
    1
from psy_scale scale
join psy_scale_question question on question.scale_id = scale.id and question.question_no = 3
join psy_scale_option option on option.question_id = question.id and option.option_code = 'D'
where scale.scale_code = 'E2E_CORE_TECH_FIXTURE'
  and scale.version_no = 'v1';

insert into psy_scale_high_risk_rule_translation (
    high_risk_rule_id, locale_code, result_title, result_description, suggestion_text, review_status
)
select
    rule.id,
    translation.locale_code,
    translation.result_title,
    translation.result_description,
    translation.suggestion_text,
    'APPROVED'
from psy_scale scale
join psy_scale_high_risk_rule rule on rule.scale_id = scale.id and rule.rule_code = 'E2E_HIGH_OPTION'
cross join (
    values
        ('en', 'E2E technical high-risk trigger', 'Technical automation trigger without clinical meaning.', 'Exercise the controlled warning and intervention workflow.'),
        ('zh-CN', 'E2E 技术高风险触发', '这是不具有临床含义的技术自动化触发。', '请演练受控预警与干预流程。'),
        ('ja-JP', 'E2E 技術高リスクトリガー', '臨床的な意味を持たない技術自動化トリガーです。', '管理されたアラートと介入フローを確認してください。')
) as translation(locale_code, result_title, result_description, suggestion_text)
on conflict (high_risk_rule_id, locale_code) do update
set result_title = excluded.result_title,
    result_description = excluded.result_description,
    suggestion_text = excluded.suggestion_text,
    review_status = excluded.review_status,
    updated_at = current_timestamp;
