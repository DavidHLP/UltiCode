import { describe, it, expect, vi, beforeEach } from "vitest";
import type {
  InternalAxiosRequestConfig,
  AxiosResponse,
  AxiosError,
} from "axios";
import { createCsrfAxiosInterceptor } from "../axiosCsrfInterceptor";
import type { CsrfTokenManager } from "../csrf";

vi.mock("axios", () => {
  const mockGet = vi.fn();
  const mockAxiosFn = vi.fn();
  return {
    default: Object.assign(mockAxiosFn, {
      get: mockGet,
      __esModule: true,
    }),
  };
});

describe("axiosCsrfInterceptor", () => {
  let mockCsrfManager: CsrfTokenManager;
  let mockAxios: typeof import("axios").default;
  let interceptors: ReturnType<typeof createCsrfAxiosInterceptor>;

  beforeEach(async () => {
    vi.clearAllMocks();
    mockCsrfManager = {
      getToken: vi.fn(),
      setToken: vi.fn(),
      clearToken: vi.fn(),
      refreshFromResponse: vi.fn(),
    };
    mockAxios = (await import("axios")).default;
  });

  describe("requestInterceptor", () => {
    beforeEach(() => {
      interceptors = createCsrfAxiosInterceptor(mockCsrfManager);
    });

    it("should attach CSRF token for POST requests", () => {
      vi.mocked(mockCsrfManager.getToken).mockReturnValue("test-csrf-token");
      const config = {
        method: "post",
        headers: {} as Record<string, string>,
      } as InternalAxiosRequestConfig;

      const result = interceptors.requestInterceptor(config);

      expect(mockCsrfManager.getToken).toHaveBeenCalled();
      expect(result.headers["X-CSRF-Token"]).toBe("test-csrf-token");
    });

    it("should NOT attach CSRF token for GET requests", () => {
      vi.mocked(mockCsrfManager.getToken).mockReturnValue("test-csrf-token");
      const config = {
        method: "get",
        headers: {} as Record<string, string>,
      } as InternalAxiosRequestConfig;

      const result = interceptors.requestInterceptor(config);

      expect(mockCsrfManager.getToken).not.toHaveBeenCalled();
      expect(result.headers["X-CSRF-Token"]).toBeUndefined();
    });

    it("should attach CSRF token for PUT requests", () => {
      vi.mocked(mockCsrfManager.getToken).mockReturnValue("put-token");
      const config = {
        method: "put",
        headers: {} as Record<string, string>,
      } as InternalAxiosRequestConfig;

      const result = interceptors.requestInterceptor(config);

      expect(result.headers["X-CSRF-Token"]).toBe("put-token");
    });

    it("should attach CSRF token for PATCH requests", () => {
      vi.mocked(mockCsrfManager.getToken).mockReturnValue("patch-token");
      const config = {
        method: "patch",
        headers: {} as Record<string, string>,
      } as InternalAxiosRequestConfig;

      const result = interceptors.requestInterceptor(config);

      expect(result.headers["X-CSRF-Token"]).toBe("patch-token");
    });

    it("should attach CSRF token for DELETE requests", () => {
      vi.mocked(mockCsrfManager.getToken).mockReturnValue("delete-token");
      const config = {
        method: "delete",
        headers: {} as Record<string, string>,
      } as InternalAxiosRequestConfig;

      const result = interceptors.requestInterceptor(config);

      expect(result.headers["X-CSRF-Token"]).toBe("delete-token");
    });

    it("should NOT attach CSRF token for HEAD requests", () => {
      vi.mocked(mockCsrfManager.getToken).mockReturnValue("test-token");
      const config = {
        method: "head",
        headers: {} as Record<string, string>,
      } as InternalAxiosRequestConfig;

      const result = interceptors.requestInterceptor(config);

      expect(result.headers["X-CSRF-Token"]).toBeUndefined();
    });

    it("should NOT attach CSRF token for OPTIONS requests", () => {
      vi.mocked(mockCsrfManager.getToken).mockReturnValue("test-token");
      const config = {
        method: "options",
        headers: {} as Record<string, string>,
      } as InternalAxiosRequestConfig;

      const result = interceptors.requestInterceptor(config);

      expect(result.headers["X-CSRF-Token"]).toBeUndefined();
    });

    it("should not attach header when no token exists", () => {
      vi.mocked(mockCsrfManager.getToken).mockReturnValue(null);
      const config = {
        method: "post",
        headers: {} as Record<string, string>,
      } as InternalAxiosRequestConfig;

      const result = interceptors.requestInterceptor(config);

      expect(result.headers["X-CSRF-Token"]).toBeUndefined();
    });
  });

  describe("responseInterceptor", () => {
    beforeEach(() => {
      interceptors = createCsrfAxiosInterceptor(mockCsrfManager);
    });

    it("should capture x-new-csrf-token from 2xx responses", () => {
      const response = {
        headers: {
          "x-new-csrf-token": "new-refreshed-token",
        },
      } as AxiosResponse;

      interceptors.responseInterceptor(response);

      expect(mockCsrfManager.refreshFromResponse).toHaveBeenCalledWith({
        csrfToken: "new-refreshed-token",
      });
    });

    it("should NOT capture from non-2xx responses", () => {
      const response = {
        status: 400,
        headers: {
          "x-new-csrf-token": "some-token",
        },
      } as AxiosResponse;

      interceptors.responseInterceptor(response);

      expect(mockCsrfManager.refreshFromResponse).toHaveBeenCalled();
    });

    it("should NOT call refreshFromResponse when header is missing", () => {
      const response = {
        headers: {},
      } as AxiosResponse;

      interceptors.responseInterceptor(response);

      expect(mockCsrfManager.refreshFromResponse).not.toHaveBeenCalled();
    });

    it("should ignore non-string x-new-csrf-token", () => {
      const response = {
        headers: {
          "x-new-csrf-token": ["array-token"] as unknown,
        },
      } as AxiosResponse;

      interceptors.responseInterceptor(response);

      expect(mockCsrfManager.refreshFromResponse).not.toHaveBeenCalled();
    });
  });

  describe("errorInterceptor", () => {
    beforeEach(() => {
      interceptors = createCsrfAxiosInterceptor(mockCsrfManager);
    });

    it("should retry once on 403 CSRF error", async () => {
      const originalConfig = {
        method: "post",
        headers: {} as Record<string, string>,
        _metadata: {},
        url: "/api/test",
      } as InternalAxiosRequestConfig;

      const error = {
        response: {
          status: 403,
          data: { message: "CSRF token mismatch" },
        },
        config: originalConfig,
      } as AxiosError;

      vi.mocked(mockAxios.get).mockResolvedValue({
        data: { csrfToken: "fresh-csrf-token" },
      });

      const retryResult = { data: "retry-success" };
      vi.mocked(mockAxios).mockResolvedValueOnce(retryResult);

      const promise = interceptors.errorInterceptor(error);

      await vi.waitFor(() => {
        expect(mockAxios.get).toHaveBeenCalledWith("/auth/me", {
          withCredentials: true,
        });
      });

      await promise;

      expect(mockCsrfManager.refreshFromResponse).toHaveBeenCalledWith({
        csrfToken: "fresh-csrf-token",
      });
    });

    it("should NOT retry if already retried", async () => {
      const originalConfig = {
        method: "post",
        headers: {} as Record<string, string>,
        _metadata: { csrfRetried: true },
        url: "/api/test",
      } as InternalAxiosRequestConfig;

      const error = {
        response: {
          status: 403,
          data: { message: "CSRF token mismatch" },
        },
        config: originalConfig,
      } as AxiosError;

      const promise = interceptors.errorInterceptor(error);

      await expect(promise).rejects.toBe(error);
      expect(mockAxios.get).not.toHaveBeenCalled();
    });

    it("should NOT retry on non-CSRF 403", async () => {
      const originalConfig = {
        method: "post",
        headers: {} as Record<string, string>,
        _metadata: {},
        url: "/api/test",
      } as InternalAxiosRequestConfig;

      const error = {
        response: {
          status: 403,
          data: { message: "Access denied" },
        },
        config: originalConfig,
      } as AxiosError;

      const promise = interceptors.errorInterceptor(error);

      await expect(promise).rejects.toBe(error);
      expect(mockAxios.get).not.toHaveBeenCalled();
    });

    it("should reject if token refresh fails", async () => {
      const originalConfig = {
        method: "post",
        headers: {} as Record<string, string>,
        _metadata: {},
        url: "/api/test",
      } as InternalAxiosRequestConfig;

      const error = {
        response: {
          status: 403,
          data: { message: "CSRF token mismatch" },
        },
        config: originalConfig,
      } as AxiosError;

      vi.mocked(mockAxios.get).mockRejectedValue(new Error("Network error"));

      const promise = interceptors.errorInterceptor(error);

      await expect(promise).rejects.toBe(error);
    });

    it("should reject when error has no config", async () => {
      const error = {
        response: {
          status: 403,
          data: { message: "CSRF token mismatch" },
        },
        config: undefined,
      } as AxiosError;

      const promise = interceptors.errorInterceptor(error);

      await expect(promise).rejects.toBe(error);
    });

    it("should detect CSRF error by code 40300", async () => {
      const originalConfig = {
        method: "post",
        headers: {} as Record<string, string>,
        _metadata: {},
        url: "/api/test",
      } as InternalAxiosRequestConfig;

      const error = {
        response: {
          status: 403,
          data: { message: "Forbidden", code: 40300 },
        },
        config: originalConfig,
      } as AxiosError;

      vi.mocked(mockAxios.get).mockResolvedValue({
        data: { csrfToken: "fresh-token" },
      });

      const retryPromise = Promise.resolve({ data: "retry-response" });
      vi.mocked(mockAxios).mockResolvedValueOnce(retryPromise as unknown);

      const promise = interceptors.errorInterceptor(error);

      await vi.waitFor(() => {
        expect(mockAxios.get).toHaveBeenCalled();
      });

      await promise;

      expect(mockCsrfManager.refreshFromResponse).toHaveBeenCalled();
    });
  });
});