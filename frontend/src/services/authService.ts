import apiClient from "./apiClient";
import type {
  ApiResponse,
  AuthResponse,
  LoginPayload,
  RegisterPayload,
} from "@/types/auth";

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
