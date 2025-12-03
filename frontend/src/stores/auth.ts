import { defineStore } from "pinia";
import { ElMessage } from "element-plus";

import type {
  AuthResponse,
  LoginPayload,
  RegisterPayload,
  User,
} from "@/types/auth";
import {
  loginRequest,
  logoutRequest,
  refreshSessionRequest,
  registerRequest,
} from "@/services/authService";

const TOKEN_KEY = "pm_auth_token";
const EXPIRES_KEY = "pm_auth_expires";
const USER_KEY = "pm_auth_user";
const REFRESH_TOKEN_KEY = "pm_refresh_token";
const REFRESH_EXPIRES_KEY = "pm_refresh_expires";
const REFRESH_THRESHOLD_MS = 30_000; // refresh 30s before expiry

interface AuthState {
  user: User | null;
  token: string | null;
  expiresAt: number | null;
  refreshToken: string | null;
  refreshExpiresAt: number | null;
  initialized: boolean;
  refreshing: boolean;
}

const defaultState = (): AuthState => ({
  user: null,
  token: null,
  expiresAt: null,
  refreshToken: null,
  refreshExpiresAt: null,
  initialized: false,
  refreshing: false,
});

interface SessionSnapshot {
  token: string;
  expiresAt: number;
  refreshToken: string;
  refreshExpiresAt: number;
  user: User;
}

const persistSession = (session: SessionSnapshot) => {
  localStorage.setItem(TOKEN_KEY, session.token);
  localStorage.setItem(EXPIRES_KEY, String(session.expiresAt));
  localStorage.setItem(USER_KEY, JSON.stringify(session.user));
  localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
  localStorage.setItem(REFRESH_EXPIRES_KEY, String(session.refreshExpiresAt));
};

const clearAccessSession = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(EXPIRES_KEY);
};

const clearRefreshSession = () => {
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(REFRESH_EXPIRES_KEY);
};

const clearUserProfile = () => {
  localStorage.removeItem(USER_KEY);
};

const clearSession = () => {
  clearAccessSession();
  clearRefreshSession();
  clearUserProfile();
};

const parseStoredUser = (): User | null => {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as User;
  } catch (error) {
    // Use Element Plus message instead of console to avoid ESLint no-console warning
    ElMessage.warning("Failed to parse user from storage");
    return null;
  }
};

const hydrateFromResponse = (response: AuthResponse): SessionSnapshot => {
  if (!response.refreshToken) {
    throw new Error("Missing refresh token in server response");
  }
  if (!response.refreshExpiresIn || response.refreshExpiresIn <= 0) {
    throw new Error("Missing refresh token expiry in server response");
  }
  const now = Date.now();
  const session: SessionSnapshot = {
    token: response.token,
    expiresAt: now + response.expiresIn * 1000,
    refreshToken: response.refreshToken,
    refreshExpiresAt: now + response.refreshExpiresIn * 1000,
    user: response.user,
  };
  persistSession(session);
  return session;
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
      this.refreshToken = session.refreshToken;
      this.refreshExpiresAt = session.refreshExpiresAt;
      this.user = session.user;
      ElMessage.success(`Welcome back, ${session.user.username}`);
    },
    async register(payload: RegisterPayload) {
      const response = await registerRequest(payload);
      const session = hydrateFromResponse(response);
      this.token = session.token;
      this.expiresAt = session.expiresAt;
      this.refreshToken = session.refreshToken;
      this.refreshExpiresAt = session.refreshExpiresAt;
      this.user = session.user;
      ElMessage.success("Registration successful");
    },
    async logout(options?: {
      logoutAllSessions?: boolean;
      silent?: boolean;
      notifyServer?: boolean;
    }) {
      const shouldNotify = options?.notifyServer ?? true;
      const refreshToken =
        this.refreshToken ?? localStorage.getItem(REFRESH_TOKEN_KEY);
      if (shouldNotify && refreshToken) {
        try {
          await logoutRequest({
            refreshToken,
            logoutAllSessions: options?.logoutAllSessions ?? false,
          });
        } catch (_error) {
          ElMessage.warning("Failed to notify server about logout");
        }
      }

      this.$reset();
      clearSession();
      if (!options?.silent) {
        ElMessage.success("已退出登录");
      }
    },
    async ensureSession(force = false) {
      if (!this.initialized) {
        this.initialize();
      }

      if (!this.refreshToken || !this.refreshExpiresAt) {
        return;
      }

      if (this.refreshExpiresAt <= Date.now()) {
        await this.logout({ notifyServer: false, silent: true });
        ElMessage.warning("登录状态已过期，请重新登录");
        return;
      }

      const needsRefresh =
        force ||
        !this.token ||
        !this.expiresAt ||
        this.expiresAt - Date.now() < REFRESH_THRESHOLD_MS;

      if (!needsRefresh || this.refreshing) {
        return;
      }

      this.refreshing = true;
      try {
        const response = await refreshSessionRequest({
          refreshToken: this.refreshToken,
        });
        const session = hydrateFromResponse(response);
        this.token = session.token;
        this.expiresAt = session.expiresAt;
        this.refreshToken = session.refreshToken;
        this.refreshExpiresAt = session.refreshExpiresAt;
        this.user = session.user;
      } catch (_error) {
        await this.logout({ notifyServer: false, silent: true });
        ElMessage.warning("登录状态已失效，请重新登录");
      } finally {
        this.refreshing = false;
      }
    },
    initialize() {
      if (this.initialized) {
        return;
      }

      const token = localStorage.getItem(TOKEN_KEY);
      const expires = localStorage.getItem(EXPIRES_KEY);
      const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
      const refreshExpires = localStorage.getItem(REFRESH_EXPIRES_KEY);
      const user = parseStoredUser();

      if (refreshToken && refreshExpires) {
        const refreshExpiresAt = Number(refreshExpires);
        if (refreshExpiresAt > Date.now()) {
          this.refreshToken = refreshToken;
          this.refreshExpiresAt = refreshExpiresAt;
        } else {
          clearRefreshSession();
        }
      }

      if (token && expires && user) {
        const expiresAt = Number(expires);
        if (expiresAt > Date.now()) {
          this.token = token;
          this.expiresAt = expiresAt;
          this.user = user;
        } else {
          clearAccessSession();
        }
      } else if (!user) {
        clearUserProfile();
      }

      this.initialized = true;
    },
  },
});
