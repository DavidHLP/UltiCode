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
import { isTerminalStatus } from '@/views/moderation/workflow/moderationWorkflow'
import { extractApiErrorMessage } from '@/utils/error'
import { createCollectionSlice } from '@/stores/createCollectionSlice'

/**
 * Moderation decision + collection store.
 *
 * <p>Three collection slices (queue / reports / appeals) back the three
 * <code>useRemoteTable</code>-driven views; the stats slice backs the dashboard
 * and the queue header counters. Each collection slice owns cancellation and
 * stale-response protection through <code>createCollectionSlice</code>.
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
 * absorbed by <code>useRemoteTable</code> and per-view saving refs and have
 * been removed; the per-item detail-fetch surface (currentQueueItem /
 * currentAppeal and their fetchers) had no view consumers and has been
 * removed alongside.
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
      if (isTerminalStatus(item.status)) {
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
  const fetchReports = reportsCollection.fetch

  // ============================================================================
  // Appeals Actions
  // ============================================================================
  const fetchAppeals = appealsCollection.fetch

  async function reviewAppeal(id: string, data: ReviewAppealDto) {
    try {
      const appeal = await appealsApi.reviewAppeal(id, data)
      const index = appeals.value.findIndex((a) => a.id === id)
      if (index !== -1) appeals.value[index] = appeal
      // Appeal decisions change the dashboard counts (approved/rejected
      // move items out of PENDING/UNDER_REVIEW). Refresh stats so the
      // header counters stay in sync; this was previously missing,
      // leaving the dashboard stale after every appeal review.
      fetchStats(true)
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
