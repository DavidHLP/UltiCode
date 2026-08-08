import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  solutionsApi,
  type Solution,
  type PageResult,
  type BulkSolutionActionDto,
} from '@/api/admin/solutions'
import { apiGet, apiPost, apiDelete } from '@/utils/request'

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiDelete: vi.fn(),
}))

const mockSolution: Solution = {
  id: 'sol-001',
  problemId: 1,
  userId: 'user-001',
  title: 'Two Sum Solution',
  content: '# Two Sum\nUse a hash map...',
  summary: 'Hash map approach',
  language: 'python',
  tags: '["array", "hash-map"]',
  views: 42,
  isPublished: true,
  publishedAt: '2024-01-15T08:00:00Z',
  publishedBy: 'user-001',
  isFlagged: false,
  isDeleted: false,
  createdAt: '2024-01-10T10:00:00Z',
  updatedAt: '2024-01-15T08:00:00Z',
  author: {
    id: 'user-001',
    username: 'alice',
    name: 'Alice Chen',
    email: 'alice@example.com',
  },
  problem: {
    id: '1',
    slug: 'two-sum',
    title: 'Two Sum',
    difficulty: 'EASY',
  },
}

const mockPageResult: PageResult<Solution> = {
  items: [mockSolution],
  total: 1,
  page: 1,
  pageSize: 10,
  totalPages: 1,
}

describe('solutionsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getSolutions', () => {
    it('should call apiGet with /admin/solutions and query params', async () => {
      vi.mocked(apiGet).mockResolvedValue(mockPageResult)

      const params = { page: 1, limit: 10, search: 'two sum' }
      const result = await solutionsApi.getSolutions(params)

      expect(apiGet).toHaveBeenCalledWith('/admin/solutions', { params })
      expect(result).toEqual(mockPageResult)
      expect(result.items[0].id).toBe('sol-001')
    })

    it('should pass filter flags correctly', async () => {
      vi.mocked(apiGet).mockResolvedValue(mockPageResult)

      const params = { isFlagged: true, isPublished: false, page: 1, limit: 20 }
      await solutionsApi.getSolutions(params)

      expect(apiGet).toHaveBeenCalledWith('/admin/solutions', { params })
    })
  })

  describe('getFlaggedSolutions', () => {
    it('should call apiGet with /admin/solutions/flagged', async () => {
      vi.mocked(apiGet).mockResolvedValue(mockPageResult)

      const result = await solutionsApi.getFlaggedSolutions({ page: 1, limit: 10 })

      expect(apiGet).toHaveBeenCalledWith('/admin/solutions/flagged', {
        params: { page: 1, limit: 10 },
      })
      expect(result.items).toHaveLength(1)
    })
  })

  describe('getSolution', () => {
    it('should call apiGet with solution id', async () => {
      vi.mocked(apiGet).mockResolvedValue(mockSolution)

      const result = await solutionsApi.getSolution('sol-001')

      expect(apiGet).toHaveBeenCalledWith('/admin/solutions/sol-001')
      expect(result).toEqual(mockSolution)
      expect(result.author.username).toBe('alice')
      expect(result.problem.slug).toBe('two-sum')
    })
  })

  describe('flagSolution', () => {
    it('should call apiPost with flag reason', async () => {
      const flaggedSolution = { ...mockSolution, isFlagged: true, flaggedReason: 'Spam' }
      vi.mocked(apiPost).mockResolvedValue(flaggedSolution)

      const result = await solutionsApi.flagSolution('sol-001', { reason: 'Spam' })

      expect(apiPost).toHaveBeenCalledWith('/admin/solutions/sol-001/flag', { reason: 'Spam' })
      expect(result.isFlagged).toBe(true)
      expect(result.flaggedReason).toBe('Spam')
    })
  })

  describe('unflagSolution', () => {
    it('should call apiPost to unflag', async () => {
      const unflaggedSolution = { ...mockSolution, isFlagged: false, flaggedReason: undefined }
      vi.mocked(apiPost).mockResolvedValue(unflaggedSolution)

      const result = await solutionsApi.unflagSolution('sol-001')

      expect(apiPost).toHaveBeenCalledWith('/admin/solutions/sol-001/unflag')
      expect(result.isFlagged).toBe(false)
    })
  })

  describe('deleteSolution', () => {
    it('should call apiDelete with solution id', async () => {
      vi.mocked(apiDelete).mockResolvedValue(undefined)

      await solutionsApi.deleteSolution('sol-001')

      expect(apiDelete).toHaveBeenCalledWith('/admin/solutions/sol-001')
    })
  })

  describe('bulkAction', () => {
    it('should return an array of results (not wrapped in { results })', async () => {
      const mockResults = [
        { id: 'sol-001', success: true, error: null },
        { id: 'sol-002', success: false, error: 'Not found' },
      ]
      vi.mocked(apiPost).mockResolvedValue(mockResults)

      const dto: BulkSolutionActionDto = {
        ids: ['sol-001', 'sol-002'],
        action: 'delete',
      }
      const result = await solutionsApi.bulkAction(dto)

      expect(apiPost).toHaveBeenCalledWith('/admin/solutions/bulk', dto)
      expect(Array.isArray(result)).toBe(true)
      expect(result).toHaveLength(2)
      expect(result[0]).toEqual({ id: 'sol-001', success: true, error: null })
      expect(result[1]).toEqual({ id: 'sol-002', success: false, error: 'Not found' })
    })

    it('should handle empty bulk action result', async () => {
      vi.mocked(apiPost).mockResolvedValue([])

      const dto: BulkSolutionActionDto = {
        ids: [],
        action: 'publish',
      }
      const result = await solutionsApi.bulkAction(dto)

      expect(Array.isArray(result)).toBe(true)
      expect(result).toHaveLength(0)
    })
  })
})
