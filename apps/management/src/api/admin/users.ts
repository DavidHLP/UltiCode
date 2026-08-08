import type { PageResult } from '@/shared/domain-types/src'
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export interface User {
  id: string
  username: string
  name: string
  email: string
  avatar?: string
  role: string
  isActive: boolean
  isBanned: boolean
  banReason?: string
  bannedUntil?: string
  joinedAt: string
  lastLoginAt?: string
  permissions?: UserPermission[]
  stats?: UserStats
}

export interface UserStats {
  totalSubmissions: number
  acceptedSubmissions: number
  totalSolutions: number
  streak: number
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
  isActive?: boolean
  isBanned?: boolean
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

// Backend PageResult structure

export interface CreateUserDto {
  username: string
  email: string
  name: string
  password?: string
  role?: string
  isActive?: boolean
}

export interface UpdateUserDto {
  username?: string
  email?: string
  name?: string
  role?: string
  isActive?: boolean
  avatar?: string
  bio?: string
  company?: string
  github?: string
  website?: string
  location?: string
  twitter?: string
  preferredLanguage?: string
}

export interface BanUserDto {
  reason?: string
  until?: string
}

export interface GrantPermissionDto {
  action: string
  resource: string
  expiresAt?: Date | null
}

export interface BulkActionDto {
  ids: string[]
  reason?: string
  role?: string
}

export const usersApi = {
  async getUsers(params: UserQueryParams): Promise<PageResult<User>> {
    return apiGet<PageResult<User>>('/admin/users', { params })
  },

  async getUser(id: string): Promise<User> {
    return apiGet<User>(`/admin/users/${id}`)
  },

  async createUser(data: CreateUserDto): Promise<User> {
    return apiPost<User>('/admin/users', data)
  },

  async updateUser(id: string, data: UpdateUserDto): Promise<User> {
    return apiPatch<User>(`/admin/users/${id}`, data)
  },

  async deleteUser(id: string): Promise<void> {
    await apiDelete(`/admin/users/${id}`)
  },

  async banUser(id: string, data: BanUserDto): Promise<User> {
    return apiPost<User>(`/admin/users/${id}/ban`, data)
  },

  async unbanUser(id: string): Promise<User> {
    return apiPost<User>(`/admin/users/${id}/unban`)
  },

  async grantPermission(id: string, data: GrantPermissionDto): Promise<void> {
    await apiPost(`/admin/users/${id}/permissions`, data)
  },

  async revokePermission(id: string, action: string, resource: string): Promise<void> {
    await apiDelete(`/admin/users/${id}/permissions`, {
      data: { action, resource },
    })
  },

  async bulkBan(
    ids: string[],
    reason?: string,
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    return apiPost<{ results: { id: string; success: boolean; error?: string }[] }>(
      '/admin/users/bulk-ban',
      { ids, reason },
    )
  },

  async bulkUnban(
    ids: string[],
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    return apiPost<{ results: { id: string; success: boolean; error?: string }[] }>(
      '/admin/users/bulk-unban',
      { ids },
    )
  },

  async bulkDelete(
    ids: string[],
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    return apiDelete<{ results: { id: string; success: boolean; error?: string }[] }>(
      '/admin/users/bulk-delete',
      { data: { ids } },
    )
  },

  async resetPassword(id: string, password: string): Promise<void> {
    await apiPost(`/admin/users/${id}/reset-password`, { password })
  },
}
