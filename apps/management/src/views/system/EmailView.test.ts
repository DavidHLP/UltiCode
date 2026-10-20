import { flushPromises, shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { EmailListResponse } from '@/api/admin/email'
import EmailView from './EmailView.vue'

const { getLogs, getTemplates, getStats, toastError } = vi.hoisted(() => ({
  getLogs: vi.fn(),
  getTemplates: vi.fn(),
  getStats: vi.fn(),
  toastError: vi.fn(),
}))
vi.mock('@/api/admin/email', () => ({ emailApi: { getLogs, getTemplates, getStats } }))
vi.mock('vue-sonner', () => ({ toast: { error: toastError } }))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))

const emptyLogs: EmailListResponse = { items: [], total: 0, page: 1, limit: 50 }
const zeroStats = { total: 0, sent: 0, pending: 0, failed: 0 }
function pendingLogs() {
  let resolve!: (value: EmailListResponse) => void
  let reject!: (error: Error) => void
  const promise = new Promise<EmailListResponse>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
function mountView() {
  return shallowMount(EmailView, {
    global: {
      renderStubDefaultSlot: true,
      stubs: { Dialog: { props: ['open'], template: '<div v-if="open"><slot /></div>' } },
    },
  })
}

beforeEach(() => {
  getLogs.mockResolvedValue(emptyLogs)
  getTemplates.mockResolvedValue([])
  getStats.mockResolvedValue(zeroStats)
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

describe('email data read states', () => {
  it('distinguishes loading and failure from successful zero/empty results and retries all reads', async () => {
    const pending = pendingLogs()
    getLogs.mockReturnValueOnce(pending.promise)
    const wrapper = mountView()
    try {
      await nextTick()
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      expect(wrapper.text()).not.toContain('system.email.logs.noLogs')
      expect(wrapper.text()).not.toContain('system.email.templates.noTemplates')
      pending.reject(new Error('Network Error'))
      await flushPromises()
      expect(wrapper.find('[role="alert"]').text()).toContain('system.email.toast.loadFailed')
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      expect(wrapper.text()).not.toContain('system.email.logs.noLogs')
      expect(wrapper.text()).not.toContain('system.email.templates.noTemplates')
      wrapper.find('[role="alert"]').findComponent({ name: 'Button' }).vm.$emit('click')
      await flushPromises()
      expect(getLogs).toHaveBeenCalledTimes(2)
      expect(getLogs).toHaveBeenLastCalledWith({ limit: 50 })
      expect(getTemplates).toHaveBeenCalledTimes(2)
      expect(getStats).toHaveBeenCalledTimes(2)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.findAll('.text-2xl').map((node) => node.text())).toEqual(['0', '0', '0', '0'])
      expect(wrapper.text()).toContain('system.email.logs.noLogs')
      expect(wrapper.text()).toContain('system.email.templates.noTemplates')
    } finally {
      wrapper.unmount()
    }
  })

  it('clears cached statistics, logs and template actions when refresh fails', async () => {
    getLogs.mockResolvedValueOnce({
      ...emptyLogs,
      items: [{ id: 'old-log', subject: 'Old log', status: 'SENT' }],
    })
    getTemplates.mockResolvedValueOnce([{ id: 'old-template', name: 'Old template' }])
    getStats.mockResolvedValueOnce({ ...zeroStats, total: 12, sent: 12 })
    const wrapper = mountView()
    try {
      await flushPromises()
      expect(wrapper.text()).toContain('Old log')
      expect(wrapper.text()).toContain('Old template')
      const pending = pendingLogs()
      getLogs.mockReturnValueOnce(pending.promise)
      wrapper
        .findAllComponents({ name: 'Button' })
        .find((button) => button.text() === 'common.refresh')!
        .vm.$emit('click')
      await flushPromises()
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      expect(wrapper.text()).not.toContain('Old log')
      expect(wrapper.text()).not.toContain('Old template')
      pending.reject(new Error('Network Error'))
      await flushPromises()
      expect(wrapper.find('[role="alert"]').exists()).toBe(true)
      expect(wrapper.text()).not.toContain('Old log')
      expect(wrapper.text()).not.toContain('Old template')
      expect(wrapper.text()).not.toContain('system.email.templates.create')
    } finally {
      wrapper.unmount()
    }
  })

  it('ignores outdated responses and failures after unmount', async () => {
    const pending = pendingLogs()
    getLogs.mockReturnValueOnce(pending.promise)
    getStats.mockResolvedValueOnce({ ...zeroStats, total: 99 })
    const wrapper = mountView()
    try {
      wrapper
        .findAllComponents({ name: 'Button' })
        .find((button) => button.text() === 'common.refresh')!
        .vm.$emit('click')
      await flushPromises()
      pending.resolve(emptyLogs)
      await flushPromises()
      expect(wrapper.findAll('.text-2xl').map((node) => node.text())).toEqual(['0', '0', '0', '0'])
    } finally {
      wrapper.unmount()
    }
    const cancelled = pendingLogs()
    getLogs.mockReturnValueOnce(cancelled.promise)
    const unmounted = mountView()
    unmounted.unmount()
    cancelled.reject(new Error('Late failure'))
    await flushPromises()
    expect(toastError).not.toHaveBeenCalled()
  })
})
