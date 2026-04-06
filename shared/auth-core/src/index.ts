// ---------------------------------------------------------------------------
// auth-core – public API
// ---------------------------------------------------------------------------

// Shared types (User, LoginCredentials, LoginResponse, Permission)
export {
  type LoginCredentials,
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

// Permission checker
export {
  type PermissionMatchMode,
  parsePermissionString,
  hasPermission,
  WILDCARD_PERMISSION,
  Permissions,
} from './permission';
