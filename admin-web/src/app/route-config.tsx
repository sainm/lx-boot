import {
  AlertOutlined,
  AuditOutlined,
  BarChartOutlined,
  BellOutlined,
  CalendarOutlined,
  DashboardOutlined,
  FileTextOutlined,
  FormOutlined,
  SafetyCertificateOutlined,
  TeamOutlined
} from "@ant-design/icons";
import { lazy, type ReactNode } from "react";
import type { AppRole } from "../auth/roles";

const DashboardPage = lazy(() => import("../pages/DashboardPage").then((module) => ({ default: module.DashboardPage })));
const GroupReportsPage = lazy(() =>
  import("../pages/GroupReportsPage").then((module) => ({ default: module.GroupReportsPage }))
);
const AppointmentPage = lazy(() => import("../pages/AppointmentPage").then((module) => ({ default: module.AppointmentPage })));
const AuthAuditPage = lazy(() => import("../pages/AuthAuditPage").then((module) => ({ default: module.AuthAuditPage })));
const LoginPage = lazy(() => import("../pages/LoginPage").then((module) => ({ default: module.LoginPage })));
const MyReportListPage = lazy(() =>
  import("../pages/MyReportListPage").then((module) => ({ default: module.MyReportListPage }))
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
const TaskQuestionPage = lazy(() => import("../pages/TaskQuestionPage").then((module) => ({ default: module.TaskQuestionPage })));
const TaskListPage = lazy(() => import("../pages/TaskListPage").then((module) => ({ default: module.TaskListPage })));
const WarningListPage = lazy(() => import("../pages/WarningListPage").then((module) => ({ default: module.WarningListPage })));

export type AppRoute = {
  key: string;
  path: string;
  label: string;
  icon?: ReactNode;
  roles: AppRole[];
  element: ReactNode;
  menu: boolean;
};

export const appRoutes: AppRoute[] = [
  {
    key: "my-tasks",
    path: "/my/tasks",
    label: "My Tasks",
    icon: <FormOutlined />,
    roles: ["USER"],
    element: <MyTaskListPage />,
    menu: true
  },
  {
    key: "my-reports",
    path: "/my/reports",
    label: "My Reports",
    roles: ["USER"],
    element: <MyReportListPage />,
    menu: true
  },
  {
    key: "dashboard",
    path: "/dashboard",
    label: "Dashboard",
    icon: <DashboardOutlined />,
    roles: ["ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    element: <DashboardPage />,
    menu: true
  },
  {
    key: "scales",
    path: "/scales",
    label: "Scale Management",
    icon: <FileTextOutlined />,
    roles: ["ASSESSMENT_ADMIN", "SYS_ADMIN"],
    element: <ScaleListPage />,
    menu: true
  },
  {
    key: "tasks",
    path: "/tasks",
    label: "Assessment Tasks",
    icon: <TeamOutlined />,
    roles: ["ASSESSMENT_ADMIN", "SYS_ADMIN"],
    element: <TaskListPage />,
    menu: true
  },
  {
    key: "warnings",
    path: "/warnings",
    label: "Warnings",
    icon: <AlertOutlined />,
    roles: ["ASSESSMENT_ADMIN", "COUNSELOR", "SYS_ADMIN"],
    element: <WarningListPage />,
    menu: true
  },
  {
    key: "group-reports",
    path: "/group-reports",
    label: "Group Reports",
    icon: <BarChartOutlined />,
    roles: ["ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    element: <GroupReportsPage />,
    menu: true
  },
  {
    key: "appointments",
    path: "/appointments",
    label: "Appointments",
    icon: <CalendarOutlined />,
    roles: ["USER", "COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"],
    element: <AppointmentPage />,
    menu: true
  },
  {
    key: "notifications",
    path: "/notifications",
    label: "Notifications",
    icon: <BellOutlined />,
    roles: ["USER", "ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    element: <NotificationPage />,
    menu: true
  },
  {
    key: "auth-audit",
    path: "/auth-audit",
    label: "Auth Audit",
    icon: <AuditOutlined />,
    roles: ["ORG_MANAGER", "SYS_ADMIN"],
    element: <AuthAuditPage />,
    menu: true
  },
  {
    key: "session",
    path: "/session",
    label: "Session Detail",
    icon: <SafetyCertificateOutlined />,
    roles: ["USER", "ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    element: <SessionDetailPage />,
    menu: true
  },
  {
    key: "reports",
    path: "/reports",
    label: "Report Detail",
    roles: ["USER", "ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    element: <ReportDetailPage />,
    menu: false
  },
  {
    key: "report-detail-id",
    path: "/reports/:reportId",
    label: "Report Detail",
    roles: ["USER", "ASSESSMENT_ADMIN", "COUNSELOR", "ORG_MANAGER", "SYS_ADMIN"],
    element: <ReportDetailPage />,
    menu: false
  },
  {
    key: "task-question",
    path: "/my/tasks/:taskId",
    label: "Task Questionnaire",
    roles: ["USER"],
    element: <TaskQuestionPage />,
    menu: false
  }
];

export const loginRoute = {
  path: "/login",
  element: <LoginPage />
};
