import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import DataTableToolbar from './DataTableToolbar.vue'

vi.stubGlobal(
  'ResizeObserver',
  class {
    observe() {}
    unobserve() {}
    disconnect() {}
  },
)

describe('DataTableToolbar selected filter labels', () => {
  it('updates a closed filter label without changing the selected value', async () => {
    const i18n = createI18n({ legacy: false, locale: 'en-US', messages: { 'en-US': {} } })
    const wrapper = mount(DataTableToolbar, {
      props: {
        searchModelValue: '',
        searchPlaceholder: 'Search',
        filters: [
          {
            modelValue: 'all',
            placeholder: '全部状态',
            options: [{ value: 'all', label: '全部状态' }],
          },
        ],
      },
      global: { plugins: [i18n] },
    })
    await flushPromises()
    expect(wrapper.get('[role="combobox"]').text()).toContain('全部状态')
    expect(wrapper.get('[role="combobox"]').attributes('aria-label')).toBe('全部状态')
    await wrapper.setProps({
      filters: [
        {
          modelValue: 'all',
          placeholder: 'All Statuses',
          options: [{ value: 'all', label: 'All Statuses' }],
        },
      ],
    })
    await flushPromises()
    expect(wrapper.get('[role="combobox"]').text()).toContain('All Statuses')
    expect(wrapper.get('[role="combobox"]').attributes('aria-label')).toBe('All Statuses')
    expect(wrapper.emitted('update:filter')).toBeUndefined()
    wrapper.unmount()
  })
})
