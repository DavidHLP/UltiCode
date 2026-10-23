import { flushPromises, shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ScoringRulesView from './ScoringRulesView.vue'

const { getAll } = vi.hoisted(() => ({ getAll: vi.fn() }))
vi.mock('@/api/admin/scoring-rules', () => ({ scoringRulesApi: { getAll } }))
vi.mock('@/components/table/DataTable.vue', () => ({
  default: { name: 'DataTable', props: ['data', 'loading'], template: '<div />' },
}))
vi.mock('@/composables/usePermissionMap', () => ({
  usePermissionMap: () => ({ manageUsers: ref(true), update: ref(true) }),
}))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('scoring rules load states', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('shows unavailable statistics and only the error state after a failure', async () => {
    getAll.mockRejectedValueOnce(new Error('Forbidden'))
    const wrapper = shallowMount(ScoringRulesView, { global: { renderStubDefaultSlot: true } })
    try {
      await flushPromises()
      expect(wrapper.findComponent({ name: 'DataTable' }).exists()).toBe(false)
      expect(wrapper.find('[role="alert"]').text()).toContain('scoringRules.loadError')
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
    } finally {
      wrapper.unmount()
    }
  })

  it('keeps pending statistics distinct from zero', async () => {
    getAll.mockReturnValueOnce(new Promise(() => {}))
    const wrapper = shallowMount(ScoringRulesView, { global: { renderStubDefaultSlot: true } })
    try {
      await flushPromises()
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      expect(wrapper.findComponent({ name: 'DataTable' }).props('loading')).toBe(true)
    } finally {
      wrapper.unmount()
    }
  })

  it('restores a genuine empty table and zero statistics after retry succeeds', async () => {
    getAll.mockRejectedValueOnce(new Error('Forbidden')).mockResolvedValueOnce([])
    const wrapper = shallowMount(ScoringRulesView, { global: { renderStubDefaultSlot: true } })
    try {
      await flushPromises()
      await wrapper
        .findAllComponents({ name: 'Button' })
        .find((b) => b.text() === 'common.retry')!
        .vm.$emit('click')
      await flushPromises()
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'DataTable' }).props('data')).toEqual([])
      expect(wrapper.text()).not.toContain('—')
      expect(wrapper.text().match(/0/g)).toHaveLength(4)
    } finally {
      wrapper.unmount()
    }
  })
})
