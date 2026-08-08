// ---------------------------------------------------------------------------
// Shared Auth TypeScript Interfaces
// ---------------------------------------------------------------------------
// Extracted from management/src/api/auth.ts to serve as the single source
// of truth consumed by both the `console` and `management` frontends.
// ---------------------------------------------------------------------------

/**
 * Authentication status of the current session.
 *
 * - `idle`          – initial state, no check performed yet
 * - `loading`       – checking auth state (e.g. calling /auth/me)
 * - `authenticated` – valid session detected
 * - `guest`         – no valid session (not logged in)
 * - `error`         – check failed (network error, etc.)
 */
export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'guest' | 'error';

/** Credential pair passed to the login endpoint. */
export interface LoginCredentials {
  username: string;
  password: string;
}

/**
 * Registration request payload (matches backend RegisterDTO).
 * Mirrors `LoginCredentials` plus optional email + display name.
 *
 * Note: console uses camelCase DTO shapes internally. The shared
 * RegisterForm component accepts this exact shape via its `onSubmit`
 * callback so apps can adapt (e.g. rename to `emailAddress`) at the
 * adapter boundary without changing the shared form.
 */
export interface RegisterRequest {
  username: string;
  password: string;
  email?: string;
  name?: string;
}

/** Successful login response returned by POST /auth/login. */
export interface LoginResponse {
  csrfToken: string;
  user: User;
}

/**
 * User entity stored in the backend and returned by /auth/me.
 * Field names mirror the backend API snake_case contract.
 *
 * **IMPORTANT**: This is the single source of truth for User type across the entire frontend.
 * Both console and management frontends MUST use this type from shared/auth-core.
 * Do NOT define duplicate User types in console/src/types/auth.ts or management/src/api/auth.ts.
 */
export interface User {
  id: string;
  username: string;
  name: string;
  email: string;
  avatar?: string;
  role: string;
  is_active: boolean;
  is_banned: boolean;
  joined_at: string;
  /** Present in some response shapes but not required for auth state. */
  csrf_token?: string;
}

/**
 * A parsed permission with an optional wildcard action and/or resource.
 * Re-exported by `permission.ts` so consumers of both modules get the same type.
 *
 * Usage:
 * - Use `parsePermissionString()` to convert permission strings like "READ:USER" to Permission objects
 * - Use `hasPermission()` to check if a user's permission set satisfies required permissions
 * - Use `Permissions` constants from './permission' for pre-defined permission strings
 *
 * Permission format: "ACTION:RESOURCE"
 * - Wildcards supported: "*:*" (all), "action:*" (action on any resource), "*:resource" (any action on resource)
 */
export interface Permission {
  action: string;
  resource: string;
}

/**
 * Full authentication state shape, suitable for passing around or
 * displaying auth-related UI without coupling to Vue reactivity.
 */
export interface AuthState {
  status: AuthStatus;
  user: User | null;
  error: Error | null;
}
