import apiClient from '../client'

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
  last_login_at?: string
  permissions?: UserPermission[]
}

export interface UserPermission {
  action: string
  resource: string
  source: 'role' | 'direct'
  expiresAt: Date | null
}

export interface UserQueryParams {
  search?: string
  role?: string
  is_active?: boolean
  is_banned?: boolean
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface UsersResponse {
  data: User[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export interface CreateUserDto {
  username: string
  email: string
  name: string
  password?: string
  role?: string
  is_active?: boolean
}

export interface UpdateUserDto {
  username?: string
  email?: string
  name?: string
  role?: string
  is_active?: boolean
  avatar?: string
  bio?: string
  company?: string
  github?: string
  website?: string
  location?: string
  twitter?: string
  preferred_language?: string
}

export interface BanUserDto {
  reason?: string
  until?: string
}

export interface GrantPermissionDto {
  action: string
  resource: string
  expires_at?: string
}

export const usersApi = {
  async getUsers(params: UserQueryParams): Promise<UsersResponse> {
    const response = await apiClient.get<UsersResponse>('/admin/users', { params })
    return response.data
  },

  async getUser(id: string): Promise<User> {
    const response = await apiClient.get<User>(`/admin/users/${id}`)
    return response.data
  },

  async createUser(data: CreateUserDto): Promise<User> {
    const response = await apiClient.post<User>('/admin/users', data)
    return response.data
  },

  async updateUser(id: string, data: UpdateUserDto): Promise<User> {
    const response = await apiClient.patch<User>(`/admin/users/${id}`, data)
    return response.data
  },

  async deleteUser(id: string): Promise<void> {
    await apiClient.delete(`/admin/users/${id}`)
  },

  async banUser(id: string, data: BanUserDto): Promise<User> {
    const response = await apiClient.post<User>(`/admin/users/${id}/ban`, data)
    return response.data
  },

  async unbanUser(id: string): Promise<User> {
    const response = await apiClient.post<User>(`/admin/users/${id}/unban`)
    return response.data
  },

  async grantPermission(id: string, data: GrantPermissionDto): Promise<void> {
    await apiClient.post(`/admin/users/${id}/permissions`, data)
  },

  async revokePermission(id: string, action: string, resource: string): Promise<void> {
    await apiClient.delete(`/admin/users/${id}/permissions`, {
      data: { action, resource },
    })
  },
}
