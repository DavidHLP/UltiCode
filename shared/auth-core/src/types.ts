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

/** Successful login response returned by POST /auth/login. */
export interface LoginResponse {
  csrfToken: string;
  user: User;
}

/**
 * User entity stored in the backend and returned by /auth/me.
 * Field names mirror the backend API snake_case contract.
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
