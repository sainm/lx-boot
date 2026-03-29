# backend

心理测评系统后端工程。

当前实现约束：

- 技术栈：`Kotlin + Spring Boot + Spring Security + Spring JDBC + PostgreSQL + Redis`
- 认证权限底座：`auth-starter`
- 工程形态：单体应用，按业务模块分包
- 当前优先实现：量表、任务、答题、评分、报告、预警主链路

## 当前已落地内容

- `auth-starter` 本地 composite build 集成
- 应用启动骨架
- 统一响应与异常处理
- 当前登录用户上下文适配
- 量表模块第一批接口：
  - `GET /api/v1/scales`
  - `POST /api/v1/scales`
  - `GET /api/v1/scales/{id}`

## 本地启动说明

1. 确保本机存在本工程可访问的本地 `auth-starter` 源码目录
2. 准备 PostgreSQL 数据库
3. 先初始化 `auth-starter` 所需认证权限表
4. 本工程启动时会自动初始化当前已实现的 `psy_*` 基础表
5. 根据环境覆盖 `spring.datasource.*` 和 `auth-module.*` 配置

## 下一步建议

1. 完成量表维度、题目、选项、计分规则的完整写入接口
2. 接入任务与分配模块
3. 接入答卷提交、自动评分、系统报告、预警生成
