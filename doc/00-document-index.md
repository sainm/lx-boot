# 文档索引

更新时间：2026-09-19

## 1. 说明

`doc/` 只保留三类文档：

- **设计与规范**：目标业务、数据结构、接口与算法方案，与实现语言无关。
- **当前状态与证据**：仓库当前的实现边界、回归证据与治理状态，代码是唯一事实来源。
- **可重复执行 Prompt**：用于让 AI 基于当前代码和运行证据做审查与执行，不代表完成状态。

维护原则：文档必须能被代码或运行证据验证。已被代码取代的草稿、与代码矛盾的状态描述，一律删除而不是归档——历史版本仍可在 git 历史中检索。

## 2. 推荐阅读顺序

1. [项目概览与范围](./01-project-overview-and-scope.md)
2. [业务流程设计](./03-business-process-design.md)
3. [数据模型设计](./04-data-model-design.md)
4. [数据库表设计](./10-database-table-design.md)
5. [API 详细设计](./13-api-design-detailed.md)
6. [计分设计](./scoring-design.md)
7. [心理测评业务需求](./psychological-assessment-system-requirements.md)
8. [技术架构基线、风险与优化计划](./26-technical-architecture-baseline-and-plan.md)
9. [PostgreSQL 初始化、升级与回滚手册](./23-database-init-guide.md)
10. [量表源包与审核说明](./scale-packages/README.md)
11. [逐量表适配与回归台账](./process/09-scale-adaptation-task-tracker.md)
12. [手动测试手顺（完整版）](./30-manual-test-procedure.md)

## 3. 设计与规范

| 文档 | 内容 | 语言相关性 |
| --- | --- | --- |
| [01-project-overview-and-scope.md](./01-project-overview-and-scope.md) | 项目范围与目标 | 无关 |
| [02-role-and-permission-design.md](./02-role-and-permission-design.md) | 角色、权限与数据范围 | 无关 |
| [03-business-process-design.md](./03-business-process-design.md) | 业务流程 | 无关 |
| [04-data-model-design.md](./04-data-model-design.md) | 数据模型 | 无关 |
| [05-technical-architecture-design.md](./05-technical-architecture-design.md) | 技术架构（Spring/JDBC 形态） | **Kotlin 专属** |
| [06-page-and-module-design.md](./06-page-and-module-design.md) | 页面与模块设计 | 前端相关 |
| [07-data-privacy-and-security.md](./07-data-privacy-and-security.md) | 隐私与安全要求 | 无关 |
| [08-acceptance-test-matrix.md](./08-acceptance-test-matrix.md) | 验收矩阵 | 无关 |
| [10-database-table-design.md](./10-database-table-design.md) | 表结构设计（由 V1–V28 实际结构生成） | 无关 |
| [13-api-design-detailed.md](./13-api-design-detailed.md) | 接口详细设计（由 Kotlin 控制器生成） | 无关（HTTP 契约） |
| [14-erd-design.md](./14-erd-design.md) | ERD | 无关 |
| [16-scale-import-design.md](./16-scale-import-design.md) | 量表导入设计 | 无关 |
| [17-scale-import-template-guide.md](./17-scale-import-template-guide.md) | 导入模板说明 | 无关 |
| [19-advanced-scale-import-and-scoring-design.md](./19-advanced-scale-import-and-scoring-design.md) | 高级导入与计分 | 无关 |
| [22-android-respondent-app-guide.md](./22-android-respondent-app-guide.md) | Android 被测者端 | Kotlin（Android） |
| [24-scale-visualization-design.md](./24-scale-visualization-design.md) | 量表可视化设计 | 无关 |
| [25-unified-login-and-wechat-integration-design.md](./25-unified-login-and-wechat-integration-design.md) | 统一登录与微信接入 | 部分相关 |
| [scoring-design.md](./scoring-design.md) | 计分与质量策略 | 无关 |
| [psychological-assessment-system-requirements.md](./psychological-assessment-system-requirements.md) | 需求全文 | 无关 |
| [adr/0001-persistence-and-migration-strategy.md](./adr/0001-persistence-and-migration-strategy.md) | 持久化与迁移决策记录 | **Kotlin 专属** |
| [30-manual-test-procedure.md](./30-manual-test-procedure.md) | 手动测试手顺（细则）：324 条历史用例 + 逐条步骤、SQL 与附录 | 无关 |
| [31-manual-test-procedure-full.md](./31-manual-test-procedure-full.md) | 全量手动测试标准：全功能（MT-API/MT-UI）、全业务（MT-BIZ）、全网络（MT-NET）与执行判定 | 无关 |
| [manual-test/coverage-matrix.md](./manual-test/coverage-matrix.md) | 自动生成的接口/路由 ↔ 用例覆盖矩阵（168 接口、23 路由） | 无关 |
| [manual-test/case-registry.json](./manual-test/case-registry.json) | 自动生成的机器可读注册表（接口、路由、用例映射） | 无关 |

## 4. 部署与运维

| 文档 | 内容 |
| --- | --- |
| [20-linux-deployment-guide.md](./20-linux-deployment-guide.md) | Linux 单机部署（nginx + systemd + PG + Redis） |
| [21-windows-development-environment-guide.md](./21-windows-development-environment-guide.md) | Windows 开发环境（Java/Node） |
| [23-database-init-guide.md](./23-database-init-guide.md) | PostgreSQL 初始化、baseline 与回滚 |
| [26-technical-architecture-baseline-and-plan.md](./26-technical-architecture-baseline-and-plan.md) | 架构基线、复杂度与分阶段计划 |
| [27-observability-runbook.md](./27-observability-runbook.md) | 指标、告警与排障 |
| [28-backup-restore-runbook.md](./28-backup-restore-runbook.md) | 备份与恢复演练 |
| [29-performance-capacity-baseline.md](./29-performance-capacity-baseline.md) | 性能与容量基线 |
| [templates/init-sys-admin.sql](./templates/init-sys-admin.sql) | 管理员初始化脚本 |

## 5. 当前状态与证据

| 文档 | 内容 |
| --- | --- |
| [README.md](../README.md) | 仓库现状、最新量表回归证据与边界声明 |
| [scale-packages/README.md](./scale-packages/README.md) | 8 个量表版本的技术状态、治理状态与源包说明 |
| [process/09-scale-adaptation-task-tracker.md](./process/09-scale-adaptation-task-tracker.md) | 逐量表适配与全量回归台账（含问题与闭环记录） |
| [process/07-i18n-guide.md](./process/07-i18n-guide.md) | 三语资源规范与校验要求 |
| [process/10-manual-test-execution-20260919.md](./process/10-manual-test-execution-20260919.md) | AI 自测执行记录：411 条用例整跑（PASS 400 / FAIL 0 / BLOCKED 11）、逐页三语侦测、G-1~G-9 发现与全网络对端证据 |

## 6. 可执行 Prompt

`prompt/` 下的文档用于让 AI 基于当前代码与运行证据执行审查、适配与回归，**不作为完成状态证明**：

- [01-assessment-closure-and-scale-adaptation.md](./prompt/01-assessment-closure-and-scale-adaptation.md)
- [02-clinical-safety-scale-adaptation-and-ux.md](./prompt/02-clinical-safety-scale-adaptation-and-ux.md)
- [03-remaining-closure-goals-and-execution.md](./prompt/03-remaining-closure-goals-and-execution.md)
- [04-m1-m2-real-scale-and-scoring-scope.md](./prompt/04-m1-m2-real-scale-and-scoring-scope.md)
- [05-real-scale-adaptation-and-report-closure.md](./prompt/05-real-scale-adaptation-and-report-closure.md)
- [06-remaining-scale-capabilities-closure.md](./prompt/06-remaining-scale-capabilities-closure.md)
- [07-scale-by-scale-adaptation-and-regression.md](./prompt/07-scale-by-scale-adaptation-and-regression.md)
- [08-regenerate-project-from-spec.md](./prompt/08-regenerate-project-from-spec.md)：**工程重建规格**（语言无关，可据此在 Go 中重建同等能力）

## 7. 语言迁移（Java/Kotlin → Go）相关说明

若后端迁移到 Go，上表"语言相关性"为**无关**的文档是可复用资产，应作为迁移契约（数据模型、HTTP 接口、计分规则、权限与租户边界、初始化与部署流程）；标注为 **Kotlin 专属**的文档描述的是当前实现形态，迁移落地后需重写或删除。

迁移时必须同时搬走的代码级契约（不在文档里，需从代码提取）：

- 由 `scripts/generate_code_docs.py` 从源码/迁移后数据库生成的 `doc/10`（61 张表）与 `doc/13`（本仓库 109 条业务路径 + auth-starter 54 条认证路径）；`python3 scripts/generate_code_docs.py check` 可校验生成物未过期
- Flyway 迁移集合 `backend/src/main/resources/db/migration/V1..V28` 及其受控执行入口（session advisory lock、baseline 预检、禁止 clean）
- 101 处 `@PreAuthorize` 的角色约束与 `TenantAccessPolicy` 的租户/全局判定规则
- 量表源包校验规则（`ScaleSourcePackageValidation`）与导入治理强制项（授权/版权强制 `PENDING_REVIEW`、治理强制 `DRAFT`）
- 安全响应策略的专业复核与管理审批双人分离约束（服务层 + V28 数据库约束）

## 8. 已删除文档（2026-09-18）

以下文档被代码或更新的文档取代，已删除（可 `git log --diff-filter=D -- <path>` 找回）：

| 文档 | 删除原因 |
| --- | --- |
| `09-api-design-outline.md` | 自述为"接口清单设计前的结构化占位"，其待补内容已由 `13-api-design-detailed.md` 与代码覆盖 |
| `11-database-ddl-draft.sql`、`12-database-init-and-seed.sql` | 历史 DDL/种子草案；正式结构入口是 Flyway `V1..V28` |
| `15-openapi-draft.yaml` | 仅 25 个路径；本仓库实际 109 条业务路径（另含 auth-starter 54 条认证路径），且缺量表包与安全响应策略等全部新增接口 |
| `18-backend-roadmap.md` | 2026-04 路线图，未包含 ScalePackage、Golden Case、安全响应策略、Flyway 等后续工作，已由 `26` 与 `process/09` 取代 |
| `process/00-process-index.md`、`03-current-progress-dashboard.md`、`04-baseline-closure.md`、`05-open-todo-list.md`、`08-doc-hygiene-checklist.md` | 状态描述与代码矛盾（例如称 Android 未落地，而 `android-app/` 已有实现与测试），且已被 README 证据与 `process/09` 取代 |
