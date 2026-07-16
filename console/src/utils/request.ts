/**
 * Re-export seam — the actual axios instance, interceptors, retry/dedup
 * policy, error envelope and apiGet/apiPost/apiPatch/apiPut/apiDelete methods
 * live in `@ulticode/http-client`. Console only configures the seam:
 *
 *   - CSRF manager (auth-core singleton, refreshFromResponse-driven)
 *   - locale resolver (`x-locale` + `Accept-Language` headers)
 *   - 401 handler: clear user state, then run the session-expired callback
 *     owned by `contexts/AuthContext` (registered once in `main.ts`).
 *   - dedup policy: dedupe all non-auth URLs (skip auth-critical only)
 *
 * Failure ownership: the propagated-401 path below and the refresh-failed
 * path (`setOnAuthFailure` in `main.ts`) both funnel through AuthContext's
 * single session-expired callback, so there is one redirect owner instead
 * of two parallel callback singletons.
 */
import { csrfManager } from '@/shared/auth-core/src'
import { createHttpClient } from '@/shared/http-client/src'
import { getActiveLocale } from '@/i18n/utils/locale'

import type {
  ApiResponse,
  RequestConfig,
  AuthFailureStrategy,
  DedupPolicy,
} from '@/shared/http-client/src'

const onAuthFailure: AuthFailureStrategy = {
  kind: 'clear-and-run',
  async onAuthFailure() {
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()
    if (authStore.isAuthenticated) {
      authStore.clearUser()
    }
    // Single failure owner: AuthContext's session-expired callback,
    // registered in main.ts. Both this propagated-401 path and the
    // refresh-failed path (setOnAuthFailure in main.ts) resolve to the
    // same callback — one redirect owner, no duplicate singletons.
    const { getSessionExpiredCallback } = await import('@/contexts/AuthContext')
    getSessionExpiredCallback()?.()
  },
}

const dedupPolicy: DedupPolicy = 'all-non-auth'

export const {
  apiGet,
  apiPost,
  apiPatch,
  apiPut,
  apiDelete,
  createAbortController,
} = createHttpClient({
  csrfManager,
  getLocale: () => getActiveLocale(),
  onAuthFailure,
  dedupPolicy,
})

export type { ApiResponse, RequestConfig }
export { ApiError } from '@/shared/http-client/src'