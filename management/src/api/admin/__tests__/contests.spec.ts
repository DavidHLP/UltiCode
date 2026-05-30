import { describe, it, expect, vi, beforeEach } from 'vitest'
import { contestsApi, CreateContestDto, ContestType } from '@/api/admin/contests'
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPatch: vi.fn(),
  apiDelete: vi.fn(),
}))

describe('contestsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('createContest', () => {
    it('should call apiPost with /admin/contest and contest data', async () => {
      const mockContest = {
        id: 'contest-123',
        slug: 'weekly-contest-123',
        title: 'Weekly Contest #123',
        description: 'Test contest',
        contestType: ContestType.ICPC,
        startTime: '2024-12-31T10:00:00Z',
        duration: 120,
        status: 'UPCOMING' as const,
        isVisible: true,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-01T00:00:00Z',
      }

      vi.mocked(apiPost).mockResolvedValue(mockContest)

      const createData: CreateContestDto = {
        slug: 'weekly-contest-123',
        title: 'Weekly Contest #123',
        description: 'Test contest',
        contestType: 'ICPC',
        startTime: '2024-12-31T10:00:00Z',
        duration: 120,
        isPublished: true,
      }

      const result = await contestsApi.createContest(createData)

      expect(apiPost).toHaveBeenCalledWith('/admin/contest', createData)
      expect(apiPost).toHaveBeenCalledTimes(1)
      expect(result).toEqual(mockContest)
      expect(result.id).toBe('contest-123')
      expect(result.title).toBe('Weekly Contest #123')
      expect(result.contestType).toBe('ICPC')
    })

    it('should handle contest creation with minimal data', async () => {
      const mockContest = {
        id: 'contest-456',
        slug: 'simple-contest',
        title: 'Simple Contest',
        contestType: ContestType.CUSTOM,
        startTime: '2024-12-31T10:00:00Z',
        duration: 60,
        status: 'UPCOMING' as const,
        isVisible: false,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-01T00:00:00Z',
      }

      vi.mocked(apiPost).mockResolvedValue(mockContest)

      const createData: CreateContestDto = {
        slug: 'simple-contest',
        title: 'Simple Contest',
        contestType: 'CUSTOM',
        startTime: '2024-12-31T10:00:00Z',
        duration: 60,
      }

      const result = await contestsApi.createContest(createData)

      expect(apiPost).toHaveBeenCalledWith('/admin/contest', createData)
      expect(result.id).toBe('contest-456')
      expect(result.contestType).toBe('CUSTOM')
    })

    it('should handle contest creation with problem IDs', async () => {
      const mockContest = {
        id: 'contest-789',
        slug: 'contest-with-problems',
        title: 'Contest With Problems',
        contestType: ContestType.ICPC,
        startTime: '2024-12-31T10:00:00Z',
        duration: 180,
        status: 'UPCOMING' as const,
        isVisible: true,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-01T00:00:00Z',
      }

      vi.mocked(apiPost).mockResolvedValue(mockContest)

      const createData: CreateContestDto = {
        slug: 'contest-with-problems',
        title: 'Contest With Problems',
        contestType: 'ICPC',
        startTime: '2024-12-31T10:00:00Z',
        duration: 180,
        problemIds: [1, 2, 3],
        scoringRuleId: 'rule-123',
      }

      const result = await contestsApi.createContest(createData)

      expect(apiPost).toHaveBeenCalledWith('/admin/contest', createData)
      expect(result.id).toBe('contest-789')
    })

    it('should propagate errors from apiPost', async () => {
      const error = new Error('Network error')
      vi.mocked(apiPost).mockRejectedValue(error)

      const createData: CreateContestDto = {
        slug: 'failed-contest',
        title: 'Failed Contest',
        contestType: 'ICPC',
        startTime: '2024-12-31T10:00:00Z',
        duration: 120,
      }

      await expect(contestsApi.createContest(createData)).rejects.toThrow('Network error')
      expect(apiPost).toHaveBeenCalledWith('/admin/contest', createData)
    })
  })

  describe('getContests', () => {
    it('should call apiGet with /admin/contest and query params', async () => {
      const mockResponse = {
        items: [],
        total: 0,
        page: 1,
        pageSize: 20,
        totalPages: 0,
      }

      vi.mocked(apiGet).mockResolvedValue(mockResponse)

      const result = await contestsApi.getContests({ page: 1, limit: 20 })

      expect(apiGet).toHaveBeenCalledWith('/admin/contest', { params: { page: 1, limit: 20 } })
      expect(result).toEqual(mockResponse)
    })
  })

  describe('getContest', () => {
    it('should call apiGet with contest id', async () => {
      const mockContest = {
        id: 'contest-123',
        slug: 'test-contest',
        title: 'Test Contest',
        contestType: ContestType.ICPC,
        startTime: '2024-12-31T10:00:00Z',
        duration: 120,
        status: 'UPCOMING' as const,
        isVisible: true,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-01T00:00:00Z',
      }

      vi.mocked(apiGet).mockResolvedValue(mockContest)

      const result = await contestsApi.getContest('contest-123')

      expect(apiGet).toHaveBeenCalledWith('/admin/contest/contest-123')
      expect(result).toEqual(mockContest)
    })
  })

  describe('updateContest', () => {
    it('should call apiPatch with contest id and data', async () => {
      const mockContest = {
        id: 'contest-123',
        slug: 'updated-contest',
        title: 'Updated Contest',
        contestType: ContestType.ICPC,
        startTime: '2024-12-31T10:00:00Z',
        duration: 120,
        status: 'UPCOMING' as const,
        isVisible: true,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-02T00:00:00Z',
      }

      vi.mocked(apiPatch).mockResolvedValue(mockContest)

      const updateData = {
        title: 'Updated Contest',
        duration: 120,
      }

      const result = await contestsApi.updateContest('contest-123', updateData)

      expect(apiPatch).toHaveBeenCalledWith('/admin/contest/contest-123', updateData)
      expect(result).toEqual(mockContest)
    })
  })

  describe('deleteContest', () => {
    it('should call apiDelete with contest id', async () => {
      vi.mocked(apiDelete).mockResolvedValue(undefined)

      await contestsApi.deleteContest('contest-123')

      expect(apiDelete).toHaveBeenCalledWith('/admin/contest/contest-123')
    })
  })
})
