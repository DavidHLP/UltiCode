import type { PageResult } from '@/shared/domain-types/src'
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

/**
 * Audit log entry with nested performer and user information.
 * All field names use camelCase following TypeScript conventions.
 */
export interface AuditLog {
  id: string
  createdAt: string
  performer?: PerformerInfo
  user?: UserInfo
  action: string
  entityType?: string
  entityId?: string
  oldValues: Record<string, unknown> | null
  newValues: Record<string, unknown> | null
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

/**
 * Normalize date params: append time portion if only date is provided.
 * - startDate without time → T00:00:00 (start of day)
 * - endDate without time → next day T00:00:00 (exclusive upper bound)
 */
export function normalizeDateParams<T extends { startDate?: string; endDate?: string }>(
  params: T,
): T {
  const p = { ...params }
  if (p.startDate && p.startDate.length === 10) {
    p.startDate = `${p.startDate}T00:00:00`
  }
  if (p.endDate && p.endDate.length === 10) {
    const next = new Date(p.endDate + 'T00:00:00')
    next.setDate(next.getDate() + 1)
    const mm = String(next.getMonth() + 1).padStart(2, '0')
    const dd = String(next.getDate()).padStart(2, '0')
    p.endDate = `${next.getFullYear()}-${mm}-${dd}T00:00:00`
  }
  return p
}

export const auditApi = {
  async getAuditLogs(params: AuditLogQueryParams = {}): Promise<PageResult<AuditLog>> {
    return apiGet<PageResult<AuditLog>>('/admin/audit/logs', {
      params: normalizeDateParams(params),
    })
  },

  async getAuditStats(params?: AuditLogQueryParams): Promise<AuditStats> {
    return apiGet<AuditStats>('/admin/audit/stats', { params: normalizeDateParams(params ?? {}) })
  },

  async exportAuditLogs(params: AuditExportParams = {}): Promise<void> {
    const { format = 'csv', ...queryParams } = params
    const filename = `audit-logs.${format}`
    await apiDownload('/admin/audit/export', filename, {
      params: { format, ...normalizeDateParams(queryParams) },
    })
  },

  async getProblemAuditLogs(
    id: string | number,
    params: AuditLogQueryParams = {},
  ): Promise<PageResult<AuditLog>> {
    return apiGet<PageResult<AuditLog>>(`/admin/problems/${id}/audit`, {
      params: normalizeDateParams(params),
    })
  },
}
