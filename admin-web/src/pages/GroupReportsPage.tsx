import { DownloadOutlined } from "@ant-design/icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, Col, Form, InputNumber, Progress, Row, Space, Table, Typography } from "antd";
import { message } from "antd";
import { useMemo, useState } from "react";
import { DimensionRadarChart, HorizontalBarChart, SegmentedRiskBar, scoreRiskColor } from "../components/ReportCharts";
import { downloadBlobFile, downloadGroupReportsPdf, fetchGroupReports } from "../features/statistics/api";
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
    mutationFn: () => downloadGroupReportsPdf({ ...query, page: 1, size: 200 }),
    onSuccess: (file) => {
      downloadBlobFile(file.blob, file.fileName, file.contentType);
      void message.success(t("groupReports.exportSuccess", { fileName: file.fileName }));
    },
    onError: () => {
      void message.error(t("groupReports.exportFailed"));
    }
  });

  const summaries = reportQuery.data?.list ?? [];
  const chartData = useMemo(() => {
    const completionItems = summaries.map((item) => ({
      key: `${item.taskId}-${item.groupId}`,
      label: item.groupName,
      value: item.completionRate,
      suffix: "%",
      meta: `${item.submittedCount}/${item.memberCount}`
    }));
    const riskMap = new Map<string, number>();
    const dimensionMap = new Map<string, { total: number; count: number }>();
    summaries.forEach((summary) => {
      summary.riskDistribution.forEach((risk) => {
        riskMap.set(risk.key, (riskMap.get(risk.key) ?? 0) + risk.value);
      });
      summary.dimensionStats.forEach((dimension) => {
        const current = dimensionMap.get(dimension.dimensionName) ?? { total: 0, count: 0 };
        dimensionMap.set(dimension.dimensionName, {
          total: current.total + dimension.averageScore,
          count: current.count + 1
        });
      });
    });
    const riskItems = Array.from(riskMap.entries()).map(([key, value]) => ({
      key,
      label: riskDisplayName(key, t),
      value,
      color: scoreRiskColor(key)
    }));
    const dimensionItems = Array.from(dimensionMap.entries())
      .map(([label, value]) => ({
        key: label,
        label,
        value: value.count === 0 ? 0 : value.total / value.count
      }))
      .sort((left, right) => right.value - left.value);
    return { completionItems, riskItems, dimensionItems };
  }, [summaries, t]);
  const overview = useMemo(() => {
    const count = summaries.length;
    const averageCompletionRate =
      count === 0 ? 0 : summaries.reduce((sum, item) => sum + item.completionRate, 0) / count;
    const highRiskGroups = summaries.filter((item) => item.highRiskCount > 0).length;
    const comparedGroups = summaries.filter((item) => Boolean(item.compareUserResult)).length;
    return [
      { label: t("groupReports.overview.current"), value: count },
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
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center" }}>
        <div>
          <Typography.Title level={4}>{t("groupReports.title")}</Typography.Title>
          <Typography.Text type="secondary">{t("groupReports.subtitle")}</Typography.Text>
        </div>
        <Space wrap>
          <Button icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => exportMutation.mutate()}>
            {t("groupReports.exportPdf")}
          </Button>
          <Button type="primary" onClick={() => void handleSearch()}>
            {t("groupReports.refresh")}
          </Button>
        </Space>
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
        <Col xs={24} xl={8}>
          <Card title={t("groupReports.chart.completion")} size="small">
            <HorizontalBarChart items={chartData.completionItems} emptyText={t("groupReports.chart.empty")} maxValue={100} />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title={t("groupReports.chart.risk")} size="small">
            <SegmentedRiskBar items={chartData.riskItems} emptyText={t("groupReports.chart.empty")} />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title={t("groupReports.chart.dimension")} size="small">
            <DimensionRadarChart items={chartData.dimensionItems} emptyText={t("groupReports.chart.empty")} />
          </Card>
        </Col>
      </Row>

      <Table
        rowKey={(record) => `${record.taskId}-${record.groupId}`}
        loading={reportQuery.isLoading}
        dataSource={summaries}
        pagination={false}
        expandable={{
          expandedRowRender: (record) => (
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Card size="small" title={t("groupReports.riskCard")}>
                <SegmentedRiskBar
                  items={record.riskDistribution.map((item) => ({
                    key: item.key,
                    label: riskDisplayName(item.key, t),
                    value: item.value,
                    color: scoreRiskColor(item.key)
                  }))}
                  emptyText={t("groupReports.chart.empty")}
                />
              </Card>
              <Card size="small" title={t("groupReports.dimensionCard")}>
                <HorizontalBarChart
                  items={record.dimensionStats.map((dimension) => ({
                    key: String(dimension.dimensionId ?? dimension.dimensionName),
                    label: dimension.dimensionName,
                    value: dimension.averageScore,
                    meta: t("groupReports.dimensionAnswerCount", { count: dimension.answerCount })
                  }))}
                  emptyText={t("groupReports.chart.empty")}
                />
              </Card>
            </Space>
          )
        }}
        columns={columns}
      />
    </Space>
  );
}

function riskDisplayName(riskLevel: string, t: (key: string) => string) {
  const key = `groupReports.risk.${riskLevel}`;
  const translated = t(key);
  return translated === key ? riskLevel : translated;
}
