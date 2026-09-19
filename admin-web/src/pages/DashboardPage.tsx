import { useQuery } from "@tanstack/react-query";
import { Card, Col, Progress, Row, Space, Statistic, Table, Typography } from "antd";
import { useI18n } from "../i18n/provider";
import {
  reportTypeLabel,
  riskLevelLabel,
  taskStatusLabel,
  warningStatusLabel,
  type TranslateFn
} from "../i18n/enumLabel";
import { fetchDashboardStatistics } from "../features/statistics/api";

type DistributionItem = { key: string; value: number };

/**
 * Largest-remainder allocation so the rendered shares always add up to 100%
 * instead of 99% when every bucket rounds down.
 */
function allocatePercents(items: DistributionItem[]) {
  const total = items.reduce((sum, item) => sum + item.value, 0);
  if (total <= 0) {
    return items.map(() => 0);
  }
  const exact = items.map((item) => (item.value / total) * 100);
  const percents = exact.map((value) => Math.floor(value));
  let remainder = 100 - percents.reduce((sum, value) => sum + value, 0);
  const byFraction = exact
    .map((value, index) => ({ index, fraction: value - Math.floor(value) }))
    .sort((a, b) => b.fraction - a.fraction);
  for (const { index } of byFraction) {
    if (remainder <= 0) {
      break;
    }
    percents[index] += 1;
    remainder -= 1;
  }
  return percents;
}

/**
 * A distribution is a share of the current tenant scope, so it is rendered as
 * a labelled bar with both the count and the percentage instead of a bare
 * `code: number` list.
 */
function DistributionCard({
  title,
  items,
  labelOf,
  t
}: {
  title: string;
  items: DistributionItem[];
  labelOf: (t: TranslateFn, code: string) => string;
  t: TranslateFn;
}) {
  const percents = allocatePercents(items);
  return (
    <Card
      title={title}
      extra={
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {t("dashboard.distributionScope")}
        </Typography.Text>
      }
    >
      {items.length === 0 ? (
        <Typography.Text type="secondary">{t("dashboard.distributionEmpty")}</Typography.Text>
      ) : (
        <Space direction="vertical" style={{ width: "100%" }}>
          {items.map((item, index) => {
            const percent = percents[index] ?? 0;
            return (
              <div
                key={item.key}
                style={{
                  display: "grid",
                  gridTemplateColumns: "minmax(100px, 160px) 1fr 110px",
                  gap: 12,
                  alignItems: "center"
                }}
              >
                <Typography.Text>{labelOf(t, item.key)}</Typography.Text>
                <Progress percent={percent} showInfo={false} />
                <Typography.Text type="secondary">
                  {t("dashboard.distributionValue", { count: item.value, percent })}
                </Typography.Text>
              </div>
            );
          })}
        </Space>
      )}
    </Card>
  );
}

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
          <DistributionCard
            title={t("dashboard.taskStatus")}
            items={dashboardQuery.data?.taskStatusDistribution ?? []}
            labelOf={taskStatusLabel}
            t={t}
          />
        </Col>
        <Col xs={24} xl={12}>
          <DistributionCard
            title={t("dashboard.riskDistribution")}
            items={dashboardQuery.data?.riskDistribution ?? []}
            labelOf={riskLevelLabel}
            t={t}
          />
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
                {
                  title: t("dashboard.col.level"),
                  dataIndex: "warningLevel",
                  render: (value: string) => riskLevelLabel(t, value)
                },
                {
                  title: t("dashboard.col.status"),
                  dataIndex: "status",
                  render: (value: string) => warningStatusLabel(t, value)
                },
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
                {
                  title: t("dashboard.col.type"),
                  dataIndex: "reportType",
                  render: (value: string) => reportTypeLabel(t, value)
                },
                {
                  title: t("dashboard.col.risk"),
                  dataIndex: "riskLevel",
                  render: (value: string) => riskLevelLabel(t, value)
                },
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
