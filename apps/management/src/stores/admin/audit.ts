import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  auditApi,
  type AuditLog,
  type AuditLogQueryParams,
  type AuditStats,
  type AuditExportParams,
} from '@/api/admin/audit'
import { createCollectionSlice } from '@/stores/createCollectionSlice'
import { extractApiErrorMessage } from '@/utils/error'
export const useAuditStore = defineStore('adminAudit', () => {
  const collection = createCollectionSlice<AuditLog, AuditLogQueryParams>({
    load: async (params = {}, signal) => {
      const data = await auditApi.getAuditLogs(params, signal)
      return { items: data.items ?? [], total: data.total }
    },
  })
  const logs = collection.items
  const total = collection.total
  const loading = collection.isLoading
  const error = collection.error
  const stats = ref<AuditStats | null>(null)
  let statsController: AbortController | null = null
  let statsSequence = 0

  const fetchLogs = collection.fetch

  function cancelStats(): void {
    statsSequence += 1
    statsController?.abort()
    statsController = null
  }

  async function fetchStats(params?: AuditLogQueryParams) {
    statsController?.abort()
    const controller = new AbortController()
    statsController = controller
    const request = ++statsSequence
    try {
      const data = await auditApi.getAuditStats(params, controller.signal)
      if (request === statsSequence && !controller.signal.aborted) stats.value = data
      return data
    } catch (err: unknown) {
      if (controller.signal.aborted || request !== statsSequence) return null
      error.value = extractApiErrorMessage(err, 'Failed to fetch audit stats')
      console.error('Failed to fetch audit stats:', err)
      throw err
    } finally {
      if (statsController === controller) statsController = null
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
    cancelStats,
    exportLogs,
    clearError,
  }
})
