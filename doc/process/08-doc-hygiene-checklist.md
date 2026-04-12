# 文档卫生检查清单

## 1. 目的

这份清单用于约束 `lx-boot` 后续的文档维护方式，避免再次出现：

- 设计文档与当前实现状态混淆
- README、过程文档、代码状态彼此不一致
- 新增能力已落地，但文档仍停留在旧阶段
- 历史规划被误读为“已经实现”

## 2. 文档分层规则

后续维护时，先判断你要改的是哪一类文档。

### 2.1 目标设计文档

目录：

- `doc/`
- `doc/prompt/`

用途：

- 描述目标业务范围
- 描述架构设计和规划路径
- 描述推荐实现方式

规则：

- 这类文档不直接代表当前实现完成度
- 如果文档内容容易被误解为“已经落地”，要加说明并指向 `doc/process/`

### 2.2 当前状态文档

目录：

- `doc/process/`
- 根目录 `README.md`
- `backend/README.md`
- `admin-web/README.md`

用途：

- 描述仓库当前真实状态
- 描述当前 baseline
- 描述已完成、未完成、可交付、待补强内容

规则：

- 这类文档必须尽量与代码和验证结果同步
- 一旦构建状态、测试状态、交付边界发生变化，应优先更新这里

## 3. 更新顺序

如果仓库发生真实变化，建议按下面顺序更新文档：

1. 先更新 `doc/process/03-current-progress-dashboard.md`
2. 再更新 `doc/process/04-baseline-closure.md`
3. 如涉及交付结论，再更新 `doc/process/05-delivery-checklist-*.md`
4. 如涉及工程风险，再更新 `doc/process/06-engineering-review-*.md`
5. 如涉及国际化规则，再更新 `doc/process/07-i18n-guide.md`
6. 最后回写 `README.md`、`backend/README.md`、`admin-web/README.md`

## 4. 什么时候必须改文档

出现下面任一情况时，应同步更新文档：

- 新增或删除主链路能力
- 构建命令、测试命令、启动方式发生变化
- 权限边界、角色边界发生变化
- 国际化范围新增或规则调整
- 导出、通知、预约、审计等对外可见行为发生变化
- Android / iOS / 微信小程序状态发生变化
- baseline 边界发生变化

## 5. 常见误区

### 5.1 不要把规划写成现状

例如：

- “Android 阶段已完成设计” 不等于 “Android 已实现”
- “技术栈包含 Redis” 不等于 “Redis 已在当前业务代码里实际发挥作用”

### 5.2 不要只改 README

README 只能做入口说明，不能替代过程文档。  
如果真实状态变了，应该先改 `doc/process/`，再回写 README。

### 5.3 不要让历史阶段记录冒充当前状态

`stage-*.md` 是历史执行记录，不是实时状态面板。  
如果这些文件可能误导阅读者，应该明确加“历史说明”。

## 6. 推荐检查项

每次准备提交文档前，至少检查下面几项：

- `README.md` 是否仍与当前工程状态一致
- `backend/README.md` 与 `admin-web/README.md` 是否与各自模块现状一致
- `doc/process/03-current-progress-dashboard.md` 是否仍准确
- `doc/process/04-baseline-closure.md` 是否仍准确
- 新增功能是否被误写进了设计文档，却没写进状态文档
- 是否出现乱码、无效链接、引用不存在文件

## 7. 当前推荐入口

如果新同事要快速了解项目，建议阅读顺序是：

1. `README.md`
2. `doc/process/00-process-index.md`
3. `doc/process/03-current-progress-dashboard.md`
4. `doc/process/04-baseline-closure.md`
5. `doc/process/07-i18n-guide.md`

## 8. 当前结论

截至 2026-04-11，当前文档卫生基线应视为：

- 过程文档可读
- 仓库首页说明已和当前状态对齐
- 阶段记录已标明历史属性
- 设计文档已标明“目标口径不等于当前实现”

后续新增文档时，也应遵守同样的分层规则。
