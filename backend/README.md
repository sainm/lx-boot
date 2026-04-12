# backend

心理测评系统后端工程。

## 技术栈

- **语言**：Kotlin 2.1 + JDK 21
- **框架**：Spring Boot 3.4 / Spring Security / Spring JDBC
- **数据库**：PostgreSQL
- **缓存**：Redis
- **认证底座**：`auth-spring-boot-starter`（Gradle composite build）

## 当前状态

截至 2026-04-11：

- `./gradlew test --rerun-tasks` 通过
- 后端主链路、导出、通知、预约、统计、审计均已具备 baseline
- 国际化底座已接通，支持按 `Accept-Language` 返回本地化文案

## 模块结构

```
org.sainm.psy
├── scale/          量表、维度、题目、选项、结果规则
├── assessment/     测评任务、答卷、自动评分（ScoreCalculator）、结果、维度分
├── report/         系统报告生成与查询
├── warning/        风险预警触发、领取、分配
├── intervention/   干预记录
├── appointment/    咨询排班与预约
├── counseling/     咨询记录
├── notification/   通知下发与已读
├── statistics/     仪表盘、群体报告
├── export/         异步报告导出（文本/PDF）
├── auth/           当前用户、会话、认证相关接口
└── common/         统一响应、异常、分页、国际化、当前用户上下文
```

## 计分引擎

提交答卷时由 `ScoreCalculator` 完成：

1. 按 `score_method` 计算每题有效分（SIMPLE_SUM / REVERSE_SUM / WEIGHTED_SUM）
2. 粗分 × `score_coefficient` = 最终总分
3. 有维度的题按维度求平均分
4. 总分匹配全局结果规则，维度分匹配维度级结果规则
5. 结果写入 `psy_assessment_result` 和 `psy_assessment_result_dimension`

详见 → [../doc/scoring-design.md](../doc/scoring-design.md)

## 数据库初始化

启动时自动执行 `src/main/resources/schema-psy.sql`（`create table if not exists` + `alter table add column if not exists`），支持全新初始化和存量数据库升级。

## 国际化

后端当前已接入国际化基础设施：

- `AcceptHeaderLocaleResolver`
- `MessageSource`
- `LocalizedMessages`

覆盖范围包括：

- `BizException`
- DTO 参数校验消息
- 通知文案
- 报告默认文案
- 导出文本 / PDF 固定字段

资源文件位于：

- `src/main/resources/i18n/messages.properties`
- `src/main/resources/i18n/messages_zh_CN.properties`

## 本地启动

```bash
./gradlew bootRun
# 默认端口 8090
```

环境变量（默认值见 `application.yml`）：

| 变量 | 默认值 |
|---|---|
| `PSY_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/auth_starter` |
| `PSY_DB_USERNAME` | `auth_starter_app` |
| `PSY_DB_PASSWORD` | `AuthStarter@2026` |
| `PSY_REDIS_HOST` | `127.0.0.1` |
| `PSY_REDIS_PORT` | `6379` |
| `PSY_JWT_SECRET` | `change-me-...` |
| `PSY_EXPORT_MAX_IN_MEMORY_JOBS` | `100` |
| `PSY_EXPORT_MAX_IN_MEMORY_FILE_BYTES` | `10485760` |

> 使用 `prod` 或 `production` profile 启动时，系统会拒绝使用默认 JWT 密钥、默认数据库密码和本地默认数据库连接。

> 异步导出任务当前仍为内存态实现，上述限制用于防止过量任务或大文件占用内存；生产级多实例部署仍建议演进为 DB/Redis 状态存储 + 文件/对象存储。

## CI 说明

GitHub Actions 基线位于 `../.github/workflows/ci.yml`。后端 job 使用 JDK 21 和 Gradle wrapper 执行 `./gradlew test`。

由于 `settings.gradle.kts` 使用 `includeBuild("../../auth-starter")`，CI 会将 `auth-starter` checkout 到与 `lx-boot` 同级的工作区目录。仓库名或组织不一致时，配置 Actions 变量 `AUTH_STARTER_REPOSITORY`；私有仓库配置 `AUTH_STARTER_TOKEN`。

## 主要接口

| 路径 | 说明 |
|---|---|
| `GET /api/v1/scales` | 量表分页列表 |
| `POST /api/v1/scales` | 创建量表（含 score_method、score_coefficient） |
| `GET /api/v1/scales/{id}` | 量表详情（含维度/题目/结果规则） |
| `POST /api/v1/scales/{id}/dimensions/batch` | 批量添加维度 |
| `POST /api/v1/scales/{id}/questions/batch` | 批量添加题目（含选项、反向计分、权重） |
| `POST /api/v1/scales/{id}/result-rules/batch` | 批量添加结果规则 |
| `GET /api/v1/tasks` | 测评任务列表 |
| `POST /api/v1/tasks` | 创建测评任务 |
| `POST /api/v1/tasks/{id}/assign-groups` | 按组分配任务 |
| `POST /api/v1/tasks/{id}/assign-users` | 按用户分配任务 |
| `GET /api/v1/my/tasks` | 我的待完成任务 |
| `GET /api/v1/my/tasks/{taskId}/questions` | 获取答题内容 |
| `POST /api/v1/answer-sheets/save` | 保存草稿 |
| `POST /api/v1/answer-sheets/submit` | 提交答卷（触发自动评分） |
| `GET /api/v1/reports/my` | 我的报告列表 |
| `GET /api/v1/reports/{id}` | 报告详情 |
| `GET /api/v1/warnings` | 预警列表 |
| `POST /api/v1/warnings/{id}/claim` | 领取预警 |
| `POST /api/v1/interventions` | 创建干预记录 |
| `GET /api/v1/counselors/{id}/schedules` | 咨询师排班 |
| `POST /api/v1/counselors/me/schedules` | 创建我的排班 |
| `POST /api/v1/appointments` | 创建咨询预约 |
| `GET /api/v1/statistics/dashboard` | 仪表盘统计 |
| `GET /api/v1/statistics/group-reports` | 群体报告 |
| `POST /api/v1/exports/reports` | 发起报告导出 |
| `GET /api/v1/exports/reports/download` | 下载导出文件 |
