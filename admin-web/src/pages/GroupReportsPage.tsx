import { useQuery } from "@tanstack/react-query";
import { Button, Card, Descriptions, Form, InputNumber, Progress, Space, Table, Tag, Typography } from "antd";
import { useMemo, useState } from "react";
import { fetchGroupReports } from "../features/statistics/api";

type QueryState = {
  taskId?: number;
  groupId?: number;
  scaleId?: number;
  compareUserId?: number;
};

export function GroupReportsPage() {
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
      { label: "当前页群体报告", value: count },
      { label: "平均完成率", value: averageCompletionRate.toFixed(2), suffix: "%" },
      { label: "高风险群体数", value: highRiskGroups },
      { label: "带个人对比", value: comparedGroups }
    ];
  }, [summaries]);

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
    {
      title: "任务",
      dataIndex: "taskName"
    },
    {
      title: "群组",
      dataIndex: "groupName"
    },
    {
      title: "组内人数",
      dataIndex: "memberCount"
    },
    {
      title: "已提交",
      dataIndex: "submittedCount"
    },
    {
      title: "完成率",
      dataIndex: "completionRate",
      render: (value: number) => <Progress percent={value} size="small" />
    },
    {
      title: "平均分",
      dataIndex: "averageScore",
      render: (value?: number | null) => (value == null ? "-" : value.toFixed(2))
    },
    {
      title: "高风险",
      dataIndex: "highRiskCount"
    },
    {
      title: "预警数",
      dataIndex: "warningCount"
    },
    {
      title: "个人对比",
      dataIndex: "compareUserResult",
      render: (value?: { displayName?: string; totalScore: number; riskLevel: string; scoreGapToAverage?: number | null }) =>
        value ? (
          <Space direction="vertical" size={0}>
            <Typography.Text>{value.displayName ?? "匿名用户"}</Typography.Text>
            <Typography.Text type="secondary">
              {value.totalScore.toFixed(2)} / {value.riskLevel}
              {value.scoreGapToAverage != null ? ` / 差值 ${value.scoreGapToAverage.toFixed(2)}` : ""}
            </Typography.Text>
          </Space>
        ) : (
          "-"
        )
    },
    {
      title: "最新提交",
      dataIndex: "latestSubmittedAt",
      render: (value?: string | null) => value ?? "-"
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center" }}>
        <div>
          <Typography.Title level={4}>群体报告</Typography.Title>
          <Typography.Text type="secondary">
            按任务和群组聚合展示群体完成情况、风险分布以及个人对比信息。
          </Typography.Text>
        </div>
        <Button type="primary" onClick={() => void handleSearch()}>
          刷新
        </Button>
      </div>

      <Card>
        <Form form={form} layout="inline" initialValues={query}>
          <Form.Item label="任务ID" name="taskId">
            <InputNumber min={1} style={{ width: 140 }} placeholder="任务ID" />
          </Form.Item>
          <Form.Item label="群组ID" name="groupId">
            <InputNumber min={1} style={{ width: 140 }} placeholder="群组ID" />
          </Form.Item>
          <Form.Item label="量表ID" name="scaleId">
            <InputNumber min={1} style={{ width: 140 }} placeholder="量表ID" />
          </Form.Item>
          <Form.Item label="对比用户ID" name="compareUserId">
            <InputNumber min={1} style={{ width: 160 }} placeholder="用户ID" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={() => void handleSearch()}>
              查询
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
              <Card size="small" title="风险分布">
                <Space wrap>
                  {record.riskDistribution.map((item) => (
                    <Tag key={item.key} color={item.key === "HIGH" ? "red" : item.key === "ATTENTION" ? "gold" : "green"}>
                      {item.key}: {item.value}
                    </Tag>
                  ))}
                </Space>
              </Card>
              <Card size="small" title="维度平均分">
                <Descriptions bordered size="small" column={2}>
                  {record.dimensionStats.map((dimension) => (
                    <Descriptions.Item
                      key={dimension.dimensionId ?? dimension.dimensionName}
                      label={dimension.dimensionName}
                    >
                      {dimension.averageScore.toFixed(2)} / {dimension.answerCount} 题
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
