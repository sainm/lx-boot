import { useQuery } from "@tanstack/react-query";
import { Card, Col, Progress, Row, Space, Statistic, Table, Typography } from "antd";
import { fetchDashboardStatistics } from "../features/statistics/api";

export function DashboardPage() {
  const dashboardQuery = useQuery({
    queryKey: ["statistics", "dashboard"],
    queryFn: fetchDashboardStatistics
  });

  const overviewCards = dashboardQuery.data?.overviewCards ?? [];
  const submissionTrend = dashboardQuery.data?.submissionTrend ?? [];
  const warningTrend = dashboardQuery.data?.warningTrend ?? [];

  const maxSubmission = Math.max(1, ...submissionTrend.map((item) => item.count));
  const maxWarning = Math.max(1, ...warningTrend.map((item) => item.count));

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4}>统计看板</Typography.Title>
        <Typography.Text type="secondary">
          聚合展示量表、任务、答卷、预警和报告的核心状态。
        </Typography.Text>
      </div>

      <Row gutter={[16, 16]}>
        {overviewCards.map((card) => (
          <Col key={card.key} xs={24} sm={12} xl={8}>
            <Card>
              <Statistic
                title={card.label}
                value={card.value}
                suffix={card.suffix ?? ""}
              />
              {card.description ? (
                <Typography.Text type="secondary">{card.description}</Typography.Text>
              ) : null}
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card title="近 7 天提交趋势">
            <Space direction="vertical" style={{ width: "100%" }}>
              {submissionTrend.map((item) => (
                <div key={item.day} style={{ display: "grid", gridTemplateColumns: "100px 1fr 60px", gap: 12, alignItems: "center" }}>
                  <Typography.Text>{item.day}</Typography.Text>
                  <Progress percent={Math.round((item.count / maxSubmission) * 100)} showInfo={false} />
                  <Typography.Text>{item.count}</Typography.Text>
                </div>
              ))}
            </Space>
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title="近 7 天预警趋势">
            <Space direction="vertical" style={{ width: "100%" }}>
              {warningTrend.map((item) => (
                <div key={item.day} style={{ display: "grid", gridTemplateColumns: "100px 1fr 60px", gap: 12, alignItems: "center" }}>
                  <Typography.Text>{item.day}</Typography.Text>
                  <Progress percent={Math.round((item.count / maxWarning) * 100)} showInfo={false} />
                  <Typography.Text>{item.count}</Typography.Text>
                </div>
              ))}
            </Space>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card title="任务状态分布">
            <Space wrap>
              {dashboardQuery.data?.taskStatusDistribution.map((item) => (
                <Typography.Text key={item.key}>
                  {item.key}: {item.value}
                </Typography.Text>
              )) ?? null}
            </Space>
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title="风险等级分布">
            <Space wrap>
              {dashboardQuery.data?.riskDistribution.map((item) => (
                <Typography.Text key={item.key}>
                  {item.key}: {item.value}
                </Typography.Text>
              )) ?? null}
            </Space>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card title="最近预警">
            <Table
              size="small"
              rowKey="warningId"
              pagination={false}
              dataSource={dashboardQuery.data?.recentWarnings ?? []}
              columns={[
                { title: "任务", dataIndex: "taskName" },
                { title: "等级", dataIndex: "warningLevel" },
                { title: "状态", dataIndex: "status" },
                {
                  title: "总分",
                  dataIndex: "totalScore",
                  render: (value: number) => value.toFixed(2)
                }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title="最近报告">
            <Table
              size="small"
              rowKey="reportId"
              pagination={false}
              dataSource={dashboardQuery.data?.recentReports ?? []}
              columns={[
                { title: "任务", dataIndex: "taskName" },
                { title: "类型", dataIndex: "reportType" },
                { title: "风险", dataIndex: "riskLevel" },
                {
                  title: "总分",
                  dataIndex: "totalScore",
                  render: (value: number) => value.toFixed(2)
                }
              ]}
            />
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
