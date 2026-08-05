import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  createRefreshAccessToken,
  _resetRefreshCoordinator,
} from "../refreshCoordinator";
import { createCsrfTokenManager, type CsrfTokenManager } from "../csrf";

// Mock rawAxios BEFORE importing it. The coordinator imports rawAxios, so
// this vi.mock replaces the module at load time.
vi.mock("../rawAxios", () => ({
  rawAxios: {
    post: vi.fn(),
  },
}));

// Imported AFTER vi.mock so the mocked module is in place.
import { rawAxios } from "../rawAxios";

/**
 * T3: concurrent dedup — multiple concurrent 401s share a single
 * in-flight /auth/refresh call. Without this, the backend's
 * RefreshTokenService.revokeIfActive would reject the second refresh
 * and the second tab would be force-logged-out.
 *
 * T4: CSRF sync — the refresh response body's csrfToken is forwarded
 * into the manager so the first write after refresh doesn't trigger
 * a 403 → /auth/me round-trip.
 */
describe("refreshCoordinator — T3 concurrent dedup + T4 CSRF sync", () => {
  let csrf: CsrfTokenManager;

  beforeEach(() => {
    _resetRefreshCoordinator();
    vi.mocked(rawAxios.post).mockReset();
    csrf = createCsrfTokenManager();
  });

  it("T3: shares a single in-flight refresh across N concurrent calls", async () => {
    // 50ms setTimeout in mock — proves all callers see the SAME promise,
    // not microtask-resolved separate calls.
    // rawAxios has no interceptors, so it returns the raw Result envelope
    // { code, data: { csrfToken } }, not the unwrapped inner payload.
    vi.mocked(rawAxios.post).mockImplementation(
      () =>
        new Promise((resolve) => {
          setTimeout(
            () =>
              resolve({ data: { code: 0, data: { csrfToken: "new" } } }),
            50,
          );
        }),
    );

    const refresh = createRefreshAccessToken(csrf);
    const p1 = refresh();
    const p2 = refresh();
    const p3 = refresh();

    await Promise.all([p1, p2, p3]);

    expect(rawAxios.post).toHaveBeenCalledTimes(1);
    expect(rawAxios.post).toHaveBeenCalledWith("/auth/refresh");
  });

  it("T3b: clears in-flight after completion so the next 401 can refresh again", async () => {
  vi.mocked(rawAxios.post).mockResolvedValue({ data: { code: 0, data: { csrfToken: "x" } } });

    const refresh = createRefreshAccessToken(csrf);
    await refresh();
    await refresh();
    await refresh();

    expect(rawAxios.post).toHaveBeenCalledTimes(3);
  });

  it("T4: syncs csrfManager from refresh response body", async () => {
    vi.mocked(rawAxios.post).mockResolvedValue({
      data: { code: 0, data: { csrfToken: "fresh-token" } },
    });
    csrf.setToken("old-token");
    const refresh = createRefreshAccessToken(csrf);

    await refresh();

    expect(csrf.getToken()).toBe("fresh-token");
  });

  it("T4b: does not clear csrfManager when refresh response has no csrfToken", async () => {
  vi.mocked(rawAxios.post).mockResolvedValue({ data: { code: 0, data: {} } });
    csrf.setToken("keep-me");
    const refresh = createRefreshAccessToken(csrf);

    await refresh();

    expect(csrf.getToken()).toBe("keep-me");
  });

  // Regression: /auth/refresh returns Result<LoginResponse> = {code, data:{...}}.
  // rawAxios has NO interceptors, so the coordinator sees this raw envelope.
  // A prior bug read csrfToken from the envelope top level (data.csrfToken),
  // which is always undefined — so csrfManager was never updated after refresh,
  // every subsequent write carried a stale CSRF, and its 5-minute Redis grace
  // expiry forced a logout. This test pins the real backend shape so neither
  // the coordinator nor these mocks can silently drift back.
  it("T4-regression: reads csrfToken from Result envelope data.data, not top level", async () => {
    vi.mocked(rawAxios.post).mockResolvedValueOnce({
      data: { code: 0, data: { csrfToken: "enveloped-token" } },
    });
    csrf.setToken("stale");
    const refresh = createRefreshAccessToken(csrf);

    await refresh();

    expect(csrf.getToken()).toBe("enveloped-token");
  });

  it("T4-regression: top-level csrfToken (the old broken shape) is NOT consulted", async () => {
    // If someone reintroduces the bug, this envelope has a top-level csrfToken
    // (where the bug looked) but the real token nested under data.data.
    // The manager must pick up the nested one, proving the top level is ignored.
    vi.mocked(rawAxios.post).mockResolvedValueOnce({
      data: { code: 0, csrfToken: "decoy-top-level", data: { csrfToken: "real-nested" } },
    });
    csrf.setToken("stale");
    const refresh = createRefreshAccessToken(csrf);

    await refresh();

    expect(csrf.getToken()).toBe("real-nested");
  });

  it("T5: propagates refresh errors and clears in-flight", async () => {
    vi.mocked(rawAxios.post).mockRejectedValueOnce(new Error("refresh 401"));
    const refresh = createRefreshAccessToken(csrf);

    await expect(refresh()).rejects.toThrow("refresh 401");

    // Even after failure, the next refresh can fire (in-flight was cleared).
    vi.mocked(rawAxios.post).mockResolvedValueOnce({
      data: { code: 0, data: { csrfToken: "after-fail" } },
    });
    await expect(refresh()).resolves.toBeDefined();
    expect(csrf.getToken()).toBe("after-fail");
  });

  it("T5b: returns a Promise (not a value) so multiple callers can await", () => {
  vi.mocked(rawAxios.post).mockResolvedValue({ data: { code: 0, data: { csrfToken: "x" } } });
    const refresh = createRefreshAccessToken(csrf);
    const result = refresh();
    expect(result).toBeInstanceOf(Promise);
    // Don't leak — clean up
    return result;
  });
});
