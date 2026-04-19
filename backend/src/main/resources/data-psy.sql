-- Development seed data for lx-boot.
-- This file is intentionally idempotent so that local environments can bootstrap
-- tenant/group/role/user relationships and a small amount of demo business data.

begin;

select setval('sys_tenant_id_seq', coalesce((select max(id) from sys_tenant), 1), true);
select setval('sys_group_id_seq', coalesce((select max(id) from sys_group), 1), true);
select setval('sys_user_id_seq', coalesce((select max(id) from sys_user), 1), true);
select setval('sys_auth_id_seq', coalesce((select max(id) from sys_auth), 1), true);
select setval('sys_role_id_seq', coalesce((select max(id) from sys_role), 1), true);
select setval('sys_user_role_id_seq', coalesce((select max(id) from sys_user_role), 1), true);
select setval('sys_group_role_id_seq', coalesce((select max(id) from sys_group_role), 1), true);
select setval('psy_scale_id_seq', coalesce((select max(id) from psy_scale), 1), true);
select setval('psy_scale_dimension_id_seq', coalesce((select max(id) from psy_scale_dimension), 1), true);
select setval('psy_scale_question_id_seq', coalesce((select max(id) from psy_scale_question), 1), true);
select setval('psy_scale_option_id_seq', coalesce((select max(id) from psy_scale_option), 1), true);
select setval('psy_scale_result_rule_id_seq', coalesce((select max(id) from psy_scale_result_rule), 1), true);
select setval('psy_scale_high_risk_rule_id_seq', coalesce((select max(id) from psy_scale_high_risk_rule), 1), true);
select setval('psy_assessment_task_id_seq', coalesce((select max(id) from psy_assessment_task), 1), true);
select setval('psy_assessment_task_assignment_id_seq', coalesce((select max(id) from psy_assessment_task_assignment), 1), true);

-- Tenants
insert into sys_tenant (tenant_code, tenant_name, is_default)
values
    ('DEFAULT', 'Default Tenant', 1),
    ('CAMPUS_DEMO', 'Campus Demo Tenant', 0),
    ('ENTERPRISE_DEMO', 'Enterprise Demo Tenant', 0)
on conflict (tenant_code) do update
set tenant_name = excluded.tenant_name,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

-- Groups
insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select t.id, 'DEFAULT_ROOT', 'Default Tenant Root', null, null, 0
from sys_tenant t
where t.tenant_code = 'DEFAULT'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'DEFAULT_GENERAL',
    'Default General Users',
    root.id,
    cast(root.id as varchar),
    1
from sys_tenant t
join sys_group root on root.group_code = 'DEFAULT_ROOT'
where t.tenant_code = 'DEFAULT'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'DEFAULT_COUNSELING',
    'Default Counseling Center',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'DEFAULT_ROOT'
where t.tenant_code = 'DEFAULT'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'DEFAULT_ASSESSMENT',
    'Default Assessment Center',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'DEFAULT_ROOT'
where t.tenant_code = 'DEFAULT'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'DEFAULT_ORG',
    'Default Organization Office',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'DEFAULT_ROOT'
where t.tenant_code = 'DEFAULT'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'DEFAULT_SYSTEM',
    'Default System Operations',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'DEFAULT_ROOT'
where t.tenant_code = 'DEFAULT'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select t.id, 'CAMPUS_ROOT', 'Campus Demo Root', null, null, 0
from sys_tenant t
where t.tenant_code = 'CAMPUS_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'CAMPUS_CLASS_2026_A',
    'Campus Class 2026-A',
    root.id,
    cast(root.id as varchar),
    1
from sys_tenant t
join sys_group root on root.group_code = 'CAMPUS_ROOT'
where t.tenant_code = 'CAMPUS_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'CAMPUS_COUNSELING',
    'Campus Counseling Center',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'CAMPUS_ROOT'
where t.tenant_code = 'CAMPUS_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'CAMPUS_ASSESSMENT',
    'Campus Assessment Center',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'CAMPUS_ROOT'
where t.tenant_code = 'CAMPUS_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'CAMPUS_ORG',
    'Campus Organization Office',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'CAMPUS_ROOT'
where t.tenant_code = 'CAMPUS_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select t.id, 'ENTERPRISE_ROOT', 'Enterprise Demo Root', null, null, 0
from sys_tenant t
where t.tenant_code = 'ENTERPRISE_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'ENTERPRISE_STAFF',
    'Enterprise Staff',
    root.id,
    cast(root.id as varchar),
    1
from sys_tenant t
join sys_group root on root.group_code = 'ENTERPRISE_ROOT'
where t.tenant_code = 'ENTERPRISE_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'ENTERPRISE_WELLNESS',
    'Enterprise Wellness Team',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'ENTERPRISE_ROOT'
where t.tenant_code = 'ENTERPRISE_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'ENTERPRISE_ASSESSMENT',
    'Enterprise Assessment Team',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'ENTERPRISE_ROOT'
where t.tenant_code = 'ENTERPRISE_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

insert into sys_group (tenant_id, group_code, group_name, parent_id, ancestors, is_default)
select
    t.id,
    'ENTERPRISE_HR',
    'Enterprise HR Office',
    root.id,
    cast(root.id as varchar),
    0
from sys_tenant t
join sys_group root on root.group_code = 'ENTERPRISE_ROOT'
where t.tenant_code = 'ENTERPRISE_DEMO'
on conflict (group_code) do update
set tenant_id = excluded.tenant_id,
    group_name = excluded.group_name,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    is_default = excluded.is_default,
    updated_at = current_timestamp;

-- Roles
insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select t.id, 'USER', 'Respondent', 'SELF', 1
from sys_tenant t
where t.tenant_code in ('DEFAULT', 'CAMPUS_DEMO', 'ENTERPRISE_DEMO')
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select t.id, 'COUNSELOR', 'Counselor', 'TENANT', 1
from sys_tenant t
where t.tenant_code in ('DEFAULT', 'CAMPUS_DEMO', 'ENTERPRISE_DEMO')
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select t.id, 'ASSESSMENT_ADMIN', 'Assessment Admin', 'TENANT', 1
from sys_tenant t
where t.tenant_code in ('DEFAULT', 'CAMPUS_DEMO', 'ENTERPRISE_DEMO')
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select t.id, 'ORG_MANAGER', 'Organization Manager', 'TENANT', 1
from sys_tenant t
where t.tenant_code in ('DEFAULT', 'CAMPUS_DEMO', 'ENTERPRISE_DEMO')
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select t.id, 'SYS_ADMIN', 'System Administrator', 'ALL', 1
from sys_tenant t
where t.tenant_code = 'DEFAULT'
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select t.id, 'ADMIN', 'Administrator', 'ALL', 1
from sys_tenant t
where t.tenant_code = 'DEFAULT'
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

insert into sys_role (tenant_id, role_code, role_name, data_scope, enabled)
select t.id, 'SUPER_ADMIN', 'Super Administrator', 'ALL', 1
from sys_tenant t
where t.tenant_code = 'DEFAULT'
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

-- Group role bindings
insert into sys_group_role (group_id, role_id)
select g.id, r.id
from sys_group g
join sys_role r on r.tenant_id = g.tenant_id and r.role_code = 'USER'
where g.group_code in ('DEFAULT_GENERAL', 'CAMPUS_CLASS_2026_A', 'ENTERPRISE_STAFF')
on conflict (group_id, role_id) do nothing;

insert into sys_group_role (group_id, role_id)
select g.id, r.id
from sys_group g
join sys_role r on r.tenant_id = g.tenant_id and r.role_code = 'COUNSELOR'
where g.group_code in ('DEFAULT_COUNSELING', 'CAMPUS_COUNSELING', 'ENTERPRISE_WELLNESS')
on conflict (group_id, role_id) do nothing;

insert into sys_group_role (group_id, role_id)
select g.id, r.id
from sys_group g
join sys_role r on r.tenant_id = g.tenant_id and r.role_code = 'ASSESSMENT_ADMIN'
where g.group_code in ('DEFAULT_ASSESSMENT', 'CAMPUS_ASSESSMENT', 'ENTERPRISE_ASSESSMENT')
on conflict (group_id, role_id) do nothing;

insert into sys_group_role (group_id, role_id)
select g.id, r.id
from sys_group g
join sys_role r on r.tenant_id = g.tenant_id and r.role_code = 'ORG_MANAGER'
where g.group_code in ('DEFAULT_ORG', 'CAMPUS_ORG', 'ENTERPRISE_HR')
on conflict (group_id, role_id) do nothing;

insert into sys_group_role (group_id, role_id)
select g.id, r.id
from sys_group g
join sys_role r on r.tenant_id = g.tenant_id and r.role_code in ('SYS_ADMIN', 'ADMIN', 'SUPER_ADMIN')
where g.group_code = 'DEFAULT_SYSTEM'
on conflict (group_id, role_id) do nothing;

-- Users
insert into sys_user (
    username,
    display_name,
    email,
    mobile,
    status,
    group_id,
    tenant_id,
    register_source,
    password_version,
    failed_login_attempts,
    deleted
)
select
    'sysadmin',
    'System Administrator',
    'sysadmin@example.local',
    '13800000000',
    1,
    g.id,
    t.id,
    'BOOTSTRAP',
    1,
    0,
    0
from sys_tenant t
join sys_group g on g.group_code = 'DEFAULT_SYSTEM'
where t.tenant_code = 'DEFAULT'
on conflict (username) do update
set display_name = excluded.display_name,
    email = excluded.email,
    mobile = excluded.mobile,
    status = excluded.status,
    group_id = excluded.group_id,
    tenant_id = excluded.tenant_id,
    register_source = excluded.register_source,
    password_version = excluded.password_version,
    failed_login_attempts = 0,
    deleted = 0,
    updated_at = current_timestamp;

insert into sys_user (
    username,
    display_name,
    email,
    mobile,
    status,
    group_id,
    tenant_id,
    register_source,
    password_version,
    failed_login_attempts,
    deleted
)
select u.username, u.display_name, u.email, u.mobile, 1, g.id, t.id, 'SEED', 1, 0, 0
from (
    values
        ('org_manager', 'Default Org Manager', 'org_manager@example.local', '13800000001', 'DEFAULT', 'DEFAULT_ORG'),
        ('assessor', 'Default Assessment Admin', 'assessor@example.local', '13800000002', 'DEFAULT', 'DEFAULT_ASSESSMENT'),
        ('counselor', 'Default Counselor', 'counselor@example.local', '13800000003', 'DEFAULT', 'DEFAULT_COUNSELING'),
        ('respondent', 'Default Respondent', 'respondent@example.local', '13800000004', 'DEFAULT', 'DEFAULT_GENERAL'),
        ('campus_manager', 'Campus Org Manager', 'campus_manager@example.local', '13800000011', 'CAMPUS_DEMO', 'CAMPUS_ORG'),
        ('campus_assessor', 'Campus Assessment Admin', 'campus_assessor@example.local', '13800000012', 'CAMPUS_DEMO', 'CAMPUS_ASSESSMENT'),
        ('campus_counselor', 'Campus Counselor', 'campus_counselor@example.local', '13800000013', 'CAMPUS_DEMO', 'CAMPUS_COUNSELING'),
        ('campus_student', 'Campus Student', 'campus_student@example.local', '13800000014', 'CAMPUS_DEMO', 'CAMPUS_CLASS_2026_A'),
        ('enterprise_manager', 'Enterprise HR Manager', 'enterprise_manager@example.local', '13800000021', 'ENTERPRISE_DEMO', 'ENTERPRISE_HR'),
        ('enterprise_assessor', 'Enterprise Assessment Admin', 'enterprise_assessor@example.local', '13800000022', 'ENTERPRISE_DEMO', 'ENTERPRISE_ASSESSMENT'),
        ('enterprise_counselor', 'Enterprise Wellness Counselor', 'enterprise_counselor@example.local', '13800000023', 'ENTERPRISE_DEMO', 'ENTERPRISE_WELLNESS'),
        ('enterprise_staff', 'Enterprise Staff User', 'enterprise_staff@example.local', '13800000024', 'ENTERPRISE_DEMO', 'ENTERPRISE_STAFF')
) as u(username, display_name, email, mobile, tenant_code, group_code)
join sys_tenant t on t.tenant_code = u.tenant_code
join sys_group g on g.group_code = u.group_code
on conflict (username) do update
set display_name = excluded.display_name,
    email = excluded.email,
    mobile = excluded.mobile,
    status = excluded.status,
    group_id = excluded.group_id,
    tenant_id = excluded.tenant_id,
    register_source = excluded.register_source,
    password_version = excluded.password_version,
    failed_login_attempts = 0,
    deleted = 0,
    updated_at = current_timestamp;

-- Password identities
insert into sys_auth (user_id, identity_type, principal_key, credential_hash, metadata_json, enabled)
select u.id, 'PASSWORD', u.username, '{noop}ChangeMe123', '{}'::jsonb, 1
from sys_user u
where u.username in (
    'sysadmin',
    'org_manager',
    'assessor',
    'counselor',
    'respondent',
    'campus_manager',
    'campus_assessor',
    'campus_counselor',
    'campus_student',
    'enterprise_manager',
    'enterprise_assessor',
    'enterprise_counselor',
    'enterprise_staff'
)
on conflict (identity_type, principal_key) do update
set user_id = excluded.user_id,
    credential_hash = excluded.credential_hash,
    metadata_json = excluded.metadata_json,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

-- Direct user role bindings for convenience in admin testing
insert into sys_user_role (user_id, role_id)
select u.id, r.id
from sys_user u
join sys_role r on r.tenant_id = u.tenant_id
where (u.username = 'sysadmin' and r.role_code in ('SYS_ADMIN', 'ADMIN', 'SUPER_ADMIN'))
   or (u.username in ('org_manager', 'campus_manager', 'enterprise_manager') and r.role_code = 'ORG_MANAGER')
   or (u.username in ('assessor', 'campus_assessor', 'enterprise_assessor') and r.role_code = 'ASSESSMENT_ADMIN')
   or (u.username in ('counselor', 'campus_counselor', 'enterprise_counselor') and r.role_code = 'COUNSELOR')
   or (u.username in ('respondent', 'campus_student', 'enterprise_staff') and r.role_code = 'USER')
on conflict (user_id, role_id) do nothing;

-- Sample scale
insert into psy_scale (
    scale_code,
    scale_name,
    description,
    applicable_target,
    version_no,
    version_group_id,
    current_version_flag,
    status,
    score_method,
    score_coefficient,
    anonymous_supported,
    created_by
)
select
    'STRESS_DEMO',
    'Stress Screening Demo Scale',
    'A small seed scale used for local testing of scale, task, and answer flows.',
    'STUDENT',
    'v1',
    10001,
    true,
    'PUBLISHED',
    'SIMPLE_SUM',
    1.0,
    false,
    u.id
from sys_user u
where u.username = 'sysadmin'
on conflict (scale_code, version_no) do update
set scale_name = excluded.scale_name,
    description = excluded.description,
    applicable_target = excluded.applicable_target,
    version_group_id = excluded.version_group_id,
    current_version_flag = excluded.current_version_flag,
    status = excluded.status,
    score_method = excluded.score_method,
    score_coefficient = excluded.score_coefficient,
    anonymous_supported = excluded.anonymous_supported,
    created_by = excluded.created_by,
    updated_at = current_timestamp;

insert into psy_scale_dimension (scale_id, dimension_code, dimension_name, description, sort_no)
select s.id, d.dimension_code, d.dimension_name, d.description, d.sort_no
from psy_scale s
cross join (
    values
        ('EMOTION', 'Emotion Stability', 'Tracks recent emotional burden and low mood signals.', 1),
        ('PRESSURE', 'Pressure Load', 'Tracks study or work pressure intensity.', 2),
        ('RECOVERY', 'Recovery State', 'Tracks sleep and daily recovery quality.', 3)
) as d(dimension_code, dimension_name, description, sort_no)
where s.scale_code = 'STRESS_DEMO'
  and s.version_no = 'v1'
on conflict (scale_id, dimension_code) do update
set dimension_name = excluded.dimension_name,
    description = excluded.description,
    sort_no = excluded.sort_no,
    updated_at = current_timestamp;

insert into psy_scale_question (
    scale_id,
    dimension_id,
    question_no,
    question_title,
    question_type,
    required_flag,
    reverse_score_flag,
    weight_value,
    sort_no
)
select
    s.id,
    d.id,
    q.question_no,
    q.question_title,
    'SINGLE_CHOICE',
    true,
    false,
    1.00,
    q.sort_no
from psy_scale s
join psy_scale_dimension d on d.scale_id = s.id
join (
    values
        ('EMOTION', 1, 'Over the last two weeks, how often have you felt emotionally overwhelmed?', 1),
        ('PRESSURE', 2, 'Over the last two weeks, how often have you felt strong study or work pressure?', 2),
        ('RECOVERY', 3, 'Over the last two weeks, how often have you had trouble falling asleep or recovering?', 3)
) as q(dimension_code, question_no, question_title, sort_no) on q.dimension_code = d.dimension_code
where s.scale_code = 'STRESS_DEMO'
  and s.version_no = 'v1'
on conflict (scale_id, question_no) do update
set dimension_id = excluded.dimension_id,
    question_title = excluded.question_title,
    question_type = excluded.question_type,
    required_flag = excluded.required_flag,
    reverse_score_flag = excluded.reverse_score_flag,
    weight_value = excluded.weight_value,
    sort_no = excluded.sort_no,
    updated_at = current_timestamp;

insert into psy_scale_option (question_id, option_code, option_label, score_value, exclusive_flag, sort_no)
select
    q.id,
    o.option_code,
    o.option_label,
    o.score_value,
    false,
    o.sort_no
from psy_scale s
join psy_scale_question q on q.scale_id = s.id
join (
    values
        (1, 'A', 'Never', 1.00, 1),
        (1, 'B', 'Sometimes', 2.00, 2),
        (1, 'C', 'Often', 3.00, 3),
        (1, 'D', 'Almost always', 4.00, 4),
        (2, 'A', 'Never', 1.00, 1),
        (2, 'B', 'Sometimes', 2.00, 2),
        (2, 'C', 'Often', 3.00, 3),
        (2, 'D', 'Almost always', 4.00, 4),
        (3, 'A', 'Never', 1.00, 1),
        (3, 'B', 'Sometimes', 2.00, 2),
        (3, 'C', 'Often', 3.00, 3),
        (3, 'D', 'Almost always', 4.00, 4)
) as o(question_no, option_code, option_label, score_value, sort_no) on o.question_no = q.question_no
where s.scale_code = 'STRESS_DEMO'
  and s.version_no = 'v1'
on conflict (question_id, option_code) do update
set option_label = excluded.option_label,
    score_value = excluded.score_value,
    exclusive_flag = excluded.exclusive_flag,
    sort_no = excluded.sort_no,
    updated_at = current_timestamp;

delete from psy_scale_result_rule
where scale_id = (
    select id from psy_scale where scale_code = 'STRESS_DEMO' and version_no = 'v1'
);

insert into psy_scale_result_rule (
    scale_id,
    risk_level,
    score_min,
    score_max,
    score_source,
    result_title,
    result_description,
    suggestion_text
)
select
    s.id,
    r.risk_level,
    r.score_min,
    r.score_max,
    'RAW_SCORE',
    r.result_title,
    r.result_description,
    r.suggestion_text
from psy_scale s
cross join (
    values
        ('LOW', 3.00, 5.99, 'Stable Range', 'Current emotional and pressure signals look stable.', 'Maintain routines, rest, and moderate exercise.'),
        ('MEDIUM', 6.00, 8.99, 'Attention Needed', 'Some stress and recovery signals need attention.', 'Review schedule pressure and consider talking with a counselor if the trend continues.'),
        ('HIGH', 9.00, 12.00, 'High Risk Signal', 'Strong stress or sleep burden signals were detected.', 'Arrange a follow-up review and counselor contact as soon as possible.')
) as r(risk_level, score_min, score_max, result_title, result_description, suggestion_text)
where s.scale_code = 'STRESS_DEMO'
  and s.version_no = 'v1';

delete from psy_scale_high_risk_rule
where scale_id = (
    select id from psy_scale where scale_code = 'STRESS_DEMO' and version_no = 'v1'
);

insert into psy_scale_high_risk_rule (
    scale_id,
    rule_code,
    question_id,
    option_id,
    warning_level,
    result_title,
    result_description,
    suggestion_text,
    sort_no
)
select
    s.id,
    'RECOVERY_SLEEP_ALERT',
    q.id,
    o.id,
    'HIGH',
    'Sleep Risk Signal',
    'The recovery question selected the highest frequency option.',
    'Recommend counselor follow-up and a short-term sleep support plan.',
    1
from psy_scale s
join psy_scale_question q on q.scale_id = s.id and q.question_no = 3
join psy_scale_option o on o.question_id = q.id and o.option_code = 'D'
where s.scale_code = 'STRESS_DEMO'
  and s.version_no = 'v1';

-- Sample counselor schedules
insert into psy_counselor_schedule (
    counselor_user_id,
    schedule_date,
    start_time,
    end_time,
    quota_count,
    status,
    created_at
)
select
    u.id,
    s.schedule_date,
    s.start_time,
    s.end_time,
    s.quota_count,
    'AVAILABLE',
    current_timestamp
from sys_user u
join (
    values
        ('counselor', current_date + 1, (current_date + 1)::timestamp + time '09:00', (current_date + 1)::timestamp + time '10:00', 2),
        ('counselor', current_date + 1, (current_date + 1)::timestamp + time '10:30', (current_date + 1)::timestamp + time '11:30', 2),
        ('counselor', current_date + 2, (current_date + 2)::timestamp + time '14:00', (current_date + 2)::timestamp + time '15:00', 1),
        ('campus_counselor', current_date + 1, (current_date + 1)::timestamp + time '13:30', (current_date + 1)::timestamp + time '14:30', 3),
        ('campus_counselor', current_date + 2, (current_date + 2)::timestamp + time '09:30', (current_date + 2)::timestamp + time '10:30', 2),
        ('campus_counselor', current_date + 3, (current_date + 3)::timestamp + time '15:00', (current_date + 3)::timestamp + time '16:00', 2),
        ('enterprise_counselor', current_date + 1, (current_date + 1)::timestamp + time '16:00', (current_date + 1)::timestamp + time '17:00', 2),
        ('enterprise_counselor', current_date + 4, (current_date + 4)::timestamp + time '10:00', (current_date + 4)::timestamp + time '11:00', 2)
) as s(username, schedule_date, start_time, end_time, quota_count) on s.username = u.username
where not exists (
    select 1
    from psy_counselor_schedule existing
    where existing.counselor_user_id = u.id
      and existing.schedule_date = s.schedule_date
      and existing.start_time = s.start_time
      and existing.end_time = s.end_time
);

-- Sample appointments and counseling records
delete from psy_counseling_record
where appointment_id in (
    select id
    from psy_appointment_record
    where remark like '[seed-appointment]%'
);

delete from psy_appointment_record
where remark like '[seed-appointment]%';

insert into psy_appointment_record (
    user_id,
    counselor_user_id,
    warning_id,
    schedule_id,
    appointment_status,
    source_type,
    remark,
    created_at,
    updated_at
)
select
    patient.id,
    counselor.id,
    null,
    schedule.id,
    seed.appointment_status,
    seed.source_type,
    seed.remark,
    seed.created_at,
    seed.updated_at
from (
    values
        ('respondent', 'counselor', current_date + 1, (current_date + 1)::timestamp + time '09:00', (current_date + 1)::timestamp + time '10:00', 'CREATED', 'USER', '[seed-appointment] respondent-upcoming', current_timestamp - interval '6 hour', current_timestamp - interval '6 hour'),
        ('respondent', 'counselor', current_date + 2, (current_date + 2)::timestamp + time '14:00', (current_date + 2)::timestamp + time '15:00', 'CANCELLED', 'USER', '[seed-appointment] respondent-cancelled', current_timestamp - interval '2 day', current_timestamp - interval '1 day'),
        ('campus_student', 'campus_counselor', current_date + 1, (current_date + 1)::timestamp + time '13:30', (current_date + 1)::timestamp + time '14:30', 'CREATED', 'ADMIN', '[seed-appointment] campus-upcoming', current_timestamp - interval '10 hour', current_timestamp - interval '10 hour'),
        ('campus_student', 'campus_counselor', current_date + 2, (current_date + 2)::timestamp + time '09:30', (current_date + 2)::timestamp + time '10:30', 'COMPLETED', 'USER', '[seed-appointment] campus-completed', current_timestamp - interval '3 day', current_timestamp - interval '2 day'),
        ('enterprise_staff', 'enterprise_counselor', current_date + 1, (current_date + 1)::timestamp + time '16:00', (current_date + 1)::timestamp + time '17:00', 'COMPLETED', 'ADMIN', '[seed-appointment] enterprise-completed', current_timestamp - interval '4 day', current_timestamp - interval '3 day')
) as seed(patient_username, counselor_username, schedule_date, start_time, end_time, appointment_status, source_type, remark, created_at, updated_at)
join sys_user patient on patient.username = seed.patient_username
join sys_user counselor on counselor.username = seed.counselor_username
join psy_counselor_schedule schedule on schedule.counselor_user_id = counselor.id
    and schedule.schedule_date = seed.schedule_date
    and schedule.start_time = seed.start_time
    and schedule.end_time = seed.end_time;

insert into psy_counseling_record (
    appointment_id,
    counselor_user_id,
    summary_text,
    suggestion_text,
    need_retest_flag,
    need_transfer_flag,
    created_at,
    updated_at
)
select
    appointment.id,
    counselor.id,
    record.summary_text,
    record.suggestion_text,
    record.need_retest_flag,
    record.need_transfer_flag,
    record.created_at,
    record.updated_at
from (
    values
        ('[seed-appointment] campus-completed', 'campus_counselor', '已完成一次面对面沟通，当前主要压力来自近期学业与睡眠波动。', '建议继续观察两周，保持规律作息，如睡眠持续恶化可再次预约。', false, false, current_timestamp - interval '2 day', current_timestamp - interval '2 day'),
        ('[seed-appointment] enterprise-completed', 'enterprise_counselor', '已完成一次员工关怀沟通，近期工作负荷较高并伴有持续疲惫感。', '建议安排短期减压计划，并在下周进行一次复盘沟通。', true, false, current_timestamp - interval '3 day', current_timestamp - interval '3 day')
) as record(appointment_remark, counselor_username, summary_text, suggestion_text, need_retest_flag, need_transfer_flag, created_at, updated_at)
join psy_appointment_record appointment on appointment.remark = record.appointment_remark
join sys_user counselor on counselor.username = record.counselor_username;

delete from psy_notification_delivery
where notification_id in (
    select id
    from psy_notification
    where title like '[seed-notification]%'
);

delete from psy_notification
where title like '[seed-notification]%';

insert into psy_notification (
    notification_type,
    title,
    content,
    biz_type,
    biz_id,
    target_path,
    target_type,
    target_id,
    deep_link,
    payload_json,
    created_at
)
select
    seed.notification_type,
    seed.title,
    seed.content,
    seed.biz_type,
    appointment.id,
    '/appointments',
    seed.biz_type,
    appointment.id,
    '/appointments',
    seed.payload_json,
    seed.created_at
from (
    values
        ('APPOINTMENT_CREATED', '[seed-notification] respondent-booked', '您已成功预约咨询，请按时到场。', 'APPOINTMENT', '[seed-appointment] respondent-upcoming', '{"seed":"appointment","kind":"booked"}', current_timestamp - interval '5 hour'),
        ('APPOINTMENT_CANCELLED', '[seed-notification] respondent-cancelled', '您的一条预约已取消，可重新选择咨询时段。', 'APPOINTMENT', '[seed-appointment] respondent-cancelled', '{"seed":"appointment","kind":"cancelled"}', current_timestamp - interval '1 day'),
        ('APPOINTMENT_CREATED', '[seed-notification] campus-booked', '已为您保留咨询时段，请提前准备需要沟通的问题。', 'APPOINTMENT', '[seed-appointment] campus-upcoming', '{"seed":"appointment","kind":"booked"}', current_timestamp - interval '8 hour'),
        ('APPOINTMENT_COMPLETED', '[seed-notification] campus-completed', '本次咨询已完成，后续建议可在预约页查看。', 'APPOINTMENT', '[seed-appointment] campus-completed', '{"seed":"appointment","kind":"completed"}', current_timestamp - interval '36 hour'),
        ('APPOINTMENT_COMPLETED', '[seed-notification] enterprise-completed', '本次咨询已完成，如需复盘可再次预约。', 'APPOINTMENT', '[seed-appointment] enterprise-completed', '{"seed":"appointment","kind":"completed"}', current_timestamp - interval '60 hour'),
        ('APPOINTMENT_CREATED', '[seed-notification] counselor-new-booking', '收到新的咨询预约，请查看预约安排并做好准备。', 'APPOINTMENT', '[seed-appointment] campus-upcoming', '{"seed":"appointment","kind":"counselor-booking"}', current_timestamp - interval '7 hour')
) as seed(notification_type, title, content, biz_type, appointment_remark, payload_json, created_at)
join psy_appointment_record appointment on appointment.remark = seed.appointment_remark;

insert into psy_notification_delivery (
    notification_id,
    receiver_user_id,
    read_flag,
    read_time,
    delivery_channel,
    delivery_status,
    created_at,
    updated_at
)
select
    notification.id,
    receiver.id,
    seed.read_flag,
    case when seed.read_flag then seed.created_at + interval '30 minute' else null end,
    'IN_APP',
    'SENT',
    seed.created_at,
    seed.created_at
from (
    values
        ('[seed-notification] respondent-booked', 'respondent', false, current_timestamp - interval '5 hour'),
        ('[seed-notification] respondent-cancelled', 'respondent', true, current_timestamp - interval '1 day'),
        ('[seed-notification] campus-booked', 'campus_student', false, current_timestamp - interval '8 hour'),
        ('[seed-notification] campus-completed', 'campus_student', true, current_timestamp - interval '36 hour'),
        ('[seed-notification] enterprise-completed', 'enterprise_staff', false, current_timestamp - interval '60 hour'),
        ('[seed-notification] counselor-new-booking', 'campus_counselor', false, current_timestamp - interval '7 hour')
) as seed(title, receiver_username, read_flag, created_at)
join psy_notification notification on notification.title = seed.title
join sys_user receiver on receiver.username = seed.receiver_username;

-- Sample task
delete from psy_assessment_task_assignment
where task_id in (
    select id from psy_assessment_task where task_name = 'Campus Mental Health Screening (Demo)'
);

delete from psy_assessment_task
where task_name = 'Campus Mental Health Screening (Demo)';

insert into psy_assessment_task (
    task_name,
    scale_id,
    scale_version_no,
    scale_version_group_id,
    task_mode,
    anonymous_flag,
    allow_save_flag,
    allow_timeout_submit_flag,
    allow_retake_flag,
    start_time,
    end_time,
    status,
    created_by
)
select
    'Campus Mental Health Screening (Demo)',
    s.id,
    s.version_no,
    s.version_group_id,
    'SCREENING',
    false,
    true,
    false,
    false,
    current_timestamp - interval '1 day',
    current_timestamp + interval '30 day',
    'IN_PROGRESS',
    u.id
from psy_scale s
join sys_user u on u.username = 'campus_assessor'
where s.scale_code = 'STRESS_DEMO'
  and s.version_no = 'v1';

insert into psy_assessment_task_assignment (task_id, target_type, target_id, assigned_by)
select
    t.id,
    'GROUP',
    g.id,
    u.id
from psy_assessment_task t
join sys_group g on g.group_code = 'CAMPUS_CLASS_2026_A'
join sys_user u on u.username = 'campus_assessor'
where t.task_name = 'Campus Mental Health Screening (Demo)';

insert into psy_assessment_task_assignment (task_id, target_type, target_id, assigned_by)
select
    t.id,
    'USER',
    respondent.id,
    assessor.id
from psy_assessment_task t
join sys_user respondent on respondent.username = 'campus_student'
join sys_user assessor on assessor.username = 'campus_assessor'
where t.task_name = 'Campus Mental Health Screening (Demo)';

commit;
