import { useMutation, useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Empty, Form, Input, Radio, Result, Space, Spin, Typography, message } from "antd";
import { useEffect, useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  fetchTaskQuestions,
  saveAnswerSheet,
  submitAnswerSheet,
  type AnswerItemRequest,
  type TaskQuestionItem
} from "../features/my-tasks/api";

type FormValues = Record<string, string | number | undefined>;
const LOCAL_DRAFT_PREFIX = "psy-respondent-task-draft";

function getDraftStorageKey(taskId: string) {
  return `${LOCAL_DRAFT_PREFIX}:${taskId}`;
}

function toAnswerItems(questions: TaskQuestionItem[], values: FormValues): AnswerItemRequest[] {
  const answers: AnswerItemRequest[] = [];
  for (const question of questions) {
    const value = values[`question-${question.questionId}`];
    if (value === undefined || value === null || value === "") {
      continue;
    }
    if (question.questionType === "TEXT") {
      answers.push({
        questionId: question.questionId,
        answerText: String(value)
      });
      continue;
    }
    answers.push({
      questionId: question.questionId,
      optionId: Number(value)
    });
  }
  return answers;
}

export function TaskQuestionPage() {
  const { taskId } = useParams();
  const navigate = useNavigate();
  const [form] = Form.useForm<FormValues>();

  const questionQuery = useQuery({
    queryKey: ["my-task-questions", taskId],
    queryFn: () => fetchTaskQuestions(Number(taskId)),
    enabled: Boolean(taskId)
  });

  const saveMutation = useMutation({
    mutationFn: saveAnswerSheet,
    onSuccess: () => {
      message.success("Draft saved");
    }
  });

  const submitMutation = useMutation({
    mutationFn: submitAnswerSheet,
    onSuccess: (result) => {
      message.success(`Submitted successfully. Risk level: ${result.riskLevel}`);
      navigate(`/reports/${result.reportId}?resultId=${result.resultId}&taskId=${taskId ?? ""}`);
    }
  });

  const payload = questionQuery.data;
  const questions = payload?.questions ?? [];
  const requiredCount = useMemo(() => questions.filter((item) => item.requiredFlag).length, [questions]);

  useEffect(() => {
    if (!taskId || typeof window === "undefined" || !payload) {
      return;
    }
    const savedDraft = window.localStorage.getItem(getDraftStorageKey(taskId));
    if (!savedDraft) {
      return;
    }
    try {
      const parsed = JSON.parse(savedDraft) as FormValues;
      form.setFieldsValue(parsed);
    } catch {
      window.localStorage.removeItem(getDraftStorageKey(taskId));
    }
  }, [form, payload, taskId]);

  const handleSave = async () => {
    if (!payload) {
      return;
    }
    const values = form.getFieldsValue();
    await saveMutation.mutateAsync({
      taskId: payload.taskId,
      scaleId: payload.scaleId,
      answers: toAnswerItems(questions, values)
    });
    if (taskId && typeof window !== "undefined") {
      window.localStorage.setItem(getDraftStorageKey(taskId), JSON.stringify(values));
    }
  };

  const handleSubmit = async () => {
    if (!payload) {
      return;
    }
    const values = await form.validateFields();
    await submitMutation.mutateAsync({
      taskId: payload.taskId,
      scaleId: payload.scaleId,
      answers: toAnswerItems(questions, values)
    });
    if (taskId && typeof window !== "undefined") {
      window.localStorage.removeItem(getDraftStorageKey(taskId));
    }
  };

  if (!taskId) {
    return <Result status="warning" title="Missing task id" />;
  }

  if (questionQuery.isLoading) {
    return (
      <div style={{ minHeight: 320, display: "grid", placeItems: "center" }}>
        <Space direction="vertical" align="center">
          <Spin size="large" />
          <Typography.Text>Loading questionnaire...</Typography.Text>
        </Space>
      </div>
    );
  }

  if (questionQuery.isError) {
    return <Alert type="warning" showIcon message="Unable to load questionnaire data." />;
  }

  if (!payload || questions.length === 0) {
    return <Empty description="No questions available for this task" />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4}>{payload.scaleName}</Typography.Title>
        <Typography.Text type="secondary">
          Task #{payload.taskId} | {questions.length} questions | {requiredCount} required
        </Typography.Text>
        <br />
        <Typography.Text type="secondary">Draft answers are also cached locally in this browser while you work.</Typography.Text>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onValuesChange={(_, allValues) => {
            if (!taskId || typeof window === "undefined") {
              return;
            }
            try {
              window.localStorage.setItem(getDraftStorageKey(taskId), JSON.stringify(allValues));
            } catch {
              // ignore local storage write issues for draft caching
            }
          }}
        >
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            {questions.map((question) => (
              <Card
                key={question.questionId}
                size="small"
                title={`${question.questionNo}. ${question.questionTitle}`}
                extra={question.requiredFlag ? <Typography.Text type="danger">Required</Typography.Text> : null}
              >
                <Form.Item
                  name={`question-${question.questionId}`}
                  rules={question.requiredFlag ? [{ required: true, message: "Please answer this question" }] : undefined}
                  style={{ marginBottom: 0 }}
                >
                  {question.questionType === "TEXT" ? (
                    <Input.TextArea rows={4} placeholder="Enter your response" />
                  ) : (
                    <Radio.Group style={{ width: "100%" }}>
                      <Space direction="vertical" size={12} style={{ width: "100%" }}>
                        {question.options.map((option) => (
                          <Radio key={option.optionId} value={option.optionId}>
                            {option.optionCode}. {option.optionLabel}
                          </Radio>
                        ))}
                      </Space>
                    </Radio.Group>
                  )}
                </Form.Item>
              </Card>
            ))}
          </Space>
        </Form>
      </Card>

      <Space>
        <Button onClick={() => navigate("/my/tasks")}>Back to tasks</Button>
        <Button loading={saveMutation.isPending} onClick={() => void handleSave()}>
          Save draft
        </Button>
        <Button type="primary" loading={submitMutation.isPending} onClick={() => void handleSubmit()}>
          Submit
        </Button>
      </Space>
    </Space>
  );
}
