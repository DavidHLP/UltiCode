import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  auditApi,
  type AuditLog,
  type AuditLogQueryParams,
  type AuditStats,
  type AuditExportParams,
} from '@/api/admin/audit'
import { extractApiErrorMessage } from '@/utils/error'
export const useAuditStore = defineStore('adminAudit', () => {
  const logs = ref<AuditLog[]>([])
  const total = ref(0)
  const stats = ref<AuditStats | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchLogs(params: AuditLogQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const data = await auditApi.getAuditLogs(params)
      logs.value = data.items ?? []
      total.value = data.total
      return data
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch audit logs')
      console.error('Failed to fetch audit logs:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function fetchStats(params?: AuditLogQueryParams) {
    loading.value = true
    error.value = null
    try {
      const data = await auditApi.getAuditStats(params)
      stats.value = data
      return data
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch audit stats')
      console.error('Failed to fetch audit stats:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function exportLogs(params: AuditExportParams) {
    loading.value = true
    error.value = null
    try {
      await auditApi.exportAuditLogs(params)
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to export audit logs')
      console.error('Failed to export audit logs:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  return {
    logs,
    total,
    stats,
    loading,
    error,
    fetchLogs,
    fetchStats,
    exportLogs,
    clearError,
  }
})
