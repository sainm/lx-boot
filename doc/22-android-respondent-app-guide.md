# 22 Android 被测者端开发说明

## 22.1 目标范围

当前新增独立工程 `android-app/`，只面向普通用户（`USER` / 被测者）提供以下能力：

- 登录
- 首页总览
- 我的任务
- 答题提交
- 我的报告
- 报告详情
- 预约咨询
- 通知消息

不包含后台管理、用户管理、会话详情、预警处置、通知运维等管理端功能。

## 22.2 工程位置

工程目录：

```text
android-app/
```

当前使用：

- Jetpack Compose
- Navigation Compose
- Retrofit + OkHttp
- Kotlinx Serialization
- Firebase Cloud Messaging
- Android Keystore（AES-GCM 会话令牌加密）

界面跟随系统语言支持：

- 中文
- 日本語
- English

包名：

```text
org.sainm.psy.respondent
```

## 22.3 接口映射

安卓端直接复用现有后端接口：

- `POST /auth/login/password`
- `POST /auth/token/refresh`
- `POST /auth/logout`
- `POST /auth/me/devices`
- `GET /api/v1/my/tasks`
- `GET /api/v1/my/tasks/{taskId}/questions`
- `POST /api/v1/answer-sheets/save`
- `POST /api/v1/answer-sheets/submit`
- `GET /api/v1/reports/my`
- `GET /api/v1/reports/{reportId}`
- `GET /api/v1/counselors`
- `GET /api/v1/counselors/{counselorId}/schedules`
- `GET /api/v1/appointments/my`
- `POST /api/v1/appointments`
- `POST /api/v1/appointments/{appointmentId}/cancel`
- `GET /api/v1/my/notifications`
- `POST /api/v1/my/notifications/{notificationId}/read`
- `POST /api/v1/my/notifications/deliveries/{deliveryId}/received`

## 22.4 服务地址配置

默认服务地址配置在：

```text
android-app/gradle.properties
```

当前默认值：

```properties
lxPsychologyApiBaseUrl=http://10.0.2.2:8090/
```

说明：

- Android 模拟器访问宿主机后端时，通常使用 `10.0.2.2`
- 如果使用真机联调，应改成实际可访问的后端地址，例如部署服务器 IP

例如：

```properties
lxPsychologyApiBaseUrl=https://example.com/
```

## 22.5 当前实现状态

已经完成：

- 独立安卓工程骨架
- Gradle/AGP 基础配置
- 登录态本地存储
- Access Token 自动附带
- Refresh Token 自动续期基础逻辑
- 会话令牌通过 Android Keystore + AES-GCM 加密保存，并自动迁移旧明文数据
- 被测者端底部导航骨架
- 首页、任务、报告、预约、通知页面骨架
- 题目页面基础答题与保存/提交链路
- 中、日、英三语界面与客户端校验提示
- FCM Token 注册、Token 更新、通知展示、送达回执与业务页面跳转
- Release 禁止明文 HTTP，Debug 保留模拟器本地 HTTP 联调
- GitHub Actions Android 单元测试与 Debug APK 构建

发布前仍需完成环境相关工作：

- 报告页图表化表达
- 真机构建、签名与安装包发布流程
- 配置生产 HTTPS 地址与 Firebase 项目参数

## 22.6 当前限制

Android 构建不再依赖版本库中的 `local.properties`。本地需要安装 Android SDK 并通过 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 或未纳入版本控制的 `local.properties` 指定路径。

Release 构建拒绝明文 HTTP；只有 Debug 构建允许使用模拟器的 `http://10.0.2.2:8090/`。

FCM 使用 Gradle 属性注入配置，不提交密钥：

```properties
lxFirebaseApplicationId=...
lxFirebaseApiKey=...
lxFirebaseProjectId=...
lxFirebaseSenderId=...
```

建议使用 Android Studio 打开：

```text
android-app/
```

然后再完成：

1. SDK 同步
2. Gradle Sync
3. 模拟器或真机联调
4. Debug APK 构建

## 22.7 当前构建结果

历史上已在 Windows 环境完成：

- `.\gradlew.bat :app:assembleDebug`

构建产物：

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

应用包名：

```text
org.sainm.psy.respondent
```

## 22.8 安装与启动

如果设备已经通过 USB 或模拟器连接，可在 `android-app/` 目录执行：

```powershell
.\gradlew.bat :app:installDebug
```

如果想直接安装并启动，也可以执行：

```powershell
.\install-debug.ps1
```

或：

```bat
run-debug.bat
```

历史版本曾验证：

- 工程可成功编译
- 如果没有连接设备，`installDebug` 会报 `No connected devices!`

本轮新增安全存储、三语和 FCM 后，当前 macOS 因缺少 Android SDK 无法重跑 APK；应以新增 CI 结果或装有 SDK 的开发机再次验证为准。

## 22.9 当前本机状态

2026-08-08 当前 macOS 环境已确认 JDK 21 位于 `/opt/homebrew/opt/openjdk@21`，但没有发现可用 Android SDK，因此本轮无法在本机重跑 APK 构建。仓库 CI 已新增 `testDebugUnitTest assembleDebug`，用于在具备 SDK 的干净环境持续验证。

## 22.10 建议补齐方式

在 Android Studio 中打开 `SDK Manager` / `Device Manager`，至少补齐以下内容：

1. 安装一个系统镜像  
   建议：
   - Android 35
   - `Google APIs x86_64` 或 `Google Play x86_64`

2. 安装 Android SDK Command-line Tools (latest)

3. 在 Device Manager 中创建一个 AVD  
   例如：
   - Pixel 6
   - Android 35

4. 启动模拟器后，在 `android-app/` 目录执行：

```powershell
.\install-debug.ps1
```

## 22.11 环境检查脚本

已补充环境检查脚本：

```powershell
.\check-android-env.ps1
```

它会检查：

- SDK 根目录
- `platform-tools`
- `emulator`
- Android 平台
- `cmdline-tools/latest`
- `system-images`
- 当前在线设备
- 当前 AVD 列表
