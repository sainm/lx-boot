# 文档卫生清单

## 1. 文档分层规则

后续维护时只保留两层：

- `doc/`：设计、规范、路线图
- `doc/process/`：当前真实状态、规则和长期维护说明

不要再往仓库里放这些内容：

- 一次性的 AI Prompt
- 历史阶段执行流水账
- 已被当前状态文档覆盖的单次评审/交付记录

## 2. 必须同步更新文档的场景

- 新增或删除主链路功能
- 权限边界发生变化
- 导出、通知、预约、审计等对外行为发生变化
- 构建、测试、启动方式发生变化
- 文档里的“未完成项”被真正落地

## 3. 更新顺序

1. 先改 [03-current-progress-dashboard.md](./03-current-progress-dashboard.md)
2. 再改 [04-baseline-closure.md](./04-baseline-closure.md)
3. 必要时再改 `README.md`、`backend/README.md`、`admin-web/README.md`
4. 如果设计口径也受影响，再改主设计文档和 roadmap

## 4. 删除标准

满足下面任意一条，就可以考虑删除：

- 只对某一次 AI 生成过程有意义
- 已被更新的状态文档完整覆盖
- 不再被索引和 README 引用
- 读者继续保留只会误判当前状态

## 5. 当前已执行的清理

本轮已经清理：

- `doc/prompt/`
- `doc/process/stage-*.md`
- `doc/process/01-prompt-driven-task-plan.md`
- `doc/process/02-stage-execution-log-template.md`
- `doc/process/05-delivery-checklist-2026-04-11.md`
- `doc/process/06-engineering-review-2026-04-11.md`
