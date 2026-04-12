import { Button, Card, Col, Descriptions, Row, Space, Tag, Typography } from "antd";
import { getRoleLabel } from "../auth/roles";
import { useSession } from "../auth/session";
import { showToast } from "../feedback/toast";
import { useI18n } from "../i18n/provider";

function formatDateTime(value: number | null, locale: string, emptyLabel: string) {
  return value ? new Date(value).toLocaleString(locale) : emptyLabel;
}

function formatRemainingMs(value: number | null, emptyLabel: string) {
  if (value === null) {
    return emptyLabel;
  }
  const seconds = Math.max(0, Math.floor(value / 1000));
  const minutes = Math.floor(seconds / 60);
  const remainSeconds = seconds % 60;
  return `${minutes}m ${remainSeconds}s`;
}

export function SessionDetailPage() {
  const { locale, t } = useI18n();
  const {
    currentRole,
    sessionSource,
    sessionHealth,
    profile,
    isAuthenticated,
    authToken,
    refreshToken,
    accessTokenRemainingMs,
    accessTokenExpiresAt,
    accessTokenTokenUse,
    refreshTokenRemainingMs,
    refreshTokenExpiresAt,
    refreshTokenTokenUse,
    tokenLastSyncAt,
    refreshSession,
    buildDiagnosticsText
  } = useSession();

  const healthColor =
    sessionHealth === "healthy"
      ? "green"
      : sessionHealth === "refreshing"
        ? "processing"
        : sessionHealth === "expiring"
          ? "gold"
          : sessionHealth === "development"
            ? "blue"
            : "default";

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={3} style={{ marginBottom: 8 }}>
          {t("sessionDetail.title")}
        </Typography.Title>
        <Typography.Text type="secondary">{t("sessionDetail.subtitle")}</Typography.Text>
      </div>

      <Space>
        <Button type="primary" onClick={() => void refreshSession()} disabled={!refreshToken}>
          {t("sessionDetail.refresh")}
        </Button>
        <Button
          onClick={async () => {
            await navigator.clipboard.writeText(buildDiagnosticsText());
            showToast("success", t("sessionDetail.copySuccess"), "session-diagnostics");
          }}
        >
          {t("sessionDetail.copyDiagnostics")}
        </Button>
      </Space>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title={t("sessionDetail.currentSession")}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t("sessionDetail.isAuthenticated")}>
                {isAuthenticated ? t("common.yes") : t("common.no")}
              </Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.currentRole")}>
                {getRoleLabel(currentRole, t)}
              </Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.source")}>{t(`session.source.${sessionSource}`)}</Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.health")}>
                <Tag color={healthColor}>{t(`session.health.${sessionHealth}`)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.lastSync")}>
                {formatDateTime(tokenLastSyncAt, locale, t("common.none"))}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={t("sessionDetail.currentUser")}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t("sessionDetail.userId")}>{profile?.userId ?? t("common.none")}</Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.username")}>{profile?.username ?? t("common.none")}</Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.displayName")}>{profile?.displayName ?? t("common.none")}</Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.roles")}>
                {profile?.roles.map((role) => getRoleLabel(role, t)).join(", ") || t("common.none")}
              </Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.permissionCount")}>{profile?.permissions.length ?? 0}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="Access Token">
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t("sessionDetail.exists")}>{authToken ? t("common.yes") : t("common.no")}</Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.tokenUse")}>{accessTokenTokenUse ?? t("common.none")}</Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.expiresAt")}>
                {formatDateTime(accessTokenExpiresAt, locale, t("common.none"))}
              </Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.remaining")}>
                {formatRemainingMs(accessTokenRemainingMs, t("common.none"))}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="Refresh Token">
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t("sessionDetail.exists")}>{refreshToken ? t("common.yes") : t("common.no")}</Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.tokenUse")}>{refreshTokenTokenUse ?? t("common.none")}</Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.expiresAt")}>
                {formatDateTime(refreshTokenExpiresAt, locale, t("common.none"))}
              </Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.remaining")}>
                {formatRemainingMs(refreshTokenRemainingMs, t("common.none"))}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
