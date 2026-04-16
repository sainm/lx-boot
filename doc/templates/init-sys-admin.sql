-- 初始化 SYS_ADMIN 管理员账号
-- 用途：
-- 1. 为新环境补一个可登录的系统管理员
-- 2. 统一以 SYS_ADMIN 作为 lx-boot 的最高管理角色
--
-- 使用前请先替换下面 4 个参数：
--   :admin_username
--   :admin_display_name
--   :admin_password_hash
--   :admin_tenant_id
--
-- 推荐密码哈希格式：
--   {bcrypt}$2a$...
-- 如果只是首次引导，也可临时使用：
--   {noop}ChangeMe123
-- 但登录后必须马上修改密码。

begin;

-- 1. 创建或更新管理员用户
insert into sys_user (
    username,
    display_name,
    status,
    tenant_id,
    register_source,
    password_version,
    failed_login_attempts,
    deleted
)
values (
    :'admin_username',
    :'admin_display_name',
    1,
    :'admin_tenant_id',
    'BOOTSTRAP',
    1,
    0,
    0
)
on conflict (username) do update
set display_name = excluded.display_name,
    status = excluded.status,
    tenant_id = excluded.tenant_id,
    register_source = excluded.register_source,
    failed_login_attempts = 0,
    deleted = 0,
    updated_at = current_timestamp;

-- 2. 创建或更新密码登录凭证
insert into sys_auth (
    user_id,
    identity_type,
    principal_key,
    credential_hash,
    metadata_json,
    enabled
)
select
    u.id,
    'PASSWORD',
    :'admin_username',
    :'admin_password_hash',
    '{}'::jsonb,
    1
from sys_user u
where u.username = :'admin_username'
on conflict (identity_type, principal_key) do update
set user_id = excluded.user_id,
    credential_hash = excluded.credential_hash,
    metadata_json = excluded.metadata_json,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

-- 3. 创建或更新 SYS_ADMIN 角色
insert into sys_role (
    tenant_id,
    role_code,
    role_name,
    data_scope,
    enabled
)
values (
    :'admin_tenant_id',
    'SYS_ADMIN',
    'System Administrator',
    'ALL',
    1
)
on conflict (tenant_id, role_code) do update
set role_name = excluded.role_name,
    data_scope = excluded.data_scope,
    enabled = excluded.enabled,
    updated_at = current_timestamp;

-- 4. 绑定管理员用户与 SYS_ADMIN 角色
insert into sys_user_role (user_id, role_id)
select
    u.id,
    r.id
from sys_user u
join sys_role r
  on r.tenant_id = :'admin_tenant_id'
 and r.role_code = 'SYS_ADMIN'
where u.username = :'admin_username'
on conflict (user_id, role_id) do nothing;

commit;
