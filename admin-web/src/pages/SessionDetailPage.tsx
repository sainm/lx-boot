import { Button, Card, Col, Descriptions, List, Popconfirm, Row, Segmented, Space, Tag, Typography, message } from "antd";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getRoleLabel, isAppRole } from "../auth/roles";
import { useSession } from "../auth/session";
import {
  fetchMyLoginActivities,
  fetchMySecurityEvents,
  fetchMySessionPolicy,
  fetchMySessions,
  revokeMySession,
  revokeOtherMySessions,
  updateMySessionPolicy
} from "../auth/profile";
import { showToast } from "../feedback/toast";
import { useI18n } from "../i18n/provider";
import { formatDateTime as formatDisplayDateTime } from "../utils/date";

function formatDateTime(value: number | null, locale: string, emptyLabel: string) {
  const formatted = formatDisplayDateTime(value);
  return formatted === "-" ? emptyLabel : formatted;
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
  const queryClient = useQueryClient();
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

  const loginActivitiesQuery = useQuery({
    queryKey: ["auth", "login-activities"],
    queryFn: fetchMyLoginActivities,
    enabled: isAuthenticated && sessionSource === "server"
  });

  const securityEventsQuery = useQuery({
    queryKey: ["auth", "security-events"],
    queryFn: fetchMySecurityEvents,
    enabled: isAuthenticated && sessionSource === "server"
  });

  const sessionsQuery = useQuery({
    queryKey: ["auth", "sessions"],
    queryFn: fetchMySessions,
    enabled: isAuthenticated && sessionSource === "server"
  });

  const sessionPolicyQuery = useQuery({
    queryKey: ["auth", "session-policy"],
    queryFn: fetchMySessionPolicy,
    enabled: isAuthenticated && sessionSource === "server"
  });

  const revokeSessionMutation = useMutation({
    mutationFn: revokeMySession,
    onSuccess: async () => {
      message.success(t("sessionDetail.sessionRevoked"));
      await queryClient.invalidateQueries({ queryKey: ["auth", "sessions"] });
    }
  });

  const revokeOtherSessionsMutation = useMutation({
    mutationFn: revokeOtherMySessions,
    onSuccess: async (result) => {
      message.success(t("sessionDetail.otherSessionsRevoked", { count: result.revokedCount }));
      await queryClient.invalidateQueries({ queryKey: ["auth", "sessions"] });
    }
  });

  const updatePolicyMutation = useMutation({
    mutationFn: updateMySessionPolicy,
    onSuccess: async () => {
      message.success(t("sessionDetail.policyUpdated"));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["auth", "session-policy"] }),
        queryClient.invalidateQueries({ queryKey: ["auth", "sessions"] })
      ]);
    }
  });

  const healthColor =
    sessionHealth === "healthy"
      ? "green"
      : sessionHealth === "refreshing"
        ? "processing"
        : sessionHealth === "expiring"
          ? "gold"
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
        <Popconfirm
          title={t("sessionDetail.revokeOthersConfirm")}
          onConfirm={() => revokeOtherSessionsMutation.mutate()}
        >
          <Button loading={revokeOtherSessionsMutation.isPending} disabled={!isAuthenticated || sessionSource !== "server"}>
            {t("sessionDetail.revokeOthers")}
          </Button>
        </Popconfirm>
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
                {profile?.roles.map((role) => isAppRole(role) ? getRoleLabel(role, t) : role).join(", ") || t("common.none")}
              </Descriptions.Item>
              <Descriptions.Item label={t("sessionDetail.permissionCount")}>{profile?.permissions.length ?? 0}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={t("sessionDetail.accessToken")}>
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
          <Card title={t("sessionDetail.refreshToken")}>
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

        <Col xs={24} lg={12}>
          <Card title={t("sessionDetail.loginActivity")}>
            <List
              size="small"
              loading={loginActivitiesQuery.isLoading}
              dataSource={loginActivitiesQuery.data ?? []}
              locale={{ emptyText: t("sessionDetail.loginActivityEmpty") }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Space size={8} wrap>
                        <Tag color={item.result === "SUCCESS" ? "green" : "red"}>{item.result}</Tag>
                        <Typography.Text>{item.loginType}</Typography.Text>
                      </Space>
                    }
                    description={[
                      formatDateTime(Date.parse(item.createdAt), locale, t("common.none")),
                      item.ip || t("common.none"),
                      item.reason || t("common.none")
                    ].join(" | ")}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={t("sessionDetail.securityEvents")}>
            <List
              size="small"
              loading={securityEventsQuery.isLoading}
              dataSource={securityEventsQuery.data ?? []}
              locale={{ emptyText: t("sessionDetail.securityEventsEmpty") }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Space size={8} wrap>
                        <Tag color={item.eventType === "ACCESS_DENIED" ? "orange" : "blue"}>{item.eventType}</Tag>
                        <Typography.Text>{item.ip || t("common.none")}</Typography.Text>
                      </Space>
                    }
                    description={[
                      formatDateTime(Date.parse(item.createdAt), locale, t("common.none")),
                      Object.keys(item.detail ?? {}).length > 0 ? JSON.stringify(item.detail) : t("common.none")
                    ].join(" | ")}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>

        <Col xs={24}>
          <Card
            title={t("sessionDetail.activeSessions")}
            extra={
              <Segmented
                options={[
                  { label: t("sessionDetail.policyMulti"), value: "MULTI_DEVICE" },
                  { label: t("sessionDetail.policySingle"), value: "SINGLE_DEVICE" }
                ]}
                value={sessionPolicyQuery.data?.policy ?? "MULTI_DEVICE"}
                onChange={(value) => updatePolicyMutation.mutate(value as "MULTI_DEVICE" | "SINGLE_DEVICE")}
              />
            }
          >
            <Typography.Text type="secondary">{t("sessionDetail.activeSessionsDesc")}</Typography.Text>
            <List
              style={{ marginTop: 12 }}
              size="small"
              loading={sessionsQuery.isLoading || updatePolicyMutation.isPending}
              dataSource={sessionsQuery.data ?? []}
              locale={{ emptyText: t("sessionDetail.activeSessionsEmpty") }}
              renderItem={(item) => (
                <List.Item
                  actions={[
                    item.current ? (
                      <Tag color="green">{t("sessionDetail.currentDevice")}</Tag>
                    ) : (
                      <Popconfirm
                        key="revoke"
                        title={t("sessionDetail.revokeSessionConfirm")}
                        onConfirm={() => revokeSessionMutation.mutate(item.sessionId)}
                      >
                        <Button type="link" size="small" loading={revokeSessionMutation.isPending}>
                          {t("sessionDetail.revokeSession")}
                        </Button>
                      </Popconfirm>
                    )
                  ]}
                >
                  <List.Item.Meta
                    title={
                      <Space size={8} wrap>
                        <Typography.Text strong>{item.deviceName || item.clientId || item.sessionId}</Typography.Text>
                        <Tag color={item.status === "ACTIVE" ? "green" : "default"}>{item.status}</Tag>
                        {item.deviceType ? <Tag>{item.deviceType}</Tag> : null}
                      </Space>
                    }
                    description={[
                      `${t("sessionDetail.clientId")}: ${item.clientId ?? "-"}`,
                      `${t("sessionDetail.userAgentShort")}: ${item.userAgent ?? "-"}`,
                      `${t("sessionDetail.ipAddress")}: ${item.ip ?? "-"}`,
                      `${t("sessionDetail.lastSeenShort")}: ${item.lastSeenAt ? formatDateTime(Date.parse(item.lastSeenAt), locale, t("common.none")) : t("common.none")}`
                    ].join(" | ")}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
