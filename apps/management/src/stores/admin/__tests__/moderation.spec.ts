import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useModerationStore } from '../moderation'
import { isTerminalStatus, TERMINAL_STATUSES } from '../moderation/moderationStore'
import {
  ModerationStatus,
  ModerationActionType,
  type BatchModerationActionDto,
  type ReviewAppealDto,
  type ModerationQueueItem,
  type Appeal,
} from '@/api/admin/moderation'

vi.mock('@/api/admin/moderation', () => ({
  moderationQueueApi: {
    getQueue: vi.fn(),
    getStats: vi.fn(),
    claimItem: vi.fn(),
    performAction: vi.fn(),
    batchAction: vi.fn(),
  },
  reportsApi: { getReports: vi.fn() },
  appealsApi: {
    getAppeals: vi.fn(),
    reviewAppeal: vi.fn(),
  },
  ModerationStatus: {
    PENDING: 'PENDING',
    UNDER_REVIEW: 'UNDER_REVIEW',
    RESOLVED: 'RESOLVED',
    DISMISSED: 'DISMISSED',
    APPEAL_PENDING: 'APPEAL_PENDING',
  },
  ModerationActionType: {
    DELETED: 'DELETED',
    HIDDEN: 'HIDDEN',
    RESTORED: 'RESTORED',
    WARNED: 'WARNED',
    TEMP_BANNED: 'TEMP_BANNED',
    PERM_BANNED: 'PERM_BANNED',
    DISMISSED: 'DISMISSED',
    RESOLVED: 'RESOLVED',
    APPEAL_PENDING: 'APPEAL_PENDING',
    APPEAL_APPROVED: 'APPEAL_APPROVED',
    APPEAL_REJECTED: 'APPEAL_REJECTED',
  },
}))

import { moderationQueueApi, reportsApi, appealsApi } from '@/api/admin/moderation'

const mockedQueueApi = vi.mocked(moderationQueueApi, true)
const mockedReportsApi = vi.mocked(reportsApi, true)
const mockedAppealsApi = vi.mocked(appealsApi, true)

const flushPromises = () => new Promise<void>((resolve) => setTimeout(resolve, 0))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

function queueItem(id: string, status = ModerationStatus.PENDING): ModerationQueueItem {
  return { id, status } as unknown as ModerationQueueItem
}

describe('useModerationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts empty for queue/reports/appeals/stats', () => {
    const store = useModerationStore()

    expect(store.queueItems).toEqual([])
    expect(store.queueTotal).toBe(0)
    expect(store.queueLoading).toBe(false)
    expect(store.reports).toEqual([])
    expect(store.appeals).toEqual([])
    expect(store.stats).toBeNull()
  })

  it('returns zero counters when stats are absent', () => {
    const store = useModerationStore()

    expect(store.pendingCount).toBe(0)
    expect(store.underReviewCount).toBe(0)
  })

  it('derives dashboard counters from stats', () => {
    const store = useModerationStore()
    store.stats = { pendingCount: 7, underReviewCount: 3 } as never

    expect(store.pendingCount).toBe(7)
    expect(store.underReviewCount).toBe(3)
  })

  it('keeps terminal status policy in the decision store', () => {
    expect(Object.keys(TERMINAL_STATUSES)).toHaveLength(2)
    expect(isTerminalStatus(ModerationStatus.RESOLVED)).toBe(true)
    expect(isTerminalStatus(ModerationStatus.DISMISSED)).toBe(true)
    expect(isTerminalStatus(ModerationStatus.PENDING)).toBe(false)
    expect(isTerminalStatus(ModerationStatus.UNDER_REVIEW)).toBe(false)
    expect(isTerminalStatus(ModerationStatus.APPEAL_PENDING)).toBe(false)
  })

  it('claimItem patches the matching queue row', async () => {
    const store = useModerationStore()
    const claimed = queueItem('q1', ModerationStatus.UNDER_REVIEW)
    store.queueItems = [queueItem('q1')]
    mockedQueueApi.claimItem.mockResolvedValueOnce(claimed)

    const result = await store.claimItem('q1')

    expect(result).toBe(claimed)
    expect(store.queueItems).toStrictEqual([claimed])
  })

  it.each([ModerationStatus.RESOLVED, ModerationStatus.DISMISSED])(
    'removes a single item from the queue using returned %s status',
    async (returnedStatus) => {
      const store = useModerationStore()
      store.queueItems = [queueItem('q1'), queueItem('q2')]
      store.queueTotal = 2
      mockedQueueApi.performAction.mockResolvedValueOnce(queueItem('q1', returnedStatus))
      mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

      await store.performAction('q1', { action: ModerationActionType.WARNED })

      expect(store.queueItems.map((item) => item.id)).toEqual(['q2'])
      expect(store.queueTotal).toBe(1)
      expect(mockedQueueApi.getStats).toHaveBeenCalledTimes(1)
    },
  )

  it('bounds a single terminal decrement at zero', async () => {
    const store = useModerationStore()
    store.queueItems = [queueItem('q1')]
    store.queueTotal = 0
    mockedQueueApi.performAction.mockResolvedValueOnce(queueItem('q1', ModerationStatus.RESOLVED))
    mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

    await store.performAction('q1', { action: ModerationActionType.RESOLVED })

    expect(store.queueItems).toEqual([])
    expect(store.queueTotal).toBe(0)
  })

  it('patches a non-terminal returned status without changing total', async () => {
    const store = useModerationStore()
    const updated = queueItem('q1', ModerationStatus.UNDER_REVIEW)
    store.queueItems = [queueItem('q1')]
    store.queueTotal = 1
    mockedQueueApi.performAction.mockResolvedValueOnce(updated)
    mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

    await store.performAction('q1', { action: ModerationActionType.RESOLVED })

    expect(store.queueItems).toStrictEqual([updated])
    expect(store.queueTotal).toBe(1)
  })

  it('leaves local state and stats untouched when a single decision rejects', async () => {
    const store = useModerationStore()
    const initialItems = [queueItem('q1')]
    store.queueItems = initialItems
    store.queueTotal = 4
    store.stats = { pendingCount: 2 } as never
    store.statsError = 'previous stats error'
    const failure = new Error('decision failed')
    mockedQueueApi.performAction.mockRejectedValueOnce(failure)

    await expect(
      store.performAction('q1', { action: ModerationActionType.RESOLVED }),
    ).rejects.toBe(failure)

    expect(store.queueItems).toStrictEqual(initialItems)
    expect(store.queueTotal).toBe(4)
    expect(store.stats).toEqual({ pendingCount: 2 })
    expect(store.statsError).toBe('previous stats error')
    expect(mockedQueueApi.getStats).not.toHaveBeenCalled()
  })

  it('removes successful IDs and preserves failed IDs for a partial batch', async () => {
    const store = useModerationStore()
    store.queueItems = [queueItem('q1'), queueItem('q2'), queueItem('q3')]
    store.queueTotal = 3
    mockedQueueApi.batchAction.mockResolvedValueOnce({
      successCount: 2,
      failureCount: 1,
      errors: [{ queueId: 'q2', message: 'still pending review' }],
    } as never)
    mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

    const dto: BatchModerationActionDto = {
      queueIds: ['q1', 'q2', 'q3'],
      action: ModerationActionType.RESOLVED,
    }
    await store.batchAction(dto)

    expect(store.queueItems.map((item) => item.id)).toEqual(['q2'])
    expect(store.queueTotal).toBe(1)
    expect(mockedQueueApi.getStats).toHaveBeenCalledTimes(1)
  })

  it('keeps rows and total while still refreshing stats when a batch fully fails', async () => {
    const store = useModerationStore()
    const initialItems = [queueItem('q1'), queueItem('q2')]
    store.queueItems = initialItems
    store.queueTotal = 2
    mockedQueueApi.batchAction.mockResolvedValueOnce({
      successCount: 0,
      failureCount: 2,
      errors: [
        { queueId: 'q1', message: 'failed' },
        { queueId: 'q2', message: 'failed' },
      ],
    } as never)
    mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

    await store.batchAction({
      queueIds: ['q1', 'q2'],
      action: ModerationActionType.RESOLVED,
    })

    expect(store.queueItems).toStrictEqual(initialItems)
    expect(store.queueTotal).toBe(2)
    expect(mockedQueueApi.getStats).toHaveBeenCalledTimes(1)
  })

  it('leaves local state unchanged and rethrows a batch transport failure', async () => {
    const store = useModerationStore()
    const initialItems = [queueItem('q1')]
    store.queueItems = initialItems
    store.queueTotal = 1
    const failure = new Error('batch failed')
    mockedQueueApi.batchAction.mockRejectedValueOnce(failure)

    await expect(
      store.batchAction({ queueIds: ['q1'], action: ModerationActionType.RESOLVED }),
    ).rejects.toBe(failure)

    expect(store.queueItems).toStrictEqual(initialItems)
    expect(store.queueTotal).toBe(1)
    expect(mockedQueueApi.getStats).not.toHaveBeenCalled()
  })

  it('patches a reviewed appeal and refreshes stats with the decision DTO', async () => {
    const store = useModerationStore()
    const reviewed = { id: 'a1', status: 'APPROVED' as Appeal['status'] } as Appeal
    const dto: ReviewAppealDto = { decision: 'APPROVED' }
    store.appeals = [{ id: 'a1', status: 'PENDING' } as unknown as Appeal]
    mockedAppealsApi.reviewAppeal.mockResolvedValueOnce(reviewed)
    mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

    const result = await store.reviewAppeal('a1', dto)

    expect(result).toBe(reviewed)
    expect(store.appeals).toStrictEqual([reviewed])
    expect(mockedAppealsApi.reviewAppeal).toHaveBeenCalledWith('a1', dto)
    expect(mockedQueueApi.getStats).toHaveBeenCalledTimes(1)
  })

  it('leaves appeals unchanged and rethrows an appeal transport failure', async () => {
    const store = useModerationStore()
    const initialAppeals = [{ id: 'a1', status: 'PENDING' } as unknown as Appeal]
    store.appeals = initialAppeals
    const failure = new Error('appeal failed')
    mockedAppealsApi.reviewAppeal.mockRejectedValueOnce(failure)

    await expect(
      store.reviewAppeal('a1', { decision: 'APPROVED' }),
    ).rejects.toBe(failure)

    expect(store.appeals).toStrictEqual(initialAppeals)
    expect(mockedQueueApi.getStats).not.toHaveBeenCalled()
  })

  it('does not reject a successful decision when the best-effort stats refresh fails', async () => {
    const store = useModerationStore()
    const updated = queueItem('q1', ModerationStatus.UNDER_REVIEW)
    store.queueItems = [queueItem('q1')]
    mockedQueueApi.performAction.mockResolvedValueOnce(updated)
    mockedQueueApi.getStats.mockRejectedValueOnce(new Error('stats unavailable'))

    const result = await store.performAction('q1', { action: ModerationActionType.WARNED })
    await flushPromises()

    expect(result).toBe(updated)
    expect(store.queueItems).toStrictEqual([updated])
    expect(store.statsError).toBe('stats unavailable')
  })

  it('clearError clears queue/reports/appeals/stats errors', () => {
    const store = useModerationStore()
    store.queueError = 'qerr'
    store.reportsError = 'rerr'
    store.appealsError = 'aerr'
    store.statsError = 'serr'

    store.clearError()

    expect(store.queueError).toBeNull()
    expect(store.reportsError).toBeNull()
    expect(store.appealsError).toBeNull()
    expect(store.statsError).toBeNull()
  })

  it('reset cancels queue, reports, appeals, and stats requests', async () => {
    const store = useModerationStore()
    const queueRequest = deferred<never>()
    const reportsRequest = deferred<never>()
    const appealsRequest = deferred<never>()
    const statsRequest = deferred<never>()
    let queueSignal!: AbortSignal
    let reportsSignal!: AbortSignal
    let appealsSignal!: AbortSignal
    let statsSignal!: AbortSignal

    mockedQueueApi.getQueue.mockImplementationOnce(async (_params, signal) => {
      queueSignal = signal!
      return queueRequest.promise
    })
    mockedReportsApi.getReports.mockImplementationOnce(async (_params, signal) => {
      reportsSignal = signal!
      return reportsRequest.promise
    })
    mockedAppealsApi.getAppeals.mockImplementationOnce(async (_params, signal) => {
      appealsSignal = signal!
      return appealsRequest.promise
    })
    mockedQueueApi.getStats.mockImplementationOnce(async (signal) => {
      statsSignal = signal!
      return statsRequest.promise
    })

    const requests = [
      store.fetchQueue(),
      store.fetchReports(),
      store.fetchAppeals(),
      store.fetchStats(true),
    ]
    store.reset()

    expect(queueSignal.aborted).toBe(true)
    expect(reportsSignal.aborted).toBe(true)
    expect(appealsSignal.aborted).toBe(true)
    expect(statsSignal.aborted).toBe(true)
    expect(store.queueItems).toEqual([])
    expect(store.reports).toEqual([])
    expect(store.appeals).toEqual([])
    expect(store.stats).toBeNull()

    queueRequest.resolve(undefined as never)
    reportsRequest.resolve(undefined as never)
    appealsRequest.resolve(undefined as never)
    statsRequest.resolve(undefined as never)
    await Promise.all(requests)
  })
})
