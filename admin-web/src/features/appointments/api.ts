import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type CounselorSchedule = {
  id: number;
  counselorUserId: number;
  scheduleDate: string;
  startTime: string;
  endTime: string;
  quotaCount: number;
  status: string;
};

export type AppointmentSummary = {
  id: number;
  userId: number;
  counselorUserId: number;
  warningId?: number;
  scheduleId?: number;
  appointmentStatus: string;
  sourceType: string;
  remark?: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateAppointmentRequest = {
  counselorUserId: number;
  scheduleId: number;
  warningId?: number;
  remark?: string;
};

export type CreateAppointmentResult = {
  id: number;
  appointmentStatus: string;
};

export type CreateScheduleRequest = {
  scheduleDate: string;
  startTime: string;
  endTime: string;
  quotaCount: number;
};

export type CreateScheduleResult = {
  id: number;
};

export async function fetchCounselorSchedules(counselorId: number) {
  const response = await http.get<ApiResponse<CounselorSchedule[]>>(`/counselors/${counselorId}/schedules`);
  return response.data.data;
}

export async function fetchMyAppointments() {
  const response = await http.get<ApiResponse<AppointmentSummary[]>>("/appointments/my");
  return response.data.data;
}

export async function createAppointment(payload: CreateAppointmentRequest) {
  const response = await http.post<ApiResponse<CreateAppointmentResult>>("/appointments", payload);
  return response.data.data;
}

export async function createSchedule(payload: CreateScheduleRequest) {
  const response = await http.post<ApiResponse<CreateScheduleResult>>("/counselors/me/schedules", payload);
  return response.data.data;
}

