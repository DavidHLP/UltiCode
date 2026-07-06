/**
 * Re-export seam — `User`, `LoginCredentials`, `LoginResponse`,
 * `RegisterRequest`, `Permission` and friends are owned by
 * `shared/auth-core/src/types.ts` as the single source of truth for both
 * frontends (see ADR note at the top of that file).
 *
 * Previously this file defined a parallel `User` shape with camelCase
 * fields (`isActive`, `joinedAt`, …) that no caller actually read at
 * runtime, and a parallel `LoginResponse`/`RegisterRequest` that drifted
 * from management's identical shapes. Both apps now consume the same
 * snake_case types from auth-core, removing the documented debt flagged in
 * the file's previous header comment.
 *
 * See `/tmp/architecture-review-1783341079.html` Card 3.
 */
export type {
  LoginCredentials,
  RegisterRequest,
  LoginResponse,
  User,
  Permission,
  AuthStatus,
} from '@/shared/auth-core/src/types'

/**
 * `/auth/me` returns `{ user, csrfToken }` — re-exported locally so the
 * `apiGet<UserWithCsrfResponse>` call site reads the same way it always has.
 */
export interface UserWithCsrfResponse {
  user: import('@/shared/auth-core/src/types').User
  csrfToken: string
}

/**
 * Console-only DTOs (not part of auth-core because they are not shared
 * with management) — kept here so callers that previously imported them
 * from `@/types/auth` continue to work.
 */
export interface LoginRequest {
  username: string
  password: string
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}