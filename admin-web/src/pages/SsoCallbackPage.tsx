import { Alert, Card, Spin, Typography } from "antd";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { exchangeSsoTicket } from "../auth/api";
import { useSession } from "../auth/session";
import { showToast } from "../feedback/toast";
import { useI18n } from "../i18n/provider";

/**
 * Receives the one-time SSO ticket that the backend appends when redirecting
 * back from the identity provider, exchanges it for tokens, and enters the app.
 */
export function SsoCallbackPage() {
  const { locale } = useI18n();
  const isEnglish = locale === "en-US";
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { setTokens } = useSession();
  const [error, setError] = useState<string | null>(null);
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) {
      return;
    }
    handled.current = true;

    const ticket = searchParams.get("ticket");
    if (!ticket) {
      setError(isEnglish ? "Missing SSO ticket." : "缺少统一登录票据。");
      return;
    }

    void exchangeSsoTicket(ticket)
      .then((result) => {
        setTokens(result.accessToken, result.refreshToken);
        showToast("success", isEnglish ? "Signed in." : "登录成功。");
        navigate("/home", { replace: true });
      })
      .catch(() => {
        setError(
          isEnglish
            ? "Unified login failed. Please try again or contact your administrator."
            : "统一登录失败，请重试或联系管理员。"
        );
      });
  }, [isEnglish, navigate, searchParams, setTokens]);

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <Card style={{ maxWidth: 420, width: "100%", textAlign: "center" }}>
        {error ? (
          <Alert
            type="error"
            showIcon
            message={isEnglish ? "Login failed" : "登录失败"}
            description={error}
            action={
              <Typography.Link onClick={() => navigate("/login", { replace: true })}>
                {isEnglish ? "Back to sign in" : "返回登录"}
              </Typography.Link>
            }
          />
        ) : (
          <>
            <Spin size="large" />
            <Typography.Paragraph style={{ marginTop: 18 }}>
              {isEnglish ? "Completing unified login..." : "正在完成统一登录..."}
            </Typography.Paragraph>
          </>
        )}
      </Card>
    </div>
  );
}
