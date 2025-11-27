import { defineStore } from "pinia";
import { ElMessage } from "element-plus";

import type {
  AuthResponse,
  LoginPayload,
  RegisterPayload,
  User,
} from "@/types/auth";
import { loginRequest, registerRequest } from "@/services/authService";

const TOKEN_KEY = "pm_auth_token";
const EXPIRES_KEY = "pm_auth_expires";
const USER_KEY = "pm_auth_user";

interface AuthState {
  user: User | null;
  token: string | null;
  expiresAt: number | null;
  initialized: boolean;
}

const defaultState = (): AuthState => ({
  user: null,
  token: null,
  expiresAt: null,
  initialized: false,
});

const persistSession = (token: string, expiresAt: number, user: User) => {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(EXPIRES_KEY, String(expiresAt));
  localStorage.setItem(USER_KEY, JSON.stringify(user));
};

const clearSession = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(EXPIRES_KEY);
  localStorage.removeItem(USER_KEY);
};

const parseStoredUser = (): User | null => {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as User;
  } catch (error) {
    console.warn("Failed to parse user from storage", error);
    return null;
  }
};

const hydrateFromResponse = (response: AuthResponse) => {
  const expiresAt = Date.now() + response.expiresIn * 1000;
  persistSession(response.token, expiresAt, response.user);
  return { token: response.token, expiresAt, user: response.user };
};

export const useAuthStore = defineStore("auth", {
  state: defaultState,
  getters: {
    isAuthenticated: (state) => {
      if (!state.token || !state.expiresAt) {
        return false;
      }
      return state.expiresAt > Date.now();
    },
  },
  actions: {
    async login(payload: LoginPayload) {
      const response = await loginRequest(payload);
      const session = hydrateFromResponse(response);
      this.token = session.token;
      this.expiresAt = session.expiresAt;
      this.user = session.user;
      ElMessage.success(`Welcome back, ${session.user.username}`);
    },
    async register(payload: RegisterPayload) {
      const response = await registerRequest(payload);
      const session = hydrateFromResponse(response);
      this.token = session.token;
      this.expiresAt = session.expiresAt;
      this.user = session.user;
      ElMessage.success("Registration successful");
    },
    async logout() {
      this.$reset();
      clearSession();
    },
    initialize() {
      if (this.initialized) {
        return;
      }

      const token = localStorage.getItem(TOKEN_KEY);
      const expires = localStorage.getItem(EXPIRES_KEY);
      const user = parseStoredUser();

      if (token && expires && user) {
        const expiresAt = Number(expires);
        if (expiresAt > Date.now()) {
          this.token = token;
          this.expiresAt = expiresAt;
          this.user = user;
        } else {
          clearSession();
        }
      }

      this.initialized = true;
    },
  },
});
