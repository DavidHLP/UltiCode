import { apiGet, apiPost } from '@/utils/request'

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginResponse {
  csrfToken: string
  user: {
    id: string
    username: string
    name: string
    role: string
  }
}

export interface User {
  id: string
  username: string
  name: string
  email: string
  avatar?: string
  role: string
  is_active: boolean
  is_banned: boolean
  joined_at: string
  csrf_token?: string
}

export const authApi = {
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    return apiPost<LoginResponse>('/auth/login', credentials)
  },

  async logout(): Promise<void> {
    return apiPost('/auth/logout')
  },

  async getCurrentUser(): Promise<{ user: User; csrfToken?: string }> {
    return apiGet<{ user: User; csrfToken?: string }>('/auth/me')
  },

  async getPermissions(): Promise<string[]> {
    return apiGet<string[]>('/auth/permissions')
  },
}
