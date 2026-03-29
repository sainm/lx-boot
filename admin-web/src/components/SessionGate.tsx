import { Result, Spin } from "antd";
import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useSession } from "../auth/session";

type Props = {
  children: ReactNode;
};

export function SessionGate({ children }: Props) {
  const { isAuthenticated, sessionSource, authRequiredDetail } = useSession();
  const location = useLocation();

  if (sessionSource === "loading") {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center" }}>
        <Spin size="large" tip="Loading session..." />
      </div>
    );
  }

  if (sessionSource === "expired") {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: authRequiredDetail?.from || location.pathname + location.search,
          reason: authRequiredDetail?.reason,
          message: authRequiredDetail?.message
        }}
      />
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
