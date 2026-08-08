/**
 * Re-export seam — the actual axios instance, interceptors, retry/dedup
 * policy, error envelope and api* methods live in `@ulticode/http-client`.
 * Management only configures the seam:
 *
 *   - CSRF manager (auth-core singleton, refreshFromResponse-driven)
 *   - locale resolver (`x-locale` + `Accept-Language` headers)
 *   - 401 handler: delegated to `runSessionExpired`, the single owner for the
 *     session-expired side-effect sequence (store cleanup + login redirect).
 *     See `management/src/auth/runSessionExpired.ts`.
 *   - dedup policy: dedupe GET only — never dedup state-changing methods
 *   - apiUpload + apiDownload (only management ships these)
 *
 * Failure ownership: the propagated-401 path below and the refresh-failed
 * path (`setOnAuthFailure` in `main.ts`) both call `runSessionExpired`, so
 * there is one owner instead of two parallel clearUser + push paths.
 * Concurrent same-reason triggers collapse to a single invocation inside
 * `shared/auth-core/src/auth-failure.ts`.
 */
import { csrfManager } from '@/shared/auth-core/src'
import { createHttpClient } from '@/shared/http-client/src'
import { getActiveLocale, i18n } from '@/i18n'
import { runSessionExpired } from '@/auth/runSessionExpired'

import type {
  ApiResponse,
  RequestConfig,
  AuthFailureStrategy,
  DedupPolicy,
} from '@/shared/http-client/src'

const onAuthFailure: AuthFailureStrategy = {
  kind: 'clear-and-run',
  onAuthFailure() {
    runSessionExpired()
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
  getLocale: () => getActiveLocale(),
  onAuthFailure,
  dedupPolicy,
  canceledMessage: i18n.global.t('errors.apiErrorCanceled'),
})

export type { ApiResponse, RequestConfig }
export { ApiError } from '@/shared/http-client/src'
