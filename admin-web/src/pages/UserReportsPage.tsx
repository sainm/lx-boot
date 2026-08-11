import { useQuery } from "@tanstack/react-query";
import { SortDescendingOutlined } from "@ant-design/icons";
import { Button, Card, Col, Form, InputNumber, Row, Select, Space, Table, Tag, Typography } from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { HorizontalBarChart, SegmentedRiskBar, scoreRiskColor } from "../components/ReportCharts";
import { searchReports, type ReportSearchParams, type StaffReportSummary } from "../features/reports/api";
import { riskCategory, riskColor } from "../features/reports/risk";
import { fetchScalePage, type ScaleSummary } from "../features/scales/api";
import { fetchUserAdminGroups, fetchUserAdminUserPage, type UserAdminGroup, type UserAdminUser } from "../features/user-admin/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

type QueryState = {
  userId?: number;
  groupId?: number;
  scaleId?: number;
  taskId?: number;
};

const PAGE_SIZE = 20;

export function UserReportsPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [form] = Form.useForm<QueryState>();
  const [query, setQuery] = useState<ReportSearchParams>({ page: 1, size: PAGE_SIZE });
  const [userSearch, setUserSearch] = useState("");

  const reportsQuery = useQuery({
    queryKey: ["reports", "search", query],
    queryFn: () => searchReports(query)
  });
  const usersQuery = useQuery({
    queryKey: ["user-admin", "users", "report-picker", userSearch],
    queryFn: () =>
      fetchUserAdminUserPage({
        username: userSearch.trim() || undefined,
        page: 1,
        size: 20
      }),
    staleTime: 30_000
  });
  const groupsQuery = useQuery({
    queryKey: ["user-admin", "groups", "report-search"],
    queryFn: () => fetchUserAdminGroups(),
    staleTime: 60_000
  });
  const scalesQuery = useQuery({
    queryKey: ["scales", "report-search"],
    queryFn: () => fetchScalePage({ page: 1, size: 100 }),
    staleTime: 60_000
  });

  const reports = reportsQuery.data?.list ?? [];
  const userOptions = useMemo(
    () =>
      (usersQuery.data?.list ?? []).map((user: UserAdminUser) => ({
        label: `${user.displayName || user.username} / ${user.username} / #${user.userId}`,
        value: user.userId
      })),
    [usersQuery.data]
  );
  const groupOptions = useMemo(
    () =>
      (groupsQuery.data ?? []).map((group: UserAdminGroup) => ({
        label: `${group.groupName} (${group.groupCode}) / #${group.groupId}`,
        value: group.groupId
      })),
    [groupsQuery.data]
  );
  const scaleOptions = useMemo(
    () =>
      (scalesQuery.data?.list ?? []).map((scale: ScaleSummary) => ({
        label: `${scale.scaleName} (${scale.scaleCode}) / #${scale.id}`,
        value: scale.id
      })),
    [scalesQuery.data]
  );
  const overview = useMemo(() => {
    const highRiskCount = reports.filter((item) => ["critical", "high"].includes(riskCategory(item.riskLevel)) || item.highRiskFlag).length;
    const latestReport = reports[0];
    return [
      { label: t("userReports.summary.total"), value: reportsQuery.data?.total ?? 0 },
      { label: t("userReports.summary.highRisk"), value: highRiskCount },
      { label: t("userReports.summary.latest"), value: formatDateTime(latestReport?.createdAt) }
    ];
  }, [reports, reportsQuery.data?.total, t]);
  const chartData = useMemo(() => {
    const riskMap = new Map<string, number>();
    const scaleMap = new Map<string, number>();
    reports.forEach((report) => {
      const riskKey = report.highRiskFlag && report.riskLevel !== "HIGH" ? "HIGH_RISK_ITEM" : report.riskLevel;
      riskMap.set(riskKey, (riskMap.get(riskKey) ?? 0) + 1);
      scaleMap.set(report.scaleName, (scaleMap.get(report.scaleName) ?? 0) + 1);
    });
    const riskItems = Array.from(riskMap.entries()).map(([key, value]) => ({
      key,
      label: riskDisplayName(key, t),
      value,
      color: key === "HIGH_RISK_ITEM" ? "#b91c1c" : scoreRiskColor(key)
    }));
    const scoreItems = reports.slice(0, 10).map((report) => ({
      key: String(report.reportId),
      label: report.displayName || report.username,
      value: report.totalScore,
      color: scoreRiskColor(report.riskLevel),
      meta: report.scaleName
    }));
    const scaleItems = Array.from(scaleMap.entries())
      .map(([label, value]) => ({
        key: label,
        label,
        value
      }))
      .sort((left, right) => right.value - left.value);
    return { riskItems, scoreItems, scaleItems };
  }, [reports, t]);

  const handleSearch = async () => {
    const values = await form.validateFields();
    setQuery({ ...values, page: 1, size: PAGE_SIZE });
  };

  const handleReset = () => {
    form.resetFields();
    setQuery({ page: 1, size: PAGE_SIZE });
  };

  const columns = [
    {
      title: t("userReports.col.user"),
      key: "user",
      width: 220,
      render: (_: unknown, record: StaffReportSummary) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{record.displayName || record.username}</Typography.Text>
          <Typography.Text type="secondary">#{record.userId} / {record.username}</Typography.Text>
        </Space>
      )
    },
    {
      title: t("userReports.col.group"),
      key: "group",
      width: 160,
      render: (_: unknown, record: StaffReportSummary) => record.groupName || (record.groupId != null ? `#${record.groupId}` : "-")
    },
    { title: t("userReports.col.task"), dataIndex: "taskName", key: "taskName", width: 180 },
    { title: t("userReports.col.scale"), dataIndex: "scaleName", key: "scaleName", width: 160 },
    { title: t("userReports.col.type"), dataIndex: "reportType", key: "reportType", width: 120 },
    {
      title: t("userReports.col.score"),
      dataIndex: "totalScore",
      key: "totalScore",
      width: 110
    },
    {
      title: t("userReports.col.standardScore"),
      dataIndex: "standardScore",
      key: "standardScore",
      width: 130,
      render: (value?: number | null) => value ?? "-"
    },
    {
      title: t("userReports.col.risk"),
      dataIndex: "riskLevel",
      key: "riskLevel",
      width: 120,
      render: (value: string, record: StaffReportSummary) => (
        <Space size={4}>
          <Tag color={riskColor(value)}>{riskDisplayName(value, t)}</Tag>
          {record.highRiskFlag ? <Tag color="red">{t("userReports.highRiskFlag")}</Tag> : null}
        </Space>
      )
    },
    {
      title: (
        <Space size={4}>
          {t("userReports.col.createdAt")}
          <SortDescendingOutlined />
        </Space>
      ),
      dataIndex: "createdAt",
      key: "createdAt",
      width: 180,
      render: (value: string) => formatDateTime(value)
    },
    {
      title: t("userReports.col.action"),
      key: "action",
      width: 120,
      render: (_: unknown, record: StaffReportSummary) => (
        <Button size="small" type="primary" onClick={() => navigate(`/reports/${record.reportId}?resultId=${record.resultId}`)}>
          {t("userReports.open")}
        </Button>
      )
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4} style={{ marginBottom: 8 }}>
          {t("userReports.title")}
        </Typography.Title>
        <Typography.Text type="secondary">{t("userReports.subtitle")}</Typography.Text>
      </div>

      <Card>
        <Form form={form} layout="inline" onFinish={() => void handleSearch()}>
          <Form.Item label={t("userReports.userId")} name="userId">
            <Select
              allowClear
              showSearch
              filterOption={false}
              loading={usersQuery.isLoading}
              onSearch={setUserSearch}
              options={userOptions}
              style={{ width: 320 }}
              placeholder={t("userReports.userIdPlaceholder")}
            />
          </Form.Item>
          <Form.Item label={t("userReports.groupId")} name="groupId">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              loading={groupsQuery.isLoading}
              options={groupOptions}
              style={{ width: 260 }}
              placeholder={t("userReports.groupIdPlaceholder")}
            />
          </Form.Item>
          <Form.Item label={t("userReports.scaleId")} name="scaleId">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              loading={scalesQuery.isLoading}
              options={scaleOptions}
              style={{ width: 280 }}
              placeholder={t("userReports.scaleIdPlaceholder")}
            />
          </Form.Item>
          <Form.Item label={t("userReports.taskId")} name="taskId">
            <InputNumber min={1} precision={0} style={{ width: 140 }} placeholder={t("userReports.taskIdPlaceholder")} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {t("userReports.search")}
              </Button>
              <Button onClick={handleReset}>{t("userReports.reset")}</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Space wrap style={{ width: "100%" }}>
        {overview.map((item) => (
          <Card key={item.label} style={{ minWidth: 180, flex: 1 }}>
            <Typography.Text type="secondary">{item.label}</Typography.Text>
            <Typography.Title level={4} style={{ marginTop: 8, marginBottom: 0 }}>
              {item.value}
            </Typography.Title>
          </Card>
        ))}
      </Space>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={8}>
          <Card title={t("userReports.chart.risk")} size="small">
            <SegmentedRiskBar items={chartData.riskItems} emptyText={t("userReports.chart.empty")} />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title={t("userReports.chart.score")} size="small">
            <HorizontalBarChart items={chartData.scoreItems} emptyText={t("userReports.chart.empty")} />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title={t("userReports.chart.scale")} size="small">
            <HorizontalBarChart items={chartData.scaleItems} emptyText={t("userReports.chart.empty")} />
          </Card>
        </Col>
      </Row>

      <Table<StaffReportSummary>
        rowKey="reportId"
        loading={reportsQuery.isLoading}
        dataSource={reports}
        columns={columns}
        locale={{ emptyText: t("userReports.empty") }}
        scroll={{ x: 1320 }}
        pagination={{
          current: reportsQuery.data?.page ?? query.page,
          pageSize: reportsQuery.data?.size ?? PAGE_SIZE,
          total: reportsQuery.data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (total, range) => t("userReports.paginationTotal", { start: range[0], end: range[1], total }),
          onChange: (page, size) => setQuery((current) => ({ ...current, page, size }))
        }}
      />
    </Space>
  );
}

function riskDisplayName(riskLevel: string, t: (key: string) => string) {
  const key = `userReports.risk.${riskLevel}`;
  const translated = t(key);
  return translated === key ? riskLevel : translated;
}
