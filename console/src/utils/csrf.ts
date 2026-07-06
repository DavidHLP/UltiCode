/**
 * Re-export seam — the typed CSRF token manager lives in
 * `shared/auth-core/src/csrf.ts`. This file exists so console consumers can
 * keep their existing `@/utils/csrf` import paths while the underlying
 * implementation converges with management's identical import.
 *
 * The previous bespoke console implementation added an extra `cookie`
 * fallback inside `getToken()`; the canonical path stores the token in
 * memory and refreshes it via `refreshFromResponse()` on every successful
 * login / refresh response (see `console/src/stores/auth.ts` line 207).
 * That is the same shape management uses, so a future change to CSRF token
 * storage semantics (cookie path, refresh trigger) lands in auth-core and
 * reaches both apps.
 *
 * See `/tmp/architecture-review-1783341079.html` Card 4.
 */
export {
  createCsrfTokenManager,
  type CsrfTokenManager,
} from '@/shared/auth-core/src/csrf'

/**
 * Singleton CSRF manager instance — kept as a module-level constant so the
 * existing `import { csrfManager } from '@/utils/csrf'` call sites in console
 * keep working byte-for-byte. The instance comes from auth-core.
 */
import { createCsrfTokenManager } from '@/shared/auth-core/src/csrf'
export const csrfManager = createCsrfTokenManager()

export function getCsrfToken(): string | null {
  return csrfManager.getToken()
}

export function setCsrfToken(token: string): void {
  csrfManager.setToken(token)
}

export function clearCsrfToken(): void {
  csrfManager.clearToken()
}