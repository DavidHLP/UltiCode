import { defineStore } from 'pinia'
import { useQueueModule } from './queue'
import { useReportsModule } from './reports'
import { useAppealsModule } from './appeals'
import { useActionsModule } from './actions'

export const useModerationStore = defineStore('adminModeration', () => {
  const queue = useQueueModule()

  const reports = useReportsModule(queue.abortControllers)
  const appeals = useAppealsModule(queue.abortControllers, queue.actionLoading)

  const actions = useActionsModule()

  return {
    // Queue
    ...queue,
    // Reports
    ...reports,
    // Appeals
    ...appeals,
    // Actions (filters, pagination)
    ...actions,
    // Utility
    clearError() {
      queue.queueError.value = null
      queue.currentQueueItemError.value = null
      reports.reportsError.value = null
      appeals.appealsError.value = null
      appeals.currentAppealError.value = null
      queue.statsError.value = null
    },
    clearCurrentQueueItem() {
      queue.currentQueueItem.value = null
      queue.currentQueueItemError.value = null
    },
    clearCurrentAppeal() {
      appeals.currentAppeal.value = null
      appeals.currentAppealError.value = null
    },
    reset() {
      queue.queueItems.value = []
      queue.queueTotal.value = 0
      queue.queueLoading.value = false
      queue.queueError.value = null
      queue.currentQueueItem.value = null
      queue.currentQueueItemLoading.value = false
      queue.currentQueueItemError.value = null
      reports.reports.value = []
      reports.reportsTotal.value = 0
      reports.reportsLoading.value = false
      reports.reportsError.value = null
      appeals.appeals.value = []
      appeals.appealsTotal.value = 0
      appeals.appealsLoading.value = false
      appeals.appealsError.value = null
      appeals.currentAppeal.value = null
      appeals.currentAppealLoading.value = false
      appeals.currentAppealError.value = null
      queue.stats.value = null
      queue.statsLoading.value = false
      queue.statsError.value = null
      queue.actionLoading.value = false
      queue.batchActionLoading.value = false
      queue.claimLoading.value = false
      actions.filters.value = {}
      actions.pagination.value = { page: 1, limit: 20 }
      queue.abortAllRequests()
    },
  }
})
