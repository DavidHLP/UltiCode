import { shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import { expect, it, vi } from 'vitest'
import { useRemoteTable } from '@/composables/useRemoteTable'
import ReportsView from './ReportsView.vue'

vi.mock('@/components/table/DataTable.vue', () => ({
  default: { name: 'DataTable', template: '<div><slot name="toolbar-left" /></div>' },
}))

vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/stores/admin/moderation', () => ({
  useModerationStore: () => ({ reportsCollection: {}, reportsTotal: 0, reports: [] }),
}))
vi.mock('@/composables/useRemoteTable', () => ({
  useRemoteTable: vi.fn(() => ({
    query: ref({ filters: { status: 'all', category: 'all', entityType: 'all' } }),
    searchQuery: ref(''),
    tablePagination: ref({ pageIndex: 0, pageSize: 10 }),
    loading: ref(false),
    data: ref([]),
    total: ref(0),
    refresh: vi.fn(),
    setFilters: vi.fn(),
  })),
}))

it('sends the explicitly labelled entity ID search to the existing paginated API contract', () => {
  vi.useFakeTimers()
  const wrapper = shallowMount(ReportsView, {
    global: { stubs: { DataTable: false } },
  })
  try {
    expect(wrapper.text()).toContain('moderation.terminal.pendingOnPage')
    const { toParams } = vi.mocked(useRemoteTable).mock.calls[0]![0]
    const params = {
      filters: { status: 'all', category: 'all', entityType: 'all' },
      page: 2,
      limit: 10,
    }
    expect(toParams({ ...params, search: ' fpost-005 ' })).toMatchObject({
      entityId: 'fpost-005',
      page: 2,
      limit: 10,
    })
    expect(toParams({ ...params, search: ' ' })).toMatchObject({ entityId: undefined })
    expect(wrapper.findComponent({ name: 'DataTableToolbar' }).props('searchPlaceholder')).toBe(
      'moderation.reports.searchPlaceholder',
    )
  } finally {
    wrapper.unmount()
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.clearAllMocks()
  }
})
