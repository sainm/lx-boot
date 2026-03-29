# 按 Prompt 推进任务清单

## 1. 任务说明

本任务清单将项目推进顺序明确为“按 Prompt 工作”，即每一个阶段都先执行对应 Prompt，再基于该阶段输出进入下一步实现。

Prompt 目录：
- `doc/prompt/00-prompt-index.md`
- `doc/prompt/implementation-master-prompt.md`
- `doc/prompt/01-architecture-and-plan-prompt.md`
- `doc/prompt/02-database-design-prompt.md`
- `doc/prompt/03-backend-core-prompt.md`
- `doc/prompt/04-backend-advanced-prompt.md`
- `doc/prompt/05-react-admin-prompt.md`
- `doc/prompt/06-android-app-prompt.md`
- `doc/prompt/07-ios-app-prompt.md`
- `doc/prompt/08-miniapp-prompt.md`
- `doc/prompt/09-testing-and-delivery-prompt.md`

## 2. 总体执行顺序

当前项目统一采用以下顺序推进：
1. 总纲理解
2. 架构与实现规划
3. 数据库设计确认
4. 后端核心主链路
5. 后端扩展能力
6. React 管理端
7. Android 原生 App
8. iOS 原生 App
9. 微信小程序
10. 测试与交付

## 3. 阶段任务清单

### 阶段 0：总纲理解

目标：
- 统一理解当前项目所有文档
- 形成总体实现路线和全局约束

执行 Prompt：
- `doc/prompt/implementation-master-prompt.md`

预期产出物：
- 总体理解摘要
- 核心约束列表
- 模块总览
- 风险清单

完成标记：
- [x] 已准备执行入口
- [x] 已执行
- [x] 已形成总结
- [x] 已确认后续顺序

### 阶段 1：架构与实现规划

目标：
- 明确 MVP 范围
- 明确模块边界
- 明确 4~6 周推进顺序

执行 Prompt：
- `doc/prompt/01-architecture-and-plan-prompt.md`

预期产出物：
- 模块划分方案
- MVP 清单
- 风险点
- 推进排期

完成标记：
- [x] 已执行
- [x] 已确认 MVP
- [x] 已确认阶段边界

### 阶段 2：数据库设计确认

目标：
- 确认数据库结构是否足够支撑业务闭环
- 确认表结构、索引、状态字典

执行 Prompt：
- `doc/prompt/02-database-design-prompt.md`

预期产出物：
- 数据库审查结果
- 字段调整建议
- 索引建议
- 最终建议版结构

完成标记：
- [x] 已执行
- [x] 已确认表结构
- [x] 已确认索引策略

### 阶段 3：后端核心主链路

目标：
- 落定主链路后端设计与实现顺序
- 确认 `auth-starter` 集成方式

执行 Prompt：
- `doc/prompt/03-backend-core-prompt.md`

预期产出物：
- 后端模块结构
- 主链路核心类设计
- `auth-starter` 集成方案
- 主链路接口实现顺序

完成标记：
- [x] 已执行
- [x] 已确认后端结构
- [x] 已确认主链路优先级

### 阶段 4：后端扩展能力

目标：
- 完善预警、预约、通知、统计、导出、审计

执行 Prompt：
- `doc/prompt/04-backend-advanced-prompt.md`

预期产出物：
- 扩展模块设计
- 异步处理建议
- 导出与通知方案

完成标记：
- [x] 已执行
- [x] 已确认扩展范围
- [x] 已确认异步边界

### 阶段 5：React 管理端

目标：
- 设计并推进管理端实现

执行 Prompt：
- `doc/prompt/05-react-admin-prompt.md`

预期产出物：
- React 项目结构
- 路由与权限方案
- UI 组件库选型
- 管理端页面优先级

完成标记：
- [x] 已执行
- [x] 已确认页面结构
- [x] 已确认权限展示策略

### 阶段 6：Android 原生 App

目标：
- 推进 Android 用户端主链路

执行 Prompt：
- `doc/prompt/06-android-app-prompt.md`

预期产出物：
- Android 项目结构
- 主链路页面流
- 网络层建议
- 弱网恢复策略

完成标记：
- [x] 已执行
- [x] 已确认页面流
- [x] 已确认本地暂存策略

### 阶段 7：iOS 原生 App

目标：
- 推进 iOS 用户端主链路

执行 Prompt：
- `doc/prompt/07-ios-app-prompt.md`

预期产出物：
- iOS 项目结构
- 主链路页面流
- 网络层建议
- iOS 合规与审核建议

完成标记：
- [x] 已执行
- [x] 已确认页面流
- [x] 已确认 iOS 特有要求

### 阶段 8：微信小程序

目标：
- 推进小程序轻量用户链路

执行 Prompt：
- `doc/prompt/08-miniapp-prompt.md`

预期产出物：
- 小程序页面结构
- 用户链路
- 订阅消息方案
- 分包策略

完成标记：
- [x] 已执行
- [x] 已确认轻量范围
- [x] 已确认消息策略

### 阶段 9：测试与交付

目标：
- 完整收敛测试、联调与交付方案

执行 Prompt：
- `doc/prompt/09-testing-and-delivery-prompt.md`

预期产出物：
- 测试分层方案
- 联调顺序
- CI/CD 建议
- 发布前检查清单

完成标记：
- [x] 已执行
- [x] 已确认测试范围
- [x] 已确认交付清单

## 4. 当前结论

当前项目按 Prompt 推进的文档规划阶段已经全部完成，后续执行方式正式定义为：

- 优先按 Prompt 顺序工作
- 每个阶段形成明确产出物
- 每个阶段完成后再进入下一阶段

## 5. 下一步建议

当前建议立即进入代码落地阶段：

1. 搭建 `backend/` 后端工程骨架
2. 搭建 `admin-web/` React 管理端工程骨架
3. 实现后端量表、任务、答题、评分、报告、预警主链路
4. 接入 React 管理端基础壳与主链路页面
