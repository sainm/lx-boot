# 文档索引

## 1. 说明

`doc/` 目录现在保留三类文档：

- 设计与规范文档：描述目标业务、数据结构、接口和技术方案。
- 当前状态文档：描述仓库当前真实实现状态、基线边界和后续未完成项。
- 可重复执行 Prompt：用于基于当前代码和运行证据持续审查，不作为完成状态证明。

已经删除的内容：

- 已失效且仅用于一次性 AI 执行的提示词文档
- 历史阶段执行日志 `doc/process/stage-*.md`
- 已被当前状态文档覆盖的一次性交付清单和工程评审记录

## 2. 推荐阅读顺序

1. [项目概览与范围](./01-project-overview-and-scope.md)
2. [业务流程设计](./03-business-process-design.md)
3. [技术架构设计](./05-technical-architecture-design.md)
4. [数据库表设计](./10-database-table-design.md)
5. [API 详细设计](./13-api-design-detailed.md)
6. [后端路线图](./18-backend-roadmap.md)
7. [当前进度看板](./process/03-current-progress-dashboard.md)
8. [未完成任务清单](./process/05-open-todo-list.md)
9. [Baseline 闭环说明](./process/04-baseline-closure.md)
10. [技术架构基线、风险与优化计划](./26-technical-architecture-baseline-and-plan.md)
11. [PostgreSQL 初始化、升级与回滚手册](./23-database-init-guide.md)
12. [心理测评业务闭环与量表适配优化 Prompt](./prompt/01-assessment-closure-and-scale-adaptation.md)
13. [K6、K10、WHO-5、GAD-7、SCL-90量表来源包与审核说明](./scale-packages/README.md)
14. [逐量表适配任务与回归台账](./process/09-scale-adaptation-task-tracker.md)
15. [逐量表适配与全量兼容回归 Prompt](./prompt/07-scale-by-scale-adaptation-and-regression.md)

## 3. 设计文档

- [01-project-overview-and-scope.md](./01-project-overview-and-scope.md)
- [02-role-and-permission-design.md](./02-role-and-permission-design.md)
- [03-business-process-design.md](./03-business-process-design.md)
- [04-data-model-design.md](./04-data-model-design.md)
- [05-technical-architecture-design.md](./05-technical-architecture-design.md)
- [06-page-and-module-design.md](./06-page-and-module-design.md)
- [07-data-privacy-and-security.md](./07-data-privacy-and-security.md)
- [08-acceptance-test-matrix.md](./08-acceptance-test-matrix.md)
- [09-api-design-outline.md](./09-api-design-outline.md)
- [10-database-table-design.md](./10-database-table-design.md)
- [13-api-design-detailed.md](./13-api-design-detailed.md)
- [15-openapi-draft.yaml](./15-openapi-draft.yaml)
- [16-scale-import-design.md](./16-scale-import-design.md)
- [17-scale-import-template-guide.md](./17-scale-import-template-guide.md)
- [18-backend-roadmap.md](./18-backend-roadmap.md)
- [19-advanced-scale-import-and-scoring-design.md](./19-advanced-scale-import-and-scoring-design.md)
- [25-unified-login-and-wechat-integration-design.md](./25-unified-login-and-wechat-integration-design.md)
- [26-technical-architecture-baseline-and-plan.md](./26-technical-architecture-baseline-and-plan.md)
- [adr/0001-persistence-and-migration-strategy.md](./adr/0001-persistence-and-migration-strategy.md)
- [20-linux-deployment-guide.md](./20-linux-deployment-guide.md)
- [21-windows-development-environment-guide.md](./21-windows-development-environment-guide.md)
- [psychological-assessment-system-requirements.md](./psychological-assessment-system-requirements.md)
- [scoring-design.md](./scoring-design.md)
- [scale-packages/README.md](./scale-packages/README.md)
- [scale-packages/k6-v1-source-official-draft.json](./scale-packages/k6-v1-source-official-draft.json)
- [scale-packages/scl90-v1-source-draft.json](./scale-packages/scl90-v1-source-draft.json)
- [scale-packages/scl90-v2-source-technical.json](./scale-packages/scl90-v2-source-technical.json)
- [scale-packages/who5-v1-source-draft.json](./scale-packages/who5-v1-source-draft.json)
- [scale-packages/gad7-v1-source-draft.json](./scale-packages/gad7-v1-source-draft.json)
- [scale-packages/phq9-v1-source-draft.json](./scale-packages/phq9-v1-source-draft.json)
- [scale-packages/scale-adaptation-registry.json](./scale-packages/scale-adaptation-registry.json)

## 历史草稿

以下文件保留用于追溯早期设计，不再作为当前数据库初始化或部署入口。新环境与已有数据库升级均以 Flyway `backend/src/main/resources/db/migration/` 和 [PostgreSQL 初始化、升级与回滚手册](./23-database-init-guide.md) 为准。

- [11-database-ddl-draft.sql](./11-database-ddl-draft.sql)
- [12-database-init-and-seed.sql](./12-database-init-and-seed.sql)
- [14-erd-design.md](./14-erd-design.md)

## 4. 当前状态文档

- [process/00-process-index.md](./process/00-process-index.md)
- [process/03-current-progress-dashboard.md](./process/03-current-progress-dashboard.md)
- [process/04-baseline-closure.md](./process/04-baseline-closure.md)
- [process/05-open-todo-list.md](./process/05-open-todo-list.md)
- [process/07-i18n-guide.md](./process/07-i18n-guide.md)
- [process/08-doc-hygiene-checklist.md](./process/08-doc-hygiene-checklist.md)
- [process/09-scale-adaptation-task-tracker.md](./process/09-scale-adaptation-task-tracker.md)

## 5. 模板与资源

- [templates/scale-import-template.xlsx](./templates/scale-import-template.xlsx)
- [templates/scale-import-sample.xlsx](./templates/scale-import-sample.xlsx)

## 6. 可重复执行 Prompt

- [心理测评业务闭环与量表适配优化 Prompt](./prompt/01-assessment-closure-and-scale-adaptation.md)
- [逐量表适配与全量兼容回归 Prompt](./prompt/07-scale-by-scale-adaptation-and-regression.md)
