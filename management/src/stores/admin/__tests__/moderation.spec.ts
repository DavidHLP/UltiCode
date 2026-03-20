import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useModerationStore } from '../moderation'
import { ModerationStatus, ReportCategory } from '@/api/admin/moderation'

// Mock the API module
vi.mock('@/api/admin/moderation', () => ({
  moderationQueueApi: {
    getQueue: vi.fn(),
    getQueueItem: vi.fn(),
    getStats: vi.fn(),
    claimItem: vi.fn(),
    assignItem: vi.fn(),
    unassignItem: vi.fn(),
    performAction: vi.fn(),
    batchAction: vi.fn(),
  },
  reportsApi: {
    getReports: vi.fn(),
    getReport: vi.fn(),
    getReportsByEntity: vi.fn(),
    createReport: vi.fn(),
  },
  appealsApi: {
    getAppeals: vi.fn(),
    getAppeal: vi.fn(),
    getMyAppeals: vi.fn(),
    getStats: vi.fn(),
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
  ReportCategory: {
    SPAM: 'SPAM',
    HARASSMENT: 'HARASSMENT',
    HATE_SPEECH: 'HATE_SPEECH',
    VIOLENCE: 'VIOLENCE',
    SEXUAL_CONTENT: 'SEXUAL_CONTENT',
    MISINFORMATION: 'MISINFORMATION',
    WRONG_ANSWER: 'WRONG_ANSWER',
    COPYRIGHT: 'COPYRIGHT',
    OTHER: 'OTHER',
  },
  ReportStatus: {
    PENDING: 'PENDING',
    REVIEWED: 'REVIEWED',
    RESOLVED: 'RESOLVED',
    DISMISSED: 'DISMISSED',
  },
  AppealStatus: {
    PENDING: 'PENDING',
    UNDER_REVIEW: 'UNDER_REVIEW',
    APPROVED: 'APPROVED',
    REJECTED: 'REJECTED',
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
describe('useModerationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have correct initial state', () => {
      const store = useModerationStore()

      expect(store.queueItems).toEqual([])
      expect(store.queueTotal).toBe(0)
      expect(store.queueLoading).toBe(false)
      expect(store.currentQueueItem).toBeNull()
      expect(store.reports).toEqual([])
      expect(store.appeals).toEqual([])
      expect(store.stats).toBeNull()
      expect(store.filters).toEqual({})
      expect(store.pagination).toEqual({ page: 1, limit: 20 })
    })
  })

  describe('computed properties', () => {
    it('pendingCount should return 0 when stats is null', () => {
      const store = useModerationStore()
      expect(store.pendingCount).toBe(0)
    })

    it('pendingCount should return correct value when stats exists', () => {
      const store = useModerationStore()
      store.stats = { total_pending: 5 } as Record<string, unknown>
      expect(store.pendingCount).toBe(5)
    })

    it('hasActiveFilters should return false when no filters', () => {
      const store = useModerationStore()
      expect(store.hasActiveFilters).toBe(false)
    })

    it('hasActiveFilters should return true when filters exist', () => {
      const store = useModerationStore()
      store.setFilters({ status: ModerationStatus.PENDING })
      expect(store.hasActiveFilters).toBe(true)
    })
  })

  describe('filter operations', () => {
    it('setFilters should update filters and reset page', () => {
      const store = useModerationStore()
      store.pagination.page = 5

      store.setFilters({ status: ModerationStatus.PENDING })

      expect(store.filters.status).toBe(ModerationStatus.PENDING)
      expect(store.pagination.page).toBe(1)
    })

    it('clearFilters should reset filters and page', () => {
      const store = useModerationStore()
      store.setFilters({ status: ModerationStatus.PENDING, category: ReportCategory.SPAM })
      store.pagination.page = 5

      store.clearFilters()

      expect(store.filters).toEqual({})
      expect(store.pagination.page).toBe(1)
    })

    it('setPage should update pagination', () => {
      const store = useModerationStore()
      store.setPage(3)
      expect(store.pagination.page).toBe(3)
    })

    it('setLimit should update limit and reset page', () => {
      const store = useModerationStore()
      store.pagination.page = 5

      store.setLimit(50)

      expect(store.pagination.limit).toBe(50)
      expect(store.pagination.page).toBe(1)
    })
  })

  describe('utility functions', () => {
    it('clearError should clear all errors', () => {
      const store = useModerationStore()
      store.queueError = 'error1'
      store.currentQueueItemError = 'error2'
      store.reportsError = 'error3'
      store.appealsError = 'error4'

      store.clearError()

      expect(store.queueError).toBeNull()
      expect(store.currentQueueItemError).toBeNull()
      expect(store.reportsError).toBeNull()
      expect(store.appealsError).toBeNull()
    })

    it('clearCurrentQueueItem should reset current item', () => {
      const store = useModerationStore()
      store.currentQueueItem = { id: '123' } as Record<string, unknown>
      store.currentQueueItemError = 'error'

      store.clearCurrentQueueItem()

      expect(store.currentQueueItem).toBeNull()
      expect(store.currentQueueItemError).toBeNull()
    })

    it('reset should clear all state', () => {
      const store = useModerationStore()
      store.queueItems = [{ id: '1' }] as Record<string, unknown>[]
      store.queueTotal = 10
      store.stats = { total_pending: 5 } as Record<string, unknown>
      store.setFilters({ status: ModerationStatus.PENDING })
      store.pagination.page = 5

      store.reset()

      expect(store.queueItems).toEqual([])
      expect(store.queueTotal).toBe(0)
      expect(store.stats).toBeNull()
      expect(store.filters).toEqual({})
      expect(store.pagination.page).toBe(1)
    })
  })
})
