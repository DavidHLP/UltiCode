/**
 * Unified CSRF interceptor for axios.
 *
 * Combines request attachment, response token capture, and error retry logic
 * into a single interceptor set driven by a CsrfTokenManager.
 */
import type {
  InternalAxiosRequestConfig,
  AxiosResponse,
  AxiosError,
} from 'axios';

import type { CsrfTokenManager } from './csrf';

export interface CsrfInterceptors {
  requestInterceptor: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig;
  responseInterceptor: (response: AxiosResponse) => AxiosResponse;
  errorInterceptor: (error: AxiosError) => Promise<unknown>;
}

/**
 * Creates axios interceptors that handle CSRF token lifecycle:
 * - Request: attaches X-CSRF-Token for state-changing methods
 * - Response: captures x-new-csrf-token header to refresh token
 * - Error: retries once on 403 CSRF mismatch before propagating
 */
export function createCsrfAxiosInterceptor(
  csrfManager: CsrfTokenManager,
): CsrfInterceptors {
  /**
   * Attach CSRF token to state-changing requests.
   */
  function requestInterceptor(
    config: InternalAxiosRequestConfig,
  ): InternalAxiosRequestConfig {
    const method = config.method?.toUpperCase();
    if (method && method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
      const token = csrfManager.getToken();
      if (token) {
        config.headers['X-CSRF-Token'] = token;
      }
    }
    return config;
  }

  /**
   * Capture rotated CSRF token from response headers.
   * Backend sends x-new-csrf-token after each state-changing request.
   */
  function responseInterceptor(response: AxiosResponse): AxiosResponse {
    const newToken = response.headers['x-new-csrf-token'];
    if (newToken && typeof newToken === 'string') {
      csrfManager.refreshFromResponse({ csrfToken: newToken });
    }
    return response;
  }

  /**
   * Retry once on 403 CSRF mismatch, then propagate.
   */
  async function errorInterceptor(
    error: AxiosError,
  ): Promise<unknown> {
    const config = error.config as InternalAxiosRequestConfig | undefined;

    if (
      error.response?.status === 403 &&
      config &&
      !config._metadata?.csrfRetried
    ) {
      const errorData = error.response.data as Record<string, unknown> | undefined;
      const isCsrfError =
        typeof errorData?.message === 'string' &&
        (errorData.message.includes('CSRF') || errorData.code === 40300);

      if (isCsrfError) {
        try {
          // Dynamic import to avoid circular deps and keep peer dep optional
          const { default: axios } = await import('axios');

          // Fetch fresh CSRF token via GET /auth/me (no CSRF validation on GET)
          const meResponse = await axios.get<{ csrfToken?: string }>('/auth/me', {
            withCredentials: true,
          });

          if (meResponse.data?.csrfToken) {
            csrfManager.refreshFromResponse(meResponse.data);
          }

          // Retry original request once with fresh token
          config._metadata = {
            ...config._metadata,
            csrfRetried: true,
          };

          return axios(config);
        } catch {
          // Token refresh failed — fall through to error propagation
        }
      }
    }

    return Promise.reject(error);
  }

  return {
    requestInterceptor,
    responseInterceptor,
    errorInterceptor,
  };
}
