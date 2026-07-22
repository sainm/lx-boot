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
  const { locale } = useI18n();
  const isEnglish = locale === "en-US";
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
        setError(isEnglish ? "WeChat App ID not configured." : "微信 AppID 未配置。");
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
          setError(isEnglish ? "WeChat login failed. Please try again." : "微信登录失败，请重试。");
        });
    });
  }, [isEnglish, navigate, searchParams]);

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <Card style={{ maxWidth: 420, width: "100%", textAlign: "center" }}>
        {error ? (
          <Alert type="error" message={error} />
        ) : (
          <>
            <Spin size="large" />
            <Typography.Paragraph style={{ marginTop: 18 }}>
              {isEnglish ? "Completing WeChat login..." : "正在完成微信登录..."}
            </Typography.Paragraph>
          </>
        )}
      </Card>
    </div>
  );
}
