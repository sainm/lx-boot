# 阶段 0 执行记录

阶段名称：总纲理解
执行日期：2026-03-27
使用 Prompt：`doc/prompt/implementation-master-prompt.md`

输入文档：
- `doc/00-document-index.md`
- `doc/prompt/00-prompt-index.md`
- 当前 `doc` 与 `doc/process` 全部已整理文档

阶段目标：
- 统一理解项目整体设计
- 形成后续阶段共识

本阶段输出：
- 项目整体理解摘要
- 总体实现顺序
- 核心模块划分
- 风险清单
- 后续阶段的统一上下文基础

确认结论：
- 当前项目采用前后端分离
- 后端采用单体应用 + 模块化分层
- 工程组织采用单仓库，多端独立工程，后端单体单工程
- 后端技术栈为 Kotlin + Spring Boot + Spring Security + Spring JDBC + PostgreSQL
- 管理端采用 React + TypeScript
- 用户端采用 Android 原生 Kotlin、iOS 原生 Swift、微信小程序
- 用户、认证、权限、组织能力复用 `auth-starter`
- 当前业务闭环明确为：
  量表配置/导入 -> 测评任务 -> 在线答题 -> 自动评分 -> 系统报告 -> 预警 -> 接单/跟进 -> 咨询预约 -> 咨询记录 -> 复测/结案 -> 群体报告
- 当前主链路优先级明确为：
  量表 -> 任务 -> 答题 -> 报告 -> 预警
- 当前第二阶段扩展能力明确为：
  预约、干预、统计、导出、通知、审计
- 当前多端推进策略明确为：
  后端优先稳定，再推进管理端，再推进 Android/iOS/小程序

项目整体理解摘要：

1. 这是一个以心理测评闭环为核心的业务系统，不是单纯问卷系统。
2. 项目重心在业务闭环和权限控制，而不是复杂分布式架构。
3. 技术实现应优先保证后端主链路和统一 API，再推进多端接入。
4. `auth-starter` 是账号、认证、权限、组织的基础底座，业务系统不重复建设这部分能力。

总体实现顺序：

1. 明确 MVP 与模块边界
2. 确认数据库结构与后端模块结构
3. 实现后端主链路
4. 实现后端扩展能力
5. 实现 React 管理端
6. 实现 Android 原生 App
7. 实现 iOS 原生 App
8. 实现微信小程序
9. 完成测试、联调与交付

核心模块划分：

- auth-integration
- scale
- assessment
- report
- warning
- appointment
- notification
- statistics
- audit

风险清单：

1. `auth-starter` 与业务系统的边界若不尽早固定，后端实现会反复调整。
2. 多端开发依赖统一 API，如果接口和权限边界不稳定，会放大返工成本。
3. 预警、预约、干预三者之间是闭环核心，数据模型和状态流转必须先稳定。
4. 敏感数据权限、脱敏与审计如果后补，代价会比较高。

发现问题：
- 阶段 1 仍需进一步明确 MVP 范围
- `auth-starter` 的具体集成方式仍需在后端阶段进一步落实

需要回写的文档：
- `doc/process/03-current-progress-dashboard.md`
- `doc/process/01-prompt-driven-task-plan.md`

是否进入下一阶段：
- 是，完成阶段 0 后进入阶段 1

下一阶段准备事项：
- 执行 `doc/prompt/01-architecture-and-plan-prompt.md`
- 明确 MVP 边界、模块划分和 4~6 周推进顺序
