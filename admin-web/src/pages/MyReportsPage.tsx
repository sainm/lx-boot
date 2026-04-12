import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Col, Empty, Grid, Row, Space, Table, Tag, Typography } from "antd";
import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { fetchMyReports, type MyReportSummary } from "../features/reports/api";
import { useI18n } from "../i18n/provider";

function riskColor(riskLevel: string) {
  switch (riskLevel) {
    case "HIGH":
      return "red";
    case "MEDIUM":
      return "gold";
    default:
      return "green";
  }
}

export function MyReportsPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [riskFilter, setRiskFilter] = useState<"ALL" | "HIGH" | "MEDIUM" | "LOW">("ALL");
  const [searchParams] = useSearchParams();
  const taskIdFilter = searchParams.get("taskId");
  const reportsQuery = useQuery({
    queryKey: ["reports", "my"],
    queryFn: fetchMyReports
  });
  const reports = reportsQuery.data ?? [];
  const filteredReports = useMemo(
    () => (taskIdFilter ? reports.filter((item) => String(item.taskId) === taskIdFilter) : reports),
    [reports, taskIdFilter]
  );
  const visibleReports = useMemo(
    () => (riskFilter === "ALL" ? filteredReports : filteredReports.filter((item) => item.riskLevel === riskFilter)),
    [filteredReports, riskFilter]
  );
  const riskSummary = useMemo(
    () => ({
      high: filteredReports.filter((item) => item.riskLevel === "HIGH").length,
      medium: filteredReports.filter((item) => item.riskLevel === "MEDIUM").length,
      low: filteredReports.filter((item) => item.riskLevel === "LOW").length
    }),
    [filteredReports]
  );

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div
        style={{
          padding: isMobile ? 18 : 20,
          borderRadius: 20,
          background: "linear-gradient(160deg, rgba(255,255,255,0.96) 0%, rgba(238,244,249,0.92) 100%)",
          border: "1px solid #dfe7f0"
        }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t("myReports.title")}
        </Typography.Title>
        <div style={{ height: 8 }} />
        <Typography.Text type="secondary">{t("myReports.subtitle")}</Typography.Text>
        {taskIdFilter ? (
          <>
            <br />
            <Typography.Text type="secondary">{t("myReports.filterTask", { taskId: taskIdFilter })}</Typography.Text>
          </>
        ) : null}
      </div>

      {reportsQuery.isError ? <Alert type="warning" showIcon message={t("myReports.error")} /> : null}

      {taskIdFilter ? (
        <Space>
          <Button onClick={() => navigate("/my/reports")}>{t("myReports.clearFilter")}</Button>
        </Space>
      ) : null}

      <Row gutter={[12, 12]}>
        <Col xs={24} md={8}>
          <Card size="small" styles={{ body: { padding: 16 } }} style={{ background: "linear-gradient(180deg, #fff5f5 0%, #ffffff 100%)" }}>
            <Typography.Text type="secondary">{t("myReports.summary.high")}</Typography.Text>
            <Typography.Title level={3} style={{ margin: "8px 0 0", color: "#cf1322" }}>
              {riskSummary.high}
            </Typography.Title>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size="small" styles={{ body: { padding: 16 } }} style={{ background: "linear-gradient(180deg, #fff9ec 0%, #ffffff 100%)" }}>
            <Typography.Text type="secondary">{t("myReports.summary.medium")}</Typography.Text>
            <Typography.Title level={3} style={{ margin: "8px 0 0", color: "#d48806" }}>
              {riskSummary.medium}
            </Typography.Title>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size="small" styles={{ body: { padding: 16 } }} style={{ background: "linear-gradient(180deg, #f3fbf5 0%, #ffffff 100%)" }}>
            <Typography.Text type="secondary">{t("myReports.summary.low")}</Typography.Text>
            <Typography.Title level={3} style={{ margin: "8px 0 0", color: "#389e0d" }}>
              {riskSummary.low}
            </Typography.Title>
          </Card>
        </Col>
      </Row>

      <Card>
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          {isMobile ? (
            <div
              style={{
                position: "sticky",
                top: 64,
                zIndex: 4,
                background: "rgba(255,255,255,0.96)",
                paddingBottom: 8,
                display: "grid",
                gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                gap: 8
              }}
            >
              <Button block type={riskFilter === "ALL" ? "primary" : "default"} onClick={() => setRiskFilter("ALL")}>
                {t("myReports.filter.all")}
              </Button>
              <Button block type={riskFilter === "HIGH" ? "primary" : "default"} onClick={() => setRiskFilter("HIGH")}>
                {t("myReports.filter.high")}
              </Button>
              <Button block type={riskFilter === "MEDIUM" ? "primary" : "default"} onClick={() => setRiskFilter("MEDIUM")}>
                {t("myReports.filter.medium")}
              </Button>
              <Button block type={riskFilter === "LOW" ? "primary" : "default"} onClick={() => setRiskFilter("LOW")}>
                {t("myReports.filter.low")}
              </Button>
            </div>
          ) : (
            <Space wrap>
              <Button type={riskFilter === "ALL" ? "primary" : "default"} onClick={() => setRiskFilter("ALL")}>
                {t("myReports.filter.all")}
              </Button>
              <Button type={riskFilter === "HIGH" ? "primary" : "default"} onClick={() => setRiskFilter("HIGH")}>
                {t("myReports.filter.high")}
              </Button>
              <Button type={riskFilter === "MEDIUM" ? "primary" : "default"} onClick={() => setRiskFilter("MEDIUM")}>
                {t("myReports.filter.medium")}
              </Button>
              <Button type={riskFilter === "LOW" ? "primary" : "default"} onClick={() => setRiskFilter("LOW")}>
                {t("myReports.filter.low")}
              </Button>
            </Space>
          )}

        {visibleReports.length ? (
          isMobile ? (
            <Space direction="vertical" size={12} style={{ width: "100%" }}>
              {visibleReports.map((record) => (
                <Card
                  key={record.reportId}
                  size="small"
                  styles={{ body: { padding: 16 } }}
                  style={{
                    borderRadius: 18,
                    boxShadow: "0 12px 28px rgba(19, 51, 78, 0.08)",
                    borderColor: "#e3edf7"
                  }}
                >
                  <Space direction="vertical" size={12} style={{ width: "100%" }}>
                    <div>
                      <Typography.Title level={5} style={{ margin: 0 }}>
                        {record.scaleName}
                      </Typography.Title>
                      <Typography.Text type="secondary">{record.taskName}</Typography.Text>
                    </div>
                    <Space wrap size={6}>
                      <Tag color={riskColor(record.riskLevel)}>{record.riskLevel}</Tag>
                      <Tag>{record.reportType}</Tag>
                      <Typography.Text>{t("myReports.col.score")}: {record.totalScore}</Typography.Text>
                      {record.standardScore !== null && record.standardScore !== undefined ? (
                        <Typography.Text>{t("myReports.col.standardScore")}: {record.standardScore}</Typography.Text>
                      ) : null}
                    </Space>
                    <Typography.Text type="secondary">{record.createdAt}</Typography.Text>
                    <Button block type="primary" size="large" onClick={() => navigate(`/reports/${record.reportId}?resultId=${record.resultId}`)}>
                      {t("myReports.open")}
                    </Button>
                  </Space>
                </Card>
              ))}
            </Space>
          ) : (
            <Table<MyReportSummary>
              rowKey="reportId"
              loading={reportsQuery.isLoading}
              pagination={false}
              dataSource={visibleReports}
              columns={[
                { title: t("myReports.col.task"), dataIndex: "taskName", key: "taskName" },
                { title: t("myReports.col.scale"), dataIndex: "scaleName", key: "scaleName" },
                { title: t("myReports.col.type"), dataIndex: "reportType", key: "reportType", width: 140 },
                { title: t("myReports.col.score"), dataIndex: "totalScore", key: "totalScore", width: 100 },
                {
                  title: t("myReports.col.standardScore"),
                  dataIndex: "standardScore",
                  key: "standardScore",
                  width: 140,
                  render: (value: number | null | undefined) => (value === null || value === undefined ? "-" : value)
                },
                {
                  title: t("myReports.col.risk"),
                  dataIndex: "riskLevel",
                  key: "riskLevel",
                  width: 120,
                  render: (value: string) => <Tag color={riskColor(value)}>{value}</Tag>
                },
                { title: t("myReports.col.createdAt"), dataIndex: "createdAt", key: "createdAt", width: 220 },
                {
                  title: t("myReports.col.action"),
                  key: "action",
                  width: 140,
                  render: (_: unknown, record: MyReportSummary) => (
                    <Button type="primary" onClick={() => navigate(`/reports/${record.reportId}?resultId=${record.resultId}`)}>
                      {t("myReports.open")}
                    </Button>
                  )
                }
              ]}
            />
          )
        ) : (
          <Empty description={taskIdFilter ? t("myReports.empty.filtered") : t("myReports.emptyFiltered")} />
        )}
        </Space>
      </Card>
    </Space>
  );
}
