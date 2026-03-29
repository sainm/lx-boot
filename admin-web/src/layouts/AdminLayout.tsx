import { Button, Layout, Space, Spin, Tag, theme, Typography } from "antd";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import type { AppRoute } from "../app/route-config";
import { ROLE_LABELS } from "../auth/roles";
import { useSession } from "../auth/session";
import { AppMenu } from "../components/AppMenu";
import { useQuery } from "@tanstack/react-query";
import { fetchMyNotifications } from "../features/notifications/api";

const { Header, Sider, Content } = Layout;

type Props = {
  routes: AppRoute[];
};

export function AdminLayout({ routes }: Props) {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    currentRole,
    resetRole,
    clearSession,
    sessionSource,
    sessionHealth,
    authToken,
    authRequiredDetail,
    isAuthenticated,
    accessTokenRemainingMs,
    refreshTokenRemainingMs,
    tokenLastSyncAt
  } = useSession();
  const {
    token: { colorBgContainer, borderRadiusLG }
  } = theme.useToken();
  const visibleRoutes = routes.filter((route) => route.roles.includes(currentRole));

  const notificationsQuery = useQuery({
    queryKey: ["notifications", "my", "unread-count"],
    queryFn: fetchMyNotifications,
    refetchInterval: 60_000,
    enabled: isAuthenticated
  });
  const unreadNotificationCount = (notificationsQuery.data ?? []).filter((item) => !item.readFlag).length;
  const sessionLabel =
    sessionSource === "server"
      ? "backend"
      : sessionSource === "dev"
        ? "development"
        : sessionSource === "expired"
          ? "expired"
          : sessionSource === "anonymous"
            ? "signed out"
            : "loading";
  const healthColor =
    sessionHealth === "healthy"
      ? "green"
      : sessionHealth === "refreshing"
        ? "processing"
        : sessionHealth === "expiring"
          ? "gold"
          : sessionHealth === "development"
            ? "blue"
            : "default";
  const remainingText =
    typeof accessTokenRemainingMs === "number"
      ? `${Math.max(0, Math.floor(accessTokenRemainingMs / 1000))}s left`
      : null;
  const refreshedText = tokenLastSyncAt
    ? new Date(tokenLastSyncAt).toLocaleTimeString()
    : null;
  const refreshRemainingText =
    typeof refreshTokenRemainingMs === "number"
      ? `refresh ${Math.max(0, Math.floor(refreshTokenRemainingMs / 1000))}s`
      : null;

  if (sessionSource === "loading") {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <span>Checking session...</span>
        </Space>
      </div>
    );
  }

  if (!isAuthenticated || sessionSource === "expired") {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: location.pathname + location.search,
          reason: authRequiredDetail?.reason,
          message: authRequiredDetail?.message
        }}
      />
    );
  }

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider width={240} theme="light">
        <div style={{ padding: 20, fontSize: 18, fontWeight: 700 }}>Psychological Admin</div>
        <AppMenu routes={visibleRoutes} currentPath={location.pathname} onNavigate={(path) => navigate(path)} unreadNotificationCount={unreadNotificationCount} />
      </Sider>
      <Layout>
        <Header
          style={{
            padding: "0 24px",
            background: colorBgContainer,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between"
          }}
        >
          <Typography.Title level={4} style={{ margin: 0 }}>
            Psychological Assessment And Early Warning
          </Typography.Title>
          <Space>
            <Typography.Text type="secondary">
              Role: {ROLE_LABELS[currentRole]} | Session: {sessionLabel}
            </Typography.Text>
            <Tag color={healthColor}>{sessionHealth}</Tag>
            {remainingText ? <Typography.Text type="secondary">{remainingText}</Typography.Text> : null}
            {refreshRemainingText ? <Typography.Text type="secondary">{refreshRemainingText}</Typography.Text> : null}
            {refreshedText ? <Typography.Text type="secondary">Refreshed: {refreshedText}</Typography.Text> : null}
            {authToken ? <Typography.Text type="secondary">Token active</Typography.Text> : null}
            {sessionSource === "dev" ? (
              <Button type="link" size="small" onClick={resetRole}>
                Reset Role
              </Button>
            ) : null}
            <Button type="link" size="small" onClick={() => void clearSession()}>
              Logout
            </Button>
          </Space>
        </Header>
        <Content style={{ margin: 24 }}>
          <div
            style={{
              minHeight: 360,
              padding: 24,
              background: colorBgContainer,
              borderRadius: borderRadiusLG
            }}
          >
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}
