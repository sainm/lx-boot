# 阶段 3 执行记录

阶段名称：后端核心主链路
执行日期：2026-03-27
使用 Prompt：`doc/prompt/03-backend-core-prompt.md`

输入文档：
- `doc/10-database-table-design.md`
- `doc/13-api-design-detailed.md`
- `doc/15-openapi-draft.yaml`
- 

阶段目标：
- 确认后端主链路结构与实现顺序
- 确认 auth-starter 集成方案

本阶段输出：
- 后端模块结构
- auth-starter 集成方案
- 主链路核心类设计
- 主链路接口实现顺序

确认结论：
- 后端采用单个 Spring Boot 工程实现
- 后端内部按业务模块分包，不优先拆 Gradle 多 module
- 数据访问统一采用 Spring JDBC + JdbcTemplate / NamedParameterJdbcTemplate
- auth-starter 负责账号、认证、权限、组织上下文，业务系统在其上扩展
- 第一阶段后端只实现主链路模块：
  `auth-integration`
  `scale`
  `assessment`
  `report`
  `warning`
  `common`

后端模块结构：

1. `config`
- Spring 配置
- JDBC 配置
- Web 配置
- 安全接入配置

2. `common`
- 通用响应结构
- 异常体系
- 分页对象
- 基础工具类

3. `auth`
- 当前登录用户上下文适配
- 与 auth-starter 的桥接封装
- 权限辅助类

4. `scale`
- 量表、维度、题目、选项、计分规则

5. `assessment`
- 测评任务
- 任务分配
- 答卷提交
- 测评结果

6. `report`
- 系统自动报告生成

7. `warning`
- 预警规则触发
- 预警记录生成

auth-starter 集成方案：

1. 不重写认证体系
- 登录、用户、角色、权限、组织能力全部复用 auth-starter

2. 后端业务系统的集成重点
- 读取当前登录用户信息
- 读取当前角色与权限
- 读取当前组织或租户上下文
- 在业务接口层面叠加自身权限控制和数据范围控制

3. 扩展方式建议
- 在业务系统中增加 `auth-integration` 包
- 封装 `CurrentUserFacade` 或类似适配层
- 不直接把业务逻辑散落到 auth-starter 内部

4. 权限控制建议
- 前端控制菜单可见性
- 后端控制接口访问权限
- 数据级权限在查询层进一步约束

主链路核心类设计：

1. `scale`
- `ScaleController`
- `ScaleService`
- `ScaleRepository`
- `ScaleDimensionRepository`
- `ScaleQuestionRepository`
- `ScaleOptionRepository`

2. `assessment`
- `AssessmentTaskController`
- `AssessmentTaskService`
- `AssessmentTaskRepository`
- `AnswerSheetController`
- `AnswerSheetService`
- `AnswerSheetRepository`
- `AssessmentResultRepository`

3. `report`
- `ReportService`
- `ReportRepository`
- `ScoringService`

4. `warning`
- `WarningService`
- `WarningRepository`
- `WarningRuleEvaluator`

主链路接口实现顺序：

1. 量表管理接口
- 创建量表
- 查询量表
- 查看量表详情

2. 测评任务接口
- 创建任务
- 按组分配
- 按个人分配
- 查询我的任务

3. 答题接口
- 获取题目
- 暂存答卷
- 提交答卷

4. 评分与报告接口
- 提交后自动评分
- 生成系统报告
- 查看报告详情

5. 预警接口
- 提交后生成预警记录
- 查询预警列表

代码落地优先顺序：

第 1 步：
- 建立后端工程结构
- 接入数据库
- 接入 auth-starter

第 2 步：
- 完成量表模块

第 3 步：
- 完成任务与分配模块

第 4 步：
- 完成答卷、评分、结果、报告模块

第 5 步：
- 完成预警生成模块

第 6 步：
- 做主链路联调与基础测试

测试建议：

1. 优先做 Service 层测试
2. 对主链路接口做集成测试
3. 优先验证：
- 量表创建
- 任务创建与分配
- 提交答卷
- 自动评分
- 报告生成
- 预警生成

发现问题：
- auth-starter 的具体依赖接入、配置类复用和权限注解复用，进入真正代码阶段时还需要结合实际工程再做一次细化
- 统计、预约、通知、审计不应提前混入主链路实现，否则会稀释第一阶段开发重心
- 报告与预警的生成边界要尽量放在答卷提交流程中统一收口，避免逻辑分散

需要回写的文档：
- `doc/process/03-current-progress-dashboard.md`
- `doc/process/01-prompt-driven-task-plan.md`

是否进入下一阶段：
- 是，主链路结构已明确，下一步可进入阶段 4 或直接开始代码骨架搭建

下一阶段准备事项：
- 如继续按文档流程，执行 `doc/prompt/04-backend-advanced-prompt.md`
- 如直接开始实现，可优先搭建后端工程骨架与主链路代码目录
