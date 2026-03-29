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
};

export type TaskQuestionItem = {
  questionId: number;
  questionNo: number;
  questionTitle: string;
  questionType: string;
  requiredFlag: boolean;
  options: TaskQuestionOption[];
};

export type TaskQuestionPayload = {
  taskId: number;
  scaleId: number;
  scaleName: string;
  questions: TaskQuestionItem[];
};

export type AnswerItemRequest = {
  questionId: number;
  optionId?: number;
  answerText?: string;
};

export type SaveAnswerSheetRequest = {
  taskId: number;
  scaleId: number;
  answers: AnswerItemRequest[];
};

export type SubmitAnswerSheetRequest = SaveAnswerSheetRequest;

export type SubmitAnswerSheetResult = {
  answerSheetId: number;
  resultId: number;
  reportId: number;
  riskLevel: string;
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
  const response = await http.post<ApiResponse<Record<string, unknown>>>("/answer-sheets/save", payload);
  return response.data.data;
}

export async function submitAnswerSheet(payload: SubmitAnswerSheetRequest) {
  const response = await http.post<ApiResponse<SubmitAnswerSheetResult>>("/answer-sheets/submit", payload);
  return response.data.data;
}
