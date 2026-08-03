import { describe, it, expect } from "vitest";
import { rawAxios } from "../rawAxios";

/**
 * rawAxios must remain structurally isolated from the main axios instance.
 *
 * The whole point: refresh calls go through this instance, which carries
 * NO interceptors (CSRF, error retry, etc.). If anything ever hooks into
 * rawAxios, the 401-refresh path could re-enter the same interceptors
 * and infinite-loop. These tests are a regression guard.
 */
describe("rawAxios", () => {
  it("is an independent instance with no request interceptors", () => {
    expect(rawAxios.interceptors.request.handlers).toHaveLength(0);
  });

  it("is an independent instance with no response interceptors", () => {
    expect(rawAxios.interceptors.response.handlers).toHaveLength(0);
  });

  it("has withCredentials enabled (HttpOnly cookies must be sent on /auth/refresh)", () => {
    expect(rawAxios.defaults.withCredentials).toBe(true);
  });

  it("uses VITE_API_BASE_URL or falls back to the same-origin /api gateway", () => {
    // Don't assert exact value (env-driven); both absolute deployments and the
    // relative local gateway are valid refresh targets.
    expect(typeof rawAxios.defaults.baseURL).toBe("string");
    expect(rawAxios.defaults.baseURL).toMatch(/^(https?:\/\/|\/api(?:\/|$))/);
  });
});
