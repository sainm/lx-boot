import { Button, Result, Spin } from "antd";
import type { ReactNode } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";

type Props = {
  children: ReactNode;
};

export function SessionGate({ children }: Props) {
  const { isAuthenticated, sessionSource, authRequiredDetail } = useSession();
  const location = useLocation();
  const navigate = useNavigate();

  if (sessionSource === "loading") {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <Spin size="large" tip="Loading session..." />
      </div>
    );
  }

  if (sessionSource === "expired") {
    const from = authRequiredDetail?.from || location.pathname + location.search;
    const reason = authRequiredDetail?.reason ?? "expired";
    const msg = authRequiredDetail?.message ?? "Your session has expired. Please sign in again.";
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <Result
          status="warning"
          title="Session Expired"
          subTitle={msg}
          extra={
            <Button
              type="primary"
              onClick={() => navigate("/login", { replace: true, state: { from, reason, message: msg } })}
            >
              Sign In Again
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
          message: authRequiredDetail?.message ?? "Please sign in to continue."
        }}
      />
    );
  }

  if (sessionSource !== "server" && sessionSource !== "dev") {
    return <Result status="warning" title="Session state is unavailable." />;
  }

  return <>{children}</>;
}
