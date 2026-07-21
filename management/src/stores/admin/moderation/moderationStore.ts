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
  type Appeal,
  type QueryAppealsParams,
  type CreateAppealDto,
  type ReviewAppealDto,
} from '@/api/admin/moderation'
import { extractApiErrorMessage } from '@/utils/error'

/**
 * Moderation decision + collection store.
 *
 * <p>Three collection slices (queue / reports / appeals) back the three
 * <code>useDataTable</code>-driven views; the stats slice backs the dashboard
 * and the queue header counters. Each collection fetch goes through the
 * per-key <code>abortControllers</code> registry so a stale response from an
 * earlier filter value cannot clobber fresh state when two fetches race —
 * <code>useDataTable</code> debounces new triggers but does not abort prior
 * in-flight requests, so this registry is the load-bearing stale-response
 * gate.
 *
 * <p>Action methods (claim / assign / performAction / batchAction /
 * reviewAppeal) own their post-action state reconciliation: they patch the
 * matching list index in place for non-terminal outcomes, remove terminal
 * (RESOLVED / DISMISSED) items from the queue, and refresh stats so the
 * dashboard stays in sync. The five moderation views layer their own UI
 * state (drawers, dialogs, per-form saving flags) on top of these
 * primitives.
 *
 * <p>Architectural note (architecture-review 2026-07-21, HTML1 C1): the
 * legacy collection-mutation surface (filters / pagination / setPage /
 * setLimit / hasActiveFilters / setFilters / clearFilters) and the per-form
 * loading flags (actionLoading / batchActionLoading / claimLoading) were
 * absorbed by <code>useDataTable</code> and per-view saving refs and have
 * been removed; the per-item detail-fetch surface (currentQueueItem /
 * currentAppeal and their fetchers) had no view consumers and has been
 * removed alongside.
 */
export const useModerationStore = defineStore('adminModeration', () => {
  // ============================================================================
  // Queue State
  // ============================================================================
  const queueItems = ref<ModerationQueueItem[]>([])
  const queueTotal = ref(0)
  const queueLoading = ref(false)
  const queueError = ref<string | null>(null)
  const stats = ref<ModerationStats | null>(null)
  const statsLoading = ref(false)
  const statsError = ref<string | null>(null)

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

  // ============================================================================
  // Abort Controllers
  // ============================================================================
  // One in-flight request per collection slice. useDataTable debounces new
  // triggers but does NOT abort prior in-flight requests; this registry is
  // the load-bearing gate that prevents a stale response (e.g. from an
  // earlier filter value) from clobbering fresh state when two fetches race.
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
    try {
      const item = await moderationQueueApi.claimItem(id)
      const index = queueItems.value.findIndex((i) => i.id === id)
      if (index !== -1) queueItems.value[index] = item
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to claim item:', err)
      throw err
    }
  }

  async function assignItem(id: string, data: AssignModerationDto) {
    try {
      const item = await moderationQueueApi.assignItem(id, data)
      const index = queueItems.value.findIndex((i) => i.id === id)
      if (index !== -1) queueItems.value[index] = item
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to assign item:', err)
      throw err
    }
  }

  async function unassignItem(id: string) {
    try {
      const item = await moderationQueueApi.unassignItem(id)
      const index = queueItems.value.findIndex((i) => i.id === id)
      if (index !== -1) queueItems.value[index] = item
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to unassign item:', err)
      throw err
    }
  }

  async function performAction(id: string, data: PerformModerationActionDto) {
    try {
      const item = await moderationQueueApi.performAction(id, data)
      if (item.status === 'RESOLVED' || item.status === 'DISMISSED') {
        queueItems.value = queueItems.value.filter((i) => i.id !== id)
        queueTotal.value = Math.max(0, queueTotal.value - 1)
      } else {
        const index = queueItems.value.findIndex((i) => i.id === id)
        if (index !== -1) queueItems.value[index] = item
      }
      fetchStats(true)
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to perform action:', err)
      throw err
    }
  }

  async function batchAction(data: BatchModerationActionDto) {
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

  async function reviewAppeal(id: string, data: ReviewAppealDto) {
    try {
      const appeal = await appealsApi.reviewAppeal(id, data)
      const index = appeals.value.findIndex((a) => a.id === id)
      if (index !== -1) appeals.value[index] = appeal
      return appeal
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to review appeal:', err)
      throw err
    }
  }

  async function createAppeal(data: CreateAppealDto) {
    try {
      const appeal = await appealsApi.createAppeal(data)
      return appeal
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to create appeal:', err)
      throw err
    }
  }

  // ============================================================================
  // Utility Actions
  // ============================================================================
  function clearError() {
    queueError.value = null
    reportsError.value = null
    appealsError.value = null
    statsError.value = null
  }

  function reset() {
    queueItems.value = []
    queueTotal.value = 0
    queueLoading.value = false
    queueError.value = null
    reports.value = []
    reportsTotal.value = 0
    reportsLoading.value = false
    reportsError.value = null
    appeals.value = []
    appealsTotal.value = 0
    appealsLoading.value = false
    appealsError.value = null
    stats.value = null
    statsLoading.value = false
    statsError.value = null
    abortAllRequests()
  }

  return {
    // Queue
    queueItems,
    queueTotal,
    queueLoading,
    queueError,
    stats,
    statsLoading,
    statsError,
    pendingCount,
    underReviewCount,
    abortControllers,
    abortAllRequests,
    extractErrorMessage,
    fetchQueue,
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
    // Appeals
    appeals,
    appealsTotal,
    appealsLoading,
    appealsError,
    fetchAppeals,
    reviewAppeal,
    createAppeal,
    // Utility
    clearError,
    reset,
  }
})
