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

let inFlight: Promise<RefreshResponse> | null = null;

export function createRefreshAccessToken(
  csrfManager: CsrfTokenManager,
): () => Promise<RefreshResponse> {
  return async function refreshAccessToken(): Promise<RefreshResponse> {
    if (inFlight) return inFlight;
    inFlight = (async () => {
      try {
        const { data } = await rawAxios.post<RefreshResponse>('/auth/refresh');
        if (data && typeof data.csrfToken === 'string') {
          csrfManager.refreshFromResponse(data);
        }
        return data;
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
