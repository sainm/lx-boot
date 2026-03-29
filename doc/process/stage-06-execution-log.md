# 阶段 6 执行记录

阶段名称：Android 原生 App
执行日期：2026-03-27
使用 Prompt：`doc/prompt/06-android-app-prompt.md`

输入文档：
- `doc/06-page-and-module-design.md`
- `doc/13-api-design-detailed.md`
- `doc/15-openapi-draft.yaml`
- 

阶段目标：
- 确认 Android 主链路结构与弱网策略

本阶段输出：
- Android 项目结构
- 主链路页面流
- 网络层与状态管理建议
- 弱网恢复策略

确认结论：
- Android 端为独立原生工程
- 推荐技术栈：
  Kotlin + Jetpack Compose + ViewModel + Navigation + Retrofit/OkHttp
- 用户端只聚焦主链路，不承载复杂管理能力

Android 项目结构：

```text
android-app/
  app/
  core/
  feature-auth/
  feature-task/
  feature-assessment/
  feature-report/
  feature-appointment/
  feature-notification/
```

主链路页面流：

1. 登录
2. 我的任务
3. 答题页
4. 提交结果页
5. 个人报告页
6. 我的预约
7. 消息通知

网络层与状态管理建议：

1. 网络层
- Retrofit
- OkHttp
- 统一鉴权拦截器

2. 状态管理
- ViewModel
- UI State 驱动页面

3. 路由
- Navigation Compose

弱网恢复策略：

1. 答题中支持本地暂存
2. 断网时保留本地答案缓存
3. 恢复网络后允许继续作答或重新提交
4. 提交动作要做幂等控制，避免重复提交

当前 Android 阶段定位：

- 先完成用户主链路设计
- 代码实现要等后端主链路接口稳定后推进更顺
- 预约和通知页可以先做轻量壳层

发现问题：
- 如果过早接入复杂图表和统计，会偏离 Android 用户端主目标
- 答题弱网恢复与本地暂存是移动端关键体验点，不能后补
- 真正开始代码实现前，后端答卷和报告接口最好先稳定

需要回写的文档：
- `doc/process/03-current-progress-dashboard.md`
- `doc/process/01-prompt-driven-task-plan.md`

是否进入下一阶段：
- 是，阶段 6 设计已完成，后续可进入 iOS 原生 App 设计阶段

下一阶段准备事项：
- 执行 `doc/prompt/07-ios-app-prompt.md`
- 保持 Android 与 iOS 主链路一致

## 2026-03-28 会话基线补充

- Android 端后续实现需统一复用 `auth-starter`：
  - `POST /auth/login/password`
  - `POST /auth/token/refresh`
  - `POST /auth/logout`
  - `GET /api/v1/auth/me`
- Android 端统一采用 `accessToken + refreshToken` 双 token 模型。
- 需要本地记录 token 生命周期，并支持提前刷新。
- 网络层统一遵守 `401 -> refresh -> retry`。
- 会话失效后回登录页，并尽量保留原始返回路径。
- Android 端不实现 `/auth-audit` 管理审计页，只保留用户侧会话状态展示与轻量调试能力。
