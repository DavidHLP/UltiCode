import { apiGet, apiDownload } from '@/utils/request'

/**
 * Backend Result wrapper - all API responses are wrapped in this structure
 */
export interface Result<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

/**
 * Paginated response wrapper matching backend PageResult<T>.
 * All admin APIs use this format: {items, total, page, pageSize, totalPages}.
 */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

/**
 * Audit log entry with nested performer and user information.
 * All field names use camelCase following TypeScript conventions.
 */
export interface AuditLog {
  id: string
  createdAt: Date
  performer?: PerformerInfo
  user?: UserInfo
  action: string
  entityType?: string
  entityId?: string
  oldValues?: unknown
  newValues?: unknown
  ipAddress?: string
  userAgent?: string
}

/**
 * Performer information - who performed the action.
 */
export interface PerformerInfo {
  id: string
  username: string
  name: string
  role: string
}

/**
 * Target user information - who the action was performed on.
 */
export interface UserInfo {
  id: string
  username: string
  name: string
}

export interface AuditLogQueryParams {
  search?: string
  performerId?: string
  userId?: string
  entityType?: string
  entityId?: string
  action?: string
  startDate?: string
  endDate?: string
  page?: number
  limit?: number
}

export interface AuditStats {
  totalActions: number
  actionsByEntity: Array<{
    entityType: string
    count: number
  }>
  topPerformers: Array<{
    performerId: string
    username: string
    name: string
    role: string
    count: number
  }>
  actionsByType: Array<{
    actionType: string
    count: number
  }>
}

export interface AuditExportParams extends AuditLogQueryParams {
  format?: 'csv' | 'json'
}

export const auditApi = {
  async getAuditLogs(params: AuditLogQueryParams = {}): Promise<PageResult<AuditLog>> {
    return apiGet<PageResult<AuditLog>>('/admin/audit/logs', { params })
  },

  async getAuditStats(params?: {
    startDate?: string
    endDate?: string
    performerId?: string
  }): Promise<AuditStats> {
    return apiGet<AuditStats>('/admin/audit/stats', { params })
  },

  async exportAuditLogs(params: AuditExportParams = {}): Promise<void> {
    const { format = 'csv', ...queryParams } = params
    const filename = `audit-logs.${format}`
    await apiDownload('/admin/audit/export', filename, {
      params: { format, ...queryParams },
    })
  },
}
