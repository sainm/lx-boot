# 高校/企业心理测评与预警系统文档索引

## 1. 文档说明

本文档用于统一整理“高校/企业心理测评与预警系统”的需求、业务、技术与设计资料，便于后续继续开展数据库设计、原型设计、开发实现和项目汇报。

补充说明：

- `doc/` 目录主要放需求、设计与接口文档
- 当前真实工程状态、交付结论与国际化规范，请结合 `doc/process/` 一起阅读

## 2. 文档清单

### 2.1 需求总览

- [心理测评系统需求整理](./psychological-assessment-system-requirements.md)

说明：

- 该文档保留当前对话沉淀出的完整需求背景和需求草稿
- 适合作为需求来源总文档继续维护

### 2.2 结构化设计文档

- [01-项目概述与范围](./01-project-overview-and-scope.md)
- [02-角色与权限设计](./02-role-and-permission-design.md)
- [03-业务流程设计](./03-business-process-design.md)
- [04-数据模型设计](./04-data-model-design.md)
- [05-技术架构设计](./05-technical-architecture-design.md)
- [06-页面与模块设计](./06-page-and-module-design.md)
- [07-数据隐私与安全设计](./07-data-privacy-and-security.md)
- [08-产品验收测试用例矩阵](./08-acceptance-test-matrix.md)
- [09-接口设计纲要](./09-api-design-outline.md)
- [10-数据库表结构设计](./10-database-table-design.md)
- [11-数据库 DDL 草案](./11-database-ddl-draft.sql)
- [12-数据库初始化与测试数据草案](./12-database-init-and-seed.sql)
- [13-接口设计详细版](./13-api-design-detailed.md)
- [14-ER 图设计](./14-erd-design.md)
- [15-OpenAPI 初稿](./15-openapi-draft.yaml)
- [16-量表导入设计](./16-scale-import-design.md)
- [17-量表导入模板说明](./17-scale-import-template-guide.md)
- [18-后端能力待办与演进路线图](./18-backend-roadmap.md)
- [19-复杂题型与复杂评分 Excel 导入设计](./19-advanced-scale-import-and-scoring-design.md)

## 3. 建议阅读顺序

建议按以下顺序阅读和推进：

1. 项目概述与范围
2. 角色与权限设计
3. 业务流程设计
4. 数据模型设计
5. 技术架构设计
6. 页面与模块设计
7. 数据隐私与安全设计
8. 产品验收测试用例矩阵
9. 接口设计纲要
10. 数据库表结构设计
11. 数据库 DDL 草案
12. 数据库初始化与测试数据草案
13. 接口设计详细版
14. ER 图设计
15. OpenAPI 初稿
16. 量表导入设计
17. 量表导入模板说明
18. 后端能力待办与演进路线图

## 4. 当前建议的下一步

在上述文档基础上，后续最适合继续补充的内容包括：

- 数据库表结构明细
- 基于现有接口纲要继续细化正式接口清单
- 数据库 DDL 与 ER 图输出
- 初始化数据与联调用例补充
- OpenAPI/Swagger 规范细化
- 页面原型图
- 角色权限矩阵表
- 量表导入模板设计
- 小程序交互流程设计

## 5. 过程文档入口

如果你想快速了解当前仓库已经做到什么程度，建议同时查看：

- [process/00-process-index.md](./process/00-process-index.md)
- [process/03-current-progress-dashboard.md](./process/03-current-progress-dashboard.md)
- [process/04-baseline-closure.md](./process/04-baseline-closure.md)
- [process/05-delivery-checklist-2026-04-11.md](./process/05-delivery-checklist-2026-04-11.md)
- [process/07-i18n-guide.md](./process/07-i18n-guide.md)
