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

delete from psy_scale_norm
where scale_id = (
    select id from psy_scale where scale_code = 'STRESS_DEMO' and version_no = 'v1'
);

insert into psy_scale_norm (
    scale_id,
    norm_code,
    norm_name,
    dimension_id,
    applicable_target,
    age_min,
    age_max,
    gender,
    org_type,
    mean_score,
    std_deviation,
    t_score_mean,
    t_score_std_deviation,
    sort_no
)
select
    scale.id,
    norm.norm_code,
    norm.norm_name,
    dimension.id,
    norm.applicable_target,
    norm.age_min,
    norm.age_max,
    norm.gender,
    norm.org_type,
    norm.mean_score,
    norm.std_deviation,
    50.0000,
    10.0000,
    norm.sort_no
from psy_scale scale
join (
    values
        ('LOCAL_DEMO_NORM', 'Local demo overall norm', null, 'STUDENT', 16, 24, null, 'CAMPUS', 6.0000, 2.0000, 1),
        ('LOCAL_DEMO_EMOTION', 'Local demo emotion norm', 'EMOTION', 'STUDENT', 16, 24, null, 'CAMPUS', 2.0000, 0.8000, 2),
        ('LOCAL_DEMO_PRESSURE', 'Local demo pressure norm', 'PRESSURE', 'STUDENT', 16, 24, null, 'CAMPUS', 2.2000, 0.9000, 3),
        ('LOCAL_DEMO_RECOVERY', 'Local demo recovery norm', 'RECOVERY', 'STUDENT', 16, 24, null, 'CAMPUS', 1.8000, 0.7000, 4)
) as norm(norm_code, norm_name, norm_dimension_code, applicable_target, age_min, age_max, gender, org_type, mean_score, std_deviation, sort_no)
    on true
left join psy_scale_dimension dimension on dimension.scale_id = scale.id
    and dimension.dimension_code = norm.norm_dimension_code
where scale.scale_code = 'STRESS_DEMO'
  and scale.version_no = 'v1';

delete from psy_scale_import_issue
where import_job_id in (
    select id from psy_scale_import_job where file_name like 'seed-%'
);

delete from psy_scale_import_job
where file_name like 'seed-%';

insert into psy_scale_import_job (
    file_name,
    file_hash,
    import_mode,
    draft_flag,
    status,
    summary_json,
    preview_json,
    error_count,
    warning_count,
    created_scale_id,
    operator_user_id,
    parsed_at,
    confirmed_at,
    finished_at,
    created_at,
    updated_at
)
select
    'seed-stress-demo-template.xlsx',
    'seed-hash-stress-demo',
    'CREATE_ONLY',
    false,
    'FINISHED',
    '{"scaleCode":"STRESS_DEMO","questionCount":3}',
    '{"sheetNames":["scale","questions","options"]}',
    0,
    1,
    scale.id,
    operator.id,
    current_timestamp - interval '3 day',
    current_timestamp - interval '3 day' + interval '10 minute',
    current_timestamp - interval '3 day' + interval '15 minute',
    current_timestamp - interval '3 day',
    current_timestamp - interval '3 day' + interval '15 minute'
from psy_scale scale
join sys_user operator on operator.username = 'assessor'
where scale.scale_code = 'STRESS_DEMO'
  and scale.version_no = 'v1';

insert into psy_scale_import_issue (
    import_job_id,
    severity,
    sheet_name,
    row_no,
    column_name,
    error_code,
    message
)
select
    job.id,
    'WARNING',
    'questions',
    4,
    'questionTitle',
    'SEED_DEMO_WARNING',
    'Seed warning: title was normalized during import.'
from psy_scale_import_job job
where job.file_name = 'seed-stress-demo-template.xlsx';

-- SCL-90 symptom checklist scale.
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
    'SCL90',
    '症状自评量表 SCL-90',
    '90 项症状自评量表，采用 1-5 五级评分，覆盖躯体化、强迫、人际关系敏感、抑郁、焦虑、敌对、恐怖、偏执、精神病性及睡眠饮食等因子。',
    'GENERAL',
    'v1',
    90001,
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
select scale.id, dim.dimension_code, dim.dimension_name, dim.description, dim.sort_no
from psy_scale scale
cross join (
    values
        ('SOM', '躯体化', '身体不适感，包括心血管、胃肠道、呼吸系统及疼痛等躯体主诉。', 1),
        ('OCD', '强迫症状', '无法摆脱的无意义思想、冲动、行为及一般认知障碍征象。', 2),
        ('INT', '人际关系敏感', '人际交往中的不自在、自卑、心神不安和消极期待。', 3),
        ('DEP', '抑郁', '苦闷、兴趣减退、动力缺乏、失望悲观及死亡/自杀观念。', 4),
        ('ANX', '焦虑', '烦躁、坐立不安、神经过敏、紧张和惊恐相关体验。', 5),
        ('HOS', '敌对', '敌对性思想、情感及行为，包括争论、冲动和摔物。', 6),
        ('PHOB', '恐怖', '广场、人群、公共场所、交通工具及社交恐惧。', 7),
        ('PAR', '偏执', '投射性思维、敌对、猜疑、关系观念及被动体验。', 8),
        ('PSY', '精神病性', '精神病性过程相关症状、行为及分裂性生活方式指征。', 9),
        ('OTHER', '睡眠及饮食', '睡眠、饮食及未归入主因子的附加项目。', 10)
) as dim(dimension_code, dimension_name, description, sort_no)
where scale.scale_code = 'SCL90'
  and scale.version_no = 'v1'
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
    scale.id,
    dimension.id,
    item.question_no,
    item.question_title,
    'SINGLE_CHOICE',
    true,
    false,
    1.00,
    item.question_no
from psy_scale scale
join (
    values
        (1, 'SOM', '头痛'),
        (2, 'ANX', '神经过敏，心中不踏实'),
        (3, 'OCD', '头脑中有不必要的想法或字句盘旋'),
        (4, 'SOM', '头晕或晕倒'),
        (5, 'DEP', '对异性的兴趣减退'),
        (6, 'INT', '对旁人责备求全'),
        (7, 'PSY', '感到别人能控制您的思想'),
        (8, 'PAR', '责怪别人制造麻烦'),
        (9, 'OCD', '忘性大'),
        (10, 'OCD', '担心自己的衣饰整齐及仪态的端正'),
        (11, 'HOS', '容易烦恼和激动'),
        (12, 'SOM', '胸痛'),
        (13, 'PHOB', '害怕空旷的场所或街道'),
        (14, 'DEP', '感到自己的精力下降，活动减慢'),
        (15, 'DEP', '想结束自己的生命'),
        (16, 'PSY', '听到旁人听不到的声音'),
        (17, 'ANX', '发抖'),
        (18, 'PAR', '感到大多数人都不可信任'),
        (19, 'OTHER', '胃口不好'),
        (20, 'DEP', '容易哭泣'),
        (21, 'INT', '同异性相处时感到害羞不自在'),
        (22, 'DEP', '感到受骗，中了圈套或有人想抓住您'),
        (23, 'ANX', '无缘无故地突然感到害怕'),
        (24, 'HOS', '自己不能控制地大发脾气'),
        (25, 'PHOB', '怕单独出门'),
        (26, 'DEP', '经常责怪自己'),
        (27, 'SOM', '腰痛'),
        (28, 'OCD', '感到难以完成任务'),
        (29, 'DEP', '感到孤独'),
        (30, 'DEP', '感到苦闷'),
        (31, 'DEP', '过分担忧'),
        (32, 'DEP', '对事物不感兴趣'),
        (33, 'ANX', '感到害怕'),
        (34, 'INT', '您的感情容易受到伤害'),
        (35, 'PSY', '旁人能知道您的私下想法'),
        (36, 'INT', '感到别人不理解您、不同情您'),
        (37, 'INT', '感到人们对您不友好，不喜欢您'),
        (38, 'OCD', '做事必须做得很慢以保证做得正确'),
        (39, 'ANX', '心跳得很厉害'),
        (40, 'SOM', '恶心或胃部不舒服'),
        (41, 'INT', '感到比不上他人'),
        (42, 'SOM', '肌肉酸痛'),
        (43, 'PAR', '感到有人在监视您、谈论您'),
        (44, 'OTHER', '难以入睡'),
        (45, 'OCD', '做事必须反复检查'),
        (46, 'OCD', '难以做出决定'),
        (47, 'PHOB', '怕乘电车、公共汽车、地铁或火车'),
        (48, 'SOM', '呼吸有困难'),
        (49, 'SOM', '一阵阵发冷或发热'),
        (50, 'PHOB', '因为感到害怕而避开某些东西、场合或活动'),
        (51, 'OCD', '脑子变空了'),
        (52, 'SOM', '身体发麻或刺痛'),
        (53, 'SOM', '喉咙有梗塞感'),
        (54, 'DEP', '感到前途没有希望'),
        (55, 'OCD', '不能集中注意力'),
        (56, 'SOM', '感到身体的某一部分软弱无力'),
        (57, 'ANX', '感到紧张或容易紧张'),
        (58, 'SOM', '感到手或脚发重'),
        (59, 'OTHER', '想到死亡的事'),
        (60, 'OTHER', '吃得太多'),
        (61, 'INT', '当别人看着您或谈论您时感到不自在'),
        (62, 'PSY', '有一些不属于您自己的想法'),
        (63, 'HOS', '有想打人或伤害他人的冲动'),
        (64, 'OTHER', '醒得太早'),
        (65, 'OCD', '必须反复洗手、点数或触摸某些东西'),
        (66, 'OTHER', '睡得不稳不深'),
        (67, 'HOS', '有想摔坏或破坏东西的想法'),
        (68, 'PAR', '有一些别人没有的想法'),
        (69, 'INT', '感到对别人神经过敏'),
        (70, 'PHOB', '在商店或电影院等人多的地方感到不自在'),
        (71, 'DEP', '感到任何事情都很困难'),
        (72, 'ANX', '一阵阵恐惧或惊恐'),
        (73, 'INT', '感到公共场合吃东西很不舒服'),
        (74, 'HOS', '经常与人争论'),
        (75, 'PHOB', '单独一人时神经很紧张'),
        (76, 'PAR', '别人对您的成绩没有做出恰当的评价'),
        (77, 'PSY', '即使和别人在一起也感到孤单'),
        (78, 'ANX', '感到坐立不安，心神不定'),
        (79, 'DEP', '感到自己没有什么价值'),
        (80, 'ANX', '感到熟悉的东西变成陌生或不像是真的'),
        (81, 'HOS', '大叫或摔东西'),
        (82, 'PHOB', '害怕会在公共场合晕倒'),
        (83, 'PAR', '感到别人想占您的便宜'),
        (84, 'PSY', '为一些有关“性”的想法而很苦恼'),
        (85, 'PSY', '您认为应该因为自己的过错而受到惩罚'),
        (86, 'ANX', '感到要很快把事情做完'),
        (87, 'PSY', '感到自己的身体有严重问题'),
        (88, 'PSY', '从未感到和其他人很亲近'),
        (89, 'OTHER', '感到自己有罪'),
        (90, 'PSY', '感到自己的脑子有毛病')
) as item(question_no, dimension_code, question_title) on true
join psy_scale_dimension dimension on dimension.scale_id = scale.id
    and dimension.dimension_code = item.dimension_code
where scale.scale_code = 'SCL90'
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

insert into psy_scale_option (question_id, option_code, option_label, score_value, exclusive_flag, sort_no)
select
    question.id,
    opt.option_code,
    opt.option_label,
    opt.score_value,
    false,
    opt.sort_no
from psy_scale scale
join psy_scale_question question on question.scale_id = scale.id
cross join (
    values
        ('1', '无', 1.00, 1),
        ('2', '轻度', 2.00, 2),
        ('3', '中度', 3.00, 3),
        ('4', '偏重', 4.00, 4),
        ('5', '严重', 5.00, 5)
) as opt(option_code, option_label, score_value, sort_no)
where scale.scale_code = 'SCL90'
  and scale.version_no = 'v1'
on conflict (question_id, option_code) do update
set option_label = excluded.option_label,
    score_value = excluded.score_value,
    exclusive_flag = excluded.exclusive_flag,
    sort_no = excluded.sort_no,
    updated_at = current_timestamp;

delete from psy_scale_result_rule
where scale_id = (
    select id from psy_scale where scale_code = 'SCL90' and version_no = 'v1'
);

insert into psy_scale_result_rule (
    scale_id,
    dimension_id,
    risk_level,
    score_min,
    score_max,
    score_source,
    result_title,
    result_description,
    suggestion_text
)
select
    scale.id,
    null,
    rule.risk_level,
    rule.score_min,
    rule.score_max,
    'RAW_SCORE',
    rule.result_title,
    rule.result_description,
    rule.suggestion_text
from psy_scale scale
cross join (
    values
        ('LOW', 90.00, 159.99, '总体症状水平较低', '总分处于较低范围，当前自评症状整体不突出。', '建议保持规律作息和日常心理健康维护。'),
        ('MEDIUM', 160.00, 199.99, '存在一定症状困扰', '总分提示存在一定程度症状体验，建议关注主要升高因子。', '建议结合因子分进行访谈或复测，必要时安排咨询支持。'),
        ('HIGH', 200.00, 249.99, '症状困扰较明显', '总分处于较高范围，可能存在较明显心理健康风险。', '建议尽快由咨询师或专业人员跟进评估。'),
        ('CRITICAL', 250.00, 450.00, '症状困扰严重', '总分处于严重范围，需重点关注风险和功能受损情况。', '建议立即进行专业评估，并建立持续跟进计划。')
) as rule(risk_level, score_min, score_max, result_title, result_description, suggestion_text)
where scale.scale_code = 'SCL90'
  and scale.version_no = 'v1';

insert into psy_scale_result_rule (
    scale_id,
    dimension_id,
    risk_level,
    score_min,
    score_max,
    score_source,
    result_title,
    result_description,
    suggestion_text
)
select
    scale.id,
    dimension.id,
    case
        when dimension.dimension_code in ('DEP', 'PSY') then 'HIGH'
        else 'MEDIUM'
    end,
    3.00,
    5.00,
    'DIMENSION_SCORE',
    dimension.dimension_name || '因子升高',
    '该因子均分达到 3 分及以上，提示对应症状可能达到中等以上水平。',
    '建议结合访谈和具体条目进一步确认症状表现。'
from psy_scale scale
join psy_scale_dimension dimension on dimension.scale_id = scale.id
where scale.scale_code = 'SCL90'
  and scale.version_no = 'v1';

delete from psy_scale_high_risk_rule
where scale_id = (
    select id from psy_scale where scale_code = 'SCL90' and version_no = 'v1'
);

insert into psy_scale_high_risk_rule (
    scale_id,
    rule_code,
    question_id,
    option_id,
    score_threshold,
    warning_level,
    result_title,
    result_description,
    suggestion_text,
    sort_no
)
select
    scale.id,
    risk.rule_code,
    question.id,
    null,
    risk.score_threshold,
    risk.warning_level,
    risk.result_title,
    risk.result_description,
    risk.suggestion_text,
    risk.sort_no
from psy_scale scale
join (
    values
        ('SCL90_SELF_HARM_IDEA', 15, 4.00, 'HIGH', '自伤/自杀意念高危信号', '第 15 题达到偏重或严重，需要优先确认安全风险。', '建议立即进行危机风险评估并安排人工跟进。', 1),
        ('SCL90_HARM_OTHERS_IDEA', 63, 4.00, 'HIGH', '伤害他人冲动高危信号', '第 63 题达到偏重或严重，需要关注冲动控制和安全风险。', '建议尽快由咨询师进行风险访谈和安全计划确认。', 2)
) as risk(rule_code, question_no, score_threshold, warning_level, result_title, result_description, suggestion_text, sort_no) on true
join psy_scale_question question on question.scale_id = scale.id and question.question_no = risk.question_no
where scale.scale_code = 'SCL90'
  and scale.version_no = 'v1'
on conflict (scale_id, rule_code) do update
set question_id = excluded.question_id,
    option_id = excluded.option_id,
    score_threshold = excluded.score_threshold,
    warning_level = excluded.warning_level,
    result_title = excluded.result_title,
    result_description = excluded.result_description,
    suggestion_text = excluded.suggestion_text,
    sort_no = excluded.sort_no,
    updated_at = current_timestamp;

delete from psy_scale_norm
where scale_id = (
    select id from psy_scale where scale_code = 'SCL90' and version_no = 'v1'
);

insert into psy_scale_norm (
    scale_id,
    norm_code,
    norm_name,
    dimension_id,
    applicable_target,
    age_min,
    age_max,
    gender,
    org_type,
    mean_score,
    std_deviation,
    t_score_mean,
    t_score_std_deviation,
    sort_no
)
select
    scale.id,
    norm.norm_code,
    norm.norm_name,
    dimension.id,
    'GENERAL',
    null,
    null,
    null,
    null,
    norm.mean_score,
    norm.std_deviation,
    50.0000,
    10.0000,
    norm.sort_no
from psy_scale scale
join (
    values
        ('SCL90_TOTAL_MEAN', 'SCL-90 总均分参考', null, 1.4400, 0.4300, 1),
        ('SCL90_SOM', '躯体化常模', 'SOM', 1.3700, 0.4800, 2),
        ('SCL90_OCD', '强迫常模', 'OCD', 1.6200, 0.5800, 3),
        ('SCL90_INT', '人际关系敏感常模', 'INT', 1.6500, 0.6100, 4),
        ('SCL90_DEP', '抑郁常模', 'DEP', 1.5000, 0.5900, 5),
        ('SCL90_ANX', '焦虑常模', 'ANX', 1.3900, 0.4300, 6),
        ('SCL90_HOS', '敌对常模', 'HOS', 1.4600, 0.5500, 7),
        ('SCL90_PHOB', '恐怖常模', 'PHOB', 1.2300, 0.4100, 8),
        ('SCL90_PAR', '偏执常模', 'PAR', 1.4300, 0.5700, 9),
        ('SCL90_PSY', '精神病性常模', 'PSY', 1.2900, 0.4200, 10)
) as norm(norm_code, norm_name, dimension_code, mean_score, std_deviation, sort_no) on true
left join psy_scale_dimension dimension on dimension.scale_id = scale.id
    and dimension.dimension_code = norm.dimension_code
where scale.scale_code = 'SCL90'
  and scale.version_no = 'v1';

delete from psy_scale_import_issue
where import_job_id in (
    select id from psy_scale_import_job where file_name = 'seed-scl90-scale.xlsx'
);

delete from psy_scale_import_job
where file_name = 'seed-scl90-scale.xlsx';

insert into psy_scale_import_job (
    file_name,
    file_hash,
    import_mode,
    draft_flag,
    status,
    summary_json,
    preview_json,
    error_count,
    warning_count,
    created_scale_id,
    operator_user_id,
    parsed_at,
    confirmed_at,
    finished_at,
    created_at,
    updated_at
)
select
    'seed-scl90-scale.xlsx',
    'seed-hash-scl90-v1',
    'CREATE_ONLY',
    false,
    'FINISHED',
    '{"scaleCode":"SCL90","questionCount":90,"optionCount":5,"dimensionCount":10}',
    '{"source":"user-provided SCL-90 text","scoreRange":"90-450"}',
    0,
    0,
    scale.id,
    operator.id,
    current_timestamp - interval '2 day',
    current_timestamp - interval '2 day' + interval '15 minute',
    current_timestamp - interval '2 day' + interval '20 minute',
    current_timestamp - interval '2 day',
    current_timestamp - interval '2 day' + interval '20 minute'
from psy_scale scale
join sys_user operator on operator.username = 'assessor'
where scale.scale_code = 'SCL90'
  and scale.version_no = 'v1';

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

update psy_assessment_task task
set scale_id = s.id,
    scale_version_no = s.version_no,
    scale_version_group_id = s.version_group_id,
    task_mode = 'SCREENING',
    anonymous_flag = false,
    allow_save_flag = true,
    allow_timeout_submit_flag = false,
    allow_retake_flag = false,
    start_time = current_timestamp - interval '1 day',
    end_time = current_timestamp + interval '30 day',
    status = 'IN_PROGRESS',
    created_by = u.id,
    updated_at = current_timestamp
from psy_scale s
join sys_user u on u.username = 'campus_assessor'
where task.task_name = 'Campus Mental Health Screening (Demo)'
  and s.scale_code = 'STRESS_DEMO'
  and s.version_no = 'v1';

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
  and s.version_no = 'v1'
  and not exists (
      select 1
      from psy_assessment_task task
      where task.task_name = 'Campus Mental Health Screening (Demo)'
  );

insert into psy_assessment_task_assignment (task_id, target_type, target_id, assigned_by)
select
    t.id,
    'GROUP',
    g.id,
    u.id
from (
    select id
    from psy_assessment_task
    where task_name = 'Campus Mental Health Screening (Demo)'
    order by id
    limit 1
) t
join sys_group g on g.group_code = 'CAMPUS_CLASS_2026_A'
join sys_user u on u.username = 'campus_assessor'
where true;

insert into psy_assessment_task_assignment (task_id, target_type, target_id, assigned_by)
select
    t.id,
    'USER',
    respondent.id,
    assessor.id
from (
    select id
    from psy_assessment_task
    where task_name = 'Campus Mental Health Screening (Demo)'
    order by id
    limit 1
) t
join sys_user respondent on respondent.username = 'campus_student'
join sys_user assessor on assessor.username = 'campus_assessor'
where true;

-- Submitted answer sheets, reports, warnings, and interventions for report search testing.
delete from psy_export_job
where id like 'seed-report-export-%';

delete from psy_intervention_status_log
where intervention_id in (
    select intervention.id
    from psy_intervention_record intervention
    join psy_warning_record warning on warning.id = intervention.warning_id
    join psy_assessment_result result on result.id = warning.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.submit_token like 'seed-submit-%'
);

delete from psy_intervention_record
where warning_id in (
    select warning.id
    from psy_warning_record warning
    join psy_assessment_result result on result.id = warning.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.submit_token like 'seed-submit-%'
);

delete from psy_warning_assignment
where warning_id in (
    select warning.id
    from psy_warning_record warning
    join psy_assessment_result result on result.id = warning.result_id
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.submit_token like 'seed-submit-%'
);

delete from psy_warning_record
where result_id in (
    select result.id
    from psy_assessment_result result
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.submit_token like 'seed-submit-%'
);

delete from psy_report
where result_id in (
    select result.id
    from psy_assessment_result result
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.submit_token like 'seed-submit-%'
);

delete from psy_assessment_result_dimension
where result_id in (
    select result.id
    from psy_assessment_result result
    join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
    where sheet.submit_token like 'seed-submit-%'
);

delete from psy_assessment_result
where answer_sheet_id in (
    select id from psy_assessment_answer_sheet where submit_token like 'seed-submit-%'
);

delete from psy_assessment_answer_item
where answer_sheet_id in (
    select id from psy_assessment_answer_sheet where submit_token like 'seed-submit-%'
);

delete from psy_assessment_answer_sheet
where submit_token like 'seed-submit-%';

insert into psy_assessment_answer_sheet (
    task_id,
    scale_id,
    user_id,
    answer_status,
    version_no,
    start_time,
    submit_time,
    duration_seconds,
    submit_token,
    created_at,
    updated_at
)
select
    task.id,
    scale.id,
    respondent.id,
    'SUBMITTED',
    1,
    seed.submitted_at - interval '9 minute',
    seed.submitted_at,
    seed.duration_seconds,
    seed.submit_token,
    seed.submitted_at - interval '10 minute',
    seed.submitted_at
from (
    values
        ('respondent', 'seed-submit-respondent-low', current_timestamp - interval '5 day', 3, 360),
        ('campus_student', 'seed-submit-campus-high', current_timestamp - interval '2 day', 10, 540),
        ('enterprise_staff', 'seed-submit-enterprise-medium', current_timestamp - interval '1 day', 7, 420)
) as seed(username, submit_token, submitted_at, total_score, duration_seconds)
join sys_user respondent on respondent.username = seed.username
join (
    select id, scale_id
    from psy_assessment_task
    where task_name = 'Campus Mental Health Screening (Demo)'
    order by id
    limit 1
) task on true
join psy_scale scale on scale.id = task.scale_id;

insert into psy_assessment_answer_item (
    answer_sheet_id,
    question_id,
    option_id,
    answer_text,
    answer_value,
    score_value,
    created_at
)
select
    sheet.id,
    question.id,
    option_row.id,
    null,
    option_row.score_value,
    option_row.score_value,
    sheet.submit_time
from psy_assessment_answer_sheet sheet
join psy_scale_question question on question.scale_id = sheet.scale_id
join (
    values
        ('seed-submit-respondent-low', 1, 'A'),
        ('seed-submit-respondent-low', 2, 'A'),
        ('seed-submit-respondent-low', 3, 'A'),
        ('seed-submit-campus-high', 1, 'C'),
        ('seed-submit-campus-high', 2, 'D'),
        ('seed-submit-campus-high', 3, 'D'),
        ('seed-submit-enterprise-medium', 1, 'B'),
        ('seed-submit-enterprise-medium', 2, 'C'),
        ('seed-submit-enterprise-medium', 3, 'B')
) as answer(submit_token, question_no, option_code)
    on answer.submit_token = sheet.submit_token
   and answer.question_no = question.question_no
join psy_scale_option option_row on option_row.question_id = question.id
    and option_row.option_code = answer.option_code;

insert into psy_assessment_result (
    answer_sheet_id,
    total_score,
    risk_level,
    warning_flag,
    result_summary,
    score_source,
    standard_score,
    z_score,
    t_score,
    norm_code,
    high_risk_flag,
    high_risk_rule_code,
    scored_at,
    created_at
)
select
    sheet.id,
    seed.total_score,
    seed.risk_level,
    seed.warning_flag,
    seed.result_summary,
    'RAW_SCORE',
    seed.standard_score,
    seed.z_score,
    seed.t_score,
    'LOCAL_DEMO_NORM',
    seed.high_risk_flag,
    seed.high_risk_rule_code,
    sheet.submit_time,
    sheet.submit_time
from (
    values
        ('seed-submit-respondent-low', 3.00, 'LOW', false, 'Seed low-risk report result for respondent.', 45.0000, -0.5000, 45.0000, false, null),
        ('seed-submit-campus-high', 10.00, 'HIGH', true, 'Seed high-risk report result for campus student.', 72.0000, 2.2000, 72.0000, true, 'RECOVERY_SLEEP_ALERT'),
        ('seed-submit-enterprise-medium', 7.00, 'MEDIUM', true, 'Seed medium-risk report result for enterprise staff.', 60.0000, 1.0000, 60.0000, false, null)
) as seed(submit_token, total_score, risk_level, warning_flag, result_summary, standard_score, z_score, t_score, high_risk_flag, high_risk_rule_code)
join psy_assessment_answer_sheet sheet on sheet.submit_token = seed.submit_token;

insert into psy_assessment_result_dimension (
    result_id,
    dimension_id,
    dimension_score,
    risk_level,
    result_title,
    created_at
)
select
    result.id,
    dimension.id,
    dimension_score.dimension_score,
    dimension_score.risk_level,
    dimension_score.result_title,
    result.created_at
from psy_assessment_result result
join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
join psy_scale_dimension dimension on dimension.scale_id = sheet.scale_id
join (
    values
        ('seed-submit-respondent-low', 'EMOTION', 1.0000, 'LOW', 'Emotion stable'),
        ('seed-submit-respondent-low', 'PRESSURE', 1.0000, 'LOW', 'Pressure stable'),
        ('seed-submit-respondent-low', 'RECOVERY', 1.0000, 'LOW', 'Recovery stable'),
        ('seed-submit-campus-high', 'EMOTION', 3.0000, 'MEDIUM', 'Emotion needs attention'),
        ('seed-submit-campus-high', 'PRESSURE', 4.0000, 'HIGH', 'Pressure is high'),
        ('seed-submit-campus-high', 'RECOVERY', 3.0000, 'HIGH', 'Recovery is poor'),
        ('seed-submit-enterprise-medium', 'EMOTION', 2.0000, 'LOW', 'Emotion slightly changed'),
        ('seed-submit-enterprise-medium', 'PRESSURE', 3.0000, 'MEDIUM', 'Pressure needs attention'),
        ('seed-submit-enterprise-medium', 'RECOVERY', 2.0000, 'LOW', 'Recovery acceptable')
) as dimension_score(submit_token, dimension_code, dimension_score, risk_level, result_title)
    on dimension_score.submit_token = sheet.submit_token
   and dimension_score.dimension_code = dimension.dimension_code;

insert into psy_report (
    result_id,
    report_type,
    author_user_id,
    report_title,
    report_content,
    version_no,
    created_at,
    updated_at
)
select
    result.id,
    'SYSTEM',
    author.id,
    seed.report_title,
    seed.report_content,
    1,
    result.created_at + interval '1 minute',
    result.created_at + interval '1 minute'
from (
    values
        ('seed-submit-respondent-low', 'Seed Report - Respondent Low', 'Overall risk is low. Continue regular self-care and follow future assessments.'),
        ('seed-submit-campus-high', 'Seed Report - Campus High', 'High-risk signals were detected. Counselor follow-up is recommended as soon as possible.'),
        ('seed-submit-enterprise-medium', 'Seed Report - Enterprise Medium', 'Medium-risk pressure signals were detected. Review workload and consider follow-up support.')
) as seed(submit_token, report_title, report_content)
join psy_assessment_answer_sheet sheet on sheet.submit_token = seed.submit_token
join psy_assessment_result result on result.answer_sheet_id = sheet.id
left join sys_user author on author.username = 'sysadmin';

insert into psy_warning_record (
    result_id,
    warning_level,
    warning_priority,
    warning_reason,
    status,
    deadline_time,
    first_response_time,
    escalated_at,
    last_reminded_at,
    escalation_count,
    created_at,
    updated_at
)
select
    result.id,
    seed.warning_level,
    seed.warning_priority,
    seed.warning_reason,
    seed.status,
    current_timestamp + seed.deadline_interval,
    seed.first_response_time,
    null,
    null,
    0,
    result.created_at + interval '2 minute',
    result.created_at + interval '2 minute'
from (
    values
        ('seed-submit-campus-high', 'HIGH', 'P1', 'Seed high-risk assessment warning.', 'ASSIGNED', interval '24 hour', null::timestamp),
        ('seed-submit-enterprise-medium', 'MEDIUM', 'P2', 'Seed medium-risk pressure warning.', 'PROCESSING', interval '72 hour', current_timestamp - interval '12 hour')
) as seed(submit_token, warning_level, warning_priority, warning_reason, status, deadline_interval, first_response_time)
join psy_assessment_answer_sheet sheet on sheet.submit_token = seed.submit_token
join psy_assessment_result result on result.answer_sheet_id = sheet.id;

insert into psy_warning_assignment (
    warning_id,
    assignee_user_id,
    assigned_by,
    assigned_at,
    claim_time
)
select
    warning.id,
    counselor.id,
    assigner.id,
    warning.created_at + interval '5 minute',
    seed.claim_time
from (
    values
        ('seed-submit-campus-high', 'campus_counselor', 'campus_assessor', null::timestamp),
        ('seed-submit-enterprise-medium', 'enterprise_counselor', 'enterprise_assessor', current_timestamp - interval '12 hour')
) as seed(submit_token, counselor_username, assigner_username, claim_time)
join psy_assessment_answer_sheet sheet on sheet.submit_token = seed.submit_token
join psy_assessment_result result on result.answer_sheet_id = sheet.id
join psy_warning_record warning on warning.result_id = result.id
join sys_user counselor on counselor.username = seed.counselor_username
join sys_user assigner on assigner.username = seed.assigner_username;

insert into psy_intervention_record (
    warning_id,
    counselor_user_id,
    current_status,
    plan_text,
    close_summary,
    need_retest_flag,
    retest_task_id,
    created_at,
    updated_at
)
select
    warning.id,
    counselor.id,
    'IN_PROGRESS',
    'Seed intervention plan: schedule follow-up conversation and monitor stress changes for one week.',
    null,
    true,
    task.id,
    current_timestamp - interval '11 hour',
    current_timestamp - interval '11 hour'
from psy_warning_record warning
join psy_assessment_result result on result.id = warning.result_id
join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
join psy_assessment_task task on task.id = sheet.task_id
join sys_user counselor on counselor.username = 'enterprise_counselor'
where sheet.submit_token = 'seed-submit-enterprise-medium';

insert into psy_intervention_status_log (
    intervention_id,
    from_status,
    to_status,
    remark,
    changed_by,
    changed_at
)
select
    intervention.id,
    null,
    'IN_PROGRESS',
    'Seed intervention opened after warning claim.',
    intervention.counselor_user_id,
    intervention.created_at
from psy_intervention_record intervention
join psy_warning_record warning on warning.id = intervention.warning_id
join psy_assessment_result result on result.id = warning.result_id
join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
where sheet.submit_token = 'seed-submit-enterprise-medium';

insert into psy_user_device (
    user_id,
    device_type,
    device_id,
    push_token,
    app_version,
    active_flag,
    last_active_at,
    created_at,
    updated_at
)
select
    user_row.id,
    seed.device_type,
    seed.device_id,
    seed.push_token,
    '1.0.0-local',
    true,
    current_timestamp - seed.last_active_age,
    current_timestamp - interval '7 day',
    current_timestamp - seed.last_active_age
from (
    values
        ('respondent', 'ANDROID', 'seed-device-respondent', 'seed-push-token-respondent', interval '2 hour'),
        ('campus_student', 'ANDROID', 'seed-device-campus', 'seed-push-token-campus', interval '1 hour'),
        ('enterprise_staff', 'IOS', 'seed-device-enterprise', 'seed-push-token-enterprise', interval '5 hour')
) as seed(username, device_type, device_id, push_token, last_active_age)
join sys_user user_row on user_row.username = seed.username
on conflict (user_id, device_id) do update
set device_type = excluded.device_type,
    push_token = excluded.push_token,
    app_version = excluded.app_version,
    active_flag = excluded.active_flag,
    last_active_at = excluded.last_active_at,
    updated_at = excluded.updated_at;

insert into psy_notification_policy (
    notification_type,
    in_app_enabled,
    push_enabled,
    cooldown_minutes,
    updated_at
)
values
    ('REPORT_GENERATED', true, true, 5, current_timestamp),
    ('WARNING_ASSIGNED', true, true, 10, current_timestamp),
    ('INTERVENTION_UPDATED', true, false, 10, current_timestamp)
on conflict (notification_type) do update
set in_app_enabled = excluded.in_app_enabled,
    push_enabled = excluded.push_enabled,
    cooldown_minutes = excluded.cooldown_minutes,
    updated_at = excluded.updated_at;

insert into psy_export_job (
    id,
    status,
    report_id,
    result_id,
    export_format,
    locale_tag,
    desensitized_flag,
    file_name,
    content_type,
    file_size,
    file_bytes,
    created_at,
    completed_at,
    updated_at
)
select
    'seed-report-export-' || report.id,
    'COMPLETED',
    report.id,
    report.result_id,
    'PDF',
    'zh-CN',
    true,
    'seed-report-' || report.id || '.pdf',
    'application/pdf',
    23,
    decode('255044462d736565642d7265706f7274', 'hex'),
    report.created_at + interval '10 minute',
    report.created_at + interval '11 minute',
    report.created_at + interval '11 minute'
from psy_report report
join psy_assessment_result result on result.id = report.result_id
join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
where sheet.submit_token like 'seed-submit-%'
on conflict (id) do update
set status = excluded.status,
    report_id = excluded.report_id,
    result_id = excluded.result_id,
    export_format = excluded.export_format,
    locale_tag = excluded.locale_tag,
    desensitized_flag = excluded.desensitized_flag,
    file_name = excluded.file_name,
    content_type = excluded.content_type,
    file_size = excluded.file_size,
    file_bytes = excluded.file_bytes,
    completed_at = excluded.completed_at,
    updated_at = excluded.updated_at;

commit;
