import { shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import { expect, it, vi } from 'vitest'
import { useRemoteTable } from '@/composables/useRemoteTable'
import AppealsView from './AppealsView.vue'

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
  useModerationStore: () => ({ appealsCollection: {}, appealsTotal: 0, appeals: [] }),
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

it('sends the explicitly labelled queue ID search to the existing paginated API contract', () => {
  vi.useFakeTimers()
  const wrapper = shallowMount(AppealsView, {
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
    expect(toParams({ ...params, search: ' mod-queue-008 ' })).toMatchObject({
      queueId: 'mod-queue-008',
      page: 2,
      limit: 10,
    })
    expect(toParams({ ...params, search: ' ' })).toMatchObject({ queueId: undefined })
    expect(wrapper.findComponent({ name: 'DataTableToolbar' }).props('searchPlaceholder')).toBe(
      'moderation.appeals.searchPlaceholder',
    )
  } finally {
    wrapper.unmount()
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.clearAllMocks()
  }
})
