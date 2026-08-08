# 技术架构基线、风险与优化计划

- 测量日期：2026-08-08
- 分支：`feature/redo`
- 基线提交：`9baa3c0`
- 原则：只记录代码、配置、构建和运行证据；未测量项不推断为已完成。

## 1. 模块基线矩阵

| 模块 | 技术栈 | 规模 | 主要职责 | 当前风险 | 建议 | 优先级 |
|---|---|---:|---|---|---|---|
| Backend | Kotlin 2.1、Spring Boot 3.4、JDBC、PostgreSQL、Redis | 112 文件 / 17,586 行 | API、评分、任务、报告、预警、咨询、通知、导出 | 单体初始化 SQL、超大 Repository/Service、时钟分散、租户边界不完整 | Flyway、PG 集成测试、按职责渐进拆分 | P0/P1 |
| Admin Web | React 19、TypeScript、Ant Design、React Query、ECharts | 73 文件 / 20,813 行 | 管理与咨询工作台 | 页面与语言目录过大；图表与主包较大；无真实 E2E | 拆页面/语言域、细化图表按需加载、补业务 Case E2E | P1 |
| Android | Kotlin、Jetpack Compose、Retrofit | 7 文件 / 2,223 行；1 app 模块；9 Screen；0 ViewModel | 被测者登录、任务、答题、报告、预约、通知 | 1,600 行单文件、无测试、令牌曾明文存储、本机 SDK 配置被提交 | Keystore、ViewModel/状态分层、CI lint/test/assemble | P0/P1 |
| auth-starter | Kotlin/Spring 独立相邻仓库 | 构建时 composite include | 用户、会话、租户、角色、SSO | CI 默认分支漂移；数据库结构与应用发布未统一版本化 | 固定提交，应用 V1 统一冻结结构 | P1 |
| PostgreSQL | PostgreSQL 18.4 本机；显式 SQL、Flyway V1-V8 | 本机 public 46 表；当前 V1 44 表 | 认证与心理业务主数据 | 历史库需显式 baseline 与租户归属预检查 | 不可变迁移、差异审批、分阶段约束验证 | P0 |
| CI | GitHub Actions | Web + Backend，改造后增加 PostgreSQL + Android | 编译、测试、构建 | 改造前后端只跑 H2/Mockito，Android不构建 | 空库/升级迁移双路径、Android lint/test/assemble | P0 |

## 2. 规模与复杂度

### 2.1 超大文件

| 行数 | 文件 | 风险 |
|---:|---|---|
| 4,237 | `admin-web/src/i18n/messages.ts` | 单目录冲突与全量加载 |
| 2,241 | `admin-web/src/pages/ScaleListPage.tsx` | 表单、导入、维护职责耦合 |
| 1,600 | `android-app/.../PsyRespondentApp.kt` | 9 个 Screen 与状态集中，无 ViewModel |
| 1,060 | `ScaleRepository.kt` | 读写、映射、版本操作耦合 |
| 965 | `AuthAuditPage.tsx` | 查询、图表、详情职责耦合 |
| 908 | `NotificationPage.tsx` | 策略、投递、运维职责耦合 |
| 905 | `TaskQuestionPage.tsx` | 答题状态与题型渲染耦合 |
| 824 | `ScaleImportService.kt` | 解析、校验、确认、落库耦合 |
| 804 | `AnswerSheetRepository.kt` | 答卷写入、查询、评分数据混合 |

全工程业务代码中：超过 500 行 20 个，超过 800 行 9 个，超过 1,000 行 4 个（不计 `wc total` 汇总行）。

### 2.2 后端结构与 SQL

- Controller 17、Service 27、Repository 14、Worker/Component 12。
- `@Transactional` 54 处、`@Scheduled` 6 处、`@Async` 1 处。
- JDBC 查询/更新调用 233 处；静态扫描到 PostgreSQL 专属语法/DDL命中 148 处。
- 动态 SQL 构造文件 10 个，`batchUpdate` 7 处。
- 单体心理 schema：29 表、59 索引、8 个显式唯一索引、44 外键、0 个显式 CHECK。
- V1 合并认证后为 44 张应用表；本机 public 实际 46 张，额外包含 `psy_appointment_status_log` 与 `psy_email_verification_token`。
- 本机任务、答卷、预警、结果、导出、通知投递和预约表当前均为 0 行，且 `pg_stat_statements` 未启用；因此不能把本机空表延迟包装成 p50/p95/p99 性能结论。

结论：复杂统计、锁、部分索引与批量写入占比足以支持继续保留显式 SQL；没有全量 ORM 重写证据。

## 3. 测试、构建与可观测性

| 类型 | 基线结果 | 说明 |
|---|---|---|
| Backend 单元/契约 | 当前 290 个，0 失败，3 跳过 | Java 21 完整测试已通过；3 个 PostgreSQL Case 仅在显式环境开关下运行 |
| PostgreSQL 集成 | 改造前 0 | 新增 Flyway 空库、baseline 升级和通知重试 Case；本机以隔离 schema 实跑 V1-V8 成功 |
| Web 单元/组件 | 5 文件 / 63 测试，全通过 | 主要集中在 auth、i18n、export；页面覆盖很低 |
| Web 构建 | 通过 | 最大 `ReportCharts` 1,150.22 kB / gzip 386.13 kB；主包 883.32 / 270.51 kB |
| Web 依赖审计 | 改造前 12 个漏洞（1 critical、8 high）；改造后 0 | 使用非强制 `npm audit fix`，未跨主版本 |
| Android 单元/仪器 | 0 / 0 | 新增 CI 构建门禁，但仍需补 Screen/ViewModel 测试 |
| E2E | 0 | 任务、答卷、预警、预约、导出尚无真实浏览器闭环 |

可观测性已有 Actuator `health/info/metrics` 和部分 Micrometer scheduler/export 指标；只有少量类有结构化日志。没有 Prometheus registry、trace/span、统一 correlation id、告警规则和容量仪表盘。改造后通知新增尝试结果、超时恢复、队列长度、死信和最老等待时长指标。

## 4. 分级问题清单

| 级别 | 问题与证据 | 影响/触发条件 | 运行验证 | 修复与状态 | 修改风险 | 回滚 |
|---|---|---|---|---|---|---|
| P0 | `schema-psy.sql` 可重复手工执行且含 `DROP INDEX`；CI 无迁移路径 | 环境漂移、误操作、不可审计升级 | 本机表数与脚本不一致已验证 | Flyway V1-V8、禁止 auto-baseline/clean、双路径 PG 测试；已实施 | baseline 版本选错 | 迁移前备份；默认 Flyway 关闭 |
| P0 | 心理业务表原先普遍无 `tenant_id`，任务/量表等查询缺少显式租户条件 | 非超级管理员访问跨租户业务对象 | 静态确认；隔离 PostgreSQL 迁移已验证 | V6 增加核心表租户归属；量表/导入、任务、报告、统计、预警、干预、通知运维、预约、咨询、外部注册审核和导出入口显式校验；旧数据预检查已实施，NOT NULL/VALIDATE 需待真实数据 100% 映射后执行 | 错误回填会锁死或串租户 | 默认 Flyway 关闭；迁移前备份；保留 nullable 过渡期 |
| P0 | Push Worker 原来带 `@Transactional` 并在事务内调用外部网关；失败即永久 FAILED，无超时回收 | 网络慢、实例崩溃、多实例重复或任务永久卡住 | 代码路径确认 | 移出事务，原子领取、指数退避、最大次数、死信、超时回收、错误脱敏和指标；已实施 | at-least-once 仍需供应商幂等键 | 关闭 scheduler；保留人工重放 |
| P0 | 后端基线测试 277 中 1 失败 | 所有 PR/主分支 CI 失败 | 本机复现 | 更新统计导出安全契约签名；已修复 | 极低 | 回退测试改动 |
| P0 | Android token 明文 SharedPreferences；AGP 8.5.2 + Gradle 9.0 与 API 35 组合不可复现；提交 Windows SDK 路径 | 设备备份/调试泄露 token；CI/其他开发机失败 | 静态配置确认；本机无 Android SDK | AES-GCM + Android Keystore、AGP 8.7.3 + Gradle 8.9、删除并忽略 local.properties、Android CI；已实施 | 旧 token 迁移失败会要求重登 | 清 token 后重新登录；构建工具版本可独立回退 |
| P0 | `npm audit` 初始报告 1 critical、8 high、2 moderate、1 low | 开发服务器文件读取、请求库与路由器安全风险 | 联网审计已复现 | 兼容范围内更新 lockfile；复审 0 vulnerabilities；已实施 | 间接依赖行为变化 | 回退 lockfile 并锁定已验证补丁版本 |
| P1 | 20 个文件超过 500 行，Repository/Service/Page 职责混合 | 变更冲突、难以隔离测试 | LOC 已测量 | 按 Command/Query/Renderer/Policy 分批拆；待实施 | 机械拆分破坏事务 | 每次只拆一职责并保持 API |
| P1 | 关键时间广泛直接调用 `now()` | 时区漂移、边界测试不稳定 | 静态命中多处 | Worker 首批注入 Clock；其余按模块迁移；进行中 | 时间语义变化 | 保持 timestamp 语义，逐模块回退 |
| P1 | 导出与统计使用 `ByteArray`，最大文件会驻留 JVM | 大导出触发 GC/OOM | 代码确认，未做容量压测 | 改流式 renderer/storage 前先建立 1x/10x 数据基线；待实施 | 文件格式兼容 | 保留旧 renderer feature flag |
| P1 | HTTP Push/FCM 与对象存储缺少统一读取/整体超时 | 线程长期占用、事务/队列堆积 | 配置确认 | RestClient 全局 connect/read timeout，对象存储 request timeout，SMTP 三类 timeout；已实施 | 超时过短造成误失败 | 环境变量调大，自动重试 |
| P1 | 缺少 pg_stat_statements、EXPLAIN 与 p50/p95/p99 基线 | 索引与分页优化只能猜测 | 已确认核心表均为 0 行且扩展未启用，当前无有效样本 | 建立合成数据和九类 Case 性能脚本；待实施 | 压测影响共享库 | 仅在隔离数据库运行 |
| P1 | Web 两个 chunk gzip 分别约 386 kB、270 kB | 首屏和报表加载慢 | Vite production build 已测量 | ECharts/locale 按需加载并保留前后对比；待实施 | 分包缓存失效 | 回退 Vite chunk 配置 |
| P2 | 只有基础 Actuator，无 Prometheus、trace 和告警 | 故障定位与容量预警不足 | 配置确认 | 增加 registry、correlation id、SLO/告警；待实施 | 指标基数 | 固定低基数标签 |
| P2 | Android 0 测试、Web 页面测试稀少、E2E 0 | 业务闭环回归依赖人工 | 文件统计确认 | 先补十个核心业务 Case，再提高覆盖率；待实施 | 测试维护成本 | 以稳定业务语义/API为断言 |

## 5. 已实施的第一阶段

1. Flyway 受控迁移、只读 baseline 预检查、显式 baseline CLI、不可变版本规则。
2. PostgreSQL 空库和 V1 升级双路径 CI 测试；H2 不再代表迁移验收。
3. 23 项关键业务 CHECK、答卷/任务/预警/报告唯一约束与通知重试约束；大表索引用并发迁移。
4. 通知投递 at-least-once 可靠性闭环和低基数指标。
5. 外部 HTTP、SMTP、对象存储超时。
6. Android token 加密、构建工具链固定和 CI 门禁。
7. 保留 JDBC 的 ADR；不进行无证据 ORM 重写。
8. 匿名答卷去除直接用户标识，并禁止生成个人报告、个人预警和个人通知。
9. 量表发布校验、任务时间窗、稳定提交幂等键、反向+加权组合，以及平均/加权平均计分。
10. Web 与 Android 被测端补齐中文、日语、英语文案；Android 编译仍需本机 Android SDK。
11. V8 将量表代码/版本从全局唯一调整为租户内唯一，使不同租户可独立维护同名标准量表；量表导入确认状态与量表结构写入合并到同一事务。

## 6. 后续阶段与验收门槛

### 阶段 2：租户约束硬化

核心入口的显式租户过滤与 nullable 过渡列已经实施。下一步必须在真实历史数据上输出每张业务表的归属推导率、孤儿行、冲突行和无法映射行；只有 100% 可映射或有明确隔离区策略后，才能新增独立迁移执行 `VALIDATE CONSTRAINT` 和 `SET NOT NULL`。继续补齐每个 Controller 的同租户成功、跨租户 404/403、超级管理员例外和匿名隐私矩阵。

### 阶段 3：结构与时间

优先拆分 `ScaleRepository`、`AnswerSheetRepository`、`StatisticsService` 和 Android `PsyRespondentApp`。先提取 RowMapper、Query Repository、Renderer、Transition Policy 和 Clock，不改变 API DTO。

### 阶段 4：性能与容量

在隔离 PostgreSQL 上生成 1x/10x 规模数据，覆盖任务列表、保存提交、评分、预警升级、群体统计、趋势、导出、通知和预约九类 Case。记录 JVM/连接池/数据库 p50、p95、p99、吞吐与内存，并对慢查询保留 `EXPLAIN (ANALYZE, BUFFERS)`。

### 阶段 5：交付与恢复

增加容器镜像 digest、SBOM、数据库备份恢复演练、蓝绿/滚动发布说明、API/E2E 冒烟和失败自动停止。完成一次从备份恢复到新数据库并切换应用的计时演练后，才能宣称具备故障恢复闭环。
