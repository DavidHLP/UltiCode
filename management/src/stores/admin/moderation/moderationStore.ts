import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  moderationQueueApi,
  reportsApi,
  appealsApi,
  type ModerationQueueItem,
  type ModerationStats,
  type QueryModerationQueueParams,
  type PerformModerationActionDto,
  type BatchModerationActionDto,
  type AssignModerationDto,
  type Report,
  type QueryReportsParams,
  type ModeratableEntityType,
  type Appeal,
  type QueryAppealsParams,
  type CreateAppealDto,
  type ReviewAppealDto,
  type ModerationStatus,
  type ReportCategory,
} from '@/api/admin/moderation'
import { extractApiErrorMessage } from '@/utils/error'

export const useModerationStore = defineStore('adminModeration', () => {
  // ============================================================================
  // Queue State
  // ============================================================================
  const queueItems = ref<ModerationQueueItem[]>([])
  const queueTotal = ref(0)
  const queueLoading = ref(false)
  const queueError = ref<string | null>(null)
  const currentQueueItem = ref<ModerationQueueItem | null>(null)
  const currentQueueItemLoading = ref(false)
  const currentQueueItemError = ref<string | null>(null)
  const stats = ref<ModerationStats | null>(null)
  const statsLoading = ref(false)
  const statsError = ref<string | null>(null)
  const actionLoading = ref(false)
  const batchActionLoading = ref(false)
  const claimLoading = ref(false)

  const pendingCount = computed(() => stats.value?.pendingCount ?? 0)
  const underReviewCount = computed(() => stats.value?.underReviewCount ?? 0)

  // ============================================================================
  // Reports State
  // ============================================================================
  const reports = ref<Report[]>([])
  const reportsTotal = ref(0)
  const reportsLoading = ref(false)
  const reportsError = ref<string | null>(null)

  // ============================================================================
  // Appeals State
  // ============================================================================
  const appeals = ref<Appeal[]>([])
  const appealsTotal = ref(0)
  const appealsLoading = ref(false)
  const appealsError = ref<string | null>(null)
  const currentAppeal = ref<Appeal | null>(null)
  const currentAppealLoading = ref(false)
  const currentAppealError = ref<string | null>(null)

  // ============================================================================
  // Actions State (filters / pagination)
  // ============================================================================
  const filters = ref<{
    status?: ModerationStatus
    category?: ReportCategory
    entityType?: ModeratableEntityType
    assignedTo?: string
  }>({})

  const pagination = ref({
    page: 1,
    limit: 20,
  })

  const hasActiveFilters = computed(() => {
    return Boolean(
      filters.value.status ||
      filters.value.category ||
      filters.value.entityType ||
      filters.value.assignedTo,
    )
  })

  // ============================================================================
  // Abort Controllers
  // ============================================================================
  const abortControllers = ref<Map<string, AbortController>>(new Map())

  function getAbortController(key: string): AbortController {
    const controller = abortControllers.value.get(key)
    if (controller) controller.abort()
    const newController = new AbortController()
    abortControllers.value.set(key, newController)
    return newController
  }

  function abortAllRequests() {
    abortControllers.value.forEach((controller) => controller.abort())
    abortControllers.value.clear()
  }

  // ============================================================================
  // Error Helpers
  // ============================================================================
  function extractErrorMessage(err: unknown): string {
    return extractApiErrorMessage(err, 'An error occurred')
  }

  // ============================================================================
  // Queue Actions
  // ============================================================================
  async function fetchQueue(params: QueryModerationQueueParams = {}) {
    const controller = getAbortController('queue')
    queueLoading.value = true
    queueError.value = null
    try {
      const queryParams: QueryModerationQueueParams = { ...params }
      const response = await moderationQueueApi.getQueue(queryParams, controller.signal)
      if (controller.signal.aborted) return
      queueItems.value = response.items ?? []
      queueTotal.value = response.total
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return
      queueError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch queue:', err)
    } finally {
      if (abortControllers.value.get('queue') === controller) queueLoading.value = false
    }
  }

  async function fetchQueueItem(id: string, forceRefresh = false) {
    if (!forceRefresh && currentQueueItem.value?.id === id) return currentQueueItem.value
    const controller = getAbortController('queueItem')
    currentQueueItemLoading.value = true
    currentQueueItemError.value = null
    try {
      const item = await moderationQueueApi.getQueueItem(id, controller.signal)
      if (controller.signal.aborted) return null
      currentQueueItem.value = item
      return item
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return null
      currentQueueItemError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch queue item:', err)
      return null
    } finally {
      if (abortControllers.value.get('queueItem') === controller)
        currentQueueItemLoading.value = false
    }
  }

  async function fetchStats(forceRefresh = false) {
    if (!forceRefresh && stats.value) return stats.value
    const controller = getAbortController('stats')
    statsLoading.value = true
    statsError.value = null
    try {
      const data = await moderationQueueApi.getStats(controller.signal)
      if (controller.signal.aborted) return null
      stats.value = data
      return data
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return null
      statsError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch stats:', err)
      return null
    } finally {
      if (abortControllers.value.get('stats') === controller) statsLoading.value = false
    }
  }

  async function claimItem(id: string) {
    claimLoading.value = true
    try {
      const item = await moderationQueueApi.claimItem(id)
      const index = queueItems.value.findIndex((i) => i.id === id)
      if (index !== -1) queueItems.value[index] = item
      if (currentQueueItem.value?.id === id) currentQueueItem.value = item
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to claim item:', err)
      throw err
    } finally {
      claimLoading.value = false
    }
  }

  async function assignItem(id: string, data: AssignModerationDto) {
    actionLoading.value = true
    try {
      const item = await moderationQueueApi.assignItem(id, data)
      const index = queueItems.value.findIndex((i) => i.id === id)
      if (index !== -1) queueItems.value[index] = item
      if (currentQueueItem.value?.id === id) currentQueueItem.value = item
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to assign item:', err)
      throw err
    } finally {
      actionLoading.value = false
    }
  }

  async function unassignItem(id: string) {
    actionLoading.value = true
    try {
      const item = await moderationQueueApi.unassignItem(id)
      const index = queueItems.value.findIndex((i) => i.id === id)
      if (index !== -1) queueItems.value[index] = item
      if (currentQueueItem.value?.id === id) currentQueueItem.value = item
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to unassign item:', err)
      throw err
    } finally {
      actionLoading.value = false
    }
  }

  async function performAction(id: string, data: PerformModerationActionDto) {
    actionLoading.value = true
    try {
      const item = await moderationQueueApi.performAction(id, data)
      if (item.status === 'RESOLVED' || item.status === 'DISMISSED') {
        queueItems.value = queueItems.value.filter((i) => i.id !== id)
        queueTotal.value = Math.max(0, queueTotal.value - 1)
      } else {
        const index = queueItems.value.findIndex((i) => i.id === id)
        if (index !== -1) queueItems.value[index] = item
      }
      if (currentQueueItem.value?.id === id) currentQueueItem.value = item
      fetchStats(true)
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to perform action:', err)
      throw err
    } finally {
      actionLoading.value = false
    }
  }

  async function batchAction(data: BatchModerationActionDto) {
    batchActionLoading.value = true
    try {
      const result = await moderationQueueApi.batchAction(data)
      const errorIds = result.errors.map((e) => e.queueId)
      const successfulIds = data.queueIds.filter((id) => !errorIds.includes(id))
      queueItems.value = queueItems.value.filter((i) => !successfulIds.includes(i.id))
      queueTotal.value = Math.max(0, queueTotal.value - successfulIds.length)
      fetchStats(true)
      return result
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to perform batch action:', err)
      throw err
    } finally {
      batchActionLoading.value = false
    }
  }

  // ============================================================================
  // Reports Actions
  // ============================================================================
  async function fetchReports(params: QueryReportsParams = {}) {
    const controller = getAbortController('reports')
    reportsLoading.value = true
    reportsError.value = null
    try {
      const response = await reportsApi.getReports(params, controller.signal)
      if (controller.signal.aborted) return
      reports.value = response.items ?? []
      reportsTotal.value = response.total
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return
      reportsError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch reports:', err)
    } finally {
      if (abortControllers.value.get('reports') === controller) reportsLoading.value = false
    }
  }

  async function fetchReportsByEntity(entityType: ModeratableEntityType, entityId: string) {
    const controller = getAbortController('entityReports')
    try {
      const data = await reportsApi.getReportsByEntity(entityType, entityId, controller.signal)
      if (controller.signal.aborted) return []
      return data
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return []
      console.error('[ModerationStore] Failed to fetch entity reports:', err)
      return []
    }
  }

  // ============================================================================
  // Appeals Actions
  // ============================================================================
  async function fetchAppeals(params: QueryAppealsParams = {}) {
    const controller = getAbortController('appeals')
    appealsLoading.value = true
    appealsError.value = null
    try {
      const response = await appealsApi.getAppeals(params, controller.signal)
      if (controller.signal.aborted) return
      appeals.value = response.items ?? []
      appealsTotal.value = response.total
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return
      appealsError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch appeals:', err)
    } finally {
      if (abortControllers.value.get('appeals') === controller) appealsLoading.value = false
    }
  }

  async function fetchAppeal(id: string, forceRefresh = false) {
    if (!forceRefresh && currentAppeal.value?.id === id) return currentAppeal.value
    const controller = getAbortController('appeal')
    currentAppealLoading.value = true
    currentAppealError.value = null
    try {
      const appeal = await appealsApi.getAppeal(id, controller.signal)
      if (controller.signal.aborted) return null
      currentAppeal.value = appeal
      return appeal
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return null
      currentAppealError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch appeal:', err)
      return null
    } finally {
      if (abortControllers.value.get('appeal') === controller) currentAppealLoading.value = false
    }
  }

  async function reviewAppeal(id: string, data: ReviewAppealDto) {
    actionLoading.value = true
    try {
      const appeal = await appealsApi.reviewAppeal(id, data)
      const index = appeals.value.findIndex((a) => a.id === id)
      if (index !== -1) appeals.value[index] = appeal
      if (currentAppeal.value?.id === id) currentAppeal.value = appeal
      return appeal
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to review appeal:', err)
      throw err
    } finally {
      actionLoading.value = false
    }
  }

  async function createAppeal(data: CreateAppealDto) {
    actionLoading.value = true
    try {
      const appeal = await appealsApi.createAppeal(data)
      return appeal
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to create appeal:', err)
      throw err
    } finally {
      actionLoading.value = false
    }
  }

  // ============================================================================
  // Filter / Pagination Actions
  // ============================================================================
  function setFilters(newFilters: Partial<typeof filters.value>) {
    filters.value = { ...filters.value, ...newFilters }
    pagination.value.page = 1
  }

  function clearFilters() {
    filters.value = {}
    pagination.value.page = 1
  }

  function setPage(page: number) {
    pagination.value.page = page
  }

  function setLimit(limit: number) {
    pagination.value.limit = limit
    pagination.value.page = 1
  }

  // ============================================================================
  // Utility Actions
  // ============================================================================
  function clearError() {
    queueError.value = null
    currentQueueItemError.value = null
    reportsError.value = null
    appealsError.value = null
    currentAppealError.value = null
    statsError.value = null
  }

  function clearCurrentQueueItem() {
    currentQueueItem.value = null
    currentQueueItemError.value = null
  }

  function clearCurrentAppeal() {
    currentAppeal.value = null
    currentAppealError.value = null
  }

  function reset() {
    queueItems.value = []
    queueTotal.value = 0
    queueLoading.value = false
    queueError.value = null
    currentQueueItem.value = null
    currentQueueItemLoading.value = false
    currentQueueItemError.value = null
    reports.value = []
    reportsTotal.value = 0
    reportsLoading.value = false
    reportsError.value = null
    appeals.value = []
    appealsTotal.value = 0
    appealsLoading.value = false
    appealsError.value = null
    currentAppeal.value = null
    currentAppealLoading.value = false
    currentAppealError.value = null
    stats.value = null
    statsLoading.value = false
    statsError.value = null
    actionLoading.value = false
    batchActionLoading.value = false
    claimLoading.value = false
    filters.value = {}
    pagination.value = { page: 1, limit: 20 }
    abortAllRequests()
  }

  return {
    // Queue
    queueItems,
    queueTotal,
    queueLoading,
    queueError,
    currentQueueItem,
    currentQueueItemLoading,
    currentQueueItemError,
    stats,
    statsLoading,
    statsError,
    actionLoading,
    batchActionLoading,
    claimLoading,
    pendingCount,
    underReviewCount,
    abortControllers,
    abortAllRequests,
    extractErrorMessage,
    fetchQueue,
    fetchQueueItem,
    fetchStats,
    claimItem,
    assignItem,
    unassignItem,
    performAction,
    batchAction,
    // Reports
    reports,
    reportsTotal,
    reportsLoading,
    reportsError,
    fetchReports,
    fetchReportsByEntity,
    // Appeals
    appeals,
    appealsTotal,
    appealsLoading,
    appealsError,
    currentAppeal,
    currentAppealLoading,
    currentAppealError,
    fetchAppeals,
    fetchAppeal,
    reviewAppeal,
    createAppeal,
    // Actions (filters, pagination)
    filters,
    pagination,
    hasActiveFilters,
    setFilters,
    clearFilters,
    setPage,
    setLimit,
    // Utility
    clearError,
    clearCurrentQueueItem,
    clearCurrentAppeal,
    reset,
  }
})
