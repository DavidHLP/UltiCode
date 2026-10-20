import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import VersionHistoryTimeline from './VersionHistoryTimeline.vue'

const { api, toast } = vi.hoisted(() => ({
  api: {
    getProblemVersions: vi.fn(),
    getProblemVersion: vi.fn(),
    getVersionDiff: vi.fn(),
    rollbackToVersion: vi.fn(),
    createInitialVersion: vi.fn(),
  },
  toast: { error: vi.fn(), success: vi.fn(), info: vi.fn() },
}))

vi.mock('@/api/admin/problems', () => ({ problemsApi: api }))
vi.mock('vue-sonner', () => ({ toast }))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))

function mountTimeline() {
  return shallowMount(VersionHistoryTimeline, {
    props: { open: false, problemId: '7' },
    global: { renderStubDefaultSlot: true },
  })
}

describe('version history loading states', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a retryable error instead of the empty state when loading fails', async () => {
    api.getProblemVersions.mockRejectedValueOnce(new Error('Forbidden'))
    const wrapper = mountTimeline()

    try {
      await wrapper.setProps({ open: true })
      await flushPromises()

      expect(wrapper.find('[role="alert"]').text()).toContain('problems.versionHistory.loadError')
      expect(wrapper.text()).toContain('common.retry')
      expect(wrapper.text()).not.toContain('problems.versionHistory.noVersions')
      expect(wrapper.text()).not.toContain('problems.versionHistory.createInitial')
      expect(api.createInitialVersion).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
    }
  })

  it('shows the empty state only after a successful empty response', async () => {
    api.getProblemVersions.mockRejectedValueOnce(new Error('Forbidden')).mockResolvedValueOnce({
      versions: [],
      pagination: { total: 0, page: 1, limit: 20, totalPages: 0 },
    })
    const wrapper = mountTimeline()

    try {
      await wrapper.setProps({ open: true })
      await flushPromises()
      await wrapper
        .findAllComponents({ name: 'Button' })
        .find((button) => button.text() === 'common.retry')!
        .vm.$emit('click')
      await flushPromises()

      expect(api.getProblemVersions).toHaveBeenCalledTimes(2)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.text()).toContain('problems.versionHistory.noVersions')
      expect(wrapper.text()).toContain('problems.versionHistory.createInitial')
    } finally {
      wrapper.unmount()
    }
  })
})
