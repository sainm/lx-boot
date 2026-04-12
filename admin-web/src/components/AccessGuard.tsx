import { Button, Result, Space, Spin } from "antd";
import type { ReactNode } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { canAccess, getRoleLabel, type AppRole } from "../auth/roles";
import { useSession } from "../auth/session";
import { useI18n } from "../i18n/provider";

type Props = {
  roles: AppRole[];
  children: ReactNode;
};

export function AccessGuard({ roles, children }: Props) {
  const { t } = useI18n();
  const { currentRole, sessionSource, authRequiredDetail, isAuthenticated } = useSession();
  const location = useLocation();
  const navigate = useNavigate();

  if (sessionSource === "loading") {
    return (
      <div style={{ minHeight: 240, display: "grid", placeItems: "center" }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <span>{t("session.loading")}</span>
        </Space>
      </div>
    );
  }

  if (sessionSource === "expired") {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: authRequiredDetail?.from || location.pathname,
          reason: authRequiredDetail?.reason,
          message: authRequiredDetail?.message
        }}
      />
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  if (canAccess(roles, currentRole)) {
    return <>{children}</>;
  }

  return (
    <Result
      status="403"
      title={t("guard.forbiddenTitle")}
      subTitle={t("guard.forbiddenSubtitle", { role: getRoleLabel(currentRole, t) })}
      extra={
        <Space>
          <Button onClick={() => navigate("/login", { replace: true, state: { from: location.pathname } })}>
            {t("guard.goLogin")}
          </Button>
          <Button type="primary" onClick={() => navigate(currentRole === "USER" ? "/my/tasks" : "/dashboard", { replace: true })}>
            {t("guard.backDashboard")}
          </Button>
        </Space>
      }
    />
  );
}
