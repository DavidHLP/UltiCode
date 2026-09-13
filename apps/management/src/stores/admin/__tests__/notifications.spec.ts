import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { adminNotificationsApi } from '@/api/admin/notifications'
import { useNotificationsStore } from '../notifications'

vi.mock('@/api/admin/notifications', () => ({
  adminNotificationsApi: {
    getAll: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

const emptyPage = { items: [], total: 0, page: 1, pageSize: 10 }

describe('useNotificationsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(adminNotificationsApi.getAll).mockResolvedValue(emptyPage)
    vi.mocked(adminNotificationsApi.create).mockResolvedValue({} as never)
  })

  it('rethrows a failed refresh after a successful create', async () => {
    const refreshError = new Error('refresh failed')
    vi.mocked(adminNotificationsApi.getAll)
      .mockResolvedValueOnce(emptyPage)
      .mockRejectedValueOnce(refreshError)
    const store = useNotificationsStore()
    const data = {
      title: 'Maintenance',
      content: 'Planned maintenance',
      type: 'SYSTEM' as const,
      target: 'ALL' as const,
    }

    await store.fetchAnnouncements()
    await expect(store.createNotification(data)).rejects.toBe(refreshError)

    expect(adminNotificationsApi.create).toHaveBeenCalledWith(data)
    expect(store.isLoading).toBe(false)
  })

  it('keeps mutation refreshes on the newest pagination metadata', async () => {
    let resolveFirst: (value: typeof emptyPage) => void = () => undefined
    const firstPage = new Promise<typeof emptyPage>((resolve) => {
      resolveFirst = resolve
    })
    vi.mocked(adminNotificationsApi.getAll)
      .mockReturnValueOnce(firstPage)
      .mockResolvedValueOnce({ items: [], total: 0, page: 2, pageSize: 20 })
    const store = useNotificationsStore()

    const firstFetch = store.fetchAnnouncements({ page: 1, limit: 10, keyword: 'old' })
    await store.fetchAnnouncements({ page: 2, limit: 20, keyword: 'new' })
    resolveFirst({ items: [], total: 0, page: 1, pageSize: 10 })
    await firstFetch

    expect(store.currentPage).toBe(2)
    expect(store.pageSize).toBe(20)

    await store.createNotification({
      title: 'Maintenance',
      content: 'Planned maintenance',
      type: 'SYSTEM',
      target: 'ALL',
    })

    expect(adminNotificationsApi.getAll).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({ page: 2, limit: 20 }),
    )
  })
})
