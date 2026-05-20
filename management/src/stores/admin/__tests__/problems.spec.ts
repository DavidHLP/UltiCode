import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProblemsStore } from '../problems'
import { problemsApi } from '@/api/admin/problems'
import type { Problem, ProblemCreateInput, ProblemUpdateInput } from '@/api/admin/problems'

vi.mock('@/api/admin/problems', () => ({
  problemsApi: {
    getProblems: vi.fn(),
    getProblem: vi.fn(),
    createProblem: vi.fn(),
    updateProblem: vi.fn(),
    deleteProblem: vi.fn(),
    publishProblem: vi.fn(),
    unpublishProblem: vi.fn(),
    getProblemSubmissions: vi.fn(),
    bulkAction: vi.fn(),
    bulkEdit: vi.fn(),
    getProblemVersions: vi.fn(),
    getProblemVersion: vi.fn(),
    getVersionDiff: vi.fn(),
    rollbackToVersion: vi.fn(),
    createInitialVersion: vi.fn(),
    exportProblems: vi.fn(),
    importProblems: vi.fn(),
    flagProblem: vi.fn(),
    moderateProblem: vi.fn(),
    getFlaggedProblems: vi.fn(),
    batchModerateProblems: vi.fn(),
    getHeader: vi.fn(),
    getDescription: vi.fn(),
    getCode: vi.fn(),
    getCases: vi.fn(),
  },
  Difficulty: { EASY: 'EASY', MEDIUM: 'MEDIUM', HARD: 'HARD' },
  ProblemStatus: { SOLVED: 'solved', ATTEMPTED: 'attempted', TODO: 'todo' },
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

const mockUpdatedProblem: Problem = {
  ...mockProblem,
  title: 'Updated Title',
  difficulty: 'MEDIUM',
}

describe('useProblemsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have correct initial state', () => {
      const store = useProblemsStore()

      expect(store.problems).toEqual([])
      expect(store.total).toBe(0)
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
      expect(store.headerData).toBeNull()
      expect(store.descriptionData).toBeNull()
      expect(store.codeData).toBeNull()
      expect(store.casesData).toBeNull()
    })
  })

  describe('fetchProblems', () => {
    it('should populate problems and total on success', async () => {
      vi.mocked(problemsApi.getProblems).mockResolvedValue({
        items: [mockProblem],
        total: 1,
        page: 1,
        pageSize: 10,
        totalPages: 1,
      })

      const store = useProblemsStore()
      await store.fetchProblems()

      expect(problemsApi.getProblems).toHaveBeenCalledWith({})
      expect(store.problems).toEqual([mockProblem])
      expect(store.total).toBe(1)
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
    })

    it('should pass query params to API', async () => {
      vi.mocked(problemsApi.getProblems).mockResolvedValue({
        items: [],
        total: 0,
        page: 1,
        pageSize: 10,
        totalPages: 0,
      })

      const store = useProblemsStore()
      await store.fetchProblems({ page: 2, limit: 20, difficulty: 'EASY' })

      expect(problemsApi.getProblems).toHaveBeenCalledWith({
        page: 2,
        limit: 20,
        difficulty: 'EASY',
      })
    })

    it('should set error and clear problems on failure', async () => {
      vi.mocked(problemsApi.getProblems).mockRejectedValue(new Error('Network error'))

      const store = useProblemsStore()
      await store.fetchProblems()

      expect(store.error).toBe('Network error')
      expect(store.loading).toBe(false)
    })
  })

  describe('createProblem', () => {
    it('should call API and return created problem', async () => {
      vi.mocked(problemsApi.createProblem).mockResolvedValue(mockProblem)

      const store = useProblemsStore()
      const input: ProblemCreateInput = {
        slug: 'test-problem',
        title: 'Test Problem',
        difficulty: 'EASY',
      }
      const result = await store.createProblem(input)

      expect(problemsApi.createProblem).toHaveBeenCalledWith(input)
      expect(result).toEqual(mockProblem)
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
    })

    it('should set error and throw on failure', async () => {
      vi.mocked(problemsApi.createProblem).mockRejectedValue(new Error('Creation failed'))

      const store = useProblemsStore()
      const input: ProblemCreateInput = {
        slug: 'test-problem',
        title: 'Test Problem',
        difficulty: 'EASY',
      }

      await expect(store.createProblem(input)).rejects.toThrow('Creation failed')
      expect(store.error).toBe('Creation failed')
      expect(store.loading).toBe(false)
    })
  })

  describe('updateProblem', () => {
    it('should update local list item when problem is in the list', async () => {
      const store = useProblemsStore()
      store.problems = [{ ...mockProblem }]
      store.total = 1

      vi.mocked(problemsApi.updateProblem).mockResolvedValue(mockUpdatedProblem)

      const input: ProblemUpdateInput = { title: 'Updated Title', difficulty: 'MEDIUM' }
      const result = await store.updateProblem('1', input)

      expect(problemsApi.updateProblem).toHaveBeenCalledWith('1', input)
      expect(result).toEqual(mockUpdatedProblem)
      expect(store.problems[0].title).toBe('Updated Title')
      expect(store.problems[0].difficulty).toBe('MEDIUM')
    })

    it('should not modify list when problem is not in the list', async () => {
      const store = useProblemsStore()
      store.problems = []

      vi.mocked(problemsApi.updateProblem).mockResolvedValue(mockUpdatedProblem)

      const input: ProblemUpdateInput = { title: 'Updated Title' }
      await store.updateProblem('999', input)

      expect(store.problems).toEqual([])
    })

    it('should clear tab data after update (cache invalidation)', async () => {
      const store = useProblemsStore()
      store.headerData = {
        id: '1',
        title: 'Test',
        slug: 'test',
        difficulty: 'EASY',
        status: 'TODO',
        isPremium: false,
        isPublished: false,
      }

      vi.mocked(problemsApi.updateProblem).mockResolvedValue(mockUpdatedProblem)

      await store.updateProblem('1', { title: 'Updated Title' })

      expect(store.headerData).toBeNull()
    })

    it('should set error and throw on failure', async () => {
      const store = useProblemsStore()
      vi.mocked(problemsApi.updateProblem).mockRejectedValue(new Error('Update failed'))

      await expect(store.updateProblem('1', { title: 'X' })).rejects.toThrow('Update failed')
      expect(store.error).toBe('Update failed')
    })
  })

  describe('deleteProblem', () => {
    it('should remove problem from list and decrement total', async () => {
      const store = useProblemsStore()
      store.problems = [{ ...mockProblem }]
      store.total = 1

      vi.mocked(problemsApi.deleteProblem).mockResolvedValue(undefined)

      await store.deleteProblem('1')

      expect(problemsApi.deleteProblem).toHaveBeenCalledWith('1')
      expect(store.problems).toEqual([])
      expect(store.total).toBe(0)
    })

    it('should not decrement total if problem was not in list', async () => {
      const store = useProblemsStore()
      store.problems = []
      store.total = 5

      vi.mocked(problemsApi.deleteProblem).mockResolvedValue(undefined)

      await store.deleteProblem('999')

      expect(store.total).toBe(5)
    })

    it('should clear tab data when deleted problem matches', async () => {
      const store = useProblemsStore()
      store.headerData = {
        id: '1',
        title: 'Test',
        slug: 'test',
        difficulty: 'EASY',
        status: 'TODO',
        isPremium: false,
        isPublished: false,
      }
      store.problems = [{ ...mockProblem }]
      store.total = 1

      vi.mocked(problemsApi.deleteProblem).mockResolvedValue(undefined)

      await store.deleteProblem('1')

      expect(store.headerData).toBeNull()
    })

    it('should set error and throw on failure', async () => {
      const store = useProblemsStore()
      vi.mocked(problemsApi.deleteProblem).mockRejectedValue(new Error('Delete failed'))

      await expect(store.deleteProblem('1')).rejects.toThrow('Delete failed')
      expect(store.error).toBe('Delete failed')
    })
  })

  describe('reset', () => {
    it('should clear all state', () => {
      const store = useProblemsStore()
      store.problems = [{ ...mockProblem }]
      store.total = 10
      store.loading = true
      store.error = 'some error'
      store.headerData = {
        id: '1',
        title: 'Test',
        slug: 'test',
        difficulty: 'EASY',
        status: 'TODO',
        isPremium: false,
        isPublished: false,
      }

      store.reset()

      expect(store.problems).toEqual([])
      expect(store.total).toBe(0)
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
      expect(store.headerData).toBeNull()
    })
  })

  describe('clearError', () => {
    it('should set error to null', () => {
      const store = useProblemsStore()
      store.error = 'some error'

      store.clearError()

      expect(store.error).toBeNull()
    })
  })

  describe('clearCurrentProblem', () => {
    it('should clear all tab data', () => {
      const store = useProblemsStore()
      store.headerData = {
        id: '1',
        title: 'Test',
        slug: 'test',
        difficulty: 'EASY',
        status: 'TODO',
        isPremium: false,
        isPublished: false,
      }
      store.descriptionData = {
        id: '1',
        title: 'Test',
        slug: 'test',
        difficulty: 'EASY',
        status: 'TODO',
        isPremium: false,
        isPublished: false,
        tags: [],
        createdAt: new Date(),
        updatedAt: new Date(),
      }

      store.clearCurrentProblem()

      expect(store.headerData).toBeNull()
      expect(store.descriptionData).toBeNull()
    })
  })
})
