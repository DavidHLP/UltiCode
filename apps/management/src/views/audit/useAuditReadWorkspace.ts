import { storeToRefs } from 'pinia'
import { computed, watch } from 'vue'
import { tryOnScopeDispose } from '@vueuse/core'
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

interface AuditStatsSnapshot {
  readonly search: string
  readonly filters: Readonly<AuditFilters>
}

function toAuditFilterQueryParams(
  search: string,
  filters: Readonly<AuditFilters>,
): Omit<AuditLogQueryParams, 'page' | 'limit'> {
  return {
    search: search || undefined,
    action: filters.action === 'all' ? undefined : filters.action,
    entityType: filters.entityType === 'all' ? undefined : filters.entityType,
    startDate: filters.startDate || undefined,
    endDate: filters.endDate || undefined,
    performerId: filters.performerId || undefined,
    userId: filters.userId || undefined,
  }
}

export function toAuditLogQueryParams(current: AuditQuerySnapshot): AuditLogQueryParams {
  const { search, filters, pagination } = current
  return normalizeDateParams({
    ...toAuditFilterQueryParams(search, filters),
    page: pagination.pageIndex + 1,
    limit: pagination.pageSize,
  })
}

export function toAuditStatsQueryParams(current: AuditStatsSnapshot): AuditLogQueryParams {
  return normalizeDateParams(toAuditFilterQueryParams(current.search, current.filters))
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
      cancel: auditStore.cancel,
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

  watch(
    table.query,
    (current, _previous, onCleanup) => {
      const timer = setTimeout(() => void refreshStats(current), 500)
      onCleanup(() => clearTimeout(timer))
    },
    { deep: true, immediate: true },
  )

  tryOnScopeDispose(() => auditStore.cancelStats())

  function setSearch(search: string): void {
    auditStore.cancelStats()
    statsError.value = null
    table.setSearch(search)
  }

  function setFilters(filters: AuditFilters): Promise<void> | void {
    auditStore.cancelStats()
    statsError.value = null
    return table.setFilters(filters)
  }

  function setPagination(pagination: PaginationState): Promise<void> | void {
    auditStore.cancelStats()
    statsError.value = null
    return table.setPagination(pagination)
  }

  const searchQuery = computed({
    get: () => table.searchQuery.value,
    set: setSearch,
  })
  const tablePagination = computed({
    get: () => table.tablePagination.value,
    set: setPagination,
  })

  return {
    query: table.query,
    searchQuery,
    tablePagination,
    loading: table.loading,
    statsLoading,
    data: table.data,
    total: table.total,
    error: computed(() => table.error.value ?? statsError.value),
    setSearch,
    setFilters,
    setPagination,
    refresh,
    stats,
    actionTypeStats: computed(() => stats.value?.actionsByType ?? []),
    statsTotal: computed(() => stats.value?.totalActions ?? table.total.value),
  }
}
