# backend

心理测评系统后端工程，使用 Kotlin + Spring Boot。

## 当前状态

后端主链路已经覆盖：

- 量表与版本管理
- 测评任务、草稿、提交、评分、报告
- 预警、干预、复测任务
- 通知分发、设备登记、投递流水、失败重试
- 导出任务持久化、自动补跑、超时恢复、管理员重试

仍未完成的重点项：

- 真实 Push 厂商 SDK / FCM 直连
- 真实对象存储厂商 SDK 适配
- 完整设备会话治理

## 编译环境

- JDK 21
- Gradle Wrapper
- Kotlin / Spring Boot Toolchain 已固定到 Java 21

## 本地启动

```bash
./gradlew bootRun
```

## 文档入口

- [../doc/18-backend-roadmap.md](../doc/18-backend-roadmap.md)
- [../doc/process/03-current-progress-dashboard.md](../doc/process/03-current-progress-dashboard.md)
- [../doc/process/04-baseline-closure.md](../doc/process/04-baseline-closure.md)
