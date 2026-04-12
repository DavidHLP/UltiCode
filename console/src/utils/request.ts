import axios, {
  type InternalAxiosRequestConfig,
  type AxiosResponse,
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosInstance,
  type AxiosRequestHeaders,
} from "axios";
import { LOCALE_HEADER_KEY } from "@/i18n";
import { getActiveLocale } from "@/i18n/utils/locale";
import { getCsrfToken } from "@/utils/csrf";

/**
 * Standard API Response wrapper (matches backend Result<T>)
 * code: 0 = success, non-zero = error
 */
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
}

/**
 * Extended request configuration with enterprise features
 */
export interface RequestConfig extends AxiosRequestConfig {
  retry?: number;
  retryDelay?: number;
  skipErrorHandler?: boolean;
  skipResponseUnwrap?: boolean;
  requestId?: string;
}

/**
 * Custom API Error class
 */
export class ApiError extends Error {
  public code: number;
  public response?: AxiosResponse;

  constructor(message: string, code: number, response?: AxiosResponse) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.response = response;
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  static fromAxiosError(error: AxiosError): ApiError {
    const message =
      (error.response?.data as { message?: string })?.message ||
      error.message ||
      "Request failed";
    const code = error.response?.status || 0;
    return new ApiError(message, code, error.response);
  }
}

/**
 * Request metadata for tracking
 */
interface RequestMetadata {
  requestId: string;
  startTime: number;
  retryCount: number;
}

/**
 * Extended config with metadata
 */
interface ConfigWithMetadata
  extends Omit<InternalAxiosRequestConfig, "headers">,
    Omit<RequestConfig, "headers"> {
  headers: AxiosRequestHeaders;
  _metadata?: RequestMetadata;
}

/**
 * Pending requests map for deduplication
 */
const pendingRequests = new Map<string, AbortController>();
let isAuthErrorHandling = false;

/**
 * URLs that should never be deduplicated
 * These are auth-critical requests that must always go through
 */
const NON_DEDUPLICABLE_URLS = new Set([
  "/auth/me",
  "/auth/login",
  "/auth/logout",
  "/auth/register",
]);

/**
 * Generate unique request ID
 */
function generateRequestId(): string {
  return `req_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
}

/**
 * Generate request key for deduplication
 * Includes URL to allow same endpoint different params
 */
function getRequestKey(config: InternalAxiosRequestConfig): string {
  const { method, url, params, data } = config;
  return `${method}_${url}_${JSON.stringify(params)}_${JSON.stringify(data)}`;
}

/**
 * Get environment specific settings
 */
const isDevelopment = import.meta.env.DEV;
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:9001";

/**
 * Create axios instance with default config
 */
const service: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true, // Important: include cookies in all requests
  headers: {
    "Content-Type": "application/json",
  },
});

/**
 * Request interceptor with enterprise features
 */
service.interceptors.request.use(
  (config: ConfigWithMetadata) => {
    // Generate request ID for tracing
    const requestId = config.requestId || generateRequestId();
    config.headers["X-Request-ID"] = requestId;
    config._metadata = {
      requestId,
      startTime: Date.now(),
      retryCount: 0,
    };

    // Note: Auth is now handled via httpOnly cookies (withCredentials: true)
    // No need to manually attach Authorization header

    // Attach CSRF token for state-changing requests (POST, PUT, PATCH, DELETE)
    const method = config.method?.toUpperCase();
    if (
      method &&
      method !== "GET" &&
      method !== "HEAD" &&
      method !== "OPTIONS"
    ) {
      const csrfToken = getCsrfToken();
      if (csrfToken) {
        config.headers["X-CSRF-Token"] = csrfToken;
      }
    }

    // Add locale headers (both custom x-locale and standard Accept-Language)
    const activeLocale = getActiveLocale();
    config.headers[LOCALE_HEADER_KEY] = activeLocale;
    config.headers["Accept-Language"] = activeLocale;

    // Request deduplication - skip for auth-critical endpoints
    const shouldDeduplicate = !NON_DEDUPLICABLE_URLS.has(config.url || "");
    if (shouldDeduplicate) {
      const key = getRequestKey(config);
      if (pendingRequests.has(key)) {
        const controller = pendingRequests.get(key)!;
        controller.abort();
        return config;
      }

      const controller = new AbortController();
      config.signal = controller.signal;
      pendingRequests.set(key, controller);
    }

    // Log request in development
    if (isDevelopment) {
      console.debug("[API Request]", {
        baseURL: config.baseURL,
        method: config.method?.toUpperCase(),
        url: config.url,
        fullUrl: `${config.baseURL || ""}${config.url || ""}`,
        headers: config.headers,
        params: config.params,
        data: config.data,
      });
    }

    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  },
);

/**
 * Response interceptor with enterprise features
 */
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const config = response.config as ConfigWithMetadata;
    const metadata = config._metadata;

    // Remove from pending requests (skip for non-deduplicated URLs)
    if (config && !NON_DEDUPLICABLE_URLS.has(config.url || "")) {
      const key = getRequestKey(config);
      pendingRequests.delete(key);
    }

    // Log response in development
    if (isDevelopment && metadata) {
      const duration = Date.now() - metadata.startTime;
      console.debug("[API Response]", {
        status: response.status,
        duration: `${duration}ms`,
        data: response.data,
      });
    }

    // Return full response for download requests, unwrapped data otherwise
    if (config.skipResponseUnwrap) {
      return response;
    }

    // Handle wrapped API responses (Result<T> format)
    const responseData = response.data;
    if (
      responseData &&
      typeof responseData === "object" &&
      "code" in responseData
    ) {
      const apiResponse = responseData as ApiResponse<unknown>;
      // code: 0 = success, non-zero = error
      if (apiResponse.code !== 0) {
        // Treat non-zero code as error
        const error = new ApiError(
          apiResponse.message || "Request failed",
          apiResponse.code,
          response,
        );
        return Promise.reject(error);
      }
      // Return the inner data field
      return apiResponse.data;
    }

    // Return raw data for non-wrapped responses (backward compatibility)
    return response.data;
  },
  async (error: AxiosError) => {
    const config = error.config as ConfigWithMetadata | undefined;

    // Remove from pending requests (skip for non-deduplicated URLs)
    if (config && !NON_DEDUPLICABLE_URLS.has(config.url || "")) {
      const key = getRequestKey(config);
      pendingRequests.delete(key);
    }

    // Handle request cancellation
    if (error.name === "CanceledError" || error.code === "ERR_CANCELED") {
      if (isDevelopment) {
      }
      return Promise.reject(new ApiError("Request canceled", -1));
    }

    // Retry logic for network errors and 5xx
    if (config) {
      if (config.skipErrorHandler) {
        return Promise.reject(ApiError.fromAxiosError(error));
      }

      const enableRetry = config.retry === undefined ? true : config.retry > 0;
      const metadata = config._metadata || {
        requestId: "unknown",
        startTime: Date.now(),
        retryCount: 0,
      };
      const retryCount = metadata.retryCount || 0;
      const maxRetry = config.retry || 2;

      if (
        enableRetry &&
        retryCount < maxRetry &&
        (!error.response || error.response.status >= 500)
      ) {
        metadata.retryCount = retryCount + 1;
        config._metadata = metadata;

        const delay = config.retryDelay || 1000 * (retryCount + 1);
        await new Promise((resolve) => setTimeout(resolve, delay));

        if (isDevelopment) {
          console.debug("[API Retry]", {
            attempt: retryCount + 1,
            maxRetry,
            delay,
          });
        }

        return service(config);
      }
    }

    // Handle authentication errors (401/403) — debounced to prevent concurrent redirects
    if (
      error.response &&
      (error.response.status === 401 || error.response.status === 403)
    ) {
      if (!isAuthErrorHandling) {
        isAuthErrorHandling = true;
        try {
          const { useAuthStore } = await import("@/stores/auth");
          const authStore = useAuthStore();

          if (authStore.isAuthenticated) {
            authStore.clearUser();

            const { getSessionExpiredCallback } = await import(
              "@/contexts/AuthContext"
            );
            const callback = getSessionExpiredCallback();
            if (callback) {
              callback();
            }

            if (isDevelopment) {
              console.warn(
                `[API Auth Error] ${error.response.status} on ${config?.url}`,
              );
            }
          }
        } finally {
          // Reset flag after a short delay to allow future auth errors to be handled
          setTimeout(() => { isAuthErrorHandling = false; }, 1000);
        }
      }

      return Promise.reject(ApiError.fromAxiosError(error));
    }

    // Log other errors - skip auth errors (already handled above)
    if (isDevelopment && config?._metadata) {
      const status = error.response?.status;
      // Skip logging 401/403 - already handled above
      if (status !== 401 && status !== 403) {
        console.error(`[API Error] ${config._metadata.requestId}`, {
          status: error.response?.status,
          message: error.message,
          data: error.response?.data,
        });
      }
    } else if (!error.response) {
      // Network error (no response) - log it
      console.error("Request error:", error);
    }

    return Promise.reject(ApiError.fromAxiosError(error));
  },
);

/**
 * HTTP Methods with full type safety
 */

export async function apiGet<T = unknown>(
  path: string,
  init?: RequestConfig,
): Promise<T> {
  return service.get<T, T>(path, { ...init });
}

export async function apiPost<T = unknown>(
  path: string,
  body?: unknown,
  init?: RequestConfig,
): Promise<T> {
  return service.post<T, T, unknown>(path, body, { ...init });
}

export async function apiPatch<T = unknown>(
  path: string,
  body?: unknown,
  init?: RequestConfig,
): Promise<T> {
  return service.patch<T, T, unknown>(path, body, { ...init });
}

export async function apiDelete<T = unknown>(
  path: string,
  init?: RequestConfig,
): Promise<T> {
  return service.delete<T, T>(path, { ...init });
}
