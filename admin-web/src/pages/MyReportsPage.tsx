import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Empty, Space, Table, Tag, Typography } from "antd";
import { useMemo } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { fetchMyReports, type MyReportSummary } from "../features/reports/api";

function riskColor(riskLevel: string) {
  switch (riskLevel) {
    case "HIGH":
      return "red";
    case "MEDIUM":
      return "gold";
    default:
      return "green";
  }
}

export function MyReportsPage() {
  const navigate = useNavigate();
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

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4}>My Reports</Typography.Title>
        <Typography.Text type="secondary">
          Review the system-generated reports for questionnaires you have already submitted.
        </Typography.Text>
        {taskIdFilter ? (
          <>
            <br />
            <Typography.Text type="secondary">Showing reports for task #{taskIdFilter}.</Typography.Text>
          </>
        ) : null}
      </div>

      {reportsQuery.isError ? <Alert type="warning" showIcon message="Unable to load your reports right now." /> : null}

      {taskIdFilter ? (
        <Space>
          <Button onClick={() => navigate("/my/reports")}>Clear task filter</Button>
        </Space>
      ) : null}

      <Card>
        {filteredReports.length ? (
          <Table<MyReportSummary>
            rowKey="reportId"
            loading={reportsQuery.isLoading}
            pagination={false}
            dataSource={filteredReports}
            columns={[
              { title: "Task", dataIndex: "taskName", key: "taskName" },
              { title: "Scale", dataIndex: "scaleName", key: "scaleName" },
              { title: "Report Type", dataIndex: "reportType", key: "reportType", width: 140 },
              { title: "Score", dataIndex: "totalScore", key: "totalScore", width: 100 },
              {
                title: "Risk",
                dataIndex: "riskLevel",
                key: "riskLevel",
                width: 120,
                render: (value: string) => <Tag color={riskColor(value)}>{value}</Tag>
              },
              { title: "Created At", dataIndex: "createdAt", key: "createdAt", width: 220 },
              {
                title: "Action",
                key: "action",
                width: 140,
                render: (_: unknown, record: MyReportSummary) => (
                  <Button type="primary" onClick={() => navigate(`/reports/${record.reportId}?resultId=${record.resultId}`)}>
                    Open
                  </Button>
                )
              }
            ]}
          />
        ) : (
          <Empty description={taskIdFilter ? "No reports found for this task yet" : "No reports yet"} />
        )}
      </Card>
    </Space>
  );
}
