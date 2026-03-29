import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Col, Descriptions, InputNumber, Result, Row, Space, Statistic, Tag, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useSession } from "../auth/session";
import { ExportReportDialog } from "../components/ExportReportDialog";
import { Permission } from "../components/Permission";
import { fetchReportByResultId, fetchReportDetail } from "../features/reports/api";

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

function riskLabel(riskLevel: string) {
  switch (riskLevel) {
    case "HIGH":
      return "High";
    case "MEDIUM":
      return "Medium";
    default:
      return "Low";
  }
}

function riskSummary(riskLevel: string) {
  switch (riskLevel) {
    case "HIGH":
      return {
        type: "error" as const,
        title: "System conclusion: high risk",
        description: "Please review the suggested follow-up actions and contact your counselor or support contact as soon as possible."
      };
    case "MEDIUM":
      return {
        type: "warning" as const,
        title: "System conclusion: medium risk",
        description: "This result suggests you should keep an eye on recent changes and consider a follow-up appointment if needed."
      };
    default:
      return {
        type: "success" as const,
        title: "System conclusion: low risk",
        description: "The current result looks stable. Continue normal self-care and keep an eye on future assessments."
      };
  }
}

function nextStepHint(riskLevel: string) {
  switch (riskLevel) {
    case "HIGH":
      return "Reach out to a counselor, review your appointment options, and keep this report available for follow-up."
    case "MEDIUM":
      return "Review this report with a counselor if needed, and consider scheduling a follow-up appointment."
    default:
      return "No immediate action is required. Continue with your regular routine and future check-ins."
  }
}

export function ReportDetailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentRole } = useSession();
  const isUserView = currentRole === "USER";
  const { reportId } = useParams();
  const [searchParams] = useSearchParams();
  const resultId = searchParams.get("resultId");
  const taskId = searchParams.get("taskId");
  const notificationSource = searchParams.get("notificationSource");
  const [inputId, setInputId] = useState<number | null>(reportId ? Number(reportId) : null);
  const [exportOpen, setExportOpen] = useState(false);

  useEffect(() => {
    if (reportId) {
      setInputId(Number(reportId));
    }
  }, [reportId]);

  const detailQuery = useQuery({
    queryKey: ["reports", reportId, resultId],
    queryFn: () => {
      if (reportId) {
        return fetchReportDetail(Number(reportId));
      }
      if (resultId) {
        return fetchReportByResultId(Number(resultId));
      }
      throw new Error("missing report id");
    },
    enabled: Boolean(reportId || resultId)
  });

  const exportTarget = useMemo(
    () =>
      reportId || resultId
        ? {
            reportId: reportId ? Number(reportId) : undefined,
            resultId: resultId ? Number(resultId) : undefined
          }
        : null,
    [reportId, resultId]
  );

  const systemSummary = useMemo(() => {
    if (!detailQuery.data) {
      return null;
    }
    return riskSummary(detailQuery.data.riskLevel);
  }, [detailQuery.data]);

  const loadReport = () => {
    if (!inputId || Number.isNaN(inputId) || inputId <= 0) {
      message.warning("Please enter a valid report id.");
      return;
    }
    navigate(`/reports/${inputId}`);
  };

  const goBack = () => {
    if (currentRole === "USER") {
      navigate(taskId ? `/my/reports?taskId=${taskId}` : "/my/reports");
      return;
    }
    if (window.history.length > 1 && location.key !== "default") {
      navigate(-1);
      return;
    }
    navigate("/dashboard");
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 8 }}>
            {isUserView ? "My Report" : "Report Detail"}
          </Typography.Title>
          <Typography.Text type="secondary">
            {isUserView
              ? "Read the system-generated conclusion, review the score snapshot, and use the back button to return to your report list."
              : "Open a report by report id or result id, then review the score and system-generated content."}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button onClick={goBack}>{isUserView ? "Back to my reports" : "Back"}</Button>
          {resultId ? <Typography.Text type="secondary">Result ID: {resultId}</Typography.Text> : null}
          {!isUserView ? (
            <>
              <InputNumber
                min={1}
                placeholder="Report ID"
                value={inputId ?? undefined}
                onChange={(value) => setInputId(value ?? null)}
                style={{ width: 160 }}
              />
              <Button type="primary" onClick={loadReport}>
                Load report
              </Button>
            </>
          ) : null}
          <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"]}>
            <Button onClick={() => setExportOpen(true)} disabled={!exportTarget}>
              Export report
            </Button>
          </Permission>
        </Space>
      </div>

      {!reportId && !resultId ? (
        <Result
          status="info"
          title="Enter a report id"
          subTitle="You can open this page directly from the report list, a task submission result, or by entering a report id."
        />
      ) : detailQuery.isError ? (
        <Result
          status="warning"
          title="Unable to load report"
          subTitle="The selected report could not be loaded. Please verify that the report still exists and that you have access."
        />
      ) : detailQuery.isLoading ? (
        <Result status="info" title="Loading report" />
      ) : detailQuery.data ? (
        isUserView ? (
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            {notificationSource === "REPORT_GENERATED" ? (
              <Alert
                type="success"
                showIcon
                message="Opened from your report notification"
                description="This report was opened directly from a notification after the assessment was submitted."
              />
            ) : null}
            {systemSummary ? (
              <Alert
                type={systemSummary.type}
                showIcon
                message={systemSummary.title}
                description={
                  <Space direction="vertical" size={4}>
                    <Typography.Text>{systemSummary.description}</Typography.Text>
                    <Typography.Text type="secondary">
                      You can return to your report list at any time or open the appointment page if you want follow-up support.
                    </Typography.Text>
                  </Space>
                }
              />
            ) : null}
            <Row gutter={16}>
              <Col xs={24} md={8}>
                <Card>
                  <Statistic title="Total Score" value={detailQuery.data.totalScore} />
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card>
                  <Space direction="vertical" size={8} style={{ width: "100%" }}>
                    <Typography.Text type="secondary">Risk Level</Typography.Text>
                    <Tag color={riskColor(detailQuery.data.riskLevel)} style={{ width: "fit-content", fontSize: 14, padding: "2px 10px" }}>
                      {riskLabel(detailQuery.data.riskLevel)}
                    </Tag>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card>
                  <Statistic title="Report Type" value={detailQuery.data.reportType} />
                </Card>
              </Col>
            </Row>
            <Card title="Assessment snapshot">
              <Descriptions bordered column={2} size="small">
                <Descriptions.Item label="Report ID">{detailQuery.data.reportId}</Descriptions.Item>
                <Descriptions.Item label="Result ID">{detailQuery.data.resultId}</Descriptions.Item>
                <Descriptions.Item label="Report Type">{detailQuery.data.reportType}</Descriptions.Item>
                <Descriptions.Item label="Risk Level">{detailQuery.data.riskLevel}</Descriptions.Item>
              </Descriptions>
            </Card>
            <Card title="System-generated summary">
              <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>
                {detailQuery.data.content}
              </Typography.Paragraph>
            </Card>
            <Card title="Suggested next step">
              <Typography.Paragraph style={{ marginBottom: 0 }}>{nextStepHint(detailQuery.data.riskLevel)}</Typography.Paragraph>
            </Card>
          </Space>
        ) : (
          <Card>
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="Report ID">{detailQuery.data.reportId}</Descriptions.Item>
              <Descriptions.Item label="Result ID">{detailQuery.data.resultId}</Descriptions.Item>
              <Descriptions.Item label="Report Type">{detailQuery.data.reportType}</Descriptions.Item>
              <Descriptions.Item label="Risk Level">
                <Tag color={riskColor(detailQuery.data.riskLevel)}>{detailQuery.data.riskLevel}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Total Score" span={2}>
                {detailQuery.data.totalScore}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 24 }}>
              <Typography.Title level={5}>Report Content</Typography.Title>
              <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>
                {detailQuery.data.content}
              </Typography.Paragraph>
            </div>
          </Card>
        )
      ) : null}

      <ExportReportDialog
        open={exportOpen}
        title="Export Report"
        description="Choose an export format to download the report file."
        target={exportTarget}
        onClose={() => setExportOpen(false)}
      />
    </Space>
  );
}
