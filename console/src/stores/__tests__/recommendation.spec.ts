// console/src/stores/__tests__/recommendation.spec.ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRecommendationStore } from '../recommendation'
import * as api from '@/api/recommendation'

vi.mock('@/api/recommendation', () => ({
  recommendationApi: {
    getDaily: vi.fn(),
    getWeakPoints: vi.fn(),
    getChallenge: vi.fn(),
    getSimilar: vi.fn(),
  },
}))

const mockItem = {
  problemId: 1,
  slug: 'two-sum',
  title: 'Two Sum',
  difficulty: 'Easy',
  score: 0.85,
  tags: ['Array'],
  reason: 'Test',
}

const mockResult = {
  items: [mockItem],
  totalCount: 1,
  scenario: 'DAILY' as const,
  generatedAt: '2026-03-14T10:00:00Z',
}

describe('useRecommendationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have empty arrays and null error by default', () => {
      const store = useRecommendationStore()

      expect(store.daily).toEqual([])
      expect(store.weakPoints).toEqual([])
      expect(store.challenge).toEqual([])
      expect(store.similar).toEqual([])
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
    })
  })

  describe('loadDaily', () => {
    it('should load daily recommendations successfully', async () => {
      vi.mocked(api.recommendationApi.getDaily).mockResolvedValue(mockResult)
      const store = useRecommendationStore()
      await store.loadDaily()

      expect(store.daily).toEqual([mockItem])
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
    })

    it('should handle API error', async () => {
      vi.mocked(api.recommendationApi.getDaily).mockRejectedValue(new Error('Network error'))
      const store = useRecommendationStore()
      await store.loadDaily()

      expect(store.daily).toEqual([])
      expect(store.loading).toBe(false)
      expect(store.error).toBe('Network error')
    })

    it('should handle null result', async () => {
      vi.mocked(api.recommendationApi.getDaily).mockResolvedValue(null)
      const store = useRecommendationStore()
      await store.loadDaily()

      expect(store.daily).toEqual([])
    })
  })

  describe('loadWeakPoints', () => {
    it('should load weak points recommendations with tags', async () => {
      vi.mocked(api.recommendationApi.getWeakPoints).mockResolvedValue({
        ...mockResult,
        scenario: 'WEAK_POINT',
      })
      const store = useRecommendationStore()
      await store.loadWeakPoints(10, ['Array'])

      expect(store.weakPoints).toEqual([mockItem])
      expect(api.recommendationApi.getWeakPoints).toHaveBeenCalledWith(10, ['Array'])
    })
  })

  describe('loadChallenge', () => {
    it('should load challenge recommendations', async () => {
      vi.mocked(api.recommendationApi.getChallenge).mockResolvedValue({
        ...mockResult,
        scenario: 'CHALLENGE',
      })
      const store = useRecommendationStore()
      await store.loadChallenge(5)

      expect(store.challenge).toEqual([mockItem])
    })
  })

  describe('loadSimilar', () => {
    it('should load similar problems', async () => {
      vi.mocked(api.recommendationApi.getSimilar).mockResolvedValue({
        ...mockResult,
        scenario: 'SIMILAR',
      })
      const store = useRecommendationStore()
      await store.loadSimilar(1, 5)

      expect(store.similar).toEqual([mockItem])
      expect(api.recommendationApi.getSimilar).toHaveBeenCalledWith(1, 5)
    })
  })

  describe('clearError', () => {
    it('should clear error', () => {
      const store = useRecommendationStore()
      store.error = 'Some error'
      store.clearError()

      expect(store.error).toBeNull()
    })
  })
})
