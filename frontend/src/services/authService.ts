import apiClient from "./apiClient";
import type {
  AuthResponse,
  LoginPayload,
  LogoutPayload,
  RefreshPayload,
  RegisterPayload,
} from "@/types/auth";
import type { ApiResponse } from "@/types/api";

const AUTH_BASE = "/auth";

const extractData = (response: ApiResponse<AuthResponse>): AuthResponse => {
  if (!response.data) {
    throw new Error(response.message || "Invalid response payload");
  }
  return response.data;
};

export const loginRequest = async (
  payload: LoginPayload
): Promise<AuthResponse> => {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>(
    `${AUTH_BASE}/login`,
    payload
  );
  return extractData(data);
};

export const registerRequest = async (
  payload: RegisterPayload
): Promise<AuthResponse> => {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>(
    `${AUTH_BASE}/register`,
    payload
  );
  return extractData(data);
};

export const refreshSessionRequest = async (
  payload: RefreshPayload
): Promise<AuthResponse> => {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>(
    `${AUTH_BASE}/refresh`,
    payload
  );
  return extractData(data);
};

export const logoutRequest = async (payload: LogoutPayload): Promise<void> => {
  await apiClient.post<ApiResponse<void>>(`${AUTH_BASE}/logout`, payload);
};
