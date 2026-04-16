# admin-web

管理端与用户侧 Web 共用的 React 前端工程。

## 当前状态

当前已可用页面包括：

- Dashboard
- 量表、任务、预警、报告、群体报告、预约、审计
- 通知中心与通知运维工作台
- 导出中心与导出值守面板
- 用户侧 Web：我的任务、作答、我的报告、通知、预约

## 编译环境

- Node.js 24.14.1（当前仓库按本地开发版本对齐）
- npm（依赖由 `package-lock.json` 固定）
- Vite + TypeScript

## 本地启动

```bash
npm install
npm run dev
```

## 构建

```bash
npm run build
```

## 文档入口

- [../doc/process/03-current-progress-dashboard.md](../doc/process/03-current-progress-dashboard.md)
- [../doc/process/04-baseline-closure.md](../doc/process/04-baseline-closure.md)
- [../doc/process/07-i18n-guide.md](../doc/process/07-i18n-guide.md)
