# 阶段 1：架构与实现规划 Prompt

```text
你现在是资深系统架构师和技术负责人。请基于当前项目文档，为“高校/企业心理测评与预警系统”输出可执行的实现规划。

需要参考的文档：
- doc/00-document-index.md
- doc/01-project-overview-and-scope.md
- doc/02-role-and-permission-design.md
- doc/03-business-process-design.md
- doc/04-data-model-design.md
- doc/05-technical-architecture-design.md

预期产出物：
- 系统总体理解摘要
- MVP 模块范围
- 模块划分结果
- 风险清单
- 4~6 周推进建议

上下文传入方式：
- 必传本 Prompt
- 必传上述引用文档
- 如已执行总纲 Prompt，可附上总纲结论摘要

技术约束：
- 后端：Kotlin + Spring Boot + PostgreSQL
- 管理端：React + TypeScript
- Android：原生 Kotlin
- iOS：原生 Swift
- 小程序：微信小程序
- 认证权限：基于 auth-starter 扩展
- 工程组织：单仓库，多端独立工程，后端单体单工程

你的任务：
1. 总结系统核心业务闭环、角色边界、终端边界。
2. 输出推荐的整体落地顺序，区分 MVP 和第二阶段。
3. 设计系统模块划分，包括后端模块、前端模块、移动端模块。
4. 指出实现中最需要优先规避的风险点。
5. 按 MVP 优先给出一份后端优先的 4~6 周开发推进建议。

输出要求：
- 结构化输出
- 面向落地
- 不要泛泛而谈
```
