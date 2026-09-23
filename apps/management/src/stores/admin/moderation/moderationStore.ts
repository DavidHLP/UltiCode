import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  ModerationStatus,
  moderationQueueApi,
  reportsApi,
  appealsApi,
  type ModerationQueueItem,
  type ModerationStats,
  type QueryModerationQueueParams,
  type PerformModerationActionDto,
  type BatchModerationActionDto,
  type Report,
  type QueryReportsParams,
  type Appeal,
  type QueryAppealsParams,
  type ReviewAppealDto,
} from '@/api/admin/moderation'
import { extractApiErrorMessage } from '@/utils/error'
import { createCollectionSlice } from '@/stores/createCollectionSlice'

export const TERMINAL_STATUSES: Readonly<Partial<Record<ModerationStatus, true>>> = {
  [ModerationStatus.RESOLVED]: true,
  [ModerationStatus.DISMISSED]: true,
}

export const isTerminalStatus = (status: ModerationStatus): boolean =>
  TERMINAL_STATUSES[status] === true

/**
 * Moderation decision and collection store.
 *
 * Collection slices own queue, reports, appeals, and stats request state,
 * including cancellation and stale-response protection. Decision methods
 * own server-result reconciliation: claim patches its queue row, moderation
 * actions remove terminal results or patch non-terminal results, batch actions
 * remove successful queue IDs, and appeal reviews patch their matching appeal.
 * Successful decisions trigger a best-effort stats refresh; views retain
 * saving, dialogs, toasts, and accessibility state.
 *
 * The removed legacy collection mutations and per-form loading flags belong to
 * useRemoteTable and individual views. Assign/unassign/create appeal remain
 * HTTP adapter capabilities but are not part of this admin decision store.
 */
export const useModerationStore = defineStore('adminModeration', () => {
  // ============================================================================
  // Queue State
  // ============================================================================
  const stats = ref<ModerationStats | null>(null)
  const statsLoading = ref(false)
  const statsError = ref<string | null>(null)

  const pendingCount = computed(() => stats.value?.pendingCount ?? 0)
  const underReviewCount = computed(() => stats.value?.underReviewCount ?? 0)

  // ============================================================================
  // Collection State
  // ============================================================================
  const queue = createCollectionSlice<ModerationQueueItem, QueryModerationQueueParams>({
    load: (params = {}, signal) => moderationQueueApi.getQueue(params, signal),
  })
  const queueItems = queue.items
  const queueTotal = queue.total
  const queueLoading = queue.isLoading
  const queueError = queue.error

  const reportsCollection = createCollectionSlice<Report, QueryReportsParams>({
    load: (params = {}, signal) => reportsApi.getReports(params, signal),
  })
  const reports = reportsCollection.items
  const reportsTotal = reportsCollection.total
  const reportsLoading = reportsCollection.isLoading
  const reportsError = reportsCollection.error

  const appealsCollection = createCollectionSlice<Appeal, QueryAppealsParams>({
    load: (params = {}, signal) => appealsApi.getAppeals(params, signal),
  })
  const appeals = appealsCollection.items
  const appealsTotal = appealsCollection.total
  const appealsLoading = appealsCollection.isLoading
  const appealsError = appealsCollection.error

  // ============================================================================
  // Error Helpers
  // ============================================================================
  function extractErrorMessage(err: unknown): string {
    return extractApiErrorMessage(err, 'An error occurred')
  }

  // ============================================================================
  // Queue Actions
  // ============================================================================
  const fetchQueue = queue.fetch

  let statsController: AbortController | null = null
  let statsSequence = 0

  async function fetchStats(forceRefresh = false) {
    if (!forceRefresh && stats.value) return stats.value
    statsController?.abort()
    const controller = new AbortController()
    statsController = controller
    const request = ++statsSequence
    statsLoading.value = true
    statsError.value = null
    try {
      const data = await moderationQueueApi.getStats(controller.signal)
      if (request !== statsSequence || controller.signal.aborted) return null
      stats.value = data
      return data
    } catch (err: unknown) {
      if (request !== statsSequence || controller.signal.aborted) return null
      statsError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch stats:', err)
      return null
    } finally {
      if (statsController === controller) {
        statsController = null
        statsLoading.value = false
      }
    }
  }

  async function claimItem(id: string) {
    try {
      const item = await moderationQueueApi.claimItem(id)
      if (queueItems.value.some((queueItem) => queueItem.id === id)) {
        queueItems.value = queueItems.value.map((queueItem) =>
          queueItem.id === id ? item : queueItem,
        )
      }
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to claim item:', err)
      throw err
    }
  }

  async function performAction(id: string, data: PerformModerationActionDto) {
    try {
      const item = await moderationQueueApi.performAction(id, data)
      if (isTerminalStatus(item.status)) {
        const hasLocalRow = queueItems.value.some((queueItem) => queueItem.id === id)
        if (hasLocalRow) {
          queueItems.value = queueItems.value.filter((queueItem) => queueItem.id !== id)
          queueTotal.value = Math.max(0, queueTotal.value - 1)
        }
      } else {
        queueItems.value = queueItems.value.map((queueItem) =>
          queueItem.id === id ? item : queueItem,
        )
      }
      void fetchStats(true)
      return item
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to perform action:', err)
      throw err
    }
  }

  async function batchAction(data: BatchModerationActionDto) {
    try {
      const result = await moderationQueueApi.batchAction(data)
      const errorIds = new Set(result.errors.map((error) => error.queueId))
      const successfulIds = data.queueIds.filter((id) => !errorIds.has(id))
      queueItems.value = queueItems.value.filter((item) => !successfulIds.includes(item.id))
      queueTotal.value = Math.max(0, queueTotal.value - successfulIds.length)
      void fetchStats(true)
      return result
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to perform batch action:', err)
      throw err
    }
  }

  // ============================================================================
  // Reports Actions
  // ============================================================================
  const fetchReports = reportsCollection.fetch

  // ============================================================================
  // Appeals Actions
  // ============================================================================
  const fetchAppeals = appealsCollection.fetch

  async function reviewAppeal(id: string, data: ReviewAppealDto) {
    try {
      const appeal = await appealsApi.reviewAppeal(id, data)
      if (appeals.value.some((currentAppeal) => currentAppeal.id === id)) {
        appeals.value = appeals.value.map((currentAppeal) =>
          currentAppeal.id === id ? appeal : currentAppeal,
        )
      }
      void fetchStats(true)
      return appeal
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to review appeal:', err)
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
    queue.reset()
    reportsCollection.reset()
    appealsCollection.reset()
    statsController?.abort()
    statsController = null
    statsSequence += 1
    stats.value = null
    statsLoading.value = false
    statsError.value = null
  }

  return {
    queue,
    reportsCollection,
    appealsCollection,
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
    extractErrorMessage,
    fetchQueue,
    fetchStats,
    claimItem,
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
    // Utility
    clearError,
    reset,
  }
})
