import { apiGet } from '@/utils/request'

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
 * Audit log entry with nested performer and user information.
 * All field names use camelCase following TypeScript conventions.
 */
export interface AuditLog {
  id: string // ID uses string to avoid precision loss
  createdAt: Date // Backend uses camelCase (LocalDateTime)
  performer?: PerformerInfo
  user?: UserInfo
  action: string
  entityType?: string
  entityId?: string // ID uses string
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
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface AuditLogsResponse {
  logs: AuditLog[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export interface AuditStats {
  totalActions: number
  actionsByEntity: Array<{
    entityType: string
    count: number
  }>
  actionsByPerformer: Array<{
    performerId: string
    count: number
  }>
  topPerformers: Array<{
    performer: PerformerInfo
  }>
}

export interface AuditExportParams extends AuditLogQueryParams {
  format?: 'csv' | 'json'
}

export const auditApi = {
  // Note: request.ts intercepts and unwraps Result<T>, returning just T
  async getAuditLogs(params: AuditLogQueryParams = {}): Promise<AuditLogsResponse> {
    return apiGet<AuditLogsResponse>('/admin/audit/logs', { params })
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
    const response = await apiGet<Blob | unknown>('/admin/audit/export', {
      params: { ...queryParams, format },
      responseType: format === 'csv' ? 'blob' : 'json',
    })

    // Create download link
    // response is already the data (Blob or JSON array) due to interceptor
    const blob =
      format === 'csv'
        ? (response as Blob)
        : new Blob([JSON.stringify(response, null, 2)], { type: 'application/json' })

    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `audit-logs-${new Date().toISOString()}.${format}`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },
}
