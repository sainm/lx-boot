import { DownloadOutlined, PlusOutlined, UploadOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Grid,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message
} from "antd";
import { useState } from "react";
import { Permission } from "../components/Permission";
import {
  batchCreateNorms,
  batchCreateDimensions,
  batchCreateQuestions,
  batchCreateResultRules,
  confirmScaleImport,
  createScale,
  createScaleVersion,
  downloadScaleImportTemplate,
  fetchScaleVersionDiff,
  fetchScaleVersions,
  fetchScaleImportDetail,
  fetchScaleImportPage,
  fetchScaleDetail,
  fetchScaleNormCoverage,
  fetchScalePage,
  parseScaleImport,
  publishScaleVersion,
  updateScaleBasic,
  updateScaleDimension,
  updateScaleOption,
  updateScaleQuestion,
  updateScaleVisualizations,
  type CreateDimensionItem,
  type CreateNormItem,
  type CreateQuestionItem,
  type CreateResultRuleItem,
  type CreateScaleVersionRequest,
  type ParseScaleImportResponse,
  type ScaleDimension,
  type ScaleVersionDiff,
  type ScaleVersionDiffChange,
  type ScaleImportDetail,
  type ScaleImportIssue,
  type ScaleImportListItem,
  type ScaleQuestion,
  type ScaleNorm,
  type ScaleNormCoverage,
  type ScaleQuestionOption,
  type ScaleSummary,
  type ScaleResultRule
} from "../features/scales/api";
import type { ScaleVisualizationConfig, ScaleVisualizationConfigDraft } from "../features/visualizations/types";
import { useI18n } from "../i18n/provider";

const PAGE_SIZE = 20;
const QUESTION_TYPES_WITH_OPTIONS = new Set(["SINGLE_CHOICE", "MULTI_SELECT", "MATRIX", "TEXT_WITH_OPTION"]);
const OPTION_DEFAULT = { optionCode: "A", optionLabel: "", scoreValue: 0, sortNo: 0 };

function chartTypeOptions(t: (key: string) => string) {
  return [
    "RADAR",
    "DIMENSION_BAR",
    "ANSWER_SCORE_DISTRIBUTION",
    "NORM_COMPARE",
    "RISK_CUE",
    "GROUP_COMPLETION_BAR",
    "GROUP_RISK_STACK",
    "GROUP_DIMENSION_HEATMAP",
    "GROUP_SCORE_RANKING"
  ].map((value) => ({ value, label: t(`scales.visualization.chartType.${value}`) }));
}

function viewScopeOptions(t: (key: string) => string) {
  return ["REPORT_DETAIL", "GROUP_REPORT"].map((value) => ({ value, label: t(`scales.visualization.viewScope.${value}`) }));
}

function dataSourceOptions(t: (key: string) => string) {
  return [
    "DIMENSION_SCORE",
    "ANSWER_SCORE_DISTRIBUTION",
    "RISK_DISTRIBUTION",
    "NORM_COMPARE",
    "COMPLETION_RATE",
    "GROUP_SCORE_RANKING"
  ].map((value) => ({ value, label: t(`scales.visualization.dataSource.${value}`) }));
}

export function ScaleListPage() {
  const { t } = useI18n();
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [dimOpen, setDimOpen] = useState(false);
  const [questionOpen, setQuestionOpen] = useState(false);
  const [ruleOpen, setRuleOpen] = useState(false);
  const [normOpen, setNormOpen] = useState(false);
  const [visualizationOpen, setVisualizationOpen] = useState(false);
  const [versionOpen, setVersionOpen] = useState(false);
  const [diffOpen, setDiffOpen] = useState(false);
  const [basicEditOpen, setBasicEditOpen] = useState(false);
  const [editingDimension, setEditingDimension] = useState<ScaleDimension | null>(null);
  const [editingQuestion, setEditingQuestion] = useState<ScaleQuestion | null>(null);
  const [editingOption, setEditingOption] = useState<ScaleQuestionOption | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [importDetailOpen, setImportDetailOpen] = useState(false);
  const [selectedScaleId, setSelectedScaleId] = useState<number | null>(null);
  const [selectedImportId, setSelectedImportId] = useState<number | null>(null);
  const [nameInput, setNameInput] = useState("");
  const [nameFilter, setNameFilter] = useState<string | undefined>(undefined);
  const [importStatusFilter, setImportStatusFilter] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importResult, setImportResult] = useState<ParseScaleImportResponse | null>(null);
  const [diffResult, setDiffResult] = useState<ScaleVersionDiff | null>(null);
  const [confirmRemark, setConfirmRemark] = useState("Confirmed from admin web");
  const screens = Grid.useBreakpoint();

  const [createForm] = Form.useForm();
  const [basicEditForm] = Form.useForm();
  const [versionForm] = Form.useForm<CreateScaleVersionRequest>();
  const [diffForm] = Form.useForm<{ targetId: number }>();
  const [dimensionEditForm] = Form.useForm();
  const [questionEditForm] = Form.useForm();
  const [optionEditForm] = Form.useForm();
  const [dimForm] = Form.useForm<{ dimensions: CreateDimensionItem[] }>();
  const [questionForm] = Form.useForm<{ questions: CreateQuestionItem[] }>();
  const [ruleForm] = Form.useForm<{ resultRules: CreateResultRuleItem[] }>();
  const [normForm] = Form.useForm<{ norms: CreateNormItem[] }>();
  const [visualizationForm] = Form.useForm<{ visualizations: ScaleVisualizationConfigDraft[] }>();
  const queryClient = useQueryClient();

  const queryParams = { scaleName: nameFilter, page, size: PAGE_SIZE };

  const scaleQuery = useQuery({
    queryKey: ["scales", queryParams],
    queryFn: () => fetchScalePage(queryParams)
  });

  const importQuery = useQuery({
    queryKey: ["scale-imports", importStatusFilter],
    queryFn: () => fetchScaleImportPage({ status: importStatusFilter, page: 1, size: 10 })
  });

  const versionQuery = useQuery({
    queryKey: ["scales", "versions", selectedScaleId],
    queryFn: () => fetchScaleVersions(selectedScaleId!),
    enabled: selectedScaleId != null && detailOpen
  });

  const detailQuery = useQuery({
    queryKey: ["scales", "detail", selectedScaleId],
    queryFn: () => fetchScaleDetail(selectedScaleId!),
    enabled: selectedScaleId != null && detailOpen
  });

  const normCoverageQuery = useQuery({
    queryKey: ["scales", "norm-coverage", selectedScaleId],
    queryFn: () => fetchScaleNormCoverage(selectedScaleId!),
    enabled: selectedScaleId != null && detailOpen
  });

  const importDetailQuery = useQuery({
    queryKey: ["scale-imports", "detail", selectedImportId],
    queryFn: () => fetchScaleImportDetail(selectedImportId!),
    enabled: selectedImportId != null && importDetailOpen
  });

  const createScaleMutation = useMutation({
    mutationFn: createScale,
    onSuccess: async () => {
      void message.success(t("scales.created"));
      setCreateOpen(false);
      createForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales"] });
    }
  });

  const createVersionMutation = useMutation({
    mutationFn: ({ scaleId, payload }: { scaleId: number; payload: CreateScaleVersionRequest }) =>
      createScaleVersion(scaleId, payload),
    onSuccess: async (data) => {
      void message.success(t("scales.versionCreated"));
      setVersionOpen(false);
      versionForm.resetFields();
      setSelectedScaleId(data.id);
      setDetailOpen(true);
      await queryClient.invalidateQueries({ queryKey: ["scales"] });
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail"] });
      await queryClient.invalidateQueries({ queryKey: ["scales", "versions"] });
    }
  });

  const publishVersionMutation = useMutation({
    mutationFn: publishScaleVersion,
    onSuccess: async (data) => {
      void message.success(t("scales.versionPublished", { versionNo: data.versionNo ?? data.id }));
      await queryClient.invalidateQueries({ queryKey: ["scales"] });
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", data.id] });
      await queryClient.invalidateQueries({ queryKey: ["scales", "versions"] });
    }
  });

  const updateBasicMutation = useMutation({
    mutationFn: ({ scaleId, payload }: { scaleId: number; payload: Parameters<typeof updateScaleBasic>[1] }) =>
      updateScaleBasic(scaleId, payload),
    onSuccess: async (data) => {
      void message.success(t("scales.updated"));
      setBasicEditOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["scales"] });
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", data.id] });
    }
  });

  const updateDimensionMutation = useMutation({
    mutationFn: ({ scaleId, dimensionId, payload }: { scaleId: number; dimensionId: number; payload: Parameters<typeof updateScaleDimension>[2] }) =>
      updateScaleDimension(scaleId, dimensionId, payload),
    onSuccess: async (data) => {
      void message.success(t("scales.updated"));
      setEditingDimension(null);
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", data.id] });
    }
  });

  const updateQuestionMutation = useMutation({
    mutationFn: ({ scaleId, questionId, payload }: { scaleId: number; questionId: number; payload: Parameters<typeof updateScaleQuestion>[2] }) =>
      updateScaleQuestion(scaleId, questionId, payload),
    onSuccess: async (data) => {
      void message.success(t("scales.updated"));
      setEditingQuestion(null);
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", data.id] });
    }
  });

  const updateOptionMutation = useMutation({
    mutationFn: ({ scaleId, optionId, payload }: { scaleId: number; optionId: number; payload: Parameters<typeof updateScaleOption>[2] }) =>
      updateScaleOption(scaleId, optionId, payload),
    onSuccess: async (data) => {
      void message.success(t("scales.updated"));
      setEditingOption(null);
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", data.id] });
    }
  });

  const updateVisualizationsMutation = useMutation({
    mutationFn: ({ scaleId, visualizations }: { scaleId: number; visualizations: ScaleVisualizationConfigDraft[] }) =>
      updateScaleVisualizations(scaleId, visualizations),
    onSuccess: async (data) => {
      void message.success(t("scales.visualizationsSaved"));
      setVisualizationOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", data.id] });
    }
  });

  const diffMutation = useMutation({
    mutationFn: ({ scaleId, targetId }: { scaleId: number; targetId: number }) =>
      fetchScaleVersionDiff(scaleId, targetId),
    onSuccess: (data) => {
      setDiffResult(data);
    }
  });

  const batchDimMutation = useMutation({
    mutationFn: ({ scaleId, dimensions }: { scaleId: number; dimensions: CreateDimensionItem[] }) =>
      batchCreateDimensions(scaleId, dimensions),
    onSuccess: async () => {
      void message.success(t("scales.dimensionsAdded"));
      setDimOpen(false);
      dimForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", selectedScaleId] });
    }
  });

  const batchQuestionMutation = useMutation({
    mutationFn: ({ scaleId, questions }: { scaleId: number; questions: CreateQuestionItem[] }) =>
      batchCreateQuestions(scaleId, questions),
    onSuccess: async () => {
      void message.success(t("scales.questionsAdded"));
      setQuestionOpen(false);
      questionForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", selectedScaleId] });
    }
  });

  const batchRuleMutation = useMutation({
    mutationFn: ({ scaleId, resultRules }: { scaleId: number; resultRules: CreateResultRuleItem[] }) =>
      batchCreateResultRules(scaleId, resultRules),
    onSuccess: async () => {
      void message.success(t("scales.rulesAdded"));
      setRuleOpen(false);
      ruleForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", selectedScaleId] });
    }
  });

  const batchNormMutation = useMutation({
    mutationFn: ({ scaleId, norms }: { scaleId: number; norms: CreateNormItem[] }) =>
      batchCreateNorms(scaleId, norms),
    onSuccess: async () => {
      void message.success(t("scales.normsAdded"));
      setNormOpen(false);
      normForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", selectedScaleId] });
      await queryClient.invalidateQueries({ queryKey: ["scales", "norm-coverage", selectedScaleId] });
    }
  });

  const downloadTemplateMutation = useMutation({
    mutationFn: downloadScaleImportTemplate,
    onSuccess: (blob) => {
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "scale-import-template.xlsx";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    }
  });

  const parseImportMutation = useMutation({
    mutationFn: (file: File) => parseScaleImport(file),
    onSuccess: (data) => {
      setImportResult(data);
      void message.success(t("scales.importParsed"));
    }
  });

  const confirmImportMutation = useMutation({
    mutationFn: ({ importId, confirmRemark }: { importId: number; confirmRemark: string }) =>
      confirmScaleImport(importId, confirmRemark),
    onSuccess: async (data) => {
      void message.success(t("scales.importConfirmed"));
      setImportOpen(false);
      setImportFile(null);
      setImportResult(null);
      setSelectedScaleId(data.scaleId);
      setDetailOpen(true);
      await queryClient.invalidateQueries({ queryKey: ["scales"] });
      await queryClient.invalidateQueries({ queryKey: ["scale-imports"] });
      await queryClient.invalidateQueries({ queryKey: ["scales", "detail", data.scaleId] });
    }
  });

  const handleSearch = () => {
    setNameFilter(nameInput.trim() || undefined);
    setPage(1);
  };

  const handleReset = () => {
    setNameInput("");
    setNameFilter(undefined);
    setPage(1);
  };

  const handleCreate = async () => {
    const values = await createForm.validateFields();
    await createScaleMutation.mutateAsync(values);
  };

  const handleCreateVersion = async () => {
    if (selectedScaleId == null) return;
    const values = await versionForm.validateFields();
    await createVersionMutation.mutateAsync({
      scaleId: selectedScaleId,
      payload: values
    });
  };

  const handlePublishVersion = async () => {
    if (selectedScaleId == null) return;
    await publishVersionMutation.mutateAsync(selectedScaleId);
  };

  const handleCompareVersion = async () => {
    if (selectedScaleId == null) return;
    const values = await diffForm.validateFields();
    await diffMutation.mutateAsync({
      scaleId: selectedScaleId,
      targetId: values.targetId
    });
  };

  const handleAddDimensions = async () => {
    if (selectedScaleId == null) return;
    const values = await dimForm.validateFields();
    await batchDimMutation.mutateAsync({
      scaleId: selectedScaleId,
      dimensions: values.dimensions
    });
  };

  const handleAddQuestions = async () => {
    if (selectedScaleId == null) return;
    const values = await questionForm.validateFields();
    await batchQuestionMutation.mutateAsync({
      scaleId: selectedScaleId,
      questions: values.questions
    });
  };

  const handleAddRules = async () => {
    if (selectedScaleId == null) return;
    const values = await ruleForm.validateFields();
    await batchRuleMutation.mutateAsync({
      scaleId: selectedScaleId,
      resultRules: values.resultRules
    });
  };

  const handleAddNorms = async () => {
    if (selectedScaleId == null) return;
    const values = await normForm.validateFields();
    await batchNormMutation.mutateAsync({
      scaleId: selectedScaleId,
      norms: values.norms
    });
  };

  const handleUpdateVisualizations = async () => {
    if (selectedScaleId == null) return;
    const values = await visualizationForm.validateFields();
    await updateVisualizationsMutation.mutateAsync({
      scaleId: selectedScaleId,
      visualizations: values.visualizations ?? []
    });
  };

  const handleUpdateBasic = async () => {
    if (selectedScaleId == null) return;
    const values = await basicEditForm.validateFields();
    await updateBasicMutation.mutateAsync({ scaleId: selectedScaleId, payload: values });
  };

  const handleUpdateDimension = async () => {
    if (selectedScaleId == null || !editingDimension) return;
    const values = await dimensionEditForm.validateFields();
    await updateDimensionMutation.mutateAsync({
      scaleId: selectedScaleId,
      dimensionId: editingDimension.id,
      payload: values
    });
  };

  const handleUpdateQuestion = async () => {
    if (selectedScaleId == null || !editingQuestion) return;
    const values = await questionEditForm.validateFields();
    await updateQuestionMutation.mutateAsync({
      scaleId: selectedScaleId,
      questionId: editingQuestion.id,
      payload: values
    });
  };

  const handleUpdateOption = async () => {
    if (selectedScaleId == null || !editingOption) return;
    const values = await optionEditForm.validateFields();
    await updateOptionMutation.mutateAsync({
      scaleId: selectedScaleId,
      optionId: editingOption.id,
      payload: values
    });
  };

  const openDetail = (id: number) => {
    setSelectedScaleId(id);
    setDetailOpen(true);
  };

  const openBasicEdit = () => {
    if (!detail) return;
    basicEditForm.setFieldsValue({
      scaleName: detail.scaleName,
      description: detail.description,
      applicableTarget: detail.applicableTarget,
      anonymousSupported: detail.anonymousSupported,
      reportTemplate: detail.reportTemplate
    });
    setBasicEditOpen(true);
  };

  const openDimensionEdit = (dimension: ScaleDimension) => {
    dimensionEditForm.setFieldsValue(dimension);
    setEditingDimension(dimension);
  };

  const openQuestionEdit = (question: ScaleQuestion) => {
    questionEditForm.setFieldsValue({
      dimensionId: question.dimensionId,
      questionTitle: question.questionTitle,
      requiredFlag: question.requiredFlag,
      reverseScoreFlag: question.reverseScoreFlag,
      weightValue: question.weightValue,
      sortNo: question.sortNo
    });
    setEditingQuestion(question);
  };

  const openOptionEdit = (option: ScaleQuestionOption) => {
    optionEditForm.setFieldsValue(option);
    setEditingOption(option);
  };

  const openVisualizationEdit = () => {
    visualizationForm.setFieldsValue({
      visualizations: (detail?.visualizationConfigs ?? []).map((item) => ({
        chartType: item.chartType,
        chartTitle: item.chartTitle,
        viewScope: item.viewScope,
        dataSource: item.dataSource,
        configJson: item.configJson,
        enabled: item.enabled,
        sortNo: item.sortNo
      }))
    });
    setVisualizationOpen(true);
  };

  const openDiff = () => {
    setDiffResult(null);
    diffForm.resetFields();
    setDiffOpen(true);
  };

  const openDiffWithTarget = (targetId: number) => {
    setDiffResult(null);
    diffForm.setFieldsValue({ targetId });
    setDiffOpen(true);
  };

  const handleDownloadTemplate = async () => {
    await downloadTemplateMutation.mutateAsync();
  };

  const handleParseImport = async () => {
    if (!importFile) {
      void message.warning(t("scales.importFileRequired"));
      return;
    }
    await parseImportMutation.mutateAsync(importFile);
  };

  const handleConfirmImport = async () => {
    if (!importResult || importResult.status !== "PARSED" || importResult.errorCount > 0) {
      return;
    }
    await confirmImportMutation.mutateAsync({
      importId: importResult.importId,
      confirmRemark
    });
  };

  const resetImportState = () => {
    setImportOpen(false);
    setImportFile(null);
    setImportResult(null);
    setConfirmRemark("Confirmed from admin web");
  };

  const openImportDetail = (id: number) => {
    setSelectedImportId(id);
    setImportDetailOpen(true);
  };

  const openCreatedScale = (scaleId: number) => {
    setSelectedScaleId(scaleId);
    setDetailOpen(true);
  };

  const detail = detailQuery.data;
  const versions = versionQuery.data ?? [];
  const importIssues = [...(importResult?.errors ?? []), ...(importResult?.warnings ?? [])];
  const hasImportErrors = (importResult?.errorCount ?? 0) > 0;
  const importDetail = importDetailQuery.data;
  const importDetailIssues = [...(importDetail?.errors ?? []), ...(importDetail?.warnings ?? [])];
  const normCoverage = normCoverageQuery.data;
  const detailDrawerWidth = screens.xl ? 1120 : screens.lg ? 960 : screens.md ? 760 : "100vw";

  const renderQuestionType = (questionType?: string | null) => {
    const labels: Record<string, string> = {
      SINGLE_CHOICE: t("scales.questionType.singleChoice"),
      MULTI_SELECT: t("scales.questionType.multiSelect"),
      SLIDER: t("scales.questionType.slider"),
      MATRIX: t("scales.questionType.matrix"),
      TEXT_WITH_OPTION: t("scales.questionType.textWithOption"),
      TEXT: t("scales.questionType.text")
    };
    return questionType ? labels[questionType] ?? questionType : "-";
  };

  const renderQuestionConfig = (question: ScaleQuestion) => {
    const items = [
      `${t("scales.weightValue")}: ${question.weightValue}`,
      question.reverseScoreFlag ? t("scales.reverseScoreFlag") : undefined,
      question.optionSelectionLimit != null ? `${t("scales.optionSelectionLimit")}: ${question.optionSelectionLimit}` : undefined,
      question.questionType === "SLIDER"
        ? `${t("scales.sliderMin")}/${t("scales.sliderMax")}/${t("scales.sliderStep")}: ${question.sliderMin ?? "-"} / ${question.sliderMax ?? "-"} / ${question.sliderStep ?? "-"}`
        : undefined,
      question.questionType === "MATRIX"
        ? `${t("scales.matrixGroupCode")}: ${question.matrixGroupCode ?? "-"}; ${t("scales.rowCode")}: ${question.rowCode ?? "-"}; ${t("scales.columnCode")}: ${question.columnCode ?? "-"}`
        : undefined,
      question.questionType === "TEXT_WITH_OPTION"
        ? `${t("scales.textInputEnabled")}: ${question.textInputEnabled ? t("common.yes") : t("common.no")}${question.textInputPlaceholder ? `; ${t("scales.textInputPlaceholder")}: ${question.textInputPlaceholder}` : ""}`
        : undefined
    ].filter((item): item is string => Boolean(item));

    return (
      <Space wrap size={[4, 4]}>
        {items.map((item) => <Tag key={item}>{item}</Tag>)}
      </Space>
    );
  };

  const renderNormScope = (norm: ScaleNorm) => {
    if (norm.dimensionId == null) {
      return t("scales.normScopeGlobal");
    }
    const dimension = detail?.dimensions.find((item) => item.id === norm.dimensionId);
    return dimension ? `${dimension.dimensionName} (${dimension.dimensionCode})` : `#${norm.dimensionId}`;
  };

  const renderNormTagList = (norm: ScaleNorm) => {
    const items = [
      norm.applicableTarget ? `${t("scales.col.applicableTarget")}: ${norm.applicableTarget}` : undefined,
      norm.ageMin != null || norm.ageMax != null
        ? `${t("scales.ageRange")}: ${norm.ageMin ?? "-"}-${norm.ageMax ?? "-"}`
        : undefined,
      norm.gender ? `${t("scales.gender")}: ${norm.gender}` : undefined,
      norm.orgType ? `${t("scales.orgType")}: ${norm.orgType}` : undefined,
      norm.meanScore != null ? `${t("scales.meanScore")}: ${norm.meanScore}` : undefined,
      norm.stdDeviation != null ? `${t("scales.stdDeviation")}: ${norm.stdDeviation}` : undefined,
      norm.tScoreMean != null ? `${t("scales.tScoreMean")}: ${norm.tScoreMean}` : undefined,
      norm.tScoreStdDeviation != null ? `${t("scales.tScoreStdDeviation")}: ${norm.tScoreStdDeviation}` : undefined
    ].filter((item): item is string => Boolean(item));

    return (
      <Space wrap size={[4, 4]}>
        {items.length > 0
          ? items.map((item) => <Tag key={item}>{item}</Tag>)
          : <Typography.Text type="secondary">-</Typography.Text>}
      </Space>
    );
  };

  const diffSnapshotLabels: Record<string, string> = {
    questionType: t("scales.col.questionType"),
    requiredFlag: t("scales.col.required"),
    reverseScoreFlag: t("scales.reverseScoreFlag"),
    weightValue: t("scales.weightValue"),
    optionSelectionLimit: t("scales.optionSelectionLimit"),
    sliderMin: t("scales.sliderMin"),
    sliderMax: t("scales.sliderMax"),
    sliderStep: t("scales.sliderStep"),
    textInputEnabled: t("scales.textInputEnabled"),
    textInputPlaceholder: t("scales.textInputPlaceholder"),
    matrixGroupCode: t("scales.matrixGroupCode"),
    rowCode: t("scales.rowCode"),
    columnCode: t("scales.columnCode"),
    exclusiveFlag: t("scales.exclusiveFlag"),
    optionGroupCode: t("scales.optionGroupCode")
  };

  const renderDiffSnapshotValue = (key: string, value: string | null | undefined) => {
    if (value == null || value === "") return "-";
    if (key === "questionType") return renderQuestionType(value);
    if (value === "true") return t("common.yes");
    if (value === "false") return t("common.no");
    return value;
  };

  const renderDiffSnapshot = (snapshot?: Record<string, string | null | undefined>) => {
    if (!snapshot) return "-";
    return (
      <Space direction="vertical" size={2}>
        {Object.entries(snapshot).map(([key, value]) => (
          <Typography.Text key={key} style={{ fontSize: 12 }}>
            <Typography.Text type="secondary">{diffSnapshotLabels[key] ?? key}: </Typography.Text>
            {renderDiffSnapshotValue(key, value)}
          </Typography.Text>
        ))}
      </Space>
    );
  };

  const diffChangeColor = (changeType: string) => {
    if (changeType === "ADDED") return "green";
    if (changeType === "REMOVED") return "red";
    return "blue";
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16 }}>
        <div>
          <Typography.Title level={4}>{t("scales.title")}</Typography.Title>
          <Typography.Text type="secondary">{t("scales.subtitle")}</Typography.Text>
        </div>
        <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
          <Space>
            <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
              {t("scales.import")}
            </Button>
            <Button type="primary" onClick={() => setCreateOpen(true)}>
              {t("scales.create")}
            </Button>
          </Space>
        </Permission>
      </div>

      <Space>
        <Input
          placeholder={t("scales.searchPlaceholder")}
          style={{ width: 260 }}
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          onPressEnter={handleSearch}
          allowClear
          onClear={handleReset}
        />
        <Button type="primary" onClick={handleSearch}>{t("scales.search")}</Button>
        <Button onClick={handleReset}>{t("scales.reset")}</Button>
      </Space>

      {scaleQuery.isError ? (
        <Alert type="warning" showIcon message={t("scales.loadError")} />
      ) : null}

      <Table
        rowKey="id"
        loading={scaleQuery.isLoading}
        dataSource={scaleQuery.data?.list ?? []}
        pagination={false}
        columns={[
          { title: t("scales.col.scaleCode"), dataIndex: "scaleCode", width: 160 },
          { title: t("scales.col.scaleName"), dataIndex: "scaleName" },
          { title: t("scales.col.applicableTarget"), dataIndex: "applicableTarget" },
          { title: t("scales.col.version"), dataIndex: "versionNo", width: 80 },
          {
            title: t("scales.col.currentVersion"),
            dataIndex: "currentVersionFlag",
            width: 110,
            render: (value: boolean) => value ? <Tag color="green">{t("common.yes")}</Tag> : <Tag>{t("common.no")}</Tag>
          },
          { title: t("scales.col.scoreMethod"), dataIndex: "scoreMethod", width: 120 },
          {
            title: t("scales.col.status"),
            dataIndex: "status",
            width: 100,
            render: (value: string) => <Tag color="gold">{value}</Tag>
          },
          {
            title: t("scales.col.action"),
            width: 120,
            render: (_, record) => (
              <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                <Button type="link" onClick={() => openDetail(record.id)}>{t("scales.viewDetail")}</Button>
              </Permission>
            )
          }
        ]}
      />

      {(scaleQuery.data?.total ?? 0) > PAGE_SIZE ? (
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Pagination
            current={page}
            pageSize={PAGE_SIZE}
            total={scaleQuery.data?.total ?? 0}
            showTotal={(total) => t("scales.total", { total })}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
          />
        </div>
      ) : null}

      <Divider orientation="left" plain>
        {t("scales.importRecords")}
      </Divider>

      <Space>
        <Select
          allowClear
          style={{ width: 220 }}
          placeholder={t("scales.import.statusPlaceholder")}
          value={importStatusFilter}
          onChange={(value) => setImportStatusFilter(value)}
          options={[
            { label: t("scales.import.status.PARSED"), value: "PARSED" },
            { label: t("scales.import.status.PARSE_FAILED"), value: "PARSE_FAILED" },
            { label: t("scales.import.status.SUCCESS"), value: "SUCCESS" },
            { label: t("scales.import.status.FAILED"), value: "FAILED" }
          ]}
        />
      </Space>

      <Table<ScaleImportListItem>
        rowKey="id"
        loading={importQuery.isLoading}
        dataSource={importQuery.data?.list ?? []}
        pagination={false}
        size="small"
        locale={{ emptyText: t("scales.importNoRecords") }}
        columns={[
          { title: t("scales.import.col.fileName"), dataIndex: "fileName" },
          { title: t("scales.import.col.status"), dataIndex: "status", width: 120 },
          { title: t("scales.import.col.errorCount"), dataIndex: "errorCount", width: 90 },
          { title: t("scales.import.col.warningCount"), dataIndex: "warningCount", width: 90 },
          {
            title: t("scales.import.col.createdScaleId"),
            dataIndex: "createdScaleId",
            width: 120,
            render: (value?: number) =>
              value ? (
                <Button type="link" onClick={() => openCreatedScale(value)}>
                  {value}
                </Button>
              ) : (
                "-"
              )
          },
          { title: t("scales.import.col.createdAt"), dataIndex: "createdAt", width: 180 },
          {
            title: t("scales.col.action"),
            width: 120,
            render: (_, record) => (
              <Button type="link" onClick={() => openImportDetail(record.id)}>
                {t("scales.viewDetail")}
              </Button>
            )
          }
        ]}
      />

      <Drawer
        title={detail ? `${detail.scaleName} (${detail.scaleCode})` : t("scales.detailTitle")}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={detailDrawerWidth}
        loading={detailQuery.isLoading}
      >
        {detailQuery.isError ? (
          <Alert type="error" showIcon message={t("scales.detailLoadError")} />
        ) : null}

        {detail ? (
          <>
            <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
              <Card size="small" style={{ marginBottom: 16 }}>
                <Space wrap size={[8, 8]}>
                  <Button onClick={() => setVersionOpen(true)}>
                    {t("scales.createVersion")}
                  </Button>
                  <Button onClick={() => void handlePublishVersion()} loading={publishVersionMutation.isPending}>
                    {t("scales.publishVersion")}
                  </Button>
                  <Button onClick={openDiff}>
                    {t("scales.compareVersion")}
                  </Button>
                  <Button icon={<PlusOutlined />} onClick={() => setDimOpen(true)}>
                    {t("scales.addDimension")}
                  </Button>
                  <Button icon={<PlusOutlined />} onClick={() => setQuestionOpen(true)}>
                    {t("scales.addQuestion")}
                  </Button>
                  <Button icon={<PlusOutlined />} onClick={() => setRuleOpen(true)}>
                    {t("scales.resultRules")}
                  </Button>
                  <Button icon={<PlusOutlined />} onClick={() => setNormOpen(true)}>
                    {t("scales.norms")}
                  </Button>
                  <Button onClick={openBasicEdit} disabled={detail.status !== "DRAFT"}>
                    {t("scales.editBasic")}
                  </Button>
                </Space>
                {detail.status !== "DRAFT" ? (
                  <Typography.Text type="secondary" style={{ display: "block", marginTop: 8 }}>
                    {t("scales.editDraftOnlyHint")}
                  </Typography.Text>
                ) : null}
              </Card>
            </Permission>

            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={t("scales.col.scaleCode")}>{detail.scaleCode}</Descriptions.Item>
              <Descriptions.Item label={t("scales.col.scaleName")}>{detail.scaleName}</Descriptions.Item>
              <Descriptions.Item label={t("scales.versionNo")}>{detail.versionNo ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("scales.versionGroupId")}>{detail.versionGroupId ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("scales.col.currentVersion")}>
                {detail.currentVersionFlag ? <Tag color="green">{t("common.yes")}</Tag> : <Tag>{t("common.no")}</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label={t("scales.col.applicableTarget")}>{detail.applicableTarget ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("scales.col.status")}>
                <Tag color="gold">{detail.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t("scales.col.scoreMethod")}>{detail.scoreMethod}</Descriptions.Item>
              <Descriptions.Item label={t("scales.scoreCoefficient")}>{detail.scoreCoefficient}</Descriptions.Item>
              <Descriptions.Item label={t("scales.normStrategy")}>{detail.normStrategy}</Descriptions.Item>
              <Descriptions.Item label={t("scales.defaultNormGroup")}>{detail.normDefaultGroup ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("scales.highRiskWarningEnabled")}>
                {detail.highRiskWarningEnabled ? t("common.yes") : t("common.no")}
              </Descriptions.Item>
              <Descriptions.Item label={t("scales.anonymousSupported")}>
                {detail.anonymousSupported ? t("common.yes") : t("common.no")}
              </Descriptions.Item>
              {detail.description ? (
                <Descriptions.Item label={t("scales.description")}>{detail.description}</Descriptions.Item>
              ) : null}
              </Descriptions>

              <Divider orientation="left" plain>
                {t("scales.versionsTitle")}
              </Divider>

              <Table<ScaleSummary>
                rowKey="id"
                size="small"
                pagination={false}
                scroll={{ x: "max-content" }}
                dataSource={versions}
                loading={versionQuery.isLoading}
                locale={{ emptyText: t("scales.versionListEmpty") }}
                columns={[
                  { title: t("scales.versionNo"), dataIndex: "versionNo", width: 120, render: (value?: string) => value ?? "-" },
                  { title: t("scales.col.status"), dataIndex: "status", width: 110 },
                  {
                    title: t("scales.col.currentVersion"),
                    dataIndex: "currentVersionFlag",
                    width: 120,
                    render: (value: boolean) => value ? <Tag color="green">{t("common.yes")}</Tag> : <Tag>{t("common.no")}</Tag>
                  },
                  { title: t("scales.col.createdAt"), dataIndex: "createdAt", width: 180 },
                  {
                    title: t("scales.col.action"),
                    width: 160,
                    render: (_, record) => (
                      <Space size={6}>
                        <Button type="link" onClick={() => openDiffWithTarget(record.id)}>
                          {t("scales.compareThisVersion")}
                        </Button>
                        {!record.currentVersionFlag ? (
                          <Button
                            type="link"
                            onClick={() => publishVersionMutation.mutateAsync(record.id)}
                            disabled={publishVersionMutation.isPending}
                          >
                            {t("scales.publishThisVersion")}
                          </Button>
                        ) : null}
                      </Space>
                    )
                  }
                ]}
              />

              <Divider orientation="left" plain>
                {t("scales.dimensionsTitle", { count: detail.dimensions.length })}
              </Divider>

            {detail.dimensions.length === 0 ? (
              <Typography.Text type="secondary">{t("scales.noDimensions")}</Typography.Text>
            ) : (
              <Table<ScaleDimension>
                rowKey="id"
                size="small"
                pagination={false}
                scroll={{ x: "max-content" }}
                dataSource={detail.dimensions}
                columns={[
                  { title: t("scales.col.sortNo"), dataIndex: "sortNo", width: 60 },
                  { title: t("scales.col.dimensionCode"), dataIndex: "dimensionCode", width: 140 },
                  { title: t("scales.col.dimensionName"), dataIndex: "dimensionName" },
                  { title: t("scales.description"), dataIndex: "description" },
                  {
                    title: t("scales.col.action"),
                    width: 90,
                    render: (_, record) => (
                      <Button type="link" disabled={detail.status !== "DRAFT"} onClick={() => openDimensionEdit(record)}>
                        {t("scales.edit")}
                      </Button>
                    )
                  }
                ]}
              />
            )}

            <Divider orientation="left" plain>
              {t("scales.questionsTitle", { count: detail.questions.length })}
            </Divider>

            {detail.questions.length === 0 ? (
              <Typography.Text type="secondary">{t("scales.noQuestions")}</Typography.Text>
            ) : (
              <Table<ScaleQuestion>
                rowKey="id"
                size="small"
                pagination={false}
                scroll={{ x: "max-content" }}
                dataSource={detail.questions}
                expandable={{
                  expandedRowRender: (q) => (
                    <Table
                      rowKey="id"
                      size="small"
                      pagination={false}
                      scroll={{ x: "max-content" }}
                      dataSource={q.options}
                      columns={[
                        { title: t("scales.col.optionCode"), dataIndex: "optionCode", width: 80 },
                        { title: t("scales.col.optionLabel"), dataIndex: "optionLabel" },
                        { title: t("scales.col.scoreValue"), dataIndex: "scoreValue", width: 80 },
                        { title: t("scales.col.sortNo"), dataIndex: "sortNo", width: 80 },
                        {
                          title: t("scales.exclusiveFlag"),
                          dataIndex: "exclusiveFlag",
                          width: 80,
                          render: (value: boolean) => value ? t("common.yes") : t("common.no")
                        },
                        {
                          title: t("scales.optionGroupCode"),
                          dataIndex: "optionGroupCode",
                          width: 120,
                          render: (value?: string | null) => value ?? "-"
                        },
                        {
                          title: t("scales.col.action"),
                          width: 90,
                          render: (_, option) => (
                            <Button type="link" disabled={detail.status !== "DRAFT"} onClick={() => openOptionEdit(option)}>
                              {t("scales.edit")}
                            </Button>
                          )
                        }
                      ]}
                    />
                  ),
                  rowExpandable: (q) => q.options.length > 0
                }}
                columns={[
                  { title: t("scales.col.questionNo"), dataIndex: "questionNo", width: 60 },
                  { title: t("scales.col.questionTitle"), dataIndex: "questionTitle" },
                  { title: t("scales.col.questionType"), dataIndex: "questionType", width: 120, render: (value: string) => renderQuestionType(value) },
                  { title: t("scales.col.dimensionId"), dataIndex: "dimensionId", width: 80 },
                  {
                    title: t("scales.col.required"),
                    dataIndex: "requiredFlag",
                    width: 60,
                    render: (value: boolean) => value ? t("common.yes") : t("common.no")
                  },
                  {
                    title: t("scales.questionConfig"),
                    key: "questionConfig",
                    render: (_, question) => renderQuestionConfig(question)
                  },
                  {
                    title: t("scales.col.action"),
                    width: 90,
                    render: (_, question) => (
                      <Button type="link" disabled={detail.status !== "DRAFT"} onClick={() => openQuestionEdit(question)}>
                        {t("scales.edit")}
                      </Button>
                    )
                  }
                ]}
              />
            )}

            <Divider orientation="left" plain>
              {t("scales.rulesTitle", { count: detail.resultRules.length })}
            </Divider>

            {detail.resultRules.length === 0 ? (
              <Typography.Text type="secondary">{t("scales.noRules")}</Typography.Text>
            ) : (
              <Table<ScaleResultRule>
                rowKey="id"
                size="small"
                pagination={false}
                scroll={{ x: "max-content" }}
                dataSource={detail.resultRules}
                columns={[
                  { title: t("scales.col.riskLevel"), dataIndex: "riskLevel", width: 100 },
                  { title: t("scales.col.scoreMin"), dataIndex: "scoreMin", width: 80 },
                  { title: t("scales.col.scoreMax"), dataIndex: "scoreMax", width: 80 },
                  { title: t("scales.scoreSource"), dataIndex: "scoreSource", width: 110, render: (value?: string) => value ?? "RAW_SCORE" },
                  { title: t("scales.normCode"), dataIndex: "normCode", width: 120, render: (value?: string | null) => value ?? "-" },
                  { title: t("scales.col.resultTitle"), dataIndex: "resultTitle" },
                  { title: t("scales.col.dimensionId"), dataIndex: "dimensionId", width: 80 }
                ]}
              />
            )}

            <Divider orientation="left" plain>
              {t("scales.normsTitle", { count: detail.norms.length })}
            </Divider>

            {normCoverageQuery.isError ? (
              <Alert type="warning" showIcon message={t("scales.normCoverageLoadError")} style={{ marginBottom: 12 }} />
            ) : normCoverage ? (
              <Space direction="vertical" size={12} style={{ width: "100%", marginBottom: 12 }}>
                <Descriptions bordered size="small" column={2} title={t("scales.normCoverage")}>
                  <Descriptions.Item label={t("scales.totalNormCount")}>{normCoverage.totalNormCount}</Descriptions.Item>
                  <Descriptions.Item label={t("scales.normStrategy")}>{normCoverage.normStrategy}</Descriptions.Item>
                  <Descriptions.Item label={t("scales.defaultNormGroup")}>{normCoverage.defaultNormGroup ?? "-"}</Descriptions.Item>
                  <Descriptions.Item label={t("scales.coveredDimensions")}>
                    {normCoverage.coveredDimensionCount}
                  </Descriptions.Item>
                  <Descriptions.Item label={t("scales.uncoveredDimensions")}>
                    {normCoverage.uncoveredDimensionCount}
                  </Descriptions.Item>
                </Descriptions>
                <Alert
                  type={normCoverage.uncoveredDimensionCount > 0 ? "warning" : "success"}
                  showIcon
                  message={t("scales.normCoverageSummary", {
                    covered: normCoverage.coveredDimensionCount,
                    uncovered: normCoverage.uncoveredDimensionCount,
                    total: detail.dimensions.length
                  })}
                />
                <Table<ScaleNormCoverage["items"][number]>
                  rowKey={(record) => `${record.dimensionId ?? "GLOBAL"}-${record.dimensionCode}`}
                  size="small"
                  pagination={false}
                  scroll={{ x: "max-content" }}
                  dataSource={normCoverage.items}
                  columns={[
                    { title: t("scales.normScope"), dataIndex: "dimensionName", render: (_, item) => `${item.dimensionName} (${item.dimensionCode})` },
                    { title: t("scales.normCount"), dataIndex: "normCount", width: 90 },
                    {
                      title: t("scales.globalNormReady"),
                      dataIndex: "hasGlobalNorm",
                      width: 120,
                      render: (value: boolean) => value ? <Tag color="green">{t("common.yes")}</Tag> : <Tag>{t("common.no")}</Tag>
                    },
                    {
                      title: t("scales.coverageStatus"),
                      key: "coverageStatus",
                      width: 140,
                      render: (_, item) => <Tag color={item.normCount > 0 ? "green" : "orange"}>{item.normCount > 0 ? t("scales.covered") : t("scales.uncovered")}</Tag>
                    }
                  ]}
                />
              </Space>
            ) : null}

            {detail.norms.length === 0 ? (
              <Typography.Text type="secondary">{t("scales.noNorms")}</Typography.Text>
            ) : (
              <Table<ScaleNorm>
                rowKey="id"
                size="small"
                pagination={false}
                scroll={{ x: "max-content" }}
                dataSource={detail.norms}
                columns={[
                  { title: t("scales.col.sortNo"), dataIndex: "sortNo", width: 70 },
                  { title: t("scales.normCode"), dataIndex: "normCode", width: 120 },
                  { title: t("scales.normName"), dataIndex: "normName", width: 160, render: (value?: string | null) => value ?? "-" },
                  { title: t("scales.normScope"), key: "scope", width: 180, render: (_, norm) => renderNormScope(norm) },
                  { title: t("scales.normProfile"), key: "profile", render: (_, norm) => renderNormTagList(norm) }
                ]}
              />
            )}

            <Divider orientation="left" plain>
              {t("scales.visualizationsTitle", { count: detail.visualizationConfigs?.length ?? 0 })}
            </Divider>
            <Space direction="vertical" size={12} style={{ width: "100%" }}>
              <Button disabled={detail.status !== "DRAFT"} onClick={openVisualizationEdit}>
                {t("scales.editVisualizations")}
              </Button>
              {(detail.visualizationConfigs ?? []).length === 0 ? (
                <Typography.Text type="secondary">{t("scales.noVisualizations")}</Typography.Text>
              ) : (
                <Table<ScaleVisualizationConfig>
                  rowKey="id"
                  size="small"
                  pagination={false}
                  scroll={{ x: "max-content" }}
                  dataSource={detail.visualizationConfigs}
                  columns={[
                    { title: t("scales.col.sortNo"), dataIndex: "sortNo", width: 70 },
                    { title: t("scales.visualization.chartTitle"), dataIndex: "chartTitle", width: 160 },
                    { title: t("scales.visualization.chartType"), dataIndex: "chartType", width: 170 },
                    { title: t("scales.visualization.viewScope"), dataIndex: "viewScope", width: 150 },
                    { title: t("scales.visualization.dataSource"), dataIndex: "dataSource", width: 190 },
                    {
                      title: t("scales.visualization.enabled"),
                      dataIndex: "enabled",
                      width: 90,
                      render: (value: boolean) => value ? <Tag color="green">{t("common.yes")}</Tag> : <Tag>{t("common.no")}</Tag>
                    }
                  ]}
                />
              )}
            </Space>
          </>
        ) : null}
      </Drawer>

      <Modal
        title={t("scales.editBasic")}
        open={basicEditOpen}
        onCancel={() => setBasicEditOpen(false)}
        onOk={() => void handleUpdateBasic()}
        confirmLoading={updateBasicMutation.isPending}
        destroyOnHidden
      >
        <Form form={basicEditForm} layout="vertical">
          <Form.Item label={t("scales.col.scaleName")} name="scaleName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label={t("scales.col.applicableTarget")} name="applicableTarget">
            <Input />
          </Form.Item>
          <Form.Item label={t("scales.description")} name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label={t("scales.reportTemplate")} name="reportTemplate">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label={t("scales.anonymousSupported")} name="anonymousSupported" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("scales.editVisualizations")}
        open={visualizationOpen}
        onCancel={() => setVisualizationOpen(false)}
        onOk={() => void handleUpdateVisualizations()}
        confirmLoading={updateVisualizationsMutation.isPending}
        width={960}
        destroyOnHidden
      >
        <Form form={visualizationForm} layout="vertical">
          <Form.List name="visualizations">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size={12} style={{ width: "100%" }}>
                {fields.map(({ key, name }) => (
                  <Card key={key} size="small">
                    <Space align="start" wrap>
                      <Form.Item name={[name, "chartTitle"]} label={t("scales.visualization.chartTitle")} rules={[{ required: true }]}>
                        <Input style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item name={[name, "chartType"]} label={t("scales.visualization.chartType")} rules={[{ required: true }]}>
                        <Select style={{ width: 180 }} options={chartTypeOptions(t)} />
                      </Form.Item>
                      <Form.Item name={[name, "viewScope"]} label={t("scales.visualization.viewScope")} rules={[{ required: true }]}>
                        <Select style={{ width: 150 }} options={viewScopeOptions(t)} />
                      </Form.Item>
                      <Form.Item name={[name, "dataSource"]} label={t("scales.visualization.dataSource")} rules={[{ required: true }]}>
                        <Select style={{ width: 210 }} options={dataSourceOptions(t)} />
                      </Form.Item>
                      <Form.Item name={[name, "sortNo"]} label={t("scales.col.sortNo")}>
                        <InputNumber min={0} style={{ width: 90 }} />
                      </Form.Item>
                      <Form.Item name={[name, "enabled"]} label={t("scales.visualization.enabled")} valuePropName="checked">
                        <Switch />
                      </Form.Item>
                      <Button danger type="link" onClick={() => remove(name)} style={{ marginTop: 30 }}>
                        {t("scales.delete")}
                      </Button>
                    </Space>
                    <Form.Item name={[name, "configJson"]} label={t("scales.visualization.configJson")}>
                      <Input.TextArea rows={2} placeholder='{"sort":"desc"}' />
                    </Form.Item>
                  </Card>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() =>
                    add({
                      chartTitle: t("scales.visualization.defaultTitle"),
                      chartType: "DIMENSION_BAR",
                      viewScope: "REPORT_DETAIL",
                      dataSource: "DIMENSION_SCORE",
                      configJson: "{}",
                      enabled: true,
                      sortNo: fields.length + 1
                    })
                  }
                  style={{ width: "100%" }}
                >
                  {t("scales.addVisualization")}
                </Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title={t("scales.editDimension")}
        open={Boolean(editingDimension)}
        onCancel={() => setEditingDimension(null)}
        onOk={() => void handleUpdateDimension()}
        confirmLoading={updateDimensionMutation.isPending}
        destroyOnHidden
      >
        <Form form={dimensionEditForm} layout="vertical">
          <Form.Item label={t("scales.col.dimensionName")} name="dimensionName" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label={t("scales.description")} name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label={t("scales.col.sortNo")} name="sortNo">
            <InputNumber style={{ width: "100%" }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("scales.editQuestion")}
        open={Boolean(editingQuestion)}
        onCancel={() => setEditingQuestion(null)}
        onOk={() => void handleUpdateQuestion()}
        confirmLoading={updateQuestionMutation.isPending}
        destroyOnHidden
        width={720}
      >
        <Form form={questionEditForm} layout="vertical">
          <Form.Item label={t("scales.col.dimensionId")} name="dimensionId">
            <Select allowClear options={detail?.dimensions.map((item) => ({ value: item.id, label: `${item.dimensionName} (${item.dimensionCode})` })) ?? []} />
          </Form.Item>
          <Form.Item label={t("scales.col.questionTitle")} name="questionTitle" rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label={t("scales.weightValue")} name="weightValue">
            <InputNumber min={0.0001} step={0.1} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("scales.col.sortNo")} name="sortNo">
            <InputNumber style={{ width: "100%" }} />
          </Form.Item>
          <Space>
            <Form.Item label={t("scales.col.required")} name="requiredFlag" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label={t("scales.reverseScoreFlag")} name="reverseScoreFlag" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title={t("scales.editOption")}
        open={Boolean(editingOption)}
        onCancel={() => setEditingOption(null)}
        onOk={() => void handleUpdateOption()}
        confirmLoading={updateOptionMutation.isPending}
        destroyOnHidden
      >
        <Form form={optionEditForm} layout="vertical">
          <Form.Item label={t("scales.col.optionLabel")} name="optionLabel" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label={t("scales.col.scoreValue")} name="scoreValue" rules={[{ required: true }]}>
            <InputNumber style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("scales.optionGroupCode")} name="optionGroupCode">
            <Input />
          </Form.Item>
          <Form.Item label={t("scales.col.sortNo")} name="sortNo">
            <InputNumber style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("scales.exclusiveFlag")} name="exclusiveFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("scales.createVersion")}
        open={versionOpen}
        onCancel={() => {
          setVersionOpen(false);
          versionForm.resetFields();
        }}
        onOk={() => void handleCreateVersion()}
        confirmLoading={createVersionMutation.isPending}
        destroyOnHidden
      >
        <Form form={versionForm} layout="vertical">
          <Alert type="info" showIcon message={t("scales.createVersionDesc")} style={{ marginBottom: 16 }} />
          <Form.Item
            label={t("scales.versionNo")}
            name="versionNo"
            rules={[{ required: true, message: t("scales.versionNoRequired") }]}
          >
            <Input placeholder={t("scales.versionNoPlaceholder")} />
          </Form.Item>
          <Form.Item label={t("scales.col.scaleName")} name="scaleName">
            <Input placeholder={detail?.scaleName ?? t("scales.scaleNamePlaceholder")} />
          </Form.Item>
          <Form.Item label={t("scales.description")} name="description">
            <Input.TextArea rows={3} placeholder={t("scales.descriptionPlaceholder")} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("scales.compareVersion")}
        open={diffOpen}
        onCancel={() => {
          setDiffOpen(false);
          setDiffResult(null);
          diffForm.resetFields();
        }}
        width={980}
        footer={[
          <Button key="close" onClick={() => setDiffOpen(false)}>
            {t("export.close")}
          </Button>,
          <Button key="compare" type="primary" onClick={() => void handleCompareVersion()} loading={diffMutation.isPending}>
            {t("scales.compareVersion")}
          </Button>
        ]}
        destroyOnHidden
      >
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Alert
            type="info"
            showIcon
            message={t("scales.compareVersionDesc")}
          />
            <Form form={diffForm} layout="vertical">
              <Form.Item
                label={t("scales.compareTargetId")}
                name="targetId"
                rules={[{ required: true, message: t("scales.compareTargetRequired") }]}
              >
                <Select
                  placeholder={t("scales.compareTargetPlaceholder")}
                  options={versions.map((version) => ({
                    value: version.id,
                    label: `${version.versionNo ?? version.id} (${version.status})`
                  }))}
                />
              </Form.Item>
            </Form>
          {diffResult ? (
            <>
              <Descriptions bordered size="small" column={3}>
                <Descriptions.Item label={t("scales.diff.from")}>
                  {diffResult.from.scaleName} #{diffResult.from.id} {diffResult.from.versionNo ?? "-"}
                </Descriptions.Item>
                <Descriptions.Item label={t("scales.diff.to")}>
                  {diffResult.to.scaleName} #{diffResult.to.id} {diffResult.to.versionNo ?? "-"}
                </Descriptions.Item>
                <Descriptions.Item label={t("scales.diff.summary")}>
                  <Space>
                    <Tag color="green">{t("scales.diff.added", { count: diffResult.summary.addedCount })}</Tag>
                    <Tag color="red">{t("scales.diff.removed", { count: diffResult.summary.removedCount })}</Tag>
                    <Tag color="blue">{t("scales.diff.modified", { count: diffResult.summary.modifiedCount })}</Tag>
                  </Space>
                </Descriptions.Item>
              </Descriptions>
              <Table<ScaleVersionDiffChange>
                rowKey={(record, index) => `${record.section}-${record.key}-${record.changeType}-${index ?? 0}`}
                size="small"
                pagination={{ pageSize: 8 }}
                dataSource={diffResult.changes}
                locale={{ emptyText: t("scales.diff.noChanges") }}
                columns={[
                  { title: t("scales.diff.section"), dataIndex: "section", width: 120 },
                  { title: t("scales.diff.key"), dataIndex: "key", width: 140 },
                  {
                    title: t("scales.diff.changeType"),
                    dataIndex: "changeType",
                    width: 120,
                    render: (value: string) => <Tag color={diffChangeColor(value)}>{value}</Tag>
                  },
                  {
                    title: t("scales.diff.before"),
                    dataIndex: "before",
                    render: (value) => renderDiffSnapshot(value)
                  },
                  {
                    title: t("scales.diff.after"),
                    dataIndex: "after",
                    render: (value) => renderDiffSnapshot(value)
                  }
                ]}
              />
            </>
          ) : null}
        </Space>
      </Modal>

      <Modal
        title={t("scales.importTitle")}
        open={importOpen}
        onCancel={resetImportState}
        width={920}
        destroyOnHidden
        footer={[
          <Button key="download" icon={<DownloadOutlined />} onClick={() => void handleDownloadTemplate()} loading={downloadTemplateMutation.isPending}>
            {t("scales.downloadTemplate")}
          </Button>,
          <Button key="parse" type="default" onClick={() => void handleParseImport()} loading={parseImportMutation.isPending}>
            {t("scales.parseImport")}
          </Button>,
          <Button
            key="confirm"
            type="primary"
            onClick={() => void handleConfirmImport()}
            loading={confirmImportMutation.isPending}
            disabled={!importResult || importResult.status !== "PARSED" || hasImportErrors}
          >
            {t("scales.confirmImport")}
          </Button>
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Alert type="info" showIcon message={t("scales.importDesc")} />
          <Alert
            type="warning"
            showIcon
            message={t("scales.importGuideTitle")}
            description={
              <Space direction="vertical" size={4}>
                <Typography.Text>{t("scales.importGuide.scale")}</Typography.Text>
                <Typography.Text>{t("scales.importGuide.questions")}</Typography.Text>
                <Typography.Text>{t("scales.importGuide.rules")}</Typography.Text>
              </Space>
            }
          />
          <div>
            <Typography.Text strong>{t("scales.selectImportFile")}</Typography.Text>
            <input
              type="file"
              accept=".xlsx"
              style={{ display: "block", marginTop: 8 }}
              onChange={(event) => {
                const nextFile = event.target.files?.[0] ?? null;
                setImportFile(nextFile);
                setImportResult(null);
              }}
            />
            {importFile ? (
              <Typography.Text type="secondary">
                {importFile.name}
              </Typography.Text>
            ) : null}
          </div>

          <div>
            <Typography.Text strong>{t("scales.importConfirmRemark")}</Typography.Text>
            <Input.TextArea
              rows={2}
              style={{ marginTop: 8 }}
              value={confirmRemark}
              onChange={(event) => setConfirmRemark(event.target.value)}
              placeholder={t("scales.importConfirmRemarkPlaceholder")}
            />
          </div>

          {importResult ? (
            <>
              <Descriptions bordered size="small" column={2} title={t("scales.importSummary")}>
                <Descriptions.Item label={t("scales.import.summary.scaleCode")}>{importResult.summary.scaleCode}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.summary.scaleName")}>{importResult.summary.scaleName}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.summary.dimensions")}>{importResult.summary.dimensionCount}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.summary.questions")}>{importResult.summary.questionCount}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.summary.options")}>{importResult.summary.optionCount}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.summary.rules")}>{importResult.summary.resultRuleCount}</Descriptions.Item>
              </Descriptions>

              {hasImportErrors ? (
                <Alert type="warning" showIcon message={t("scales.importErrorsBlocked")} />
              ) : null}

              <Table<ScaleImportIssue>
                rowKey={(record, index) => `${record.errorCode}-${index ?? 0}`}
                size="small"
                pagination={false}
                dataSource={importIssues}
                locale={{ emptyText: t("scales.importNoIssues") }}
                title={() => t("scales.importIssues")}
                columns={[
                  { title: t("scales.import.col.severity"), dataIndex: "severity", width: 90 },
                  { title: t("scales.import.col.sheet"), dataIndex: "sheetName", width: 120 },
                  { title: t("scales.import.col.row"), dataIndex: "rowNo", width: 80 },
                  { title: t("scales.import.col.column"), dataIndex: "columnName", width: 120 },
                  { title: t("scales.import.col.code"), dataIndex: "errorCode", width: 180 },
                  { title: t("scales.import.col.message"), dataIndex: "message" }
                ]}
              />
            </>
          ) : null}
        </Space>
      </Modal>

      <Drawer
        title={t("scales.importDetailTitle")}
        open={importDetailOpen}
        onClose={() => setImportDetailOpen(false)}
        width={860}
        loading={importDetailQuery.isLoading}
      >
        {importDetail ? (
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label={t("scales.import.col.fileName")}>{importDetail.fileName}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.col.status")}>{importDetail.status}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.col.createdScaleId")}>
                  {importDetail.createdScaleId ? (
                    <Button type="link" onClick={() => openCreatedScale(importDetail.createdScaleId!)}>
                      {importDetail.createdScaleId}
                    </Button>
                  ) : (
                    "-"
                  )}
                </Descriptions.Item>
                <Descriptions.Item label={t("scales.import.col.operatorUserId")}>{importDetail.operatorUserId}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.col.parsedAt")}>{importDetail.parsedAt ?? "-"}</Descriptions.Item>
                <Descriptions.Item label={t("scales.import.col.finishedAt")}>{importDetail.finishedAt ?? "-"}</Descriptions.Item>
            </Descriptions>

            <Descriptions bordered size="small" column={2} title={t("scales.importSummary")}>
              <Descriptions.Item label={t("scales.import.summary.scaleCode")}>{importDetail.summary.scaleCode ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("scales.import.summary.scaleName")}>{importDetail.summary.scaleName ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("scales.import.summary.dimensions")}>{importDetail.summary.dimensionCount}</Descriptions.Item>
              <Descriptions.Item label={t("scales.import.summary.questions")}>{importDetail.summary.questionCount}</Descriptions.Item>
              <Descriptions.Item label={t("scales.import.summary.options")}>{importDetail.summary.optionCount}</Descriptions.Item>
              <Descriptions.Item label={t("scales.import.summary.rules")}>{importDetail.summary.resultRuleCount}</Descriptions.Item>
            </Descriptions>

            <Table<ScaleImportIssue>
              rowKey={(record, index) => `${record.errorCode}-${index ?? 0}`}
              size="small"
              pagination={false}
              dataSource={importDetailIssues}
              locale={{ emptyText: t("scales.importNoIssues") }}
              columns={[
                { title: t("scales.import.col.severity"), dataIndex: "severity", width: 90 },
                { title: t("scales.import.col.sheet"), dataIndex: "sheetName", width: 120 },
                { title: t("scales.import.col.row"), dataIndex: "rowNo", width: 80 },
                { title: t("scales.import.col.column"), dataIndex: "columnName", width: 120 },
                { title: t("scales.import.col.code"), dataIndex: "errorCode", width: 180 },
                { title: t("scales.import.col.message"), dataIndex: "message" }
              ]}
            />
          </Space>
        ) : null}
      </Drawer>

      <Modal
        title={t("scales.batchAddDimensions")}
        open={dimOpen}
        onCancel={() => {
          setDimOpen(false);
          dimForm.resetFields();
        }}
        onOk={() => void handleAddDimensions()}
        confirmLoading={batchDimMutation.isPending}
        width={600}
        destroyOnHidden
      >
        <Form form={dimForm} layout="vertical">
          <Form.List name="dimensions" initialValue={[{ dimensionCode: "", dimensionName: "", sortNo: 0 }]}>
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Space key={key} align="start" style={{ display: "flex", marginBottom: 8 }} wrap>
                    <Form.Item
                      {...restField}
                      name={[name, "dimensionCode"]}
                      rules={[{ required: true, message: t("scales.dimensionCodeRequired") }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder={t("scales.col.dimensionCode")} style={{ width: 130 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, "dimensionName"]}
                      rules={[{ required: true, message: t("scales.dimensionNameRequired") }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder={t("scales.col.dimensionName")} style={{ width: 150 }} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, "sortNo"]} style={{ marginBottom: 0 }}>
                      <InputNumber placeholder={t("scales.sortPlaceholder")} style={{ width: 80 }} min={0} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, "description"]} style={{ marginBottom: 0 }}>
                      <Input placeholder={t("scales.optionalDescription")} style={{ width: 160 }} />
                    </Form.Item>
                    {fields.length > 1 ? (
                      <Button type="link" danger onClick={() => remove(name)}>
                        {t("scales.delete")}
                      </Button>
                    ) : null}
                  </Space>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => add({ dimensionCode: "", dimensionName: "", sortNo: fields.length })}
                  style={{ width: "100%" }}
                >
                  {t("scales.addRow")}
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title={t("scales.batchAddQuestions")}
        open={questionOpen}
        onCancel={() => { setQuestionOpen(false); questionForm.resetFields(); }}
        onOk={() => void handleAddQuestions()}
        confirmLoading={batchQuestionMutation.isPending}
        width={780}
        destroyOnHidden
      >
        <Form form={questionForm} layout="vertical">
          <Form.List
            name="questions"
            initialValue={[
              {
                questionNo: 1,
                questionTitle: "",
                questionType: "SINGLE_CHOICE",
                requiredFlag: true,
                reverseScoreFlag: false,
                weightValue: 1,
                sortNo: 1,
                options: [OPTION_DEFAULT]
              }
            ]}
          >
            {(qFields, { add: addQ, remove: removeQ }) => (
              <>
                {qFields.map(({ key: qKey, name: qName }) => (
                  <div key={qKey} style={{ border: "1px solid #f0f0f0", borderRadius: 6, padding: 12, marginBottom: 12 }}>
                    <Space style={{ marginBottom: 8 }} wrap>
                      <Form.Item name={[qName, "questionNo"]} label={t("scales.col.questionNo")} style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                        <InputNumber min={1} style={{ width: 70 }} />
                      </Form.Item>
                      <Form.Item
                        name={[qName, "questionTitle"]}
                        label={t("scales.col.questionTitle")}
                        style={{ marginBottom: 0 }}
                        rules={[{ required: true, message: t("scales.questionTitleRequired") }]}
                      >
                        <Input placeholder={t("scales.questionTitleRequired")} style={{ width: 260 }} />
                      </Form.Item>
                      <Form.Item name={[qName, "questionType"]} label={t("scales.col.questionType")} style={{ marginBottom: 0 }}>
                        <Select
                          style={{ width: 150 }}
                          onChange={(value) => {
                            if (QUESTION_TYPES_WITH_OPTIONS.has(value)) {
                              const options = questionForm.getFieldValue(["questions", qName, "options"]) ?? [];
                              if (options.length === 0) {
                                questionForm.setFieldValue(["questions", qName, "options"], [OPTION_DEFAULT]);
                              }
                              if (value === "TEXT_WITH_OPTION") {
                                questionForm.setFieldValue(["questions", qName, "textInputEnabled"], true);
                              }
                              return;
                            }
                            questionForm.setFieldValue(["questions", qName, "options"], []);
                          }}
                          options={[
                            { label: t("scales.questionType.singleChoice"), value: "SINGLE_CHOICE" },
                            { label: t("scales.questionType.multiSelect"), value: "MULTI_SELECT" },
                            { label: t("scales.questionType.slider"), value: "SLIDER" },
                            { label: t("scales.questionType.matrix"), value: "MATRIX" },
                            { label: t("scales.questionType.textWithOption"), value: "TEXT_WITH_OPTION" },
                            { label: t("scales.questionType.text"), value: "TEXT" }
                          ]}
                        />
                      </Form.Item>
                      <Form.Item name={[qName, "dimensionId"]} label={t("scales.col.dimensionId")} style={{ marginBottom: 0 }}>
                        <InputNumber min={1} placeholder={t("scales.optional")} style={{ width: 90 }} />
                      </Form.Item>
                      <Form.Item name={[qName, "sortNo"]} label={t("scales.col.sortNo")} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} style={{ width: 80 }} />
                      </Form.Item>
                      <Form.Item name={[qName, "weightValue"]} label={t("scales.weightValue")} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} step={0.1} style={{ width: 90 }} />
                      </Form.Item>
                      <Form.Item name={[qName, "requiredFlag"]} label={t("scales.col.required")} valuePropName="checked" style={{ marginBottom: 0 }}>
                        <Switch size="small" />
                      </Form.Item>
                      <Form.Item name={[qName, "reverseScoreFlag"]} label={t("scales.reverseScoreFlag")} valuePropName="checked" style={{ marginBottom: 0 }}>
                        <Switch size="small" />
                      </Form.Item>
                      {qFields.length > 1 ? (
                        <Button type="link" danger onClick={() => removeQ(qName)} style={{ marginTop: 22 }}>
                          {t("scales.deleteQuestion")}
                        </Button>
                      ) : null}
                    </Space>
                    <Form.Item noStyle shouldUpdate={(prev, curr) => prev.questions?.[qName]?.questionType !== curr.questions?.[qName]?.questionType}>
                      {() => {
                        const questionType = questionForm.getFieldValue(["questions", qName, "questionType"]);
                        const hasOptions = QUESTION_TYPES_WITH_OPTIONS.has(questionType);

                        return (
                          <>
                            {questionType === "MULTI_SELECT" ? (
                              <Form.Item name={[qName, "optionSelectionLimit"]} label={t("scales.optionSelectionLimit")} style={{ maxWidth: 160 }}>
                                <InputNumber min={1} placeholder={t("scales.optional")} style={{ width: "100%" }} />
                              </Form.Item>
                            ) : null}
                            {questionType === "SLIDER" ? (
                              <Space wrap>
                                <Form.Item name={[qName, "sliderMin"]} label={t("scales.sliderMin")}>
                                  <InputNumber style={{ width: 110 }} />
                                </Form.Item>
                                <Form.Item name={[qName, "sliderMax"]} label={t("scales.sliderMax")}>
                                  <InputNumber style={{ width: 110 }} />
                                </Form.Item>
                                <Form.Item name={[qName, "sliderStep"]} label={t("scales.sliderStep")}>
                                  <InputNumber min={0.1} step={0.1} style={{ width: 110 }} />
                                </Form.Item>
                              </Space>
                            ) : null}
                            {questionType === "MATRIX" ? (
                              <Space wrap>
                                <Form.Item
                                  name={[qName, "matrixGroupCode"]}
                                  label={t("scales.matrixGroupCode")}
                                  rules={[{ required: true, message: t("scales.matrixGroupRequired") }]}
                                >
                                  <Input placeholder={t("scales.matrixGroupCode")} style={{ width: 150 }} />
                                </Form.Item>
                                <Form.Item
                                  name={[qName, "rowCode"]}
                                  label={t("scales.rowCode")}
                                  rules={[{ required: true, message: t("scales.rowCodeRequired") }]}
                                >
                                  <Input placeholder={t("scales.rowCode")} style={{ width: 120 }} />
                                </Form.Item>
                                <Form.Item
                                  name={[qName, "columnCode"]}
                                  label={t("scales.columnCode")}
                                  rules={[{ required: true, message: t("scales.columnCodeRequired") }]}
                                >
                                  <Input placeholder={t("scales.columnCode")} style={{ width: 120 }} />
                                </Form.Item>
                              </Space>
                            ) : null}
                            {questionType === "TEXT_WITH_OPTION" ? (
                              <Space wrap>
                                <Form.Item name={[qName, "textInputEnabled"]} label={t("scales.textInputEnabled")} valuePropName="checked">
                                  <Switch size="small" />
                                </Form.Item>
                                <Form.Item name={[qName, "textInputPlaceholder"]} label={t("scales.textInputPlaceholder")}>
                                  <Input placeholder={t("scales.optional")} style={{ width: 220 }} />
                                </Form.Item>
                              </Space>
                            ) : null}
                            {hasOptions ? (
                              <>
                                <Typography.Text type="secondary" style={{ fontSize: 12 }}>{t("scales.options")}</Typography.Text>
                                <Form.List name={[qName, "options"]}>
                                  {(oFields, { add: addO, remove: removeO }) => (
                                    <>
                                      {oFields.map(({ key: oKey, name: oName }) => (
                                        <Space key={oKey} style={{ display: "flex", marginBottom: 4 }} wrap>
                                          <Form.Item name={[oName, "optionCode"]} style={{ marginBottom: 0 }} rules={[{ required: true, message: t("scales.codeRequired") }]}>
                                            <Input placeholder={t("scales.optionCodePlaceholder")} style={{ width: 80 }} />
                                          </Form.Item>
                                          <Form.Item name={[oName, "optionLabel"]} style={{ marginBottom: 0 }} rules={[{ required: true, message: t("scales.contentRequired") }]}>
                                            <Input placeholder={t("scales.col.optionLabel")} style={{ width: 180 }} />
                                          </Form.Item>
                                          <Form.Item name={[oName, "scoreValue"]} style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                                            <InputNumber placeholder={t("scales.col.scoreValue")} style={{ width: 80 }} />
                                          </Form.Item>
                                          <Form.Item name={[oName, "sortNo"]} style={{ marginBottom: 0 }}>
                                            <InputNumber min={0} placeholder={t("scales.col.sortNo")} style={{ width: 80 }} />
                                          </Form.Item>
                                          <Form.Item name={[oName, "exclusiveFlag"]} valuePropName="checked" style={{ marginBottom: 0 }}>
                                            <Switch size="small" checkedChildren={t("scales.exclusiveFlag")} />
                                          </Form.Item>
                                          <Form.Item name={[oName, "optionGroupCode"]} style={{ marginBottom: 0 }}>
                                            <Input placeholder={t("scales.optionGroupCode")} style={{ width: 120 }} />
                                          </Form.Item>
                                          {oFields.length > 1 ? (
                                            <Button type="link" danger size="small" onClick={() => removeO(oName)}>{t("scales.delete")}</Button>
                                          ) : null}
                                        </Space>
                                      ))}
                                      <Button
                                        type="dashed"
                                        size="small"
                                        icon={<PlusOutlined />}
                                        onClick={() => addO({ optionCode: "", optionLabel: "", scoreValue: 0, sortNo: oFields.length })}
                                      >
                                        {t("scales.addOption")}
                                      </Button>
                                    </>
                                  )}
                                </Form.List>
                              </>
                            ) : (
                              <Typography.Text type="secondary" style={{ fontSize: 12 }}>{t("scales.optionsNotRequired")}</Typography.Text>
                            )}
                          </>
                        );
                      }}
                    </Form.Item>
                  </div>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() =>
                    addQ({
                      questionNo: qFields.length + 1,
                      questionTitle: "",
                      questionType: "SINGLE_CHOICE",
                      requiredFlag: true,
                      reverseScoreFlag: false,
                      weightValue: 1,
                      sortNo: qFields.length + 1,
                      options: [OPTION_DEFAULT]
                    })
                  }
                  style={{ width: "100%" }}
                >
                  {t("scales.addQuestion")}
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title={t("scales.batchAddRules")}
        open={ruleOpen}
        onCancel={() => { setRuleOpen(false); ruleForm.resetFields(); }}
        onOk={() => void handleAddRules()}
        confirmLoading={batchRuleMutation.isPending}
        width={700}
        destroyOnHidden
      >
        <Form form={ruleForm} layout="vertical">
          <Form.List name="resultRules" initialValue={[{ riskLevel: "NORMAL", scoreMin: 0, scoreMax: 100, scoreSource: "RAW_SCORE" }]}>
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <Space key={key} align="start" style={{ display: "flex", marginBottom: 8 }} wrap>
                    <Form.Item name={[name, "riskLevel"]} label={t("scales.col.riskLevel")} style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                      <Select
                        style={{ width: 110 }}
                        options={[
                          { label: t("scales.risk.normal"), value: "NORMAL" },
                          { label: t("scales.risk.low"), value: "LOW" },
                          { label: t("scales.risk.moderate"), value: "MODERATE" },
                          { label: t("scales.risk.high"), value: "HIGH" }
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name={[name, "scoreMin"]} label={t("scales.col.scoreMin")} style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                      <InputNumber style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item name={[name, "scoreMax"]} label={t("scales.col.scoreMax")} style={{ marginBottom: 0 }} rules={[{ required: true }]}>
                      <InputNumber style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item name={[name, "scoreSource"]} label={t("scales.scoreSource")} style={{ marginBottom: 0 }}>
                      <Select
                        style={{ width: 120 }}
                        options={[
                          { label: "RAW_SCORE", value: "RAW_SCORE" },
                          { label: "Z_SCORE", value: "Z_SCORE" },
                          { label: "T_SCORE", value: "T_SCORE" }
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name={[name, "normCode"]} label={t("scales.normCode")} style={{ marginBottom: 0 }}>
                      <Input placeholder={t("scales.optional")} style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item name={[name, "resultTitle"]} label={t("scales.col.resultTitle")} style={{ marginBottom: 0 }}>
                      <Input placeholder={t("scales.optional")} style={{ width: 140 }} />
                    </Form.Item>
                    <Form.Item name={[name, "dimensionId"]} label={t("scales.col.dimensionId")} style={{ marginBottom: 0 }}>
                      <InputNumber min={1} placeholder={t("scales.optional")} style={{ width: 80 }} />
                    </Form.Item>
                    {fields.length > 1 ? (
                      <Button type="link" danger onClick={() => remove(name)} style={{ marginTop: 22 }}>{t("scales.delete")}</Button>
                    ) : null}
                  </Space>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => add({ riskLevel: "NORMAL", scoreMin: 0, scoreMax: 100, scoreSource: "RAW_SCORE" })}
                  style={{ width: "100%" }}
                >
                  {t("scales.addRule")}
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title={t("scales.batchAddNorms")}
        open={normOpen}
        onCancel={() => { setNormOpen(false); normForm.resetFields(); }}
        onOk={() => void handleAddNorms()}
        confirmLoading={batchNormMutation.isPending}
        width={860}
        destroyOnHidden
      >
        <Form form={normForm} layout="vertical">
          <Form.List name="norms" initialValue={[{ normCode: "", sortNo: 1 }]}>
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name }) => (
                  <div key={key} style={{ border: "1px solid #f0f0f0", borderRadius: 6, padding: 12, marginBottom: 12 }}>
                    <Space align="start" style={{ display: "flex", marginBottom: 8 }} wrap>
                      <Form.Item
                        name={[name, "normCode"]}
                        label={t("scales.normCode")}
                        style={{ marginBottom: 0 }}
                        rules={[{ required: true, message: t("scales.normCodeRequired") }]}
                      >
                        <Input placeholder="GLOBAL_A" style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item name={[name, "normName"]} label={t("scales.normName")} style={{ marginBottom: 0 }}>
                        <Input placeholder={t("scales.optional")} style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item name={[name, "dimensionId"]} label={t("scales.col.dimensionId")} style={{ marginBottom: 0 }}>
                        <InputNumber min={1} placeholder={t("scales.optional")} style={{ width: 90 }} />
                      </Form.Item>
                      <Form.Item name={[name, "sortNo"]} label={t("scales.col.sortNo")} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} style={{ width: 80 }} />
                      </Form.Item>
                      {fields.length > 1 ? (
                        <Button type="link" danger onClick={() => remove(name)} style={{ marginTop: 22 }}>
                          {t("scales.delete")}
                        </Button>
                      ) : null}
                    </Space>
                    <Space align="start" style={{ display: "flex", marginBottom: 8 }} wrap>
                      <Form.Item name={[name, "applicableTarget"]} label={t("scales.col.applicableTarget")} style={{ marginBottom: 0 }}>
                        <Input placeholder={t("scales.optional")} style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item name={[name, "ageMin"]} label={t("scales.ageMin")} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} style={{ width: 90 }} />
                      </Form.Item>
                      <Form.Item name={[name, "ageMax"]} label={t("scales.ageMax")} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} style={{ width: 90 }} />
                      </Form.Item>
                      <Form.Item name={[name, "gender"]} label={t("scales.gender")} style={{ marginBottom: 0 }}>
                        <Input placeholder={t("scales.optional")} style={{ width: 110 }} />
                      </Form.Item>
                      <Form.Item name={[name, "orgType"]} label={t("scales.orgType")} style={{ marginBottom: 0 }}>
                        <Input placeholder={t("scales.optional")} style={{ width: 130 }} />
                      </Form.Item>
                    </Space>
                    <Space align="start" style={{ display: "flex" }} wrap>
                      <Form.Item name={[name, "meanScore"]} label={t("scales.meanScore")} style={{ marginBottom: 0 }}>
                        <InputNumber step={0.1} style={{ width: 110 }} />
                      </Form.Item>
                      <Form.Item name={[name, "stdDeviation"]} label={t("scales.stdDeviation")} style={{ marginBottom: 0 }}>
                        <InputNumber min={0.0001} step={0.1} style={{ width: 110 }} />
                      </Form.Item>
                      <Form.Item name={[name, "tScoreMean"]} label={t("scales.tScoreMean")} style={{ marginBottom: 0 }}>
                        <InputNumber step={0.1} style={{ width: 110 }} />
                      </Form.Item>
                      <Form.Item name={[name, "tScoreStdDeviation"]} label={t("scales.tScoreStdDeviation")} style={{ marginBottom: 0 }}>
                        <InputNumber min={0.0001} step={0.1} style={{ width: 120 }} />
                      </Form.Item>
                    </Space>
                  </div>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => add({ normCode: "", sortNo: fields.length + 1 })}
                  style={{ width: "100%" }}
                >
                  {t("scales.addNorm")}
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title={t("scales.create")}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void handleCreate()}
        confirmLoading={createScaleMutation.isPending}
        destroyOnHidden
      >
        <Form
          form={createForm}
          layout="vertical"
          initialValues={{ versionNo: "v1", scoreMethod: "SIMPLE_SUM", scoreCoefficient: 1, anonymousSupported: false }}
        >
          <Form.Item label={t("scales.col.scaleCode")} name="scaleCode" rules={[{ required: true, message: t("scales.scaleCodeRequired") }]}>
            <Input placeholder={t("scales.scaleCodePlaceholder")} />
          </Form.Item>
          <Form.Item label={t("scales.col.scaleName")} name="scaleName" rules={[{ required: true, message: t("scales.scaleNameRequired") }]}>
            <Input placeholder={t("scales.scaleNamePlaceholder")} />
          </Form.Item>
          <Form.Item label={t("scales.col.applicableTarget")} name="applicableTarget">
            <Input placeholder={t("scales.applicableTargetPlaceholder")} />
          </Form.Item>
          <Form.Item label={t("scales.versionNo")} name="versionNo">
            <Input placeholder="v1" />
          </Form.Item>
          <Form.Item label={t("scales.col.scoreMethod")} name="scoreMethod" rules={[{ required: true }]}>
            <Select
              options={[
                { label: t("scales.scoreMethod.simple"), value: "SIMPLE_SUM" },
                { label: t("scales.scoreMethod.reverse"), value: "REVERSE_SUM" },
                { label: t("scales.scoreMethod.weighted"), value: "WEIGHTED_SUM" }
              ]}
            />
          </Form.Item>
          <Form.Item
            label={t("scales.scoreCoefficient")}
            name="scoreCoefficient"
            tooltip={t("scales.scoreCoefficientTooltip")}
          >
            <InputNumber min={0.0001} step={0.01} precision={4} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("scales.description")} name="description">
            <Input.TextArea rows={3} placeholder={t("scales.descriptionPlaceholder")} />
          </Form.Item>
          <Form.Item label={t("scales.reportTemplate")} name="reportTemplate">
            <Input.TextArea rows={4} placeholder={t("scales.reportTemplatePlaceholder")} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
