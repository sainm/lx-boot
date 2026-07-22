import { Alert, Button, Card, Form, Input, Space, Typography } from "antd";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authHttp } from "../auth/api";
import { useI18n } from "../i18n/provider";

type FormValues = { username?: string; email?: string; password?: string; confirmPassword?: string; displayName?: string };

/**
 * Public external registration page for overseas students (or anyone without WeChat).
 * Creates a PENDING_EMAIL account; the user receives an activation link via email.
 */
export function ExternalRegisterPage() {
  const { locale } = useI18n();
  const isEnglish = locale === "en-US";
  const navigate = useNavigate();
  const [form] = Form.useForm<FormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<"success" | "error" | null>(null);
  const [errorMsg, setErrorMsg] = useState("");

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    setResult(null);
    try {
      await authHttp.post("/auth/external-register", {
        username: values.username?.trim() ?? "",
        password: values.password ?? "",
        email: values.email?.trim() ?? "",
        displayName: values.displayName?.trim() || undefined,
      });
      setResult("success");
    } catch (err: any) {
      setResult("error");
      setErrorMsg(err?.response?.data?.message || err?.message || "Registration failed");
    } finally {
      setSubmitting(false);
    }
  };

  const t = (keyEn: string, keyZh: string) => (isEnglish ? keyEn : keyZh);

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <Card style={{ maxWidth: 460, width: "100%" }}>
        {result === "success" ? (
          <Space direction="vertical" size={16} style={{ width: "100%", textAlign: "center" }}>
            <Alert type="success" showIcon message={
              isEnglish ? "Activation email sent" : "激活邮件已发送"
            } description={
              isEnglish
                ? "Check your email and click the activation link. After activation your account will be reviewed by an administrator."
                : "请检查邮箱并点击激活链接。激活后由管理员审核。"
            } />
            <Button type="primary" block onClick={() => navigate("/login", { replace: true })}>
              {isEnglish ? "Back to Sign In" : "返回登录"}
            </Button>
          </Space>
        ) : (
          <>
            <Typography.Title level={3} style={{ marginTop: 0 }}>
              {t("External Registration", "外部用户注册")}
            </Typography.Title>
            <Typography.Paragraph type="secondary">
              {isEnglish
                ? "For overseas students and users without WeChat. You'll need to verify your email before signing in."
                : "面向留学生及无微信用户。注册后需通过邮箱激活，并由管理员审核。"
              }
            </Typography.Paragraph>

            {result === "error" && <Alert type="error" message={errorMsg} style={{ marginBottom: 16 }} />}

            <Form<FormValues> form={form} layout="vertical" style={{ marginTop: 8 }}>
              <Form.Item label={t("Username", "用户名")} name="username" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item label={t("Email", "邮箱")} name="email" rules={[{ required: true, type: "email" }]}>
                <Input />
              </Form.Item>
              <Form.Item label={t("Display Name", "显示名称")} name="displayName">
                <Input />
              </Form.Item>
              <Form.Item label={t("Password", "密码")} name="password" rules={[{ required: true, min: 8 }]}>
                <Input.Password />
              </Form.Item>
              <Form.Item label={t("Confirm Password", "确认密码")} name="confirmPassword"
                dependencies={["password"]}
                rules={[{ required: true }, ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue("password") === value) return Promise.resolve();
                    return Promise.reject(t("Passwords do not match", "两次密码不一致"));
                  }
                })]}
              >
                <Input.Password />
              </Form.Item>
              <Button type="primary" block size="large" loading={submitting}
                onClick={() => void handleSubmit()}>
                {t("Register", "注册")}
              </Button>
              <Button block size="large" style={{ marginTop: 10 }}
                onClick={() => navigate("/login", { replace: true })}>
                {t("Back to Sign In", "返回登录")}
              </Button>
            </Form>
          </>
        )}
      </Card>
    </div>
  );
}
