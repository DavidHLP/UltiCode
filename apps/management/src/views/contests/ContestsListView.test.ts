import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { createI18n } from 'vue-i18n'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ContestsListView from './ContestsListView.vue'

const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<{ id: string; status: string }[]>([])
const total = ref(0)
const refresh = vi.fn()

vi.mock('@/components/table/DataTable.vue', () => ({
  default: {
    name: 'DataTable',
    props: ['data', 'rowCount', 'hidePagination'],
    template: '<div><slot name="toolbar-left" /><slot name="empty" /></div>',
  },
}))
vi.mock('@/stores/admin/contests', () => ({
  useContestsStore: () => ({
    get items() {
      return data.value
    },
    get total() {
      return total.value
    },
  }),
}))
vi.mock('@/composables/usePermissionMap', () => ({
  usePermissionMap: () => ({ create: true, delete: false }),
}))
vi.mock('./columns', () => ({ createColumns: () => [] }))
vi.mock('@/composables/useRemoteTable', () => ({
  useRemoteTable: () => ({
    query: ref({ filters: { statusFilter: 'all', typeFilter: 'all' } }),
    searchQuery: ref(''),
    tablePagination: ref({ pageIndex: 0, pageSize: 10 }),
    loading,
    data,
    total,
    error,
    refresh,
    setFilters: vi.fn(),
  }),
}))

describe('contest list header', () => {
  it('lets the title and create action wrap with a gap on narrow screens', () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(ContestsListView, {
      global: {
        plugins: [
          createI18n({ legacy: false, locale: 'en-US', missingWarn: false, fallbackWarn: false }),
        ],
        renderStubDefaultSlot: true,
      },
    })
    try {
      const header = wrapper.get('h1').element.parentElement!
      expect(header.classList).toContain('flex-wrap')
      expect(header.classList).toContain('gap-3')
      expect(header.textContent).toContain('contests.createContest')
    } finally {
      wrapper.unmount()
      vi.runOnlyPendingTimers()
      vi.useRealTimers()
    }
  })
})

afterEach(() => {
  loading.value = false
  error.value = null
  data.value = []
  total.value = 0
  vi.clearAllMocks()
})

describe('contest list read failures', () => {
  it('replaces stale rows, statistics, pagination and bulk actions with a retryable error', async () => {
    vi.useFakeTimers()
    data.value = [{ id: 'old-contest', status: 'FINISHED' }]
    total.value = 1
    const wrapper = shallowMount(ContestsListView, {
      global: {
        plugins: [
          createI18n({ legacy: false, locale: 'en-US', missingWarn: false, fallbackWarn: false }),
        ],
        renderStubDefaultSlot: true,
        stubs: { DataTable: false },
      },
    })
    try {
      const table = wrapper.findComponent({ name: 'DataTable' })
      table.vm.$emit('update:selectedRows', data.value)
      await nextTick()
      expect(wrapper.find('.animate-in').exists()).toBe(true)
      loading.value = true
      await nextTick()
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      loading.value = false
      error.value = 'Network Error'
      await nextTick()
      expect(table.props('data')).toEqual([])
      expect(table.props('rowCount')).toBe(0)
      expect(table.props('hidePagination')).toBe(true)
      expect(wrapper.find('.animate-in').exists()).toBe(false)
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      expect(wrapper.get('[role="alert"]').text()).toContain('Network Error')
      expect(wrapper.findComponent({ name: 'DataTableToolbar' }).exists()).toBe(true)
      wrapper.get('[role="alert"]').findComponent({ name: 'Button' }).vm.$emit('click')
      expect(refresh).toHaveBeenCalledTimes(1)
      error.value = null
      data.value = []
      total.value = 0
      table.vm.$emit('update:selectedRows', [])
      await nextTick()
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(table.vm.$slots.empty).toBeUndefined()
      expect(table.props('hidePagination')).toBe(false)
      expect(wrapper.findAll('.tabular-nums').map((node) => node.text())).toEqual([
        '0',
        '0',
        '0',
        '0',
      ])
    } finally {
      wrapper.unmount()
      vi.clearAllTimers()
      vi.useRealTimers()
    }
  })
})
