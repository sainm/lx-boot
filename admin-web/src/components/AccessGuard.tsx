import { Button, Result, Space, Spin } from "antd";
import type { ReactNode } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { canAccess, type AppRole } from "../auth/roles";
import { useSession } from "../auth/session";

type Props = {
  roles: AppRole[];
  children: ReactNode;
};

export function AccessGuard({ roles, children }: Props) {
  const { currentRole, sessionSource, authRequiredDetail, isAuthenticated } = useSession();
  const location = useLocation();
  const navigate = useNavigate();

  if (sessionSource === "loading") {
    return (
      <div style={{ minHeight: 240, display: "grid", placeItems: "center" }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <span>Restoring session...</span>
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
      title="Current role cannot access this page"
      subTitle={`Current role is ${currentRole}. Please switch role or sign in with a different account.`}
      extra={
        <Space>
          <Button onClick={() => navigate("/login", { replace: true, state: { from: location.pathname } })}>
            Go To Login
          </Button>
          <Button type="primary" onClick={() => navigate("/dashboard", { replace: true })}>
            Back To Dashboard
          </Button>
        </Space>
      }
    />
  );
}
