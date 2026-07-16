import { describe, it, expect, vi, beforeEach } from "vitest";
import type { AuthFailureStrategy } from "@/shared/http-client/src";

// Capture the AuthFailureStrategy that request.ts wires into createHttpClient,
// without exporting an internal constant. The strategy under test is the exact
// object production code installs on the HTTP client.
const harness = vi.hoisted(() => ({
  strategy: null as AuthFailureStrategy | null,
  authStore: { isAuthenticated: true, clearUser: vi.fn() },
  expiredCallback: null as (() => void) | null,
}));

vi.mock("@/shared/http-client/src", () => ({
  createHttpClient: (cfg: { onAuthFailure?: AuthFailureStrategy }) => {
    if (cfg.onAuthFailure) harness.strategy = cfg.onAuthFailure;
    return {};
  },
}));

vi.mock("@/shared/auth-core/src", () => ({
  csrfManager: {
    clearToken: vi.fn(),
    refreshFromResponse: vi.fn(),
    getToken: vi.fn(),
    setToken: vi.fn(),
  },
}));

vi.mock("@/i18n/utils/locale", () => ({ getActiveLocale: () => "zh-CN" }));

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => harness.authStore,
}));

vi.mock("@/contexts/AuthContext", () => ({
  getSessionExpiredCallback: () => harness.expiredCallback,
}));

// Importing the module runs its top level, which calls createHttpClient and
// thereby captures the production onAuthFailure strategy into `harness`.
import "@/utils/request";

function strategy() {
  const s = harness.strategy;
  if (!s || s.kind !== "clear-and-run") {
    throw new Error("expected clear-and-run auth-failure strategy");
  }
  return s;
}

describe("console request onAuthFailure strategy", () => {
  beforeEach(() => {
    harness.authStore = { isAuthenticated: true, clearUser: vi.fn() };
    harness.expiredCallback = vi.fn();
  });

  it("clears the user and fires the AuthContext session-expired callback when authenticated", async () => {
    await strategy().onAuthFailure();
    expect(harness.authStore.clearUser).toHaveBeenCalledTimes(1);
    expect(harness.expiredCallback).toHaveBeenCalledTimes(1);
  });

  it("skips clearUser when already unauthenticated but still notifies the failure owner", async () => {
    harness.authStore.isAuthenticated = false;
    await strategy().onAuthFailure();
    expect(harness.authStore.clearUser).not.toHaveBeenCalled();
    expect(harness.expiredCallback).toHaveBeenCalledTimes(1);
  });

  it("is a navigation no-op when no session-expired callback is registered", async () => {
    harness.expiredCallback = null;
    await expect(strategy().onAuthFailure()).resolves.toBeUndefined();
    expect(harness.authStore.clearUser).toHaveBeenCalledTimes(1);
  });
});
