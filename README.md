# lx-boot

心理测评与预警系统仓库，面向高校心理中心、辅导员体系和企业 EAP 场景。

## 当前状态

截至 2026-08-08，仓库已经进入可持续迭代的工程基线阶段：

- `admin-web` 可构建
- `backend` 关键测试可执行
- 管理端、用户侧 Web、导出、通知、预警、预约、审计等主链路均已有第一版闭环
- Android 被测者端已覆盖登录、任务、答题、报告、预约、通知、三语界面与 FCM 设备注册

仍未完成的重点项：

- iOS / 小程序端
- Android 正式签名、商店发布和 FCM 生产参数配置（后端 FCM HTTP v1 与 Android 客户端接入均已完成第一版）
- 真实对象存储厂商 SDK 适配
- 设备会话治理增强（基础能力已下沉到 auth-starter，后续重点是租户级 / 用户级风控、刷新令牌治理与异常设备处置策略）

## 目录

```text
lx-boot/
├─ backend/      Spring Boot 后端
├─ admin-web/    React 管理端与用户侧 Web
└─ doc/          设计文档与当前状态文档
```

## 文档入口

优先阅读：

- [doc/00-document-index.md](doc/00-document-index.md)
- [doc/process/03-current-progress-dashboard.md](doc/process/03-current-progress-dashboard.md)
- [doc/process/04-baseline-closure.md](doc/process/04-baseline-closure.md)
- [doc/18-backend-roadmap.md](doc/18-backend-roadmap.md)

## 编译环境

- JDK 21
- Node.js 24.14.1（与当前本地开发机保持一致）
- 仓库根目录提供 `.java-version` 与 `.nvmrc`
- 后端通过 Gradle Toolchain 固定到 Java 21

建议首次进入仓库后先清理一次本地产物，再执行构建。

## 本地启动

### 后端

```bash
cd backend
./gradlew bootRun
```

### 前端

```bash
cd admin-web
npm install
npm run dev
```
