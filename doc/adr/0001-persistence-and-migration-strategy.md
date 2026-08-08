# ADR-0001：保留显式 JDBC，并引入受控 Flyway 迁移

- 状态：Accepted
- 日期：2026-08-08
- 范围：backend 持久化与 PostgreSQL 结构治理

## 背景

当前后端有 233 处 JDBC 查询/更新调用，并广泛使用动态条件、聚合、PostgreSQL JSONB、部分索引、条件更新和行锁。主要问题是迁移不可追踪、超大 Repository、时间与映射样板分散，而不是缺少 ORM。

## 决策

1. 保留 Spring JDBC/NamedParameterJdbcTemplate 作为默认持久化方案。
2. 复杂统计、多租户条件、锁和批量写入继续使用可审查的显式 SQL。
3. 用 Flyway 管理整个应用数据库版本；禁止自动 baseline 和 clean。
4. 后续先拆分 Command Repository、Query Repository、RowMapper 和分页组件，再决定是否局部试点其他查询技术。
5. 任何试点只能选择低风险模块，并用相同 PostgreSQL Case 比较代码量、p95、租户条件可见性和测试成本。

## 未采用或暂缓

- JPA：当前不是标准实体 CRUD 占绝对多数，隐式加载与全局过滤器还会降低租户条件可见性，因此拒绝全量迁移。
- MyBatis：可能减少参数映射样板，但尚无数据证明能改善核心复杂查询，暂缓；未来可在通知策略等低风险 CRUD 模块试点。
- jOOQ：类型安全和生成代码有价值，但需要数据库代码生成与 CI 产物治理，暂缓到 Flyway 稳定后评估。
- JDBC、MyBatis、JPA 无边界混用：拒绝。

## 结果

优点是 API 与 SQL 行为保持兼容、迁移路径可验证、多租户条件仍显式可见。代价是短期仍需治理 JDBC 样板代码，并为 PostgreSQL 集成测试维护真实数据库环境。

## 回滚

Flyway 默认关闭，因此应用代码可先回滚而不执行迁移。已经执行的迁移不回改；通过新增前滚迁移或从已验证备份恢复。
