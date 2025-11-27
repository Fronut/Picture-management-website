import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

import { useAuthStore } from "@/stores/auth";

vi.mock("@/services/authService", () => ({
  loginRequest: vi.fn(),
  registerRequest: vi.fn(),
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
  });

  it("starts unauthenticated by default", () => {
    const store = useAuthStore();
    expect(store.isAuthenticated).toBe(false);
    expect(store.user).toBeNull();
    expect(store.token).toBeNull();
  });

  it("clears state and storage on logout", async () => {
    const store = useAuthStore();
    const expiresAt = Date.now() + 10_000;
    store.$patch({
      user: createSampleUser(),
      token: "mock-token",
      expiresAt,
    });
    localStorage.setItem("pm_auth_token", "mock-token");
    localStorage.setItem("pm_auth_expires", String(expiresAt));
    localStorage.setItem("pm_auth_user", JSON.stringify(createSampleUser()));

    await store.logout();

    expect(store.isAuthenticated).toBe(false);
    expect(store.user).toBeNull();
    expect(localStorage.getItem("pm_auth_token")).toBeNull();
  });
});
