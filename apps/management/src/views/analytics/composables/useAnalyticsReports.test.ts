import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAnalyticsReports } from './useAnalyticsReports'

const mocks = vi.hoisted(() => ({
  authStore: { isAuthenticated: false, hasAnyRole: vi.fn(() => true) },
  analyticsApi: {
    getUserActivity: vi.fn(),
    getProblemCompletion: vi.fn(),
    getContestParticipation: vi.fn(),
    getPerformance: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }))
vi.mock('@/api/admin/analytics', () => ({ analyticsApi: mocks.analyticsApi }))
vi.mock('vue-sonner', () => ({ toast: { error: vi.fn() } }))

beforeEach(() => {
  mocks.authStore.isAuthenticated = false
  mocks.authStore.hasAnyRole.mockReturnValue(true)
  Object.values(mocks.analyticsApi).forEach((mock) => mock.mockReset())
})

describe('analytics percentage formatting', () => {
  it('keeps unavailable metrics distinct from a measured zero', () => {
    let reports!: ReturnType<typeof useAnalyticsReports>
    const wrapper = mount(
      defineComponent({
        setup() {
          reports = useAnalyticsReports()
          return () => null
        },
      }),
      {
        global: {
          plugins: [
            createI18n({
              legacy: false,
              locale: 'en-US',
              messages: { 'en-US': {} },
              missingWarn: false,
              fallbackWarn: false,
            }),
          ],
        },
      },
    )

    for (const value of [null, undefined, NaN, Infinity, -Infinity]) {
      expect(reports.formatPercent(value)).toBe('—')
    }
    expect(reports.formatPercent(0)).toBe('0%')
    expect(reports.formatPercent(25)).toBe('25%')
    expect(reports.formatPercent(1.25)).toBe('1.3%')
    wrapper.unmount()
  })

  it('tracks failed loads and clears the error after a successful retry', async () => {
    const report = {} as never
    mocks.authStore.isAuthenticated = true
    mocks.analyticsApi.getUserActivity.mockResolvedValue(report)
    mocks.analyticsApi.getProblemCompletion.mockResolvedValue(report)
    mocks.analyticsApi.getContestParticipation.mockResolvedValue(report)
    mocks.analyticsApi.getPerformance.mockResolvedValue(report)

    let reports!: ReturnType<typeof useAnalyticsReports>
    const wrapper = mount(
      defineComponent({
        setup() {
          reports = useAnalyticsReports()
          return () => null
        },
      }),
      {
        global: {
          plugins: [
            createI18n({
              legacy: false,
              locale: 'en-US',
              messages: { 'en-US': {} },
              missingWarn: false,
              fallbackWarn: false,
            }),
          ],
        },
      },
    )

    try {
      await flushPromises()
      const previousReport = reports.userActivityReport.value
      mocks.analyticsApi.getUserActivity.mockRejectedValueOnce({ code: 403 })
      await reports.loadReport()
      expect(reports.loadError.value).toBe(true)
      expect(reports.userActivityReport.value).toBe(previousReport)

      const refreshedReport = { source: 'fresh' } as never
      mocks.analyticsApi.getUserActivity.mockResolvedValue(refreshedReport)
      mocks.analyticsApi.getProblemCompletion.mockResolvedValue(refreshedReport)
      mocks.analyticsApi.getContestParticipation.mockResolvedValue(refreshedReport)
      mocks.analyticsApi.getPerformance.mockResolvedValue(refreshedReport)
      await reports.loadReport()
      expect(reports.loadError.value).toBe(false)
      expect(reports.userActivityReport.value).toMatchObject({ source: 'fresh' })
    } finally {
      wrapper.unmount()
    }
  })
})
