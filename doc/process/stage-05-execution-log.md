# 阶段 5 执行记录

> 历史说明：本文件记录的是 2026-03-27 的阶段设计结论，不代表当前仓库的实时实现状态。当前真实状态请以 `03-current-progress-dashboard.md` 和 `04-baseline-closure.md` 为准。

阶段名称：React 管理端
执行日期：2026-03-27
使用 Prompt：`doc/prompt/05-react-admin-prompt.md`

输入文档：
- `doc/02-role-and-permission-design.md`
- `doc/06-page-and-module-design.md`
- `doc/13-api-design-detailed.md`
- 

阶段目标：
- 确认管理端结构、权限、页面优先级

本阶段输出：
- React 项目结构建议
- 路由与权限方案
- UI 组件库选型建议
- 管理端页面优先级

确认结论：
- React 管理端采用独立工程实现
- 推荐技术栈：
  React + TypeScript + React Router + TanStack Query
- 推荐 UI 组件库：
  Ant Design
- 当前阶段不建议过度自定义设计系统，优先保证开发效率和后台场景适配

React 项目结构建议：

```text
admin-web/
  src/
    app/
    router/
    pages/
    features/
    components/
    layouts/
    services/
    hooks/
    store/
    utils/
    types/
```

路由与权限方案：

1. 路由分层
- 登录页
- 管理端壳层
- 按角色加载的业务页面

2. 页面路由建议
- `/login`
- `/dashboard`
- `/scales`
- `/tasks`
- `/warnings`
- `/group-reports`
- `/appointments`
- `/users`
- `/roles`

3. 权限控制建议
- 菜单按角色显示
- 路由按角色校验
- 页面按钮按权限点控制
- 敏感数据在展示层做脱敏

UI 组件库选型建议：

推荐：

- Ant Design

原因：

1. 适合管理后台
2. 表格、表单、弹窗、分页成熟
3. 与 React + TypeScript 配合稳定
4. 适合快速搭建统计看板和后台页面

前端数据脱敏与敏感操作建议：

1. 个体敏感信息默认不在列表页完全展开
2. 群体报告页默认以汇总展示为主
3. 查看敏感报告、导出、状态变更等动作增加二次确认
4. 管理端仅控制展示，真正权限由后端接口兜底

管理端页面优先级：

第一优先级：

- 登录与应用壳
- 角色驱动菜单
- 量表管理页
- 测评任务管理页

第二优先级：

- 基础统计看板
- 预警列表页

第三优先级：

- 群体报告页
- 预约管理页
- 用户组织与角色管理页

与后端对接建议：

1. 主链路页面优先对接：
- 量表
- 任务
- 题目与结果
- 预警

2. 统一服务层封装 API
3. 用 TanStack Query 管理查询与缓存
4. 错误提示与权限失败统一处理

当前管理端阶段定位：

- 第一阶段只要求支撑后台主链路
- 统计能力保持轻量
- 不急于一次性做完所有管理页面

发现问题：
- 预警、统计、预约这三块页面容易继续膨胀，当前阶段应控制在“能支撑主链路管理”即可
- React 管理端需要等待后端主链路接口相对稳定后再进入深度联调
- 群体报告图表可以先做轻量版，不宜过早进入复杂 BI 化

需要回写的文档：
- `doc/process/03-current-progress-dashboard.md`
- `doc/process/01-prompt-driven-task-plan.md`

是否进入下一阶段：
- 是，阶段 5 设计已完成；在当时的规划中，后续会进入 Android 原生 App 设计阶段

下一阶段准备事项：
- 执行 `doc/prompt/06-android-app-prompt.md`
- 或并行开始 React 管理端工程骨架搭建
