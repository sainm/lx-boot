# 阶段 3：后端核心主链路 Prompt

```text
你现在是资深 Kotlin/Spring Boot 后端工程师。请基于当前项目文档，优先实现心理测评系统的主链路功能。

重点参考：
- doc/02-role-and-permission-design.md
- doc/10-database-table-design.md
- doc/11-database-ddl-draft.sql
- doc/13-api-design-detailed.md
- doc/15-openapi-draft.yaml

技术约束：
- Kotlin + Spring Boot
- PostgreSQL
- Spring JDBC + JdbcTemplate / NamedParameterJdbcTemplate
- 基于 auth-starter 扩展认证与权限

主链路功能范围：
1. 量表管理
2. 测评任务管理
3. 按组/按个人分配任务
4. 在线答题与提交
5. 自动评分
6. 系统自动报告生成
7. 预警生成

你的任务：
1. 设计后端包结构、模块结构和核心类。
2. 生成核心实体、Repository、Service、Controller 设计方案。
3. 说明 auth-starter 的集成方式、依赖引入方式和扩展点。
4. 明确每个模块的输入输出与职责。
5. 如果开始写代码，优先从主链路开始，不要先做边缘模块。
6. 给出接口实现顺序和测试建议。

预期产出物：
- 后端模块结构
- auth-starter 集成方案
- 主链路核心类设计
- 主链路接口实现顺序

上下文传入方式：
- 必传本 Prompt
- 必传数据库与接口文档
- 建议附上阶段 2 的数据库审查结论

输出要求：
- 面向代码实现
- 不要停留在概念层
- 如果发现接口和表结构不一致，先指出
```
