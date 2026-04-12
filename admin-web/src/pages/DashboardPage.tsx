import { useQuery } from "@tanstack/react-query";
import { Card, Col, Progress, Row, Space, Statistic, Table, Typography } from "antd";
import { useI18n } from "../i18n/provider";
import { fetchDashboardStatistics } from "../features/statistics/api";

export function DashboardPage() {
  const { t } = useI18n();
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
        <Typography.Title level={4}>{t("dashboard.title")}</Typography.Title>
        <Typography.Text type="secondary">{t("dashboard.subtitle")}</Typography.Text>
      </div>

      <Row gutter={[16, 16]}>
        {overviewCards.map((card) => (
          <Col key={card.key} xs={24} sm={12} xl={8}>
            <Card>
              <Statistic title={card.label} value={card.value} suffix={card.suffix ?? ""} />
              {card.description ? <Typography.Text type="secondary">{card.description}</Typography.Text> : null}
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card title={t("dashboard.submissionTrend")}>
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
          <Card title={t("dashboard.warningTrend")}>
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
          <Card title={t("dashboard.taskStatus")}>
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
          <Card title={t("dashboard.riskDistribution")}>
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
          <Card title={t("dashboard.recentWarnings")}>
            <Table
              size="small"
              rowKey="warningId"
              pagination={false}
              dataSource={dashboardQuery.data?.recentWarnings ?? []}
              columns={[
                { title: t("dashboard.col.task"), dataIndex: "taskName" },
                { title: t("dashboard.col.level"), dataIndex: "warningLevel" },
                { title: t("dashboard.col.status"), dataIndex: "status" },
                {
                  title: t("dashboard.col.score"),
                  dataIndex: "totalScore",
                  render: (value: number) => value.toFixed(2)
                },
                {
                  title: t("dashboard.col.standardScore"),
                  dataIndex: "standardScore",
                  render: (value?: number | null) => (value == null ? "-" : value.toFixed(2))
                }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title={t("dashboard.recentReports")}>
            <Table
              size="small"
              rowKey="reportId"
              pagination={false}
              dataSource={dashboardQuery.data?.recentReports ?? []}
              columns={[
                { title: t("dashboard.col.task"), dataIndex: "taskName" },
                { title: t("dashboard.col.type"), dataIndex: "reportType" },
                { title: t("dashboard.col.risk"), dataIndex: "riskLevel" },
                {
                  title: t("dashboard.col.score"),
                  dataIndex: "totalScore",
                  render: (value: number) => value.toFixed(2)
                },
                {
                  title: t("dashboard.col.standardScore"),
                  dataIndex: "standardScore",
                  render: (value?: number | null) => (value == null ? "-" : value.toFixed(2))
                }
              ]}
            />
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
