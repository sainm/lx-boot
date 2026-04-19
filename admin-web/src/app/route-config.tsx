import {
  AlertOutlined,
  AuditOutlined,
  BarChartOutlined,
  BellOutlined,
  CalendarOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  FormOutlined,
  HomeOutlined,
  ReadOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined
} from "@ant-design/icons";
import { lazy, type ReactNode } from "react";
import type { AppRole } from "../auth/roles";

export type AppShell = "user" | "admin";

const DashboardPage = lazy(() => import("../pages/DashboardPage").then((module) => ({ default: module.DashboardPage })));
const ExportOpsPage = lazy(() => import("../pages/ExportOpsPage").then((module) => ({ default: module.ExportOpsPage })));
const GroupReportsPage = lazy(() =>
  import("../pages/GroupReportsPage").then((module) => ({ default: module.GroupReportsPage }))
);
const AppointmentPage = lazy(() => import("../pages/AppointmentPage").then((module) => ({ default: module.AppointmentPage })));
const AuthAuditPage = lazy(() => import("../pages/AuthAuditPage").then((module) => ({ default: module.AuthAuditPage })));
const LoginPage = lazy(() => import("../pages/LoginPage").then((module) => ({ default: module.LoginPage })));
const MyReportsPage = lazy(() =>
  import("../pages/MyReportsPage").then((module) => ({ default: module.MyReportsPage }))
);
const NotificationPage = lazy(() =>
  import("../pages/NotificationPage").then((module) => ({ default: module.NotificationPage }))
);
const ReportDetailPage = lazy(() =>
  import("../pages/ReportDetailPage").then((module) => ({ default: module.ReportDetailPage }))
);
const ScaleListPage = lazy(() => import("../pages/ScaleListPage").then((module) => ({ default: module.ScaleListPage })));
const SessionDetailPage = lazy(() =>
  import("../pages/SessionDetailPage").then((module) => ({ default: module.SessionDetailPage }))
);
const MyTaskListPage = lazy(() => import("../pages/MyTaskListPage").then((module) => ({ default: module.MyTaskListPage })));
const UserHomePage = lazy(() => import("../pages/UserHomePage").then((module) => ({ default: module.UserHomePage })));
const TaskQuestionPage = lazy(() => import("../pages/TaskQuestionPage").then((module) => ({ default: module.TaskQuestionPage })));
const TaskListPage = lazy(() => import("../pages/TaskListPage").then((module) => ({ default: module.TaskListPage })));
const WarningListPage = lazy(() => import("../pages/WarningListPage").then((module) => ({ default: module.WarningListPage })));
const UserManagementPage = lazy(() =>
  import("../pages/UserManagementPage").then((module) => ({ default: module.UserManagementPage }))
);

export type AppRoute = {
  key: string;
  path: string;
  labelKey: string;
  icon?: ReactNode;
  roles: AppRole[];
  shells: AppShell[];
  element: ReactNode;
  menu: boolean;
};

export const appRoutes: AppRoute[] = [
  {
    key: "user-home",
    path: "/home",
    labelKey: "route.user-home",
    icon: <HomeOutlined />,
    roles: ["USER"],
    shells: ["user"],
    element: <UserHomePage />,
    menu: true
  },
  {
    key: "my-tasks",
    path: "/my/tasks",
    labelKey: "route.my-tasks",
    icon: <FormOutlined />,
    roles: ["USER"],
    shells: ["user"],
    element: <MyTaskListPage />,
    menu: true
  },
  {
    key: "my-reports",
    path: "/my/reports",
    labelKey: "route.my-reports",
    icon: <ReadOutlined />,
    roles: ["USER"],
    shells: ["user"],
    element: <MyReportsPage />,
    menu: true
  },
  {
    key: "dashboard",
    path: "/dashboard",
    labelKey: "route.dashboard",
    icon: <HomeOutlined />,
    roles: ["ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    shells: ["admin"],
    element: <DashboardPage />,
    menu: true
  },
  {
    key: "scales",
    path: "/scales",
    labelKey: "route.scales",
    icon: <FileTextOutlined />,
    roles: ["ASSESSMENT_ADMIN", "SYS_ADMIN"],
    shells: ["admin"],
    element: <ScaleListPage />,
    menu: true
  },
  {
    key: "tasks",
    path: "/tasks",
    labelKey: "route.tasks",
    icon: <TeamOutlined />,
    roles: ["ASSESSMENT_ADMIN", "SYS_ADMIN"],
    shells: ["admin"],
    element: <TaskListPage />,
    menu: true
  },
  {
    key: "warnings",
    path: "/warnings",
    labelKey: "route.warnings",
    icon: <AlertOutlined />,
    roles: ["ASSESSMENT_ADMIN", "COUNSELOR", "SYS_ADMIN"],
    shells: ["admin"],
    element: <WarningListPage />,
    menu: true
  },
  {
    key: "group-reports",
    path: "/group-reports",
    labelKey: "route.group-reports",
    icon: <BarChartOutlined />,
    roles: ["ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    shells: ["admin"],
    element: <GroupReportsPage />,
    menu: true
  },
  {
    key: "appointments",
    path: "/appointments",
    labelKey: "route.appointments",
    icon: <CalendarOutlined />,
    roles: ["USER", "COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"],
    shells: ["user", "admin"],
    element: <AppointmentPage />,
    menu: true
  },
  {
    key: "export-ops",
    path: "/exports-center",
    labelKey: "route.export-ops",
    icon: <DatabaseOutlined />,
    roles: ["ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"],
    shells: ["admin"],
    element: <ExportOpsPage />,
    menu: true
  },
  {
    key: "notifications",
    path: "/notifications",
    labelKey: "route.notifications",
    icon: <BellOutlined />,
    roles: ["USER", "ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    shells: ["user", "admin"],
    element: <NotificationPage />,
    menu: true
  },
  {
    key: "user-admin",
    path: "/user-admin",
    labelKey: "route.user-admin",
    icon: <UserOutlined />,
    roles: ["ORG_MANAGER", "SYS_ADMIN"],
    shells: ["admin"],
    element: <UserManagementPage />,
    menu: true
  },
  {
    key: "auth-audit",
    path: "/auth-audit",
    labelKey: "route.auth-audit",
    icon: <AuditOutlined />,
    roles: ["ORG_MANAGER", "SYS_ADMIN"],
    shells: ["admin"],
    element: <AuthAuditPage />,
    menu: true
  },
  {
    key: "session",
    path: "/session",
    labelKey: "route.session",
    icon: <SafetyCertificateOutlined />,
    roles: ["ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    shells: ["admin"],
    element: <SessionDetailPage />,
    menu: true
  },
  {
    key: "reports",
    path: "/reports",
    labelKey: "route.report-detail",
    roles: ["USER", "ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    shells: ["user", "admin"],
    element: <ReportDetailPage />,
    menu: false
  },
  {
    key: "report-detail-id",
    path: "/reports/:reportId",
    labelKey: "route.report-detail",
    roles: ["USER", "ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    shells: ["user", "admin"],
    element: <ReportDetailPage />,
    menu: false
  },
  {
    key: "task-question",
    path: "/my/tasks/:taskId",
    labelKey: "route.task-question",
    roles: ["USER"],
    shells: ["user"],
    element: <TaskQuestionPage />,
    menu: false
  }
];

export function routesForShell(shell: AppShell) {
  return appRoutes.filter((route) => route.shells.includes(shell));
}

export const loginRoute = {
  path: "/login",
  element: <LoginPage />
};
