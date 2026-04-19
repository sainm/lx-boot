import { Alert, Button, Card, Form, Grid, Input, Modal, Select, Space, Typography } from "antd";
import { useEffect, useState } from "react";
import { fetchRegistrationOptions, passwordLogin, registerAccount } from "../auth/api";
import { useLocation, useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import { showToast } from "../feedback/toast";
import { useI18n } from "../i18n/provider";

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
  const isEnglish = locale === "en-US";

  const pageTitle = isEnglish ? "Psychological Assessment" : "心理测评系统";
  const pageSubtitle = isEnglish
    ? "A focused workspace for assessments, reports, and follow-up."
    : "测评、报告与后续跟进的统一工作入口。";
  const formTitle = isEnglish ? "Sign In" : "账号登录";
  const formSubtitle = isEnglish
    ? "Use your account to continue."
    : "使用已有账号继续登录。";
  const registerTexts = {
    trigger: isEnglish ? "Create Account" : "注册账号",
    title: isEnglish ? "Self-service Registration" : "自助注册",
    subtitle: isEnglish
      ? "This entry is controlled by backend configuration for each environment."
      : "该入口由后端配置控制，可按部署环境开启或关闭。",
    username: isEnglish ? "Username" : "用户名",
    usernameRequired: isEnglish ? "Please enter a username" : "请输入用户名",
    displayName: isEnglish ? "Display Name" : "显示名称",
    email: isEnglish ? "Email" : "邮箱",
    mobile: isEnglish ? "Mobile" : "手机号",
    password: isEnglish ? "Password" : "密码",
    passwordRequired: isEnglish ? "Please enter a password" : "请输入密码",
    passwordRule: isEnglish
      ? `Password must be at least ${passwordMinLength} characters`
      : `密码长度至少为 ${passwordMinLength} 位`,
    confirmPassword: isEnglish ? "Confirm Password" : "确认密码",
    confirmRequired: isEnglish ? "Please confirm your password" : "请再次输入密码",
    confirmMismatch: isEnglish ? "The two passwords do not match" : "两次输入的密码不一致",
    submit: isEnglish ? "Register" : "立即注册",
    success: isEnglish ? "Account created. Please sign in with your new account." : "账号已创建，请使用新账号登录。"
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
          "linear-gradient(180deg, #eef4f6 0%, #f7f5ef 42%, #f2ece4 100%)"
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
              {[isEnglish ? "Assess" : "测评", isEnglish ? "Review" : "报告", isEnglish ? "Follow Up" : "跟进"].map((item) => (
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
                  { value: "en-US", label: t("locale.en-US") }
                ]}
                style={{ width: 116 }}
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
                {registrationEnabled
                  ? isEnglish
                    ? "Self-service registration is available in this environment."
                    : "当前环境已开放自助注册。"
                  : isEnglish
                    ? "This environment only allows existing accounts to sign in."
                    : "当前环境仅允许已有账号登录。"}
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
        cancelText={isEnglish ? "Cancel" : "取消"}
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
