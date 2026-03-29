# Prompt 执行索引

## 1. 使用方式

本目录中的 Prompt 已按项目推进顺序拆分，可以按阶段逐个执行，而不是一次性把所有任务丢给 AI。

建议执行原则：

1. 严格按顺序推进
2. 上一阶段产出确认后，再进入下一阶段
3. 每个 Prompt 都默认以上一阶段产物为输入
4. 如果某一阶段有改动，后续 Prompt 需要同步更新上下文

## 2. 推荐执行顺序

0. [implementation-master-prompt.md](./implementation-master-prompt.md)
1. [01-architecture-and-plan-prompt.md](./01-architecture-and-plan-prompt.md)
2. [02-database-design-prompt.md](./02-database-design-prompt.md)
3. [03-backend-core-prompt.md](./03-backend-core-prompt.md)
4. [04-backend-advanced-prompt.md](./04-backend-advanced-prompt.md)
5. [05-react-admin-prompt.md](./05-react-admin-prompt.md)
6. [06-android-app-prompt.md](./06-android-app-prompt.md)
7. [07-ios-app-prompt.md](./07-ios-app-prompt.md)
8. [08-miniapp-prompt.md](./08-miniapp-prompt.md)
9. [09-testing-and-delivery-prompt.md](./09-testing-and-delivery-prompt.md)

说明：

- `implementation-master-prompt.md` 是总纲 Prompt，适合一次性长会话或前期统一理解项目
- `01-09` 是推荐的分阶段执行 Prompt，适合实际开发推进
- 实际执行时，优先使用 `01-09`，总纲 Prompt 作为全局参考

## 2.1 各阶段预期产出物

- 阶段 0：项目总体理解、全局实现路线、核心风险列表
- 阶段 1：模块划分方案、MVP 落地顺序、4~6 周推进建议
- 阶段 2：数据库修订建议、表结构确认结果、字段/索引调整建议
- 阶段 3：后端核心模块设计、主链路接口实现顺序、auth-starter 集成方案
- 阶段 4：后端扩展模块设计、异步处理建议、预警/预约/通知扩展方案
- 阶段 5：React 管理端项目结构、页面与权限方案、前端对接计划
- 阶段 6：Android 原生 App 结构设计、主链路页面流、弱网处理建议
- 阶段 7：iOS 原生 App 结构设计、平台特有合规与审核注意事项
- 阶段 8：小程序端页面结构、订阅消息策略、分包建议
- 阶段 9：测试分层方案、联调顺序、CI/CD 与交付检查清单

## 2.2 上下文传入建议

每个阶段执行前，建议向 AI 传入：

- 当前阶段 Prompt 文件内容
- `doc/00-document-index.md`
- 本阶段明确要求引用的文档
- 上一阶段的关键结论摘要

建议上一阶段至少提炼以下内容传给下一阶段：

- 已确认的技术栈
- 已确认的数据结构或接口方向
- 已确认的优先级和边界
- 本阶段需要承接的具体模块

## 3. 当前技术栈约束

- 后端：Kotlin + Spring Boot + PostgreSQL
- 管理端：React + TypeScript
- Android：原生 Kotlin
- iOS：原生 Swift
- 小程序：微信小程序
- 用户与权限基础：`auth-starter`

## 3.1 最终技术栈结论

当前项目最终明确的技术栈如下：

- 后端：Kotlin + Spring Boot + Spring Security
- 认证权限底座：`auth-starter`
- 数据库：PostgreSQL
- 数据访问：Spring JDBC + JdbcTemplate / NamedParameterJdbcTemplate
- 缓存：Redis
- 管理端：React + TypeScript
- Android：原生 Kotlin
- iOS：原生 Swift
- 小程序：原生微信小程序
- 文件存储：本地文件服务或对象存储

## 3.2 最终架构结论

当前项目最终明确的架构如下：

- 整体采用前后端分离架构
- 后端当前阶段采用单体应用 + 模块化分层实现
- 工程组织采用单仓库，多端独立工程，后端单体单工程
- 管理端为独立 PC Web 前端
- 用户端采用 Android 原生 App、iOS 原生 App 和微信小程序三端接入
- 三类前端统一复用后端核心业务 API
- 用户、认证、权限、组织能力复用 `auth-starter`
- 心理业务模块独立实现，包括量表、任务、答题、报告、预警、干预、预约、通知、统计
- 数据访问保持 JDBC 风格，与 `auth-starter` 一致，不默认使用 JPA/Hibernate

## 4. 通用要求

所有 Prompt 默认遵循：

- 复用 `auth-starter`，不要重复造认证权限体系
- 心理业务表统一使用 `psy_` 前缀
- 严格遵循现有 `doc` 文档中的业务闭环、角色权限、数据模型、接口设计
- 发现文档冲突时，先指出冲突，再给修正建议
