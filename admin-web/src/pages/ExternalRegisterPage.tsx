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
  const { t } = useI18n();
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
      setErrorMsg(err?.response?.data?.message || err?.message || t("externalRegister.registrationFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <Card style={{ maxWidth: 460, width: "100%" }}>
        {result === "success" ? (
          <Space direction="vertical" size={16} style={{ width: "100%", textAlign: "center" }}>
            <Alert type="success" showIcon message={t("externalRegister.successTitle")} description={t("externalRegister.successDescription")} />
            <Button type="primary" block onClick={() => navigate("/login", { replace: true })}>
              {t("externalRegister.back")}
            </Button>
          </Space>
        ) : (
          <>
            <Typography.Title level={3} style={{ marginTop: 0 }}>
              {t("externalRegister.title")}
            </Typography.Title>
            <Typography.Paragraph type="secondary">
              {t("externalRegister.subtitle")}
            </Typography.Paragraph>

            {result === "error" && <Alert type="error" message={errorMsg} style={{ marginBottom: 16 }} />}

            <Form<FormValues> form={form} layout="vertical" style={{ marginTop: 8 }}>
              <Form.Item label={t("register.username")} name="username" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item label={t("register.email")} name="email" rules={[{ required: true, type: "email" }]}>
                <Input />
              </Form.Item>
              <Form.Item label={t("register.displayName")} name="displayName">
                <Input />
              </Form.Item>
              <Form.Item label={t("register.password")} name="password" rules={[{ required: true, min: 8 }]}>
                <Input.Password />
              </Form.Item>
              <Form.Item label={t("register.confirmPassword")} name="confirmPassword"
                dependencies={["password"]}
                rules={[{ required: true }, ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue("password") === value) return Promise.resolve();
                    return Promise.reject(t("register.confirmMismatch"));
                  }
                })]}
              >
                <Input.Password />
              </Form.Item>
              <Button type="primary" block size="large" loading={submitting}
                onClick={() => void handleSubmit()}>
                {t("register.submit")}
              </Button>
              <Button block size="large" style={{ marginTop: 10 }}
                onClick={() => navigate("/login", { replace: true })}>
                {t("externalRegister.back")}
              </Button>
            </Form>
          </>
        )}
      </Card>
    </div>
  );
}
