import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Empty, Space, Table, Tag, Typography } from "antd";
import { useNavigate } from "react-router-dom";
import { fetchMyReports, type MyReportSummary } from "../features/reports/api";

function riskColor(riskLevel: string) {
  switch (riskLevel) {
    case "HIGH":
      return "red";
    case "MEDIUM":
      return "gold";
    case "LOW":
      return "blue";
    default:
      return "green";
  }
}

export function MyReportListPage() {
  const navigate = useNavigate();
  const reportsQuery = useQuery({
    queryKey: ["reports", "my"],
    queryFn: fetchMyReports
  });

  const reports = reportsQuery.data ?? [];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4}>My Reports</Typography.Title>
        <Typography.Text type="secondary">
          View your submitted questionnaire reports and open the full detail page.
        </Typography.Text>
      </div>

      {reportsQuery.isError ? <Alert type="warning" showIcon message="Unable to load your reports right now." /> : null}

      <Card>
        {reports.length ? (
          <Table<MyReportSummary>
            rowKey="reportId"
            loading={reportsQuery.isLoading}
            pagination={false}
            dataSource={reports}
            columns={[
              { title: "Task", dataIndex: "taskName", key: "taskName" },
              { title: "Scale", dataIndex: "scaleName", key: "scaleName" },
              { title: "Report Type", dataIndex: "reportType", key: "reportType", width: 140 },
              {
                title: "Risk Level",
                dataIndex: "riskLevel",
                key: "riskLevel",
                width: 120,
                render: (value: string) => <Tag color={riskColor(value)}>{value}</Tag>
              },
              { title: "Score", dataIndex: "totalScore", key: "totalScore", width: 100 },
              { title: "Created At", dataIndex: "createdAt", key: "createdAt", width: 220 },
              {
                title: "Action",
                key: "action",
                width: 140,
                render: (_: unknown, record: MyReportSummary) => (
                  <Button type="primary" onClick={() => navigate(`/reports/${record.reportId}`)}>
                    Open
                  </Button>
                )
              }
            ]}
          />
        ) : (
          <Empty description="No reports available yet" />
        )}
      </Card>
    </Space>
  );
}
