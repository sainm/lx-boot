# 心理测评与预警系统（lx-boot）

面向高校心理中心、辅导员体系、企业 EAP 场景的心理测评与风险预警平台，覆盖量表配置、测评任务下发、在线答题、自动评分、报告生成、风险预警、咨询预约、干预跟踪和群体统计分析。

## 当前状态

截至 2026-04-11，仓库已经达到可继续开发的稳定 baseline：

- `admin-web`：`npm run build` 通过
- `backend`：`./gradlew test --rerun-tasks` 通过
- 管理端主链路可用
- 用户侧 Web 主链路可用
- 中英双语国际化基础设施已接通

当前仍未落地：

- Android 原生端
- iOS 原生端
- 微信小程序

## 业务闭环

```
量表配置 → 任务下发 → 在线作答 → 自动评分
    → 系统报告 → 预警识别 → 咨询预约
    → 干预记录 → 结案/复测 → 群体统计
```

---

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端语言 | Kotlin 2.1 + JDK 21 |
| 后端框架 | Spring Boot 3.4 / Spring Security / Spring JDBC |
| 数据库 | PostgreSQL |
| 缓存 | Redis |
| 认证权限底座 | `auth-spring-boot-starter`（composite build） |
| 管理端前端 | React 19 + TypeScript + Vite + Ant Design 5 |
| 前端状态管理 | TanStack Query（React Query） |

---

## 项目结构

```
lx-boot/
├── backend/          Spring Boot 后端（单体，按业务模块分包）
├── admin-web/        PC Web 管理端（React + Vite）
└── doc/              设计文档
    ├── scoring-design.md            量表计分设计（含各量表配置示例）
    ├── 01-project-overview-and-scope.md
    ├── 02-role-and-permission-design.md
    ├── 04-data-model-design.md
    ├── 13-api-design-detailed.md
    └── 14-erd-design.md
```

---

## 已实现功能

### 后端接口

| 模块 | 说明 |
|---|---|
| **量表管理** | 量表 CRUD；批量添加维度、题目（含反向计分/权重）、结果规则 |
| **计分引擎** | SIMPLE_SUM / REVERSE_SUM / WEIGHTED_SUM；粗分换算系数；维度平均分；维度级结果规则独立匹配 |
| **测评任务** | 任务创建、分配（按组/按用户）；通知推送 |
| **在线答题** | 获取题目、保存草稿、提交评分 |
| **评分与报告** | 提交后自动生成 `psy_assessment_result` + 维度分明细 + 系统报告 |
| **风险预警** | 自动触发预警；领取/分配/干预/关闭 |
| **咨询预约** | 咨询师排班；用户预约；咨询记录 |
| **通知** | 系统通知下发；标记已读 |
| **统计** | 仪表盘；群体报告（维度分、风险分布） |
| **导出** | 报告异步导出（文本/PDF）；任务状态轮询；文件下载 |
| **认证审计** | 登录日志；安全事件（由 auth-starter 自动注册） |
| **国际化** | 前端 `zh-CN / en-US` 切换；后端按 `Accept-Language` 返回本地化文案 |

### 管理端页面

| 页面 | 功能 |
|---|---|
| 量表管理 | 创建（含计分方式/换算系数）、批量配置维度/题目/规则、详情查看 |
| 测评任务 | 任务列表、创建、分配 |
| 我的测评 | 待完成任务、在线答题 |
| 我的报告 | 报告列表与详情 |
| 预警管理 | 预警列表、领取/分配/干预/关闭/导出 |
| 群体报告 | 维度均分统计、风险分布 |
| 咨询预约 | 排班管理、预约、咨询记录 |
| 通知 | 通知列表、标记已读 |
| 仪表盘 | 关键统计数据概览 |
| 认证审计 | 登录日志、安全事件查询 |

### 用户侧 Web

| 页面 | 功能 |
|---|---|
| 我的任务 | 待完成任务、进入问卷作答 |
| 问卷作答 | 本地草稿恢复、提交后跳转报告 |
| 我的报告 | 个人报告列表与详情 |
| 通知 | 通知查看、业务回流 |
| 咨询预约 | 预约创建、成功态展示 |
| 会话详情 | 查看当前 token 生命周期与会话状态 |

---

## 量表计分设计

系统支持主流心理测评量表（PHQ-9、GAD-7、SCL-90、SAS、SDS 等），设计要点：

- `score_method`：控制单题有效分计算方式（简单求和 / 反向计分 / 加权）
- `score_coefficient`：粗分换算系数（默认 1.0；SAS/SDS 填 1.25）
- 维度分：有维度的题自动按维度求平均，匹配维度级结果规则
- 总分：所有题（含无维度题）参与总分计算，匹配全局结果规则

详见 → [doc/scoring-design.md](doc/scoring-design.md)

---

## 角色说明

| 角色标识 | 说明 |
|---|---|
| `SYS_ADMIN` | 超级管理员，全部权限 |
| `ASSESSMENT_ADMIN` | 测评管理员，管理量表/任务/预警/报告 |
| `COUNSELOR` | 咨询师，查看预警、填写咨询记录、管理自己的排班 |
| `USER` | 普通用户（学生/员工），参与测评、查看自己的报告和通知 |

---

## 本地启动

### 前置条件

- JDK 21+
- Node.js 18+
- PostgreSQL
- Redis
- `auth-starter` 源码（与本工程同级目录，composite build 依赖）

### 后端

```bash
cd backend
./gradlew bootRun
# 默认端口 8090
# 启动时自动执行 schema-psy.sql 初始化/升级业务表
```

> 首次启动前需先执行 `auth-starter` 的认证权限表 DDL。

### 管理端前端

```bash
cd admin-web
npm install
npm run dev
# 默认端口 5173
# /api 和 /auth 自动代理到 localhost:8090
```

### 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PSY_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/auth_starter` | 数据库连接地址 |
| `PSY_DB_USERNAME` | `auth_starter_app` | 数据库用户名 |
| `PSY_DB_PASSWORD` | `AuthStarter@2026` | 数据库密码 |
| `PSY_REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `PSY_REDIS_PORT` | `6379` | Redis 端口 |
| `PSY_JWT_SECRET` | `change-me-...` | JWT 签名密钥（**生产环境必须替换**） |
| `PSY_EXPORT_MAX_IN_MEMORY_JOBS` | `100` | 异步导出内存任务数上限 |
| `PSY_EXPORT_MAX_IN_MEMORY_FILE_BYTES` | `10485760` | 单个异步导出文件内存保存上限（字节） |

> 使用 `prod` 或 `production` profile 启动时，系统会拒绝使用默认 JWT 密钥、默认数据库密码和本地默认数据库连接。

---

## 国际化

系统当前支持中英双语：

- 前端支持 `zh-CN` / `en-US`
- 后端基于 `Accept-Language` 返回本地化文案
- 错误消息、DTO 校验、通知、报告、导出文案都已接入 i18n

国际化维护说明见：

- [doc/process/07-i18n-guide.md](doc/process/07-i18n-guide.md)

---

## 过程与交付文档

如果要快速了解当前真实状态，建议从这些文档开始：

- [doc/process/00-process-index.md](doc/process/00-process-index.md)
- [doc/process/03-current-progress-dashboard.md](doc/process/03-current-progress-dashboard.md)
- [doc/process/04-baseline-closure.md](doc/process/04-baseline-closure.md)
- [doc/process/05-delivery-checklist-2026-04-11.md](doc/process/05-delivery-checklist-2026-04-11.md)

---

## CI

仓库已提供 GitHub Actions 基线：`.github/workflows/ci.yml`。

- 管理端：`npm ci`、`npm test`、`npm run build`
- 后端：JDK 21 + Gradle wrapper 执行 `./gradlew test`

后端依赖同级目录的 `auth-starter` composite build。CI 会默认 checkout `${repository_owner}/auth-starter` 到工作区同级目录；如果仓库名或组织不同，可配置 GitHub Actions 变量 `AUTH_STARTER_REPOSITORY`，私有仓库可配置 secret `AUTH_STARTER_TOKEN`。
