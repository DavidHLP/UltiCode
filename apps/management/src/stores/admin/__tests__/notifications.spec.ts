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
})
