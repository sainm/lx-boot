import { Alert, Button, Card, Form, Grid, Input, Radio, Select, Space, Tag, Typography } from "antd";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { passwordLogin } from "../auth/api";
import { DEFAULT_ROLE, getAdminRoleOptions, type AppRole } from "../auth/roles";
import { useSession } from "../auth/session";
import { showToast } from "../feedback/toast";
import { useI18n } from "../i18n/provider";

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
  const { locale, setLocale, t } = useI18n();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
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
      ? `${Math.max(0, Math.floor(accessTokenRemainingMs / 1000))}s`
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
      showToast("success", t("login.success.auth"));
    } else {
      enableDevelopmentSession(values.role, values.token);
      showToast("success", t("login.success.dev"));
    }
    navigate(resolveSafeRedirect(state?.from), { replace: true });
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: isMobile ? 16 : 24,
        background:
          "radial-gradient(circle at top left, rgba(31,74,109,0.16), transparent 36%), linear-gradient(160deg, #f7fafc 0%, #e4edf5 48%, #d8e4ee 100%)"
      }}
    >
      <Card style={{ width: "100%", maxWidth: isMobile ? 520 : 420 }} bodyStyle={{ padding: isMobile ? 20 : 24 }}>
        <Space direction="vertical" size={20} style={{ width: "100%" }}>
          <div>
            <Space direction="vertical" size={10} style={{ width: "100%" }}>
              <Tag color="blue" style={{ width: "fit-content", paddingInline: 10 }}>
                {t("app.userBrand")}
              </Tag>
              <Typography.Title level={isMobile ? 3 : 3} style={{ margin: 0 }}>
                {t("login.title")}
              </Typography.Title>
            </Space>
            <Typography.Text type="secondary">{t("login.subtitle")}</Typography.Text>
            <Space direction={isMobile ? "vertical" : "horizontal"} size={[8, 8]} wrap style={{ marginTop: 12, width: "100%" }}>
              <Tag>{t("session.status")}: {t(`session.source.${sessionSource}`)}</Tag>
              <Tag color={sessionHealth === "healthy" ? "green" : sessionHealth === "refreshing" ? "processing" : sessionHealth === "expiring" ? "gold" : sessionHealth === "development" ? "blue" : "default"}>
                {t("login.sessionHealth")}: {t(`session.health.${sessionHealth}`)}
              </Tag>
            </Space>
            {remainingText || tokenLastSyncAt ? (
              <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
                {remainingText ? `${t("login.accessTokenRemaining")}: ${remainingText}` : ""}
                {remainingText && tokenLastSyncAt ? " | " : ""}
                {tokenLastSyncAt ? `${t("login.syncedAt")}: ${new Date(tokenLastSyncAt).toLocaleTimeString(locale)}` : ""}
              </Typography.Paragraph>
            ) : null}
          </div>
          <Select
            value={locale}
            onChange={setLocale}
            options={[
              { value: "zh-CN", label: t("locale.zh-CN") },
              { value: "en-US", label: t("locale.en-US") }
            ]}
            style={{ width: 120 }}
            aria-label={t("locale.label")}
          />
          {infoMessage ? <Alert type={state?.reason === "logout" ? "info" : "warning"} showIcon message={infoMessage} /> : null}
          <Form<LoginFormValues>
            form={form}
            layout="vertical"
            initialValues={{ mode: "auth", role: currentRole || DEFAULT_ROLE }}
          >
            {allowDevMode ? (
              <Form.Item label={t("login.mode")} name="mode">
                <Radio.Group
                  block={isMobile}
                  optionType="button"
                  buttonStyle="solid"
                  options={[
                    { label: t("login.mode.auth"), value: "auth" },
                    { label: t("login.mode.dev"), value: "dev" }
                  ]}
                />
              </Form.Item>
            ) : null}
            {loginMode === "auth" ? (
              <>
                <Form.Item
                  label={t("login.account")}
                  name="account"
                  rules={[{ required: true, message: t("login.accountRequired") }]}
                >
                  <Input size={isMobile ? "large" : "middle"} placeholder={t("login.accountPlaceholder")} />
                </Form.Item>
                <Form.Item
                  label={t("login.password")}
                  name="password"
                  rules={[{ required: true, message: t("login.passwordRequired") }]}
                >
                  <Input.Password size={isMobile ? "large" : "middle"} placeholder={t("login.passwordPlaceholder")} />
                </Form.Item>
              </>
            ) : (
              <>
                <Form.Item label={t("login.accessToken")} name="token" extra={t("login.accessTokenExtra")}>
                  <Input.Password size={isMobile ? "large" : "middle"} placeholder={t("login.accessTokenPlaceholder")} />
                </Form.Item>
                <Form.Item label={t("login.role")} name="role" rules={[{ required: true, message: t("login.roleRequired") }]}>
                  <Select size={isMobile ? "large" : "middle"} options={getAdminRoleOptions(t)} />
                </Form.Item>
              </>
            )}
            <Button type="primary" size={isMobile ? "large" : "middle"} block onClick={() => void handleLogin()}>
              {loginMode === "auth" ? t("login.signIn") : t("login.enterDevMode")}
            </Button>
            {authToken || sessionSource === "dev" ? (
              <Button
                block
                size={isMobile ? "large" : "middle"}
                style={{ marginTop: 8 }}
                onClick={() =>
                  void (sessionSource === "server"
                    ? clearSession()
                    : Promise.resolve().then(() => {
                        disableDevelopmentSession();
                      }))
                }
              >
                {t("login.exitCurrentSession")}
              </Button>
            ) : null}
            {sessionSource === "server" ? <Typography.Text type="secondary">{t("login.backendSessionActive")}</Typography.Text> : null}
            {!allowDevMode ? <Typography.Text type="secondary">{t("login.devModeHidden")}</Typography.Text> : null}
          </Form>
        </Space>
      </Card>
    </div>
  );
}
