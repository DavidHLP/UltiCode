import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UsersListView from './UsersListView.vue'

const error = ref<string | null>(null)
const data = ref([{ id: 'old-page-user' }])
vi.mock('@/composables/usePermissionMap', () => ({
  usePermissionMap: () => ({ create: ref(true), delete: ref(true) }),
}))
vi.mock('@/stores/admin/users', () => ({
  useUsersStore: () => ({ items: [{ isActive: true, isBanned: false }], total: 21 }),
}))
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
    query: ref({ filters: { role: 'all', status: 'all' } }),
    searchQuery: ref(''),
    tablePagination: ref({ pageIndex: 1, pageSize: 10 }),
    loading: ref(false),
    data,
    total: ref(21),
    error,
    refresh: vi.fn(),
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
  error.value = null
})

describe('user list failed pagination', () => {
  it('replaces stale rows and pagination with an error but keeps filters available', async () => {
    vi.useFakeTimers()
    error.value = 'Forbidden'
    const wrapper = shallowMount(UsersListView, {
      global: { renderStubDefaultSlot: true, stubs: { DataTable: false } },
    })
    try {
      const table = wrapper.findComponent({ name: 'DataTable' })
      expect(table.props('data')).toEqual([])
      expect(table.props('rowCount')).toBe(0)
      expect(table.props('hidePagination')).toBe(true)
      expect(wrapper.find('[role="alert"]').text()).toContain('Forbidden')
      expect(wrapper.findComponent({ name: 'DataTableToolbar' }).exists()).toBe(true)
      expect(wrapper.text().match(/—/g)).toHaveLength(3)
      error.value = null
      await nextTick()
      expect(table.props('data')).toEqual(data.value)
      expect(table.props('hidePagination')).toBe(false)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('—')
    } finally {
      wrapper.unmount()
    }
  })
})
