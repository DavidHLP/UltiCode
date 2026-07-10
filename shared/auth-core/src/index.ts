// ---------------------------------------------------------------------------
// auth-core – public API
// ---------------------------------------------------------------------------

// Shared types (User, LoginCredentials, LoginResponse, RegisterRequest, Permission)
export {
  type LoginCredentials,
  type RegisterRequest,
  type LoginResponse,
  type User,
  type Permission,
} from './types';

// Cookie parsing utilities (exact name matching, not prefix-based)
export {
  parseCookies,
  hasCookie,
  getCookie,
  buildCookieHeader,
} from './cookie';

// CSRF token manager (survives page refresh via refreshFromResponse)
import { createCsrfTokenManager as _createCsrfTokenManager, type CsrfTokenManager } from './csrf';
export {
  createCsrfTokenManager,
  type CsrfTokenManager,
} from './csrf';

// Lazy memo singleton — every `import { csrfManager } from '@/shared/auth-core/src'`
// binds to the same instance, so `csrfManager.setToken(...)` from one caller is
// `csrfManager.getToken()` for the next. Replaces the per-app `utils/csrf.ts`
// re-export shims that previously created independent instances (the root cause
// of the `POST ... 403 CSRF token is required` regression in mgmt+console).
//
// Method forwarding is via closures so the proxy shape matches `CsrfTokenManager`
// exactly; callers keep using `csrfManager.getToken()` / `.refreshFromResponse(...)`.
let _csrfInstance: CsrfTokenManager | null = null;
function _getCsrfSingleton(): CsrfTokenManager {
  if (_csrfInstance === null) {
    _csrfInstance = _createCsrfTokenManager();
  }
  return _csrfInstance;
}
export const csrfManager: CsrfTokenManager = {
  getToken: () => _getCsrfSingleton().getToken(),
  setToken: (token) => _getCsrfSingleton().setToken(token),
  clearToken: () => _getCsrfSingleton().clearToken(),
  refreshFromResponse: (response) => _getCsrfSingleton().refreshFromResponse(response),
};
// Convenience wrappers preserved so legacy `import { getCsrfToken } from '...utils/csrf'`
// call sites (socket.ts, useContestSocket.ts) migrate with one import-path change.
export function getCsrfToken(): string | null {
  return csrfManager.getToken();
}
export function setCsrfToken(token: string): void {
  csrfManager.setToken(token);
}
export function clearCsrfToken(): void {
  csrfManager.clearToken();
}

// Auth state machine
export {
  type AuthStatus as AuthStatusFromState,
  type AuthUser,
  type AuthState,
  authStateMachine,
  createAuthStateMachine,
} from './auth-state';

// CSRF axios interceptor
export {
  createCsrfAxiosInterceptor,
  type CsrfInterceptors,
} from './axiosCsrfInterceptor';

// Permission checker
export {
  type PermissionMatchMode,
  type PermissionKey,
  parsePermissionString,
  hasPermission,
  WILDCARD_PERMISSION,
  Permissions,
  checkPermission,
  checkRole,
  checkAnyRole,
} from './permission';

// 401 auto-refresh coordinator
export {
  createRefreshAccessToken,
  _resetRefreshCoordinator,
  type RefreshResponse,
} from './refreshCoordinator';
export { rawAxios } from './rawAxios';
export {
  setOnAuthFailure,
  clearOnAuthFailure,
  triggerAuthFailure,
  type AuthFailureReason,
  type AuthFailureHandler,
} from './auth-failure';

// Utility — className concatenation (clsx + tailwind-merge).
// Single source of truth; `console/src/lib/utils.ts` and
// `management/src/lib/utils.ts` re-export from here.
export { cn } from './utils'

// Authenticated navigation policy — single seam shared by `console` and
// `management` routers (architecture-review candidate #1). Owns the
// staleness revalidation, cancellation ordering, and post-auth redirects
// that were previously duplicated line-for-line in both `router/index.ts`
// files. Per-app adapters keep their own route definitions and per-app
// post-redirects; the seam owns ordering and timing only.
export {
  createNavigationPolicy,
  installAuthNavigation,
  type NavigationAuthAdapter,
  type NavigationClock,
  type NavigationDecision,
  type NavigationPolicy,
  type NavigationPolicyOptions,
  type NavigationTarget,
  type NavigationVerdict,
  type VueRouterLike,
  type VueRouterTo,
  type InstallAuthNavigationOptions,
} from './navigation'
