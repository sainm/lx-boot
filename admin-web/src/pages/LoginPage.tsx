import { Alert, Button, Card, Form, Input, Radio, Select, Space, Typography } from "antd";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { passwordLogin } from "../auth/api";
import { showToast } from "../feedback/toast";
import { ADMIN_ROLE_OPTIONS, DEFAULT_ROLE, type AppRole } from "../auth/roles";
import { useSession } from "../auth/session";

type LocationState = {
  from?: string;
  reason?: "expired" | "unauthorized" | "logout";
  message?: string;
};

type LoginFormValues = {
  mode: "auth" | "dev";
  account?: string;
  password?: string;
  token?: string;
  role: AppRole;
};

function resolveSafeRedirect(from?: string) {
  if (!from || !from.startsWith("/") || from.startsWith("/login")) {
    return "/dashboard";
  }
  return from;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    currentRole,
    sessionSource,
    sessionHealth,
    isAuthenticated,
    authToken,
    authRequiredDetail,
    accessTokenRemainingMs,
    tokenLastSyncAt,
    setTokens,
    enableDevelopmentSession,
    disableDevelopmentSession,
    clearSession,
    clearAuthRequired
  } = useSession();
  const [form] = Form.useForm<LoginFormValues>();
  const state = (location.state as LocationState | null) ?? null;
  const infoMessage = state?.message || authRequiredDetail?.message;
  const loginMode = Form.useWatch("mode", form) ?? "auth";
  const allowDevMode = import.meta.env.DEV;
  const remainingText =
    typeof accessTokenRemainingMs === "number"
      ? `${Math.max(0, Math.floor(accessTokenRemainingMs / 1000))} seconds`
      : null;

  useEffect(() => {
    return () => {
      clearAuthRequired();
    };
  }, [clearAuthRequired]);

  useEffect(() => {
    if (isAuthenticated && (sessionSource === "server" || sessionSource === "dev")) {
      navigate(resolveSafeRedirect(state?.from), { replace: true });
    }
  }, [isAuthenticated, navigate, sessionSource, state?.from]);

  const handleLogin = async () => {
    const values = await form.validateFields();
    if (values.mode === "auth") {
      const result = await passwordLogin({
        principal: values.account?.trim() ?? "",
        password: values.password ?? ""
      });
      setTokens(result.accessToken, result.refreshToken);
      showToast("success", "Logged in with auth-starter.");
    } else {
      enableDevelopmentSession(values.role, values.token);
      showToast("success", "Entered development session mode.");
    }
    navigate(resolveSafeRedirect(state?.from), { replace: true });
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        background: "linear-gradient(135deg, #eef3f7 0%, #dfe9f3 100%)"
      }}
    >
      <Card style={{ width: 420 }}>
        <Space direction="vertical" size={20} style={{ width: "100%" }}>
          <div>
            <Typography.Title level={3}>Admin Login</Typography.Title>
            <Typography.Text type="secondary">
              The session first tries backend profile loading. If auth-starter is not available, it falls back to local development role mode.
            </Typography.Text>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              Session source: {sessionSource === "server" ? "backend" : sessionSource === "expired" ? "expired" : sessionSource === "dev" ? "development fallback" : sessionSource === "anonymous" ? "signed out" : "loading"}
            </Typography.Paragraph>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              Session health: {sessionHealth}
              {remainingText ? ` | Access token: ${remainingText}` : ""}
              {tokenLastSyncAt ? ` | Synced at: ${new Date(tokenLastSyncAt).toLocaleTimeString()}` : ""}
            </Typography.Paragraph>
          </div>
          {infoMessage ? (
            <Alert
              type={state?.reason === "logout" ? "info" : "warning"}
              showIcon
              message={infoMessage}
            />
          ) : null}
          <Form<LoginFormValues>
            form={form}
            layout="vertical"
            initialValues={{ mode: "auth", role: currentRole || DEFAULT_ROLE }}
          >
            {allowDevMode ? (
              <Form.Item label="Mode" name="mode">
                <Radio.Group
                  optionType="button"
                  buttonStyle="solid"
                  options={[
                    { label: "Auth Login", value: "auth" },
                    { label: "Dev Mode", value: "dev" }
                  ]}
                />
              </Form.Item>
            ) : null}
            {loginMode === "auth" ? (
              <>
                <Form.Item
                  label="Account"
                  name="account"
                  rules={[{ required: true, message: "Please enter your account" }]}
                >
                  <Input placeholder="username or email" />
                </Form.Item>
                <Form.Item
                  label="Password"
                  name="password"
                  rules={[{ required: true, message: "Please enter your password" }]}
                >
                  <Input.Password placeholder="password" />
                </Form.Item>
              </>
            ) : (
              <>
                <Form.Item
                  label="Access Token"
                  name="token"
                  extra="Optional. Use this only when you want to enter development mode with a pre-issued token."
                >
                  <Input.Password placeholder="Bearer token" />
                </Form.Item>
                <Form.Item label="Role" name="role" rules={[{ required: true, message: "Please choose a role" }]}>
                  <Select options={ADMIN_ROLE_OPTIONS} />
                </Form.Item>
              </>
            )}
            <Button type="primary" block onClick={() => void handleLogin()}>
              {loginMode === "auth" ? "Sign In" : "Enter Development Mode"}
            </Button>
            {authToken || sessionSource === "dev" ? (
              <Button
                block
                style={{ marginTop: 8 }}
                onClick={() =>
                  void (sessionSource === "server"
                    ? clearSession()
                    : Promise.resolve().then(() => {
                        disableDevelopmentSession();
                      }))
                }
              >
                Exit Current Session
              </Button>
            ) : null}
            {sessionSource === "server" ? (
              <Typography.Text type="secondary">
                A backend session is active. The role selector only remains here for development fallback.
              </Typography.Text>
            ) : null}
            {!allowDevMode ? (
              <Typography.Text type="secondary">
                Development mode is hidden in production builds.
              </Typography.Text>
            ) : null}
          </Form>
        </Space>
      </Card>
    </div>
  );
}
