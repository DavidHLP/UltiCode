import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'
import StatCards from '@/components/dashboard/StatCards.vue'
import AreaChart from '@/components/dashboard/AreaChart.vue'
import DashboardTimeline from '@/components/dashboard/DashboardTimeline.vue'
import { Button } from '@ulticode/design-system/ui/button'

const { fetchStats, fetchChartStats, fetchLogs } = vi.hoisted(() => ({
  fetchStats: vi.fn(),
  fetchChartStats: vi.fn(),
  fetchLogs: vi.fn(),
}))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ userName: 'unit-admin', userRole: 'ADMIN' }),
}))
vi.mock('@/stores/admin/dashboard', () => ({
  useDashboardStore: () => ({
    stats: {
      users: { total: 21, activeToday: 1, activeWeek: 1 },
      problems: { total: 6, published: 6, unpublished: 0 },
      contests: { running: 0, upcoming: 0, finished: 5 },
      solutions: { flagged: 0 },
      forum: { flaggedPosts: 0, flaggedComments: 0 },
    },
    chartData: { data: [{ date: '2026-10-01', count: 1 }] },
    fetchStats,
    fetchChartStats,
  }),
}))
vi.mock('@/stores/admin/audit', () => ({
  useAuditStore: () => ({ logs: [], fetchLogs }),
}))

const wrappers: ReturnType<typeof shallowMount>[] = []
function mountView() {
  const wrapper = shallowMount(DashboardView, {
    global: { renderStubDefaultSlot: true },
  })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  for (const fetch of [fetchStats, fetchChartStats, fetchLogs]) {
    fetch.mockReset().mockResolvedValue(undefined)
  }
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => {
  wrappers.forEach((wrapper) => wrapper.unmount())
  wrappers.length = 0
  vi.restoreAllMocks()
})

describe('dashboard read failures', () => {
  it.each([
    [fetchStats, StatCards, 'dashboard.loadStatsError'],
    [fetchChartStats, AreaChart, 'dashboard.loadChartError'],
    [fetchLogs, DashboardTimeline, 'dashboard.loadActivityError'],
  ] as const)(
    'hides the failed section, preserves successful reads and retries: %s',
    async (fetch, component, message) => {
      fetch.mockRejectedValueOnce(new Error('offline'))
      const wrapper = mountView()
      await flushPromises()
      expect(wrapper.find('[role="alert"]').text()).toContain(message)
      expect(wrapper.findComponent(component).exists()).toBe(false)
      for (const successful of [StatCards, AreaChart, DashboardTimeline]) {
        if (successful !== component) expect(wrapper.findComponent(successful).exists()).toBe(true)
      }
      await wrapper.getComponent(Button).trigger('click')
      await flushPromises()
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.findComponent(component).exists()).toBe(true)
      expect(fetchStats).toHaveBeenCalledTimes(2)
      expect(fetchChartStats).toHaveBeenCalledTimes(2)
      expect(fetchLogs).toHaveBeenLastCalledWith({ limit: 10 }, { rethrow: true })
      expect(fetchLogs).toHaveBeenCalledTimes(2)
    },
  )

  it('waits for remaining reads before offering retry after an early rejection', async () => {
    let finish!: () => void
    fetchStats.mockRejectedValueOnce(new Error('offline'))
    fetchChartStats.mockReturnValueOnce(
      new Promise<void>((resolve) => {
        finish = resolve
      }),
    )
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    finish()
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').text()).toContain('dashboard.loadStatsError')
  })
})
