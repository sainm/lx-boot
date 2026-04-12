# 阶段 7 执行记录

> 历史说明：本文件是 iOS 端规划记录，不代表 iOS 已在当前仓库中落地。当前真实状态是 iOS 仍未开始实现。

阶段名称：iOS 原生 App
执行日期：2026-03-27
使用 Prompt：`doc/prompt/07-ios-app-prompt.md`

输入文档：
- `doc/06-page-and-module-design.md`
- `doc/13-api-design-detailed.md`
- `doc/15-openapi-draft.yaml`
- 

阶段目标：
- 确认 iOS 主链路结构、弱网策略与平台特有要求

本阶段输出：
- iOS 项目结构
- 主链路页面流
- 网络层与状态管理建议
- iOS 合规与审核建议

确认结论：
- iOS 端为独立原生工程
- 推荐技术栈：
  Swift + SwiftUI + URLSession/Alamofire + MVVM
- 用户端与 Android 保持相同主链路，但补充 iOS 特有要求

iOS 项目结构：

```text
ios-app/
  App/
  Core/
  Features/Auth/
  Features/Task/
  Features/Assessment/
  Features/Report/
  Features/Appointment/
  Features/Notification/
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
- URLSession 或 Alamofire
- 统一鉴权与错误处理

2. 状态管理
- MVVM
- SwiftUI 状态驱动页面

3. 页面导航
- SwiftUI NavigationStack

弱网恢复策略：

1. 答题过程支持本地暂存
2. 断网时保持本地状态
3. 恢复后继续作答或重提
4. 提交接口配合后端做幂等控制

iOS 平台特有要求：

1. 注意 Privacy Manifest
2. 明确隐私数据采集说明
3. 心理健康类 App 页面文案避免误导为医疗诊断
4. App Store 审核时需要清晰说明用途、数据处理和用户授权

当前 iOS 阶段定位：

- 与 Android 一样聚焦用户主链路
- 先保证流程一致，再考虑平台细节优化

发现问题：
- 心理健康类应用在 iOS 审核中更容易被关注文案和隐私说明，必须提前处理
- 如果与 Android 主链路分叉过大，会增加后续多端维护成本
- 真正开始实现前，建议以后端稳定接口为基准

需要回写的文档：
- `doc/process/03-current-progress-dashboard.md`
- `doc/process/01-prompt-driven-task-plan.md`

是否进入下一阶段：
- 是，阶段 7 设计已完成；在当时的规划中，后续会进入微信小程序阶段

下一阶段准备事项：
- 执行 `doc/prompt/08-miniapp-prompt.md`
- 控制小程序范围为轻量主链路

## 2026-03-28 会话基线补充

- iOS 端后续实现需统一复用 `auth-starter`：
  - `POST /auth/login/password`
  - `POST /auth/token/refresh`
  - `POST /auth/logout`
  - `GET /api/v1/auth/me`
- iOS 端统一采用 `accessToken + refreshToken` 双 token 模型。
- 需要本地记录 token 生命周期，并支持提前刷新。
- 网络层统一遵守 `401 -> refresh -> retry`。
- 会话失效后回登录页，并尽量保留原始返回路径。
- iOS 端不实现 `/auth-audit` 管理审计页，只保留用户侧会话状态展示与轻量调试能力。
