import { describe, it, expect, vi, beforeEach } from "vitest";
import type { AuthFailureStrategy } from "@/shared/http-client/src";

// Capture the AuthFailureStrategy that request.ts wires into createHttpClient,
// without exporting an internal constant. The strategy under test is the exact
// object production code installs on the HTTP client.
const harness = vi.hoisted(() => ({
  strategy: null as AuthFailureStrategy | null,
  runSessionExpired: vi.fn(),
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

vi.mock("@/auth/runSessionExpired", () => ({
  runSessionExpired: () => harness.runSessionExpired(),
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
    harness.runSessionExpired.mockReset();
    harness.runSessionExpired.mockImplementation(() => undefined);
  });

  it("delegates to runSessionExpired for an authenticated failure", () => {
    strategy().onAuthFailure();
    expect(harness.runSessionExpired).toHaveBeenCalledTimes(1);
  });

  it("is invoked exactly once per call regardless of caller state", () => {
    strategy().onAuthFailure();
    strategy().onAuthFailure();
    // Two separate onAuthFailure entries still produce two helper invocations;
    // dedupe of concurrent same-reason triggers lives in shared/auth-core and
    // is asserted by the auth-failure module's own spec.
    expect(harness.runSessionExpired).toHaveBeenCalledTimes(2);
  });

  it("is a navigation no-op when runSessionExpired is a stub", () => {
    expect(() => strategy().onAuthFailure()).not.toThrow();
    expect(harness.runSessionExpired).toHaveBeenCalledTimes(1);
  });
});
