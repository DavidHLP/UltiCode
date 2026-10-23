import { shallowMount } from '@vue/test-utils'
import { nextTick, reactive, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SubmissionsView from './SubmissionsView.vue'

const error = ref<string | null>(null)
const data = ref([{ id: 'old-page-submission' }])
const statistics = { total: 81, pending: 1, topLanguage: 'python', acceptedRate: '77.8' }
const store = reactive({
  stats: statistics as typeof statistics | null,
  statsError: null as string | null,
  statuses: [],
  languages: [],
  fetchStatistics: vi.fn(),
  fetchFilters: vi.fn(),
})
vi.mock('@/stores/admin/submissions', () => ({ useSubmissionsStore: () => store }))
vi.mock('./columns', () => ({ createColumns: () => [] }))
vi.mock('@/components/table/DataTable.vue', () => ({
  default: {
    name: 'DataTable',
    props: ['data', 'rowCount', 'hidePagination'],
    template: '<div><slot name="toolbar-left" /><slot name="empty" /></div>',
  },
}))
vi.mock('@/composables/useRemoteTable', () => ({
  useRemoteTable: () => ({
    query: ref({ filters: { status: 'all', language: 'all' } }),
    searchQuery: ref(''),
    tablePagination: ref({ pageIndex: 1, pageSize: 10 }),
    loading: ref(false),
    data,
    total: ref(81),
    error,
    refresh: vi.fn(),
    setFilters: vi.fn(),
  }),
}))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key, te: () => false }),
}))

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.clearAllMocks()
  error.value = null
  store.stats = statistics
  store.statsError = null
})

describe('submission read failure states', () => {
  it('uses shared whole-dialog scrolling without fixed short-screen header and footer', () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(SubmissionsView, {
      global: { renderStubDefaultSlot: true, stubs: { DataTable: false } },
    })
    try {
      const dialog = wrapper.findAllComponents({ name: 'DialogContent' })[0]!
      expect(dialog.classes()).not.toContain('flex')
      expect(dialog.findComponent({ name: 'DialogHeader' }).classes()).not.toContain('shrink-0')
      expect(dialog.findComponent({ name: 'DialogFooter' }).classes()).not.toContain('shrink-0')
    } finally {
      wrapper.unmount()
    }
  })

  it('hides stale list rows, pagination and bulk actions while keeping independent statistics and retry', async () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(SubmissionsView, {
      global: { renderStubDefaultSlot: true, stubs: { DataTable: false } },
    })
    try {
      const table = wrapper.findComponent({ name: 'DataTable' })
      await table.vm.$emit('update:selectedRows', data.value)
      expect(wrapper.find('.animate-in').exists()).toBe(true)
      error.value = 'Network Error'
      await nextTick()
      expect(table.props('data')).toEqual([])
      expect(table.props('rowCount')).toBe(0)
      expect(table.props('hidePagination')).toBe(true)
      expect(wrapper.find('.animate-in').exists()).toBe(false)
      expect(wrapper.get('[role="alert"]').text()).toContain('Network Error')
      expect(wrapper.findComponent({ name: 'DataTableToolbar' }).exists()).toBe(true)
      expect(wrapper.text()).toContain('77.8%')
      error.value = null
      await nextTick()
      expect(table.props('data')).toEqual(data.value)
      expect(table.props('hidePagination')).toBe(false)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)

      store.stats = null
      store.statsError = 'statistics unavailable'
      await nextTick()
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      expect(wrapper.get('[role="alert"]').text()).toContain('submissions.loadStatisticsError')
      await wrapper.get('[role="alert"]').findComponent({ name: 'Button' }).vm.$emit('click')
      expect(store.fetchStatistics).toHaveBeenCalledTimes(2)
      expect(table.props('data')).toEqual(data.value)
    } finally {
      wrapper.unmount()
    }
  })
})
