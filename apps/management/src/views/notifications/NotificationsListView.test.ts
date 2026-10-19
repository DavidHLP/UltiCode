import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { createI18n } from 'vue-i18n'
import { afterEach, describe, expect, it, vi } from 'vitest'
import NotificationsListView from './NotificationsListView.vue'
import type { SystemAnnouncement } from '@/api/admin/notifications'

const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<SystemAnnouncement[]>([])
const total = ref(0)
const refresh = vi.fn()

vi.mock('@/components/table/DataTable.vue', () => ({
  default: {
    name: 'DataTable',
    props: ['data', 'rowCount', 'hidePagination'],
    template: '<div><slot name="toolbar-left" /><slot name="empty" /></div>',
  },
}))
vi.mock('@/stores/admin/notifications', () => ({
  useNotificationsStore: () => ({ items: [], total: 0 }),
}))
vi.mock('@/composables/useRemoteTable', () => ({
  useRemoteTable: () => ({
    query: ref({ filters: { type: 'all', category: 'all' } }),
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

describe('notification list header', () => {
  it('lets the title and create action wrap with a gap on narrow screens', () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(NotificationsListView, {
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
      expect(header.textContent).toContain('notifications.newNotification')
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

describe('notification list read failures', () => {
  it('does not present failed reads as zero statistics, successful emptiness or old editable rows', async () => {
    vi.useFakeTimers()
    data.value = [
      {
        id: 'old',
        title: 'Old notification',
        content: '',
        type: 'SYSTEM',
        createdAt: '2026-10-01T00:00:00Z',
      },
    ]
    total.value = 1
    const wrapper = shallowMount(NotificationsListView, {
      global: {
        plugins: [
          createI18n({ legacy: false, locale: 'en-US', missingWarn: false, fallbackWarn: false }),
        ],
        renderStubDefaultSlot: true,
        stubs: { DataTable: false },
      },
    })
    try {
      loading.value = true
      await nextTick()
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      loading.value = false
      error.value = 'Network Error'
      await nextTick()
      const table = wrapper.findComponent({ name: 'DataTable' })
      expect(table.props('data')).toEqual([])
      expect(table.props('rowCount')).toBe(0)
      expect(table.props('hidePagination')).toBe(true)
      expect(wrapper.text().match(/—/g)).toHaveLength(4)
      expect(wrapper.get('[role="alert"]').text()).toContain('Network Error')
      expect(wrapper.findComponent({ name: 'DataTableToolbar' }).exists()).toBe(true)
      wrapper.get('[role="alert"]').findComponent({ name: 'Button' }).vm.$emit('click')
      expect(refresh).toHaveBeenCalledTimes(1)
      error.value = null
      data.value = []
      total.value = 0
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
