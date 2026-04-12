# 项目过程索引

## 1. 目的

本目录用于记录 `lx-boot` 从方案梳理、Prompt 执行、工程落地到交付收口的过程文档。  
当前文档状态已按 2026-04-11 的仓库实际情况重新整理，可作为后续维护入口。

## 2. 过程文档

- [01-prompt-driven-task-plan.md](./01-prompt-driven-task-plan.md)
  - Prompt 驱动的阶段任务清单与当前适用方式。
- [02-stage-execution-log-template.md](./02-stage-execution-log-template.md)
  - 阶段执行记录模板，供后续迭代复用。
- [03-current-progress-dashboard.md](./03-current-progress-dashboard.md)
  - 当前进度总览、已完成项、未完成项、风险与下一步。
- [04-baseline-closure.md](./04-baseline-closure.md)
  - 当前 baseline 的定义、范围、验证结果与后续补强点。
- [05-delivery-checklist-2026-04-11.md](./05-delivery-checklist-2026-04-11.md)
  - 可交付能力、建议补强项、未完成项。
- [06-engineering-review-2026-04-11.md](./06-engineering-review-2026-04-11.md)
  - 工程审查结论、风险、改进建议。
- [07-i18n-guide.md](./07-i18n-guide.md)
  - 中英双语国际化接入与维护指南。
- [08-doc-hygiene-checklist.md](./08-doc-hygiene-checklist.md)
  - 文档卫生规则、更新顺序与常见误区。

## 3. 阶段执行记录

- [stage-00-execution-log.md](./stage-00-execution-log.md)
- [stage-01-execution-log.md](./stage-01-execution-log.md)
- [stage-02-execution-log.md](./stage-02-execution-log.md)
- [stage-03-execution-log.md](./stage-03-execution-log.md)
- [stage-04-execution-log.md](./stage-04-execution-log.md)
- [stage-05-execution-log.md](./stage-05-execution-log.md)
- [stage-06-execution-log.md](./stage-06-execution-log.md)
- [stage-07-execution-log.md](./stage-07-execution-log.md)
- [stage-08-execution-log.md](./stage-08-execution-log.md)
- [stage-09-execution-log.md](./stage-09-execution-log.md)

## 4. 推荐使用顺序

1. 先看 [01-prompt-driven-task-plan.md](./01-prompt-driven-task-plan.md)，了解项目最初的阶段划分。
2. 再看 [03-current-progress-dashboard.md](./03-current-progress-dashboard.md) 和 [04-baseline-closure.md](./04-baseline-closure.md)，快速判断当前真实状态。
3. 如果要评估是否可交付，查看 [05-delivery-checklist-2026-04-11.md](./05-delivery-checklist-2026-04-11.md)。
4. 如果要继续做国际化，直接参考 [07-i18n-guide.md](./07-i18n-guide.md)。

## 5. 当前结论

截至 2026-04-11：

- `admin-web` 构建通过：`npm run build`
- `backend` 测试通过：`./gradlew test --rerun-tasks`
- 前后端主链路已具备稳定 baseline
- 中英双语国际化基础设施已接通
- Android / iOS / 微信小程序仍未落地
