import { storeToRefs } from 'pinia'
import { computed, watch } from 'vue'
import { tryOnScopeDispose, watchDebounced } from '@vueuse/core'
import { normalizeDateParams, type AuditLog, type AuditLogQueryParams } from '@/api/admin/audit'
import { useRemoteTable, type PaginationState } from '@/composables/useRemoteTable'
import { useAuditStore } from '@/stores/admin/audit'

export interface AuditFilters {
  action: string
  entityType: string
  startDate: string
  endDate: string
  performerId: string
  userId: string
}

interface AuditQuerySnapshot {
  readonly search: string
  readonly filters: Readonly<AuditFilters>
  readonly pagination: Readonly<PaginationState>
}

export function toAuditLogQueryParams(current: AuditQuerySnapshot): AuditLogQueryParams {
  const { search, filters, pagination } = current
  return normalizeDateParams({
    search: search || undefined,
    action: filters.action === 'all' ? undefined : filters.action,
    entityType: filters.entityType === 'all' ? undefined : filters.entityType,
    startDate: filters.startDate || undefined,
    endDate: filters.endDate || undefined,
    performerId: filters.performerId || undefined,
    userId: filters.userId || undefined,
    page: pagination.pageIndex + 1,
    limit: pagination.pageSize,
  })
}

export function useAuditReadWorkspace() {
  const auditStore = useAuditStore()
  const {
    logs,
    total: auditTotal,
    loading: auditLoading,
    error: auditError,
    stats,
    statsLoading,
    statsError,
  } = storeToRefs(auditStore)
  const table = useRemoteTable<AuditLog, AuditFilters, AuditLogQueryParams>({
    store: {
      items: logs,
      total: auditTotal,
      isLoading: auditLoading,
      error: auditError,
      fetch: auditStore.fetchLogs,
    },
    initialQuery: {
      filters: {
        action: 'all',
        entityType: 'all',
        startDate: '',
        endDate: '',
        performerId: '',
        userId: '',
      },
      pagination: { pageIndex: 0, pageSize: 50 },
    },
    toParams: ({ search, filters, page, limit }) =>
      toAuditLogQueryParams({
        search: search ?? '',
        filters,
        pagination: { pageIndex: page - 1, pageSize: limit },
      }),
    debounceMs: 500,
    autoLoad: true,
  })

  async function refreshStats(current: AuditQuerySnapshot = table.query.value): Promise<void> {
    try {
      await auditStore.fetchStats(toAuditLogQueryParams(current))
    } catch {
      // The store records the user-facing error; consume watcher and refresh rejections.
    }
  }

  async function refresh(): Promise<void> {
    await Promise.all([table.refresh(), refreshStats()])
  }

  watch(
    table.query,
    () => {
      auditStore.cancelStats()
      statsError.value = null
    },
    { deep: true, flush: 'sync' },
  )

  watchDebounced(
    table.query,
    (current) => {
      void refreshStats(current)
    },
    { debounce: 500, deep: true, immediate: true },
  )

  tryOnScopeDispose(() => auditStore.cancelStats())

  return {
    query: table.query,
    searchQuery: table.searchQuery,
    tablePagination: table.tablePagination,
    loading: computed(() => table.loading.value || statsLoading.value),
    data: table.data,
    total: table.total,
    error: computed(() => table.error.value ?? statsError.value),
    setSearch: table.setSearch,
    setFilters: table.setFilters,
    setPagination: table.setPagination,
    refresh,
    stats,
    actionTypeStats: computed(() => stats.value?.actionsByType ?? []),
    statsTotal: computed(() => stats.value?.totalActions ?? table.total.value),
  }
}
