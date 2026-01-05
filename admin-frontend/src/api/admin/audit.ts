import apiClient from '../client'

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
    const response = await apiClient.get<AuditLogsResponse>('/admin/audit/logs', { params })
    return response.data
  },

  async getAuditStats(params?: {
    startDate?: string
    endDate?: string
    performerId?: string
  }): Promise<AuditStats> {
    const response = await apiClient.get<AuditStats>('/admin/audit/stats', { params })
    return response.data
  },

  async exportAuditLogs(params: AuditExportParams = {}): Promise<void> {
    const { format = 'csv', ...queryParams } = params
    const response = await apiClient.get('/admin/audit/export', {
      params: { ...queryParams, format },
      responseType: format === 'csv' ? 'blob' : 'json',
    })

    // Create download link
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `audit-logs-${new Date().toISOString()}.${format}`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },
}
