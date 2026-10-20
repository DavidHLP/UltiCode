import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProblemsListView from './ProblemsListView.vue'

const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<{ id: string; isPublished: boolean; isFlagged: boolean }[]>([])
const total = ref(0)
const selectedRows = ref<typeof data.value>([])
const refresh = vi.fn()
const locale = ref('en-US')
const query = ref({
  filters: {
    difficulty: 'all',
    status: 'all',
    published: 'all',
    sortBy: 'default',
    sortOrder: 'desc',
  },
})
vi.mock('@/components/table/DataTable.vue', () => ({
  default: {
    name: 'DataTable',
    props: ['data', 'rowCount', 'hidePagination'],
    template: '<div><slot name="toolbar-left" /><slot name="empty" /></div>',
  },
}))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => `${locale.value}:${key}` }),
}))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/stores/admin/problems', () => ({
  useProblemsStore: () => ({
    get items() {
      return data.value
    },
    get total() {
      return total.value
    },
  }),
}))
vi.mock('@/composables/usePermissionMap', () => ({ usePermissionMap: () => ({ create: false }) }))
vi.mock('./composables/useProblemColumns', () => ({ useProblemColumns: () => [] }))
vi.mock('./composables/useProblemActions', () => ({
  useProblemActions: () => ({ selectedRows, bulkActionLoading: ref(false) }),
}))
vi.mock('./composables/useProblemFilters', () => ({
  useProblemFilters: () => ({ initialQuery: {}, routeAdapter: {}, buildExportParams: vi.fn() }),
}))
vi.mock('@/composables/useRemoteTable', () => ({
  useRemoteTable: () => ({
    query,
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

describe('problem sorting label', () => {
  it('updates the closed label when language or the selected sort changes', async () => {
    const wrapper = shallowMount(ProblemsListView, {
      global: {
        renderStubDefaultSlot: true,
        stubs: {
          DataTable: { template: '<div><slot name="toolbar-left" /></div>' },
          DataTableToolbar: { template: '<div><slot name="extra-actions" /></div>' },
        },
      },
    })
    try {
      expect(wrapper.get('h1').element.parentElement?.classList).toContain('flex-wrap')
      expect(wrapper.get('h1').element.parentElement?.classList).toContain('gap-3')
      const value = wrapper.findComponent({ name: 'SelectValue' })
      expect(value.text()).toBe('en-US:problems.sort.default')
      expect(wrapper.get('[aria-label$="problems.sort.desc"]').attributes('title')).toContain(
        'problems.sort.desc',
      )
      query.value.filters.sortOrder = 'asc'
      await nextTick()
      expect(wrapper.get('[aria-label$="problems.sort.asc"]').attributes('title')).toContain(
        'problems.sort.asc',
      )
      locale.value = 'zh-CN'
      await nextTick()
      expect(value.text()).toBe('zh-CN:problems.sort.default')
      query.value.filters.sortBy = 'title'
      await nextTick()
      expect(value.text()).toBe('zh-CN:problems.columns.title')
      query.value.filters.sortBy = 'unknown'
      await nextTick()
      expect(value.text()).toBe('unknown')
    } finally {
      wrapper.unmount()
    }
  })
})

beforeEach(() => vi.useFakeTimers())
afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.clearAllMocks()
  loading.value = false
  error.value = null
  data.value = []
  total.value = 0
  selectedRows.value = []
  locale.value = 'en-US'
  query.value.filters.sortBy = 'default'
  query.value.filters.sortOrder = 'desc'
})

describe('problem list read failures', () => {
  it('hides stale rows, statistics, pagination and bulk actions but retains filters and retry', async () => {
    data.value = [{ id: 'old-problem', isPublished: true, isFlagged: false }]
    total.value = 1
    selectedRows.value = data.value
    const wrapper = shallowMount(ProblemsListView, {
      global: { renderStubDefaultSlot: true, stubs: { DataTable: false } },
    })
    try {
      const table = wrapper.findComponent({ name: 'DataTable' })
      expect(wrapper.findComponent({ name: 'ProblemBulkActions' }).exists()).toBe(true)
      loading.value = true
      await nextTick()
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      loading.value = false
      error.value = 'Network Error'
      await nextTick()
      expect(table.props('data')).toEqual([])
      expect(table.props('rowCount')).toBe(0)
      expect(table.props('hidePagination')).toBe(true)
      expect(wrapper.findComponent({ name: 'ProblemBulkActions' }).exists()).toBe(false)
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      expect(wrapper.get('[role="alert"]').text()).toContain('Network Error')
      expect(wrapper.findComponent({ name: 'DataTableToolbar' }).exists()).toBe(true)
      wrapper.get('[role="alert"]').findComponent({ name: 'Button' }).vm.$emit('click')
      expect(refresh).toHaveBeenCalledTimes(2)
      error.value = null
      data.value = []
      total.value = 0
      selectedRows.value = []
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
    }
  })
})
