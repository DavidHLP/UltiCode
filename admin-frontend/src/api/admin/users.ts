import { apiGet, apiPost, apiPatch, apiDelete } from '../client'
import { toast } from 'vue-sonner'

export interface User {
  id: string
  username: string
  name: string
  email: string
  avatar?: string
  role: string
  is_active: boolean
  is_banned: boolean
  ban_reason?: string
  banned_at?: string
  joined_at: string
  last_login_at?: string
  permissions?: UserPermission[]
  stats?: UserStats
}

export interface UserStats {
  stats: {
    Easy: { count: number; total: number }
    Medium: { count: number; total: number }
    Hard: { count: number; total: number }
  }
  streak: number
  totalSolved: number
  heatmap: { date: string; level: number }[]
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

export interface BulkActionDto {
  ids: string[]
  reason?: string
  role?: string
}

export const usersApi = {
  async getUsers(params: UserQueryParams): Promise<UsersResponse> {
    const response = await apiGet<UsersResponse>('/admin/users', { params })
    return response
  },

  async getUser(id: string): Promise<User> {
    const response = await apiGet<User>(`/admin/users/${id}`)
    return response
  },

  async createUser(data: CreateUserDto): Promise<User> {
    const response = await apiPost<User>('/admin/users', data)
    toast.success('User created successfully')
    return response
  },

  async updateUser(id: string, data: UpdateUserDto): Promise<User> {
    const response = await apiPatch<User>(`/admin/users/${id}`, data)
    toast.success('User updated successfully')
    return response
  },

  async deleteUser(id: string): Promise<void> {
    await apiDelete(`/admin/users/${id}`)
    toast.success('User deleted successfully')
  },

  async banUser(id: string, data: BanUserDto): Promise<User> {
    const response = await apiPost<User>(`/admin/users/${id}/ban`, data)
    toast.success('User has been banned')
    return response
  },

  async unbanUser(id: string): Promise<User> {
    const response = await apiPost<User>(`/admin/users/${id}/unban`)
    toast.success('User has been unbanned')
    return response
  },

  async grantPermission(id: string, data: GrantPermissionDto): Promise<void> {
    await apiPost(`/admin/users/${id}/permissions`, data)
    toast.success('Permission granted successfully')
  },

  async revokePermission(id: string, action: string, resource: string): Promise<void> {
    await apiDelete(`/admin/users/${id}/permissions`, {
      data: { action, resource },
    })
    toast.success('Permission revoked successfully')
  },

  async bulkBan(
    ids: string[],
    reason?: string,
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    const response = await apiPost<{
      results: { id: string; success: boolean; error?: string }[]
    }>('/admin/users/bulk-ban', { ids, reason })
    toast.success(`Batch operation processed for ${ids.length} users`)
    return response
  },

  async bulkUnban(
    ids: string[],
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    const response = await apiPost<{
      results: { id: string; success: boolean; error?: string }[]
    }>('/admin/users/bulk-unban', { ids })
    toast.success(`Batch operation processed for ${ids.length} users`)
    return response
  },

  async bulkDelete(
    ids: string[],
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    const response = await apiDelete<{
      results: { id: string; success: boolean; error?: string }[]
    }>('/admin/users/bulk-delete', { data: { ids } })
    toast.success(`Batch operation processed for ${ids.length} users`)
    return response
  },

  async resetPassword(id: string, password: string): Promise<void> {
    await apiPost(`/admin/users/${id}/reset-password`, { password })
    toast.success('Password reset successfully')
  },
}
