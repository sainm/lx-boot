import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type MyAssessmentTask = {
  taskId: number;
  taskName: string;
  scaleId: number;
  scaleName: string;
  endTime: string;
  status: string;
};

export type TaskQuestionOption = {
  optionId: number;
  optionCode: string;
  optionLabel: string;
  scoreValue: number;
  exclusiveFlag?: boolean;
  optionGroupCode?: string | null;
};

export type TaskQuestionItem = {
  questionId: number;
  questionNo: number;
  questionTitle: string;
  questionType: string;
  requiredFlag: boolean;
  optionSelectionLimit?: number | null;
  sliderMin?: number | null;
  sliderMax?: number | null;
  sliderStep?: number | null;
  textInputEnabled?: boolean;
  textInputPlaceholder?: string | null;
  matrixGroupCode?: string | null;
  rowCode?: string | null;
  columnCode?: string | null;
  options: TaskQuestionOption[];
};

export type TaskSkipRule = {
  whenQuestionNo: number;
  whenOptionCode: string;
  skipQuestionNos: number[];
};

export type TaskScaleGovernanceContent = {
  description?: string | null;
  instructionText?: string | null;
  purposeText?: string | null;
  dataUsageText?: string | null;
  resultVisibilityText?: string | null;
  nonDiagnosticText?: string | null;
  highRiskActionText?: string | null;
  helpResourceText?: string | null;
};

export type TaskQuestionPayload = {
  taskId: number;
  scaleId: number;
  scaleName: string;
  allowSaveFlag: boolean;
  allowRetakeFlag?: boolean;
  anonymousFlag?: boolean;
  allowTimeoutSubmitFlag?: boolean;
  startTime?: string;
  endTime?: string;
  taskStatus?: string;
  completedFlag?: boolean;
  completedReportId?: number;
  completedResultId?: number;
  completedRiskLevel?: string;
  draftAnswerSheetId?: number;
  draftVersionNo?: number;
  draftAnswers?: TaskDraftAnswerItem[];
  governance?: TaskScaleGovernanceContent;
  skipRules?: TaskSkipRule[];
  questions: TaskQuestionItem[];
};

export type TaskDraftAnswerItem = {
  questionId: number;
  optionId?: number | null;
  answerText?: string | null;
  answerValue?: number | null;
};

export type AnswerItemRequest = {
  questionId: number;
  optionId?: number;
  answerText?: string;
  answerValue?: number;
};

export type SaveAnswerSheetRequest = {
  taskId: number;
  scaleId: number;
  answerSheetId?: number;
  versionNo?: number;
  answers: AnswerItemRequest[];
};

export type SubmitAnswerSheetRequest = SaveAnswerSheetRequest & {
  submitToken?: string;
};

export type SaveAnswerSheetResult = {
  answerSheetId: number;
  status: string;
  versionNo: number;
};

export type SubmitAnswerSheetResult = {
  answerSheetId: number;
  resultId: number;
  reportId?: number | null;
  riskLevel: string;
  versionNo?: number;
  anonymous?: boolean;
};

export type RescoreResult = {
  answerSheetId: number;
  resultId: number;
  reportId: number;
  totalScore: number;
  riskLevel: string;
  previousRiskLevel: string;
};

export async function fetchMyTasks() {
  const response = await http.get<ApiResponse<MyAssessmentTask[]>>("/my/tasks");
  return response.data.data;
}

export async function fetchTaskQuestions(taskId: number) {
  const response = await http.get<ApiResponse<TaskQuestionPayload>>(`/my/tasks/${taskId}/questions`);
  return response.data.data;
}

export async function saveAnswerSheet(payload: SaveAnswerSheetRequest) {
  const response = await http.post<ApiResponse<SaveAnswerSheetResult>>("/answer-sheets/save", payload);
  return response.data.data;
}

export async function submitAnswerSheet(payload: SubmitAnswerSheetRequest) {
  const response = await http.post<ApiResponse<SubmitAnswerSheetResult>>("/answer-sheets/submit", payload, {
    headers: payload.submitToken ? { "Idempotency-Key": payload.submitToken } : undefined,
    timeout: 60000
  });
  return response.data.data;
}

export async function rescoreResult(resultId: number) {
  const response = await http.post<ApiResponse<RescoreResult>>(`/results/${resultId}/rescore`);
  return response.data.data;
}
