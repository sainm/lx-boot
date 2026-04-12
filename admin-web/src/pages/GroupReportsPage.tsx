import { useQuery } from "@tanstack/react-query";
import { Button, Card, Descriptions, Form, InputNumber, Progress, Space, Table, Tag, Typography } from "antd";
import { useMemo, useState } from "react";
import { fetchGroupReports } from "../features/statistics/api";
import { useI18n } from "../i18n/provider";

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

  const summaries = reportQuery.data?.list ?? [];
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
      render: (value?: string | null) => value ?? "-"
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

      <Table
        rowKey={(record) => `${record.taskId}-${record.groupId}`}
        loading={reportQuery.isLoading}
        dataSource={summaries}
        pagination={false}
        expandable={{
          expandedRowRender: (record) => (
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Card size="small" title={t("groupReports.riskCard")}>
                <Space wrap>
                  {record.riskDistribution.map((item) => (
                    <Tag key={item.key} color={item.key === "HIGH" ? "red" : item.key === "ATTENTION" ? "gold" : "green"}>
                      {item.key}: {item.value}
                    </Tag>
                  ))}
                </Space>
              </Card>
              <Card size="small" title={t("groupReports.dimensionCard")}>
                <Descriptions bordered size="small" column={2}>
                  {record.dimensionStats.map((dimension) => (
                    <Descriptions.Item key={dimension.dimensionId ?? dimension.dimensionName} label={dimension.dimensionName}>
                      {dimension.averageScore.toFixed(2)} / {t("groupReports.dimensionAnswerCount", { count: dimension.answerCount })}
                    </Descriptions.Item>
                  ))}
                </Descriptions>
              </Card>
            </Space>
          )
        }}
        columns={columns}
      />
    </Space>
  );
}
