import { useQuery } from "@tanstack/react-query";
import { DownloadOutlined, PrinterOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Descriptions, Grid, InputNumber, Result, Row, Space, Statistic, Table, Tag, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useSession } from "../auth/session";
import { DimensionRadarChart, HorizontalBarChart, SegmentedRiskBar, scoreRiskColor } from "../components/ReportCharts";
import { ExportReportDialog } from "../components/ExportReportDialog";
import { Permission } from "../components/Permission";
import { fetchReportByResultId, fetchReportDetail, type ReportAnswerDetail } from "../features/reports/api";
import { useI18n } from "../i18n/provider";

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

function riskLabel(riskLevel: string, t: (key: string) => string) {
  switch (riskLevel) {
    case "HIGH":
      return t("reportDetail.risk.high");
    case "MEDIUM":
      return t("reportDetail.risk.medium");
    default:
      return t("reportDetail.risk.low");
  }
}

function riskSummary(riskLevel: string, t: (key: string) => string) {
  switch (riskLevel) {
    case "HIGH":
      return {
        type: "error" as const,
        title: t("reportDetail.summary.high.title"),
        description: t("reportDetail.summary.high.desc")
      };
    case "MEDIUM":
      return {
        type: "warning" as const,
        title: t("reportDetail.summary.medium.title"),
        description: t("reportDetail.summary.medium.desc")
      };
    default:
      return {
        type: "success" as const,
        title: t("reportDetail.summary.low.title"),
        description: t("reportDetail.summary.low.desc")
      };
  }
}

function nextStepHint(riskLevel: string, t: (key: string) => string) {
  switch (riskLevel) {
    case "HIGH":
      return t("reportDetail.next.high");
    case "MEDIUM":
      return t("reportDetail.next.medium");
    default:
      return t("reportDetail.next.low");
  }
}

function userNextHint(riskLevel: string, t: (key: string) => string) {
  switch (riskLevel) {
    case "HIGH":
      return t("reportDetail.userNext.high");
    case "MEDIUM":
      return t("reportDetail.userNext.medium");
    default:
      return t("reportDetail.userNext.low");
  }
}

function userCareSummary(riskLevel: string, t: (key: string) => string) {
  switch (riskLevel) {
    case "HIGH":
      return {
        type: "warning" as const,
        title: t("reportDetail.userSummary.high.title"),
        description: t("reportDetail.userSummary.high.desc")
      };
    case "MEDIUM":
      return {
        type: "info" as const,
        title: t("reportDetail.userSummary.medium.title"),
        description: t("reportDetail.userSummary.medium.desc")
      };
    default:
      return {
        type: "success" as const,
        title: t("reportDetail.userSummary.low.title"),
        description: t("reportDetail.userSummary.low.desc")
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

function normalizeReportContent(content: string) {
  return content.replace(/\\r\\n/g, "\n").replace(/\\n/g, "\n").replace(/\\t/g, "  ");
}

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
  const reportContent = detailQuery.data ? normalizeReportContent(detailQuery.data.content) : "";
  const reportChartData = useMemo(() => {
    const answers = detailQuery.data?.answerDetails ?? [];
    const dimensionMap = new Map<string, { total: number; count: number }>();
    const scoreMap = new Map<string, number>();
    answers.forEach((answer) => {
      if (answer.scoreValue != null) {
        const dimensionName = answer.dimensionName ?? answer.dimensionCode;
        if (dimensionName) {
          const current = dimensionMap.get(dimensionName) ?? { total: 0, count: 0 };
          dimensionMap.set(dimensionName, {
            total: current.total + answer.scoreValue,
            count: current.count + 1
          });
        }
        const scoreKey = String(answer.scoreValue);
        scoreMap.set(scoreKey, (scoreMap.get(scoreKey) ?? 0) + 1);
      }
    });
    const dimensionItems = Array.from(dimensionMap.entries())
      .map(([label, value]) => ({
        key: label,
        label,
        value: value.count === 0 ? 0 : value.total / value.count,
        meta: t("reportDetail.chart.answerCount", { count: value.count })
      }))
      .sort((left, right) => right.value - left.value);
    const scoreItems = Array.from(scoreMap.entries())
      .map(([score, count]) => ({
        key: score,
        label: t("reportDetail.chart.scoreBucket", { score }),
        value: count
      }))
      .sort((left, right) => Number(left.key) - Number(right.key));
    const riskItems = detailQuery.data
      ? [
          {
            key: detailQuery.data.riskLevel,
            label: riskLabel(detailQuery.data.riskLevel, t),
            value: 1,
            color: scoreRiskColor(detailQuery.data.riskLevel)
          },
          ...(detailQuery.data.highRiskFlag
            ? [
                {
                  key: "HIGH_RISK_ITEM",
                  label: t("reportDetail.chart.highRiskItem"),
                  value: 1,
                  color: "#991b1b"
                }
              ]
            : [])
        ]
      : [];
    return { dimensionItems, scoreItems, riskItems };
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
    return <Card title={t("reportDetail.answerDetails")} size={isMobile ? "small" : "default"}>{table}</Card>;
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
            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Card size={isMobile ? "small" : "default"}>
                  <Space direction="vertical" size={8} style={{ width: "100%" }}>
                    <Typography.Text type="secondary">{t("reportDetail.currentState")}</Typography.Text>
                    <Typography.Text strong style={{ fontSize: isMobile ? 18 : 16 }}>
                      {t(`reportDetail.currentState.${detailQuery.data.riskLevel}`)}
                    </Typography.Text>
                  </Space>
                </Card>
              </Col>
            </Row>
            <Card title={t("reportDetail.summary")} size={isMobile ? "small" : "default"}>
              <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0, fontSize: isMobile ? 15 : undefined, lineHeight: 1.75 }}>
                {reportContent}
              </Typography.Paragraph>
            </Card>
            <Card title={t("reportDetail.nextStep")} size={isMobile ? "small" : "default"}>
              <Typography.Paragraph style={{ marginBottom: 0, fontSize: isMobile ? 15 : undefined, lineHeight: 1.75 }}>
                {userNextHint(detailQuery.data.riskLevel, t)}
              </Typography.Paragraph>
            </Card>
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
          <Card className="report-print-area" size={isMobile ? "small" : "default"}>
            <Descriptions bordered column={isMobile ? 1 : 2} size="small">
              <Descriptions.Item label={t("reportDetail.reportId")}>{detailQuery.data.reportId}</Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.resultIdLabel")}>{detailQuery.data.resultId}</Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.reportType")}>{detailQuery.data.reportType}</Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.riskLevel")}>
                <Tag color={riskColor(detailQuery.data.riskLevel)}>{detailQuery.data.riskLevel}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.totalScore")} span={2}>
                {detailQuery.data.totalScore}
              </Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.scoreSource")}>{detailQuery.data.scoreSource ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.standardScoreLabel")}>
                {detailQuery.data.standardScore ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.zScore")}>{detailQuery.data.zScore ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.tScore")}>{detailQuery.data.tScore ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.normCode")}>{detailQuery.data.normCode ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.highRiskFlag")}>
                {detailQuery.data.highRiskFlag ? t("reportDetail.highRiskYes") : t("reportDetail.highRiskNo")}
              </Descriptions.Item>
              <Descriptions.Item label={t("reportDetail.highRiskRuleCode")}>
                {detailQuery.data.highRiskRuleCode ?? "-"}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 24 }}>
              <Row gutter={[16, 16]}>
                <Col xs={24} xl={8}>
                  <Card title={t("reportDetail.chart.risk")} size="small">
                    <SegmentedRiskBar items={reportChartData.riskItems} emptyText={t("reportDetail.chart.empty")} />
                  </Card>
                </Col>
                <Col xs={24} xl={8}>
                  <Card title={t("reportDetail.chart.dimension")} size="small">
                    <DimensionRadarChart items={reportChartData.dimensionItems} emptyText={t("reportDetail.chart.empty")} />
                  </Card>
                </Col>
                <Col xs={24} xl={8}>
                  <Card title={t("reportDetail.chart.answerScore")} size="small">
                    <HorizontalBarChart items={reportChartData.scoreItems} emptyText={t("reportDetail.chart.empty")} />
                  </Card>
                </Col>
              </Row>
            </div>
            <div style={{ marginTop: 24 }}>
              <Typography.Title level={5}>{t("reportDetail.content")}</Typography.Title>
              <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>{reportContent}</Typography.Paragraph>
            </div>
            <div style={{ marginTop: 24 }}>
              {answerDetailTable(detailQuery.data.answerDetails, false)}
            </div>
          </Card>
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
