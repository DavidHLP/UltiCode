/**
 * Re-export seam — the actual axios instance, interceptors, retry/dedup
 * policy, error envelope and api* methods live in `@ulticode/http-client`.
 * Management only configures the seam:
 *
 *   - CSRF manager (auth-core singleton, refreshFromResponse-driven)
 *   - locale resolver (`x-locale` + `Accept-Language` headers)
 *   - 401 handler: clear user state then `router.push('/login')`
 *   - dedup policy: dedupe GET only — never dedup state-changing methods
 *   - apiUpload + apiDownload (only management ships these)
 *
 * See `/tmp/architecture-review-1783341079.html` Card 2.
 */
import { createCsrfTokenManager } from '@/shared/auth-core/src/csrf'
import { createHttpClient } from '@/shared/http-client/src'
import { LOCALE_HEADER_KEY, getActiveLocale, i18n } from '@/i18n'
import router from '@/router'

import type {
  ApiResponse,
  RequestConfig,
  AuthFailureStrategy,
  DedupPolicy,
} from '@/shared/http-client/src'

const csrfManager = createCsrfTokenManager()

const onAuthFailure: AuthFailureStrategy = {
  kind: 'redirect-login',
  onAuthFailure(path: string) {
    void import('@/stores/auth').then(({ useAuthStore }) => {
      const authStore = useAuthStore()
      if (authStore.isAuthenticated) {
        authStore.clearUser()
      }
    })
    if (router.currentRoute.value.name !== 'login') {
      router.push(path)
    }
  },
}

const dedupPolicy: DedupPolicy = 'non-auth-readonly'

export const {
  apiGet,
  apiPost,
  apiPatch,
  apiPut,
  apiDelete,
  apiUpload,
  apiDownload,
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
  canceledMessage: i18n.global.t('errors.apiErrorCanceled'),
})

export type { ApiResponse, RequestConfig }
export { ApiError } from '@/shared/http-client/src'
