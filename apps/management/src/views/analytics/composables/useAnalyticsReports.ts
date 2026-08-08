import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@/utils/request'
import { formatNumberByLocale, formatCompactNumber } from '@/i18n/utils'
import { formatAnalyticsDate, formatAnalyticsTime } from './analyticsDateTime'
import {
  analyticsApi,
  type UserActivityReport,
  type ProblemCompletionReport,
  type ContestParticipationReport,
  type PerformanceReport,
} from '@/api/admin/analytics'

export function useAnalyticsReports() {
  const { t, locale } = useI18n()
  const authStore = useAuthStore()

  const loading = ref(false)
  const days = ref(30)
  const showRefreshSession = ref(false)

  const userActivityReport = ref<UserActivityReport | null>(null)
  const problemCompletionReport = ref<ProblemCompletionReport | null>(null)
  const contestParticipationReport = ref<ContestParticipationReport | null>(null)
  const performanceReport = ref<PerformanceReport | null>(null)

  // Current time display
  const currentTime = ref(new Date())
  const formattedTime = computed(() => {
    return formatAnalyticsTime(currentTime.value, locale.value)
  })

  const formattedDate = computed(() => {
    return formatAnalyticsDate(currentTime.value, locale.value)
  })

  // Update time every minute
  let timeInterval: ReturnType<typeof setInterval>
  onMounted(() => {
    timeInterval = setInterval(() => {
      currentTime.value = new Date()
    }, 60000)
  })
  onUnmounted(() => {
    clearInterval(timeInterval)
  })

  // Format utilities
  function formatNumber(num: number): string {
    return formatCompactNumber(num)
  }

  function formatPercent(num: number): string {
    return formatNumberByLocale(num / 100, { style: 'percent', maximumFractionDigits: 1 })
  }

  function formatUptime(seconds: number): string {
    const d = Math.floor(seconds / 86400)
    const h = Math.floor((seconds % 86400) / 3600)
    return `${d}d ${h}h`
  }

  async function loadReport() {
    if (!authStore.isAuthenticated) {
      toast.error(t('analytics.authRequired'))
      return
    }
    if (!authStore.hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
      const currentRole = authStore.userRole || 'none'
      toast.error(t('analytics.adminRequiredWithRole', { role: currentRole }))
      showRefreshSession.value = true
      return
    }

    showRefreshSession.value = false
    loading.value = true
    try {
      const [userAct, probComp, contestPart, perf] = await Promise.all([
        analyticsApi.getUserActivity({ days: days.value }),
        analyticsApi.getProblemCompletion({ days: days.value }),
        analyticsApi.getContestParticipation({ days: days.value }),
        analyticsApi.getPerformance(),
      ])
      userActivityReport.value = userAct
      problemCompletionReport.value = probComp
      contestParticipationReport.value = contestPart
      performanceReport.value = perf
    } catch (error) {
      console.error('Failed to load report:', error)
      const apiError = error as ApiError
      if (apiError.code !== 401) {
        toast.error(t('analytics.loadError'))
      }
    } finally {
      loading.value = false
    }
  }

  async function refreshSession() {
    showRefreshSession.value = false
    loading.value = true
    try {
      await authStore.fetchUser()
      await loadReport()
      toast.success(t('analytics.sessionRefreshed'))
    } catch (error) {
      console.error('Failed to refresh session:', error)
      toast.error(t('analytics.sessionRefreshFailed'))
    } finally {
      loading.value = false
    }
  }

  watch(days, () => {
    loadReport()
  })

  onMounted(() => {
    loadReport()
  })

  return {
    loading,
    days,
    showRefreshSession,
    formattedTime,
    formattedDate,
    formatNumber,
    formatPercent,
    formatUptime,
    userActivityReport,
    problemCompletionReport,
    contestParticipationReport,
    performanceReport,
    loadReport,
    refreshSession,
  }
}
