import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useModerationStore } from '../moderation'
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
    assignItem: vi.fn(),
    unassignItem: vi.fn(),
    performAction: vi.fn(),
    batchAction: vi.fn(),
  },
  reportsApi: { getReports: vi.fn() },
  appealsApi: {
    getAppeals: vi.fn(),
    createAppeal: vi.fn(),
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

import { moderationQueueApi, appealsApi } from '@/api/admin/moderation'

const mockedQueueApi = vi.mocked(moderationQueueApi, true)
const mockedAppealsApi = vi.mocked(appealsApi, true)

describe('useModerationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('starts empty for queue/reports/appeals/stats', () => {
      const store = useModerationStore()
      expect(store.queueItems).toEqual([])
      expect(store.queueTotal).toBe(0)
      expect(store.queueLoading).toBe(false)
      expect(store.reports).toEqual([])
      expect(store.appeals).toEqual([])
      expect(store.stats).toBeNull()
    })
  })

  describe('computed properties', () => {
    it('pendingCount returns 0 when stats is null', () => {
      const store = useModerationStore()
      expect(store.pendingCount).toBe(0)
    })

    it('pendingCount reflects stats value', () => {
      const store = useModerationStore()
      store.stats = { pendingCount: 7 } as never
      expect(store.pendingCount).toBe(7)
    })

    it('underReviewCount returns 0 when stats is null', () => {
      const store = useModerationStore()
      expect(store.underReviewCount).toBe(0)
    })

    it('underReviewCount reflects stats value', () => {
      const store = useModerationStore()
      store.stats = { underReviewCount: 3 } as never
      expect(store.underReviewCount).toBe(3)
    })
  })

  describe('decision lifecycle', () => {
    it('claimItem patches the matching queueItem index in place', async () => {
      const store = useModerationStore()
      const claimed = {
        id: 'q1',
        status: ModerationStatus.UNDER_REVIEW,
        assignedToId: 'u1',
      } as ModerationQueueItem
      store.queueItems = [
        { id: 'q1', status: ModerationStatus.PENDING } as unknown as ModerationQueueItem,
      ]
      mockedQueueApi.claimItem.mockResolvedValueOnce(claimed)

      const result = await store.claimItem('q1')

      expect(result).toBe(claimed)
      expect(store.queueItems).toHaveLength(1)
      expect(store.queueItems[0]).toStrictEqual(claimed)
    })

    it('performAction with RESOLVED removes the item, decrements total, refreshes stats', async () => {
      const store = useModerationStore()
      store.queueItems = [
        { id: 'q1' } as unknown as ModerationQueueItem,
        { id: 'q2' } as unknown as ModerationQueueItem,
      ]
      store.queueTotal = 2
      mockedQueueApi.performAction.mockResolvedValueOnce({
        id: 'q1',
        status: ModerationStatus.RESOLVED,
      } as ModerationQueueItem)
      mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

      await store.performAction('q1', { action: ModerationActionType.RESOLVED })

      expect(store.queueItems).toHaveLength(1)
      expect((store.queueItems[0] as ModerationQueueItem).id).toBe('q2')
      expect(store.queueTotal).toBe(1)
      expect(mockedQueueApi.getStats).toHaveBeenCalledTimes(1)
    })

    it('performAction with DISMISSED removes the item and triggers fetchStats', async () => {
      const store = useModerationStore()
      store.queueItems = [{ id: 'q1' } as unknown as ModerationQueueItem]
      store.queueTotal = 1
      mockedQueueApi.performAction.mockResolvedValueOnce({
        id: 'q1',
        status: ModerationStatus.DISMISSED,
      } as ModerationQueueItem)
      mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

      await store.performAction('q1', { action: ModerationActionType.DISMISSED })

      expect(store.queueItems).toHaveLength(0)
      expect(store.queueTotal).toBe(0)
      expect(mockedQueueApi.getStats).toHaveBeenCalledTimes(1)
    })

    it('performAction with non-terminal status patches the matching queueItem index', async () => {
      const store = useModerationStore()
      const updated = {
        id: 'q1',
        status: ModerationStatus.UNDER_REVIEW,
      } as ModerationQueueItem
      store.queueItems = [
        { id: 'q1', status: ModerationStatus.PENDING } as unknown as ModerationQueueItem,
      ]
      store.queueTotal = 1
      mockedQueueApi.performAction.mockResolvedValueOnce(updated)
      mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

      await store.performAction('q1', { action: ModerationActionType.WARNED })

      expect(store.queueItems).toHaveLength(1)
      expect(store.queueItems[0]).toStrictEqual(updated)
      expect(store.queueTotal).toBe(1)
    })

    it('batchAction filters successful IDs from queueItems and triggers fetchStats', async () => {
      const store = useModerationStore()
      store.queueItems = [
        { id: 'q1' } as unknown as ModerationQueueItem,
        { id: 'q2' } as unknown as ModerationQueueItem,
        { id: 'q3' } as unknown as ModerationQueueItem,
      ]
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

      // q1 + q3 succeeded and are removed; q2 failed and stays
      expect(store.queueItems).toHaveLength(1)
      expect((store.queueItems[0] as ModerationQueueItem).id).toBe('q2')
      expect(store.queueTotal).toBe(1)
      expect(mockedQueueApi.getStats).toHaveBeenCalledTimes(1)
    })

    it('reviewAppeal patches the matching appeals index in place', async () => {
      const store = useModerationStore()
      const reviewed = { id: 'a1', status: 'APPROVED' as Appeal['status'] } as Appeal
      store.appeals = [{ id: 'a1', status: 'PENDING' } as unknown as Appeal]
      mockedAppealsApi.reviewAppeal.mockResolvedValueOnce(reviewed)
      mockedQueueApi.getStats.mockResolvedValueOnce({} as never)

      const dto: ReviewAppealDto = { decision: 'APPROVED' }
      const result = await store.reviewAppeal('a1', dto)

      expect(result).toBe(reviewed)
      expect(store.appeals).toHaveLength(1)
      expect(store.appeals[0]).toStrictEqual(reviewed)
      // Regression: appeal decisions change the dashboard counts; the
      // store must refresh stats so the header counters stay in sync.
      // This was previously missing, leaving the dashboard stale.
      expect(mockedQueueApi.getStats).toHaveBeenCalledTimes(1)
    })
  })

  describe('utility functions', () => {
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

    it('reset clears queue/reports/appeals/stats and aborts in-flight', () => {
      const store = useModerationStore()
      store.queueItems = [{ id: 'q1' } as unknown as ModerationQueueItem]
      store.queueTotal = 5
      store.reports = [{ id: 'r1' } as never]
      store.appeals = [{ id: 'a1' } as never]
      store.stats = { pendingCount: 9 } as never

      store.reset()

      expect(store.queueItems).toEqual([])
      expect(store.queueTotal).toBe(0)
      expect(store.reports).toEqual([])
      expect(store.appeals).toEqual([])
      expect(store.stats).toBeNull()
    })
  })
})
