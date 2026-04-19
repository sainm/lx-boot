import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Empty, Grid, Space, Table, Typography } from "antd";
import { useMemo } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { fetchMyReports, type MyReportSummary } from "../features/reports/api";
import { useI18n } from "../i18n/provider";

export function MyReportsPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
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
  const usingFallbackReports = Boolean(taskIdFilter) && filteredReports.length === 0 && reports.length > 0;
  const displayReports = usingFallbackReports ? reports : filteredReports;
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
      {usingFallbackReports ? (
        <Alert
          type="info"
          showIcon
          message="当前任务筛选下没有匹配到历史报告，已为你显示全部历史报告。"
        />
      ) : null}

      {taskIdFilter ? (
        <Space>
          <Button onClick={() => navigate("/my/reports")}>{t("myReports.clearFilter")}</Button>
        </Space>
      ) : null}

      <Card size="small" styles={{ body: { padding: 16 } }}>
        <Space direction="vertical" size={4}>
          <Typography.Text type="secondary">{t("myReports.summary.total")}</Typography.Text>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {displayReports.length}
          </Typography.Title>
          <Typography.Text type="secondary">{t("myReports.summary.note")}</Typography.Text>
        </Space>
      </Card>

      <Card>
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
        {displayReports.length ? (
          isMobile ? (
            <Space direction="vertical" size={12} style={{ width: "100%" }}>
              {displayReports.map((record) => (
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
                      <Typography.Text>{record.reportType}</Typography.Text>
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
              dataSource={displayReports}
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
