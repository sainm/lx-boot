# 国际化接入指南

## 1. 目标

本项目当前采用中英双语：

- 前端支持 `zh-CN` / `en-US`
- 后端基于 `Accept-Language` 返回本地化文案
- 导出、通知、报告、业务异常、参数校验统一走消息资源

新增页面、接口或业务提示时，应默认按国际化方式落地，不再直接写死中文或英文。

## 2. 前端约定

### 2.1 文案存放位置

前端文案统一放在：

- [admin-web/src/i18n/messages.ts](/d:/source/lx-boot/admin-web/src/i18n/messages.ts:1)

当前支持：

- `zh-CN`
- `en-US`

建议命名方式：

- 页面标题：`dashboard.title`
- 页面副标题：`dashboard.subtitle`
- 列名：`dashboard.col.task`
- 操作按钮：`warnings.assign`
- 通用状态：`status.COMPLETED`
- 通用提示：`session.expiredMessage`

### 2.2 页面中如何使用

页面或组件通过：

- [admin-web/src/i18n/provider.tsx](/d:/source/lx-boot/admin-web/src/i18n/provider.tsx:1)

使用方式：

```tsx
import { useI18n } from "../i18n/provider";

export function ExamplePage() {
  const { t } = useI18n();
  return <h1>{t("dashboard.title")}</h1>;
}
```

带参数时：

```tsx
t("notifications.unreadSummary", { count: 3 })
```

### 2.3 Locale 持久化

前端当前把语言保存到：

- `localStorage["psy-admin-web.locale"]`

相关实现：

- [admin-web/src/i18n/messages.ts](/d:/source/lx-boot/admin-web/src/i18n/messages.ts:1)
- [admin-web/src/i18n/provider.tsx](/d:/source/lx-boot/admin-web/src/i18n/provider.tsx:1)

### 2.4 请求头透传

前端请求会自动附带：

- `Accept-Language: zh-CN`
- 或 `Accept-Language: en-US`

实现位置：

- [admin-web/src/services/http.ts](/d:/source/lx-boot/admin-web/src/services/http.ts:1)

新增 API 请求时，优先复用 `http` 实例，不要绕开这层。

## 3. 后端约定

### 3.1 消息资源位置

后端消息资源统一放在：

- [backend/src/main/resources/i18n/messages.properties](/d:/source/lx-boot/backend/src/main/resources/i18n/messages.properties:1)
- [backend/src/main/resources/i18n/messages_zh_CN.properties](/d:/source/lx-boot/backend/src/main/resources/i18n/messages_zh_CN.properties:1)

约定：

- `messages.properties` 作为英文默认文案
- `messages_zh_CN.properties` 作为中文文案

### 3.2 获取文案方式

服务层和仓储层统一通过：

- [backend/src/main/kotlin/org/sainm/psy/common/i18n/LocalizedMessages.kt](/d:/source/lx-boot/backend/src/main/kotlin/org/sainm/psy/common/i18n/LocalizedMessages.kt:1)

示例：

```kotlin
throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
```

带参数：

```kotlin
messages.get("appointment.created.content", appointmentId)
```

### 3.3 Locale 解析方式

后端当前使用：

- [backend/src/main/kotlin/org/sainm/psy/common/i18n/I18nConfig.kt](/d:/source/lx-boot/backend/src/main/kotlin/org/sainm/psy/common/i18n/I18nConfig.kt:1)

默认行为：

- 读取请求头 `Accept-Language`
- 写入 `LocaleContextHolder`
- `LocalizedMessages` 从当前线程 locale 取值

### 3.4 适用范围

以下内容应优先走消息资源：

- `BizException` 的 message
- 通知标题与内容
- 报告默认标题与总结文案
- 导出文本 / PDF 固定字段
- DTO 参数校验消息
- 统计看板卡片标题

不强制国际化的内容：

- 仅开发内部使用的临时调试日志
- 不直接给用户展示的技术性常量

## 4. DTO 校验文案规范

DTO 校验注解不要再写死文本，统一写消息键：

```kotlin
@field:NotBlank(message = "{validation.task_name_required}")
```

对应资源文件：

```properties
validation.task_name_required=Task name is required.
```

```properties
validation.task_name_required=任务名称不能为空
```

这样 Spring 校验异常会自动按 locale 解析。

## 5. 导出与异步任务

导出链路已经支持按 locale 输出，关键实现：

- [backend/src/main/kotlin/org/sainm/psy/export/service/ExportService.kt](/d:/source/lx-boot/backend/src/main/kotlin/org/sainm/psy/export/service/ExportService.kt:1)
- [backend/src/main/kotlin/org/sainm/psy/export/api/ExportController.kt](/d:/source/lx-boot/backend/src/main/kotlin/org/sainm/psy/export/api/ExportController.kt:1)

注意事项：

- 同步导出直接使用当前请求 locale
- 异步导出提交任务时要显式把 locale 传进后台线程
- 新增异步业务任务时，如果会生成用户可见文案，也要保留 locale

## 6. 新增文案时的推荐流程

1. 先定义稳定 key，避免直接用中文做 key。
2. 在 `messages.properties` 增加英文默认值。
3. 在 `messages_zh_CN.properties` 增加中文值。
4. 前端使用 `t("...")`，后端使用 `messages.get("...")`。
5. 如果涉及异步任务，确认 locale 不会在线程切换时丢失。
6. 跑构建和测试验证。

## 7. Key 命名建议

推荐按领域分组：

- `app.*`
- `locale.*`
- `session.*`
- `login.*`
- `dashboard.*`
- `myTasks.*`
- `myReports.*`
- `notifications.*`
- `groupReports.*`
- `tasks.*`
- `warnings.*`
- `scales.*`
- `authAudit.*`
- `export.*`
- `intervention.*`
- `error.*`
- `validation.*`

避免：

- `msg1`
- `test.label`
- 同时混用中划线和点号层级

## 8. 验证方式

前端验证：

- 执行 `npm run build`
- 手动切换语言，检查标题、按钮、表格列、错误提示是否同步变化

后端验证：

- 执行 `./gradlew test --rerun-tasks`
- 对同一接口分别用 `Accept-Language: zh-CN` 和 `Accept-Language: en-US` 调用
- 检查异常消息、校验错误、通知文案、导出内容是否切换

## 9. 当前已完成范围

截至 2026-04-11，已完成：

- 前端国际化基础设施
- 管理端主要页面双语
- 用户主链路页面双语
- 后端业务异常双语
- 通知、报告、导出双语
- DTO 参数校验双语

后续新增功能默认应按本指南执行，不再补做“二次国际化”。
