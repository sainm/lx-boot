import { useQuery } from "@tanstack/react-query";
import { DownloadOutlined, PrinterOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Descriptions, Grid, InputNumber, Result, Row, Space, Statistic, Table, Tag, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useSession } from "../auth/session";
import { ChartRenderer } from "../components/ReportCharts";
import { ExportReportDialog } from "../components/ExportReportDialog";
import { Permission } from "../components/Permission";
import { fetchReportByResultId, fetchReportDetail, type ReportAnswerDetail } from "../features/reports/api";
import { riskCategory, riskColor } from "../features/reports/risk";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

function riskLabel(riskLevel: string, t: (key: string) => string) {
  switch (riskCategory(riskLevel)) {
    case "critical":
      return t("reportDetail.risk.critical");
    case "high":
      return t("reportDetail.risk.high");
    case "moderate":
      return t("reportDetail.risk.medium");
    case "low":
      return t("reportDetail.risk.low");
    case "normal":
      return t("reportDetail.risk.normal");
    default:
      return t("reportDetail.risk.unknown");
  }
}

function riskSummary(riskLevel: string, t: (key: string) => string) {
  switch (riskCategory(riskLevel)) {
    case "critical":
    case "high":
      return {
        type: "error" as const,
        title: t("reportDetail.summary.high.title"),
        description: t("reportDetail.summary.high.desc")
      };
    case "moderate":
      return {
        type: "warning" as const,
        title: t("reportDetail.summary.medium.title"),
        description: t("reportDetail.summary.medium.desc")
      };
    case "low":
    case "normal":
      return {
        type: "success" as const,
        title: t("reportDetail.summary.low.title"),
        description: t("reportDetail.summary.low.desc")
      };
    default:
      return {
        type: "warning" as const,
        title: t("reportDetail.risk.unknown"),
        description: t("reportDetail.metricPendingReview")
      };
  }
}

function nextStepHint(riskLevel: string, t: (key: string) => string) {
  switch (riskCategory(riskLevel)) {
    case "critical":
    case "high":
      return t("reportDetail.next.high");
    case "moderate":
      return t("reportDetail.next.medium");
    case "low":
    case "normal":
      return t("reportDetail.next.low");
    default:
      return t("reportDetail.metricPendingReview");
  }
}

function userNextHint(riskLevel: string, t: (key: string) => string) {
  switch (riskCategory(riskLevel)) {
    case "critical":
    case "high":
      return t("reportDetail.userNext.high");
    case "moderate":
      return t("reportDetail.userNext.medium");
    case "low":
    case "normal":
      return t("reportDetail.userNext.low");
    default:
      return t("reportDetail.metricPendingReview");
  }
}

function userCareSummary(riskLevel: string, t: (key: string) => string) {
  switch (riskCategory(riskLevel)) {
    case "critical":
    case "high":
      return {
        type: "warning" as const,
        title: t("reportDetail.userSummary.high.title"),
        description: t("reportDetail.userSummary.high.desc")
      };
    case "moderate":
      return {
        type: "info" as const,
        title: t("reportDetail.userSummary.medium.title"),
        description: t("reportDetail.userSummary.medium.desc")
      };
    case "low":
    case "normal":
      return {
        type: "success" as const,
        title: t("reportDetail.userSummary.low.title"),
        description: t("reportDetail.userSummary.low.desc")
      };
    default:
      return {
        type: "warning" as const,
        title: t("reportDetail.risk.unknown"),
        description: t("reportDetail.metricPendingReview")
      };
  }
}

function questionTypeLabel(questionType: string, t: (key: string) => string) {
  switch (questionType) {
    case "SINGLE_CHOICE":
      return t("reportDetail.questionType.singleChoice");
    case "MULTI_SELECT":
      return t("reportDetail.questionType.multiSelect");
    case "SLIDER":
      return t("reportDetail.questionType.slider");
    case "MATRIX":
      return t("reportDetail.questionType.matrix");
    case "TEXT_WITH_OPTION":
      return t("reportDetail.questionType.textWithOption");
    case "TEXT":
      return t("reportDetail.questionType.text");
    default:
      return questionType;
  }
}

function metricLabel(code: string, t: (key: string) => string) {
  switch (code) {
    case "TOTAL_SCORE":
      return t("reportDetail.totalScore");
    case "STANDARD_SCORE":
      return t("reportDetail.standardScoreLabel");
    case "Z_SCORE":
      return t("reportDetail.zScore");
    case "T_SCORE":
      return t("reportDetail.tScore");
    default:
      return code;
  }
}

const REPORT_CONTENT_WIDTH = 920;

export function ReportDetailPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentRole } = useSession();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const isUserView = currentRole === "USER";
  const { reportId } = useParams();
  const [searchParams] = useSearchParams();
  const resultId = searchParams.get("resultId");
  const taskId = searchParams.get("taskId");
  const notificationSource = searchParams.get("notificationSource");
  const [inputId, setInputId] = useState<number | null>(reportId ? Number(reportId) : null);
  const [exportOpen, setExportOpen] = useState(false);
  const [printing, setPrinting] = useState(false);

  useEffect(() => {
    if (reportId) {
      setInputId(Number(reportId));
    }
  }, [reportId]);

  useEffect(() => {
    const beforePrint = () => setPrinting(true);
    const afterPrint = () => setPrinting(false);
    window.addEventListener("beforeprint", beforePrint);
    window.addEventListener("afterprint", afterPrint);
    return () => {
      window.removeEventListener("beforeprint", beforePrint);
      window.removeEventListener("afterprint", afterPrint);
    };
  }, []);

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
    return riskSummary(detailQuery.data.riskLevel, t);
  }, [detailQuery.data, t]);
  const userSummary = useMemo(() => {
    if (!detailQuery.data) {
      return null;
    }
    return userCareSummary(detailQuery.data.riskLevel, t);
  }, [detailQuery.data, t]);
  const metricRows = useMemo(() => {
    const report = detailQuery.data;
    if (!report) return [];
    return (report.metrics ?? []).map((metric) => ({
      key: metric.code,
      label: metricLabel(metric.code, t),
      value: metric.displayValue,
      reference: metric.referenceText || t("reportDetail.referenceNotConfigured"),
      interpretation: metric.reviewStatus === "PENDING_PROFESSIONAL_REVIEW"
        ? t("reportDetail.metricPendingReview")
        : metric.interpretationCode
    }));
  }, [detailQuery.data, t]);
  const dimensionRows = useMemo(() => {
    return (detailQuery.data?.dimensionResults ?? []).map((dimension) => ({
      dimensionCode: dimension.dimensionCode,
      dimensionName: dimension.dimensionName,
      score: dimension.score,
      reference: dimension.referenceText || t("reportDetail.referenceNotConfigured"),
      description: dimension.resultTitle || (dimension.riskLevel
        ? riskLabel(dimension.riskLevel, t)
        : t("reportDetail.metricPendingReview"))
    }));
  }, [detailQuery.data, t]);

  const renderAnswerValue = (answer: ReportAnswerDetail) => {
    if (answer.questionType === "SLIDER") {
      return (
        <Space direction="vertical" size={2}>
          <Typography.Text>{answer.answerValue ?? "-"}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t("reportDetail.sliderRange", {
              min: answer.sliderMin ?? "-",
              max: answer.sliderMax ?? "-",
              step: answer.sliderStep ?? "-"
            })}
          </Typography.Text>
        </Space>
      );
    }
    if (answer.optionLabel || answer.optionCode) {
      return (
        <Space direction="vertical" size={2}>
          <Typography.Text>{answer.optionLabel ?? answer.optionCode}</Typography.Text>
          {answer.optionCode ? <Typography.Text type="secondary" style={{ fontSize: 12 }}>{answer.optionCode}</Typography.Text> : null}
          {answer.answerText ? <Typography.Text type="secondary" style={{ fontSize: 12 }}>{answer.answerText}</Typography.Text> : null}
        </Space>
      );
    }
    return answer.answerText ?? answer.answerValue ?? "-";
  };

  const renderQuestionContext = (answer: ReportAnswerDetail) => {
    const items = [
      answer.dimensionName ?? answer.dimensionCode,
      answer.questionType === "MATRIX"
        ? `${t("reportDetail.matrixContext", {
            group: answer.matrixGroupCode ?? "-",
            row: answer.rowCode ?? "-",
            column: answer.columnCode ?? "-"
          })}`
        : undefined
    ].filter((item): item is string => Boolean(item));

    if (items.length === 0) return "-";
    return (
      <Space wrap size={[4, 4]}>
        {items.map((item) => <Tag key={item}>{item}</Tag>)}
      </Space>
    );
  };

  const answerDetailTable = (answers: ReportAnswerDetail[] = [], framed = true) => {
    const columns = isUserView
      ? [
          { title: t("reportDetail.questionNo"), dataIndex: "questionNo", width: 80 },
          { title: t("reportDetail.questionTitle"), dataIndex: "questionTitle", width: 280 },
          {
            title: t("reportDetail.answer"),
            key: "answer",
            render: (_: unknown, answer: ReportAnswerDetail) => renderAnswerValue(answer)
          }
        ]
      : [
          { title: t("reportDetail.questionNo"), dataIndex: "questionNo", width: 80 },
          { title: t("reportDetail.questionTitle"), dataIndex: "questionTitle", width: 240 },
          {
            title: t("reportDetail.questionType"),
            dataIndex: "questionType",
            width: 130,
            render: (value: string) => questionTypeLabel(value, t)
          },
          {
            title: t("reportDetail.answer"),
            key: "answer",
            width: 220,
            render: (_: unknown, answer: ReportAnswerDetail) => renderAnswerValue(answer)
          },
          {
            title: t("reportDetail.questionContext"),
            key: "context",
            render: (_: unknown, answer: ReportAnswerDetail) => renderQuestionContext(answer)
          },
          { title: t("reportDetail.scoreValue"), dataIndex: "scoreValue", width: 90, render: (value?: number | null) => value ?? "-" }
        ];
    const table = (
      <Table<ReportAnswerDetail>
        rowKey={(record, index) => `${record.questionId}-${record.optionCode ?? "value"}-${index ?? 0}`}
        size="small"
        pagination={!printing && answers.length > 8 ? { pageSize: 8 } : false}
        dataSource={answers}
        locale={{ emptyText: t("reportDetail.answerDetailsEmpty") }}
        scroll={{ x: 760 }}
        columns={columns}
      />
    );

    if (!framed) {
      return (
        <Space direction="vertical" size={12} style={{ width: "100%" }}>
          <Typography.Title level={5} style={{ marginBottom: 0 }}>{t("reportDetail.answerDetails")}</Typography.Title>
          {table}
        </Space>
      );
    }
    return (
      <Card title={t("reportDetail.answerDetails")} size={isMobile ? "small" : "default"} style={{ width: "100%", maxWidth: REPORT_CONTENT_WIDTH, margin: "0 auto" }}>
        {table}
      </Card>
    );
  };

  const loadReport = () => {
    if (!inputId || Number.isNaN(inputId) || inputId <= 0) {
      message.warning(t("reportDetail.invalidReportId"));
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

  const printReport = () => {
    setPrinting(true);
    window.setTimeout(() => window.print(), 0);
  };

  const personalReportTitle = (scaleName?: string | null) =>
    t("reportDetail.dynamicPersonalReportTitle", {
      scaleName: scaleName?.trim() || t("reportDetail.defaultScaleName")
    });

  const reportTableStyle = { width: "100%" };
  const reportSectionTitleStyle = { marginBottom: 12, marginTop: 8 };
  const reportBodyTextStyle = { whiteSpace: "pre-wrap" as const, marginBottom: 0, fontSize: isMobile ? 15 : 16, lineHeight: 1.85 };

  const renderPersonalReport = (showStaffDetails = false) => {
    const report = detailQuery.data;
    if (!report) return null;
    const resultText = report.content?.trim() || t("reportDetail.generatedResultText", {
      risk: riskLabel(report.riskLevel, t),
      totalScore: report.totalScore
    });
    const suggestionText = isUserView ? userNextHint(report.riskLevel, t) : nextStepHint(report.riskLevel, t);

    return (
      <div
        className="report-print-area"
        style={{
          width: "100%",
          maxWidth: REPORT_CONTENT_WIDTH,
          margin: "0 auto",
          background: "#fff",
          padding: isMobile ? 20 : 40,
          border: "1px solid #e5e7eb",
          boxShadow: printing ? "none" : "0 8px 24px rgba(15, 23, 42, 0.06)"
        }}
      >
        <Typography.Title level={3} style={{ textAlign: "center", marginTop: 0, marginBottom: 28 }}>
          {personalReportTitle(report.scaleName)}
        </Typography.Title>

        <Typography.Title level={4} style={reportSectionTitleStyle}>{t("reportDetail.section.basic")}</Typography.Title>
        <Space direction="vertical" size={8} style={{ width: "100%", marginBottom: 20 }}>
          <Typography.Text>{t("reportDetail.basicNameLine", { name: report.displayName ?? report.username ?? "-" })}</Typography.Text>
          <Typography.Text>{t("reportDetail.basicDateLine", { date: report.createdAt ? formatDateTime(report.createdAt).slice(0, 10) : "-" })}</Typography.Text>
          <Typography.Text>{t("reportDetail.basicPurposeLine", { purpose: t("reportDetail.purposeText") })}</Typography.Text>
          {showStaffDetails ? (
            <Typography.Text type="secondary">
              {t("reportDetail.staffMetaLine", { reportId: report.reportId, resultId: report.resultId })}
            </Typography.Text>
          ) : null}
          {showStaffDetails && report.localeCode ? (
            <Typography.Text type="secondary">
              {t("reportDetail.localeLine", { locale: report.localeCode })}
            </Typography.Text>
          ) : null}
        </Space>

        <Typography.Title level={4} style={reportSectionTitleStyle}>{t("reportDetail.section.overall")}</Typography.Title>
        <Table
          size="small"
          rowKey="key"
          pagination={false}
          dataSource={metricRows}
          style={reportTableStyle}
          columns={[
            { title: t("reportDetail.metric"), dataIndex: "label" },
            { title: t("reportDetail.value"), dataIndex: "value" },
            { title: t("reportDetail.referenceRange"), dataIndex: "reference" },
            { title: t("reportDetail.interpretation"), dataIndex: "interpretation" }
          ]}
        />

        <Typography.Title level={4} style={reportSectionTitleStyle}>{t("reportDetail.section.dimensions")}</Typography.Title>
        <Table
          size="small"
          rowKey="dimensionCode"
          pagination={false}
          dataSource={dimensionRows}
          style={reportTableStyle}
          columns={[
            { title: t("reportDetail.dimensionFactor"), dataIndex: "dimensionName" },
            { title: t("reportDetail.value"), dataIndex: "score" },
            { title: t("reportDetail.referenceRange"), dataIndex: "reference" },
            { title: t("reportDetail.description"), dataIndex: "description" }
          ]}
        />

        <Typography.Title level={4} style={reportSectionTitleStyle}>{t("reportDetail.section.content")}</Typography.Title>
        <Space direction="vertical" size={12} style={{ width: "100%", marginBottom: 20 }}>
          <Typography.Text strong>{t("reportDetail.resultDescriptionLabel")}</Typography.Text>
          <Typography.Paragraph style={reportBodyTextStyle}>{resultText}</Typography.Paragraph>
          <Typography.Text strong>{t("reportDetail.psychologicalSuggestionLabel")}</Typography.Text>
          <Typography.Paragraph style={reportBodyTextStyle}>{suggestionText}</Typography.Paragraph>
        </Space>

        <Typography.Title level={4} style={reportSectionTitleStyle}>{t("reportDetail.section.notice")}</Typography.Title>
        <Typography.Paragraph style={reportBodyTextStyle}>{t("reportDetail.noticeText")}</Typography.Paragraph>
      </div>
    );
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div className="no-print" style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
        <div style={{ minWidth: 0, flex: 1 }}>
          <Typography.Title level={4} style={{ marginBottom: 8 }}>
            {isUserView ? t("reportDetail.userTitle") : t("reportDetail.staffTitle")}
          </Typography.Title>
          <Typography.Text type="secondary">
            {isUserView ? t("reportDetail.userSubtitle") : t("reportDetail.staffSubtitle")}
          </Typography.Text>
        </div>
        <Space direction={isMobile ? "vertical" : "horizontal"} wrap style={{ width: isMobile ? "100%" : undefined }}>
          <Button block={isMobile} onClick={goBack}>
            {isUserView ? t("reportDetail.backMyReports") : t("reportDetail.back")}
          </Button>
          {resultId ? <Typography.Text type="secondary">{t("reportDetail.resultId", { resultId })}</Typography.Text> : null}
          {!isUserView ? (
            <>
              <InputNumber
                min={1}
                placeholder={t("reportDetail.reportIdPlaceholder")}
                value={inputId ?? undefined}
                onChange={(value) => setInputId(value ?? null)}
                style={{ width: isMobile ? "100%" : 160 }}
              />
              <Button block={isMobile} type="primary" onClick={loadReport}>
                {t("reportDetail.loadReport")}
              </Button>
            </>
          ) : null}
          <Button block={isMobile} icon={<PrinterOutlined />} onClick={printReport} disabled={!detailQuery.data}>
            {t("reportDetail.printReport")}
          </Button>
          <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"]}>
            <Button block={isMobile} icon={<DownloadOutlined />} onClick={() => setExportOpen(true)} disabled={!exportTarget}>
              {t("reportDetail.exportReport")}
            </Button>
          </Permission>
        </Space>
      </div>

      {!reportId && !resultId ? (
        <Result status="info" title={t("reportDetail.enterReportId")} subTitle={t("reportDetail.enterReportIdDesc")} />
      ) : detailQuery.isError ? (
        <Result status="warning" title={t("reportDetail.loadError")} subTitle={t("reportDetail.loadErrorDesc")} />
      ) : detailQuery.isLoading ? (
        <Result status="info" title={t("reportDetail.loading")} />
      ) : detailQuery.data ? (
        isUserView ? (
          <Space className="report-print-area" direction="vertical" size={16} style={{ width: "100%" }}>
            {notificationSource === "REPORT_GENERATED" ? (
              <Alert type="success" showIcon message={t("reportDetail.notificationOpened")} description={t("reportDetail.notificationDesc")} />
            ) : null}
            {notificationSource === "REPORT_AUTO_SUBMITTED" ? (
              <Alert
                type="warning"
                showIcon
                message={t("reportDetail.autoSubmittedOpened")}
                description={t("reportDetail.autoSubmittedDesc")}
              />
            ) : null}
            {userSummary ? (
              <Alert
                type={userSummary.type}
                showIcon
                message={userSummary.title}
                description={
                  <Space direction="vertical" size={4}>
                    <Typography.Text>{userSummary.description}</Typography.Text>
                    <Typography.Text type="secondary">{t("reportDetail.userFollowupHint")}</Typography.Text>
                  </Space>
                }
              />
            ) : null}
            {renderPersonalReport(false)}
            <div
              style={{
                position: isMobile ? "sticky" : "static",
                bottom: isMobile ? 72 : undefined,
                zIndex: isMobile ? 5 : undefined,
                background: isMobile ? "rgba(246, 248, 251, 0.96)" : undefined,
                paddingTop: isMobile ? 4 : 0
              }}
            >
              <Button block={isMobile} size={isMobile ? "large" : "middle"} onClick={goBack}>
                {t("reportDetail.backMyReports")}
              </Button>
            </div>
          </Space>
        ) : (
          <Space className="report-print-area" direction="vertical" size={16} style={{ width: "100%" }}>
            {renderPersonalReport(true)}
            <div className="no-print" style={{ width: "100%", maxWidth: REPORT_CONTENT_WIDTH, margin: "0 auto" }}>
              <ChartRenderer visualizations={detailQuery.data.visualizations} emptyText={t("reportDetail.chart.empty")} chartHeight={isMobile ? 260 : 300} />
            </div>
            {answerDetailTable(detailQuery.data.answerDetails, true)}
          </Space>
        )
      ) : null}

      <ExportReportDialog
        open={exportOpen}
        title={t("reportDetail.exportTitle")}
        description={t("reportDetail.exportDesc")}
        target={exportTarget}
        onClose={() => setExportOpen(false)}
      />
    </Space>
  );
}
