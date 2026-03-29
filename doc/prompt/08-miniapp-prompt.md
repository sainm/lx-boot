# 阶段 8：微信小程序 Prompt

```text
你现在是资深微信小程序工程师，请基于当前项目文档，为“高校/企业心理测评与预警系统”设计并实现微信小程序端。

重点参考：
- doc/02-role-and-permission-design.md
- doc/05-technical-architecture-design.md
- doc/06-page-and-module-design.md
- doc/13-api-design-detailed.md
- doc/15-openapi-draft.yaml

技术约束：
- 原生微信小程序
- 轻量化用户端
- 统一复用 auth-starter 认证契约

小程序范围：
1. 登录与身份绑定
2. 我的任务
3. 在线答题
4. 个人报告
5. 我的预约
6. 消息通知
7. 用户侧会话状态

必须遵守的实现约束：
1. 统一采用 accessToken + refreshToken 双 token 模型。
2. 统一复用：
   - POST /auth/login/password
   - POST /auth/token/refresh
   - POST /auth/logout
   - GET /api/v1/auth/me
3. 网络层统一遵守 401 -> refresh -> retry 原则。
4. 会话失效后需要重新登录，并对当前页面和待办入口做合理降级。
5. 小程序不实现 /auth-audit 管理审计页面。
6. 小程序只承载用户侧会话状态，不承载管理审计能力。
7. 需要考虑弱网下 token 刷新失败和答题暂存的协同策略。
8. 需要考虑消息通知与登录态联动，例如登录失效时预约提醒和任务提醒的降级策略。

你的任务：
1. 设计小程序页面结构和交互流程。
2. 优先保留最核心用户链路，避免过重设计。
3. 说明小程序与原生 App 的职责边界。
4. 给出网络请求、登录态管理、订阅消息方案。
5. 说明主包 2MB / 分包 20MB 限制下的分包策略。
6. 说明弱网场景下：
   - 答题暂存
   - token 刷新失败
   - 登录失效后的回退策略

预期产出物：
- 小程序页面结构
- 用户链路说明
- 会话与 token 管理方案
- 订阅消息方案
- 分包策略建议

上下文传入方式：
- 必传本 Prompt
- 建议附上阶段 3/4 的接口设计结果
- 建议附上 auth-starter 登录/刷新/退出契约摘要

输出要求：
- 强调轻量、快速触达
- 页面少而核心
- 适配现有接口设计
- 明确不实现管理侧认证审计页
```
