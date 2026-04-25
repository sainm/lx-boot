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
const USER_MOBILE_PRIMARY_ROUTE_KEYS = new Set(["user-home", "my-tasks", "my-reports"]);

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
    () => (shell === "user" ? visibleMenuRoutes.filter((route) => USER_MOBILE_PRIMARY_ROUTE_KEYS.has(route.key)) : []),
    [shell, visibleMenuRoutes]
  );
  const mobileMoreRoutes = useMemo(
    () => (shell === "user" ? visibleMenuRoutes.filter((route) => !USER_MOBILE_PRIMARY_ROUTE_KEYS.has(route.key)) : []),
    [shell, visibleMenuRoutes]
  );
  const mobileBottomNavColumnCount = mobilePrimaryRoutes.length + (mobileMoreRoutes.length > 0 ? 1 : 0);
  const showUserBottomNav = shell === "user" && isMobile && mobilePrimaryRoutes.length > 0;
  const showUserTopNav = shell === "user" && !isMobile && visibleMenuRoutes.length > 0;
  const showSidebar = shell !== "user";
  const showUserMoreDrawer = shell === "user" && isMobile && mobileMoreRoutes.length > 0;
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
    <Layout
      className={`app-shell app-shell-${shell}${isMobile ? " app-shell-mobile" : ""}`}
      style={{
        minHeight: "100vh",
        background: shell === "user"
          ? "linear-gradient(180deg, #eef6f8 0%, #f7f9fc 44%, #f4f7fb 100%)"
          : "#eef3f7"
      }}
    >
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
      {showUserMoreDrawer ? (
        <Drawer
          placement="bottom"
          open={navOpen}
          onClose={() => setNavOpen(false)}
          height="auto"
          title={t("common.more")}
          styles={{ body: { padding: "8px 0 16px" } }}
        >
          <AppMenu
            routes={mobileMoreRoutes}
            selectedKey={activeMenuKey}
            onNavigate={(path) => {
              navigate(path);
              setNavOpen(false);
            }}
            unreadNotificationCount={unreadNotificationCount}
          />
        </Drawer>
      ) : null}
      <Layout>
        <Header
          className="app-shell-header"
          style={{
            padding: isMobile ? "max(10px, env(safe-area-inset-top)) 14px 10px" : "0 24px",
            minHeight: isMobile ? 58 : 64,
            height: "auto",
            lineHeight: "normal",
            background: isMobile ? "rgba(255, 255, 255, 0.9)" : colorBgContainer,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: isMobile ? 10 : 16,
            position: "sticky",
            top: 0,
            zIndex: 10,
            borderBottom: isMobile ? "1px solid rgba(203, 213, 225, 0.7)" : undefined,
            backdropFilter: isMobile ? "blur(16px)" : undefined
          }}
        >
          <Space size={isMobile ? 8 : 12} style={{ minWidth: 0 }}>
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
          <Space size={isMobile ? 6 : 8} wrap={!isMobile} style={{ justifyContent: "flex-end", flexShrink: 0 }}>
            <Select
              size="small"
              value={locale}
              options={[
                { value: "zh-CN", label: t("locale.zh-CN") },
                { value: "en-US", label: t("locale.en-US") }
              ]}
              onChange={setLocale}
              style={{ width: isMobile ? 92 : 112 }}
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
          className="app-shell-content"
          style={{
            margin: isMobile ? 0 : 24,
            marginBottom: showUserBottomNav ? 82 : isMobile ? 0 : 24
          }}
        >
          <div
            className="app-shell-content-surface"
            style={{
              minHeight: 360,
              padding: isMobile ? 12 : 24,
              background: isMobile ? "transparent" : colorBgContainer,
              borderRadius: isMobile ? 0 : borderRadiusLG
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
              padding: "7px 12px max(9px, env(safe-area-inset-bottom))",
              background: "rgba(255, 255, 255, 0.9)",
              borderTop: "1px solid rgba(226, 232, 240, 0.9)",
              boxShadow: "0 -14px 34px rgba(31, 74, 109, 0.12)",
              backdropFilter: "blur(12px)"
            }}
          >
            <div
              style={{
                display: "grid",
                gridTemplateColumns: `repeat(${mobileBottomNavColumnCount}, minmax(0, 1fr))`,
                gap: 6
              }}
            >
              {mobilePrimaryRoutes.map((route) => {
                const isActive = activeMenuKey === route.path;
                const label = t(route.labelKey);
                return (
                  <Button
                    key={route.path}
                    type="text"
                    aria-current={isActive ? "page" : undefined}
                    style={{
                      height: 56,
                      padding: "5px 4px 4px",
                      borderRadius: 18,
                      color: isActive ? accent : "#667085",
                      background: isActive ? "rgba(31, 95, 134, 0.1)" : "transparent",
                      boxShadow: isActive ? "inset 0 0 0 1px rgba(31, 95, 134, 0.08)" : "none"
                    }}
                    onClick={() => navigate(route.path)}
                  >
                    <span style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2, lineHeight: 1 }}>
                      <span style={{ fontSize: 20, height: 22, display: "inline-flex", alignItems: "center" }}>{route.icon}</span>
                      <span style={{ fontSize: 11, fontWeight: isActive ? 700 : 500, whiteSpace: "nowrap" }}>{label}</span>
                    </span>
                  </Button>
                );
              })}
              {mobileMoreRoutes.length > 0 ? (
                <Button
                  type="text"
                  aria-current={mobileMoreRoutes.some((route) => activeMenuKey === route.path) ? "page" : undefined}
                  style={{
                    height: 56,
                    padding: "5px 4px 4px",
                    borderRadius: 18,
                    color: mobileMoreRoutes.some((route) => activeMenuKey === route.path) ? accent : "#667085",
                    background: mobileMoreRoutes.some((route) => activeMenuKey === route.path)
                      ? "rgba(31, 95, 134, 0.1)"
                      : "transparent",
                    boxShadow: mobileMoreRoutes.some((route) => activeMenuKey === route.path)
                      ? "inset 0 0 0 1px rgba(31, 95, 134, 0.08)"
                      : "none"
                  }}
                  onClick={() => setNavOpen(true)}
                >
                  <span style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2, lineHeight: 1 }}>
                    <Badge count={unreadNotificationCount > 0 ? unreadNotificationCount : 0} size="small" offset={[8, -2]}>
                      <span style={{ fontSize: 20, height: 22, display: "inline-flex", alignItems: "center" }}>
                        <MenuOutlined />
                      </span>
                    </Badge>
                    <span
                      style={{
                        fontSize: 11,
                        fontWeight: mobileMoreRoutes.some((route) => activeMenuKey === route.path) ? 700 : 500,
                        whiteSpace: "nowrap"
                      }}
                    >
                      {t("common.more")}
                    </span>
                  </span>
                </Button>
              ) : null}
            </div>
          </div>
        ) : null}
      </Layout>
    </Layout>
  );
}
