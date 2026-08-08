import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  problemsApi,
  type Problem,
  type PageResult,
  type ProblemCreateInput,
  type ProblemUpdateInput,
} from '@/api/admin/problems'
import { serializeCreateInput, serializeUpdateInput } from '@/api/admin/problems'
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
    it('should call apiPost with /admin/problems and serialized data', async () => {
      vi.mocked(apiPost).mockResolvedValue(mockProblem)

      const input: ProblemCreateInput = {
        slug: 'new-problem',
        title: 'New Problem',
        difficulty: 'EASY',
        examples: [{ input: '1', output: '2' }],
        constraints: ['n <= 10'],
        hints: ['Think about DP'],
      }
      const result = await problemsApi.createProblem(input)

      expect(apiPost).toHaveBeenCalledWith('/admin/problems', serializeCreateInput(input))
      expect(result).toEqual(mockProblem)
    })
  })

  describe('updateProblem', () => {
    it('should call apiPatch with /admin/problems/{id} and serialized data', async () => {
      const updatedProblem = { ...mockProblem, title: 'Updated Title' }
      vi.mocked(apiPatch).mockResolvedValue(updatedProblem)

      const input: ProblemUpdateInput = {
        title: 'Updated Title',
        examples: [{ input: '1', output: '2', order: 0 }],
        constraintsJson: ['n <= 10'],
        hints: ['Think about DP'],
      }
      const result = await problemsApi.updateProblem('1', input)

      expect(apiPatch).toHaveBeenCalledWith('/admin/problems/1', serializeUpdateInput(input))
      expect(result).toEqual(updatedProblem)
    })
  })

  describe('serializeCreateInput', () => {
    it('should serialize examples, constraints, hints to JSON strings', () => {
      const input: ProblemCreateInput = {
        slug: 'test',
        title: 'Test',
        difficulty: 'EASY',
        examples: [{ input: '1', output: '2' }],
        constraints: ['n <= 10'],
        hints: ['hint1'],
      }
      const dto = serializeCreateInput(input)

      expect(dto.examples).toBe('[{"input":"1","output":"2","order":0}]')
      expect(dto.constraints).toBe('["n <= 10"]')
      expect(dto.hints).toBe('["hint1"]')
    })

    it('should leave undefined fields as undefined', () => {
      const input: ProblemCreateInput = { slug: 'test', title: 'Test', difficulty: 'EASY' }
      const dto = serializeCreateInput(input)

      expect(dto.examples).toBeUndefined()
      expect(dto.constraints).toBeUndefined()
      expect(dto.hints).toBeUndefined()
    })

    it('should serialize empty arrays', () => {
      const input: ProblemCreateInput = {
        slug: 'test',
        title: 'Test',
        difficulty: 'EASY',
        examples: [],
        constraints: [],
        hints: [],
      }
      const dto = serializeCreateInput(input)

      expect(dto.examples).toBe('[]')
      expect(dto.constraints).toBe('[]')
      expect(dto.hints).toBe('[]')
    })

    it('should preserve existing order and only assign default when missing', () => {
      const input: ProblemCreateInput = {
        slug: 'test',
        title: 'Test',
        difficulty: 'EASY',
        examples: [
          { input: '1', output: '2', order: 5 },
          { input: '3', output: '4' },
        ],
      }
      const dto = serializeCreateInput(input)

      const parsed = JSON.parse(dto.examples!)
      expect(parsed[0].order).toBe(5)
      expect(parsed[1].order).toBe(1)
    })
  })

  describe('serializeUpdateInput', () => {
    it('should serialize examples, constraintsJson, hints to JSON strings', () => {
      const input: ProblemUpdateInput = {
        examples: [{ input: '1', output: '2' }],
        constraintsJson: ['n <= 10'],
        hints: ['hint1'],
      }
      const dto = serializeUpdateInput(input)

      expect(dto.examples).toBe('[{"input":"1","output":"2","order":0}]')
      expect(dto.constraintsJson).toBe('["n <= 10"]')
      expect(dto.hints).toBe('["hint1"]')
    })

    it('should not serialize languages (kept as structured array)', () => {
      const input: ProblemUpdateInput = {
        languages: [{ language: 'python', starterCode: '# code' }],
      }
      const dto = serializeUpdateInput(input)

      expect(dto.languages).toEqual([{ language: 'python', starterCode: '# code' }])
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
