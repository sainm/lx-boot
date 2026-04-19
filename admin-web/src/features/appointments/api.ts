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

export type CounselorOption = {
  userId: number;
  username: string;
  displayName: string;
};

export type AppointmentSummary = {
  id: number;
  userId: number;
  counselorUserId: number;
  counselorDisplayName?: string;
  warningId?: number;
  scheduleId?: number;
  appointmentStatus: string;
  sourceType: string;
  remark?: string;
  scheduleDate?: string;
  startTime?: string;
  endTime?: string;
  createdAt: string;
};

export type CreateAppointmentRequest = {
  counselorUserId: number;
  scheduleId: number;
  warningId?: number;
  remark?: string;
};

export type CreateAppointmentResult = {
  appointmentId: number;
  status: string;
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

export type AppointmentActionResult = {
  appointmentId: number;
  status: string;
};

export async function fetchCounselorSchedules(counselorId: number) {
  const response = await http.get<ApiResponse<CounselorSchedule[]>>(`/counselors/${counselorId}/schedules`);
  return response.data.data;
}

export async function fetchCounselors() {
  const response = await http.get<ApiResponse<CounselorOption[]>>("/counselors");
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

export async function cancelAppointment(appointmentId: number) {
  const response = await http.post<ApiResponse<AppointmentActionResult>>(`/appointments/${appointmentId}/cancel`);
  return response.data.data;
}
