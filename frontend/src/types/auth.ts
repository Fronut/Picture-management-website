export interface User {
  id: number;
  username: string;
  email: string;
  avatarUrl?: string | null;
  role: string;
}

export type { ApiResponse } from "./api";

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
  user: User;
}

export interface LoginPayload {
  usernameOrEmail: string;
  password: string;
}

export interface RegisterPayload {
  username: string;
  email: string;
  password: string;
}

export interface RefreshPayload {
  refreshToken: string;
}

export interface LogoutPayload extends RefreshPayload {
  logoutAllSessions?: boolean;
}
