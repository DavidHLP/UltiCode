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
import { triggerAuthFailure } from './auth-failure';

/**
 * Extended config with CSRF + refresh retry metadata.
 *
 * `csrfRetried` and `refreshRetried` are independent — a request can go
 * through at most one CSRF retry and at most one refresh retry.
 */
interface ConfigWithCsrfMeta {
  _metadata?: {
    csrfRetried?: boolean;
    refreshRetried?: boolean;
    [key: string]: unknown;
  };
  [key: string]: unknown;
}

export interface CsrfInterceptors {
  requestInterceptor: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig;
  responseInterceptor: (response: AxiosResponse) => AxiosResponse;
  errorInterceptor: (error: AxiosError) => Promise<unknown>;
}

/**
 * Creates axios interceptors that handle CSRF token lifecycle:
 * - Request: attaches X-CSRF-Token for state-changing methods
 * - Response: captures x-new-csrf-token header to refresh token
 * - Error (401): refreshes access token via /auth/refresh (deduped),
 *   then replays the original request once. If refresh itself fails,
 *   triggers onAuthFailure (console/management handle the UX).
 * - Error (403 CSRF): fetches new CSRF via GET /auth/me, then replays once.
 *
 * @param csrfManager - The CSRF token manager instance
 * @param baseURL - The backend API base URL (e.g., 'http://localhost:9001').
 *                  Required for token refresh requests to reach the correct origin.
 * @param refreshAccessToken - Optional refresh coordinator. When omitted,
 *                  401s fall through (legacy behavior — caller has its own
 *                  refresh path). Provide `createRefreshAccessToken(...)`
 *                  to enable auto-refresh.
 */
export function createCsrfAxiosInterceptor(
  csrfManager: CsrfTokenManager,
  baseURL?: string,
  refreshAccessToken?: () => Promise<unknown>,
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
   * Retry on 401 (refresh access token) or 403 (refresh CSRF token) mismatches,
   * at most once per category per request, then propagate.
   */
  async function errorInterceptor(
    error: AxiosError,
  ): Promise<unknown> {
    const config = error.config as (InternalAxiosRequestConfig & ConfigWithCsrfMeta) | undefined;

    // --- 401 path: refresh access token via /auth/refresh, then replay ---
    if (
      error.response?.status === 401 &&
      config &&
      !config._metadata?.refreshRetried &&
      refreshAccessToken
    ) {
      // Mark BEFORE the await so a refresh that throws still leaves
      // the config marked — without this, a refresh-failed request
      // would re-enter the branch on the next 401 and hammer refresh.
      config._metadata = {
        ...config._metadata,
        refreshRetried: true,
      };
      try {
        await refreshAccessToken();
        // Replay via rawAxios — it has `withCredentials: true` so the
        // freshly-set HttpOnly access cookie is sent, AND it has no
        // interceptors so the replay cannot re-enter this 401 branch
        // (which would mark `_metadata.refreshRetried` already and
        // therefore be a no-op, but bypassing it is cleaner).
        // The previous implementation used `await import('axios')` +
        // bare `axios(config)`, but the bare axios default has
        // `withCredentials` undefined, so the replay would drop the
        // HttpOnly cookies and the backend would re-401.
        const { rawAxios } = await import('./rawAxios');
        return rawAxios.request(config);
      } catch (refreshErr) {
        triggerAuthFailure('refresh-failed', refreshErr);
        return Promise.reject(error);
      }
    }

    // --- 403 path: refresh CSRF token via GET /auth/me, then replay ---
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
          // Use rawAxios (not the main service axios): rawAxios has
          // withCredentials and the correct baseURL, so the httpOnly access
          // cookie is sent and the URL resolves to the backend. Bare
          // `axios(config)` (the previous implementation) resolved the URL
          // against the frontend origin and dropped the cookie — see the
          // 401 retry path above for the same fix.
          const { rawAxios } = await import('./rawAxios');

          // Fetch fresh CSRF token via GET /auth/me (no CSRF validation on GET)
          const refreshUrl = baseURL ? `${baseURL}/auth/me` : '/auth/me';
          const meResponse = await rawAxios.get<{ csrfToken?: string }>(refreshUrl);

          if (meResponse.data?.csrfToken) {
            csrfManager.refreshFromResponse(meResponse.data);
          }

          // Retry original request once with fresh token via rawAxios —
          // see the 401 path above for why we bypass the main service
          // (avoiding re-entry into this interceptor and infinite loops).
          config._metadata = {
            ...config._metadata,
            csrfRetried: true,
          };

          return rawAxios.request(config);
        } catch (err) {
          // Token refresh failed — fall through to error propagation
          console.error('CSRF token refresh failed:', err);
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
