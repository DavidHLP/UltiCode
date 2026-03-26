import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'
import { toast } from 'vue-sonner'

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
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

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
    // apiGet already unwraps response.data automatically
    return apiGet<PageResult<User>>('/admin/users', { params })
  },

  async getUser(id: string): Promise<User> {
    // apiGet already unwraps response.data automatically
    return apiGet<User>(`/admin/users/${id}`)
  },

  async createUser(data: CreateUserDto): Promise<User> {
    // apiPost already unwraps response.data automatically
    const result = apiPost<User>('/admin/users', data)
    toast.success('User created successfully')
    return result
  },

  async updateUser(id: string, data: UpdateUserDto): Promise<User> {
    // apiPatch already unwraps response.data automatically
    const result = apiPatch<User>(`/admin/users/${id}`, data)
    toast.success('User updated successfully')
    return result
  },

  async deleteUser(id: string): Promise<void> {
    await apiDelete(`/admin/users/${id}`)
    toast.success('User deleted successfully')
  },

  async banUser(id: string, data: BanUserDto): Promise<User> {
    // apiPost already unwraps response.data automatically
    const result = apiPost<User>(`/admin/users/${id}/ban`, data)
    toast.success('User has been banned')
    return result
  },

  async unbanUser(id: string): Promise<User> {
    // apiPost already unwraps response.data automatically
    const result = apiPost<User>(`/admin/users/${id}/unban`)
    toast.success('User has been unbanned')
    return result
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
    // apiPost already unwraps response.data automatically
    const result = apiPost<{ results: { id: string; success: boolean; error?: string }[] }>(
      '/admin/users/bulk-ban',
      { ids, reason },
    )
    toast.success(`Batch operation processed for ${ids.length} users`)
    return result
  },

  async bulkUnban(
    ids: string[],
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    // apiPost already unwraps response.data automatically
    const result = apiPost<{ results: { id: string; success: boolean; error?: string }[] }>(
      '/admin/users/bulk-unban',
      { ids },
    )
    toast.success(`Batch operation processed for ${ids.length} users`)
    return result
  },

  async bulkDelete(
    ids: string[],
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    // apiDelete already unwraps response.data automatically
    const result = apiDelete<{ results: { id: string; success: boolean; error?: string }[] }>(
      '/admin/users/bulk-delete',
      { data: { ids } },
    )
    toast.success(`Batch operation processed for ${ids.length} users`)
    return result
  },

  async resetPassword(id: string, password: string): Promise<void> {
    await apiPost(`/admin/users/${id}/reset-password`, { password })
    toast.success('Password reset successfully')
  },
}
