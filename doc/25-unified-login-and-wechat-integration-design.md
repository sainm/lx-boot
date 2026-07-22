# 统一登录、外部登录与微信公众号接入设计

## 1. 文档说明

本文档描述心理测评与预警系统在登录与身份接入方面的三项能力扩展设计，作为后续开发的直接依据：

1. **统一登录（SSO）**：对接学校统一身份认证，支持 CAS 与 OIDC 双协议，校领导等已有账号用户可直接登录，无需重新注册。
2. **外部登录服务**：面向尚未入境、无法使用微信的留学生，提供公网可访问的邮箱注册通道，注册后由管理员审核放行。
3. **微信公众号接入**：以学校微信公众号为学生入口，支持网页授权登录、模板消息通知与公众号自定义菜单配置。

### 1.1 技术基线

- 认证与权限统一由外部依赖 `org.sainm:auth-spring-boot-starter`（源码仓库 `auth-starter`）提供。
- 本系统（`lx-boot`）通过配置与 SPI Bean 覆盖的方式接入，尽量不侵入 `auth-starter` 的业务代码。
- 后端：Kotlin + Spring Boot + Spring JDBC + PostgreSQL + Redis。
- 前端：`admin-web`（管理端与受评者侧合一的 React 应用）。
- 移动端：`android-app`（受评者 Android 应用）。

### 1.2 现状结论（改造起点）

| 能力 | 现状 |
| --- | --- |
| 密码登录 | 三端均已支持（`/auth/login/password`） |
| 自助注册 | `POST /auth/register`，`email`/`mobile` 为可选纯文本，**无验证码、无邮件验证、无审核** |
| 社交登录类型 | `auth-starter` 支持 `PASSWORD` / `GOOGLE` / `WECHAT` 三种 |
| 微信登录 | provider（`WechatCodeSocialAuthProvider`，`sns/oauth2` 网页授权）已存在，但 `social.wechat.enabled=false` 未启用；无公众号服务端、无消息回调、无菜单 |
| 统一登录（CAS/OIDC/SAML/LDAP） | **完全没有**，roadmap 仅有规划 |
| 身份映射机制 | 所有身份经 `sys_auth(identity_type, principal_key)` 映射到 `sys_user`，扩展点干净 |
| 前端登录页 | `LoginPage.tsx` 仅账号密码 + 自助注册，无社交 / SSO / 扫码入口 |

## 2. 需求一：统一登录（CAS + OIDC）

### 2.1 目标

- 校领导及其他已在学校身份中心存在的用户，用学校统一账号登录本系统，不重复注册。
- 同时支持 **CAS**（票据校验）与 **OIDC**（OAuth2 授权码 + ID Token）两种协议，按学校实际提供的能力择一或并存。
- 首次登录时，将学校身份**映射到系统内已存在的用户**（按学工号 / 邮箱匹配），而非默认新建游客账号，以保留其"校领导"等既有角色与数据权限。

### 2.2 协议流程

**OIDC（授权码模式）：**

1. 前端点击"统一身份登录" → 后端 `GET /auth/sso/oidc/authorize` 生成 `state`+`nonce` 并 302 跳转到学校 IdP 授权端点。
2. 用户在学校 IdP 完成登录 → IdP 回调 `GET /auth/sso/oidc/callback?code=...&state=...`。
3. 后端用 `code` 向 IdP `token_endpoint` 换取 `id_token`+`access_token`，校验 `id_token` 签名（JWKS）、`iss`、`aud`、`nonce`、过期时间。
4. 从 `id_token` / `userinfo` 取出学工号（`sub` 或自定义 claim）、姓名、邮箱，产出 `SocialIdentity(provider="OIDC", externalId=学工号, ...)`。

**CAS：**

1. 前端点击 → 后端 302 跳转到 `${cas-server}/login?service=${回调地址}`。
2. CAS 登录成功回调 `service` 地址并带 `ticket`。
3. 后端向 `${cas-server}/serviceValidate?ticket=...&service=...` 校验票据，解析返回的 XML/JSON 得到学工号与属性。
4. 产出 `SocialIdentity(provider="CAS", externalId=学工号, ...)`。

### 2.3 auth-starter 改造

- 新增模块 `auth-sso`（或分 `auth-sso-oidc`、`auth-sso-cas`），实现 `SocialAuthProvider` SPI：
  - `OidcSocialAuthProvider`：`provider="OIDC"`，封装授权跳转 URL 构造、`code` 换 token、`id_token` 校验、claim 映射。
  - `CasSocialAuthProvider`：`provider="CAS"`，封装 `serviceValidate` 调用与属性解析。
- `SocialAuthProvider.resolve(authCode)` 现有签名接收单一 `authCode`。SSO 回调需要 `state`、`nonce`、`service` 等上下文，需扩展 SPI 契约（新增可携带上下文的 `resolve` 重载，或引入 `SsoAuthProvider` 子接口），这是本需求对 `auth-starter` 核心契约的主要改动点。
- `AuthController` 社交端点当前为**每个 provider 硬编码**（`/auth/social/google`、`/auth/social/wechat`）。需新增：
  - `GET /auth/sso/{provider}/authorize`：生成跳转 URL（`state`/`nonce` 存 Redis，防 CSRF/重放）。
  - `GET /auth/sso/{provider}/callback`：处理回调，完成登录，签发本系统 token（或 302 回前端带一次性 code）。
- 新增配置项：
  ```yaml
  auth-module:
    authentication:
      enabled-types: [PASSWORD, WECHAT, OIDC, CAS]
    sso:
      oidc:
        enabled: false
        issuer: https://id.school.edu.cn
        client-id: ...
        client-secret: ...
        redirect-uri: https://psy.school.edu.cn/auth/sso/oidc/callback
        scopes: [openid, profile, email]
        username-claim: sub
      cas:
        enabled: false
        server-url: https://cas.school.edu.cn/cas
        service-url: https://psy.school.edu.cn/auth/sso/cas/callback
  ```

### 2.4 账号映射（本系统 lx-boot 侧定制）

默认的 `JdbcSocialAccountService.findOrCreate` 在找不到映射时会**新建游客账号**并只赋予 `USER` 角色，这不符合"校领导用已有账号"的诉求。需在 `lx-boot` 定义自定义 `SocialAccountService` Bean（覆盖默认）：

1. 先按 `sys_auth(identity_type, principal_key=学工号)` 精确查映射，命中即返回。
2. 未命中时，**不直接新建**，而是按 `SocialIdentity` 的邮箱 / 学工号去 `sys_user` 匹配已存在用户；命中则**补建 `sys_auth` 映射行**并返回该用户（保留其角色）。
3. 仍未命中：按策略决定是拒绝登录（提示"请联系管理员开通"）还是新建待审核账号。校领导场景建议**预先在系统内建好账号并预置学工号**，避免自动新建。

### 2.5 角色与数据权限

- 当前角色：`USER` / `COUNSELOR` / `ASSESSMENT_ADMIN` / `ORG_MANAGER` / `SYS_ADMIN`，**无"校领导"角色**。
- 新增角色 `SCHOOL_LEADER`（校领导），数据范围建议 `ALL` 只读：可查看全校群体统计、预警概览、报表，但不涉及个体隐私明细与高危操作。需在 `data-psy.sql` 补种子数据，并在 `02-role-and-permission-design.md` 补充定义。
- 前端菜单与路由需按新角色控制可见项（`admin-web/src/auth/roles.ts`）。

### 2.6 前端改造（admin-web）

- `LoginPage.tsx`：增加"统一身份登录"按钮（可配置显示，读取后端 `/auth/register/options` 类似的能力开关接口）。
- 新增 SSO 回调处理页 `/auth/sso/callback`：接收后端回跳的一次性 code / token，写入登录态并跳转首页。
- `authHttp` 无硬编码 host，SSO 跳转的绝对回调地址需由部署层配置注入。

## 3. 需求二：外部登录服务（留学生邮箱注册 + 管理员审核）

### 3.1 目标

- 为尚未入境、无法访问微信的留学生提供公网可用的独立注册与登录通道。
- 注册凭证为**邮箱 + 密码**，并发送邮件验证激活；激活后进入待审核状态，由管理员放行后才能正常使用系统。
- 防止公网注册接口被滥用：引入邮件激活 + 管理员审核两道门。

### 3.2 业务流程

```
留学生                后端                         管理员
  │                    │                              │
  ├─ POST /auth/external-register ─→ 创建 pending_email 状态账号
  │                    ├─ 发激活邮件（链接含 token）
  │                    │
  ├─ 点击邮件链接 GET /auth/email-verify?token=... ─→ 账号变 PENDING_APPROVAL
  │                    │                              │
  │                    │  ←── GET /api/v1/admin/users（状态=PENDING_APPROVAL）
  │                    │  ←── POST /api/v1/admin/users/{id}/approve（or reject）
  │                    │                              │
  ├─ POST /auth/login/password ─→ 登录成功（已激活+已审核）
```

### 3.3 账号状态扩展

`sys_user.status` 现有值需确认（参见 `auth-starter` 的 schema）。需新增或明确使用：

| 状态值 | 含义 |
| --- | --- |
| `PENDING_EMAIL` | 已注册，邮件尚未激活 |
| `PENDING_APPROVAL` | 邮件已激活，等待管理员审核 |
| `ACTIVE` | 正常可用 |
| `REJECTED` | 管理员拒绝 |
| `DISABLED` | 停用 |

密码登录时需在 `PasswordAuthenticationHandler`（`auth-starter` 侧）或应用侧校验：`PENDING_EMAIL` / `PENDING_APPROVAL` / `REJECTED` 状态的账号拒绝登录并返回明确错误提示。

### 3.4 新增接口

**邮箱注册（公网，无需登录，需防频率攻击）：**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/auth/external-register` | 提交 `username`、`password`、`email`、`displayName`；创建 `PENDING_EMAIL` 账号，发激活邮件 |
| `GET` | `/auth/email-verify` | 参数 `?token=<uuid>`，校验 token 并将账号变为 `PENDING_APPROVAL` |
| `POST` | `/auth/external-register/resend` | 重新发送激活邮件（限频）；需携带邮箱 |

**管理员审核（需 `ASSESSMENT_ADMIN` / `ORG_MANAGER` 权限）：**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/users/pending` | 查询待审核账号列表 |
| `POST` | `/api/v1/admin/users/{id}/approve` | 审核通过，账号变 `ACTIVE` |
| `POST` | `/api/v1/admin/users/{id}/reject` | 拒绝，账号变 `REJECTED`，可携带 `reason` |

### 3.5 新增数据表

```sql
-- 邮箱验证 token，存 Redis 或专用表均可；建议 Redis（TTL 24h）
-- key: email_verify:{token-uuid}  value: userId  TTL: 86400s
-- 若用 DB 表：
create table if not exists sys_email_verify_token (
    id bigserial primary key,
    user_id bigint not null references sys_user(id),
    token varchar(64) not null unique,
    used_flag boolean not null default false,
    expires_at timestamp not null,
    created_at timestamp not null default current_timestamp
);
create index on sys_email_verify_token(token);
create index on sys_email_verify_token(user_id);
```

### 3.6 邮件发送基础设施

`lx-boot` 的 `notification` 模块目前只有推送通知（FCM/HTTP Push），没有邮件发送能力。需新增 `EmailNotificationGateway` SPI + 默认的 SMTP 实现：

```yaml
psy:
  notification:
    email:
      enabled: ${PSY_NOTIFICATION_EMAIL_ENABLED:false}
      smtp-host: ${PSY_NOTIFICATION_EMAIL_SMTP_HOST:smtp.school.edu.cn}
      smtp-port: ${PSY_NOTIFICATION_EMAIL_SMTP_PORT:587}
      username: ${PSY_NOTIFICATION_EMAIL_USERNAME:}
      password: ${PSY_NOTIFICATION_EMAIL_PASSWORD:}
      from: ${PSY_NOTIFICATION_EMAIL_FROM:noreply@school.edu.cn}
```

邮件模板需支持 i18n（英文为留学生，中文为其他场景），至少提供：
- `email-activate.html`：账号激活
- `email-activate-resend.html`：重新发送
- `email-approved.html`：审核通过通知
- `email-rejected.html`：审核拒绝通知（含原因）

### 3.7 安全要求

- `/auth/external-register` 和 `/auth/external-register/resend` 需接入频率限制（IP+邮箱维度，建议 Redis + Lua）；现有 `LockStrategyProperties` 可参考，但需新增接口级限流，而非仅密码错误限流。
- 激活 token 为 UUID v4，存 Redis，TTL 24 小时，使用后立即删除（防重放）。
- `PENDING_EMAIL` 状态的账号在密码登录时必须返回专用错误码（`auth.user.pendingEmail`），前端提示"请先激活邮箱"。

### 3.8 部署要求（非代码）

- 需在学校网络防火墙侧开放本系统的 HTTPS 入口到公网（或在已有外部访问通道中增加 `/auth/external-register`、`/auth/email-verify`、`/auth/login/password` 这几个路径的白名单）。
- 出站 SMTP 端口（587/465）需确认学校网络策略。
- 公网域名需已备案且配置 SSL。

## 4. 需求三：微信公众号作为入口

### 4.1 目标

全校学生通过微信公众号访问本系统。承载三类能力：

1. **网页授权登录**：学生点击公众号菜单进入 H5，微信静默/显式授权后免密登录。
2. **模板消息通知**：测评任务提醒、预警、预约结果等通过公众号模板消息推送。
3. **公众号菜单配置**：自定义菜单跳转到系统各入口。

### 4.2 现有能力与差距

现有 `WechatCodeSocialAuthProvider`（`auth-starter/auth-social-wechat`）已实现 `sns/oauth2/access_token`（code 换 openid/unionid），这是网页授权的核心一环。差距在于：

- 缺公众号**服务端接入**：服务器地址校验（`GET /wechat/portal` 校验 `signature`）与消息/事件回调（`POST /wechat/portal`）。
- 缺**网页授权重定向中转**：从公众号菜单跳转 → 微信授权页 → 回调换 code → 前端拿 token。
- 缺**模板消息发送**能力。
- 缺 **JS-SDK** 签名接口（如需分享/定位等）。
- 配置未启用：`social.wechat.enabled: false`。

### 4.3 网页授权登录流程

```
学生微信            公众号/微信服务器           本系统后端            admin-web H5
  │                      │                        │                    │
  ├─点击菜单→ 授权URL(redirect_uri=本系统H5) ─────────────────────────→│
  │                      │                        │        H5 检测无 code，302 跳微信授权
  │←─────── 微信授权页 ──┤                        │                    │
  ├─同意授权→ 微信带 code 回调 redirect_uri ───────────────────────────→│
  │                      │                        │      H5 拿到 code，POST /auth/social/wechat
  │                      │        ←───────────────┤ code 换 openid → sys_auth 映射/建号 → 发 JWT
  │                      │                        │      前端存 token，进入系统
```

复用现有 `POST /auth/social/wechat`（`authCode` = 微信 code）。前端新增微信授权中转页处理跳转与回调。

### 4.4 账号映射策略

- 公众号 openid/unionid 通过 `sys_auth(identity_type='WECHAT', principal_key=unionid或openid)` 映射。
- 建议使用 **unionid**（同一微信开放平台下多应用统一），现有 provider 已优先取 unionid。
- 首次授权无对应账号时，`JdbcSocialAccountService` 默认建 `USER`（受评者）角色新账号——符合"全校学生"场景。
- **需与需求一/二统一策略**：若学生已通过学号在系统内存在，应支持绑定而非重复建号（可选：授权后引导补充学号完成绑定）。

### 4.5 新增接口（公众号服务端）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/wechat/portal` | 服务器地址校验，回显 `echostr`（校验 `signature`/`timestamp`/`nonce`） |
| `POST` | `/wechat/portal` | 接收公众号消息/事件（关注、菜单点击等），XML 收发 |
| `GET` | `/wechat/oauth/authorize` | 生成微信网页授权跳转 URL（可选，也可前端直接拼） |
| `GET` | `/wechat/jssdk/config` | 返回 JS-SDK 签名配置（`appId`/`timestamp`/`nonceStr`/`signature`） |
| `POST` | `/api/v1/wechat/template-message` | 内部调用：发送模板消息（预警/任务提醒等） |

### 4.6 模板消息与通知模块整合

`notification` 模块现有 `PushDeliveryGateway` 抽象（Simulated/Http/Fcm）。新增 `WechatTemplateMessageGateway` 作为一个新的投递渠道：

- 复用 `access_token`（公众号全局 token，需缓存 + 定时刷新，`expires_in` 约 7200s，存 Redis）。
- 模板消息需在微信公众平台后台申请模板 ID，配置到系统。
- 触达对象需有 openid（即已通过公众号授权登录过）。
- 建议渠道优先级：有 openid 走微信模板消息，无 openid（如留学生）回退邮件。

```yaml
psy:
  notification:
    wechat:
      enabled: ${PSY_NOTIFICATION_WECHAT_ENABLED:false}
      template-ids:
        task-remind: ${PSY_WECHAT_TPL_TASK_REMIND:}
        warning-alert: ${PSY_WECHAT_TPL_WARNING:}
        appointment-result: ${PSY_WECHAT_TPL_APPOINTMENT:}
```

### 4.7 公众号菜单配置

- 通过微信 `POST menu/create` 接口下发自定义菜单，菜单项 `view` 类型指向网页授权 URL（进入系统各入口）。
- 建议提供一个管理端一次性配置动作或启动时同步：`POST /api/v1/wechat/menu/sync`（`SYS_ADMIN` 权限）。

### 4.8 access_token 统一管理

公众号 `access_token` 是网页授权之外所有主动调用（模板消息、菜单、JS-SDK ticket）的凭证，全局唯一、有并发刷新问题。需：

- 集中式缓存（Redis），带分布式锁刷新，避免多实例并发获取导致失效。
- 抽象 `WechatAccessTokenProvider`，供模板消息、菜单、JS-SDK 复用。

### 4.9 配置启用

```yaml
auth-module:
  authentication:
    enabled-types:
      - PASSWORD
      - WECHAT
      - CAS      # 需求一
      - OIDC     # 需求一
  social:
    wechat:
      enabled: true
      app-id: ${WECHAT_APP_ID:}
      app-secret: ${WECHAT_APP_SECRET:}
```

### 4.10 部署要求（非代码）

- 公众号后台配置服务器地址（URL 指向 `/wechat/portal`）、Token、EncodingAESKey。
- 网页授权域名、JS 安全域名需在公众号后台白名单配置。
- 服务器需公网可达且 HTTPS。
- 认证服务号才有模板消息与网页授权 scope=snsapi_userinfo 权限（订阅号受限）。

## 5. 数据模型改动汇总

复用现有 `sys_auth(identity_type, principal_key)` → `sys_user` 映射，不改主结构。新增/调整：

| 表 | 改动 | 用途 |
| --- | --- | --- |
| `sys_auth` | 新增 `identity_type` 取值：`CAS`、`OIDC`、`EMAIL`；`metadata_json` 存 SSO 原始 subject/学工号 | 需求一、二、三 |
| `sys_role` | 新增角色 `SCHOOL_LEADER`（校领导，`data_scope=ALL` 只读） | 需求一 |
| `sys_user` | 复用 `email`、`register_source`；新增留学生标识（可用现有字段或 `metadata`） | 需求二 |
| 新增 `psy_registration_review`（或复用审批表） | 留学生注册待审核队列：申请信息、状态、审核人、时间 | 需求二 |
| 新增 `psy_email_verify_code` | 邮箱验证码：email、code_hash、expires_at、used | 需求二 |
| Redis key | 公众号 `access_token`、jsapi_ticket 缓存；邮箱验证码限流 | 需求三、二 |

具体 DDL 在开发阶段补充到 `schema-psy.sql` 与 `auth-starter/doc/schema-postgresql.sql`。

## 6. 需要修改的功能与文件清单

### 6.1 auth-starter（认证核心，改动最大）

| 项 | 位置 | 需求 |
| --- | --- | --- |
| 新增 `auth-sso` 模块（CAS + OIDC provider，实现 `SocialAuthProvider`） | 新模块 | 一 |
| 定制 `SocialAccountService`：SSO/已有账号按学工号/邮箱匹配已存在用户而非新建 | `JdbcAuthServices.kt` 或 psy 侧覆盖 Bean | 一 |
| AuthController 新增 SSO 端点与回调 | `auth-security/.../AuthController.kt` | 一 |
| 新增邮箱验证码 + 邮件发送 SPI（`EmailVerificationService`/`MailSender`） | 新增 SPI + 默认实现 | 二 |
| 注册流程接入验证码校验 + 审核状态 | `JdbcUserRegistrationService`、`AuthController#register` | 二 |
| 完善微信：公众号服务端校验、消息回调、access_token 管理、模板消息、JS-SDK | `auth-social-wechat` 扩展 | 三 |
| 配置项扩展（SSO、邮件、公众号参数） | `AuthModuleProperties.kt`、`AuthModuleAutoConfiguration.kt` | 一二三 |

### 6.2 lx-boot/backend

| 项 | 位置 | 需求 |
| --- | --- | --- |
| 新增 `SCHOOL_LEADER` 角色 + 数据权限种子 | `data-psy.sql` | 一 |
| 启用配置：`enabled-types` 增 CAS/OIDC、`social.wechat.enabled: true`、SSO/邮件/公众号参数 | `application.yml` | 一二三 |
| 留学生注册审核后台接口 + 页面数据 | `useradmin` 模块 | 二 |
| 微信模板消息投递网关接入 notification | `notification/service/WechatTemplateMessageGateway.kt`（新增） | 三 |
| 公众号 portal/oauth/menu/jssdk 控制器 | 新增 `wechat` 包 | 三 |

### 6.3 admin-web

| 项 | 位置 | 需求 |
| --- | --- | --- |
| LoginPage 增加"统一身份登录（CAS/OIDC）"入口按钮 | `pages/LoginPage.tsx` | 一 |
| SSO 回调处理页（接收 ticket/code → 换 token） | 新增页面 + 路由 | 一 |
| 留学生外网注册页（邮箱验证码 + 提交审核 + 审核中状态提示） | 新增页面 | 二 |
| 微信 H5 授权中转页（检测 code、跳授权、换 token） | 新增页面 + 路由 | 三 |
| 注册/审核相关 API 封装 | `auth/api.ts` | 二 |

### 6.4 android-app（按需，可后置）

| 项 | 需求 |
| --- | --- |
| （可选）增加微信登录 / SSO WebView 登录入口 | 一、三 |

### 6.5 部署 / 运维（非代码）

| 项 | 需求 |
| --- | --- |
| 对接学校 CAS/OIDC 身份中心，登记回调地址、拿到 client 配置 | 一 |
| 公网可访问的外部登录入口 + 网关映射 + HTTPS + 频率限制/WAF | 二 |
| 公众号服务器地址、Token、网页授权域名、JS 安全域名白名单、认证服务号 | 三 |
| 邮件发送服务（SMTP / 邮件厂商）账号 | 二 |
| 模板消息模板 ID 在公众平台申请 | 三 |

## 7. 建议实施顺序

分三期，每期可独立交付验证：

**第一期 — 统一登录（需求一，校领导）**
1. auth-starter 新增 `auth-sso` 模块（先做 OIDC，CAS 复用同抽象）。
2. 定制账号映射：SSO subject/学工号匹配已存在用户。
3. 新增 `SCHOOL_LEADER` 角色与数据权限。
4. AuthController SSO 端点 + admin-web 登录入口与回调页。
5. 与学校身份中心联调。

**第二期 — 留学生外部登录（需求二）**
1. auth-starter 新增邮箱验证码 + 邮件发送能力。
2. 注册流程接入验证码 + 管理员审核队列。
3. admin-web 外网注册页 + 后台审核页。
4. 部署层公网入口 + 安全加固（限流、验证码、WAF）。

**第三期 — 微信公众号（需求三）**
1. 公众号服务端接入（portal 校验、消息回调、access_token 管理）。
2. 网页授权中转页 + 复用 `/auth/social/wechat` 登录。
3. 模板消息网关接入 notification。
4. 公众号菜单配置同步。
5. 公众号后台配置与联调。

## 8. 安全与合规要点

- **公网入口（需求二）必须有频率限制、验证码、审核**，否则易被恶意注册刷量；邮箱验证码需限流防轰炸。
- **SSO 回调需校验 state/nonce**，防 CSRF 与重放。
- **校领导数据权限严格只读且脱敏**，遵循现有隐私与安全规范（见 `07-data-privacy-and-security.md`）。
- **微信 access_token、SSO client secret、邮件账号密码等机密**统一走环境变量/密钥管理，不入库明文、不进代码。
- **账号绑定冲突**（同一人多身份）需明确合并/绑定策略，避免重复建号导致角色/数据错乱。
- 新增外网可达认证端点均需评估是否补充审计事件（复用现有 audit 能力）。

## 9. 待补充事项（进入开发前需与学校/甲方确认）

- 学校 OIDC/CAS 身份中心的具体地址、协议版本、可获取的用户属性（学工号、姓名、部门、角色）。
- 校领导账号在身份中心与本系统的映射依据字段（学工号还是邮箱）。
- 留学生审核责任人与审核 SLA。
- 公众号是否已认证为服务号，是否具备网页授权与模板消息权限。
- 邮件发送通道（自建 SMTP 还是第三方邮件服务）。
