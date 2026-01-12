import { apiPost, apiGet } from './client'

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginResponse {
  access_token: string
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
}

export const authApi = {
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    return apiPost<LoginResponse>('/auth/login', credentials)
  },

  async logout(): Promise<void> {
    return apiPost('/auth/logout')
  },

  async getCurrentUser(): Promise<User> {
    return apiGet<User>('/auth/profile')
  },
}
