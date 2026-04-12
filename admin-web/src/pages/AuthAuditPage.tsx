import { useQuery } from "@tanstack/react-query";
import { Button, Card, Col, Drawer, Input, Row, Select, Space, Table, Tag, Typography, message } from "antd";
import { useMemo, useState } from "react";
import {
  fetchLoginLogs,
  fetchSecurityEvents,
  type LoginLogRecord,
  type SecurityEventDetail,
  type SecurityEventRecord
} from "../features/auth-audit/api";
import { useI18n } from "../i18n/provider";

const PAGE_SIZE = 20;
const QUICK_SECURITY_EVENT_TYPES = [
  "PSY_REPORT_VIEWED",
  "PSY_REPORT_EXPORTED",
  "PSY_WARNING_CLAIMED",
  "PSY_WARNING_ASSIGNED",
  "PSY_INTERVENTION_CREATED",
  "PSY_INTERVENTION_CLOSED"
];

type SecurityCategory = "ALL" | "AUTH" | "BUSINESS";
type FilterChip = { key: string; label: string };

function formatActiveFilters(filters: Array<string | undefined>, emptyText: string) {
  return filters.filter(Boolean).join(" | ") || emptyText;
}

function renderSecurityEventTag(eventType: string) {
  return <Tag color={eventType.startsWith("PSY_") ? "purple" : "blue"}>{eventType}</Tag>;
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

  const importantKeys = ["reportType", "riskLevel", "exportFormat", "exportChannel", "warningId", "interventionId"];
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
  const [reportTypeFilter, setReportTypeFilter] = useState<string | undefined>();
  const [exportFormatFilter, setExportFormatFilter] = useState<string | undefined>();
  const [userIdFilter, setUserIdFilter] = useState("");
  const [userIdQueryFilter, setUserIdQueryFilter] = useState("");
  const [warningIdFilter, setWarningIdFilter] = useState("");
  const [interventionIdFilter, setInterventionIdFilter] = useState("");

  const [selectedSecurityEvent, setSelectedSecurityEvent] = useState<SecurityEventRecord | null>(null);
  const [loginPage, setLoginPage] = useState(1);
  const [securityPage, setSecurityPage] = useState(1);
  const [messageApi, contextHolder] = message.useMessage();

  const applySecurityDetailFilter = (key: string, value: string) => {
    const normalizedValue = value.trim();
    if (!normalizedValue) {
      return;
    }

    switch (key) {
      case "riskLevel":
        setRiskLevelFilter(normalizedValue);
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

  const securityItems = securityEventsQuery.data?.items ?? [];

  const riskLevelOptions = Array.from(
    new Set(securityItems.map((item) => getStringDetail(item.parsedDetail, "riskLevel")).filter(Boolean))
  ).map((value) => ({ label: value as string, value: value as string }));

  const reportTypeOptions = Array.from(
    new Set(securityItems.map((item) => getStringDetail(item.parsedDetail, "reportType")).filter(Boolean))
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
    if (reportTypeFilter && getStringDetail(item.parsedDetail, "reportType") !== reportTypeFilter) {
      return false;
    }
    if (exportFormatFilter && getStringDetail(item.parsedDetail, "exportFormat") !== exportFormatFilter) {
      return false;
    }
    if (userIdFilter && String(item.userId ?? "") !== userIdFilter.trim()) {
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
    reportTypeFilter ? `reportType: ${reportTypeFilter}` : undefined,
    exportFormatFilter ? `exportFormat: ${exportFormatFilter}` : undefined,
    userIdFilter ? `userId: ${userIdFilter}` : undefined,
    warningIdFilter ? `warningId: ${warningIdFilter}` : undefined,
    interventionIdFilter ? `interventionId: ${interventionIdFilter}` : undefined
  ], t("authAudit.noFilters"));

  const activeSecurityChips: FilterChip[] = [
    securityCategory !== "ALL" ? { key: "securityCategory", label: `category: ${securityCategory}` } : null,
    eventTypeFilter ? { key: "eventType", label: `eventType: ${eventTypeFilter}` } : null,
    riskLevelFilter ? { key: "riskLevel", label: `riskLevel: ${riskLevelFilter}` } : null,
    reportTypeFilter ? { key: "reportType", label: `reportType: ${reportTypeFilter}` } : null,
    exportFormatFilter ? { key: "exportFormat", label: `exportFormat: ${exportFormatFilter}` } : null,
    userIdFilter ? { key: "userId", label: `userId: ${userIdFilter}` } : null,
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
      { title: t("authAudit.col.reason"), dataIndex: "reason", key: "reason", render: (value: string | null) => value || "-" },
      { title: t("authAudit.col.createdAt"), dataIndex: "createdAt", key: "createdAt", width: 220 }
    ],
    [t]
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
      { title: t("authAudit.col.createdAt"), dataIndex: "createdAt", key: "createdAt", width: 220 }
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
                <Select allowClear placeholder={t("authAudit.reportType")} value={reportTypeFilter} onChange={(value) => setReportTypeFilter(value)} style={{ width: 160 }} options={reportTypeOptions} />
                <Select allowClear placeholder={t("authAudit.exportFormat")} value={exportFormatFilter} onChange={(value) => setExportFormatFilter(value)} style={{ width: 140 }} options={exportFormatOptions} />
                <Input allowClear placeholder={t("authAudit.userId")} value={userIdFilter} onChange={(event) => setUserIdFilter(event.target.value)} style={{ width: 110 }} />
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
                    setReportTypeFilter(undefined);
                    setExportFormatFilter(undefined);
                    setUserIdFilter("");
                    setUserIdQueryFilter("");
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
