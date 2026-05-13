import { describe, it, expect, vi, beforeEach } from 'vitest'
import { contestsApi, CreateContestDto, ContestType, ContestStatus } from '@/api/admin/contests'
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
    it('should call apiPost with /admin/contests and contest data', async () => {
      const mockContest = {
        id: 'contest-123',
        slug: 'weekly-contest-123',
        title: 'Weekly Contest #123',
        description: 'Test contest',
        contestType: ContestType.PUBLIC,
        startTime: '2024-12-31T10:00:00Z',
        durationMinutes: 120,
        status: ContestStatus.UPCOMING,
        isVisible: true,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-01T00:00:00Z',
      }

      vi.mocked(apiPost).mockResolvedValue(mockContest)

      const createData: CreateContestDto = {
        slug: 'weekly-contest-123',
        title: 'Weekly Contest #123',
        description: 'Test contest',
        type: ContestType.PUBLIC,
        startTime: '2024-12-31T10:00:00Z',
        duration: 120,
        isPublished: true,
      }

      const result = await contestsApi.createContest(createData)

      expect(apiPost).toHaveBeenCalledWith('/admin/contests', createData)
      expect(apiPost).toHaveBeenCalledTimes(1)
      expect(result).toEqual(mockContest)
      expect(result.id).toBe('contest-123')
      expect(result.title).toBe('Weekly Contest #123')
      expect(result.contestType).toBe(ContestType.PUBLIC)
    })

    it('should handle contest creation with minimal data', async () => {
      const mockContest = {
        id: 'contest-456',
        slug: 'simple-contest',
        title: 'Simple Contest',
        contestType: ContestType.PRIVATE,
        startTime: '2024-12-31T10:00:00Z',
        durationMinutes: 60,
        status: ContestStatus.UPCOMING,
        isVisible: false,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-01T00:00:00Z',
      }

      vi.mocked(apiPost).mockResolvedValue(mockContest)

      const createData: CreateContestDto = {
        slug: 'simple-contest',
        title: 'Simple Contest',
        type: ContestType.PRIVATE,
        startTime: '2024-12-31T10:00:00Z',
        duration: 60,
      }

      const result = await contestsApi.createContest(createData)

      expect(apiPost).toHaveBeenCalledWith('/admin/contests', createData)
      expect(result.id).toBe('contest-456')
      expect(result.contestType).toBe(ContestType.PRIVATE)
    })

    it('should handle contest creation with problem IDs', async () => {
      const mockContest = {
        id: 'contest-789',
        slug: 'contest-with-problems',
        title: 'Contest With Problems',
        contestType: ContestType.PUBLIC,
        startTime: '2024-12-31T10:00:00Z',
        durationMinutes: 180,
        status: ContestStatus.UPCOMING,
        isVisible: true,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-01T00:00:00Z',
      }

      vi.mocked(apiPost).mockResolvedValue(mockContest)

      const createData: CreateContestDto = {
        slug: 'contest-with-problems',
        title: 'Contest With Problems',
        type: ContestType.PUBLIC,
        startTime: '2024-12-31T10:00:00Z',
        duration: 180,
        problemIds: ['problem-1', 'problem-2', 'problem-3'],
        scoringRuleId: 'rule-123',
      }

      const result = await contestsApi.createContest(createData)

      expect(apiPost).toHaveBeenCalledWith('/admin/contests', createData)
      expect(result.id).toBe('contest-789')
    })

    it('should propagate errors from apiPost', async () => {
      const error = new Error('Network error')
      vi.mocked(apiPost).mockRejectedValue(error)

      const createData: CreateContestDto = {
        slug: 'failed-contest',
        title: 'Failed Contest',
        type: ContestType.PUBLIC,
        startTime: '2024-12-31T10:00:00Z',
        duration: 120,
      }

      await expect(contestsApi.createContest(createData)).rejects.toThrow('Network error')
      expect(apiPost).toHaveBeenCalledWith('/admin/contests', createData)
    })
  })

  describe('getContests', () => {
    it('should call apiGet with /admin/contests and query params', async () => {
      const mockResponse = {
        items: [],
        total: 0,
        page: 1,
        pageSize: 20,
        totalPages: 0,
      }

      vi.mocked(apiGet).mockResolvedValue(mockResponse)

      const result = await contestsApi.getContests({ page: 1, limit: 20 })

      expect(apiGet).toHaveBeenCalledWith('/admin/contests', { params: { page: 1, limit: 20 } })
      expect(result).toEqual(mockResponse)
    })
  })

  describe('getContest', () => {
    it('should call apiGet with contest id', async () => {
      const mockContest = {
        id: 'contest-123',
        slug: 'test-contest',
        title: 'Test Contest',
        contestType: ContestType.PUBLIC,
        startTime: '2024-12-31T10:00:00Z',
        durationMinutes: 120,
        status: ContestStatus.UPCOMING,
        isVisible: true,
        createdAt: '2024-12-01T00:00:00Z',
        updatedAt: '2024-12-01T00:00:00Z',
      }

      vi.mocked(apiGet).mockResolvedValue(mockContest)

      const result = await contestsApi.getContest('contest-123')

      expect(apiGet).toHaveBeenCalledWith('/admin/contests/contest-123')
      expect(result).toEqual(mockContest)
    })
  })

  describe('updateContest', () => {
    it('should call apiPatch with contest id and data', async () => {
      const mockContest = {
        id: 'contest-123',
        slug: 'updated-contest',
        title: 'Updated Contest',
        contestType: ContestType.PUBLIC,
        startTime: '2024-12-31T10:00:00Z',
        durationMinutes: 120,
        status: ContestStatus.UPCOMING,
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

      expect(apiPatch).toHaveBeenCalledWith('/admin/contests/contest-123', updateData)
      expect(result).toEqual(mockContest)
    })
  })

  describe('deleteContest', () => {
    it('should call apiDelete with contest id', async () => {
      vi.mocked(apiDelete).mockResolvedValue(undefined)

      await contestsApi.deleteContest('contest-123')

      expect(apiDelete).toHaveBeenCalledWith('/admin/contests/contest-123')
    })
  })
})
