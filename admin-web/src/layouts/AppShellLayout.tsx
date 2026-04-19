import { MenuOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Badge, Button, Drawer, Grid, Layout, Select, Space, Spin, Tag, theme, Typography } from "antd";
import { useMemo, useState, type ReactNode } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import type { AppRoute, AppShell } from "../app/route-config";
import { getRoleLabel } from "../auth/roles";
import { useSession } from "../auth/session";
import { AppMenu } from "../components/AppMenu";
import { fetchMyNotifications } from "../features/notifications/api";
import { useI18n } from "../i18n/provider";

const { Header, Sider, Content } = Layout;
const USER_MOBILE_NAV_LIMIT = 4;

type Props = {
  routes: AppRoute[];
  shell: AppShell;
  titleKey: string;
  brandKey: string;
  accent: string;
  responsive?: boolean;
  hero?: ReactNode;
};

export function AppShellLayout({ routes, shell, titleKey, brandKey, accent, responsive = true, hero }: Props) {
  const { locale, setLocale, t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  const {
    currentRole,
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
  const screens = Grid.useBreakpoint();
  const isMobile = responsive ? !screens.md : false;
  const isPad = responsive ? Boolean(screens.md && !screens.lg) : false;
  const isUserView = currentRole === "USER";
  const visibleRoutes = useMemo(
    () => routes.filter((route) => route.roles.includes(currentRole)),
    [routes, currentRole]
  );
  const visibleMenuRoutes = useMemo(() => visibleRoutes.filter((route) => route.menu), [visibleRoutes]);
  const activeMenuKey = useMemo(() => {
    const exactRoute = visibleMenuRoutes.find((route) => route.path === location.pathname);
    if (exactRoute) {
      return exactRoute.path;
    }
    const matchedRoute = [...visibleMenuRoutes]
      .sort((left, right) => right.path.length - left.path.length)
      .find((route) => location.pathname.startsWith(`${route.path}/`));
    return matchedRoute?.path ?? location.pathname;
  }, [location.pathname, visibleMenuRoutes]);
  const mobilePrimaryRoutes = useMemo(
    () => (shell === "user" ? visibleMenuRoutes.slice(0, USER_MOBILE_NAV_LIMIT) : []),
    [shell, visibleMenuRoutes]
  );
  const mobileMoreRoutes = useMemo(
    () => (shell === "user" ? visibleMenuRoutes.slice(USER_MOBILE_NAV_LIMIT) : []),
    [shell, visibleMenuRoutes]
  );
  const showUserBottomNav = shell === "user" && isMobile && mobilePrimaryRoutes.length > 0;
  const showUserTopNav = shell === "user" && !isMobile && visibleMenuRoutes.length > 0;
  const showSidebar = shell !== "user";
  const showMobileMenuButton = isMobile && (!showUserBottomNav || mobileMoreRoutes.length > 0);

  const notificationsQuery = useQuery({
    queryKey: ["notifications", "my", "unread-count"],
    queryFn: fetchMyNotifications,
    refetchInterval: 60_000,
    enabled: isAuthenticated
  });
  const unreadNotificationCount = (notificationsQuery.data ?? []).filter((item) => !item.readFlag).length;
  const healthColor =
    sessionHealth === "healthy"
      ? "green"
      : sessionHealth === "refreshing"
        ? "processing"
        : sessionHealth === "expiring"
          ? "gold"
          : "default";
  const remainingText =
    typeof accessTokenRemainingMs === "number"
      ? t("session.accessRemaining", { seconds: Math.max(0, Math.floor(accessTokenRemainingMs / 1000)) })
      : null;
  const refreshedText = tokenLastSyncAt ? new Date(tokenLastSyncAt).toLocaleTimeString(locale) : null;
  const refreshRemainingText =
    typeof refreshTokenRemainingMs === "number"
      ? t("session.refreshRemaining", { seconds: Math.max(0, Math.floor(refreshTokenRemainingMs / 1000)) })
      : null;

  if (sessionSource === "loading") {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <span>{t("session.checking")}</span>
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

  const menuNode = (
    <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <div
        style={{
          padding: isMobile ? 16 : 20,
          fontSize: isPad ? 16 : 18,
          fontWeight: 700,
          color: "#fff",
          background: accent
        }}
      >
        {t(brandKey)}
      </div>
      <div style={{ flex: 1, overflow: "auto", paddingTop: 8 }}>
        <AppMenu
          routes={visibleMenuRoutes}
          selectedKey={activeMenuKey}
          onNavigate={(path) => {
            navigate(path);
            setNavOpen(false);
          }}
          unreadNotificationCount={unreadNotificationCount}
        />
      </div>
    </div>
  );

  return (
    <Layout style={{ minHeight: "100vh", background: shell === "user" ? "#f6f8fb" : "#eef3f7" }}>
      {showSidebar && isMobile ? (
        <Drawer
          placement="left"
          open={navOpen}
          onClose={() => setNavOpen(false)}
          width={260}
          styles={{ body: { padding: 0 } }}
        >
          {menuNode}
        </Drawer>
      ) : showSidebar ? (
        <Sider width={isPad ? 216 : 240} theme="light">
          {menuNode}
        </Sider>
      ) : null}
      <Layout>
        <Header
          style={{
            padding: isMobile ? "max(8px, env(safe-area-inset-top)) 16px 0" : "0 24px",
            background: colorBgContainer,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 16,
            position: "sticky",
            top: 0,
            zIndex: 10
          }}
        >
          <Space size={12} style={{ minWidth: 0 }}>
            {showSidebar && showMobileMenuButton ? (
              <Button type="text" icon={<MenuOutlined />} onClick={() => setNavOpen(true)} />
            ) : null}
            <div style={{ minWidth: 0 }}>
              <Typography.Title level={4} style={{ margin: 0, fontSize: isMobile ? 18 : 22 }}>
                {t(titleKey)}
              </Typography.Title>
              {hero ? (
                <Typography.Text type="secondary" style={{ display: isMobile ? "none" : "block" }}>
                  {hero}
                </Typography.Text>
              ) : null}
            </div>
          </Space>
          <Space size={8} wrap style={{ justifyContent: "flex-end" }}>
            <Select
              size="small"
              value={locale}
              options={[
                { value: "zh-CN", label: t("locale.zh-CN") },
                { value: "en-US", label: t("locale.en-US") }
              ]}
              onChange={setLocale}
              style={{ width: 112 }}
              aria-label={t("locale.label")}
            />
            {!isMobile && !isUserView ? (
              <Typography.Text type="secondary">
                {t("session.role")}: {getRoleLabel(currentRole, t)}
              </Typography.Text>
            ) : null}
            {!showUserBottomNav && !isUserView ? <Tag color={healthColor}>{t(`session.health.${sessionHealth}`)}</Tag> : null}
            {!isMobile && !isUserView && remainingText ? <Typography.Text type="secondary">{remainingText}</Typography.Text> : null}
            {!isMobile && !isUserView && refreshRemainingText ? <Typography.Text type="secondary">{refreshRemainingText}</Typography.Text> : null}
            {!isMobile && !isUserView && refreshedText ? <Typography.Text type="secondary">{t("session.refreshed")}: {refreshedText}</Typography.Text> : null}
            {!isMobile && !isUserView && authToken ? <Typography.Text type="secondary">{t("session.tokenActive")}</Typography.Text> : null}
            <Button type="link" size="small" onClick={() => void clearSession()}>
              {t("session.logout")}
            </Button>
          </Space>
        </Header>
        {showUserTopNav ? (
          <div
            style={{
              padding: "12px 24px 0",
              background: colorBgContainer,
              borderBottom: "1px solid #eef2f6"
            }}
          >
            <div
              style={{
                display: "flex",
                gap: 12,
                flexWrap: "wrap"
              }}
            >
              {visibleMenuRoutes.map((route) => {
                const isActive = activeMenuKey === route.path;
                const label = t(route.labelKey);
                const content =
                  route.key === "notifications" && unreadNotificationCount > 0 ? (
                    <Badge count={unreadNotificationCount} size="small" offset={[10, 0]}>
                      <span>{label}</span>
                    </Badge>
                  ) : (
                    label
                  );
                return (
                  <Button
                    key={route.path}
                    type={isActive ? "primary" : "default"}
                    icon={route.icon}
                    onClick={() => navigate(route.path)}
                    style={{
                      borderRadius: 999,
                      minWidth: 104
                    }}
                  >
                    {content}
                  </Button>
                );
              })}
            </div>
          </div>
        ) : null}
        <Content
          style={{
            margin: isMobile ? 12 : 24,
            marginBottom: showUserBottomNav ? 88 : isMobile ? 12 : 24
          }}
        >
          <div
            style={{
              minHeight: 360,
              padding: isMobile ? 16 : 24,
              background: colorBgContainer,
              borderRadius: borderRadiusLG
            }}
          >
            <Outlet />
          </div>
        </Content>
        {showUserBottomNav ? (
          <div
            style={{
              position: "sticky",
              bottom: 0,
              zIndex: 20,
              padding: "8px 12px max(10px, env(safe-area-inset-bottom))",
              background: "rgba(255, 255, 255, 0.96)",
              borderTop: "1px solid #e8edf4",
              boxShadow: "0 -10px 30px rgba(31, 74, 109, 0.08)",
              backdropFilter: "blur(12px)"
            }}
          >
            <div
              style={{
                display: "grid",
                gridTemplateColumns: `repeat(${mobilePrimaryRoutes.length}${mobileMoreRoutes.length > 0 ? " 1" : ""}, minmax(0, 1fr))`,
                gap: 8
              }}
            >
              {mobilePrimaryRoutes.map((route) => {
                const isActive = activeMenuKey === route.path;
                const label = t(route.labelKey);
                const content =
                  route.key === "notifications" && unreadNotificationCount > 0 ? (
                    <Badge count={unreadNotificationCount} size="small" offset={[10, -2]}>
                      <span>{label}</span>
                    </Badge>
                  ) : (
                    label
                  );
                return (
                  <Button
                    key={route.path}
                    type={isActive ? "primary" : "default"}
                    size="large"
                    icon={route.icon}
                    style={{ height: 52 }}
                    onClick={() => navigate(route.path)}
                  >
                    {content}
                  </Button>
                );
              })}
              {mobileMoreRoutes.length > 0 ? (
                <Button size="large" icon={<MenuOutlined />} style={{ height: 52 }} onClick={() => setNavOpen(true)}>
                  {t("common.more")}
                </Button>
              ) : null}
            </div>
          </div>
        ) : null}
      </Layout>
    </Layout>
  );
}
