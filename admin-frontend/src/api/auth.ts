import apiClient from './client'

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
    const response = await apiClient.post<LoginResponse>('/auth/login', credentials)
    return response.data
  },

  async logout(): Promise<void> {
    await apiClient.post('/auth/logout')
  },

  async getCurrentUser(): Promise<User> {
    const response = await apiClient.get<User>('/auth/profile')
    return response.data
  },
}
