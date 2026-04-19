# backend

`backend` 是 `lx-boot` 的 Kotlin + Spring Boot 后端工程。

## 环境要求

- JDK 21
- Gradle Wrapper
- PostgreSQL
- Redis

## 重要前置条件

当前工程使用 Gradle composite build，在 [settings.gradle.kts](/D:/source/lx-boot/backend/settings.gradle.kts) 中通过 `includeBuild("../../auth-starter")` 引入兄弟仓库。

这意味着本地或 CI 构建时，目录结构需要保持为：

```text
<workspace>/
├─ auth-starter/
└─ lx-boot/
   └─ backend/
```

如果缺少同级的 `auth-starter` 仓库，`backend` 无法独立完成编译。

## 本地启动

```bash
./gradlew bootRun
```

默认配置文件在 [src/main/resources/application.yml](/D:/source/lx-boot/backend/src/main/resources/application.yml)。

## 当前已覆盖能力

- 量表与版本管理
- 测评任务、草稿、提交、评分、报告
- 预警、干预、复测任务
- 通知分发、投递流水、失败重试
- 导出任务持久化、补跑、超时恢复
- 设备登记、设备画像、设备自动处置
- 会话治理与认证域设备接口联动

## Push 回执接口

- `POST /api/v1/my/notifications/deliveries/{deliveryId}/received`
- `POST /api/v1/my/notifications/deliveries/{deliveryId}/clicked`
- `POST /api/v1/notifications/deliveries/{deliveryId}/callbacks`

说明：

- App 可用 push payload 里的 `deliveryId` 上报送达和点击
- 运维回调允许单调升级投递状态，不再允许把终态回写成低级状态

## 认证与设备治理

认证与设备治理主入口已经统一到 `auth-starter`：

- `GET /auth/me/sessions`
- `POST /auth/me/sessions/{sessionId}/revoke`
- `POST /auth/me/sessions/revoke-others`
- `GET /auth/me/session-policy`
- `POST /auth/me/session-policy`
- `GET /auth/me/devices`
- `POST /auth/me/devices`
- `POST /auth/me/devices/{deviceId}/deactivate`
- `GET /auth/users/{userId}/devices`
- `POST /auth/users/{userId}/devices/{deviceId}/deactivate`

## 相关文档

- [18-backend-roadmap.md](/D:/source/lx-boot/doc/18-backend-roadmap.md)
- [20-linux-deployment-guide.md](/D:/source/lx-boot/doc/20-linux-deployment-guide.md)
- [21-windows-development-environment-guide.md](/D:/source/lx-boot/doc/21-windows-development-environment-guide.md)
- [03-current-progress-dashboard.md](/D:/source/lx-boot/doc/process/03-current-progress-dashboard.md)
- [04-baseline-closure.md](/D:/source/lx-boot/doc/process/04-baseline-closure.md)
