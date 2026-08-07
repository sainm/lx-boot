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
  const { t } = useI18n();
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
      setError(t("sso.missingTicket"));
      return;
    }

    void exchangeSsoTicket(ticket)
      .then((result) => {
        setTokens(result.accessToken, result.refreshToken);
        showToast("success", t("sso.success"));
        navigate("/home", { replace: true });
      })
      .catch(() => {
        setError(t("sso.failed"));
      });
  }, [navigate, searchParams, setTokens, t]);

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <Card style={{ maxWidth: 420, width: "100%", textAlign: "center" }}>
        {error ? (
          <Alert
            type="error"
            showIcon
            message={t("sso.failedTitle")}
            description={error}
            action={
              <Typography.Link onClick={() => navigate("/login", { replace: true })}>
                {t("sso.back")}
              </Typography.Link>
            }
          />
        ) : (
          <>
            <Spin size="large" />
            <Typography.Paragraph style={{ marginTop: 18 }}>
              {t("sso.completing")}
            </Typography.Paragraph>
          </>
        )}
      </Card>
    </div>
  );
}
