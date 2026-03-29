# 阶段 7：iOS 原生 App Prompt

```text
你现在是资深 iOS 工程师，请基于当前项目文档，为“高校/企业心理测评与预警系统”设计并实现 iOS 原生 App。

重点参考：
- doc/01-project-overview-and-scope.md
- doc/02-role-and-permission-design.md
- doc/05-technical-architecture-design.md
- doc/06-page-and-module-design.md
- doc/13-api-design-detailed.md
- doc/15-openapi-draft.yaml

技术约束：
- iOS 原生 Swift
- 用户端为主，不做复杂管理后台
- 统一复用 auth-starter 认证契约

iOS App 范围：
1. 登录与身份接入
2. 我的测评任务
3. 在线答题
4. 个人报告
5. 我的预约
6. 消息通知
7. 会话状态/调试信息

必须遵守的实现约束：
1. 登录、刷新、退出统一复用：
   - POST /auth/login/password
   - POST /auth/token/refresh
   - POST /auth/logout
   - GET /api/v1/auth/me
2. token 采用 accessToken + refreshToken 双 token 模型。
3. 本地需要保存 token 生命周期信息，并在接近过期时提前刷新。
4. 网络层统一遵守 401 -> refresh -> retry 原则。
5. 会话失效后必须回到登录页，并保留原始返回路径。
6. iOS 端不实现 /auth-audit 管理审计页面。
7. 可以提供轻量会话状态页，展示 token 剩余时间、最近同步时间与当前登录用户。
8. 会话与调试页面中的敏感信息展示必须受隐私策略限制，不默认暴露完整 token 内容。

你的任务：
1. 设计 iOS 项目结构、页面结构和网络层方案。
2. 规划主链路页面与数据流。
3. 输出推荐技术方案，例如 SwiftUI / UIKit、NavigationStack、MVVM、URLSession / Alamofire。
4. 说明如何处理权限控制与敏感数据展示限制。
5. 说明弱网环境下：
   - 答题暂存
   - token 刷新失败后的恢复
   - 断网重试
6. 说明 iOS 特有关注点：
   - Privacy Manifest
   - 隐私权限说明
   - 心理健康类 App 的审核敏感点
   - App Store 上架注意事项

预期产出物：
- iOS 项目结构
- 主链路页面流
- 网络层与状态管理建议
- 会话与 token 管理方案
- 弱网恢复策略
- iOS 平台特有合规建议

上下文传入方式：
- 必传本 Prompt
- 建议附上阶段 3/4 的接口设计结论
- 建议附上 auth-starter 登录/刷新/退出契约摘要

输出要求：
- 面向原生实现
- 优先主链路
- 与后端接口文档保持一致
- 明确区分用户侧会话页和管理侧认证审计页
```
