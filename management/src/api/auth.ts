import { apiGet, apiPost } from '@/utils/request'
import type { LoginCredentials, LoginResponse, User } from '@/shared/auth-core/src/types'

export type { LoginCredentials, LoginResponse, User }

/**
 * `/auth/me` returns `{ user, csrfToken }` — typed locally because the
 * snake_case `csrf_token` field on the auth-core `User` represents the
 * auth-state shape, not the `/auth/me` response envelope.
 */
interface UserWithCsrfResponse {
  user: User
  csrfToken?: string
}

export const authApi = {
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    return apiPost<LoginResponse>('/auth/login', credentials)
  },

  async logout(): Promise<void> {
    return apiPost('/auth/logout')
  },

  async getCurrentUser(): Promise<{ user: User; csrfToken?: string }> {
    return apiGet<UserWithCsrfResponse>('/auth/me')
  },

  async getPermissions(): Promise<string[]> {
    return apiGet<string[]>('/auth/permissions')
  },
}
