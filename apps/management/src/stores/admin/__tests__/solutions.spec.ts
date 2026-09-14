import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSolutionsStore } from '../solutions'
import { solutionsApi, type SolutionListItem } from '@/api/admin/solutions'

vi.mock('@/api/admin/solutions', () => ({
  solutionsApi: {
    getSolutions: vi.fn(),
    deleteSolution: vi.fn(),
  },
}))

const solution: SolutionListItem = {
  id: 'sol-001',
  title: 'Example solution',
  language: 'java',
  views: 1,
  isPublished: true,
  isFlagged: false,
  isDeleted: false,
  createdAt: '2026-01-01T00:00:00Z',
  author: { id: 'user-001', username: 'user', name: 'User' },
  problem: { id: 'problem-001', slug: 'problem', title: 'Problem', difficulty: 'EASY' },
}

describe('useSolutionsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(solutionsApi.getSolutions).mockResolvedValue({ items: [solution], total: 5 })
    vi.mocked(solutionsApi.deleteSolution).mockResolvedValue(undefined)
  })

  it('does not decrement total when deleting a solution outside the current page', async () => {
    const store = useSolutionsStore()

    await store.fetchSolutions()
    await store.deleteSolution('sol-off-page')

    expect(store.total).toBe(5)
    expect(store.solutions).toEqual([solution])

    await store.deleteSolution(solution.id)

    expect(store.total).toBe(4)
    expect(store.solutions).toEqual([])
  })
})
