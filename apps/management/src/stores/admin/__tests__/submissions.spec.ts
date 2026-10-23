import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSubmissionsStore } from '../submissions'
import { submissionsApi } from '@/api/admin/submissions'

vi.mock('@/api/admin/submissions', () => ({
  submissionsApi: { getStatistics: vi.fn() },
}))

describe('submission statistics', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('keeps unavailable and loading statistics distinct from a successful zero result', async () => {
    const store = useSubmissionsStore()
    expect(store.stats).toBeNull()
    vi.mocked(submissionsApi.getStatistics).mockRejectedValueOnce(new Error('unavailable'))
    await store.fetchStatistics()
    expect(store.stats).toBeNull()
    expect(store.statsError).toBeTruthy()
    vi.mocked(submissionsApi.getStatistics).mockResolvedValueOnce({
      total: 0,
      pending: 0,
      last24h: 0,
      byStatus: [],
      byLanguage: [],
    })
    const retry = store.fetchStatistics()
    expect(store.statsLoading).toBe(true)
    expect(store.stats).toBeNull()
    await retry
    expect(store.statsError).toBeNull()
    expect(store.stats?.total).toBe(0)
    expect(store.stats?.acceptedRate).toBe('0')
  })

  it('clears cached statistics on failure and ignores superseded or reset requests', async () => {
    const zero = { total: 0, pending: 0, last24h: 0, byStatus: [], byLanguage: [] }
    const store = useSubmissionsStore()
    vi.mocked(submissionsApi.getStatistics).mockResolvedValueOnce({ ...zero, total: 12 })
    await store.fetchStatistics()
    expect(store.stats?.total).toBe(12)
    vi.mocked(submissionsApi.getStatistics).mockRejectedValueOnce(new Error('unavailable'))
    await store.fetchStatistics()
    expect(store.stats).toBeNull()

    let resolveOld!: (value: typeof zero) => void
    vi.mocked(submissionsApi.getStatistics)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve
          }),
      )
      .mockResolvedValueOnce(zero)
    const old = store.fetchStatistics()
    await store.fetchStatistics()
    resolveOld({ ...zero, total: 99 })
    await old
    expect(store.stats?.total).toBe(0)

    vi.mocked(submissionsApi.getStatistics).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve
        }),
    )
    const pending = store.fetchStatistics()
    store.reset()
    resolveOld({ ...zero, total: 99 })
    await pending
    expect(store.stats).toBeNull()
    expect(store.statsError).toBeNull()
  })

  it('counts accepted verdicts across supported backend spellings', async () => {
    vi.mocked(submissionsApi.getStatistics).mockResolvedValue({
      total: 4,
      pending: 0,
      last24h: 0,
      byStatus: [
        { status: 'Accepted', count: 2 },
        { status: 'ACCEPTED', count: 1 },
        { status: 'Wrong Answer', count: 1 },
      ],
      byLanguage: [{ language: 'python', count: 4 }],
    })
    const store = useSubmissionsStore()
    await store.fetchStatistics()
    expect(store.stats?.acceptedRate).toBe('75.0')
    expect(store.stats?.total).toBe(4)
  })

  it('keeps an empty total at zero without division by zero', async () => {
    vi.mocked(submissionsApi.getStatistics).mockResolvedValue({
      total: 0,
      pending: 0,
      last24h: 0,
      byStatus: [],
      byLanguage: [],
    })
    const store = useSubmissionsStore()
    await store.fetchStatistics()
    expect(store.stats?.acceptedRate).toBe('0')
  })
})
