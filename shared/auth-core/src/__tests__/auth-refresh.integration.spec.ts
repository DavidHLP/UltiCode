import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import type { AxiosError, InternalAxiosRequestConfig } from "axios";
import { createCsrfAxiosInterceptor } from "../axiosCsrfInterceptor";
import { createCsrfTokenManager, type CsrfTokenManager } from "../csrf";
import {
  createRefreshAccessToken,
  _resetRefreshCoordinator,
} from "../refreshCoordinator";
import {
  setOnAuthFailure,
  clearOnAuthFailure,
  type AuthFailureHandler,
} from "../auth-failure";

vi.mock("../rawAxios", () => ({
  rawAxios: {
    post: vi.fn(),
    request: vi.fn(),
  },
}));

import { rawAxios } from "../rawAxios";

const make401Error = (): AxiosError => ({
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
} as unknown as AxiosError);

/**
 * T6 regression: the "10-minute hang" scenario end-to-end.
 *
 * User story: a user opens console, leaves for 10 minutes, comes back
 * and clicks anything. The 401 path should auto-refresh + replay so the
 * user does not get logged out. This integration spec ties together:
 *  - T1 (401 → refresh → replay happy path)
 *  - T2 (refresh fails → onAuthFailure → no infinite loop)
 *  - T3 (concurrent dedup)
 *  - T4 (csrf sync from refresh response)
 *
 * Real HTTP is not exercised here — that belongs to the manual smoke
 * test in the plan's Validation Commands section. The unit-level
 * integration gives a single regression entry point.
 */
describe("auth-refresh integration — T6 regression (10-minute hang)", () => {
  let csrf: CsrfTokenManager;
  let onFailure: ReturnType<typeof vi.fn>;
  let refresh: () => Promise<unknown>;
  let errorInterceptor: (error: AxiosError) => Promise<unknown>;

  beforeEach(() => {
    _resetRefreshCoordinator();
    vi.mocked(rawAxios.post).mockReset();
    vi.mocked(rawAxios.request).mockReset();
    csrf = createCsrfTokenManager();
    csrf.setToken("stale-csrf-from-login");
    onFailure = vi.fn() as unknown as ReturnType<typeof vi.fn>;
    setOnAuthFailure(onFailure as unknown as AuthFailureHandler);

    refresh = createRefreshAccessToken(csrf);

    const interceptors = createCsrfAxiosInterceptor(
      csrf,
      "http://test",
      refresh,
    );
    errorInterceptor = interceptors.errorInterceptor;
  });

  afterEach(() => {
    clearOnAuthFailure();
  });

  it("user clicks after 10 min: stale CSRF is replaced + replay succeeds + user stays logged in", async () => {
    vi.mocked(rawAxios.post).mockResolvedValueOnce({
      data: { csrfToken: "fresh-csrf" },
    });
    vi.mocked(rawAxios.request).mockResolvedValueOnce({
      status: 200,
      data: { problems: [] },
    } as never);

    const error = make401Error();
    await errorInterceptor(error);

    expect(rawAxios.post).toHaveBeenCalledTimes(1);
    expect(rawAxios.post).toHaveBeenCalledWith("/auth/refresh");
    expect(rawAxios.request).toHaveBeenCalledTimes(1);
    expect(csrf.getToken()).toBe("fresh-csrf");
    expect(onFailure).not.toHaveBeenCalled();
  });

  it("refresh itself fails (refresh cookie expired, 30+ days idle): user is logged out, no loop", async () => {
    vi.mocked(rawAxios.post).mockRejectedValue(new Error("refresh 401"));

    const error = make401Error();
    await expect(errorInterceptor(error)).rejects.toBeDefined();

    expect(onFailure).toHaveBeenCalledTimes(1);
    expect(onFailure).toHaveBeenCalledWith("refresh-failed", expect.any(Error));
    expect(rawAxios.request).not.toHaveBeenCalled();

    // Second 401 on the same config does NOT retry refresh — T2b guard.
    vi.mocked(rawAxios.post).mockClear();
    onFailure.mockClear();
    const error2 = make401Error();
    error2.config = error.config;
    await expect(errorInterceptor(error2)).rejects.toBeDefined();
    expect(rawAxios.post).not.toHaveBeenCalled();
  });
});
