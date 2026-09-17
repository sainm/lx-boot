import { Alert, Card, Spin, Typography } from "antd";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useI18n } from "../i18n/provider";

const WECHAT_OAUTH_STATE_KEY = "psy.wechat.oauth.state";

function createOAuthState() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
    const bytes = new Uint8Array(32);
    crypto.getRandomValues(bytes);
    return Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
  }
  throw new Error("secure random source unavailable");
}

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
      let state: string;
      try {
        state = createOAuthState();
      } catch {
        setError(t("wechat.loginFailed"));
        return;
      }
      window.sessionStorage.setItem(WECHAT_OAUTH_STATE_KEY, state);
      const redirectUri = encodeURIComponent(window.location.origin + "/wechat/oauth");
      const scope = "snsapi_userinfo";
      window.location.href =
        `https://open.weixin.qq.com/connect/oauth2/authorize?appid=${encodeURIComponent(appId)}&redirect_uri=${redirectUri}&response_type=code&scope=${scope}&state=${encodeURIComponent(state)}#wechat_redirect`;
      return;
    }

    const returnedState = searchParams.get("state");
    const expectedState = window.sessionStorage.getItem(WECHAT_OAUTH_STATE_KEY);
    window.sessionStorage.removeItem(WECHAT_OAUTH_STATE_KEY);
    if (!returnedState || !expectedState || returnedState !== expectedState) {
      setError(t("wechat.loginFailed"));
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
