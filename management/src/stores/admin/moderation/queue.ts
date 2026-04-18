import { ref, computed } from 'vue'
import {
  moderationQueueApi,
  type ModerationQueueItem,
  type ModerationStats,
  type QueryModerationQueueParams,
  type PerformModerationActionDto,
  type BatchModerationActionDto,
  type AssignModerationDto,
} from '@/api/admin/moderation'

export function useQueueModule() {
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
  const abortControllers = ref<Map<string, AbortController>>(new Map())

  const pendingCount = computed(() => stats.value?.pendingCount ?? 0)
  const underReviewCount = computed(() => stats.value?.underReviewCount ?? 0)

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

  function extractErrorMessage(err: unknown): string {
    const errorObj = err as { response?: { data?: { message?: string } }; message?: string }
    return errorObj?.response?.data?.message || errorObj?.message || 'An error occurred'
  }

  async function fetchQueue(params: QueryModerationQueueParams = {}) {
    const controller = getAbortController('queue')
    queueLoading.value = true
    queueError.value = null
    try {
      const queryParams: QueryModerationQueueParams = { ...params }
      const response = await moderationQueueApi.getQueue(queryParams, controller.signal)
      if (controller.signal.aborted) return
      queueItems.value = response.items ?? []
      queueTotal.value = response.total ?? response.meta?.total ?? 0
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
      const successfulIds = result.results.filter((r) => r.success).map((r) => r.id)
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

  return {
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
  }
}
