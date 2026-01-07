import { apiGet } from '../client'

export interface AuditLog {
  id: string
  created_at: Date
  performer_id: string
  performer?: {
    id: string
    username: string
    name: string
    role: string
  }
  user_id?: string
  user?: {
    id: string
    username: string
    name: string
  }
  action: string
  entity_type?: string
  entity_id?: string
  old_values?: unknown
  new_values?: unknown
  ip_address?: string
  user_agent?: string
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
    performer: {
      id: string
      username: string
      name: string
      role: string
    }
  }>
}

export interface AuditExportParams extends AuditLogQueryParams {
  format?: 'csv' | 'json'
}

export const auditApi = {
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
