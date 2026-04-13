import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Col, Descriptions, Grid, InputNumber, Result, Row, Space, Statistic, Table, Tag, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useSession } from "../auth/session";
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
    return riskSummary(detailQuery.data.riskLevel, t);
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
    const table = (
      <Table<ReportAnswerDetail>
        rowKey={(record, index) => `${record.questionId}-${record.optionCode ?? "value"}-${index ?? 0}`}
        size="small"
        pagination={answers.length > 8 ? { pageSize: 8 } : false}
        dataSource={answers}
        locale={{ emptyText: t("reportDetail.answerDetailsEmpty") }}
        scroll={{ x: 760 }}
        columns={[
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
            render: (_, answer) => renderAnswerValue(answer)
          },
          {
            title: t("reportDetail.questionContext"),
            key: "context",
            render: (_, answer) => renderQuestionContext(answer)
          },
          { title: t("reportDetail.scoreValue"), dataIndex: "scoreValue", width: 90, render: (value?: number | null) => value ?? "-" }
        ]}
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

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
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
          <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"]}>
            <Button block={isMobile} onClick={() => setExportOpen(true)} disabled={!exportTarget}>
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
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
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
            {systemSummary ? (
              <Alert
                type={systemSummary.type}
                showIcon
                message={systemSummary.title}
                description={
                  <Space direction="vertical" size={4}>
                    <Typography.Text>{systemSummary.description}</Typography.Text>
                    <Typography.Text type="secondary">{t("reportDetail.followupHint")}</Typography.Text>
                  </Space>
                }
              />
            ) : null}
            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Card size={isMobile ? "small" : "default"}>
                  <Statistic title={t("reportDetail.totalScore")} value={detailQuery.data.totalScore} valueStyle={{ fontSize: isMobile ? 28 : 36 }} />
                </Card>
              </Col>
              {detailQuery.data.standardScore !== null && detailQuery.data.standardScore !== undefined ? (
                <Col xs={24} md={8}>
                  <Card size={isMobile ? "small" : "default"}>
                    <Statistic
                      title={t("reportDetail.standardScore", { source: detailQuery.data.scoreSource ?? "RAW_SCORE" })}
                      value={detailQuery.data.standardScore}
                      valueStyle={{ fontSize: isMobile ? 24 : 32 }}
                    />
                  </Card>
                </Col>
              ) : null}
              <Col xs={24} md={8}>
                <Card size={isMobile ? "small" : "default"}>
                  <Space direction="vertical" size={8} style={{ width: "100%" }}>
                    <Typography.Text type="secondary">{t("reportDetail.riskLevel")}</Typography.Text>
                    <Tag color={riskColor(detailQuery.data.riskLevel)} style={{ width: "fit-content", fontSize: isMobile ? 16 : 14, padding: isMobile ? "4px 12px" : "2px 10px" }}>
                      {riskLabel(detailQuery.data.riskLevel, t)}
                    </Tag>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card size={isMobile ? "small" : "default"}>
                  <Statistic title={t("reportDetail.reportType")} value={detailQuery.data.reportType} valueStyle={{ fontSize: isMobile ? 22 : 28 }} />
                </Card>
              </Col>
            </Row>
            <Card title={t("reportDetail.snapshot")} size={isMobile ? "small" : "default"}>
              <Descriptions bordered column={isMobile ? 1 : 2} size="small">
                <Descriptions.Item label={t("reportDetail.reportId")}>{detailQuery.data.reportId}</Descriptions.Item>
                <Descriptions.Item label={t("reportDetail.resultIdLabel")}>{detailQuery.data.resultId}</Descriptions.Item>
                <Descriptions.Item label={t("reportDetail.reportType")}>{detailQuery.data.reportType}</Descriptions.Item>
                <Descriptions.Item label={t("reportDetail.riskLevel")}>{detailQuery.data.riskLevel}</Descriptions.Item>
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
            </Card>
            <Card title={t("reportDetail.summary")} size={isMobile ? "small" : "default"}>
              <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0, fontSize: isMobile ? 15 : undefined, lineHeight: 1.75 }}>
                {detailQuery.data.content}
              </Typography.Paragraph>
            </Card>
            {answerDetailTable(detailQuery.data.answerDetails)}
            <Card title={t("reportDetail.nextStep")} size={isMobile ? "small" : "default"}>
              <Typography.Paragraph style={{ marginBottom: 0, fontSize: isMobile ? 15 : undefined, lineHeight: 1.75 }}>
                {nextStepHint(detailQuery.data.riskLevel, t)}
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
          <Card size={isMobile ? "small" : "default"}>
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
              <Typography.Title level={5}>{t("reportDetail.content")}</Typography.Title>
              <Typography.Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>{detailQuery.data.content}</Typography.Paragraph>
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
