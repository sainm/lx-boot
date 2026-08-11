import { DeleteOutlined, DownloadOutlined, PlusOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  Typography,
  message
} from "antd";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  fetchScalePackage,
  downloadScalePackageExport,
  replaceScalePackage,
  saveScalePackageExport,
  type UpdateScalePackageRequest
} from "../features/scale-package/api";
import { buildScalePackageDraft, isValidJson, REVIEW_STATUSES, sanitizeScalePackageDraft } from "../features/scale-package/model";
import { fetchScaleDetail, type ScaleDetail } from "../features/scales/api";
import { useI18n } from "../i18n/provider";

type TranslationListName =
  | "dimensionTranslations"
  | "questionTranslations"
  | "optionTranslations"
  | "resultRuleTranslations"
  | "highRiskRuleTranslations";

type ExtraField = { key: string; labelKey: string; rows?: number };

const reviewOptions = REVIEW_STATUSES.map((value) => ({ value, label: value }));
const copyrightOptions = ["PENDING_REVIEW", "AUTHORIZED", "PUBLIC_DOMAIN", "RESTRICTED", "EXPIRED", "REJECTED"].map((value) => ({ value, label: value }));
const authorizationOptions = ["PENDING_REVIEW", "AUTHORIZED", "NOT_REQUIRED", "RESTRICTED", "EXPIRED", "REJECTED"].map((value) => ({ value, label: value }));

export function ScaleGovernancePage() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialScaleId = Number(searchParams.get("scaleId")) || undefined;
  const [scaleIdInput, setScaleIdInput] = useState<number | null>(initialScaleId ?? null);
  const [scaleId, setScaleId] = useState<number | undefined>(initialScaleId);
  const [form] = Form.useForm<UpdateScalePackageRequest>();
  const [messageApi, messageContextHolder] = message.useMessage();

  const packageQuery = useQuery({
    queryKey: ["scale-governance", scaleId],
    queryFn: async () => {
      const [scale, snapshot] = await Promise.all([fetchScaleDetail(scaleId!), fetchScalePackage(scaleId!)]);
      return { scale, snapshot };
    },
    enabled: Boolean(scaleId)
  });

  useEffect(() => {
    if (packageQuery.data) {
      form.setFieldsValue(buildScalePackageDraft(packageQuery.data.snapshot, packageQuery.data.scale));
    }
  }, [form, packageQuery.data]);

  const saveMutation = useMutation({
    mutationFn: (payload: UpdateScalePackageRequest) => replaceScalePackage(scaleId!, sanitizeScalePackageDraft(payload)),
    onSuccess: async (snapshot) => {
      if (packageQuery.data) {
        form.setFieldsValue(buildScalePackageDraft(snapshot, packageQuery.data.scale));
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["scale-governance", scaleId] }),
        queryClient.invalidateQueries({ queryKey: ["scale-publication-readiness", scaleId] })
      ]);
      void messageApi.success(t("scaleGovernance.saved"));
    }
  });

  const exportMutation = useMutation({
    mutationFn: () => downloadScalePackageExport(scaleId!),
    onSuccess: (artifact) => {
      saveScalePackageExport(artifact);
      void messageApi.success(t("scaleGovernance.exported"));
    },
    onError: () => {
      void messageApi.error(t("scaleGovernance.exportFailed"));
    }
  });

  const scale = packageQuery.data?.scale;
  const loadScale = () => {
    if (!scaleIdInput || scaleIdInput <= 0) return;
    form.resetFields();
    setScaleId(scaleIdInput);
    setSearchParams({ scaleId: String(scaleIdInput) });
  };
  const submit = async () => saveMutation.mutate(await form.validateFields());

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {messageContextHolder}
      <div>
        <Typography.Title level={4}>{t("scaleGovernance.title")}</Typography.Title>
        <Typography.Text type="secondary">{t("scaleGovernance.subtitle")}</Typography.Text>
      </div>
      <Alert type="warning" showIcon message={t("scaleGovernance.evidenceNotice")} />
      <Card>
        <Space wrap align="end">
          <div>
            <Typography.Text>{t("scalePublication.scaleId")}</Typography.Text>
            <div><InputNumber min={1} value={scaleIdInput} onChange={setScaleIdInput} style={{ width: 180 }} /></div>
          </div>
          <Button type="primary" onClick={loadScale}>{t("scaleGovernance.load")}</Button>
          {scale ? (
            <Button icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => exportMutation.mutate()}>
              {t("scaleGovernance.export")}
            </Button>
          ) : null}
          {scale ? <Tag color={scale.status === "DRAFT" ? "blue" : "gold"}>{scale.scaleCode} · {scale.versionNo} · {scale.status}</Tag> : null}
        </Space>
      </Card>
      {packageQuery.isError ? <Alert type="error" showIcon message={t("scaleGovernance.loadFailed")} /> : null}
      {scale && scale.status !== "DRAFT" ? <Alert type="error" showIcon message={t("scaleGovernance.draftOnly")} /> : null}
      {scale ? <Alert type="info" showIcon message={t("scaleGovernance.exportNotice")} /> : null}
      {scale ? (
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Tabs
            destroyOnHidden={false}
            items={[
              { key: "governance", label: t("scaleGovernance.tabGovernance"), children: <GovernanceFields /> },
              { key: "scale-translations", label: t("scaleGovernance.tabScaleTranslations"), children: <ScaleTranslationFields /> },
              { key: "content-translations", label: t("scaleGovernance.tabContentTranslations"), children: <ContentTranslationFields form={form} scale={scale} /> },
              { key: "quality", label: t("scaleGovernance.tabQuality"), children: <QualityFields /> },
              { key: "algorithm", label: t("scaleGovernance.tabAlgorithm"), children: <AlgorithmFields /> },
              { key: "norms", label: t("scaleGovernance.tabNorms"), children: <NormGovernanceFields form={form} scale={scale} /> }
            ]}
          />
          <Space style={{ marginTop: 16 }}>
            <Button type="primary" onClick={() => void submit()} loading={saveMutation.isPending} disabled={scale.status !== "DRAFT"}>
              {t("scaleGovernance.save")}
            </Button>
            <Typography.Text type="secondary">{t("scaleGovernance.replaceNotice")}</Typography.Text>
          </Space>
        </Form>
      ) : null}
    </Space>
  );
}

function GovernanceFields() {
  const { t } = useI18n();
  return (
    <Card size="small">
      <Row gutter={16}>
        <TextField name="sourceTitle" labelKey="scaleGovernance.sourceTitle" />
        <TextField name="publisherName" labelKey="scaleGovernance.publisherName" />
        <TextField name="manualVersion" labelKey="scaleGovernance.manualVersion" />
        <TextField name="sourceUrl" labelKey="scaleGovernance.sourceUrl" />
        <TextField name="citationText" labelKey="scaleGovernance.citationText" span={24} multiline />
        <SelectField name="copyrightStatus" labelKey="scaleGovernance.copyrightStatus" options={copyrightOptions} required />
        <TextField name="rightsHolder" labelKey="scaleGovernance.rightsHolder" />
        <SelectField name="authorizationStatus" labelKey="scaleGovernance.authorizationStatus" options={authorizationOptions} required />
        <TextField name="authorizationType" labelKey="scaleGovernance.authorizationType" />
        <TextField name="authorizationScope" labelKey="scaleGovernance.authorizationScope" span={24} multiline />
        <TextField name="authorizedTerritories" labelKey="scaleGovernance.authorizedTerritories" />
        <TextField name="authorizedLanguages" labelKey="scaleGovernance.authorizedLanguages" />
        <TextField name="authorizationValidFrom" labelKey="scaleGovernance.validFrom" placeholder="YYYY-MM-DD" />
        <TextField name="authorizationValidTo" labelKey="scaleGovernance.validTo" placeholder="YYYY-MM-DD" />
        <TextField name="targetPopulation" labelKey="scaleGovernance.targetPopulation" />
        <TextField name="exclusionCriteria" labelKey="scaleGovernance.exclusionCriteria" />
        <Col xs={24} md={12}><Form.Item name={["governance", "estimatedMinutes"]} label={t("scaleGovernance.estimatedMinutes")}><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col>
        <TextField name="resultVisibility" labelKey="scaleGovernance.resultVisibility" />
        <TextField name="dataUsageStatement" labelKey="scaleGovernance.dataUsage" span={24} multiline />
        <TextField name="nonDiagnosticStatement" labelKey="scaleGovernance.nonDiagnostic" span={24} multiline />
        <TextField name="helpResourceText" labelKey="scaleGovernance.helpResource" span={24} multiline />
        <Col xs={24} md={12}><Form.Item name={["governance", "governanceStatus"]} label={t("scaleGovernance.reviewStatus")} rules={[{ required: true }]}><Select options={reviewOptions} /></Form.Item></Col>
      </Row>
    </Card>
  );
}

function TextField({ name, labelKey, span = 12, multiline = false, required = false, placeholder }: { name: string; labelKey: string; span?: number; multiline?: boolean; required?: boolean; placeholder?: string }) {
  const { t } = useI18n();
  return <Col xs={24} md={span}><Form.Item name={["governance", name]} label={t(labelKey)} rules={required ? [{ required: true }] : undefined}>{multiline ? <Input.TextArea rows={3} /> : <Input placeholder={placeholder} />}</Form.Item></Col>;
}

function SelectField({ name, labelKey, options, required = false }: { name: string; labelKey: string; options: Array<{ value: string; label: string }>; required?: boolean }) {
  const { t } = useI18n();
  return <Col xs={24} md={12}><Form.Item name={["governance", name]} label={t(labelKey)} rules={required ? [{ required: true }] : undefined}><Select options={options} /></Form.Item></Col>;
}

function ScaleTranslationFields() {
  const { t } = useI18n();
  const textAreas: Array<[string, string]> = [
    ["description", "scaleGovernance.description"], ["instructionText", "scaleGovernance.instructions"],
    ["purposeText", "scaleGovernance.purpose"], ["dataUsageText", "scaleGovernance.dataUsage"],
    ["resultVisibilityText", "scaleGovernance.resultVisibility"], ["nonDiagnosticText", "scaleGovernance.nonDiagnostic"],
    ["highRiskActionText", "scaleGovernance.highRiskAction"], ["helpResourceText", "scaleGovernance.helpResource"]
  ];
  return (
    <Form.List name="translations">
      {(fields) => <Space direction="vertical" size={12} style={{ width: "100%" }}>{fields.map((field) => (
        <Card key={field.key} size="small" title={<Form.Item noStyle shouldUpdate>{({ getFieldValue }) => getFieldValue(["translations", field.name, "localeCode"])}</Form.Item>}>
          <Form.Item name={[field.name, "localeCode"]} hidden><Input /></Form.Item>
          <Form.Item name={[field.name, "scaleName"]} label={t("scaleGovernance.scaleName")}><Input /></Form.Item>
          {textAreas.map(([key, labelKey]) => <Form.Item key={key} name={[field.name, key]} label={t(labelKey)}><Input.TextArea rows={2} /></Form.Item>)}
          <Form.Item name={[field.name, "reviewStatus"]} label={t("scaleGovernance.reviewStatus")} rules={[{ required: true }]}><Select options={reviewOptions} /></Form.Item>
        </Card>
      ))}</Space>}
    </Form.List>
  );
}

function ContentTranslationFields({ form, scale }: { form: ReturnType<typeof Form.useForm<UpdateScalePackageRequest>>[0]; scale: ScaleDetail }) {
  const { t } = useI18n();
  const dimensionLabels = useMemo(() => new Map(scale.dimensions.map((item) => [item.id, `${item.dimensionCode} · ${item.dimensionName}`])), [scale.dimensions]);
  const questionLabels = useMemo(() => new Map(scale.questions.map((item) => [item.id, `#${item.questionNo} · ${item.questionTitle}`])), [scale.questions]);
  const optionLabels = useMemo(() => new Map(scale.questions.flatMap((question) => question.options.map((item) => [item.id, `#${question.questionNo}/${item.optionCode} · ${item.optionLabel}`] as const))), [scale.questions]);
  const ruleLabels = useMemo(() => new Map(scale.resultRules.map((item) => [item.id, `#${item.id} · ${item.riskLevel} ${item.scoreMin}-${item.scoreMax}`])), [scale.resultRules]);
  const highRiskRuleLabels = useMemo(() => new Map((scale.highRiskRules ?? []).map((item) => [item.id, `${item.ruleCode} · #${item.questionNo} · ${item.warningLevel}`])), [scale.highRiskRules]);
  return <Collapse items={[
    { key: "dimensions", label: t("scaleGovernance.dimensions"), children: <TranslationRows form={form} name="dimensionTranslations" idField="dimensionId" titleField="dimensionName" labels={dimensionLabels} extras={[{ key: "description", labelKey: "scaleGovernance.description", rows: 2 }]} /> },
    { key: "questions", label: t("scaleGovernance.questions"), children: <TranslationRows form={form} name="questionTranslations" idField="questionId" titleField="questionTitle" labels={questionLabels} extras={[{ key: "textInputPlaceholder", labelKey: "scaleGovernance.textPlaceholder" }]} /> },
    { key: "options", label: t("scaleGovernance.options"), children: <TranslationRows form={form} name="optionTranslations" idField="optionId" titleField="optionLabel" labels={optionLabels} extras={[]} /> },
    { key: "results", label: t("scaleGovernance.resultRules"), children: <TranslationRows form={form} name="resultRuleTranslations" idField="resultRuleId" titleField="resultTitle" labels={ruleLabels} extras={[{ key: "resultDescription", labelKey: "scaleGovernance.description", rows: 2 }, { key: "suggestionText", labelKey: "scaleGovernance.suggestion", rows: 2 }]} /> },
    { key: "high-risk-results", label: t("scaleGovernance.highRiskRules"), children: <TranslationRows form={form} name="highRiskRuleTranslations" idField="highRiskRuleId" titleField="resultTitle" labels={highRiskRuleLabels} extras={[{ key: "resultDescription", labelKey: "scaleGovernance.description", rows: 2 }, { key: "suggestionText", labelKey: "scaleGovernance.suggestion", rows: 2 }]} /> }
  ]} />;
}

function TranslationRows({ form, name, idField, titleField, labels, extras }: { form: ReturnType<typeof Form.useForm<UpdateScalePackageRequest>>[0]; name: TranslationListName; idField: string; titleField: string; labels: Map<number, string>; extras: ExtraField[] }) {
  const { t } = useI18n();
  return <Form.List name={name}>{(fields) => <Space direction="vertical" size={8} style={{ width: "100%" }}>{fields.map((field) => {
    const row = form.getFieldValue([name, field.name]) as Record<string, unknown> | undefined;
    const entityId = Number(row?.[idField]);
    return <Card key={field.key} size="small" title={`${labels.get(entityId) ?? `#${entityId}`} · ${String(row?.localeCode ?? "")}`}>
      <Form.Item name={[field.name, idField]} hidden><InputNumber /></Form.Item>
      <Form.Item name={[field.name, "localeCode"]} hidden><Input /></Form.Item>
      <Form.Item name={[field.name, titleField]} label={t("scaleGovernance.localizedTitle")}><Input /></Form.Item>
      {extras.map((extra) => <Form.Item key={extra.key} name={[field.name, extra.key]} label={t(extra.labelKey)}>{extra.rows ? <Input.TextArea rows={extra.rows} /> : <Input />}</Form.Item>)}
      <Form.Item name={[field.name, "reviewStatus"]} label={t("scaleGovernance.reviewStatus")} rules={[{ required: true }]}><Select options={reviewOptions} /></Form.Item>
    </Card>;
  })}</Space>}</Form.List>;
}

function QualityFields() {
  const { t } = useI18n();
  const jsonRule = { validator: async (_: unknown, value: unknown) => { if (!isValidJson(value)) throw new Error(t("scaleGovernance.invalidJson")); } };
  return <Space direction="vertical" size={12} style={{ width: "100%" }}>
    <Card size="small" title={t("scaleGovernance.qualityPolicy")}>
      <Row gutter={16}>
        <Col xs={24} md={12}><Form.Item name={["qualityPolicy", "missingAnswerPolicy"]} label={t("scaleGovernance.missingPolicy")} rules={[{ required: true }]}><Select options={["REJECT", "ALLOW", "PRORATE", "PENDING_PROFESSIONAL_REVIEW"].map((value) => ({ value, label: value }))} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name={["qualityPolicy", "maxMissingRatio"]} label={t("scaleGovernance.maxMissingRatio")} rules={[{ required: true }]}><InputNumber min={0} max={1} step={0.01} style={{ width: "100%" }} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name={["qualityPolicy", "minimumDurationSeconds"]} label={t("scaleGovernance.minimumDuration")}><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name={["qualityPolicy", "maximumDurationSeconds"]} label={t("scaleGovernance.maximumDuration")}><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name={["qualityPolicy", "invalidResultAction"]} label={t("scaleGovernance.invalidAction")} rules={[{ required: true }]}><Select options={["INVALIDATE", "REQUIRE_REVIEW", "ALLOW_WITH_WARNING"].map((value) => ({ value, label: value }))} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name={["qualityPolicy", "requireAllRequiredAnswers"]} label={t("scaleGovernance.requireAll")} valuePropName="checked"><Switch /></Form.Item></Col>
      </Row>
    </Card>
    <Form.List name="validityRules">{(fields, { add, remove }) => <Card size="small" title={t("scaleGovernance.validityRules")} extra={<Button icon={<PlusOutlined />} onClick={() => add({ ruleCode: "", ruleType: "CONSISTENCY", ruleVersion: "1", configJson: "{}", reviewStatus: "DRAFT", enabled: false, sortNo: fields.length })}>{t("scaleGovernance.addRule")}</Button>}>
      <Space direction="vertical" size={8} style={{ width: "100%" }}>{fields.map((field) => <Card key={field.key} size="small" extra={<Button danger type="text" icon={<DeleteOutlined />} onClick={() => remove(field.name)} />}>
        <Row gutter={12}>
          <Col xs={24} md={8}><Form.Item name={[field.name, "ruleCode"]} label={t("scaleGovernance.ruleCode")} rules={[{ required: true }]}><Input /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name={[field.name, "ruleType"]} label={t("scaleGovernance.ruleType")} rules={[{ required: true }]}><Select options={["CONSISTENCY", "CONTRADICTION", "DURATION", "RESPONSE_PATTERN", "CUSTOM_EXTENSION"].map((value) => ({ value, label: value }))} /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name={[field.name, "ruleVersion"]} label={t("scaleGovernance.ruleVersion")} rules={[{ required: true }]}><Input /></Form.Item></Col>
          <Col span={24}><Form.Item name={[field.name, "configJson"]} label={t("scaleGovernance.configJson")} rules={[jsonRule]}><Input.TextArea rows={3} /></Form.Item></Col>
          <Col xs={24} md={8}><Form.Item name={[field.name, "reviewStatus"]} label={t("scaleGovernance.reviewStatus")}><Select options={reviewOptions} /></Form.Item></Col>
          <Col xs={12} md={8}><Form.Item name={[field.name, "enabled"]} label={t("scaleGovernance.enabled")} valuePropName="checked"><Switch /></Form.Item></Col>
          <Col xs={12} md={8}><Form.Item name={[field.name, "sortNo"]} label={t("scaleGovernance.sortNo")}><InputNumber min={0} /></Form.Item></Col>
        </Row>
      </Card>)}</Space>
    </Card>}</Form.List>
  </Space>;
}

function AlgorithmFields() {
  const { t } = useI18n();
  const jsonRule = { validator: async (_: unknown, value: unknown) => { if (!isValidJson(value)) throw new Error(t("scaleGovernance.invalidJson")); } };
  return <Card size="small"><Row gutter={16}>
    <Col xs={24} md={12}><Form.Item name={["algorithmBinding", "algorithmCode"]} label={t("scaleGovernance.algorithmCode")} rules={[{ required: true }]}><Input /></Form.Item></Col>
    <Col xs={24} md={12}><Form.Item name={["algorithmBinding", "algorithmVersion"]} label={t("scaleGovernance.algorithmVersion")} rules={[{ required: true }]}><Input /></Form.Item></Col>
    <Col xs={24} md={12}><Form.Item name={["algorithmBinding", "implementationType"]} label={t("scaleGovernance.implementationType")} rules={[{ required: true }]}><Select options={["BUILTIN", "RESTRICTED_EXTENSION"].map((value) => ({ value, label: value }))} /></Form.Item></Col>
    <Col xs={24} md={12}><Form.Item name={["algorithmBinding", "implementationChecksum"]} label={t("scaleGovernance.checksum")}><Input /></Form.Item></Col>
    <Col span={24}><Form.Item name={["algorithmBinding", "inputSchemaJson"]} label={t("scaleGovernance.inputSchema")} rules={[jsonRule]}><Input.TextArea rows={5} /></Form.Item></Col>
    <Col span={24}><Form.Item name={["algorithmBinding", "outputSchemaJson"]} label={t("scaleGovernance.outputSchema")} rules={[jsonRule]}><Input.TextArea rows={5} /></Form.Item></Col>
    <Col xs={24} md={12}><Form.Item name={["algorithmBinding", "reviewStatus"]} label={t("scaleGovernance.reviewStatus")} rules={[{ required: true }]}><Select options={reviewOptions} /></Form.Item></Col>
  </Row></Card>;
}

function NormGovernanceFields({ form, scale }: { form: ReturnType<typeof Form.useForm<UpdateScalePackageRequest>>[0]; scale: ScaleDetail }) {
  const { t } = useI18n();
  const labels = useMemo(() => new Map(scale.norms.map((norm) => [norm.id, `${norm.normCode} · ${norm.normName ?? ""}`])), [scale.norms]);
  return <Form.List name="normGovernance">{(fields) => <Space direction="vertical" size={8} style={{ width: "100%" }}>{fields.map((field) => {
    const normId = Number(form.getFieldValue(["normGovernance", field.name, "normId"]));
    return <Card key={field.key} size="small" title={labels.get(normId) ?? `#${normId}`}>
      <Form.Item name={[field.name, "normId"]} hidden><InputNumber /></Form.Item>
      <Row gutter={12}>
        <Col xs={24} md={12}><Form.Item name={[field.name, "sourceReference"]} label={t("scaleGovernance.sourceReference")}><Input /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name={[field.name, "normVersion"]} label={t("scaleGovernance.normVersion")}><Input /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={[field.name, "sampleSize"]} label={t("scaleGovernance.sampleSize")}><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={[field.name, "regionCode"]} label={t("scaleGovernance.regionCode")}><Input /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={[field.name, "languageCode"]} label={t("scaleGovernance.languageCode")}><Input /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={[field.name, "validFrom"]} label={t("scaleGovernance.validFrom")}><Input placeholder="YYYY-MM-DD" /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={[field.name, "validTo"]} label={t("scaleGovernance.validTo")}><Input placeholder="YYYY-MM-DD" /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={[field.name, "reviewStatus"]} label={t("scaleGovernance.reviewStatus")}><Select options={["PENDING_REVIEW", "APPROVED", "REJECTED", "EXPIRED"].map((value) => ({ value, label: value }))} /></Form.Item></Col>
      </Row>
    </Card>;
  })}</Space>}</Form.List>;
}
