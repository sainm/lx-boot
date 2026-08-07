import { Alert, Button, Card, Form, Grid, Input, Modal, Select, Space, Typography } from "antd";
import { useEffect, useState } from "react";
import { fetchRegistrationOptions, passwordLogin, registerAccount, ssoAuthorizeUrl } from "../auth/api";
import { useLocation, useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import { showToast } from "../feedback/toast";
import { useI18n } from "../i18n/provider";
import loginBackgroundUrl from "../assets/login-background.png";

type LocationState = {
  from?: string;
  reason?: "expired" | "unauthorized" | "logout";
  message?: string;
};

type LoginFormValues = {
  account?: string;
  password?: string;
};

type RegisterFormValues = {
  username?: string;
  displayName?: string;
  email?: string;
  mobile?: string;
  password?: string;
  confirmPassword?: string;
};

function resolveSafeRedirect(from?: string) {
  if (!from || !from.startsWith("/") || from.startsWith("/login")) {
    return "/home";
  }
  return from;
}

// Unified-login (SSO) entries are gated by build-time env flags so environments
// without a school identity provider don't show a dead button.
const ssoOidcEnabled = import.meta.env.VITE_SSO_OIDC_ENABLED === "true";
const ssoCasEnabled = import.meta.env.VITE_SSO_CAS_ENABLED === "true";

function ssoReturnTo() {
  if (typeof window === "undefined") {
    return undefined;
  }
  return `${window.location.origin}/auth/sso/callback`;
}

export function LoginPage() {
  const { locale, setLocale, t } = useI18n();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const navigate = useNavigate();
  const location = useLocation();
  const {
    sessionSource,
    isAuthenticated,
    authRequiredDetail,
    setTokens,
    clearSession,
    clearAuthRequired
  } = useSession();
  const [form] = Form.useForm<LoginFormValues>();
  const [registerForm] = Form.useForm<RegisterFormValues>();
  const [registrationEnabled, setRegistrationEnabled] = useState(false);
  const [passwordMinLength, setPasswordMinLength] = useState(8);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [registerSubmitting, setRegisterSubmitting] = useState(false);
  const state = (location.state as LocationState | null) ?? null;
  const infoMessage = state?.message || authRequiredDetail?.message;
  const pageTitle = t("login.heroTitle");
  const pageSubtitle = t("login.heroSubtitle");
  const formTitle = t("login.formTitle");
  const formSubtitle = t("login.formSubtitle");
  const registerTexts = {
    trigger: t("register.trigger"),
    title: t("register.title"),
    subtitle: t("register.subtitle"),
    username: t("register.username"),
    usernameRequired: t("register.usernameRequired"),
    displayName: t("register.displayName"),
    email: t("register.email"),
    mobile: t("register.mobile"),
    password: t("register.password"),
    passwordRequired: t("register.passwordRequired"),
    passwordRule: t("register.passwordRule", { count: passwordMinLength }),
    confirmPassword: t("register.confirmPassword"),
    confirmRequired: t("register.confirmRequired"),
    confirmMismatch: t("register.confirmMismatch"),
    submit: t("register.submit"),
    success: t("register.success")
  };

  useEffect(() => {
    return () => {
      clearAuthRequired();
    };
  }, [clearAuthRequired]);

  useEffect(() => {
    let cancelled = false;
    void fetchRegistrationOptions()
      .then((options) => {
        if (cancelled) {
          return;
        }
        setRegistrationEnabled(options.selfServiceEnabled);
        setPasswordMinLength(options.passwordMinLength);
      })
      .catch(() => {
        if (!cancelled) {
          setRegistrationEnabled(false);
          setPasswordMinLength(8);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      navigate(resolveSafeRedirect(state?.from), { replace: true });
    }
  }, [isAuthenticated, navigate, state?.from]);

  const handleLogin = async () => {
    const values = await form.validateFields();
    const result = await passwordLogin({
      principal: values.account?.trim() ?? "",
      password: values.password ?? ""
    });
    setTokens(result.accessToken, result.refreshToken);
    showToast("success", t("login.success.auth"));
    navigate(resolveSafeRedirect(state?.from), { replace: true });
  };

  const handleRegister = async () => {
    const values = await registerForm.validateFields();
    setRegisterSubmitting(true);
    try {
      await registerAccount({
        username: values.username?.trim() ?? "",
        displayName: values.displayName?.trim() || undefined,
        email: values.email?.trim() || undefined,
        mobile: values.mobile?.trim() || undefined,
        password: values.password ?? ""
      });
      showToast("success", registerTexts.success);
      form.setFieldValue("account", values.username?.trim() ?? "");
      setRegisterOpen(false);
      registerForm.resetFields();
    } finally {
      setRegisterSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: isMobile ? 18 : 28,
        background:
          `linear-gradient(180deg, rgba(238,244,246,0.78) 0%, rgba(247,245,239,0.66) 42%, rgba(242,236,228,0.74) 100%), url(${loginBackgroundUrl}) center / cover no-repeat`
      }}
    >
      <div
        style={{
          width: "100%",
          maxWidth: 1080,
          display: "grid",
          gridTemplateColumns: isMobile ? "1fr" : "minmax(0, 1fr) 420px",
          gap: isMobile ? 18 : 28,
          alignItems: "center"
        }}
      >
        <div
          style={{
            padding: isMobile ? "6px 4px" : "0 20px 0 8px"
          }}
        >
          <Typography.Text
            style={{
              display: "inline-block",
              marginBottom: 14,
              padding: "6px 12px",
              borderRadius: 999,
              background: "rgba(20, 66, 88, 0.08)",
              color: "#21495d",
              fontWeight: 700,
              letterSpacing: 0.3
            }}
          >
            {t("app.title")}
          </Typography.Text>
          <Typography.Title
            style={{
              margin: 0,
              color: "#16384a",
              fontSize: isMobile ? 34 : 58,
              lineHeight: 1.05,
              fontWeight: 800,
              maxWidth: 520
            }}
          >
            {pageTitle}
          </Typography.Title>
          <Typography.Paragraph
            style={{
              marginTop: 18,
              marginBottom: 0,
              color: "#5c7280",
              fontSize: isMobile ? 16 : 19,
              lineHeight: 1.8,
              maxWidth: 520
            }}
          >
            {pageSubtitle}
          </Typography.Paragraph>
          <div
            style={{
              marginTop: 28,
              width: isMobile ? "100%" : 460,
              height: isMobile ? 140 : 220,
              borderRadius: 28,
              background:
                "linear-gradient(135deg, #11364c 0%, #1b5f7e 52%, #d69d59 100%)",
              boxShadow: "0 24px 60px rgba(25, 62, 83, 0.18)",
              position: "relative",
              overflow: "hidden"
            }}
          >
            <div
              style={{
                position: "absolute",
                inset: 0,
                background:
                  "radial-gradient(circle at 18% 22%, rgba(255,255,255,0.16), transparent 20%), radial-gradient(circle at 78% 28%, rgba(255,255,255,0.12), transparent 18%), linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0))"
              }}
            />
            <div
              style={{
                position: "absolute",
                left: isMobile ? 18 : 24,
                right: isMobile ? 18 : 24,
                bottom: isMobile ? 18 : 24,
                display: "grid",
                gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
                gap: 10
              }}
            >
              {[t("login.journey.assess"), t("login.journey.review"), t("login.journey.followUp")].map((item) => (
                <div
                  key={item}
                  style={{
                    borderRadius: 18,
                    padding: isMobile ? "12px 10px" : "14px 12px",
                    background: "rgba(255,255,255,0.1)",
                    border: "1px solid rgba(255,255,255,0.16)",
                    color: "#f7fbfd",
                    textAlign: "center",
                    fontWeight: 700,
                    fontSize: isMobile ? 12 : 13
                  }}
                >
                  {item}
                </div>
              ))}
            </div>
          </div>
        </div>

        <Card
          style={{
            borderRadius: 28,
            border: "1px solid rgba(204, 215, 220, 0.8)",
            boxShadow: "0 20px 55px rgba(42, 67, 82, 0.10)",
            background: "rgba(255,255,255,0.92)"
          }}
          styles={{ body: { padding: isMobile ? 22 : 30 } }}
        >
          <Space direction="vertical" size={18} style={{ width: "100%" }}>
            <Space align="start" style={{ justifyContent: "space-between", width: "100%" }}>
              <div>
                <Typography.Title level={2} style={{ margin: 0, color: "#18384a" }}>
                  {formTitle}
                </Typography.Title>
                <Typography.Text style={{ color: "#718390" }}>
                  {formSubtitle}
                </Typography.Text>
              </div>
              <Select
                value={locale}
                onChange={setLocale}
                options={[
                  { value: "zh-CN", label: t("locale.zh-CN") },
                  { value: "ja-JP", label: t("locale.ja-JP") },
                  { value: "en-US", label: t("locale.en-US") }
                ]}
                style={{ width: 124 }}
                aria-label={t("locale.label")}
              />
            </Space>

            {infoMessage ? (
              <Alert
                type={state?.reason === "logout" ? "info" : "warning"}
                showIcon
                message={infoMessage}
              />
            ) : null}

            <Form<LoginFormValues> form={form} layout="vertical">
              <Form.Item
                label={t("login.account")}
                name="account"
                rules={[{ required: true, message: t("login.accountRequired") }]}
              >
                <Input
                  size="large"
                  placeholder={t("login.accountPlaceholder")}
                  styles={{ input: { minHeight: 46 } }}
                />
              </Form.Item>
              <Form.Item
                label={t("login.password")}
                name="password"
                rules={[{ required: true, message: t("login.passwordRequired") }]}
              >
                <Input.Password
                  size="large"
                  placeholder={t("login.passwordPlaceholder")}
                  styles={{ input: { minHeight: 46 } }}
                />
              </Form.Item>
              <Button
                type="primary"
                size="large"
                block
                style={{
                  height: 48,
                  border: "none",
                  fontWeight: 700,
                  background: "#173f56",
                  boxShadow: "0 12px 30px rgba(23, 63, 86, 0.18)"
                }}
                onClick={() => void handleLogin()}
              >
                {t("login.signIn")}
              </Button>

              {ssoOidcEnabled || ssoCasEnabled ? (
                <Button
                  size="large"
                  block
                  style={{
                    height: 46,
                    marginTop: 10,
                    fontWeight: 600,
                    color: "#173f56",
                    borderColor: "rgba(23, 63, 86, 0.18)",
                    background: "#f8fbfc"
                  }}
                  onClick={() => {
                    const provider = ssoOidcEnabled ? "oidc" : "cas";
                    window.location.href = ssoAuthorizeUrl(provider, ssoReturnTo());
                  }}
                >
                  {t("login.unified")}
                </Button>
              ) : null}

              {registrationEnabled ? (
                <Button
                  size="large"
                  block
                  style={{
                    height: 46,
                    marginTop: 10,
                    fontWeight: 600,
                    color: "#173f56",
                    borderColor: "rgba(23, 63, 86, 0.18)",
                    background: "#f8fbfc"
                  }}
                  onClick={() => setRegisterOpen(true)}
                >
                  {registerTexts.trigger}
                </Button>
              ) : null}

              {isAuthenticated || sessionSource === "server" ? (
                <Button
                  size="large"
                  block
                  style={{ height: 46, marginTop: 10, fontWeight: 600 }}
                  onClick={() => void clearSession()}
                >
                  {t("login.exitCurrentSession")}
                </Button>
              ) : null}
            </Form>

            <div
              style={{
                paddingTop: 12,
                borderTop: "1px solid rgba(219, 226, 231, 0.88)"
              }}
            >
              <Typography.Text style={{ color: "#6f7f89", lineHeight: 1.8 }}>
                {t(registrationEnabled ? "login.registrationAvailable" : "login.registrationUnavailable")}
                <br />
                <Typography.Link onClick={() => navigate("/register/external")}>
                  {t("login.externalRegistration")}
                </Typography.Link>
              </Typography.Text>
            </div>
          </Space>
        </Card>
      </div>

      <Modal
        open={registerOpen}
        title={registerTexts.title}
        onCancel={() => {
          setRegisterOpen(false);
          registerForm.resetFields();
        }}
        onOk={() => void handleRegister()}
        confirmLoading={registerSubmitting}
        okText={registerTexts.submit}
        cancelText={t("common.cancel")}
        destroyOnHidden
      >
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Typography.Text type="secondary">{registerTexts.subtitle}</Typography.Text>
          <Form<RegisterFormValues> form={registerForm} layout="vertical">
            <Form.Item
              label={registerTexts.username}
              name="username"
              rules={[{ required: true, message: registerTexts.usernameRequired }]}
            >
              <Input />
            </Form.Item>
            <Form.Item label={registerTexts.displayName} name="displayName">
              <Input />
            </Form.Item>
            <Form.Item label={registerTexts.email} name="email">
              <Input />
            </Form.Item>
            <Form.Item label={registerTexts.mobile} name="mobile">
              <Input />
            </Form.Item>
            <Form.Item
              label={registerTexts.password}
              name="password"
              rules={[
                { required: true, message: registerTexts.passwordRequired },
                { min: passwordMinLength, message: registerTexts.passwordRule }
              ]}
            >
              <Input.Password />
            </Form.Item>
            <Form.Item
              label={registerTexts.confirmPassword}
              name="confirmPassword"
              dependencies={["password"]}
              rules={[
                { required: true, message: registerTexts.confirmRequired },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue("password") === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error(registerTexts.confirmMismatch));
                  }
                })
              ]}
            >
              <Input.Password />
            </Form.Item>
          </Form>
        </Space>
      </Modal>
    </div>
  );
}
