/**
 * Re-export seam — the actual axios instance, interceptors, retry/dedup
 * policy, error envelope and apiGet/apiPost/apiPatch/apiPut/apiDelete methods
 * live in `@ulticode/http-client`. Console only configures the seam:
 *
 *   - CSRF manager (auth-core singleton, refreshFromResponse-driven)
 *   - locale resolver (`x-locale` + `Accept-Language` headers)
 *   - 401 handler: delegated to `runSessionExpired`, the single owner for the
 *     session-expired side-effect sequence (store cleanup + AuthContext
 *     callback). See `console/src/auth/runSessionExpired.ts`.
 *   - dedup policy: dedupe all non-auth URLs (skip auth-critical only)
 *
 * Failure ownership: the propagated-401 path below and the refresh-failed
 * path (`setOnAuthFailure` in `main.ts`) both call `runSessionExpired`,
 * so there is one owner instead of two parallel callback singletons.
 * Concurrent same-reason triggers collapse to a single invocation inside
 * `shared/auth-core/src/auth-failure.ts`.
 */
import { csrfManager } from '@/shared/auth-core/src';
import { createHttpClient } from '@/shared/http-client/src';
import { getActiveLocale } from '@/i18n/utils/locale';
import { runSessionExpired } from '@/auth/runSessionExpired';

import type {
  ApiResponse,
  RequestConfig,
  AuthFailureStrategy,
  DedupPolicy,
} from '@/shared/http-client/src';

const onAuthFailure: AuthFailureStrategy = {
  kind: 'clear-and-run',
  onAuthFailure() {
    runSessionExpired();
  },
};

const dedupPolicy: DedupPolicy = 'all-non-auth';

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
});

export type { ApiResponse, RequestConfig }
export { ApiError } from '@/shared/http-client/src'
