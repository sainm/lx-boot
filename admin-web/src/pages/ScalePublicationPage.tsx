import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import { Alert, App as AntdApp, Button, Card, Col, Descriptions, Form, Input, InputNumber, Modal, Row, Select, Space, Switch, Table, Tag, Typography } from "antd";
import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Permission } from "../components/Permission";
import {
  approveScaleGoldenCase,
  fetchScalePublicationHistory,
  fetchScalePublicationReadiness,
  runScaleGoldenCase,
  saveScaleGoldenCase,
  submitScalePublicationReview,
  type CreateScaleGoldenCaseRequest,
  type GoldenCaseRunResponse,
  type ScaleGoldenCaseHistory,
  type ScaleGoldenCaseRun,
  type ScalePublicationReview
} from "../features/scale-publication/api";
import { buildGoldenCaseRequest, buildHistoricRunEvidence, formatPublicationBlocker, type GoldenCaseDraft } from "../features/scale-publication/model";
import { publishScaleVersion } from "../features/scales/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

type ReviewForm = { decision: "APPROVED" | "REJECTED"; comment?: string };
export function ScalePublicationPage() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialScaleId = Number(searchParams.get("scaleId")) || undefined;
  const [scaleIdInput, setScaleIdInput] = useState<number | null>(initialScaleId ?? null);
  const [scaleId, setScaleId] = useState<number | undefined>(initialScaleId);
  const [reviewType, setReviewType] = useState<"PROFESSIONAL" | "BUSINESS" | null>(null);
  const [goldenCaseOpen, setGoldenCaseOpen] = useState(false);
  const [runEvidence, setRunEvidence] = useState<GoldenCaseRunResponse | null>(null);
  const [reviewToken, setReviewToken] = useState("");
  const [reviewForm] = Form.useForm<ReviewForm>();
  const [goldenCaseForm] = Form.useForm<GoldenCaseDraft>();
  const queryKey = useMemo(() => ["scale-publication-readiness", scaleId] as const, [scaleId]);
  const readinessQuery = useQuery({
    queryKey,
    queryFn: () => fetchScalePublicationReadiness(scaleId!),
    enabled: Boolean(scaleId)
  });
  const historyQuery = useQuery({
    queryKey: ["scale-publication-history", scaleId],
    queryFn: () => fetchScalePublicationHistory(scaleId!),
    enabled: Boolean(scaleId)
  });
  const refresh = async () => Promise.all([
    queryClient.invalidateQueries({ queryKey }),
    queryClient.invalidateQueries({ queryKey: ["scale-publication-history", scaleId] })
  ]);
  const runMutation = useMutation({
    mutationFn: (caseId: number) => runScaleGoldenCase(scaleId!, caseId),
    onSuccess: async (result) => {
      setRunEvidence(result);
      await refresh();
      void message[result.passed ? "success" : "error"](
        result.passed ? t("scalePublication.runPassed") : t("scalePublication.runFailed")
      );
    }
  });
  const saveCaseMutation = useMutation({
    mutationFn: (payload: CreateScaleGoldenCaseRequest) => saveScaleGoldenCase(scaleId!, payload),
    onSuccess: async () => {
      setGoldenCaseOpen(false);
      goldenCaseForm.resetFields();
      await refresh();
      void message.success(t("scalePublication.caseSaved"));
    }
  });
  const approveCaseMutation = useMutation({
    mutationFn: (caseId: number) => approveScaleGoldenCase(scaleId!, caseId),
    onSuccess: async () => {
      await refresh();
      void message.success(t("scalePublication.caseApproved"));
    }
  });
  const reviewMutation = useMutation({
    mutationFn: ({ type, values }: { type: "PROFESSIONAL" | "BUSINESS"; values: ReviewForm }) =>
      submitScalePublicationReview(scaleId!, type, { ...values, reviewToken }),
    onSuccess: async () => {
      setReviewType(null);
      reviewForm.resetFields();
      await refresh();
      void message.success(t("scalePublication.reviewSaved"));
    }
  });
  const publishMutation = useMutation({
    mutationFn: () => publishScaleVersion(scaleId!),
    onSuccess: async () => {
      await refresh();
      void message.success(t("scalePublication.published"));
    }
  });
  const readiness = readinessQuery.data;

  const loadScale = () => {
    if (!scaleIdInput || scaleIdInput <= 0) return;
    setScaleId(scaleIdInput);
    setSearchParams({ scaleId: String(scaleIdInput) });
  };
  const openReview = (type: "PROFESSIONAL" | "BUSINESS") => {
    setReviewType(type);
    setReviewToken(globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`);
    reviewForm.setFieldsValue({ decision: "APPROVED", comment: "" });
  };
  const submitReview = async () => {
    if (!reviewType) return;
    reviewMutation.mutate({ type: reviewType, values: await reviewForm.validateFields() });
  };
  const openGoldenCase = () => {
    goldenCaseForm.setFieldsValue({
      caseType: "NORMAL",
      answers: [{ questionNo: 1, optionCodes: "" }],
      valid: true,
      dimensionsJson: "{}",
      metricsJson: "{}"
    });
    setGoldenCaseOpen(true);
  };
  const submitGoldenCase = async () => {
    const values = await goldenCaseForm.validateFields();
    saveCaseMutation.mutate(buildGoldenCaseRequest(values));
  };
  const reviewTag = (review?: ScalePublicationReview | null) =>
    review ? <Tag color={review.decision === "APPROVED" ? "green" : "red"}>{review.decision}</Tag> : <Tag>{t("scalePublication.pending")}</Tag>;
  const viewHistoricRun = (run: ScaleGoldenCaseRun) => {
    try {
      setRunEvidence(buildHistoricRunEvidence(run));
    } catch {
      void message.error(t("scalePublication.invalidStoredEvidence"));
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4}>{t("scalePublication.title")}</Typography.Title>
        <Typography.Text type="secondary">{t("scalePublication.subtitle")}</Typography.Text>
      </div>
      <Alert type="warning" showIcon message={t("scalePublication.governanceNotice")} />
      <Card>
        <Space wrap align="end">
          <div>
            <Typography.Text>{t("scalePublication.scaleId")}</Typography.Text>
            <div><InputNumber min={1} value={scaleIdInput} onChange={setScaleIdInput} style={{ width: 180 }} /></div>
          </div>
          <Button type="primary" onClick={loadScale}>{t("scalePublication.load")}</Button>
          <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
            <Button disabled={!scaleIdInput} onClick={() => scaleIdInput && navigate(`/scale-governance?scaleId=${scaleIdInput}`)}>
              {t("scalePublication.editGovernance")}
            </Button>
          </Permission>
        </Space>
      </Card>

      {readinessQuery.isError ? <Alert type="error" showIcon message={t("scalePublication.loadFailed")} /> : null}
      {readiness ? (
        <>
          <Alert
            type={readiness.ready ? "success" : "error"}
            showIcon
            message={readiness.ready ? t("scalePublication.ready") : t("scalePublication.notReady")}
          />
          <Card title={t("scalePublication.evidence")}> 
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={t("scalePublication.contentHash")}><Typography.Text copyable code>{readiness.scaleContentHash}</Typography.Text></Descriptions.Item>
              <Descriptions.Item label={t("scalePublication.releaseFingerprint")}><Typography.Text copyable code>{readiness.releaseFingerprint}</Typography.Text></Descriptions.Item>
              <Descriptions.Item label={t("scalePublication.professionalReview")}>
                {reviewTag(readiness.professionalReview)} {readiness.professionalReview ? `${readiness.professionalReview.reviewerRoleSnapshot} #${readiness.professionalReview.reviewerId} · ${formatDateTime(readiness.professionalReview.createdAt)}` : ""}
              </Descriptions.Item>
              <Descriptions.Item label={t("scalePublication.businessReview")}>
                {reviewTag(readiness.businessReview)} {readiness.businessReview ? `${readiness.businessReview.reviewerRoleSnapshot} #${readiness.businessReview.reviewerId} · ${formatDateTime(readiness.businessReview.createdAt)}` : ""}
              </Descriptions.Item>
            </Descriptions>
            <Space wrap style={{ marginTop: 16 }}>
              <Permission roles={["COUNSELOR"]}><Button onClick={() => openReview("PROFESSIONAL")}>{t("scalePublication.professionalReview")}</Button></Permission>
              <Permission roles={["ASSESSMENT_ADMIN", "ORG_MANAGER"]}><Button onClick={() => openReview("BUSINESS")}>{t("scalePublication.businessReview")}</Button></Permission>
              <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                <Button type="primary" disabled={!readiness.ready} loading={publishMutation.isPending} onClick={() => publishMutation.mutate()}>
                  {t("scalePublication.publish")}
                </Button>
              </Permission>
            </Space>
          </Card>
          <Card title={`${t("scalePublication.blockers")} (${readiness.blockers.length})`}>
            {readiness.blockers.length ? (
              <Space wrap>{readiness.blockers.map((blocker) => <Tag color="red" key={blocker}>{formatPublicationBlocker(blocker, t)}</Tag>)}</Space>
            ) : <Typography.Text type="success">{t("scalePublication.noBlockers")}</Typography.Text>}
          </Card>
          <Card title={t("scalePublication.cases")}>
            <Permission roles={["ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"]}>
              <Button icon={<PlusOutlined />} onClick={openGoldenCase} style={{ marginBottom: 12 }}>
                {t("scalePublication.createCase")}
              </Button>
            </Permission>
            <Table
              rowKey="id"
              pagination={false}
              dataSource={readiness.cases}
              columns={[
                { title: t("scalePublication.caseCode"), dataIndex: "caseCode" },
                { title: t("scalePublication.caseType"), dataIndex: "caseType" },
                { title: t("scalePublication.revision"), dataIndex: "revisionNo", width: 90 },
                { title: t("scalePublication.currentContent"), dataIndex: "currentContent", width: 120, render: (value: boolean) => <Tag color={value ? "green" : "red"}>{value ? t("common.yes") : t("common.no")}</Tag> },
                { title: t("scalePublication.runStatus"), dataIndex: "latestRunPassed", width: 120, render: (value: boolean) => <Tag color={value ? "green" : "red"}>{value ? t("scalePublication.passed") : t("scalePublication.notPassed")}</Tag> },
                { title: t("scalePublication.approvalStatus"), dataIndex: "approved", width: 120, render: (value: boolean) => <Tag color={value ? "green" : "gold"}>{value ? t("scalePublication.approved") : t("scalePublication.pending")}</Tag> },
                {
                  title: t("scalePublication.actions"), width: 180,
                  render: (_, record) => <Space>
                    <Permission roles={["ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"]}>
                      <Button
                        type="link"
                        loading={runMutation.isPending && runMutation.variables === record.id}
                        onClick={() => runMutation.mutate(record.id)}
                      >
                        {t("scalePublication.run")}
                      </Button>
                    </Permission>
                    <Permission roles={["COUNSELOR"]}>
                      <Button type="link" disabled={!record.latestRunPassed || record.approved} loading={approveCaseMutation.isPending} onClick={() => approveCaseMutation.mutate(record.id)}>{t("scalePublication.approveCase")}</Button>
                    </Permission>
                  </Space>
                }
              ]}
            />
          </Card>
          <Card title={`${t("scalePublication.caseHistory")} (${historyQuery.data?.cases.length ?? 0})`}>
            <Alert type="info" showIcon message={t("scalePublication.historyNotice")} style={{ marginBottom: 12 }} />
            {historyQuery.data && (historyQuery.data.caseNextCursor || historyQuery.data.runNextCursor || historyQuery.data.reviewNextCursor) ? (
              <Alert type="warning" showIcon message={t("scalePublication.historyLimited")} style={{ marginBottom: 12 }} />
            ) : null}
            <Table<ScaleGoldenCaseHistory>
              rowKey={(record) => record.goldenCase.id}
              size="small"
              dataSource={historyQuery.data?.cases ?? []}
              pagination={{ pageSize: 8 }}
              expandable={{
                expandedRowRender: (record) => record.runs.length ? (
                  <Table<ScaleGoldenCaseRun>
                    rowKey="id"
                    size="small"
                    pagination={false}
                    dataSource={record.runs}
                    columns={[
                      { title: t("scalePublication.runId"), dataIndex: "id", width: 90 },
                      { title: t("scalePublication.algorithm"), render: (_, run) => `${run.algorithmCode ?? "-"} / ${run.algorithmVersion ?? "-"}` },
                      { title: t("scalePublication.runStatus"), dataIndex: "passed", width: 110, render: (value: boolean) => <Tag color={value ? "green" : "red"}>{value ? t("scalePublication.passed") : t("scalePublication.notPassed")}</Tag> },
                      { title: t("scalePublication.executedBy"), dataIndex: "executedBy", width: 110, render: (value: number) => `#${value}` },
                      { title: t("scalePublication.executedAt"), dataIndex: "executedAt", render: (value: string) => formatDateTime(value) },
                      { title: t("scalePublication.actions"), width: 110, render: (_, run) => <Button type="link" onClick={() => viewHistoricRun(run)}>{t("scalePublication.viewEvidence")}</Button> }
                    ]}
                  />
                ) : <Typography.Text type="secondary">{t("scalePublication.noRuns")}</Typography.Text>
              }}
              columns={[
                { title: t("scalePublication.caseCode"), render: (_, record) => record.goldenCase.caseCode },
                { title: t("scalePublication.revision"), render: (_, record) => record.goldenCase.revisionNo, width: 90 },
                { title: t("scalePublication.caseType"), render: (_, record) => record.goldenCase.caseType },
                { title: t("scalePublication.caseHash"), render: (_, record) => <Typography.Text copyable code ellipsis>{record.goldenCase.caseContentHash}</Typography.Text> },
                { title: t("scalePublication.createdBy"), render: (_, record) => `#${record.goldenCase.createdBy}`, width: 110 },
                { title: t("scalePublication.createdAt"), render: (_, record) => formatDateTime(record.goldenCase.createdAt) },
                { title: t("scalePublication.approvalStatus"), render: (_, record) => record.goldenCase.approvedBy ? <Tag color="green">{t("scalePublication.approved")}</Tag> : <Tag>{t("scalePublication.pending")}</Tag> }
              ]}
            />
          </Card>
          <Card title={`${t("scalePublication.approvalHistory")} (${historyQuery.data?.reviews.length ?? 0})`}>
            <Table<ScalePublicationReview>
              rowKey="id"
              size="small"
              dataSource={historyQuery.data?.reviews ?? []}
              pagination={{ pageSize: 8 }}
              columns={[
                { title: t("scalePublication.reviewType"), dataIndex: "reviewType" },
                { title: t("scalePublication.decision"), dataIndex: "decision", render: (value: string) => <Tag color={value === "APPROVED" ? "green" : "red"}>{value}</Tag> },
                { title: t("scalePublication.reviewer"), render: (_, review) => `${review.reviewerRoleSnapshot} #${review.reviewerId}` },
                { title: t("scalePublication.reviewFingerprint"), dataIndex: "releaseFingerprint", render: (value: string) => <Typography.Text copyable code ellipsis>{value}</Typography.Text> },
                { title: t("scalePublication.comment"), dataIndex: "commentText", render: (value?: string | null) => value || "-" },
                { title: t("scalePublication.createdAt"), dataIndex: "createdAt", render: (value: string) => formatDateTime(value) }
              ]}
            />
          </Card>
        </>
      ) : null}

      <Modal
        open={Boolean(reviewType)}
        title={reviewType === "PROFESSIONAL" ? t("scalePublication.professionalReview") : t("scalePublication.businessReview")}
        onCancel={() => setReviewType(null)}
        onOk={() => void submitReview()}
        confirmLoading={reviewMutation.isPending}
        forceRender
        destroyOnHidden
      >
        <Alert type="info" showIcon message={t("scalePublication.independentReviewNotice")} style={{ marginBottom: 16 }} />
        <Form form={reviewForm} layout="vertical">
          <Form.Item name="decision" label={t("scalePublication.decision")} rules={[{ required: true }]}>
            <Select options={[
              { value: "APPROVED", label: t("scalePublication.approve") },
              { value: "REJECTED", label: t("scalePublication.reject") }
            ]} />
          </Form.Item>
          <Form.Item name="comment" label={t("scalePublication.comment")}><Input.TextArea rows={4} /></Form.Item>
        </Form>
      </Modal>

      <Modal
        open={goldenCaseOpen}
        width={920}
        title={t("scalePublication.createCase")}
        onCancel={() => setGoldenCaseOpen(false)}
        onOk={() => void submitGoldenCase()}
        confirmLoading={saveCaseMutation.isPending}
        forceRender
        destroyOnHidden
      >
        <Alert type="warning" showIcon message={t("scalePublication.caseEvidenceNotice")} style={{ marginBottom: 16 }} />
        <Form form={goldenCaseForm} layout="vertical">
          <Row gutter={12}>
            <Col xs={24} md={8}><Form.Item name="caseCode" label={t("scalePublication.caseCode")} rules={[{ required: true }]}><Input /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="caseType" label={t("scalePublication.caseType")} rules={[{ required: true }]}><Select options={["NORMAL", "BOUNDARY", "REVERSE", "MISSING", "INVALID", "HIGH_RISK"].map((value) => ({ value, label: value }))} /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="durationSeconds" label={t("scalePublication.durationSeconds")}><InputNumber min={0} style={{ width: "100%" }} /></Form.Item></Col>
          </Row>
          <Form.Item name="sourceReference" label={t("scaleGovernance.sourceReference")} rules={[{ required: true }]}><Input.TextArea rows={2} /></Form.Item>
          <Form.List name="answers">{(fields, { add, remove }) => <Card size="small" title={t("scalePublication.caseAnswers")} extra={<Button icon={<PlusOutlined />} onClick={() => add({})}>{t("scalePublication.addAnswer")}</Button>}>
            <Space direction="vertical" style={{ width: "100%" }}>{fields.map((field) => <Card size="small" key={field.key} extra={<Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(field.name)} />}>
              <Row gutter={12}>
                <Col xs={24} md={6}><Form.Item name={[field.name, "questionNo"]} label={t("scalePublication.questionNo")} rules={[{ required: true }]}><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col>
                <Col xs={24} md={6}><Form.Item name={[field.name, "optionCodes"]} label={t("scalePublication.optionCodes")}><Input placeholder="A,B" /></Form.Item></Col>
                <Col xs={24} md={6}><Form.Item name={[field.name, "answerValue"]} label={t("scalePublication.answerValue")}><InputNumber style={{ width: "100%" }} /></Form.Item></Col>
                <Col xs={24} md={6}><Form.Item name={[field.name, "answerText"]} label={t("scalePublication.answerText")}><Input /></Form.Item></Col>
              </Row>
            </Card>)}</Space>
          </Card>}</Form.List>
          <Card size="small" title={t("scalePublication.normContext")} style={{ marginTop: 12 }}><Row gutter={12}>
            <Col xs={24} md={8}><Form.Item name="age" label={t("scalePublication.age")}><InputNumber min={0} style={{ width: "100%" }} /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="gender" label={t("scalePublication.gender")}><Input /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="orgType" label={t("scalePublication.orgType")}><Input /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item name="applicableTarget" label={t("scalePublication.applicableTarget")}><Input /></Form.Item></Col>
            <Col xs={24} md={12}><Form.Item name="preferredNormCode" label={t("scalePublication.preferredNormCode")}><Input /></Form.Item></Col>
          </Row></Card>
          <Card size="small" title={t("scalePublication.expectedResult")} style={{ marginTop: 12 }}><Row gutter={12}>
            <Col xs={24} md={6}><Form.Item name="valid" label={t("scalePublication.valid")} valuePropName="checked"><Switch /></Form.Item></Col>
            <Col xs={24} md={6}><Form.Item name="totalScore" label={t("scalePublication.totalScore")}><InputNumber style={{ width: "100%" }} /></Form.Item></Col>
            <Col xs={24} md={6}><Form.Item name="riskLevel" label={t("scalePublication.riskLevel")}><Input /></Form.Item></Col>
            <Col xs={24} md={6}><Form.Item name="normCode" label={t("scalePublication.normCode")}><Input /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="errorCode" label={t("scalePublication.errorCode")}><Input /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="highRiskTriggered" label={t("scalePublication.highRiskTriggered")} valuePropName="checked"><Switch /></Form.Item></Col>
            <Col xs={24} md={8}><Form.Item name="highRiskRuleCode" label={t("scalePublication.highRiskRuleCode")}><Input /></Form.Item></Col>
            <Col span={24}><Form.Item name="dimensionsJson" label={t("scalePublication.dimensionsJson")} rules={[{ validator: async (_, value) => { try { JSON.parse(value || "{}"); } catch { throw new Error(t("scaleGovernance.invalidJson")); } } }]}><Input.TextArea rows={5} /></Form.Item></Col>
            <Col span={24}><Form.Item name="metricsJson" label={t("scalePublication.metricsJson")} rules={[{ validator: async (_, value) => { try { const parsed = JSON.parse(value || "{}"); if (!parsed || Array.isArray(parsed) || typeof parsed !== "object" || Object.values(parsed).some((item) => typeof item !== "number" || !Number.isFinite(item))) throw new Error(); } catch { throw new Error(t("scaleGovernance.invalidJson")); } } }]}><Input.TextArea rows={4} placeholder='{"GSI":1.25,"PST":12,"PSDI":2.4}' /></Form.Item></Col>
          </Row></Card>
        </Form>
      </Modal>

      <Modal open={Boolean(runEvidence)} title={t("scalePublication.runEvidence")} footer={null} onCancel={() => setRunEvidence(null)} width={760}>
        {runEvidence ? <Space direction="vertical" style={{ width: "100%" }}>
          <Tag color={runEvidence.passed ? "green" : "red"}>{runEvidence.passed ? t("scalePublication.passed") : t("scalePublication.notPassed")}</Tag>
          <Typography.Title level={5}>{t("scalePublication.differences")}</Typography.Title>
          <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{JSON.stringify(runEvidence.differences, null, 2)}</pre>
          <Typography.Title level={5}>{t("scalePublication.actualResult")}</Typography.Title>
          <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{JSON.stringify(runEvidence.actual, null, 2)}</pre>
        </Space> : null}
      </Modal>
    </Space>
  );
}
