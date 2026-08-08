/**
 * Coordinates a single in-flight /auth/refresh call across concurrent 401s.
 *
 * Multiple concurrent 401 errors share the same refresh Promise to avoid
 * the backend's rotate-lock (`RefreshTokenService.revokeIfActive`) killing
 * tab2/tab3's refresh.
 *
 * CSRF sync seam: refresh response body contains a new csrfToken — we
 * sync it into the manager so the first write after refresh doesn't
 * trigger a 403 → /auth/me round-trip.
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
        const { data } = await rawAxios.post<ResultEnvelope<RefreshResponse>>(
          '/auth/refresh',
        );
        // `data` is the raw Result envelope; csrfToken lives in
        // `data.data`, NOT at the top level. Reading `data.csrfToken`
        // (the old code) always saw undefined, so the csrfManager was
        // never updated after refresh — every subsequent write carried a
        // stale CSRF value whose Redis grace (5min) eventually expired,
        // producing a 403 and a forced logout. This is the root cause of
        // the "前台不会续约，到点必须重新登录" symptom.
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
