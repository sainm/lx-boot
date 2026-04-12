# Prompt 驱动任务清单

## 1. 文档说明

这个文档记录项目最初按照 Prompt 分阶段推进的工作方式。  
现在代码已经不再停留在“纯规划阶段”，但这份清单仍然适合作为理解项目结构和后续拆任务的参考。

Prompt 目录：

- `doc/prompt/00-prompt-index.md`
- `doc/prompt/implementation-master-prompt.md`
- `doc/prompt/01-architecture-and-plan-prompt.md`
- `doc/prompt/02-database-design-prompt.md`
- `doc/prompt/03-backend-core-prompt.md`
- `doc/prompt/04-backend-advanced-prompt.md`
- `doc/prompt/05-react-admin-prompt.md`
- `doc/prompt/06-android-app-prompt.md`
- `doc/prompt/07-ios-app-prompt.md`
- `doc/prompt/08-miniapp-prompt.md`
- `doc/prompt/09-testing-and-delivery-prompt.md`

## 2. 原始阶段划分

1. 总体理解
2. 架构与实现规划
3. 数据库设计确认
4. 后端核心主链路
5. 后端扩展能力
6. React 管理端
7. Android 原生 App
8. iOS 原生 App
9. 微信小程序
10. 测试与交付

## 3. 当前阶段状态

| 阶段 | 内容 | 当前状态 | 说明 |
| --- | --- | --- | --- |
| 0 | 总体理解 | 已完成 | 文档、边界、路线已确认 |
| 1 | 架构与实现规划 | 已完成 | MVP 与模块边界已形成 |
| 2 | 数据库设计确认 | 已完成 | 结构已落到代码并可运行 |
| 3 | 后端核心主链路 | 已完成 | 核心业务已实现并通过测试 |
| 4 | 后端扩展能力 | 基本完成 | 预约、通知、统计、导出已具备 baseline |
| 5 | React 管理端 | 基本完成 | 管理端与用户侧 Web baseline 已落地 |
| 6 | Android 原生 App | 未开始 | 仅有文档规划 |
| 7 | iOS 原生 App | 未开始 | 仅有文档规划 |
| 8 | 微信小程序 | 未开始 | 仅有文档规划 |
| 9 | 测试与交付 | 基线完成 | 构建与测试已恢复，但 CI/CD 仍未完成 |

## 4. 当前适用方式

现在继续推进时，不建议再机械地按原始 0-9 顺序执行，而是按以下方式使用这套 Prompt：

1. 保留 Prompt 作为需求拆解和新模块设计参考。
2. 代码实现优先围绕现有 baseline 继续补强。
3. 新增任务优先处理：
   - 产品级收口
   - 工程化收口
   - 多语言维护
   - 多端扩展

## 5. 已经形成的基线

当前仓库已经不再只是“规划产物”，而是有明确可验证工程基线：

- 前端 `admin-web` 可以构建
- 后端 `backend` 可以完整跑通测试
- 管理端主链路可用
- 用户侧 Web 主链路可用
- 导出、通知、审计、会话页可用
- 前后端国际化基础设施可用

## 6. 下一步建议

后续继续按照 Prompt 工作时，建议只把它用作“设计输入”，不要再把未落地的阶段误认为已完成。

优先顺序建议：

1. 产品级收口：会话失效 UX、PDF 稳定性、预约咨询细节。
2. 工程级收口：CI、自动化检查、部署文档。
3. 分析能力补强：更深的统计图表与群体分析。
4. 多端落地：Android、iOS、微信小程序。
