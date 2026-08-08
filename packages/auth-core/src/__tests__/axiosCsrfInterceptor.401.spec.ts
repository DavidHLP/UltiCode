import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import type { AxiosError, InternalAxiosRequestConfig } from "axios";
import { createCsrfAxiosInterceptor } from "../axiosCsrfInterceptor";
import { createCsrfTokenManager, type CsrfTokenManager } from "../csrf";
import { _resetRefreshCoordinator } from "../refreshCoordinator";
import {
  setOnAuthFailure,
  clearOnAuthFailure,
  type AuthFailureHandler,
} from "../auth-failure";

// Mock rawAxios — both .post (for /auth/refresh) and .request (for replay).
// Why rawAxios.request and not bare axios: the 401 interceptor replays
// through rawAxios specifically so that withCredentials: true is honored
// and HttpOnly cookies ride along. Using bare axios would silently drop
// them (regression guard — see H1 in the review).
vi.mock("../rawAxios", () => ({
  rawAxios: {
    post: vi.fn(),
    request: vi.fn(),
  },
}));

import { rawAxios } from "../rawAxios";

/**
 * T1: 401 → refresh 200 → replay 200 (happy path). User does not get
 * logged out; onAuthFailure is NOT triggered.
 *
 * T2: 401 → refresh 401/throws → onAuthFailure IS triggered, the original
 * error is rejected, and the request is NOT retried.
 *
 * T2b: After a refresh failure, the same config with refreshRetried=true
 * must not re-attempt refresh (no infinite loop).
 */
describe("errorInterceptor — 401 auto-refresh (T1 + T2)", () => {
  let csrf: CsrfTokenManager;
  let onFailure: ReturnType<typeof vi.fn>;
  let refreshAccessToken: () => Promise<unknown>;
  let errorInterceptor: (error: AxiosError) => Promise<unknown>;

  function make401Error(): AxiosError {
    return {
      name: "AxiosError",
      message: "Request failed with status code 401",
      response: {
        status: 401,
        data: { code: 40100, message: "AUTH_TOKEN_EXPIRED" },
        statusText: "Unauthorized",
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      },
      config: {
        method: "get",
        url: "/problems",
      } as InternalAxiosRequestConfig,
    } as unknown as AxiosError;
  }

  beforeEach(() => {
    _resetRefreshCoordinator();
    vi.mocked(rawAxios.post).mockReset();
    vi.mocked(rawAxios.request).mockReset();
    csrf = createCsrfTokenManager();
    onFailure = vi.fn() as unknown as ReturnType<typeof vi.fn>;
    setOnAuthFailure(onFailure as unknown as AuthFailureHandler);

    // Build a refresh fn that uses the same coordinator-shaped closure.
    refreshAccessToken = async () => {
      const { data } = await rawAxios.post<{ csrfToken?: string }>(
        "/auth/refresh",
      );
      csrf.refreshFromResponse(data as { csrfToken?: string });
      return data;
    };

    const interceptors = createCsrfAxiosInterceptor(
      csrf,
      "http://test",
      refreshAccessToken,
    );
    errorInterceptor = interceptors.errorInterceptor;
  });

  // Cleanup global state between describe blocks (singleton handler).
  afterEach(() => {
    clearOnAuthFailure();
  });

  it("T1: 401 → refresh 200 → replay 200 via rawAxios (happy path, no onAuthFailure)", async () => {
    vi.mocked(rawAxios.post).mockResolvedValue({
      data: { csrfToken: "new-csrf" },
    });
    vi.mocked(rawAxios.request).mockResolvedValueOnce({
      status: 200,
      data: { ok: true },
    } as never);

    const error = make401Error();
    await errorInterceptor(error);

    expect(rawAxios.post).toHaveBeenCalledTimes(1);
    expect(rawAxios.post).toHaveBeenCalledWith("/auth/refresh");
    // Replay goes through rawAxios (withCredentials: true baked in) —
    // this is the regression guard for the H1 review finding.
    expect(rawAxios.request).toHaveBeenCalledTimes(1);
    expect(onFailure).not.toHaveBeenCalled();
  });

  it("T2: 401 → refresh throws → onAuthFailure triggered, no replay, error rejects", async () => {
    vi.mocked(rawAxios.post).mockRejectedValue(new Error("refresh 401"));

    const error = make401Error();
    await expect(errorInterceptor(error)).rejects.toBeDefined();

    expect(rawAxios.post).toHaveBeenCalledTimes(1);
    expect(rawAxios.request).not.toHaveBeenCalled();
    expect(onFailure).toHaveBeenCalledTimes(1);
    expect(onFailure).toHaveBeenCalledWith("refresh-failed", expect.any(Error));
  });

  it("T2b: refresh-failed request does NOT retry refresh on second 401 (no infinite loop)", async () => {
    vi.mocked(rawAxios.post).mockRejectedValue(new Error("refresh 401"));

    // First 401 — refresh attempted, fails, refreshRetried set
    const error1 = make401Error();
    await expect(errorInterceptor(error1)).rejects.toBeDefined();
    expect(rawAxios.post).toHaveBeenCalledTimes(1);

    // Second 401 on the SAME config (refreshRetried now true)
    vi.mocked(rawAxios.post).mockClear();
    const error2 = make401Error();
    error2.config = error1.config; // share config
    await expect(errorInterceptor(error2)).rejects.toBeDefined();

    // Critical: refresh is NOT re-attempted
    expect(rawAxios.post).not.toHaveBeenCalled();
  });

  it("does not enter 401 branch when refreshAccessToken is omitted (legacy path)", async () => {
    // Recreate interceptor WITHOUT refreshAccessToken
    const legacy = createCsrfAxiosInterceptor(csrf, "http://test");
    vi.mocked(rawAxios.post).mockClear();
    vi.mocked(rawAxios.request).mockReset();

    const error = make401Error();
    await expect(legacy.errorInterceptor(error)).rejects.toBeDefined();

    expect(rawAxios.post).not.toHaveBeenCalled();
    expect(onFailure).not.toHaveBeenCalled();
  });

  it("does not retry when error has no config (defensive)", async () => {
    vi.mocked(rawAxios.post).mockClear();
    const error = make401Error();
    (error as { config: undefined }).config = undefined;

    await expect(errorInterceptor(error)).rejects.toBeDefined();
    expect(rawAxios.post).not.toHaveBeenCalled();
  });
});
