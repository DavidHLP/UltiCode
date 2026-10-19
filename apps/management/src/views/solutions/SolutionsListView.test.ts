import { shallowMount } from '@vue/test-utils'
import { nextTick, reactive, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SolutionsListView from './SolutionsListView.vue'

const loading = ref(false)
const error = ref<string | null>(null)
const data = ref([{ id: 'old-solution' }])
const total = ref(12)
const refresh = vi.fn()
const store = reactive({ totalCount: 12, flaggedCount: 0, publishedCount: 10 })
vi.mock('@/stores/admin/solutions', () => ({ useSolutionsStore: () => store }))
vi.mock('@/composables/usePermissionMap', () => ({ usePermissionMap: () => ({}) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
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
    query: ref({ filters: { flaggedFilter: 'all', publishedFilter: 'all' } }),
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
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.clearAllMocks()
  loading.value = false
  error.value = null
  data.value = [{ id: 'old-solution' }]
  total.value = 12
  Object.assign(store, { totalCount: 12, flaggedCount: 0, publishedCount: 10 })
})

describe('solution list read failures', () => {
  it('keeps filters and retry while hiding stale statistics, editable rows and pagination', async () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(SolutionsListView, {
      global: { renderStubDefaultSlot: true, stubs: { DataTable: false } },
    })
    try {
      const table = wrapper.findComponent({ name: 'DataTable' })
      loading.value = true
      await nextTick()
      expect(wrapper.text().match(/—/g)).toHaveLength(3)
      loading.value = false
      error.value = 'Network Error'
      await nextTick()
      expect(table.props('data')).toEqual([])
      expect(table.props('rowCount')).toBe(0)
      expect(table.props('hidePagination')).toBe(true)
      expect(wrapper.text().match(/—/g)).toHaveLength(3)
      expect(wrapper.get('[role="alert"]').text()).toContain('Network Error')
      expect(wrapper.findComponent({ name: 'DataTableToolbar' }).exists()).toBe(true)
      wrapper.get('[role="alert"]').findComponent({ name: 'Button' }).vm.$emit('click')
      expect(refresh).toHaveBeenCalledTimes(1)
      error.value = null
      data.value = []
      total.value = 0
      Object.assign(store, { totalCount: 0, flaggedCount: 0, publishedCount: 0 })
      await nextTick()
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(table.vm.$slots.empty).toBeUndefined()
      expect(table.props('hidePagination')).toBe(false)
      expect(wrapper.findAll('.tabular-nums').map((node) => node.text())).toEqual(['0', '0', '0'])
    } finally {
      wrapper.unmount()
    }
  })
})
