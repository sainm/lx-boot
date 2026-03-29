import { Button, Card, Col, Descriptions, Row, Space, Tag, Typography } from "antd";
import { useSession } from "../auth/session";
import { showToast } from "../feedback/toast";

function formatDateTime(value: number | null) {
  return value ? new Date(value).toLocaleString() : "-";
}

function formatRemainingMs(value: number | null) {
  if (value === null) {
    return "-";
  }
  const seconds = Math.max(0, Math.floor(value / 1000));
  const minutes = Math.floor(seconds / 60);
  const remainSeconds = seconds % 60;
  return `${minutes}m ${remainSeconds}s`;
}

export function SessionDetailPage() {
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
          会话详情
        </Typography.Title>
        <Typography.Text type="secondary">
          查看当前登录状态、token 生命周期以及最近一次会话同步信息。
        </Typography.Text>
      </div>

      <Space>
        <Button type="primary" onClick={() => void refreshSession()} disabled={!refreshToken}>
          刷新会话
        </Button>
        <Button
          onClick={async () => {
            await navigator.clipboard.writeText(buildDiagnosticsText());
            showToast("success", "会话诊断信息已复制。", "session-diagnostics");
          }}
        >
          复制诊断信息
        </Button>
      </Space>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="当前会话">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="是否已认证">{isAuthenticated ? "是" : "否"}</Descriptions.Item>
              <Descriptions.Item label="当前角色">{currentRole}</Descriptions.Item>
              <Descriptions.Item label="会话来源">{sessionSource}</Descriptions.Item>
              <Descriptions.Item label="会话健康状态">
                <Tag color={healthColor}>{sessionHealth}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="最近同步时间">{formatDateTime(tokenLastSyncAt)}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="当前用户">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="用户 ID">{profile?.userId ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="用户名">{profile?.username ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="显示名称">{profile?.displayName ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="角色">{profile?.roles.join(", ") || "-"}</Descriptions.Item>
              <Descriptions.Item label="权限数量">{profile?.permissions.length ?? 0}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="Access Token">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="是否存在">{authToken ? "是" : "否"}</Descriptions.Item>
              <Descriptions.Item label="Token 用途">{accessTokenTokenUse ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="到期时间">{formatDateTime(accessTokenExpiresAt)}</Descriptions.Item>
              <Descriptions.Item label="剩余时间">{formatRemainingMs(accessTokenRemainingMs)}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="Refresh Token">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="是否存在">{refreshToken ? "是" : "否"}</Descriptions.Item>
              <Descriptions.Item label="Token 用途">{refreshTokenTokenUse ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="到期时间">{formatDateTime(refreshTokenExpiresAt)}</Descriptions.Item>
              <Descriptions.Item label="剩余时间">{formatRemainingMs(refreshTokenRemainingMs)}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
