import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { contestsApi, type ContestRanking } from '@/api/admin/contests'
import { useContestsStore } from '../contests'

vi.mock('@/api/admin/contests', () => ({
  contestsApi: {
    getContests: vi.fn(),
    getContest: vi.fn(),
    getRankings: vi.fn(),
  },
}))

const page = (items: ContestRanking[]) =>
  ({ items } as unknown as Awaited<ReturnType<typeof contestsApi.getRankings>>)

const ranking = {
  userId: 'user-1',
  username: 'contestant',
  score: 100,
  penalty: 0,
} as ContestRanking

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('contest rankings store', () => {
  it('distinguishes loading, failure, and successful empty results', async () => {
    const store = useContestsStore()
    let rejectRequest!: (reason: unknown) => void
    const request = new Promise<Awaited<ReturnType<typeof contestsApi.getRankings>>>(
      (_resolve, reject) => {
        rejectRequest = reject
      },
    )
    store.currentRankings = [ranking]
    vi.mocked(contestsApi.getRankings).mockReturnValueOnce(request)
    vi.spyOn(console, 'error').mockImplementation(() => {})

    const failedLoad = store.fetchRankings('contest-1')
    expect(store.rankingsLoading).toBe(true)
    expect(store.rankingsError).toBe(false)
    expect(store.currentRankings).toEqual([])

    rejectRequest(new Error('network failure'))
    await failedLoad
    expect(store.rankingsLoading).toBe(false)
    expect(store.rankingsError).toBe(true)

    vi.mocked(contestsApi.getRankings).mockResolvedValueOnce(page([]))
    await store.fetchRankings('contest-1')
    expect(store.rankingsLoading).toBe(false)
    expect(store.rankingsError).toBe(false)
    expect(store.currentRankings).toEqual([])
  })

  it('ignores stale responses when a newer rankings request finishes first', async () => {
    const store = useContestsStore()
    let resolveOlder!: (response: Awaited<ReturnType<typeof contestsApi.getRankings>>) => void
    const olderRequest = new Promise<Awaited<ReturnType<typeof contestsApi.getRankings>>>(
      (resolve) => {
        resolveOlder = resolve
      },
    )
    vi.mocked(contestsApi.getRankings)
      .mockReturnValueOnce(olderRequest)
      .mockResolvedValueOnce(page([ranking]))

    const olderLoad = store.fetchRankings('contest-1')
    const newerLoad = store.fetchRankings('contest-2')
    await newerLoad
    expect(store.currentRankings).toEqual([ranking])
    expect(store.rankingsLoading).toBe(false)

    resolveOlder(page([]))
    await olderLoad
    expect(store.currentRankings).toEqual([ranking])
    expect(store.rankingsError).toBe(false)
  })
})
