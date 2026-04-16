import { describe, it, expect, vi, beforeEach } from 'vitest'
import { problemsApi, type Problem, type PageResult, type CreateProblemDto, type UpdateProblemDto } from '@/api/admin/problems'
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPatch: vi.fn(),
  apiDelete: vi.fn(),
  apiDownload: vi.fn(),
}))

const mockProblem: Problem = {
  id: '1',
  slug: 'test-problem',
  title: 'Test Problem',
  difficulty: 'EASY',
  status: 'TODO',
  isPremium: false,
  hasSolution: false,
  isPublished: false,
  isDeleted: false,
  createdAt: new Date('2025-01-01'),
  updatedAt: new Date('2025-01-01'),
  tags: [],
}

const mockPageResult: PageResult<Problem> = {
  items: [mockProblem],
  total: 1,
  page: 1,
  pageSize: 10,
  totalPages: 1,
}

describe('problemsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getProblems', () => {
    it('should call apiGet with /admin/problems and query params', async () => {
      vi.mocked(apiGet).mockResolvedValue(mockPageResult)

      const params = { page: 1, limit: 10 }
      const result = await problemsApi.getProblems(params)

      expect(apiGet).toHaveBeenCalledWith('/admin/problems', { params })
      expect(result).toEqual(mockPageResult)
    })
  })

  describe('getProblem', () => {
    it('should call apiGet with /admin/problems/{id}', async () => {
      vi.mocked(apiGet).mockResolvedValue(mockProblem)

      const result = await problemsApi.getProblem('1')

      expect(apiGet).toHaveBeenCalledWith('/admin/problems/1')
      expect(result).toEqual(mockProblem)
    })
  })

  describe('createProblem', () => {
    it('should call apiPost with /admin/problems and data', async () => {
      vi.mocked(apiPost).mockResolvedValue(mockProblem)

      const createDto: CreateProblemDto = {
        slug: 'new-problem',
        title: 'New Problem',
        difficulty: 'EASY',
      }
      const result = await problemsApi.createProblem(createDto)

      expect(apiPost).toHaveBeenCalledWith('/admin/problems', createDto)
      expect(result).toEqual(mockProblem)
    })
  })

  describe('updateProblem', () => {
    it('should call apiPatch with /admin/problems/{id} and data', async () => {
      const updatedProblem = { ...mockProblem, title: 'Updated Title' }
      vi.mocked(apiPatch).mockResolvedValue(updatedProblem)

      const updateDto: UpdateProblemDto = { title: 'Updated Title' }
      const result = await problemsApi.updateProblem('1', updateDto)

      expect(apiPatch).toHaveBeenCalledWith('/admin/problems/1', updateDto)
      expect(result).toEqual(updatedProblem)
    })
  })

  describe('deleteProblem', () => {
    it('should call apiDelete with /admin/problems/{id}', async () => {
      vi.mocked(apiDelete).mockResolvedValue(undefined)

      await problemsApi.deleteProblem('1')

      expect(apiDelete).toHaveBeenCalledWith('/admin/problems/1')
    })
  })

  describe('publishProblem', () => {
    it('should call apiPost with /admin/problems/{id}/publish', async () => {
      const publishedProblem = { ...mockProblem, isPublished: true }
      vi.mocked(apiPost).mockResolvedValue(publishedProblem)

      const result = await problemsApi.publishProblem('1')

      expect(apiPost).toHaveBeenCalledWith('/admin/problems/1/publish')
      expect(result).toEqual(publishedProblem)
    })
  })
})
