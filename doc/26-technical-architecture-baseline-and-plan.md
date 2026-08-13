# 技术架构基线、风险与优化计划

> 剩余闭环工作的目标、执行顺序和验收标准见 `doc/prompt/03-remaining-closure-goals-and-execution.md`。

- 测量日期：2026-08-08
- 分支：`feature/redo`
- 基线提交：`9baa3c0`
- 原则：只记录代码、配置、构建和运行证据；未测量项不推断为已完成。

## 1. 模块基线矩阵

| 模块 | 技术栈 | 规模 | 主要职责 | 当前风险 | 建议 | 优先级 |
|---|---|---:|---|---|---|---|
| Backend | Kotlin 2.1、Spring Boot 3.4、JDBC、PostgreSQL、Redis | 主代码 21,313 行；测试 9,057 行、46 个 Kotlin 测试文件 | API、评分、任务、报告、预警、咨询、通知、导出 | 超大 Repository/Service、时钟分散、量表治理元数据不完整 | PG 集成测试、量表包门禁、按职责渐进拆分 | P0/P1 |
| Admin Web | React 19、TypeScript、Ant Design、React Query、ECharts、Playwright | 20,836 行 | 管理与咨询工作台 | 页面与语言目录过大；图表与主包较大；只有 ScalePackage 聚焦 E2E | 拆页面/语言域、细化图表按需加载、补核心业务 Case E2E | P1 |
| Android | Kotlin、Jetpack Compose、Retrofit | 主代码 2,774 行；1 app 模块；9 Screen；1 ViewModel | 被测者登录、任务、答题、报告、预约、通知 | `PsyRespondentApp.kt` 仍有 1,482 行；本机无 Android SDK；缺少设备/无障碍测试 | 继续按状态域拆分，并在 SDK/设备上执行 lint、构建与 UI 验证 | P1 |
| auth-starter | Kotlin/Spring 独立相邻仓库 | 构建时 composite include | 用户、会话、租户、角色、SSO | CI 默认分支漂移；数据库结构与应用发布未统一版本化 | 固定提交，应用 V1 统一冻结结构 | P1 |
| PostgreSQL | PostgreSQL 18.4 本机；显式 SQL、Flyway V1-V23 | 本机 public 46 表（31 心理、15 认证）；隔离空库迁移后 61 表 | 认证、心理业务、安全响应策略、ScalePackage、Golden Case、发布审批、质量策略、评分轨迹及导出/通知可靠性 | 其他历史库仍需显式 baseline 与租户预检查；Flyway 10.20.1 尚未声明支持 PostgreSQL 18 | 保持不可变迁移和差异审批，继续确认 PG18 兼容性 | P0/P1 |
| CI | GitHub Actions | Web + Backend，改造后增加 PostgreSQL + Android | 编译、测试、构建 | 改造前后端只跑 H2/Mockito，Android不构建 | 空库/升级迁移双路径、Android lint/test/assemble | P0 |

## 2. 规模与复杂度

### 2.1 超大文件

| 行数 | 文件 | 风险 |
|---:|---|---|
| 4,237 | `admin-web/src/i18n/messages.ts` | 单目录冲突与全量加载 |
| 2,241 | `admin-web/src/pages/ScaleListPage.tsx` | 表单、导入、维护职责耦合 |
| 1,482 | `android-app/.../PsyRespondentApp.kt` | 已提取答题校验、最终确认页和任务 `UiState/ViewModel/Screen`；首页、答题、报告、预约和通知状态仍集中 |
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
- V1 合并认证后为 44 张应用表；V1-V23 空库迁移后为 61 张应用表、99 个 `ck_psy_*` 约束，并有版本组当前发布版本唯一索引、导出待重试部分索引和 Golden Case/审批历史游标索引；V15-V18 完成联系结果、三语和租户硬化，V19/V20 分别增加导出与 Push 通知处理租约，V21 增加追加历史游标索引，V22 增加答卷/结果质量决策留痕，V23 增加结果评分审计轨迹。本机 `lx/public` 实际仍为 46 张旧表且未执行 baseline/迁移。
- 本机 `lx/public` 任务、答卷、预警、结果、导出、通知投递和预约表当前均为 0 行，且 `pg_stat_statements` 未启用；不能把该空库延迟包装成性能结论。已新增只使用 `psy_perf_*` 隔离 schema 的 1x/10x 性能基线脚本，并在 2026-08-11 以 100/1,000 个技术任务真实运行 HTTP、评分、数据库 `EXPLAIN (ANALYZE, BUFFERS)`、Actuator Hikari/JVM 和数据库资源采样；生产并发容量仍未由该本机串行样本证明。

结论：复杂统计、锁、部分索引与批量写入占比足以支持继续保留显式 SQL；没有全量 ORM 重写证据。

## 3. 测试、构建与可观测性

> 2026-08-12 SCL-90 适配回归追加结果：后端强制 PostgreSQL 测试 387 个、0 失败/错误、15 个条件 Case 跳过；Web 13 个文件/102 个测试通过，production build 主入口 976.16 kB（gzip 297.50 kB），`ScalePublicationPage` 19.60 kB（gzip 5.20 kB）；Playwright 隔离闭环 9/9 通过，用时 36.2 秒。另有独立 SCL-90 来源包导入与 4 个 Golden Case 验证。下方较早的基线数字仅作历史对照。

| 类型 | 基线结果 | 说明 |
|---|---|---|
| Backend 单元/契约 | 当前 387 个，0 失败/错误，15 个条件 Case 跳过 | Java 21 完整测试已连接本机 PostgreSQL 18.4，条件 Case 的跳过原因由 PostgreSQL 集成开关控制；新增 SCL90_PROFILE GSI/PST/PSDI 指标、质量策略/PRORATE、评分轨迹和未审核常模阻断单测；另有导出重放/下载租户门禁与强制审计、审计失败回滚、导出/通知租约 fencing、callback 脱敏与强制审计、Spring SQL 初始化、可观测性、ScalePackage、Golden Case 游标分页、草稿乐观锁、重新评分租户门禁和集中租户策略测试 |
| PostgreSQL 集成 | 15 个，全通过 | Flyway V1-V23 空库、V1 baseline 预检后增量升级、V19 导出租约、V20 通知处理租约与 fencing、V21 历史游标索引、V22 质量留痕、V23 评分轨迹 JSONB 约束、callback 状态门禁、批量重放租户隔离、导出死信重放/下载及审计回滚、评分版本、ScalePackage、Golden Case、租户约束、导入领取、开发种子/租户一致性、全量归属预检及草稿原子创建/版本 CAS 均在随机隔离 schema 实跑 |
| Web 单元/组件 | 13 文件 / 102 测试，全通过 | 新增敏感草稿存储、稳定 submit token、风险归一化、答题完成度、ScalePackage、发布审批、历史证据、版本化导出、受控导入 API/模型及量表指标 Golden Case 本地化测试；页面级覆盖仍不足 |
| Web 构建 | 通过 | 最大 `ReportCharts` 1,150.22 kB / gzip 386.13 kB；最新主入口 975.91 / gzip 297.40 kB |
| Web 依赖审计 | 改造前 12 个漏洞（1 critical、8 high）；改造后 0 | 使用非强制 `npm audit fix`，未跨主版本 |
| Android 单元/仪器 | 10 / 0（未运行） | 新增 5 个纯答题校验及 5 个任务状态/ViewModel 单测和 CI 构建门禁；本机缺 Android SDK，不能把测试源文件计为通过 |
| E2E | 9 个 Playwright 自动化 Case | 随机隔离 PostgreSQL schema 上完成 ScalePackage、发布、并发保存、高风险关闭、匿名隐私、中日英内容及结果/干预/通知/导出租户矩阵，并覆盖 Push callback/批量重放以及导出死信重放/下载的租户边界、审计和凭据脱敏。本机最新实跑 9/9 通过（36.2 秒），数据库后置断言同时验证结果 `scoring_trace_json` 的算法版本和审计字段；另有独立来源包 E2E 验证 SCL-90 草稿导入、三语选项矩阵和 4 个 Golden Case。导出和通知 Worker 均有真实 JVM 强杀/重启证据。真实量表外部签审、更高并发压力和预约仍未覆盖；Android 按当前范围延期 |

可观测性已接入 Prometheus registry 和受认证保护的 `/actuator/prometheus`，统一 `X-Correlation-Id` 会校验、回传并进入 Spring Boot Logstash JSON 日志；Micrometer Brave bridge 接收 W3C `traceparent`，本地请求日志输出 `trace_id`/`span_id`，采样率可由环境变量控制。提交、评分、预警生命周期/队列、通知、导出和 scheduler 使用固定枚举标签，JVM/Hikari/HTTP 指标由 Actuator 暴露。规则文件已覆盖 5xx、连接池、提交、评分、预警逾期、通知死信/积压、scheduler 和导出，并有 Runbook。真实浏览器已验证匿名抓取 401、授权抓取成功、核心业务指标存在，以及同一错误请求的 `correlation_id`、已知 `trace_id` 和 `span_id` 出现在合法 JSON 日志。仍没有外部 trace 存储/导出器、容量仪表盘、真实 Alertmanager 路由和值班触发演练；手工 Java `HttpClient` 对象存储调用尚未纳入自动 trace 传播。

## 4. 分级问题清单

| 级别 | 问题与证据 | 影响/触发条件 | 运行验证 | 修复与状态 | 修改风险 | 回滚 |
|---|---|---|---|---|---|---|
| P0 | `schema-psy.sql` 可重复手工执行且含 `DROP INDEX`；CI 无迁移路径 | 环境漂移、误操作、不可审计升级 | 本机表数与脚本不一致已验证 | Flyway V1-V8、禁止 auto-baseline/clean、双路径 PG 测试；已实施 | baseline 版本选错 | 迁移前备份；默认 Flyway 关闭 |
| P0 | Flyway 默认事务级 advisory lock 与 V14 `CREATE INDEX CONCURRENTLY` 自阻塞；通知重试 CASE 时间参数被 PostgreSQL 推断为 text | 空库/升级迁移永久等待；失败投递无法进入重试/死信 | 本机 PostgreSQL 18.4 真实复现 | 改用 session advisory lock；时间参数显式 cast；当前 15 个 PG Case 与完整 383 测试通过 | 外部 Flyway 未同步配置 | 外部执行器设置 `FLYWAY_POSTGRESQL_TRANSACTIONAL_LOCK=false`；回退应用配置前暂停迁移 |
| P0 | 心理业务表原先普遍无 `tenant_id`，任务/量表等查询缺少显式租户条件 | 非超级管理员访问跨租户业务对象 | 静态确认；隔离 PostgreSQL 迁移已验证 | V6 增加核心表租户归属；量表/导入、任务、报告、统计、预警、干预、通知运维、预约、咨询、外部注册审核和导出入口显式校验；旧数据预检查已实施，NOT NULL/VALIDATE 需待真实数据 100% 映射后执行 | 错误回填会锁死或串租户 | 默认 Flyway 关闭；迁移前备份；保留 nullable 过渡期 |
| P0 | Push Worker 原来带 `@Transactional` 并在事务内调用外部网关；失败即永久 FAILED，无超时回收 | 网络慢、实例崩溃、多实例重复或任务永久卡住 | 代码路径确认 | 移出事务，原子领取、指数退避、最大次数、死信、超时回收、错误脱敏和指标；已实施 | at-least-once 仍需供应商幂等键 | 关闭 scheduler；保留人工重放 |
| P0 | 后端基线测试 277 中 1 失败 | 所有 PR/主分支 CI 失败 | 本机复现 | 更新统计导出安全契约签名；已修复 | 极低 | 回退测试改动 |
| P0 | Android token 明文 SharedPreferences；AGP 8.5.2 + Gradle 9.0 与 API 35 组合不可复现；提交 Windows SDK 路径 | 设备备份/调试泄露 token；CI/其他开发机失败 | 静态配置确认；本机无 Android SDK | AES-GCM + Android Keystore、AGP 8.7.3 + Gradle 8.9、删除并忽略 local.properties、Android CI；已实施 | 旧 token 迁移失败会要求重登 | 清 token 后重新登录；构建工具版本可独立回退 |
| P0 | `npm audit` 初始报告 1 critical、8 high、2 moderate、1 low | 开发服务器文件读取、请求库与路由器安全风险 | 联网审计已复现 | 兼容范围内更新 lockfile；复审 0 vulnerabilities；已实施 | 间接依赖行为变化 | 回退 lockfile 并锁定已验证补丁版本 |
| P0 | Web 曾把答卷答案写入 `localStorage`，Web/Android 每次提交曾生成新 token | XSS、共享设备或超时重试导致敏感答案泄露或重复结果 | 代码复现；Web 80 个测试与 Android 静态路径验证 | Web 本地仅保存游标和答卷版本；答案从服务端草稿恢复；Web `sessionStorage` 与 Android 加密存储复用稳定 token，成功后清除；已实施 | 旧本地草稿兼容 | 首次读取即清除旧答案字段；服务端唯一约束仍是最终防线 |
| P0 | 报告和群体统计把所有量表按 SCL-90/GSI/PST/PSDI 和固定 2.0 阈值解释 | 非 SCL 量表出现错误解释或风险结论 | 代码确认；构建和单测通过 | 改为读取评分结果维度和量表版本规则；缺少专业依据显示待审核；已实施 | 旧报表展示变化 | 保留原始结果和历史报告，不回写历史评分 |
| P0 | 高风险预警缺少已审批 SLA、责任角色、联系证据、随访和关闭门禁 | 预警无法及时响应，或在没有处置证据时关闭 | V11 隔离 PG 迁移与预检查通过 | V11 增加版本化双审批安全响应策略、不可变快照、响应事件、随访和关闭清单；P0/P1 关闭必须完成证据且存在迫近危险时禁止关闭；已实施 | 策略缺失会阻止形成完整处置闭环 | 策略缺失显式标红并进入升级查询；不自动编造 SLA/联系人 |
| P0 | `data-psy.sql` 曾不兼容 V8 租户部分唯一索引，且同一演示链跨 DEFAULT/CAMPUS/ENTERPRISE 复用量表与任务；Spring SQL 初始化还会在 Flyway 后重跑冻结 schema | 显式启用演示种子时启动失败或恢复已淘汰的全局索引；若直接猜测回填会固化串租户数据 | 最新 Flyway 隔离 schema 连续执行两次；同一 schema 真实应用连续启动两次；父子租户 SQL 断言 | 正式结构改为只由 Flyway 管理并增加配置门禁；按租户拆分 fixture，租户业务写入显式写 `tenant_id`；旧非法状态值已修正；未审核 SCL 示例改为 `SCL90_TECH_DEMO` 草稿；已实施 | 既有开发库中的旧 `SCL90` 不自动删除或猜测转换 | 默认继续 `PSY_SQL_INIT_MODE=never`；生产禁止演示种子；历史旧示例进入人工清单 |
| P1 | Web 答题页曾禁止返回、单选自动跳题，最终页只显示数量且多选按选项重复计数 | 误选无法纠正、可访问性和提交确认不足 | 代码确认；新增 3 个完成度测试 | 增加上一题、取消默认自动跳题、逐题状态/摘要/修改入口、隐私提醒、焦点和 `aria-live`；已实施 | 页面操作习惯变化 | 保持 API 与服务端草稿不变，可回退纯 UI 变更 |
| P1 | 20 个文件超过 500 行，Repository/Service/Page 职责混合 | 变更冲突、难以隔离测试 | LOC 已测量 | 按 Command/Query/Renderer/Policy 分批拆；待实施 | 机械拆分破坏事务 | 每次只拆一职责并保持 API |
| P1 | 关键时间广泛直接调用 `now()` | 时区漂移、边界测试不稳定 | 静态命中多处 | Worker 首批注入 Clock；其余按模块迁移；进行中 | 时间语义变化 | 保持 timestamp 语义，逐模块回退 |
| P1 | 导出与统计使用 `ByteArray`，最大文件会驻留 JVM | 大导出触发 GC/OOM | 代码确认，未做容量压测 | 改流式 renderer/storage 前先建立 1x/10x 数据基线；待实施 | 文件格式兼容 | 保留旧 renderer feature flag |
| P1 | HTTP Push/FCM 与对象存储缺少统一读取/整体超时 | 线程长期占用、事务/队列堆积 | 配置确认 | RestClient 全局 connect/read timeout，对象存储 request timeout，SMTP 三类 timeout；已实施 | 超时过短造成误失败 | 环境变量调大，自动重试 |
| P1 | 缺少生产级 pg_stat_statements、并发容量与前后优化基线 | 索引与容量结论不能外推到生产 | `scripts/run-performance-baseline.sh` 已在隔离 schema 以 100/1,000 个技术任务、15 次/Case 实测 p50/p95/p99、评分、预警、统计、导出、通知、预约和三类 EXPLAIN；本机 `shared_preload_libraries` 为空，pg_stat_statements 未启用 | 隔离 1x/10x 基线已实施；仍需启用扩展的专用 PG 实例、并发压测、优化前后同 Case 对比和流式导出容量验证 | 压测影响共享库 | 脚本默认只创建/删除 `psy_perf_*` schema；不改 PG 配置 |
| P1 | Web 两个 chunk gzip 分别约 386 kB、270 kB | 首屏和报表加载慢 | Vite production build 已测量 | ECharts/locale 按需加载并保留前后对比；待实施 | 分包缓存失效 | 回退 Vite chunk 配置 |
| P2 | 原来只有基础 Actuator，无 Prometheus、trace 和告警 | 故障定位与容量预警不足 | 配置、单测和真实浏览器抓取及日志关联已验证 | Prometheus、correlation id、JSON 日志、本地 W3C trace/span、低基数业务指标、规则和 Runbook 已实施；外部 trace 后端/导出、SLO 仪表盘、真实路由和值班演练待实施 | 指标基数、采样成本和告警噪声 | 固定枚举标签与可配置采样率；规则和 tracing bridge 可分别回退，保留基础 Actuator |
| P2 | Android 设备矩阵按当前范围延期 | 设备/无障碍/截图证据仍未运行 | 导出/通知 Worker 真实 JVM `SIGKILL`/重启恢复；Push callback/批量重放、导出死信重放/download 及全局管理员重评分已进入 9 个 Playwright Case | Android 暂不执行；恢复 Android 范围后再补设备矩阵 | 测试维护成本 | 以稳定业务语义/API 和数据库后置断言为准 |

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
10. Web 主流程已具备中文、日语、英语文案；Android 已删除字符串替换式翻译，默认英语、日语、简体中文 169 个资源 key/placeholder 一致，源码中文硬编码扫描为 0；Android 编译仍需可用 SDK。
11. V8 将量表代码/版本从全局唯一调整为租户内唯一，使不同租户可独立维护同名标准量表；量表导入确认状态与量表结构写入合并到同一事务。
12. V9 把重新评分从覆盖旧结果改为追加计算版本；数据库唯一索引保证每份答卷只有一个当前结果，统计和答卷查询显式读取当前版本，历史结果、维度分和报告保留。
13. V10 在新量表发布时生成覆盖题目、选项、维度、结果规则、常模、高风险规则和可视化配置的 SHA-256 摘要，并快照到任务和评分结果；旧数据摘要保持空值，不伪造可追溯性。
14. 修复量表复制新版本时遗漏高风险规则的问题；按题号和选项编码重映射新 ID，高风险预警启用但无规则时阻止发布，关闭开关时评分器不再错误触发休眠规则。
15. 重新评分锁定查询增加答卷所属租户条件；租户管理员不能仅凭跨租户 `resultId` 触发评分和报告生成。
16. 个人/群体报告移除跨量表硬编码 SCL-90 指标与固定 2.0 阈值，改为评分结果维度和量表版本规则；无可追溯专业规则时标记为待专业审核。
17. V11 增加安全响应策略双审批、预警策略快照与期限、响应事件、随访和关闭清单；策略缺失不会静默使用系统猜测值。
18. Web 不再把答案写入浏览器持久存储；Web 与 Android 使用稳定 submit token；答题页支持返回、逐题确认且不再自动跳题。
19. V12 建立 ScalePackage 数据库基础：来源/版权/授权、五类中日英翻译、缺失与质量策略、效度规则、受限算法绑定和常模来源审核字段；新增租户安全的聚合读写 API，仅允许修改草稿版本，校验所有子对象归属和 JSON；创建新版本会复制配置并重置审核状态，新发布摘要覆盖全部 ScalePackage 内容。
20. V13 建立六类版本化 Golden Case、生产计分器运行证据、专业审核和业务审批；审批绑定量表内容摘要与样例集合发布指纹，样例或量表变化会使旧审批失效。发布门禁还检查三语内容、非诊断/高风险说明、来源版权授权、常模来源、已知算法和独立审核人；不支持的缺失/效度/专用算法配置会明确阻止发布，不静默降级。
21. V14 增加同一量表版本组只能有一个当前已发布版本的 PostgreSQL 部分唯一索引；发布事务先按稳定顺序锁定版本组，目标必须仍为草稿，重复发布返回失败而不是覆盖状态。
22. Web 增加三语发布就绪与审批工作台，可查看内容摘要、发布证据指纹、原始阻塞码、六类 Golden Case 的当前/运行/审批状态，按角色运行样例、专业审批、业务审批并在完全就绪后发布；量表详情可直接进入对应版本工作台。
23. 量表删除、复制版本、基础/子对象批量编辑、ScalePackage 更新、Golden Case 和发布操作统一先锁定量表行并重新检查草稿状态，关闭“校验后并发发布/编辑”导致摘要与实际内容不一致以及已发布版本被批量写入的竞态。
24. Android 新增独立答题校验策略和 5 个单测 Case、上一题、非敏感游标恢复、最终逐题确认与修改入口；错误使用 assertive live region，进度条增加语义说明。任务列表已提取为 `TasksUiState`、`TasksViewModel`、`TasksRoute`、`TasksScreen` 和 `RespondentTasksDataSource`，保持原路由/API，新增类型化筛选、失败重试、部分加载保留、无底层异常泄露、标题语义和 5 个状态/ViewModel Case；`PsyRespondentApp.kt` 从 1,575 行降至 1,482 行。当前仅通过三语静态门禁，单测、Lint 和 APK 构建仍因本机缺少 Android SDK 而未实际执行。
25. Web 增加 ScalePackage 结构化草稿编辑和三语内容矩阵，可维护治理、版权/授权、质量、效度、算法与常模信息；发布工作台增加 Golden Case 新 revision、输入/预期编辑和单次运行差异展示。
26. 后端增加租户归属校验后的只读事务历史聚合 API，一次查询全部 Case revision、运行证据和发布审批，避免 N+1；Web 可展开查看算法版本、内容摘要、实际结果/差异和审核指纹，并对异常旧 JSON 给出三语错误而不崩溃。
27. 增加版本化 ScalePackage 导出 API 和 Web 三语下载入口；当前导出 schema v2 且继续接受 v1。导出在显式租户归属校验后的 PostgreSQL 可重复读事务中包含量表正文、可视化配置、治理数据、全部 Golden Case revision/run、发布审批、内容摘要与发布指纹；文件名经过服务端与客户端双重净化，导出审计写入失败会阻止成功响应。跨租户导出已验证返回 404。Golden Case/审批历史已具备 V21 服务端游标分页；导出本身仍待流式化和容量基线。
28. 受控 ScalePackage 导入已从仅预览推进到确认落库：预览持久化为租户归属的导入作业，确认时重新校验发布指纹和完整载荷摘要，并用数据库条件更新保证同一作业只能领取一次；新建草稿按业务键重映射维度、题目、选项、结果规则和常模 ID，外部版权/授权、翻译、效度、算法与常模审核状态全部重置。仅转入 Golden Case revision，历史 run 和外部发布审批明确丢弃并返回计数，不把外部环境证据伪装为本租户审批。旧 Excel 导入也改为同一原子领取机制。接口已补匿名、普通用户和管理员角色测试；隔离 PostgreSQL 已验证新草稿租户、状态、审批清除、Golden revision 保留以及 run/审批丢弃；真实浏览器已完成 JSON 文件选择、预览、确认和新草稿验证。
29. 修复真实运行发现的三项缺口：明确 Spring Boot 主类，避免迁移 CLI 与应用入口并存导致 `bootRun` 无法选择；ScalePackage 预览直接从原始 JSON 字节反序列化，避免 PostgreSQL decimal 经 JsonNode 标准化后发生载荷摘要误报；资源不存在与跨租户不可见统一返回稳定 404。Web 导入问题按稳定错误码动态显示中日英，未知错误码保留原始消息；量表页移除 Ant Design 静态消息和已弃用行号 `rowKey`，全新浏览器页面控制台 0 error。
30. 修复认证异常被应用通用异常处理器抢先捕获的问题：`AuthException` 按稳定码族映射 400/401/403/409，只记录不含凭据和堆栈的拒绝日志；应用 MessageSource 补齐全部核心认证异常的中日英消息。隔离运行验证错误密码和账户锁定均返回正确 401 与对应语言消息。
31. 增加 Playwright Chromium 自动化和随机 `psy_e2e_*` PostgreSQL schema 运行器；ScalePackage Case 覆盖版本化下载、真实文件上传、预览/确认、幂等、租户/角色/匿名矩阵、三语持久化 issue 以及浏览器 console error 断言。GitHub Actions 已加入 PostgreSQL 17.6 E2E job 和失败 trace/截图/视频 artifact 上传，远端首次运行仍待验证。
32. 修复开发种子与当前数据库治理脱节：正式结构不再经 Spring SQL 初始化重复执行冻结的 `schema-psy.sql`，并增加配置回归门禁；STRESS_DEMO 为三个租户建立独立版本组、任务和答卷链；量表导入、预约、咨询、通知、预警、干预和导出均显式写入权威来源的 `tenant_id`；修正 V5/V2 状态白名单外的 `DIMENSION_SCORE`、`IN_PROGRESS`、`COMPLETED`。新增 PostgreSQL Case 在最新 schema 连续执行种子两次并断言所有父子租户冲突为 0；同一隔离 schema 的真实应用连续启动两次均 ready（该双启动证据形成于 V14），种子业务行不重复。未经授权和专业审核的 SCL 示例更名 `SCL90_TECH_DEMO` 并固定为非当前草稿。
33. 增加非匿名高风险核心业务浏览器 Case：管理员创建/分配任务，被测者暂存、恢复、逐题确认、提交并用相同 token 重放，随后校验报告免责声明、跨租户任务/报告 404、唯一预警、咨询师联系/安全评估/责任交接/随访和关闭。SQL 后置断言验证唯一当前结果/报告/预警/干预、响应事件、关闭检查单、安全审计和租户父链。真实运行同时修复 PostgreSQL nullable 参数推断、`smallint` 删除标记、预警期限时间类型、报告版本字符串、表单完整答卷观察、Ant Design 上下文/可访问性，以及 V15 联系结果长度契约。
34. 增加不同 token 并发提交、匿名高风险隐私和中日英运行时内容 Case。并发请求只允许一个成功并只留下一个 sheet/result/report；匿名答卷以服务端伪匿名 token 落库且无个人报告、预警、结果通知或导出来源，安全事件不泄露 token；量表、题目、选项、维度和结果规则只选择请求语言下 `APPROVED` 的 ScalePackage 翻译，未审核翻译回退基础字段。中日英三次真实提交分别生成对应语言报告和非诊断声明，报告详情按语言返回量表与逐题内容。通知 PostgreSQL Case同步补原子领取、超时回收、死信与人工重放。
35. V16 补齐逐条高风险规则中日英翻译，ScalePackage v2 导入时按规则 ID 受控重映射并重置审核状态，复制版本同样按 `rule_code` 重映射；三语缺一或未审核会明确阻止发布。答卷保存/提交记录规范化 `response_locale_code`，报告版本记录 `locale_code`，重新评分和自动超时提交沿用答卷语言，历史行保持空值而不猜测。计分器按显式语言仅选择 `APPROVED` 的高风险规则翻译，Web 治理页可维护三语内容，工作人员报告页显示生成语言。
36. 修复首次并发保存依靠捕获唯一冲突后在已失败 PostgreSQL 事务中继续查询，以及版本 CAS 晚于答案替换的问题。同一任务/受测者写入使用 PostgreSQL advisory transaction lock；首次草稿在 V5 部分唯一索引上以 `ON CONFLICT DO NOTHING` 原子创建；已有草稿先验证完整 `answerSheetId/versionNo`，成功取得版本后才替换答案。超时自动提交也取得相同身份锁，重新读取当前草稿版本、语言和答案后再 CAS，避免提交扫描时的旧答案。真实 PostgreSQL 双线程 Case 与 Playwright 两轮并发保存证明每轮只有一个成功者、最终答案不混写且租户父链一致。
37. 新增集中 `TenantAccessPolicy`，将全局例外限定为无租户上下文的 `SYS_ADMIN`/`SUPER_ADMIN`；tenant-bound 管理员始终受自身租户约束，无租户的普通业务角色稳定返回 `TENANT_CONTEXT_REQUIRED`。量表、任务、报告、预警、干预、通知运维、ScalePackage/发布治理、导入、统计、安全响应策略、预约、咨询、用户管理、外部注册审核和导出入口已接入同一策略；跨租户全局读取写入 `PSY_TENANT_SCOPE_OVERRIDE` 安全审计，导出作业继承目标报告租户，预警不得被非目标租户成员冒领。隔离 PostgreSQL + Playwright 已验证全局/tenant-bound/tenantless/跨租户矩阵并断言至少四条例外审计；相邻 `auth-starter` 同时修复 PostgreSQL 对 tenantless 登录查询参数无法推断类型的问题并增加回归测试。
38. 新增 V16 后只读租户归属硬化预检：用显式注册表覆盖全部 46 张 `psy_*` 表，逐表标明 `DIRECT`、`INHERITED` 或 `GLOBAL` 的权威归属来源，精确统计直接租户列的未映射行和孤儿租户，并检查量表、任务、目标分配、答卷、结果、报告、预警、干预、预约、通知、导出、Golden Case 与发布审批等父子/人员租户冲突。隔离 PostgreSQL Case 在最新迁移和开发种子上实际执行，46 张表全部被注册且所有 `issue_count=0`；脚本不写入数据，真实历史库仍须人工处理非零记录后才能新增 `VALIDATE CONSTRAINT`/`SET NOT NULL` 迁移。
39. 增加可观测性基础：使用 Spring Boot 内置 Logstash JSON 日志，严格校验并传播 `X-Correlation-Id`，避免控制字符注入 MDC；接入 Prometheus registry、HTTP histogram 和全局 application 标签。提交成功/失败及耗时、评分成功/失败及耗时、预警创建/领取/分配/升级/提醒/关闭、开放/逾期队列、通知、导出和 scheduler 指标均限制为固定枚举，不使用租户、用户、任务、答卷、量表代码或提交 token 标签。新增 9 条 Prometheus 规则和脱敏 Runbook；Playwright 实跑验证匿名抓取 401、授权抓取及 JVM/Hikari/提交/评分/预警指标，运行器逐行解析后端 JSON 日志并要求 `service` 与 `correlation_id`。
40. 接入 Micrometer Brave bridge 和 W3C propagation；默认采样率 `0.1`，可通过 `PSY_TRACING_SAMPLING_PROBABILITY` 调整。Playwright 使用固定 `traceparent` 发起预期失败请求，运行器验证响应 correlation id 与 JSON 日志中的同一 `correlation_id`、精确 `trace_id` 及非空 `span_id`，证明本地请求关联已实际运行。当前未配置 Zipkin/OTLP 等外部 trace 导出和存储；Boot `RestClient.Builder` 调用具备自动插桩基础，但对象存储使用手工 Java `HttpClient`，其跨服务传播仍需显式实现并用真实下游验证。
41. 增加安全的 PostgreSQL 备份恢复演练器和核心恢复断言：数据库名、端口和进程目标均有保护，默认只创建并清理专用隔离源库/恢复库。演练对同一不可变 `bootJar` 执行 Flyway、技术种子、租户化 API 冒烟、custom-format dump/单事务 restore、全表行数/稳定约束属性/索引/序列/数据内容比较，再在恢复库启动应用验证登录、任务、报告审计、预警和数据库内导出文件摘要。实际运行同时修复演示导出 `file_size` 与真实字节不一致、历史时间导致启动即清理的问题；CI backend job 已加入该门禁。PITR、外部对象存储和上一版本应用回滚仍保留为 G6 未闭环项。
42. 增加治理发布到任务版本锁定的浏览器 Case：从仓库自有、明确无临床含义的合成量表创建草稿版本，运行并专业批准 NORMAL/BOUNDARY/REVERSE/MISSING/INVALID/HIGH_RISK 六类 Golden Case，由不同用户完成专业和业务审核后发布；跨租户 readiness 返回 404，已发布问题不可修改，后续草稿版本不会改变旧任务保存的版本号、版本组和内容摘要。另在后续草稿建立完整可发布证据后修改题目，真实验证六个旧 Case 全部变为 stale、旧双审核继续作为审计历史保留但不再匹配新发布指纹，Web 用中日英明确提示新建 revision、重跑和重审。PostgreSQL 后置断言校验通过运行绑定内容/Case 摘要和算法版本、双审核人分离及任务租户父链。发布页同时改用 Ant Design 上下文 message，并让 Modal 表单预渲染，浏览器 console/page error 为 0。该证据只证明技术门禁，不代替真实量表授权和专业签审。
43. 修复正式 `databaseMigration baseline` 入口的两个运行级缺口：改为把含 `DO $$` 的预检脚本作为单一批次交由 PostgreSQL 解析，避免 Spring 通用分号分割器截断 PL/pgSQL；standalone CLI 显式使用 session advisory lock，避免 V4/V8/V14 并发索引等待自身事务快照。对本地旧结构 `lx` 完成全套只读预检后，custom-format 克隆到专用数据库并真实执行 V1 baseline、V2-V18、validate；V17 分离验证 13 条租户外键和 16 个非空证明约束，V18 再将 16 张直接租户表设为 `NOT NULL`。原 `lx/public` 保持无 Flyway 表且未修改；V19-V21 只在随机隔离 schema 和恢复演练库验证，尚未应用到该旧库克隆或 `public`。
44. 增加 V19 导出任务持久化租约、指数退避、最大尝试次数和 `DEAD_LETTER`；领取和完成均用令牌 CAS，租约专属对象 key 阻止过期 Worker 覆盖或删除新产物，人工重放同时支持 `FAILED`/`DEAD_LETTER`。任务状态/列表不再读取整个文件，终态清理改按完成时间，Web 运维页显示重试、下次执行、处理开始及死信时间。`scripts/run-export-worker-recovery-rehearsal.sh` 在随机 PostgreSQL schema 中让真实 JVM 阻塞于 HTTP 对象存储 PUT 后执行 `SIGKILL`，重启后验证原任务 `DONE`、`retry_count=1`、租约清空、产物位于隔离目录且数据库仍只有一行。
45. 增加 V20 Push 通知处理租约和状态一致性约束；领取返回 UUID，成功/失败写入必须按令牌 CAS，超时回收会清空旧租约，因此崩溃前 Worker 无法迟到覆盖重启实例。人工重放也会清除租约，HTTP Push 请求携带由投递 ID 派生的稳定 `Idempotency-Key`，并将租约丢失单独记录为 `claim_lost` 指标。`scripts/run-notification-worker-recovery-rehearsal.sh` 让真实 JVM 阻塞于 Push HTTP POST 后执行 `SIGKILL`，重启后验证原投递 `SENT`、`retry_count=1`、租约清空且数据库仍只有一行；CI 已纳入该演练。
46. 收紧 Push callback 和客户端回执状态机：`PENDING`/`PROCESSING` 不能绕过 Worker 租约直接进入终态，callback 更新 SQL 同时携带原状态条件避免检查后竞争。callback 错误和原始载荷写库前统一遮蔽 authorization/token/password/secret/credential/api-key，批量或单条重放及 callback 都使用强制安全审计，审计失败回滚业务事务。PostgreSQL Case 验证活动租约不被 callback/回执破坏及跨租户批量重放零更新；浏览器 E2E 验证跨租户 callback 404、同租户 callback/重放成功、明文凭据未落库及两类审计事件存在。
47. 将异步导出提交、人工死信重放和文件下载收进 `ExportJobOpsService` 事务边界：提交/重放/下载使用强制安全审计，重放 SQL 同时限制租户与失败状态，审计失败不会删除外部对象或提交状态变更，旧对象清理改为提交后动作；异步 job 与下载保持原 API。真实 PostgreSQL Case 验证跨租户重放/download 拒绝、死信重放状态与审计回滚；Playwright 预置技术答卷进一步验证跨租户拒绝、DONE 下载、审计事件和凭据不泄露。
48. 为全局管理员补真实重评分成功 Case：隔离技术答卷包含完整答案项，`SUPER_ADMIN` 跨租户调用保留旧结果、生成新 calculation version、新报告和重评分审计；浏览器与 SQL 后置断言验证旧结果不被覆盖、当前结果唯一且 tenant 归属不变。该 Case 仍只证明技术能力，不代替专业审核。
49. 增加 V21 追加历史治理：Golden Case 修订、运行和发布审批新增三条 PostgreSQL 并发游标索引；保留旧 `/publication/history` API 兼容，并提供 `/history/cases`、`/history/runs`、`/history/reviews` 三个显式租户父链校验的 keyset 分页接口，单页上限 100，负游标返回稳定三语错误码。真实 PostgreSQL Case 验证多页 ID 顺序、无重复/遗漏、上限裁剪和迁移索引存在；Web 发布页改用首批 50 条有界查询并在存在后续游标时明确提示。
50. 增加可重复性能基线：`scripts/run-performance-baseline.sh` 只创建 `psy_perf_*` 隔离 schema，先用技术 fixture 测量 1x/10x，再执行实际任务/报告/预警/统计/群体统计/导出/通知/预约/被测者列表、答卷保存/提交评分 HTTP Case；`measure_http.py` 记录每个 Case 的 p50/p95/p99、串行吞吐和错误率，`collect_actuator.py` 通过受保护 Actuator 采集 Hikari/JVM/CPU，脚本另存三类 `EXPLAIN (ANALYZE, BUFFERS)` 和数据库资源快照。2026-08-11 默认 100/1,000 任务实跑 0 错误；结果明确记录本机 `pg_stat_statements` 未启用和生产容量不可外推。脚本结束自动删除隔离 schema，默认不触碰 `lx/public`。
51. 增加 V22 答卷质量决策运行时闭环：量表质量策略的 `ALLOW`、`PRORATE`、缺失比例、必答开关、最短/最长作答时长和 `INVALIDATE`/`REQUIRE_REVIEW`/`ALLOW_WITH_WARNING` 结果会在提交前评估；`PRORATE` 对总体和维度分数按已答题/权重折算；质量状态、问题码、缺失比例和作答时长写入答卷，并由结果快照继承。V23 在结果上追加可空 `scoring_trace_json`，保存算法版本、逐题原始/反向/加权分、维度聚合、缺失处理、常模选择和规则匹配证据，不保存自由文本答案；报告读取同一不可变内容快照，并按受控 `report_template` 代码区分默认、单分数、维度画像、常模画像和风险分流的 Web/Word/PDF/文本布局，重新生成报告保留量表特定解释和建议。缺少质量策略仍使用安全的 REJECT 默认值，`PENDING_PROFESSIONAL_REVIEW`、未审核常模、未知报告模板和未支持算法仍被发布门禁阻断。ScoreCalculator 单测、V1-V23 PostgreSQL 迁移和完整 387 测试已通过；正式量表仍受外部授权和专业审核阻塞。

## 6. 量表能力矩阵（实际代码）

| 能力 | 状态 | 代码事实与限制 |
|---|---|---|
| 简单求和、反向、加权、平均、加权平均 | 完全支持 | 评分器有显式方法白名单；不支持的方法阻止发布/评分；V22 对 PRORATE 量表保留权重比例 |
| 多维度、总体区间、Z 分、T 分、条件常模 | 部分支持 | 支持维度与年龄/性别/组织等常模匹配和缺失策略的维度平均；未支持百分位和自定义公式 |
| 量表结果解释、展示和个人报表模板 | 技术支持待治理审核 | `report_template` 白名单支持默认、单分数、维度画像、常模画像和风险分流；报告内容按答卷语言冻结，Web 与 Word/PDF/文本按模板输出；正式量表的解释、建议和模板仍需专业审核 |
| 单选、多选、滑块、矩阵、选项加文本、纯文本 | 部分支持 | 纯文本默认不计分；缺少独立 NUMBER、RANKING、跳题和显示条件模型 |
| 高风险规则、预警、干预关闭 | 部分支持 | 高风险规则会复制、校验、计入摘要；V16 增加逐条三语翻译与发布门禁；V11 支持经双审批的响应责任角色、时限、升级、联系/评估/交接/随访证据和 P0/P1 关闭门禁；真实责任人、联系方式和演练仍是外部治理项 |
| 缺失题、作答时长和效度质量门禁 | 技术支持待专业审核 | V22 运行 `ALLOW/PRORATE`、缺失比例、时长和三种无效动作，并留痕质量状态；V23 追加逐题评分轨迹；一致性/矛盾/反应模式规则仍未实现 |
| 量表版本冻结和评分追溯 | 新发布版本支持 | 任务引用具体 scale_id 并保存版本与摘要；V9 保留重评分历史；V23 追加可审计评分轨迹；旧已发布量表没有可证明的摘要 |
| 中、日、英三语 | 部分支持 | Web 主流程和后端错误消息已三语；Android 169 个资源 key/placeholder 静态门禁通过且中文硬编码为 0，但尚无 SDK 构建、截图和设备验证；V12/V13 已有量表三语模型和发布门禁，历史内容及真实翻译审核仍未录入 |
| 缺失处理、效度、异常时长、Golden Case | 部分支持 | V13 已实现六类 Golden Case、生产评分器运行与发布门禁；V22 已运行 `ALLOW/PRORATE`、缺失比例、作答时长和无效动作并留痕；一致性/矛盾/反应模式效度仍未实现，其他不支持配置继续阻止发布 |
| 来源、版权、授权、专业/业务双审批 | 技术门禁已实现，外部审核待完成 | V12 保存保守治理状态；V13 要求不同用户的咨询师与业务角色审批并绑定发布指纹。真实版权授权和专业资格仍必须由组织/法务核验 |
| SCL-90 专用指标 | 技术支持但外部阻塞 | 受限 `SCL90_PROFILE/1` 已计算 GSI、PST、PSDI、阳性症状数/均分并写入 V23 评分轨迹与报告指标；0–4 口径和 90 题/10 维度草稿已固化，但授权、三语复核、正式常模、总分区间和危机处置仍阻止发布 |
| EPQ、16PF、MMPI 等其他专用算法 | 待专业审核 | 无正式手册、授权常模和验证样例时只能标记不支持，不能退化为求和 |

## 7. 本轮运行证据与新增风险

- 本地现有 `lx` 只读 V9 预检查：一份答卷多结果 0，孤儿结果 0；未修改 public 数据。
- 本地现有 `lx/public` 只读 V11 预检查：待策略审查的开放预警 0、无期限高风险开放预警 0、需归档复核的历史关闭预警 0；`public.flyway_schema_history` 尚不存在，因此必须先人工确认并执行显式 baseline，不能让应用自动推断。
- 本地现有 `lx/public` 只读 V12 预检查：当前量表、已发布量表、常模和非内置计分量表均为 0；没有写入 public。
- SCL-90 来源草稿已通过 `python3 scripts/validate_scl90_source_package.py`：90 题、10 维度、zh-CN/ja-JP/en 三语矩阵和 4 个 Golden Case 结构完整；状态仍为 `DRAFT/BLOCKED_EXTERNAL`，不构成正式授权或临床支持。
- 隔离 schema 按 V1-V23 顺序执行成功：61 张表、99 个 `ck_psy_*` 约束、13 条已验证的历史租户外键、16 张直接租户表 `tenant_id NOT NULL`，并保留版本组当前版本唯一索引、导出待重试部分索引和三条历史游标索引；V16-V23 均已由 PostgreSQL 验证。
- 带 `PSY_POSTGRES_INTEGRATION=true` 且强制重跑的 `./gradlew test`：387 个测试，0 失败、0 错误、15 个条件 Case 跳过。Java 为 OpenJDK 21.0.11、PostgreSQL 18.4；已实跑的 PostgreSQL Case 覆盖 V1-V23 空库和 V1 baseline 预检后的增量迁移，另覆盖 V19 导出租约、V20 通知租约 fencing、V21 历史游标索引、V22 答卷/结果质量留痕、V23 评分轨迹 JSONB 约束以及既有租户、评分、ScalePackage、Golden Case、导入、种子和恢复分支。`TenantAccessPolicy`、两类 Worker 租约 fencing/自动重试/死信、通知 callback 脱敏/强制审计、无租户非全局角色重新评分阻断、合法全局过滤例外、可观测性和低基数指标等分支也有覆盖；`./gradlew bootJar` 通过。
- `npm test`：13 个文件、102 个测试全部通过；`npm run build` 通过。最新 `ScalePublicationPage` 为 19.60 kB / gzip 5.20 kB，`ScaleGovernancePage` 为 29.19 kB / gzip 8.32 kB，`ScaleListPage` 为 62.67 kB / gzip 13.00 kB；主入口 976.16 / gzip 297.50 kB，`ReportCharts` 1,150.22 kB / gzip 386.13 kB。
- `scripts/run-scale-package-e2e.sh` 在随机隔离 schema 运行 9 个 Playwright Chromium Case：ScalePackage v2 跨租户导出/导入、六类 Golden Case/独立双审核/发布/任务版本锁定、题目变化后旧 Case/审核失效与中日英 UI 原因、首次/已有草稿并发保存、非匿名高风险到干预关闭、不同 token 并发提交、匿名高风险无个人产物、中日英已审核内容与报告，以及日语高风险规则翻译驱动报告。核心 Case 进一步验证无租户非全局角色和跨租户角色不能重新评分、创建干预或导出报告，同租户其他用户不能读取通知；通知运维 feed/delivery、Push callback/批量重放、异步导出 job 的跨租户隐藏/零更新、死信重放与文件下载、全局管理员重评分、合法操作强制安全审计及 callback 凭据脱敏均已验证。后置 SQL 证明拒绝请求没有新增结果或干预，结果 `scoring_trace_json` 包含通用算法版本、题目/维度轨迹和规则匹配字段，导出 job/通知 delivery 与答卷租户一致且只有接收者可读，并继续验证发布摘要、审核人分离、任务快照、并发胜者、评分历史和语言元数据。页面 console/page error 为 0；最新 9/9 用时 36.2 秒。另有独立来源包 E2E 验证 SCL-90 草稿导入、三语选项矩阵和 4 个 Golden Case。量表发布 Case 同时修正了全局 mutation loading 导致所有行按钮进入 loading 的不稳定等待。
- `scripts/run-export-worker-recovery-rehearsal.sh` 已真实强杀阻塞在对象存储 PUT 的 JVM，并在重启后验证同一导出任务自动恢复为 `DONE`、`retry_count=1`、任务行数为 1；成功演练自动删除隔离 schema、进程和临时产物。
- `scripts/run-backup-restore-rehearsal.sh` 使用 PostgreSQL 18.4 在两个全新隔离数据库完成 V1-V23 custom-format 全库备份恢复：339,486 字节 dump，SHA-256 为 `005ddf8a86e7f6ec37a3a1f8a88e1c1a4b76806027bc09769ce0931af754557f`，备份 98 ms、恢复 179 ms，恢复开始至认证业务冒烟完成 3,788 ms。源/恢复库全部表行数、约束验证/非空属性、索引、序列和数据 dump 一致；23 条迁移及核心业务/审计/数据库内导出文件均通过断言，恢复库真实启动后完成两个租户冒烟。静态源边界 RPO 为 0；PITR、外部对象存储恢复、目标规模与旧版本应用回滚仍未验证。
- `data-psy.sql` 已在最新 schema 连续执行两次：三个租户分别生成 1 份 STRESS_DEMO、1 个任务和 1 份答卷，量表/导入/任务/答卷/预警/干预/预约/咨询/通知/导出的父子租户冲突为 0；测试结束 schema 残留为 0。`SCL90_TECH_DEMO` 保持 `DRAFT` 且 `current_version_flag=false`，不构成正式量表支持证据。
- `python3 android-app/scripts/verify_i18n.py`：169 个默认英语/日语/简体中文资源 key 与 placeholder 一致，主 Android 源码中文硬编码和旧翻译 Map 均为 0。当前共有 10 个答题校验及任务状态/ViewModel 单测；Java 21.0.11 已实测可用，但本机 `testDebugUnitTest` 在配置阶段因 `SDK location not found` 失败，Lint、APK 和单测均未标记为通过。
- 首次使用错误的默认角色 `lx` 失败；改用本机实际角色 `sainm` 后发现 Flyway 事务锁自阻塞和测试 schema 搜索路径问题。修复后 JDBC/Flyway 12 个 Case 与完整回归均真实通过。Flyway 10.20.1 对 PostgreSQL 18.4 输出兼容性警告，仍需在交付门禁中跟踪。
- P0 已修复：重新评分覆盖历史；量表新版本遗漏高风险规则；浏览器持久化敏感答案；不稳定提交 token；跨量表错误报告阈值；高风险关闭缺少处置证据门禁。
- P1 仍未闭环：真实量表三语内容、来源/授权、常模和 Golden Case 尚未由专业/业务/法务人员录入审核；缺失、效度、百分位和专用算法仍受门禁或模型能力限制。导出与通知 Worker 的进程终止/重启、Push 回执/状态通知、租户化重放、导出死信重放/download 以及全局管理员技术重评分成功路径已闭环；核心业务还缺更高并发压力。真实规模性能、PITR、外部对象存储恢复和旧版本应用回滚仍待实施。安全响应策略仍需组织录入真实 SLA、联系人并演练。Android 剩余拆分、SDK 构建、截图/设备/无障碍和 E2E 按用户要求暂不进入本轮范围。
- P1 仍未闭环：真实量表三语内容、来源/授权、常模和 Golden Case 尚未由专业/业务/法务人员录入审核；缺失、效度、百分位和专用算法仍受门禁或模型能力限制。导出与通知 Worker 的进程终止/重启、Push 回执/状态通知、租户化重放、导出死信重放/download 以及全局管理员技术重评分成功路径已闭环；Golden Case/审批历史已增加服务端游标 API 和 V21 索引，2026-08-11 已补隔离 1x/10x（100/1,000 技术任务）性能样本，但仍缺专用 PG `pg_stat_statements`、并发压力、优化前后对比和真实大数据测量。流式大导出、PITR、外部对象存储恢复和旧版本应用回滚仍待实施。安全响应策略仍需组织录入真实 SLA、联系人并演练。Android 剩余拆分、SDK 构建、截图/设备/无障碍和 E2E 按用户要求暂不进入本轮范围。

## 8. 后续阶段与验收门槛

### 阶段 2：租户约束硬化

核心入口显式租户过滤、V16 只读归属报告以及 V17/V18 分阶段 FK 验证/非空硬化已经实施。本地 `lx` 旧结构因业务表为空而通过只读预检，克隆升级与隔离种子均通过；其他真实历史环境仍必须独立保存逐表报告，并为任何非零项形成经业务确认的回填或隔离清单后才能执行 V17。结果重评分拒绝/全局成功路径、干预创建/关闭、通知所有权/运维读取/callback/批量重放、同步导出和异步 job 的死信重放/download 已具备相应租户、状态和审计证据，并继续保持匿名任务不生成可识别对象。

### 阶段 3：结构与时间

优先拆分 `ScaleRepository`、`AnswerSheetRepository`、`StatisticsService` 和 Android `PsyRespondentApp`。先提取 RowMapper、Query Repository、Renderer、Transition Policy 和 Clock，不改变 API DTO。

### 阶段 4：性能与容量

`scripts/run-performance-baseline.sh` 已在隔离 PostgreSQL schema 生成 1x/10x 技术数据，覆盖任务列表、深分页、答卷保存/提交评分、报告、预警、统计/群体统计、导出任务、通知、预约和被测者列表。2026-08-11 默认 100/1,000 目标任务、每个读 Case 15 次并发外串行样本均 0 错误；输出保存 p50/p95/p99、吞吐、错误率、Hikari/JVM/CPU、数据库读写/锁/连接快照和三类 `EXPLAIN (ANALYZE, BUFFERS)`。这只是可重复本机基线，尚未证明生产并发容量；`pg_stat_statements` 仍需在专用 PostgreSQL 实例启动时启用，流式大导出和同 Case 优化前后对比仍待实施。

### 阶段 5：交付与恢复

数据库内文件和同一制品恢复到新数据库的计时演练已经完成并加入 CI；继续增加容器镜像 digest、SBOM、蓝绿/滚动发布说明、PITR、外部对象存储恢复、上一版本应用兼容/回滚和失败自动停止。完成目标规模、外部文件和版本回滚演练后，才能宣称具备完整故障恢复闭环。
