import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  auditApi,
  type AuditLog,
  type AuditLogQueryParams,
  type AuditStats,
  type AuditExportParams,
} from '@/api/admin/audit'
import { createCollectionSlice } from '@/stores/createCollectionSlice'
import { createValueRequest } from '@/stores/createValueRequest'
export const useAuditStore = defineStore('adminAudit', () => {
  const collection = createCollectionSlice<AuditLog, AuditLogQueryParams>({
    load: async (params = {}, signal) => {
      const data = await auditApi.getAuditLogs(params, signal)
      return { items: data.items ?? [], total: data.total }
    },
  })
  const logs = collection.items
  const total = collection.total
  const loading = computed(() => collection.isLoading.value || collection.mutationLoading.value)
  const error = computed(() => collection.error.value || collection.mutationError.value)
  const stats = ref<AuditStats | null>(null)
  const statsRequest = createValueRequest({
    errorMessage: 'Failed to fetch audit stats',
    rethrow: true,
    onError: (err) => console.error('Failed to fetch audit stats:', err),
  })

  const fetchLogs = collection.fetch

  const cancelStats = statsRequest.cancel

  async function fetchStats(params?: AuditLogQueryParams) {
    const data = await statsRequest.run((signal) => auditApi.getAuditStats(params, signal))
    if (data !== null) stats.value = data
    return data
  }

  async function exportLogs(params: AuditExportParams) {
    return collection.runMutation(async () => {
      await auditApi.exportAuditLogs(params)
    }, 'Failed to export audit logs')
  }

  return {
    logs,
    total,
    stats,
    statsLoading: statsRequest.loading,
    statsError: statsRequest.error,
    loading,
    error,
    fetchLogs,
    cancel: collection.cancel,
    fetchStats,
    cancelStats,
    exportLogs,
  }
})
