import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import { Alert, Button, Card, Checkbox, Empty, Form, Grid, Input, Progress, Radio, Result, Slider, Space, Spin, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  fetchMyTasks,
  fetchTaskQuestions,
  saveAnswerSheet,
  submitAnswerSheet,
  type AnswerItemRequest,
  type TaskQuestionItem
} from "../features/my-tasks/api";
import { useI18n } from "../i18n/provider";

type FormValues = Record<string, string | number | number[] | undefined>;
const LOCAL_DRAFT_PREFIX = "psy-respondent-task-draft";
const LOCAL_COMPLETED_PREFIX = "psy-respondent-task-completed";
type DraftSnapshot = {
  answers: FormValues;
  currentIndex: number;
};

type DraftMeta = {
  answerSheetId?: number;
  versionNo?: number;
};

type SubmitState = "idle" | "validating" | "submitting" | "failed" | "succeeded";

function getDraftStorageKey(taskId: string) {
  return `${LOCAL_DRAFT_PREFIX}:${taskId}`;
}

function getCompletedStorageKey(taskId: string) {
  return `${LOCAL_COMPLETED_PREFIX}:${taskId}`;
}

function clampQuestionIndex(index: number, questionCount: number) {
  if (questionCount <= 0) {
    return 0;
  }
  return Math.min(Math.max(index, 0), questionCount);
}

function isDraftSnapshot(value: unknown): value is DraftSnapshot {
  return Boolean(
    value &&
      typeof value === "object" &&
      "answers" in value &&
      "currentIndex" in value &&
      typeof (value as { currentIndex?: unknown }).currentIndex === "number"
  );
}

function toAnswerItems(questions: TaskQuestionItem[], values: FormValues): AnswerItemRequest[] {
  const answers: AnswerItemRequest[] = [];
  for (const question of questions) {
    const value = values[`question-${question.questionId}`];
    if (question.questionType === "TEXT") {
      if (value === undefined || value === null || value === "") {
        continue;
      }
      answers.push({
        questionId: question.questionId,
        answerText: String(value)
      });
      continue;
    }
    if (question.questionType === "TEXT_WITH_OPTION") {
      if (value === undefined || value === null || value === "") {
        continue;
      }
      const textValue = values[`question-${question.questionId}-text`];
      answers.push({
        questionId: question.questionId,
        optionId: Number(value),
        answerText: textValue ? String(textValue) : undefined
      });
      continue;
    }
    if (question.questionType === "MULTI_SELECT") {
      if (!Array.isArray(value) || value.length === 0) {
        continue;
      }
      value.forEach((selected) => {
        answers.push({
          questionId: question.questionId,
          optionId: Number(selected)
        });
      });
      continue;
    }
    if (question.questionType === "SLIDER") {
      if (value === undefined || value === null || value === "") {
        continue;
      }
      answers.push({
        questionId: question.questionId,
        answerValue: Number(value)
      });
      continue;
    }
    if (value === undefined || value === null || value === "") {
      continue;
    }
    answers.push({
      questionId: question.questionId,
      optionId: Number(value)
    });
  }
  return answers;
}

function findFirstIncompleteQuestion(questions: TaskQuestionItem[], values: FormValues) {
  for (let index = 0; index < questions.length; index += 1) {
    const question = questions[index];
    const value = values[`question-${question.questionId}`];

    if (question.requiredFlag) {
      if (question.questionType === "MULTI_SELECT") {
        if (!Array.isArray(value) || value.length === 0) {
          return { index, messageKey: "taskQuestion.requiredMessage" as const };
        }
      } else if (question.questionType === "TEXT") {
        if (typeof value !== "string" || value.trim().length === 0) {
          return { index, messageKey: "taskQuestion.requiredMessage" as const };
        }
      } else if (value === undefined || value === null || value === "") {
        return { index, messageKey: "taskQuestion.requiredMessage" as const };
      }
    }

    if (question.questionType === "TEXT_WITH_OPTION" && question.textInputEnabled && value) {
      const textValue = values[`question-${question.questionId}-text`];
      if (typeof textValue !== "string" || textValue.trim().length === 0) {
        return { index, messageKey: "taskQuestion.textRequired" as const };
      }
    }
  }

  return null;
}

function createSubmitToken() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `submit-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function isVersionConflict(error: unknown) {
  const response = (error as AxiosError<{ code?: string }>)?.response;
  return response?.data?.code === "ANSWER_SHEET_VERSION_CONFLICT";
}

function isTaskAlreadySubmitted(error: unknown) {
  const response = (error as AxiosError<{ code?: string }>)?.response;
  return response?.data?.code === "TASK_ALREADY_SUBMITTED";
}

function isTimeoutError(error: unknown) {
  const axiosError = error as AxiosError<{ message?: string }>;
  return axiosError.code === "ECONNABORTED" || axiosError.message?.toLowerCase().includes("timeout") === true;
}

function getErrorMessage(error: unknown, fallbackMessage: string) {
  const axiosError = error as AxiosError<{ message?: string }>;
  return axiosError.response?.data?.message || axiosError.message || fallbackMessage;
}

export function TaskQuestionPage() {
  const { t } = useI18n();
  const { taskId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();
  const [currentIndex, setCurrentIndex] = useState(0);
  const [draftMeta, setDraftMeta] = useState<DraftMeta>({});
  const [submitToken, setSubmitToken] = useState<string | null>(null);
  const [completedLocally, setCompletedLocally] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitState, setSubmitState] = useState<SubmitState>("idle");
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const questionQuery = useQuery({
    queryKey: ["my-task-questions", taskId],
    queryFn: () => fetchTaskQuestions(Number(taskId)),
    enabled: Boolean(taskId)
  });
  const tasksQuery = useQuery({
    queryKey: ["my-tasks"],
    queryFn: fetchMyTasks
  });

  const saveMutation = useMutation({
    mutationFn: saveAnswerSheet,
    onSuccess: (result) => {
      setDraftMeta({
        answerSheetId: result.answerSheetId,
        versionNo: result.versionNo
      });
      setSubmitError(null);
      setSubmitState("idle");
      message.success(t("taskQuestion.draftSaved"));
    },
    onError: async (error) => {
      if (!isVersionConflict(error)) {
        const nextError = getErrorMessage(error, t("taskQuestion.loadError"));
        setSubmitError(nextError);
        setSubmitState("failed");
        message.error(nextError);
        return;
      }
      setSubmitError(t("taskQuestion.versionConflict"));
      setSubmitState("failed");
      message.warning(t("taskQuestion.versionConflict"));
      await questionQuery.refetch();
    }
  });

  const submitMutation = useMutation({
    mutationFn: submitAnswerSheet,
    onSuccess: (result) => {
      setDraftMeta({
        answerSheetId: result.answerSheetId,
        versionNo: result.versionNo
      });
      setSubmitToken(null);
      setSubmitError(null);
      setSubmitState("succeeded");
      if (taskId && typeof window !== "undefined") {
        window.localStorage.setItem(getCompletedStorageKey(taskId), "1");
        setCompletedLocally(true);
      }
      void queryClient.invalidateQueries({ queryKey: ["my-tasks"] });
      void queryClient.invalidateQueries({ queryKey: ["my-task-questions", taskId] });
      void queryClient.invalidateQueries({ queryKey: ["reports"] });
      message.success(t("taskQuestion.submitted", { riskLevel: result.riskLevel }));
      navigate(`/reports/${result.reportId}?resultId=${result.resultId}&taskId=${taskId ?? ""}`);
    },
    onError: async (error) => {
      if (isTimeoutError(error)) {
        await Promise.all([questionQuery.refetch(), tasksQuery.refetch()]);
        setSubmitError(submitTimeoutMessage);
        setSubmitState("failed");
        message.warning(submitTimeoutMessage);
        return;
      }
      setSubmitToken(null);
      if (!isVersionConflict(error)) {
        const nextError = getErrorMessage(error, t("taskQuestion.loadError"));
        setSubmitError(nextError);
        setSubmitState("failed");
        message.error(nextError);
        return;
      }
      setSubmitError(t("taskQuestion.versionConflict"));
      setSubmitState("failed");
      message.warning(t("taskQuestion.versionConflict"));
      await questionQuery.refetch();
    }
  });

  const payload = questionQuery.data;
  const currentTask = (tasksQuery.data ?? []).find((item) => String(item.taskId) === String(taskId));
  const questions = payload?.questions ?? [];
  const watchedValues = (Form.useWatch([], form) as FormValues | undefined) ?? {};
  const requiredCount = useMemo(() => questions.filter((item) => item.requiredFlag).length, [questions]);
  const answeredCount = toAnswerItems(questions, watchedValues).length;
  const currentQuestion = questions[currentIndex];
  const isReviewStep = currentIndex >= questions.length;
  const progressStep = questions.length > 0 ? Math.min(currentIndex + 1, questions.length) : 0;
  const progressPercent = questions.length > 0 ? Math.round((progressStep / questions.length) * 100) : 0;
  const currentAnswerValue = currentQuestion ? watchedValues[`question-${currentQuestion.questionId}`] : undefined;
  const currentAnswerText = currentQuestion ? watchedValues[`question-${currentQuestion.questionId}-text`] : undefined;
  const cardRadius = isMobile ? 18 : 16;
  const cardShadow = isMobile ? "0 16px 40px rgba(19, 51, 78, 0.12)" : "0 8px 18px rgba(19, 51, 78, 0.08)";
  const validatingMessage = t("taskQuestion.validating");
  const submittingMessage = t("taskQuestion.submitting");
  const submitSucceededMessage = t("taskQuestion.submitSucceeded");
  const submitTimeoutMessage = t("taskQuestion.submitTimeout");

  useEffect(() => {
    setDraftMeta({
      answerSheetId: payload?.draftAnswerSheetId,
      versionNo: payload?.draftVersionNo
    });
  }, [payload?.draftAnswerSheetId, payload?.draftVersionNo]);

  useEffect(() => {
    if (!taskId || typeof window === "undefined" || !payload) {
      return;
    }
    const savedDraft = window.localStorage.getItem(getDraftStorageKey(taskId));
    if (!savedDraft) {
      return;
    }
    try {
      const parsed = JSON.parse(savedDraft) as unknown;
      const answers = isDraftSnapshot(parsed) ? parsed.answers : (parsed as FormValues);
      const nextIndex = isDraftSnapshot(parsed) ? parsed.currentIndex : 0;
      form.setFieldsValue(answers);
      setCurrentIndex(clampQuestionIndex(nextIndex, payload.questions.length));
    } catch {
      window.localStorage.removeItem(getDraftStorageKey(taskId));
    }
  }, [form, payload, taskId]);

  useEffect(() => {
    if (!taskId || typeof window === "undefined") {
      return;
    }
    setCompletedLocally(window.localStorage.getItem(getCompletedStorageKey(taskId)) === "1");
  }, [taskId]);

  useEffect(() => {
    if (!payload?.completedFlag || !payload.completedReportId) {
      return;
    }
    if (taskId && typeof window !== "undefined") {
      window.localStorage.setItem(getCompletedStorageKey(taskId), "1");
      setCompletedLocally(true);
    }
    navigate(
      `/reports/${payload.completedReportId}?resultId=${payload.completedResultId ?? ""}&taskId=${taskId ?? ""}`,
      { replace: true }
    );
  }, [navigate, payload?.completedFlag, payload?.completedReportId, payload?.completedResultId, taskId]);

  useEffect(() => {
    if (!taskId || typeof window === "undefined" || questions.length === 0) {
      return;
    }
    const values = form.getFieldsValue(true);
    try {
      window.localStorage.setItem(
        getDraftStorageKey(taskId),
        JSON.stringify({ answers: values, currentIndex } satisfies DraftSnapshot)
      );
    } catch {
      // ignore local storage write issues for draft caching
    }
  }, [currentIndex, form, questions.length, taskId]);

  const handleSave = async () => {
    if (!payload || !payload.allowSaveFlag) {
      return;
    }
    setSubmitError(null);
    setSubmitState("idle");
    const values = form.getFieldsValue(true);
    await saveMutation.mutateAsync({
      taskId: payload.taskId,
      scaleId: payload.scaleId,
      answerSheetId: draftMeta.answerSheetId,
      versionNo: draftMeta.versionNo,
      answers: toAnswerItems(questions, values)
    });
    if (taskId && typeof window !== "undefined") {
      window.localStorage.setItem(getDraftStorageKey(taskId), JSON.stringify({ answers: values, currentIndex }));
    }
  };

  const handleNext = async () => {
    if (!currentQuestion) {
      return;
    }
    const fieldNames = [`question-${currentQuestion.questionId}`];
    if (currentQuestion.questionType === "TEXT_WITH_OPTION" && currentQuestion.textInputEnabled) {
      fieldNames.push(`question-${currentQuestion.questionId}-text`);
    }
    await form.validateFields(fieldNames);
    const nextIndex = clampQuestionIndex(currentIndex + 1, questions.length);
    setCurrentIndex(nextIndex);
    if (taskId && typeof window !== "undefined") {
      const values = form.getFieldsValue(true);
      window.localStorage.setItem(
        getDraftStorageKey(taskId),
        JSON.stringify({ answers: values, currentIndex: nextIndex })
      );
    }
  };

  const handleAutoAdvance = async (nextValue: number) => {
    if (!currentQuestion || !["SINGLE_CHOICE", "MATRIX"].includes(currentQuestion.questionType)) {
      return;
    }
    form.setFieldValue(`question-${currentQuestion.questionId}`, nextValue);
    const nextIndex = clampQuestionIndex(currentIndex + 1, questions.length);
    if (taskId && typeof window !== "undefined") {
      const values = {
        ...form.getFieldsValue(true),
        [`question-${currentQuestion.questionId}`]: nextValue
      };
      window.localStorage.setItem(
        getDraftStorageKey(taskId),
        JSON.stringify({ answers: values, currentIndex: nextIndex })
      );
    }
    window.setTimeout(() => {
      setCurrentIndex(nextIndex);
    }, 120);
  };

  const handleMultiSelectChange = (question: TaskQuestionItem, values: (string | number)[]) => {
    const exclusiveOption = question.options.find((option) => option.exclusiveFlag);
    if (!exclusiveOption) {
      form.setFieldValue(`question-${question.questionId}`, values);
      return;
    }
    const exclusiveSelected = values.includes(exclusiveOption.optionId);
    const nextValues = exclusiveSelected ? [exclusiveOption.optionId] : values.filter((item) => item !== exclusiveOption.optionId);
    form.setFieldValue(`question-${question.questionId}`, nextValues);
  };

  const handleSubmit = async () => {
    if (!payload) {
      return;
    }
    setSubmitError(null);
    setSubmitState("validating");
    try {
      if (currentQuestion && !isReviewStep) {
        const fieldNames = [`question-${currentQuestion.questionId}`];
        if (currentQuestion.questionType === "TEXT_WITH_OPTION" && currentQuestion.textInputEnabled) {
          fieldNames.push(`question-${currentQuestion.questionId}-text`);
        }
        await form.validateFields(fieldNames);
      }
      const values = form.getFieldsValue(true);
      const incompleteQuestion = findFirstIncompleteQuestion(questions, values);
      if (incompleteQuestion) {
        const nextError = t(incompleteQuestion.messageKey);
        setCurrentIndex(incompleteQuestion.index);
        setSubmitError(nextError);
        setSubmitState("failed");
        message.warning(nextError);
        return;
      }
      const nextSubmitToken = submitToken ?? createSubmitToken();
      setSubmitToken(nextSubmitToken);
      setSubmitState("submitting");
      await submitMutation.mutateAsync({
        taskId: payload.taskId,
        scaleId: payload.scaleId,
        answerSheetId: draftMeta.answerSheetId,
        versionNo: draftMeta.versionNo,
        submitToken: nextSubmitToken,
        answers: toAnswerItems(questions, values)
      });
      if (taskId && typeof window !== "undefined") {
        window.localStorage.removeItem(getDraftStorageKey(taskId));
      }
    } catch (error) {
      if (isTimeoutError(error)) {
        setSubmitError(submitTimeoutMessage);
        setSubmitState("failed");
        return;
      }
      const nextError = getErrorMessage(error, t("taskQuestion.loadError"));
      setSubmitError(nextError);
      setSubmitState("failed");
    }
  };

  if (!taskId) {
    return <Result status="warning" title={t("taskQuestion.missingTask")} />;
  }

  if (currentTask?.status === "COMPLETED" || completedLocally) {
    return (
      <Result
        status="success"
        title={t("taskQuestion.completedTitle")}
        subTitle={t("taskQuestion.completedDesc")}
        extra={
          <Button type="primary" onClick={() => navigate(`/my/reports?taskId=${taskId}`)}>
            {t("taskQuestion.openCompletedReport")}
          </Button>
        }
      />
    );
  }

  if (questionQuery.isLoading) {
    return (
      <div style={{ minHeight: 320, display: "grid", placeItems: "center" }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <Typography.Text>{t("taskQuestion.loading")}</Typography.Text>
        </Space>
      </div>
    );
  }

  if (questionQuery.isError) {
    if (isTaskAlreadySubmitted(questionQuery.error) || currentTask?.status === "COMPLETED" || completedLocally) {
      return (
        <Result
          status="success"
          title={t("taskQuestion.completedTitle")}
          subTitle={t("taskQuestion.completedDesc")}
          extra={
            <Button type="primary" onClick={() => navigate(`/my/reports?taskId=${taskId}`)}>
              {t("taskQuestion.openCompletedReport")}
            </Button>
          }
        />
      );
    }
    return <Alert type="warning" showIcon message={t("taskQuestion.loadError")} />;
  }

  if (!payload || questions.length === 0) {
    if (payload?.completedFlag) {
      return (
        <Result
          status="success"
          title={t("taskQuestion.completedTitle")}
          subTitle={t("taskQuestion.completedDesc")}
          extra={
            <Button
              type="primary"
              onClick={() =>
                navigate(
                  payload.completedReportId
                    ? `/reports/${payload.completedReportId}?resultId=${payload.completedResultId ?? ""}&taskId=${taskId ?? ""}`
                    : `/my/reports?taskId=${taskId}`
                )
              }
            >
              {t("taskQuestion.openCompletedReport")}
            </Button>
          }
        />
      );
    }
    return <Empty description={t("taskQuestion.empty")} />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div
        style={{
          padding: isMobile ? "14px 16px" : "8px 4px 0",
          borderRadius: isMobile ? 16 : 0,
          background: isMobile ? "linear-gradient(160deg, rgba(242,248,255,0.98) 0%, rgba(255,255,255,0.98) 100%)" : "transparent",
          border: isMobile ? "1px solid #e0ebf7" : "none"
        }}
      >
        <Typography.Title level={4} style={{ marginBottom: 4 }}>
          {payload.scaleName}
        </Typography.Title>
        <Typography.Text type="secondary">
          {t("taskQuestion.meta", { taskId: payload.taskId, count: questions.length, requiredCount })}
        </Typography.Text>
        <br />
        <Typography.Text type="secondary">{t("taskQuestion.oneWayHint")}</Typography.Text>
      </div>

      <div
        style={{
          position: isMobile ? "sticky" : "static",
          top: isMobile ? 56 : undefined,
          zIndex: isMobile ? 8 : undefined
        }}
      >
        <Card
          size={isMobile ? "small" : "default"}
          styles={{
            body: {
              padding: isMobile ? 14 : 16
            }
          }}
          style={
            isMobile
              ? {
                  borderRadius: 16,
                  background: "rgba(255,255,255,0.96)",
                  boxShadow: "0 12px 28px rgba(31, 74, 109, 0.08)",
                  backdropFilter: "blur(10px)"
                }
              : undefined
          }
        >
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <div>
              <Typography.Text strong>
                {t("taskQuestion.progress", { current: progressStep, total: questions.length })}
              </Typography.Text>
              <Progress percent={progressPercent} showInfo={false} style={{ marginTop: 8 }} />
            </div>
            <Alert type="info" showIcon message={t("taskQuestion.lockedHint")} />
            {!isReviewStep && ["SINGLE_CHOICE", "MATRIX"].includes(currentQuestion?.questionType ?? "") ? (
              <Typography.Text type="secondary">{t("taskQuestion.autoNextHint")}</Typography.Text>
            ) : null}
          </Space>
        </Card>
      </div>

      <Card
        size={isMobile ? "small" : "default"}
        styles={{ body: { padding: isMobile ? 16 : 20 } }}
        style={{
          borderRadius: cardRadius,
          boxShadow: cardShadow,
          borderColor: "#e5edf5"
        }}
      >
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          {submitError ? <Alert type="error" showIcon message={submitError} /> : null}
          {submitState === "validating" ? <Alert type="info" showIcon message={validatingMessage} /> : null}
          {submitState === "submitting" ? <Alert type="info" showIcon message={submittingMessage} /> : null}
          {submitState === "succeeded" ? <Alert type="success" showIcon message={submitSucceededMessage} /> : null}
          <Form
            form={form}
            layout="vertical"
            preserve
            onValuesChange={(_, allValues) => {
              if (!taskId || typeof window === "undefined") {
                return;
              }
              try {
                window.localStorage.setItem(
                  getDraftStorageKey(taskId),
                  JSON.stringify({ answers: allValues, currentIndex } satisfies DraftSnapshot)
                );
              } catch {
                // ignore local storage write issues for draft caching
              }
            }}
          >
            {currentQuestion && !isReviewStep ? (
              <Card
                size="small"
                title={`${currentQuestion.questionNo}. ${currentQuestion.questionTitle}`}
                extra={currentQuestion.requiredFlag ? <Typography.Text type="danger">{t("taskQuestion.required")}</Typography.Text> : null}
                styles={{
                  body: {
                    padding: isMobile ? 20 : 24
                  }
                }}
                style={{
                  borderRadius: cardRadius,
                  borderColor: "#e5edf5",
                  background: "linear-gradient(180deg, #ffffff 0%, #f8fbff 100%)"
                }}
              >
                {currentQuestion.questionType === "MATRIX" ? (
                  <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
                    {t("taskQuestion.matrixHint", {
                      group: currentQuestion.matrixGroupCode ?? "-",
                      row: currentQuestion.rowCode ?? "-",
                      column: currentQuestion.columnCode ?? "-"
                    })}
                  </Typography.Text>
                ) : null}
                <Form.Item
                  name={`question-${currentQuestion.questionId}`}
                  rules={currentQuestion.requiredFlag ? [{ required: true, message: t("taskQuestion.requiredMessage") }] : undefined}
                  style={{ marginBottom: 0 }}
                >
                  {currentQuestion.questionType === "TEXT" ? (
                    <Input.TextArea
                      rows={isMobile ? 6 : 4}
                      placeholder={t("taskQuestion.textPlaceholder")}
                      style={{ fontSize: isMobile ? 16 : undefined }}
                    />
                  ) : currentQuestion.questionType === "SLIDER" ? (
                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                      <Slider
                        min={currentQuestion.sliderMin ?? 0}
                        max={currentQuestion.sliderMax ?? 100}
                        step={currentQuestion.sliderStep ?? 1}
                        value={typeof currentAnswerValue === "number" ? currentAnswerValue : undefined}
                        onChange={(value) => {
                          const nextValue = Array.isArray(value) ? value[0] : value;
                          form.setFieldValue(`question-${currentQuestion.questionId}`, nextValue);
                        }}
                      />
                      <Typography.Text type="secondary">
                        {t("taskQuestion.sliderRange", {
                          min: currentQuestion.sliderMin ?? 0,
                          max: currentQuestion.sliderMax ?? 100
                        })}
                      </Typography.Text>
                    </Space>
                  ) : currentQuestion.questionType === "MULTI_SELECT" ? (
                    <Checkbox.Group
                      value={Array.isArray(currentAnswerValue) ? currentAnswerValue : []}
                      onChange={(values) => handleMultiSelectChange(currentQuestion, values)}
                      style={{ width: "100%" }}
                    >
                      {currentQuestion.optionSelectionLimit ? (
                        <Typography.Text type="secondary">
                          {t("taskQuestion.multiSelectLimit", { count: currentQuestion.optionSelectionLimit })}
                        </Typography.Text>
                      ) : null}
                      <Space direction="vertical" size={12} style={{ width: "100%" }}>
                        {(() => {
                          const selectedValues = Array.isArray(currentAnswerValue) ? currentAnswerValue : [];
                          const limitReached =
                            currentQuestion.optionSelectionLimit != null &&
                            selectedValues.length >= currentQuestion.optionSelectionLimit;
                          const exclusiveOption = currentQuestion.options.find((option) => option.exclusiveFlag);
                          const exclusiveSelected =
                            exclusiveOption && selectedValues.includes(exclusiveOption.optionId);
                          return currentQuestion.options.map((option) => {
                            const checked = selectedValues.includes(option.optionId);
                            const disabled =
                              (!checked && limitReached) ||
                              (exclusiveSelected && option.optionId !== exclusiveOption?.optionId) ||
                              (!exclusiveSelected && exclusiveOption?.optionId === option.optionId && selectedValues.length > 0 && !checked);
                            return (
                              <Checkbox
                                key={option.optionId}
                                value={option.optionId}
                                disabled={disabled}
                                style={{
                                  width: "100%",
                                  marginInlineEnd: 0,
                                  border: checked ? "2px solid #1677ff" : "1px solid #d9d9d9",
                                  borderRadius: 14,
                                  padding: isMobile ? "12px 16px" : "10px 14px",
                                  background: checked ? "#e6f4ff" : "#fff",
                                  minHeight: isMobile ? 56 : 48,
                                  display: "flex",
                                  alignItems: "center"
                                }}
                              >
                                <div
                                  style={{
                                    fontSize: isMobile ? 16 : undefined,
                                    lineHeight: 1.6
                                  }}
                                >
                                  {option.optionCode}. {option.optionLabel}
                                </div>
                              </Checkbox>
                            );
                          });
                        })()}
                      </Space>
                    </Checkbox.Group>
                  ) : currentQuestion.questionType === "TEXT_WITH_OPTION" ? (
                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                      <Radio.Group
                        style={{ width: "100%" }}
                        value={typeof currentAnswerValue === "number" ? currentAnswerValue : undefined}
                        onChange={(event) => form.setFieldValue(`question-${currentQuestion.questionId}`, Number(event.target.value))}
                      >
                        <Space direction="vertical" size={12} style={{ width: "100%" }}>
                          {currentQuestion.options.map((option) => (
                            <Radio
                              key={option.optionId}
                              value={option.optionId}
                              style={{
                                width: "100%",
                                marginInlineEnd: 0,
                                border: currentAnswerValue === option.optionId ? "2px solid #1677ff" : "1px solid #d9d9d9",
                                borderRadius: 14,
                                padding: isMobile ? "12px 16px" : "10px 14px",
                                background: currentAnswerValue === option.optionId ? "#e6f4ff" : "#fff",
                                minHeight: isMobile ? 56 : 48,
                                display: "flex",
                                alignItems: "center"
                              }}
                            >
                              <div
                                style={{
                                  fontSize: isMobile ? 16 : undefined,
                                  lineHeight: 1.6
                                }}
                              >
                                {option.optionCode}. {option.optionLabel}
                              </div>
                            </Radio>
                          ))}
                        </Space>
                      </Radio.Group>
                      {currentQuestion.textInputEnabled ? (
                        <Form.Item
                          name={`question-${currentQuestion.questionId}-text`}
                          rules={
                            currentQuestion.textInputEnabled
                              ? [
                                  {
                                    required: Boolean(currentAnswerValue),
                                    message: t("taskQuestion.textRequired")
                                  }
                                ]
                              : undefined
                          }
                          style={{ marginBottom: 0 }}
                        >
                          <Input.TextArea
                            rows={isMobile ? 4 : 3}
                            placeholder={currentQuestion.textInputPlaceholder ?? t("taskQuestion.textPlaceholder")}
                            value={typeof currentAnswerText === "string" ? currentAnswerText : undefined}
                            onChange={(event) => form.setFieldValue(`question-${currentQuestion.questionId}-text`, event.target.value)}
                            disabled={!currentAnswerValue}
                          />
                        </Form.Item>
                      ) : null}
                    </Space>
                  ) : (
                    <Radio.Group style={{ width: "100%" }} onChange={(event) => void handleAutoAdvance(Number(event.target.value))}>
                      <Space direction="vertical" size={12} style={{ width: "100%" }}>
                        {currentQuestion.options.map((option) => (
                          <Radio
                            key={option.optionId}
                            value={option.optionId}
                            style={{
                              width: "100%",
                              marginInlineEnd: 0,
                              border: currentAnswerValue === option.optionId ? "2px solid #1677ff" : "1px solid #d9d9d9",
                              borderRadius: 14,
                              padding: isMobile ? "12px 16px" : "10px 14px",
                              background: currentAnswerValue === option.optionId ? "#e6f4ff" : "#fff",
                              minHeight: isMobile ? 56 : 48,
                              display: "flex",
                              alignItems: "center"
                            }}
                            >
                              <div
                                style={{
                                  fontSize: isMobile ? 16 : undefined,
                                  lineHeight: 1.6
                                }}
                              >
                                {option.optionCode}. {option.optionLabel}
                              </div>
                            </Radio>
                          ))}
                      </Space>
                    </Radio.Group>
                  )}
                </Form.Item>
              </Card>
            ) : isReviewStep ? (
              <Card
                size="small"
                title={t("taskQuestion.reviewTitle")}
                styles={{
                  body: {
                    padding: isMobile ? 20 : 24
                  }
                }}
                style={{
                  borderRadius: cardRadius,
                  borderColor: "#e5edf5"
                }}
              >
                <Space direction="vertical" size={16} style={{ width: "100%" }}>
                  <Typography.Text>{t("taskQuestion.reviewDesc")}</Typography.Text>
                  <Space wrap>
                    <Typography.Text strong>{t("taskQuestion.reviewAnswered", { count: answeredCount })}</Typography.Text>
                    <Typography.Text strong>{t("taskQuestion.reviewRequired", { count: requiredCount })}</Typography.Text>
                  </Space>
                  <Alert type="warning" showIcon message={t("taskQuestion.reviewLocked")} />
                </Space>
              </Card>
            ) : null}
          </Form>
        </Space>
      </Card>

      <div
        style={{
          position: isMobile ? "sticky" : "static",
          bottom: isMobile ? 0 : undefined,
          zIndex: isMobile ? 9 : undefined,
          background: isMobile ? "rgba(255,255,255,0.98)" : undefined,
          borderTop: isMobile ? "1px solid #f0f0f0" : undefined,
          boxShadow: isMobile ? "0 -12px 28px rgba(31, 74, 109, 0.08)" : undefined,
          backdropFilter: isMobile ? "blur(10px)" : undefined,
          padding: isMobile ? "12px 16px calc(12px + env(safe-area-inset-bottom))" : 0
        }}
      >
        <Space direction={isMobile ? "vertical" : "horizontal"} style={{ width: isMobile ? "100%" : undefined }} size={12}>
          {payload.allowSaveFlag ? (
            <Button block={isMobile} size={isMobile ? "large" : "middle"} loading={saveMutation.isPending} onClick={() => void handleSave()}>
              {t("taskQuestion.saveDraft")}
            </Button>
          ) : null}
          {currentIndex < questions.length - 1 ? (
            <Button block={isMobile} size={isMobile ? "large" : "middle"} type="primary" onClick={() => void handleNext()}>
            {t("taskQuestion.next")}
            </Button>
          ) : currentIndex === questions.length - 1 ? (
            <Button block={isMobile} size={isMobile ? "large" : "middle"} type="primary" onClick={() => void handleNext()}>
            {t("taskQuestion.review")}
            </Button>
          ) : (
            <Button block={isMobile} size={isMobile ? "large" : "middle"} type="primary" loading={submitMutation.isPending} onClick={() => void handleSubmit()}>
            {t("taskQuestion.submit")}
            </Button>
          )}
        </Space>
      </div>
    </Space>
  );
}
