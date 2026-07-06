/**
 * Re-export seam — the actual axios instance, interceptors, retry/dedup
 * policy, error envelope and apiGet/apiPost/apiPatch/apiPut/apiDelete methods
 * live in `@ulticode/http-client`. Console only configures the seam:
 *
 *   - CSRF manager (auth-core singleton, refreshFromResponse-driven)
 *   - locale resolver (`x-locale` + `Accept-Language` headers)
 *   - 401 handler (clear user state, then notify the active session-expired
 *     callback registered by the AppLayout)
 *   - dedup policy: dedupe all non-auth URLs (skip auth-critical only)
 *
 * See `/tmp/architecture-review-1783341079.html` Card 2.
 */
import { createCsrfTokenManager } from '@/shared/auth-core/src/csrf'
import { createHttpClient } from '@/shared/http-client/src'
import { getActiveLocale } from '@/i18n/utils/locale'
import { LOCALE_HEADER_KEY } from '@/i18n'

import type {
  ApiResponse,
  RequestConfig,
  AuthFailureStrategy,
  DedupPolicy,
} from '@/shared/http-client/src'

const csrfManager = createCsrfTokenManager()

/** Late-bound reference to the AppLayout's session-expired callback. */
let sessionExpiredCallback: (() => void) | null = null

export function setSessionExpiredCallback(cb: (() => void) | null): void {
  sessionExpiredCallback = cb
}

const onAuthFailure: AuthFailureStrategy = {
  kind: 'clear-and-run',
  async onAuthFailure() {
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()
    if (authStore.isAuthenticated) {
      authStore.clearUser()
    }
    if (sessionExpiredCallback) sessionExpiredCallback()
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
  getLocale: () => {
    const locale = getActiveLocale()
    void LOCALE_HEADER_KEY
    return locale
  },
  onAuthFailure,
  dedupPolicy,
})

export type { ApiResponse, RequestConfig }
export { ApiError } from '@/shared/http-client/src'