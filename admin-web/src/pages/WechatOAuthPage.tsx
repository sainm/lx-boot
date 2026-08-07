import { Alert, Card, Spin, Typography } from "antd";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useI18n } from "../i18n/provider";

/**
 * WeChat Official Account OAuth landing page.
 * Flow:
 * 1. WeChat menu click → browser opens this page.
 * 2. No code in URL → redirect to WeChat OAuth authorize page.
 * 3. WeChat calls back with code → POST /auth/social/wechat → tokens → enter app.
 */
export function WechatOAuthPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const code = searchParams.get("code")?.trim();
    if (!code) {
      // No code → redirect to WeChat OAuth
      const appId = import.meta.env.VITE_WECHAT_APP_ID;
      if (!appId) {
        setError(t("wechat.appIdMissing"));
        return;
      }
      const redirectUri = encodeURIComponent(window.location.origin + "/wechat/oauth");
      const scope = "snsapi_userinfo";
      window.location.href =
        `https://open.weixin.qq.com/connect/oauth2/authorize?appid=${appId}&redirect_uri=${redirectUri}&response_type=code&scope=${scope}&state=wechat#wechat_redirect`;
      return;
    }

    // Got code → exchange for tokens
    import("../auth/api").then(({ authHttp }) => {
      authHttp
        .post("/auth/social/wechat", { authCode: code, deviceType: "WEB", deviceName: "WeChat H5" })
        .then((res) => {
          const data = res.data.data;
          import("../auth/token").then(({ setAuthTokens }) => {
            setAuthTokens(data.accessToken, data.refreshToken, { expiresInSeconds: data.expiresIn });
            navigate("/home", { replace: true });
          });
        })
        .catch(() => {
          setError(t("wechat.loginFailed"));
        });
    });
  }, [navigate, searchParams, t]);

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <Card style={{ maxWidth: 420, width: "100%", textAlign: "center" }}>
        {error ? (
          <Alert type="error" message={error} />
        ) : (
          <>
            <Spin size="large" />
            <Typography.Paragraph style={{ marginTop: 18 }}>
              {t("wechat.completing")}
            </Typography.Paragraph>
          </>
        )}
      </Card>
    </div>
  );
}
