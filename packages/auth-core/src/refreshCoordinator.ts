/**
 * Coordinates a single in-flight /auth/refresh call across concurrent 401s.
 *
 * Multiple concurrent 401 errors share the same refresh Promise to avoid
 * the backend's rotate-lock (`RefreshTokenService.revokeIfActive`) killing
 * tab2/tab3's refresh.
 *
 * The refresh request itself carries the current double-submit CSRF header,
 * then the response replaces both session cookies and the in-memory token.
 */
import { rawAxios } from './rawAxios';
import type { CsrfTokenManager } from './csrf';

export interface RefreshResponse {
  csrfToken?: string;
  [key: string]: unknown;
}

/**
 * Result envelope wrapping every backend response. `rawAxios` deliberately
 * mounts no interceptors (it must stay loop-free so /auth/refresh can never
 * re-enter the 401 handler), so the coordinator receives this raw envelope,
 * not the unwrapped inner payload that the main axios instance produces.
 */
interface ResultEnvelope<T> {
  code: number;
  data: T;
  message?: string;
  traceId?: string;
}

let inFlight: Promise<RefreshResponse> | null = null;

export function createRefreshAccessToken(
  csrfManager: CsrfTokenManager,
): () => Promise<RefreshResponse> {
  return async function refreshAccessToken(): Promise<RefreshResponse> {
    if (inFlight) return inFlight;
    inFlight = (async () => {
      try {
        const csrfToken = csrfManager.getToken();
        if (!csrfToken) {
          throw new Error('CSRF token is required to refresh the browser session');
        }
        const { data } = await rawAxios.post<ResultEnvelope<RefreshResponse>>(
          '/auth/refresh',
          undefined,
          { headers: { 'X-CSRF-Token': csrfToken } },
        );
        // rawAxios returns the untouched Result envelope; synchronize the
        // rotated double-submit cookie value from its nested payload.
        const inner = data?.data;
        if (inner && typeof inner.csrfToken === 'string') {
          csrfManager.refreshFromResponse(inner);
        }
        return inner ?? {};
      } finally {
        inFlight = null;
      }
    })();
    return inFlight;
  };
}

/** Test-only: reset internal state between tests. */
export function _resetRefreshCoordinator(): void {
  inFlight = null;
}
