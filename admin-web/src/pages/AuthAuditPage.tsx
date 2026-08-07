import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Card, Col, Drawer, Input, Popconfirm, Row, Select, Space, Table, Tag, Typography, message } from "antd";
import { useMemo, useState } from "react";
import {
  deactivateUserDevice,
  fetchUserDevices,
  fetchLoginLogs,
  fetchSecurityEvents,
  fetchUserSessions,
  type LoginLogRecord,
  type SecurityEventDetail,
  type SecurityEventRecord,
  type UserDeviceRecord,
  type UserSessionRecord,
  revokeAllUserSessions,
  revokeUserSession
} from "../features/auth-audit/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

const PAGE_SIZE = 20;
const QUICK_SECURITY_EVENT_TYPES = [
  "PSY_REPORT_VIEWED",
  "PSY_REPORT_EXPORTED",
  "PSY_WARNING_CLAIMED",
  "PSY_WARNING_ASSIGNED",
  "PSY_INTERVENTION_CREATED",
  "PSY_INTERVENTION_CLOSED",
  "PSY_USER_DEVICE_DEACTIVATED",
  "PSY_USER_DEVICE_AUTO_DISPOSED"
];

type SecurityCategory = "ALL" | "AUTH" | "BUSINESS";
type FilterChip = { key: string; label: string };

function formatActiveFilters(filters: Array<string | undefined>, emptyText: string) {
  return filters.filter(Boolean).join(" | ") || emptyText;
}

function renderSecurityEventTag(eventType: string) {
  return <Tag color={eventType.startsWith("PSY_") ? "purple" : "blue"}>{eventType}</Tag>;
}

function renderDeviceTrustTag(level: string) {
  const color = level === "TRUSTED" ? "green" : level === "REVIEW" ? "gold" : "default";
  return <Tag color={color}>{level}</Tag>;
}

function renderRiskLevelTag(level: string) {
  const color = level === "CRITICAL" ? "red" : level === "HIGH" ? "volcano" : level === "MEDIUM" ? "gold" : "green";
  return <Tag color={color}>{level}</Tag>;
}

function renderAutoDispositionTag(value: string) {
  const color =
    value === "DEACTIVATE_DEVICE_AND_REVOKE_SESSIONS"
      ? "red"
      : value === "REVOKE_DEVICE_SESSIONS"
        ? "volcano"
        : value === "REVIEW_ONLY"
          ? "gold"
          : "default";
  return <Tag color={color}>{value}</Tag>;
}

function isBusinessSecurityEvent(eventType: string) {
  return eventType.startsWith("PSY_");
}

function getStringDetail(detail: SecurityEventDetail | null, key: string) {
  const value = detail?.[key];
  return value === undefined || value === null ? undefined : String(value);
}

function buildDetailSummary(parsedDetail: SecurityEventDetail | null, rawDetailJson: string | null) {
  if (!parsedDetail) {
    return { summary: rawDetailJson || "-", extra: [] as string[] };
  }

  const importantKeys = [
    "targetUserId",
    "deviceId",
    "autoDisposition",
    "autoDispositionReason",
    "triggerSource",
    "riskLevel",
    "deviceTrustLevel",
    "revokedSessionCount",
    "reportType",
    "exportFormat",
    "exportChannel",
    "warningId",
    "interventionId"
  ];
  const extra = importantKeys
    .map((key) => {
      const value = parsedDetail[key];
      return value === undefined || value === null ? null : `${key}: ${String(value)}`;
    })
    .filter((item): item is string => Boolean(item));

  const structuredSummary = Object.entries(parsedDetail)
    .slice(0, 2)
    .map(([key, value]) => `${key}: ${String(value)}`)
    .join(" | ");

  return {
    summary: extra[0] ?? (structuredSummary || rawDetailJson || "-"),
    extra: extra.slice(1)
  };
}

function extractFilterTag(item: string) {
  const separatorIndex = item.indexOf(": ");
  if (separatorIndex <= 0) {
    return null;
  }
  return {
    key: item.slice(0, separatorIndex),
    value: item.slice(separatorIndex + 2)
  };
}

async function copyText(text: string) {
  await navigator.clipboard.writeText(text);
}

export function AuthAuditPage() {
  const { t } = useI18n();
  const [principal, setPrincipal] = useState("");
  const [principalFilter, setPrincipalFilter] = useState("");
  const [result, setResult] = useState<string | undefined>();
  const [resultFilter, setResultFilter] = useState<string | undefined>();

  const [eventType, setEventType] = useState("");
  const [eventTypeFilter, setEventTypeFilter] = useState("");
  const [securityCategory, setSecurityCategory] = useState<SecurityCategory>("ALL");
  const [riskLevelFilter, setRiskLevelFilter] = useState<string | undefined>();
  const [deviceTrustLevelFilter, setDeviceTrustLevelFilter] = useState<string | undefined>();
  const [autoDispositionFilter, setAutoDispositionFilter] = useState<string | undefined>();
  const [reportTypeFilter, setReportTypeFilter] = useState<string | undefined>();
  const [exportFormatFilter, setExportFormatFilter] = useState<string | undefined>();
  const [userIdFilter, setUserIdFilter] = useState("");
  const [userIdQueryFilter, setUserIdQueryFilter] = useState("");
  const [targetUserIdFilter, setTargetUserIdFilter] = useState("");
  const [deviceIdFilter, setDeviceIdFilter] = useState("");
  const [sessionUserId, setSessionUserId] = useState("");
  const [sessionUserIdFilter, setSessionUserIdFilter] = useState("");
  const [warningIdFilter, setWarningIdFilter] = useState("");
  const [interventionIdFilter, setInterventionIdFilter] = useState("");

  const [selectedSecurityEvent, setSelectedSecurityEvent] = useState<SecurityEventRecord | null>(null);
  const [loginPage, setLoginPage] = useState(1);
  const [securityPage, setSecurityPage] = useState(1);
  const [messageApi, contextHolder] = message.useMessage();
  const queryClient = useQueryClient();

  const applySecurityDetailFilter = (key: string, value: string) => {
    const normalizedValue = value.trim();
    if (!normalizedValue) {
      return;
    }

    switch (key) {
      case "riskLevel":
        setRiskLevelFilter(normalizedValue);
        break;
      case "deviceTrustLevel":
        setDeviceTrustLevelFilter(normalizedValue);
        break;
      case "autoDisposition":
        setAutoDispositionFilter(normalizedValue);
        break;
      case "reportType":
        setReportTypeFilter(normalizedValue);
        break;
      case "exportFormat":
        setExportFormatFilter(normalizedValue);
        break;
      case "userId":
        setUserIdFilter(normalizedValue);
        setUserIdQueryFilter(normalizedValue);
        break;
      case "targetUserId":
        setTargetUserIdFilter(normalizedValue);
        break;
      case "deviceId":
        setDeviceIdFilter(normalizedValue);
        break;
      case "warningId":
        setWarningIdFilter(normalizedValue);
        break;
      case "interventionId":
        setInterventionIdFilter(normalizedValue);
        break;
      default:
        return;
    }

    setSecurityCategory("BUSINESS");
    setSecurityPage(1);
    setSelectedSecurityEvent(null);
    void messageApi.success(t("authAudit.appliedFilter", { key, value: normalizedValue }));
  };

  const clearSecurityFilter = (key: string) => {
    switch (key) {
      case "securityCategory":
        setSecurityCategory("ALL");
        break;
      case "eventType":
        setEventType("");
        setEventTypeFilter("");
        break;
      case "riskLevel":
        setRiskLevelFilter(undefined);
        break;
      case "deviceTrustLevel":
        setDeviceTrustLevelFilter(undefined);
        break;
      case "autoDisposition":
        setAutoDispositionFilter(undefined);
        break;
      case "reportType":
        setReportTypeFilter(undefined);
        break;
      case "exportFormat":
        setExportFormatFilter(undefined);
        break;
      case "userId":
        setUserIdFilter("");
        setUserIdQueryFilter("");
        break;
      case "targetUserId":
        setTargetUserIdFilter("");
        break;
      case "deviceId":
        setDeviceIdFilter("");
        break;
      case "warningId":
        setWarningIdFilter("");
        break;
      case "interventionId":
        setInterventionIdFilter("");
        break;
      default:
        return;
    }

    setSecurityPage(1);
  };

  const loginLogsQuery = useQuery({
    queryKey: ["auth-audit", "login-logs", principalFilter, resultFilter, loginPage],
    queryFn: () =>
      fetchLoginLogs({
        page: loginPage,
        size: PAGE_SIZE,
        principal: principalFilter || undefined,
        result: resultFilter
      })
  });

  const securityEventsQuery = useQuery({
    queryKey: ["auth-audit", "security-events", eventTypeFilter, userIdQueryFilter, securityPage],
    queryFn: () =>
      fetchSecurityEvents({
        page: securityPage,
        size: PAGE_SIZE,
        eventType: eventTypeFilter || undefined,
        userId: userIdQueryFilter || undefined
      })
  });

  const userSessionsQuery = useQuery({
    queryKey: ["auth-audit", "user-sessions", sessionUserIdFilter],
    queryFn: () => fetchUserSessions(sessionUserIdFilter),
    enabled: Boolean(sessionUserIdFilter)
  });

  const userDevicesQuery = useQuery({
    queryKey: ["auth-audit", "user-devices", sessionUserIdFilter],
    queryFn: () => fetchUserDevices(sessionUserIdFilter),
    enabled: Boolean(sessionUserIdFilter)
  });

  const revokeSessionMutation = useMutation({
    mutationFn: ({ userId, sessionId }: { userId: number; sessionId: string }) => revokeUserSession(userId, sessionId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["auth-audit", "user-sessions", sessionUserIdFilter] });
      await queryClient.invalidateQueries({ queryKey: ["auth-audit", "user-devices", sessionUserIdFilter] });
      void messageApi.success("Session revoked");
    }
  });

  const revokeAllSessionsMutation = useMutation({
    mutationFn: (userId: string) => revokeAllUserSessions(userId),
    onSuccess: async (revokedCount) => {
      await queryClient.invalidateQueries({ queryKey: ["auth-audit", "user-sessions", sessionUserIdFilter] });
      await queryClient.invalidateQueries({ queryKey: ["auth-audit", "user-devices", sessionUserIdFilter] });
      void messageApi.success(`Revoked ${revokedCount} sessions`);
    }
  });

  const deactivateUserDeviceMutation = useMutation({
    mutationFn: ({ userId, deviceId }: { userId: string; deviceId: string }) => deactivateUserDevice(userId, deviceId),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ["auth-audit", "user-devices", sessionUserIdFilter] });
      await queryClient.invalidateQueries({ queryKey: ["auth-audit", "user-sessions", sessionUserIdFilter] });
      void messageApi.success(`Device deactivated, revoked ${result.revokedSessionCount} sessions`);
    }
  });

  const securityItems = securityEventsQuery.data?.items ?? [];

  const riskLevelOptions = Array.from(
    new Set(securityItems.map((item) => getStringDetail(item.parsedDetail, "riskLevel")).filter(Boolean))
  ).map((value) => ({ label: value as string, value: value as string }));

  const reportTypeOptions = Array.from(
    new Set(securityItems.map((item) => getStringDetail(item.parsedDetail, "reportType")).filter(Boolean))
  ).map((value) => ({ label: value as string, value: value as string }));

  const deviceTrustLevelOptions = Array.from(
    new Set(securityItems.map((item) => getStringDetail(item.parsedDetail, "deviceTrustLevel")).filter(Boolean))
  ).map((value) => ({ label: value as string, value: value as string }));

  const autoDispositionOptions = Array.from(
    new Set(securityItems.map((item) => getStringDetail(item.parsedDetail, "autoDisposition")).filter(Boolean))
  ).map((value) => ({ label: value as string, value: value as string }));

  const exportFormatOptions = Array.from(
    new Set(securityItems.map((item) => getStringDetail(item.parsedDetail, "exportFormat")).filter(Boolean))
  ).map((value) => ({ label: value as string, value: value as string }));

  const visibleSecurityEvents = securityItems.filter((item) => {
    if (securityCategory === "BUSINESS" && !isBusinessSecurityEvent(item.eventType)) {
      return false;
    }
    if (securityCategory === "AUTH" && isBusinessSecurityEvent(item.eventType)) {
      return false;
    }
    if (riskLevelFilter && getStringDetail(item.parsedDetail, "riskLevel") !== riskLevelFilter) {
      return false;
    }
    if (deviceTrustLevelFilter && getStringDetail(item.parsedDetail, "deviceTrustLevel") !== deviceTrustLevelFilter) {
      return false;
    }
    if (autoDispositionFilter && getStringDetail(item.parsedDetail, "autoDisposition") !== autoDispositionFilter) {
      return false;
    }
    if (reportTypeFilter && getStringDetail(item.parsedDetail, "reportType") !== reportTypeFilter) {
      return false;
    }
    if (exportFormatFilter && getStringDetail(item.parsedDetail, "exportFormat") !== exportFormatFilter) {
      return false;
    }
    if (userIdFilter && String(item.userId ?? "") !== userIdFilter.trim()) {
      return false;
    }
    if (targetUserIdFilter && getStringDetail(item.parsedDetail, "targetUserId") !== targetUserIdFilter.trim()) {
      return false;
    }
    if (deviceIdFilter && getStringDetail(item.parsedDetail, "deviceId") !== deviceIdFilter.trim()) {
      return false;
    }
    if (warningIdFilter && getStringDetail(item.parsedDetail, "warningId") !== warningIdFilter.trim()) {
      return false;
    }
    if (interventionIdFilter && getStringDetail(item.parsedDetail, "interventionId") !== interventionIdFilter.trim()) {
      return false;
    }
    return true;
  });

  const currentPageBusinessCount = securityItems.filter((item) => isBusinessSecurityEvent(item.eventType)).length;
  const currentPageAuthCount = securityItems.length - currentPageBusinessCount;

  const loginFilterSummary = formatActiveFilters([
    principalFilter ? `principal: ${principalFilter}` : undefined,
    resultFilter ? `result: ${resultFilter}` : undefined
  ], t("authAudit.noFilters"));

  const securityFilterSummary = formatActiveFilters([
    securityCategory !== "ALL" ? `category: ${securityCategory}` : undefined,
    eventTypeFilter ? `eventType: ${eventTypeFilter}` : undefined,
    riskLevelFilter ? `riskLevel: ${riskLevelFilter}` : undefined,
    deviceTrustLevelFilter ? `deviceTrustLevel: ${deviceTrustLevelFilter}` : undefined,
    autoDispositionFilter ? `autoDisposition: ${autoDispositionFilter}` : undefined,
    reportTypeFilter ? `reportType: ${reportTypeFilter}` : undefined,
    exportFormatFilter ? `exportFormat: ${exportFormatFilter}` : undefined,
    userIdFilter ? `userId: ${userIdFilter}` : undefined,
    targetUserIdFilter ? `targetUserId: ${targetUserIdFilter}` : undefined,
    deviceIdFilter ? `deviceId: ${deviceIdFilter}` : undefined,
    warningIdFilter ? `warningId: ${warningIdFilter}` : undefined,
    interventionIdFilter ? `interventionId: ${interventionIdFilter}` : undefined
  ], t("authAudit.noFilters"));

  const activeSecurityChips: FilterChip[] = [
    securityCategory !== "ALL" ? { key: "securityCategory", label: `category: ${securityCategory}` } : null,
    eventTypeFilter ? { key: "eventType", label: `eventType: ${eventTypeFilter}` } : null,
    riskLevelFilter ? { key: "riskLevel", label: `riskLevel: ${riskLevelFilter}` } : null,
    deviceTrustLevelFilter ? { key: "deviceTrustLevel", label: `deviceTrustLevel: ${deviceTrustLevelFilter}` } : null,
    autoDispositionFilter ? { key: "autoDisposition", label: `autoDisposition: ${autoDispositionFilter}` } : null,
    reportTypeFilter ? { key: "reportType", label: `reportType: ${reportTypeFilter}` } : null,
    exportFormatFilter ? { key: "exportFormat", label: `exportFormat: ${exportFormatFilter}` } : null,
    userIdFilter ? { key: "userId", label: `userId: ${userIdFilter}` } : null,
    targetUserIdFilter ? { key: "targetUserId", label: `targetUserId: ${targetUserIdFilter}` } : null,
    deviceIdFilter ? { key: "deviceId", label: `deviceId: ${deviceIdFilter}` } : null,
    warningIdFilter ? { key: "warningId", label: `warningId: ${warningIdFilter}` } : null,
    interventionIdFilter ? { key: "interventionId", label: `interventionId: ${interventionIdFilter}` } : null
  ].filter((chip): chip is FilterChip => Boolean(chip));

  const loginColumns = useMemo(
    () => [
      { title: t("authAudit.col.id"), dataIndex: "id", key: "id", width: 80 },
      { title: t("authAudit.col.principal"), dataIndex: "principal", key: "principal", render: (value: string | null) => value || "-" },
      { title: t("authAudit.col.loginType"), dataIndex: "loginType", key: "loginType", width: 140 },
      {
        title: t("authAudit.col.result"),
        dataIndex: "result",
        key: "result",
        width: 120,
        render: (value: string) => <Tag color={value === "SUCCESS" ? "green" : "red"}>{value}</Tag>
      },
      { title: t("authAudit.col.ip"), dataIndex: "ip", key: "ip", width: 140, render: (value: string | null | undefined) => value || "-" },
      { title: t("authAudit.col.userAgent"), dataIndex: "userAgent", key: "userAgent", width: 220, render: (value: string | null | undefined) => value || "-" },
      { title: t("authAudit.col.reason"), dataIndex: "reason", key: "reason", render: (value: string | null) => value || "-" },
      { title: t("authAudit.col.createdAt"), dataIndex: "createdAt", key: "createdAt", width: 180, render: (value: string) => formatDateTime(value) }
    ],
    [t]
  );

  const sessionColumns = useMemo(
    () => [
      { title: t("authAudit.sessionId"), dataIndex: "sessionId", key: "sessionId", width: 220 },
      { title: t("authAudit.username"), dataIndex: "username", key: "username", width: 140 },
      { title: t("authAudit.tenant"), dataIndex: "tenantId", key: "tenantId", width: 100, render: (value: number | null) => value ?? "-" },
      { title: t("authAudit.device"), key: "device", width: 220, render: (_: unknown, record: UserSessionRecord) => record.deviceName || record.deviceType || record.clientId || "-" },
      { title: t("authAudit.col.ip"), dataIndex: "ip", key: "ip", width: 140, render: (value: string | null) => value || "-" },
      { title: t("authAudit.status"), dataIndex: "status", key: "status", width: 120, render: (value: string) => <Tag color={value === "ACTIVE" ? "green" : "default"}>{value}</Tag> },
      { title: t("authAudit.lastSeen"), dataIndex: "lastSeenAt", key: "lastSeenAt", width: 180, render: (value: string | null) => formatDateTime(value) },
      {
        title: t("authAudit.action"),
        key: "action",
        width: 140,
        render: (_: unknown, record: UserSessionRecord) => (
          <Popconfirm
            title={t("authAudit.revokeSessionConfirm")}
            onConfirm={() => revokeSessionMutation.mutate({ userId: record.userId, sessionId: record.sessionId })}
          >
            <Button size="small" danger loading={revokeSessionMutation.isPending}>
              {t("authAudit.revoke")}
            </Button>
          </Popconfirm>
        )
      }
    ],
    [revokeSessionMutation, t]
  );

  const deviceColumns = useMemo(
    () => [
      { title: t("authAudit.deviceId"), dataIndex: "deviceId", key: "deviceId", width: 220 },
      { title: t("authAudit.type"), dataIndex: "deviceType", key: "deviceType", width: 100 },
      {
        title: t("authAudit.trust"),
        dataIndex: "deviceTrustLevel",
        key: "deviceTrustLevel",
        width: 120,
        render: (value: string) => renderDeviceTrustTag(value)
      },
      {
        title: t("authAudit.active"),
        dataIndex: "activeFlag",
        key: "activeFlag",
        width: 100,
        render: (value: boolean) => <Tag color={value ? "green" : "default"}>{value ? "ACTIVE" : "INACTIVE"}</Tag>
      },
      { title: t("authAudit.authSession"), dataIndex: "authSessionId", key: "authSessionId", width: 220, render: (value: string | null) => value || "-" },
      { title: t("authAudit.authStatus"), dataIndex: "authSessionStatus", key: "authSessionStatus", width: 120, render: (value: string | null) => value ? <Tag color={value === "ACTIVE" ? "blue" : "default"}>{value}</Tag> : "-" },
      { title: t("authAudit.lastSeen"), dataIndex: "authSessionLastSeenAt", key: "authSessionLastSeenAt", width: 180, render: (value: string | null) => formatDateTime(value) },
      { title: t("authAudit.pushToken"), dataIndex: "pushTokenMasked", key: "pushTokenMasked", width: 160, render: (value: string | null) => value || "-" },
      { title: t("authAudit.appVersion"), dataIndex: "appVersion", key: "appVersion", width: 120, render: (value: string | null) => value || "-" },
      {
        title: t("authAudit.riskSignals"),
        dataIndex: "riskSignals",
        key: "riskSignals",
        render: (value: string[]) =>
          value.length === 0 ? (
            <Tag color="green">{t("authAudit.none")}</Tag>
          ) : (
            <Space wrap size={[4, 4]}>
              {value.map((signal) => (
                <Tag key={signal} color="orange">
                  {signal}
                </Tag>
              ))}
            </Space>
          )
      },
      {
        title: t("authAudit.riskLevel"),
        dataIndex: "riskLevel",
        key: "riskLevel",
        width: 120,
        render: (value: string) => renderRiskLevelTag(value)
      },
      {
        title: t("authAudit.disposition"),
        dataIndex: "autoDisposition",
        key: "autoDisposition",
        width: 240,
        render: (_: string, record: UserDeviceRecord) =>
          record.autoDisposition === "NONE" ? "-" : (
            <Space direction="vertical" size={4}>
              {renderAutoDispositionTag(record.autoDisposition)}
              {record.autoDispositionReason ? <Typography.Text type="secondary">{record.autoDispositionReason}</Typography.Text> : null}
            </Space>
          )
      },
      {
        title: t("authAudit.action"),
        key: "action",
        width: 120,
        render: (_: unknown, record: UserDeviceRecord) => (
          <Popconfirm
            title={t("authAudit.deactivateDeviceConfirm")}
            onConfirm={() => deactivateUserDeviceMutation.mutate({ userId: String(sessionUserIdFilter), deviceId: record.deviceId })}
            disabled={!record.activeFlag || !sessionUserIdFilter}
          >
            <Button
              size="small"
              danger
              disabled={!record.activeFlag || !sessionUserIdFilter}
              loading={deactivateUserDeviceMutation.isPending}
            >
              {t("authAudit.deactivate")}
            </Button>
          </Popconfirm>
        )
      }
    ],
    [deactivateUserDeviceMutation, sessionUserIdFilter, t]
  );

  const securityColumns = useMemo(
    () => [
      { title: t("authAudit.col.id"), dataIndex: "id", key: "id", width: 80 },
      {
        title: t("authAudit.col.eventType"),
        dataIndex: "eventType",
        key: "eventType",
        width: 220,
        render: (value: string) => renderSecurityEventTag(value)
      },
      {
        title: t("authAudit.col.userId"),
        dataIndex: "userId",
        key: "userId",
        render: (value: number | null) =>
          value === null ? (
            "-"
          ) : (
            <Tag color="gold" style={{ cursor: "pointer" }} onClick={() => applySecurityDetailFilter("userId", String(value))}>
              {value}
            </Tag>
          )
      },
      { title: t("authAudit.col.tenantId"), dataIndex: "tenantId", key: "tenantId", render: (value: number | null) => value ?? "-" },
      {
        title: t("authAudit.col.summary"),
        key: "detailSummary",
        render: (_: unknown, record: SecurityEventRecord) => {
          const detail = buildDetailSummary(record.parsedDetail, record.detailJson);
          return (
            <Space direction="vertical" size={4} style={{ width: "100%" }}>
              <Typography.Text>{detail.summary}</Typography.Text>
              {detail.extra.length > 0 ? (
                <Space wrap size={[4, 4]}>
                  {detail.extra.map((item) => (
                    <Tag
                      key={item}
                      color="geekblue"
                      style={{ cursor: "pointer" }}
                      onClick={() => {
                        const pair = extractFilterTag(item);
                        if (pair) {
                          applySecurityDetailFilter(pair.key, pair.value);
                        }
                      }}
                    >
                      {item}
                    </Tag>
                  ))}
                </Space>
              ) : null}
            </Space>
          );
        }
      },
      {
        title: t("authAudit.col.detail"),
        key: "rawDetail",
        width: 120,
        render: (_: unknown, record: SecurityEventRecord) => (
          <Button size="small" onClick={() => setSelectedSecurityEvent(record)}>
            {t("authAudit.view")}
          </Button>
        )
      },
      { title: t("authAudit.col.createdAt"), dataIndex: "createdAt", key: "createdAt", width: 180, render: (value: string) => formatDateTime(value) }
    ],
    [t]
  );

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}

      <div>
        <Typography.Title level={3} style={{ marginBottom: 8 }}>
          {t("authAudit.title")}
        </Typography.Title>
        <Typography.Text type="secondary">
          {t("authAudit.subtitle")}
        </Typography.Text>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card
            title={t("authAudit.loginLogs")}
            extra={
              <Space wrap>
                <Input allowClear placeholder={t("authAudit.principal")} value={principal} onChange={(event) => setPrincipal(event.target.value)} style={{ width: 180 }} />
                <Select
                  allowClear
                  placeholder={t("authAudit.result")}
                  value={result}
                  onChange={(value) => setResult(value)}
                  style={{ width: 140 }}
                  options={[
                    { label: "SUCCESS", value: "SUCCESS" },
                    { label: "FAIL", value: "FAIL" }
                  ]}
                />
                <Button
                  type="primary"
                  onClick={() => {
                    setLoginPage(1);
                    setPrincipalFilter(principal.trim());
                    setResultFilter(result);
                  }}
                >
                  {t("authAudit.search")}
                </Button>
                <Button
                  onClick={() => {
                    setPrincipal("");
                    setResult(undefined);
                    setPrincipalFilter("");
                    setResultFilter(undefined);
                    setLoginPage(1);
                  }}
                >
                  {t("authAudit.reset")}
                </Button>
              </Space>
            }
          >
            <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
              {loginFilterSummary}
            </Typography.Text>
            <Table<LoginLogRecord>
              rowKey="id"
              loading={loginLogsQuery.isLoading}
              columns={loginColumns}
              dataSource={loginLogsQuery.data?.items ?? []}
              pagination={false}
              scroll={{ x: 900 }}
            />
            <Space style={{ marginTop: 12 }}>
              <Button disabled={loginPage <= 1} onClick={() => setLoginPage((page) => Math.max(1, page - 1))}>
                {t("authAudit.previous")}
              </Button>
              <Button disabled={!loginLogsQuery.data?.hasNext} onClick={() => setLoginPage((page) => page + 1)}>
                {t("authAudit.next")}
              </Button>
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={12}>
          <Card
            title={t("authAudit.securityEvents")}
            extra={
              <Space wrap>
                <Input allowClear placeholder={t("authAudit.eventTypePlaceholder")} value={eventType} onChange={(event) => setEventType(event.target.value)} style={{ width: 220 }} />
                <Select allowClear placeholder={t("authAudit.riskLevel")} value={riskLevelFilter} onChange={(value) => setRiskLevelFilter(value)} style={{ width: 140 }} options={riskLevelOptions} />
                <Select allowClear placeholder={t("authAudit.deviceTrust")} value={deviceTrustLevelFilter} onChange={(value) => setDeviceTrustLevelFilter(value)} style={{ width: 140 }} options={deviceTrustLevelOptions} />
                <Select allowClear placeholder={t("authAudit.disposition")} value={autoDispositionFilter} onChange={(value) => setAutoDispositionFilter(value)} style={{ width: 220 }} options={autoDispositionOptions} />
                <Select allowClear placeholder={t("authAudit.reportType")} value={reportTypeFilter} onChange={(value) => setReportTypeFilter(value)} style={{ width: 160 }} options={reportTypeOptions} />
                <Select allowClear placeholder={t("authAudit.exportFormat")} value={exportFormatFilter} onChange={(value) => setExportFormatFilter(value)} style={{ width: 140 }} options={exportFormatOptions} />
                <Input allowClear placeholder={t("authAudit.userId")} value={userIdFilter} onChange={(event) => setUserIdFilter(event.target.value)} style={{ width: 110 }} />
                <Input allowClear placeholder={t("authAudit.targetUserId")} value={targetUserIdFilter} onChange={(event) => setTargetUserIdFilter(event.target.value)} style={{ width: 130 }} />
                <Input allowClear placeholder={t("authAudit.deviceId")} value={deviceIdFilter} onChange={(event) => setDeviceIdFilter(event.target.value)} style={{ width: 180 }} />
                <Input allowClear placeholder={t("authAudit.warningId")} value={warningIdFilter} onChange={(event) => setWarningIdFilter(event.target.value)} style={{ width: 120 }} />
                <Input allowClear placeholder={t("authAudit.interventionId")} value={interventionIdFilter} onChange={(event) => setInterventionIdFilter(event.target.value)} style={{ width: 120 }} />
                <Button
                  type="primary"
                  onClick={() => {
                    setSecurityPage(1);
                    setEventTypeFilter(eventType.trim());
                    setUserIdQueryFilter(userIdFilter.trim());
                  }}
                >
                  {t("authAudit.search")}
                </Button>
                <Button
                  onClick={() => {
                    setEventType("");
                    setEventTypeFilter("");
                    setRiskLevelFilter(undefined);
                    setDeviceTrustLevelFilter(undefined);
                    setAutoDispositionFilter(undefined);
                    setReportTypeFilter(undefined);
                    setExportFormatFilter(undefined);
                    setUserIdFilter("");
                    setUserIdQueryFilter("");
                    setTargetUserIdFilter("");
                    setDeviceIdFilter("");
                    setWarningIdFilter("");
                    setInterventionIdFilter("");
                    setSecurityCategory("ALL");
                    setSecurityPage(1);
                  }}
                >
                  {t("authAudit.reset")}
                </Button>
              </Space>
            }
          >
            <Space wrap size={[8, 8]} style={{ marginBottom: 12 }}>
              <Tag color={securityCategory === "ALL" ? "blue" : "default"} style={{ cursor: "pointer" }} onClick={() => setSecurityCategory("ALL")}>
                {t("authAudit.all")}
              </Tag>
              <Tag color={securityCategory === "AUTH" ? "blue" : "default"} style={{ cursor: "pointer" }} onClick={() => setSecurityCategory("AUTH")}>
                {t("authAudit.auth")}
              </Tag>
              <Tag color={securityCategory === "BUSINESS" ? "purple" : "default"} style={{ cursor: "pointer" }} onClick={() => setSecurityCategory("BUSINESS")}>
                {t("authAudit.business")}
              </Tag>
            </Space>

            <Space wrap size={[8, 8]} style={{ marginBottom: 12 }}>
              {QUICK_SECURITY_EVENT_TYPES.map((quickType) => (
                <Tag
                  key={quickType}
                  color={eventTypeFilter === quickType ? "purple" : "default"}
                  style={{ cursor: "pointer" }}
                  onClick={() => {
                    setEventType(quickType);
                    setEventTypeFilter(quickType);
                    setSecurityPage(1);
                  }}
                >
                  {quickType}
                </Tag>
              ))}
            </Space>

            {activeSecurityChips.length > 0 ? (
              <Space wrap size={[8, 8]} style={{ marginBottom: 12 }}>
                {activeSecurityChips.map((chip) => (
                  <Tag
                    key={chip.key}
                    closable
                    color="blue"
                    onClose={(event) => {
                      event.preventDefault();
                      clearSecurityFilter(chip.key);
                    }}
                  >
                    {chip.label}
                  </Tag>
                ))}
              </Space>
            ) : null}

            <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
              {securityFilterSummary}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
              {t("authAudit.detailTagsHint")}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
              {t("authAudit.currentPageSummary", {
                total: securityItems.length,
                authCount: currentPageAuthCount,
                businessCount: currentPageBusinessCount,
                visibleCount: visibleSecurityEvents.length
              })}
            </Typography.Text>

            <Table<SecurityEventRecord>
              rowKey="id"
              loading={securityEventsQuery.isLoading}
              columns={securityColumns}
              dataSource={visibleSecurityEvents}
              pagination={false}
              scroll={{ x: 1100 }}
            />

            <Space style={{ marginTop: 12 }}>
              <Button disabled={securityPage <= 1} onClick={() => setSecurityPage((page) => Math.max(1, page - 1))}>
                {t("authAudit.previous")}
              </Button>
              <Button disabled={!securityEventsQuery.data?.hasNext} onClick={() => setSecurityPage((page) => page + 1)}>
                {t("authAudit.next")}
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card
        title={t("authAudit.sessionGovernance")}
        extra={
          <Space wrap>
            <Input
              allowClear
              placeholder={t("authAudit.targetUserId")}
              value={sessionUserId}
              onChange={(event) => setSessionUserId(event.target.value)}
              style={{ width: 180 }}
            />
            <Button
              type="primary"
              onClick={() => {
                setSessionUserIdFilter(sessionUserId.trim());
              }}
            >
              {t("authAudit.loadSessions")}
            </Button>
            <Popconfirm
              title={t("authAudit.revokeAllConfirm")}
              onConfirm={() => revokeAllSessionsMutation.mutate(sessionUserIdFilter)}
              disabled={!sessionUserIdFilter}
            >
              <Button danger disabled={!sessionUserIdFilter} loading={revokeAllSessionsMutation.isPending}>
                {t("authAudit.revokeAll")}
              </Button>
            </Popconfirm>
          </Space>
        }
      >
        <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
          {t("authAudit.sessionGovernanceDesc")}
        </Typography.Text>
        <Table<UserSessionRecord>
          rowKey="sessionId"
          loading={userSessionsQuery.isLoading}
          columns={sessionColumns}
          dataSource={userSessionsQuery.data ?? []}
          pagination={false}
          scroll={{ x: 1200 }}
        />
        <div style={{ height: 16 }} />
        <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
          {t("authAudit.deviceInventoryDesc")}
        </Typography.Text>
        <Table<UserDeviceRecord>
          rowKey="id"
          loading={userDevicesQuery.isLoading}
          columns={deviceColumns}
          dataSource={userDevicesQuery.data ?? []}
          pagination={false}
          scroll={{ x: 1500 }}
        />
      </Card>

      <Drawer
        title={selectedSecurityEvent ? `${t("authAudit.eventDetail")} #${selectedSecurityEvent.id}` : t("authAudit.eventDetail")}
        open={Boolean(selectedSecurityEvent)}
        width={720}
        onClose={() => setSelectedSecurityEvent(null)}
      >
        <Space direction="vertical" size={12} style={{ width: "100%" }}>
          <Space wrap>
            {selectedSecurityEvent ? (
              <Tag
                color={selectedSecurityEvent.eventType.startsWith("PSY_") ? "purple" : "blue"}
                style={{ cursor: "pointer" }}
                onClick={() => {
                  setEventType(selectedSecurityEvent.eventType);
                  setEventTypeFilter(selectedSecurityEvent.eventType);
                  setSecurityPage(1);
                  setSelectedSecurityEvent(null);
                  void messageApi.success(t("authAudit.appliedFilter", { key: "eventType", value: selectedSecurityEvent.eventType }));
                }}
              >
                {selectedSecurityEvent.eventType}
              </Tag>
            ) : null}

            {selectedSecurityEvent?.userId !== null && selectedSecurityEvent?.userId !== undefined ? (
              <Tag color="gold" style={{ cursor: "pointer" }} onClick={() => applySecurityDetailFilter("userId", String(selectedSecurityEvent.userId))}>
                {`userId: ${selectedSecurityEvent.userId}`}
              </Tag>
            ) : null}

            {selectedSecurityEvent?.parsedDetail
              ? Object.entries(selectedSecurityEvent.parsedDetail).map(([key, value]) => (
                  <Tag key={key} color="geekblue" style={{ cursor: "pointer" }} onClick={() => applySecurityDetailFilter(key, String(value))}>
                    {`${key}: ${String(value)}`}
                  </Tag>
                ))
              : null}
          </Space>

          <Typography.Text type="secondary">{t("authAudit.rawDetail")}</Typography.Text>

          <Space>
            <Button
              onClick={() => {
                void copyText(selectedSecurityEvent?.detailJson || "-").then(() => {
                  void messageApi.success(t("authAudit.copiedRawDetail"));
                });
              }}
            >
              {t("authAudit.copyRawDetail")}
            </Button>
            <Button
              onClick={() => {
                const structuredDetail = selectedSecurityEvent?.parsedDetail
                  ? JSON.stringify(selectedSecurityEvent.parsedDetail, null, 2)
                  : selectedSecurityEvent?.detailJson || "-";
                void copyText(structuredDetail).then(() => {
                  void messageApi.success(t("authAudit.copiedStructuredDetail"));
                });
              }}
            >
              {t("authAudit.copyStructuredDetail")}
            </Button>
          </Space>

          <pre
            style={{
              margin: 0,
              padding: 12,
              borderRadius: 8,
              background: "#fafafa",
              overflowX: "auto",
              whiteSpace: "pre-wrap",
              wordBreak: "break-word"
            }}
          >
            {selectedSecurityEvent?.detailJson || "-"}
          </pre>
        </Space>
      </Drawer>
    </Space>
  );
}
