import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import AnalyticsView from './AnalyticsView.vue'

const locale = ref('en-US')
const days = ref(30)
const loadError = ref(false)
const userActivityReport = ref<unknown>(null)
const problemCompletionReport = ref<unknown>(null)
const contestParticipationReport = ref<unknown>(null)
const performanceReport = ref<unknown>(null)
const loadReport = vi.fn()
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => `${locale.value}:${key}` }),
}))
vi.mock('./composables/useAnalyticsReports', () => ({
  useAnalyticsReports: () => ({
    days,
    loading: false,
    loadError,
    showRefreshSession: false,
    formattedTime: '',
    formattedDate: '',
    userActivityReport,
    problemCompletionReport,
    contestParticipationReport,
    performanceReport,
    loadReport,
  }),
}))

describe('analytics period label', () => {
  it('updates the closed selection when locale or period changes', async () => {
    const wrapper = shallowMount(AnalyticsView, {
      global: { renderStubDefaultSlot: true },
    })
    try {
      const value = wrapper.findComponent({ name: 'SelectValue' })
      expect(value.text()).toBe('en-US:analytics.periods.30days')
      locale.value = 'zh-CN'
      await nextTick()
      expect(value.text()).toBe('zh-CN:analytics.periods.30days')
      for (const [period, key] of [
        [7, '7days'],
        [90, '90days'],
        [365, '1year'],
      ] as const) {
        days.value = period
        await nextTick()
        expect(value.text()).toBe(`zh-CN:analytics.periods.${key}`)
      }
    } finally {
      wrapper.unmount()
    }
  })

  it('shows a retry alert instead of stale reports after a failed load', async () => {
    locale.value = 'en-US'
    days.value = 30
    loadError.value = true
    loadReport.mockClear()
    userActivityReport.value = {
      activeUsersDaily: [],
      userRetention: { day1: 0, day7: 0, day30: 0 },
      peakActiveHours: [],
      topActiveUsers: [],
    }
    problemCompletionReport.value = {
      totalAttempts: 0,
      successfulAttempts: 0,
      overallCompletionRate: 0,
      trendingProblems: [],
      byDifficulty: [],
      hardestProblems: [],
      byTag: [],
    }
    contestParticipationReport.value = {
      totalContests: 0,
      totalParticipants: 0,
      averageParticipantsPerContest: 0,
      virtualParticipation: { total: 0 },
      byType: [],
      topContests: [],
    }
    performanceReport.value = {
      errorRate: 0,
      systemUptime: 0,
      throughput: 0,
      resourceUsage: { memory: 0 },
      slowestEndpoints: [],
    }

    const wrapper = shallowMount(AnalyticsView, {
      global: { renderStubDefaultSlot: true },
    })
    try {
      expect(wrapper.get('[role="alert"]').text()).toContain(
        'en-US:analytics.loadError',
      )
      expect(
        wrapper.findComponent({ name: 'AnalyticsOverviewPanel' }).exists(),
      ).toBe(false)
      const retry = wrapper
        .findAllComponents({ name: 'Button' })
        .find((button) => button.text() === 'en-US:common.retry')
      expect(retry).toBeDefined()
      await retry!.trigger('click')
      expect(loadReport).toHaveBeenCalledOnce()
    } finally {
      wrapper.unmount()
      loadError.value = false
      userActivityReport.value = null
      problemCompletionReport.value = null
      contestParticipationReport.value = null
      performanceReport.value = null
    }
  })
})
