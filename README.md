# 心理测评与预警系统

面向高校/企业场景的心理测评闭环平台，覆盖量表配置、测评任务下发、在线答题、自动评分、报告生成、风险预警、咨询预约、干预跟踪和群体统计分析。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Kotlin + Spring Boot 3 + Spring Security + Spring JDBC |
| 数据库 | PostgreSQL |
| 缓存 | Redis |
| 认证权限底座 | `auth-starter` (composite build) |
| 管理端前端 | React + TypeScript + Vite |
| 移动端 | Android (Kotlin) / iOS (Swift) / 微信小程序 |

## 项目结构

```
lx-boot/
├── backend/          # Spring Boot 后端（单体，按业务模块分包）
├── admin-web/        # PC Web 管理端（React + Vite）
└── doc/              # 设计文档
```

## 本地启动

### 前置条件

- JDK 21+
- Node.js 18+
- PostgreSQL
- Redis
- `auth-starter` 源码（与本工程同级目录，用于 composite build）

### 后端

```bash
cd backend

# 覆盖环境变量（或直接修改 application.yml）
export PSY_DB_URL=jdbc:postgresql://127.0.0.1:5432/your_db
export PSY_DB_USERNAME=your_user
export PSY_DB_PASSWORD=your_password
export PSY_JWT_SECRET=your-jwt-secret-at-least-32-chars

./gradlew bootRun
# 服务启动于 http://localhost:8090
```

> 首次启动会自动执行 `schema-psy.sql` 初始化业务表。
> 需提前手动执行 `auth-starter` 的认证权限表 DDL。

### 管理端前端

```bash
cd admin-web
npm install
npm run dev
# 开发服务器启动于 http://localhost:5173
```

## 环境变量说明

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PSY_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/auth_starter` | 数据库连接地址 |
| `PSY_DB_USERNAME` | `auth_starter_app` | 数据库用户名 |
| `PSY_DB_PASSWORD` | `AuthStarter@2026` | 数据库密码 |
| `PSY_REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `PSY_REDIS_PORT` | `6379` | Redis 端口 |
| `PSY_JWT_SECRET` | `change-me-...` | JWT 签名密钥（生产必须替换） |

## 业务闭环

```
量表配置 → 任务下发 → 在线作答 → 自动评分
    → 系统报告 → 预警识别 → 咨询预约
    → 干预记录 → 结案/复测 → 群体统计
```

## 主要角色

- **被测者** — 参与测评的学生或员工
- **咨询师** — 查看报告、预约咨询、记录干预
- **测评管理员** — 配置量表、分配任务、处理预警
- **管理人员** — 查看群体报告与统计
- **系统管理员** — 用户、角色与权限管理
