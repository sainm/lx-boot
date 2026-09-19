# lx-boot

心理测评与预警系统仓库，面向高校心理中心、辅导员体系和企业 EAP 场景。

## 当前状态

截至 2026-09-19，仓库处于可持续迭代的工程基线阶段：

- `admin-web` 可构建
- `backend` 关键测试可执行
- 管理端、用户侧 Web、导出、通知、预警、预约、审计等主链路均已有第一版闭环
- 代码事实基线：分支 `feature/redo`，HEAD `f178857`；Flyway `V1–V28`，`lx/public` 实测 61 张表（46 `psy_*` + 15 `sys_*`）；本仓库 109 条业务 HTTP 路径 + `auth-starter` 54 条认证路径
- 参考文档 [doc/10-database-table-design.md](doc/10-database-table-design.md) 与 [doc/13-api-design-detailed.md](doc/13-api-design-detailed.md) 由 `python3 scripts/generate_code_docs.py all` 从代码/实际结构生成，可用 `python3 scripts/generate_code_docs.py check` 校验是否过期

仍未完成的重点项：

- iOS / 小程序端（Android 不在本量表技术/发布目标范围）
- 更多真实 Push 厂商 SDK 直连（FCM HTTP v1 已完成第一版）
- 真实对象存储厂商 SDK 适配
- 设备会话治理增强（基础能力已下沉到 auth-starter，后续重点是租户级 / 用户级风控、刷新令牌治理与异常设备处置策略）

量表技术回归当前以 7 个 active 版本为范围，最新隔离 PostgreSQL 全量证据为
[`REG-PLAYWRIGHT-20260816-051445`](build/reports/scale-adaptation/registry-psy_e2e_1786857239_21004.json)：7 个版本各 17/17 required checks PASS，报告 SHA-256 为 `f405c24c6262183f436e1debfbf7611d47894118d4bd06056a3ed04f773f00a3`，registry immutable fingerprint 为 `c4969aadb2c7dce167bba714637849d1029624ca3d232f7fbd74ae1b3b8c5880`。五种通用计分方法、五方法 × `REJECT`/`ALLOW`/`PRORATE` 十五组合质量策略，以及不含原题的三条通用维度/时间重编码规则均 PASS；同一合成矩阵还覆盖 `SINGLE_CHOICE`、`MULTI_SELECT`、`MATRIX`、`TEXT_WITH_OPTION`、`TEXT`、`TIME`、`SLIDER` 七种输入路径，并验证每个 active 包 respondent 逐题显示/导航、中文/日文/英文报告 Web、q1=0→跳过 q2 的 Web/API/数据库分支。统一覆盖 scoring trace、结果解释、三语 Web、Word/PDF/文本、任务锁版和历史兼容；导出语义共享 `ExportServiceTest` 8 tests、0 skipped/failures/errors；wrapper 创建并清理隔离 schema，残留 `psy_e2e_*` schema 为 0；Android 明确排除。真实授权、三语正式审校、专业双审批和业务验收仍不由技术 PASS 代替，PSS-10 继续保持 `INPUT_PENDING`。

后端兼容复核已于 2026-09-19 在本机重新执行：启用 `PSY_POSTGRES_INTEGRATION=true` 的完整回归为 480 tests、0 skipped、0 failures、0 errors（BUILD SUCCESSFUL，51s），测试 schema `psy_migration_*` 残留计数为 0；该复核包含 V28 安全响应策略专业审核迁移及其 PostgreSQL 隔离验证，不改变运行时 ScalePackage 语义。

剩余候选的快速技术映射见 [`doc/scale-packages/scale-capability-catalog.json`](doc/scale-packages/scale-capability-catalog.json)；该目录不含原题或正式支持声明，正式资料到位后仍按单份版本化 ScalePackage 和全量回归导入。

## 目录

```text
lx-boot/
├─ backend/      Spring Boot 后端
├─ admin-web/    React 管理端与用户侧 Web
└─ doc/          设计文档与当前状态文档
```

## 文档入口

优先阅读：

- [doc/00-document-index.md](doc/00-document-index.md)
- [doc/26-technical-architecture-baseline-and-plan.md](doc/26-technical-architecture-baseline-and-plan.md)
- [doc/process/09-scale-adaptation-task-tracker.md](doc/process/09-scale-adaptation-task-tracker.md)
- [doc/scale-packages/README.md](doc/scale-packages/README.md)
- [doc/30-manual-test-procedure.md](doc/30-manual-test-procedure.md)

## 编译环境

- JDK 21
- Node.js 24.14.1（与当前本地开发机保持一致）
- 仓库根目录提供 `.java-version` 与 `.nvmrc`
- 后端通过 Gradle Toolchain 固定到 Java 21

建议首次进入仓库后先清理一次本地产物，再执行构建。

## 本地启动

### 后端

```bash
cd backend
./gradlew bootRun
```

### 前端

```bash
cd admin-web
npm install
npm run dev
```
