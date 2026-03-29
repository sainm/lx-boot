import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";

export type ScaleSummary = {
  id: number;
  scaleCode: string;
  scaleName: string;
  applicableTarget?: string;
  versionNo?: string;
  status: string;
  anonymousSupported: boolean;
  createdAt: string;
};

export type CreateScaleRequest = {
  scaleCode: string;
  scaleName: string;
  description?: string;
  applicableTarget?: string;
  versionNo?: string;
  anonymousSupported?: boolean;
  reportTemplate?: string;
};

export type CreateScaleResponse = {
  id: number;
  status: string;
};

export async function fetchScalePage(params: {
  scaleName?: string;
  status?: string;
  page?: number;
  size?: number;
}) {
  const response = await http.get<ApiResponse<PageResponse<ScaleSummary>>>("/scales", {
    params
  });
  return response.data.data;
}

export async function createScale(payload: CreateScaleRequest) {
  const response = await http.post<ApiResponse<CreateScaleResponse>>("/scales", payload);
  return response.data.data;
}
