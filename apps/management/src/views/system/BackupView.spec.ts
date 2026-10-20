import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BackupView from './BackupView.vue'
import { backupApi } from '@/api/admin/backup'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/i18n/utils', () => ({ formatDateTimeByLocale: (value: string) => value }))
vi.mock('vue-sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))
vi.mock('@/api/admin/backup', () => ({
  backupApi: { getBackups: vi.fn(), createBackup: vi.fn() },
}))

const emptyResponse = { items: [], total: 0, page: 1, limit: 100 }
const mountView = () =>
  shallowMount(BackupView, {
    global: {
      renderStubDefaultSlot: true,
      stubs: { Button: { template: '<button><slot /></button>' } },
    },
  })

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('backup list load state', () => {
  it('shows failure instead of a successful empty list and zero statistics', async () => {
    vi.mocked(backupApi.getBackups).mockRejectedValue(new Error('Forbidden'))
    const view = mountView()
    try {
      await flushPromises()
      expect(view.get('[role="alert"]').text()).toBe('system.backup.toast.loadFailed')
      expect(view.text()).not.toContain('system.backup.noBackups')
      expect(view.findAll('.text-2xl').map((node) => node.text())).toEqual(['—', '—', '—'])
      expect(backupApi.createBackup).not.toHaveBeenCalled()
    } finally {
      view.unmount()
    }
  })

  it('keeps a successful empty response distinct from failure', async () => {
    vi.mocked(backupApi.getBackups).mockResolvedValue(emptyResponse)
    const view = mountView()
    try {
      await flushPromises()
      expect(view.find('[role="alert"]').exists()).toBe(false)
      expect(view.text()).toContain('system.backup.noBackups')
      expect(view.findAll('.text-2xl').map((node) => node.text())).toEqual(['0', '0', '0'])
    } finally {
      view.unmount()
    }
  })

  it('clears the failure after the existing refresh succeeds', async () => {
    vi.mocked(backupApi.getBackups)
      .mockRejectedValueOnce(new Error('Unavailable'))
      .mockResolvedValue(emptyResponse)
    const view = mountView()
    try {
      await flushPromises()
      expect(view.find('[role="alert"]').exists()).toBe(true)
      await view
        .findAll('button')
        .find((node) => node.text() === 'common.refresh')!
        .trigger('click')
      await flushPromises()
      expect(view.find('[role="alert"]').exists()).toBe(false)
      expect(view.text()).toContain('system.backup.noBackups')
    } finally {
      view.unmount()
    }
  })
})
