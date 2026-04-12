import { Button, Result, Spin } from "antd";
import type { ReactNode } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import { useI18n } from "../i18n/provider";

type Props = {
  children: ReactNode;
};

export function SessionGate({ children }: Props) {
  const { t } = useI18n();
  const { isAuthenticated, sessionSource, authRequiredDetail } = useSession();
  const location = useLocation();
  const navigate = useNavigate();

  if (sessionSource === "loading") {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <Spin size="large" tip={t("session.loading")} />
      </div>
    );
  }

  if (sessionSource === "expired") {
    const from = authRequiredDetail?.from || location.pathname + location.search;
    const reason = authRequiredDetail?.reason ?? "expired";
    const msg = authRequiredDetail?.message ?? t("session.expiredMessage");
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <Result
          status="warning"
          title={t("session.expiredTitle")}
          subTitle={msg}
          extra={
            <Button type="primary" onClick={() => navigate("/login", { replace: true, state: { from, reason, message: msg } })}>
              {t("session.expiredAction")}
            </Button>
          }
        />
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: location.pathname + location.search,
          reason: authRequiredDetail?.reason ?? "unauthorized",
          message: authRequiredDetail?.message ?? t("session.signInRequired")
        }}
      />
    );
  }

  if (sessionSource !== "server" && sessionSource !== "dev") {
    return <Result status="warning" title={t("session.unavailable")} />;
  }

  return <>{children}</>;
}
