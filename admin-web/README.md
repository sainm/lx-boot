# admin-web

心理测评系统管理端前端。

## 当前状态

截至 2026-04-11：

- `npm run build` 通过
- 管理端主链路可用
- 用户侧 Web 主链路已接入同一个 React 工程
- 中英双语国际化基础设施已接通

## 技术栈

- **框架**：React 19 + TypeScript
- **构建工具**：Vite
- **UI 组件**：Ant Design 5
- **数据请求**：TanStack Query（React Query）+ Axios
- **路由**：React Router v7

## 本地开发

```bash
npm install
npm run dev
# 开发服务器：http://localhost:5173
# /api 和 /auth 自动代理到 http://localhost:8090（后端）
```

## 构建

```bash
npm run build
# 产物输出到 dist/
```

## 国际化

当前前端已支持：

- `zh-CN`
- `en-US`

基础设施位置：

- `src/i18n/messages.ts`
- `src/i18n/provider.tsx`
- `src/services/http.ts`

其中 `http.ts` 会自动透传 `Accept-Language`，新增请求应优先复用这一层。

完整接入说明见：

- [../doc/process/07-i18n-guide.md](../doc/process/07-i18n-guide.md)

## 目录结构

```
src/
├── auth/           登录态管理（SessionProvider、useSession）
├── components/     通用组件（Permission 权限控制等）
├── features/       各业务模块的 API 封装（axios 调用 + 类型定义）
│   ├── scales/
│   ├── assessment/
│   ├── appointments/
│   ├── counseling-records/
│   ├── warnings/
│   ├── notifications/
│   └── ...
├── pages/          页面组件（每个页面一个文件）
├── services/       axios 实例（http.ts、authAuditHttp.ts）
├── types/          通用类型（ApiResponse、PageResponse 等）
└── test/           测试配置（Vitest + jsdom）
```

## 页面路由

| 路径 | 页面 | 权限 |
|---|---|---|
| `/login` | 登录 | 公开 |
| `/dashboard` | 仪表盘 | 全角色 |
| `/scales` | 量表管理 | ASSESSMENT_ADMIN、SYS_ADMIN |
| `/tasks` | 测评任务管理 | ASSESSMENT_ADMIN、SYS_ADMIN |
| `/my/tasks` | 我的测评 | USER |
| `/my/reports` | 我的报告 | USER |
| `/warnings` | 预警管理 | COUNSELOR、ASSESSMENT_ADMIN、SYS_ADMIN |
| `/group-reports` | 群体报告 | ASSESSMENT_ADMIN、SYS_ADMIN |
| `/appointments` | 咨询预约 | 全角色 |
| `/notifications` | 通知 | 全角色 |
| `/session` | 会话详情 | 全角色 |
| `/auth-audit` | 认证审计 | SYS_ADMIN |

## 运行测试

```bash
npm run test
```
