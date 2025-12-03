import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

import { useAuthStore } from "@/stores/auth";
import type { AuthResponse } from "@/types/auth";

const serviceMocks = vi.hoisted(() => ({
  loginRequest: vi.fn(),
  registerRequest: vi.fn(),
  refreshSessionRequest: vi.fn(),
  logoutRequest: vi.fn(),
}));

const messageMock = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}));

vi.mock("@/services/authService", () => serviceMocks);

vi.mock("element-plus", () => ({
  ElMessage: messageMock,
}));

const createSampleUser = () => ({
  id: 1,
  username: "demo",
  email: "demo@example.com",
  avatarUrl: null,
  role: "ROLE_USER",
});

describe("auth store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    serviceMocks.loginRequest.mockReset();
    serviceMocks.registerRequest.mockReset();
    serviceMocks.refreshSessionRequest.mockReset();
    serviceMocks.logoutRequest.mockReset();
    messageMock.success.mockReset();
    messageMock.warning.mockReset();
    messageMock.error.mockReset();
  });

  it("starts unauthenticated by default", () => {
    const store = useAuthStore();
    expect(store.isAuthenticated).toBe(false);
    expect(store.user).toBeNull();
    expect(store.token).toBeNull();
  });

  it("clears state, storage, and notifies backend on logout", async () => {
    const store = useAuthStore();
    const expiresAt = Date.now() + 10_000;
    const refreshExpiresAt = Date.now() + 60_000;
    store.$patch({
      user: createSampleUser(),
      token: "mock-token",
      expiresAt,
      refreshToken: "refresh-token",
      refreshExpiresAt,
    });
    localStorage.setItem("pm_auth_token", "mock-token");
    localStorage.setItem("pm_auth_expires", String(expiresAt));
    localStorage.setItem("pm_auth_user", JSON.stringify(createSampleUser()));
    localStorage.setItem("pm_refresh_token", "refresh-token");
    localStorage.setItem("pm_refresh_expires", String(refreshExpiresAt));

    await store.logout();

    expect(store.isAuthenticated).toBe(false);
    expect(store.user).toBeNull();
    expect(localStorage.getItem("pm_auth_token")).toBeNull();
    expect(localStorage.getItem("pm_refresh_token")).toBeNull();
    expect(serviceMocks.logoutRequest).toHaveBeenCalledWith({
      refreshToken: "refresh-token",
      logoutAllSessions: false,
    });
  });

  it("refreshes the session when forced", async () => {
    const store = useAuthStore();
    const expiresAt = Date.now() + 10;
    const refreshExpiresAt = Date.now() + 60_000;
    store.$patch({
      user: createSampleUser(),
      token: "stale",
      expiresAt,
      refreshToken: "refresh-token",
      refreshExpiresAt,
      initialized: true,
    });
    const freshResponse: AuthResponse = {
      token: "new-token",
      tokenType: "Bearer",
      expiresIn: 120,
      refreshToken: "next-refresh",
      refreshExpiresIn: 7200,
      user: createSampleUser(),
    };
    serviceMocks.refreshSessionRequest.mockResolvedValueOnce(freshResponse);

    await store.ensureSession(true);

    expect(serviceMocks.refreshSessionRequest).toHaveBeenCalledWith({
      refreshToken: "refresh-token",
    });
    expect(store.token).toBe("new-token");
    expect(store.refreshToken).toBe("next-refresh");
  });
});
