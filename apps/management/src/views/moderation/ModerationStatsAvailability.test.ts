import { flushPromises, shallowMount } from '@vue/test-utils'
import { reactive, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import ModerationQueueView from './ModerationQueueView.vue'
import ModerationDashboardView from './ModerationDashboardView.vue'

const store = reactive({
  queue: {},
  queueTotal: 8,
  queueError: null as string | null,
  stats: null as null | {
    pendingCount: number
    underReviewCount: number
    resolvedCount: number
    byCategory: Record<string, number>
    byEntityType: Record<string, number>
  },
  statsLoading: false,
  statsError: null as string | null,
  fetchStats: vi.fn(),
})
vi.mock('@/stores/admin/moderation', () => ({ useModerationStore: () => store }))
vi.mock('@/components/table/DataTable.vue', () => ({ default: { template: '<div />' } }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/composables/useRemoteTable', () => ({
  useRemoteTable: () => ({
    query: ref({ filters: { status: 'all', category: 'all', entityType: 'all' } }),
    searchQuery: ref(''),
    tablePagination: ref({ pageIndex: 0, pageSize: 10 }),
    loading: ref(false),
    data: ref([]),
    total: ref(8),
    refresh: vi.fn(),
    setFilters: vi.fn(),
  }),
}))
beforeEach(() => {
  vi.useFakeTimers()
  store.stats = null
  store.statsError = null
  store.statsLoading = false
})
afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.clearAllMocks()
})
const slots = { template: '<div><slot /></div>' }
const stubs = {
  Card: slots,
  CardHeader: slots,
  CardTitle: slots,
  CardContent: slots,
  RouterLink: slots,
}

it.each([
  { component: ModerationQueueView, name: 'queue' },
  { component: ModerationDashboardView, name: 'dashboard' },
])(
  'keeps $name unavailable statistics distinct from successful zero values',
  async ({ component, name }) => {
    const wrapper = shallowMount(component, { global: { stubs } })
    try {
      await flushPromises()
      expect(wrapper.text()).toContain('—')
      store.stats = {
        pendingCount: 7,
        underReviewCount: 3,
        resolvedCount: 0,
        byCategory: { SPAM: 7 },
        byEntityType: { forum_post: 7 },
      }
      store.statsError = 'unavailable'
      await flushPromises()
      expect(wrapper.get('[role="alert"]').text()).toContain('moderation.stats.loadError')
      expect(wrapper.text()).not.toContain('moderation.categories.SPAM')
      expect(wrapper.findAll('.tabular-nums').map((node) => node.text())).toEqual(
        name === 'queue' ? ['8', '—', '—', '—'] : ['—', '—', '—', '—', '—'],
      )
      store.statsError = null
      store.statsLoading = true
      await flushPromises()
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.text()).toContain('—')
      store.statsLoading = false
      await flushPromises()
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.text()).toContain('7')
      expect(wrapper.text()).toContain('0')
      if (name === 'queue') expect(wrapper.text()).toContain('8')
      else expect(wrapper.text()).toContain('moderation.categories.SPAM')
    } finally {
      wrapper.unmount()
    }
  },
)
