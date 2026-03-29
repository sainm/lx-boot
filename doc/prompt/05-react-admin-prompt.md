# 阶段 5：React 管理端 Prompt

```text
你现在是资深 React 前端工程师，请基于当前项目文档设计并实现心理测评系统的管理端。

重点参考：
- doc/02-role-and-permission-design.md
- doc/06-page-and-module-design.md
- doc/07-data-privacy-and-security.md
- doc/13-api-design-detailed.md
- doc/15-openapi-draft.yaml

技术约束：
- React + TypeScript
- 管理端面向 PC Web
- 需要支持角色差异化菜单与权限控制
- 需要明确 UI 组件库选型

管理端范围：
1. 测评管理员后台
2. 咨询师工作台
3. 学校/企业管理人员群体统计页面
4. 系统管理员页面

你的任务：
1. 设计 React 项目结构、路由结构、状态管理建议。
2. 说明 UI 组件库选型建议，如 Ant Design 或其他方案。
3. 设计角色驱动的菜单与页面权限控制。
4. 说明前端数据脱敏策略和敏感操作二次确认设计。
5. 输出页面实现优先级，先做主链路页面。
6. 给出与后端 API 的对接建议。
7. 图表展示优先考虑统计看板、群体报告、个人与群体对比。

预期产出物：
- React 项目结构建议
- 路由与权限方案
- UI 组件库选型建议
- 前端脱敏与敏感操作方案

上下文传入方式：
- 必传本 Prompt
- 建议附上阶段 3/4 的后端接口结论

输出要求：
- 结构化
- 面向实现
- 优先考虑可迭代开发顺序
```
