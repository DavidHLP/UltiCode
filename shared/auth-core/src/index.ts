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
export {
  createCsrfTokenManager,
  type CsrfTokenManager,
} from './csrf';

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
  parsePermissionString,
  hasPermission,
  WILDCARD_PERMISSION,
  Permissions,
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
