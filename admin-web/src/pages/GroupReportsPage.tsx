import { DownloadOutlined } from "@ant-design/icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, Col, Form, InputNumber, Progress, Row, Space, Table, Typography } from "antd";
import { message } from "antd";
import { useMemo, useState } from "react";
import { ChartRenderer } from "../components/ReportCharts";
import { downloadBlobFile, downloadGroupReportsFile, fetchGroupReports, type GroupReportExportFormat, type GroupReportSummary } from "../features/statistics/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

type QueryState = {
  taskId?: number;
  groupId?: number;
  scaleId?: number;
  compareUserId?: number;
};

export function GroupReportsPage() {
  const { t } = useI18n();
  const [form] = Form.useForm<QueryState>();
  const [query, setQuery] = useState<QueryState>({});

  const reportQuery = useQuery({
    queryKey: ["statistics", "group-reports", query],
    queryFn: () => fetchGroupReports({ ...query, page: 1, size: 20 })
  });

  const exportMutation = useMutation({
    mutationFn: ({ format, params }: { format: GroupReportExportFormat; params: QueryState }) =>
      downloadGroupReportsFile({ ...params, page: 1, size: 200, format }),
    onSuccess: (file) => {
      downloadBlobFile(file.blob, file.fileName, file.contentType);
      void message.success(t("groupReports.exportSuccess", { fileName: file.fileName }));
    },
    onError: () => {
      void message.error(t("groupReports.exportFailed"));
    }
  });

  const summaries = reportQuery.data?.list ?? [];
  const overview = useMemo(() => {
    const count = summaries.length;
    const averageCompletionRate =
      count === 0 ? 0 : summaries.reduce((sum, item) => sum + item.completionRate, 0) / count;
    const highRiskGroups = summaries.filter((item) => item.highRiskCount > 0).length;
    const comparedGroups = summaries.filter((item) => Boolean(item.compareUserResult)).length;
    const memberCount = summaries.reduce((sum, item) => sum + item.memberCount, 0);
    const submittedCount = summaries.reduce((sum, item) => sum + item.submittedCount, 0);
    return [
      { label: t("groupReports.overview.current"), value: count },
      { label: t("groupReports.overview.members"), value: memberCount },
      { label: t("groupReports.overview.submitted"), value: submittedCount },
      { label: t("groupReports.overview.avgCompletion"), value: averageCompletionRate.toFixed(2), suffix: "%" },
      { label: t("groupReports.overview.highRisk"), value: highRiskGroups },
      { label: t("groupReports.overview.compare"), value: comparedGroups }
    ];
  }, [summaries, t]);

  const handleSearch = async () => {
    const values = await form.validateFields();
    setQuery({
      taskId: values.taskId,
      groupId: values.groupId,
      scaleId: values.scaleId,
      compareUserId: values.compareUserId
    });
  };

  const handleExport = (record: GroupReportSummary, format: GroupReportExportFormat) => {
    exportMutation.mutate({
      format,
      params: {
        taskId: record.taskId,
        groupId: record.groupId,
        scaleId: record.scaleId,
        compareUserId: query.compareUserId
      }
    });
  };

  const columns = [
    { title: t("groupReports.col.task"), dataIndex: "taskName" },
    { title: t("groupReports.col.group"), dataIndex: "groupName" },
    { title: t("groupReports.col.memberCount"), dataIndex: "memberCount" },
    { title: t("groupReports.col.submittedCount"), dataIndex: "submittedCount" },
    {
      title: t("groupReports.col.completionRate"),
      dataIndex: "completionRate",
      render: (value: number) => <Progress percent={value} size="small" />
    },
    {
      title: t("groupReports.col.avgScore"),
      dataIndex: "averageScore",
      render: (value?: number | null) => (value == null ? "-" : value.toFixed(2))
    },
    { title: t("groupReports.col.highRisk"), dataIndex: "highRiskCount" },
    { title: t("groupReports.col.warningCount"), dataIndex: "warningCount" },
    {
      title: t("groupReports.col.compareUser"),
      dataIndex: "compareUserResult",
      render: (value?: {
        displayName?: string;
        totalScore: number;
        riskLevel: string;
        standardScore?: number | null;
        normCode?: string | null;
        scoreGapToAverage?: number | null;
      }) =>
        value ? (
          <Space direction="vertical" size={0}>
            <Typography.Text>{value.displayName ?? t("groupReports.anonymous")}</Typography.Text>
            <Typography.Text type="secondary">
              {value.totalScore.toFixed(2)} / {value.riskLevel}
              {value.scoreGapToAverage != null ? ` / ${t("groupReports.scoreGap", { value: value.scoreGapToAverage.toFixed(2) })}` : ""}
            </Typography.Text>
            {value.standardScore != null ? (
              <Typography.Text type="secondary">
                {t("groupReports.standardScoreLabel")}: {value.standardScore.toFixed(2)}
                {value.normCode ? ` / ${value.normCode}` : ""}
              </Typography.Text>
            ) : null}
          </Space>
        ) : (
          "-"
        )
    },
    {
      title: t("groupReports.col.latestSubmitted"),
      dataIndex: "latestSubmittedAt",
      render: (value?: string | null) => formatDateTime(value)
    },
    {
      title: t("groupReports.col.actions"),
      fixed: "right" as const,
      render: (_: unknown, record: GroupReportSummary) => (
        <Space>
          <Button size="small" icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => handleExport(record, "PDF")}>
            PDF
          </Button>
          <Button size="small" icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => handleExport(record, "WORD")}>
            Word
          </Button>
        </Space>
      )
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center" }}>
        <div>
          <Typography.Title level={4}>{t("groupReports.title")}</Typography.Title>
          <Typography.Text type="secondary">{t("groupReports.subtitle")}</Typography.Text>
        </div>
        <Button type="primary" onClick={() => void handleSearch()}>
          {t("groupReports.refresh")}
        </Button>
      </div>

      <Card>
        <Form form={form} layout="inline" initialValues={query}>
          <Form.Item label={t("groupReports.taskId")} name="taskId">
            <InputNumber min={1} style={{ width: 140 }} placeholder={t("groupReports.taskId")} />
          </Form.Item>
          <Form.Item label={t("groupReports.groupId")} name="groupId">
            <InputNumber min={1} style={{ width: 140 }} placeholder={t("groupReports.groupId")} />
          </Form.Item>
          <Form.Item label={t("groupReports.scaleId")} name="scaleId">
            <InputNumber min={1} style={{ width: 140 }} placeholder={t("groupReports.scaleId")} />
          </Form.Item>
          <Form.Item label={t("groupReports.compareUserId")} name="compareUserId">
            <InputNumber min={1} style={{ width: 160 }} placeholder={t("groupReports.compareUserId")} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={() => void handleSearch()}>
              {t("groupReports.search")}
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Space wrap style={{ width: "100%" }}>
        {overview.map((item) => (
          <Card key={item.label} style={{ minWidth: 180, flex: 1 }}>
            <Typography.Text type="secondary">{item.label}</Typography.Text>
            <Typography.Title level={3} style={{ marginTop: 8, marginBottom: 0 }}>
              {item.value}
              {item.suffix ?? ""}
            </Typography.Title>
          </Card>
        ))}
      </Space>

      <Row gutter={[16, 16]}>
        {summaries.slice(0, 3).map((summary) => (
          <Col key={`${summary.taskId}-${summary.groupId}`} xs={24} xl={8}>
            <ChartRenderer visualizations={summary.visualizations} emptyText={t("groupReports.chart.empty")} />
          </Col>
        ))}
      </Row>

      <Table
        rowKey={(record) => `${record.taskId}-${record.groupId}`}
        loading={reportQuery.isLoading}
        dataSource={summaries}
        pagination={false}
        expandable={{
          expandedRowRender: (record) => (
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Card size="small" title={t("groupReports.section.dimensions")}>
                <Table
                  size="small"
                  pagination={false}
                  rowKey={(item) => `${record.taskId}-${record.groupId}-${item.dimensionId ?? item.dimensionName}`}
                  dataSource={record.dimensionStats}
                  columns={[
                    { title: t("groupReports.dimension.name"), dataIndex: "dimensionName" },
                    { title: t("groupReports.dimension.average"), dataIndex: "averageScore", render: (value: number) => value.toFixed(2) },
                    {
                      title: t("groupReports.dimension.standardDeviation"),
                      dataIndex: "standardDeviation",
                      render: (value?: number | null) => (value == null ? "-" : value.toFixed(2))
                    },
                    {
                      title: t("groupReports.dimension.maxScore"),
                      dataIndex: "maxScore",
                      render: (value?: number | null) => (value == null ? "-" : value.toFixed(2))
                    },
                    {
                      title: t("groupReports.dimension.minScore"),
                      dataIndex: "minScore",
                      render: (value?: number | null) => (value == null ? "-" : value.toFixed(2))
                    },
                    { title: t("groupReports.dimension.criticalValue"), render: () => "2.0" },
                    { title: t("groupReports.dimension.exceedCount"), dataIndex: "exceedCount", render: (value?: number | null) => value ?? 0 }
                  ]}
                />
              </Card>
              <Card size="small" title={t("groupReports.section.suggestion")}>
                <Typography.Paragraph style={{ marginBottom: 0 }}>{t("groupReports.suggestionText")}</Typography.Paragraph>
              </Card>
              <ChartRenderer visualizations={record.visualizations} emptyText={t("groupReports.chart.empty")} />
            </Space>
          )
        }}
        columns={columns}
      />
    </Space>
  );
}
