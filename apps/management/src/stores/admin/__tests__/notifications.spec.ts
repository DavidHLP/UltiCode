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

  it('does not refresh after a successful create', async () => {
    vi.mocked(adminNotificationsApi.getAll).mockResolvedValueOnce(emptyPage)
    const store = useNotificationsStore()
    const data = {
      title: 'Maintenance',
      content: 'Planned maintenance',
      type: 'SYSTEM' as const,
      target: 'ALL' as const,
    }

    await store.fetchAnnouncements()
    await expect(store.createNotification(data)).resolves.toBeUndefined()

    expect(adminNotificationsApi.create).toHaveBeenCalledWith(data)
    expect(adminNotificationsApi.getAll).toHaveBeenCalledTimes(1)
    expect(store.isLoading).toBe(false)
  })

  it('keeps collection state on the newest notification page', async () => {
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

    expect(store.total).toBe(0)

    await store.createNotification({
      title: 'Maintenance',
      content: 'Planned maintenance',
      type: 'SYSTEM',
      target: 'ALL',
    })

    expect(adminNotificationsApi.getAll).toHaveBeenCalledTimes(2)
  })
})
