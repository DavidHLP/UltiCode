<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import type { ApiError } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { IconRefresh } from '@tabler/icons-vue'
import {
  analyticsApi,
  type UserActivityReport,
  type ProblemCompletionReport,
  type ContestParticipationReport,
  type RevenueReport,
  type PerformanceReport,
} from '@/api/admin/analytics'
import {
  AnalyticsNav,
  AnalyticsMetricCard,
  AnalyticsBarList,
  AnalyticsTagCloud,
  AnalyticsHeatmap,
} from '@/components/analytics'
import type { MetricData } from '@/components/analytics'
import type { BarListItem } from '@/components/analytics'
import type { TagItem } from '@/components/analytics'
import type { HeatmapCell, HeatmapRow, HeatmapColumn } from '@/components/analytics'
import AreaChart from '@/components/dashboard/AreaChart.vue'
import type { ChartDataPoint } from '@/components/dashboard/AreaChart.vue'

const { t } = useI18n()
const authStore = useAuthStore()

type ReportTab =
  | 'user_activity'
  | 'problem_completion'
  | 'contest_participation'
  | 'revenue'
  | 'performance'

const activeTab = ref<ReportTab>('user_activity')
const loading = ref(false)
const days = ref(30)
const showRefreshSession = ref(false)

const userActivityReport = ref<UserActivityReport | null>(null)
const problemCompletionReport = ref<ProblemCompletionReport | null>(null)
const contestParticipationReport = ref<ContestParticipationReport | null>(null)
const revenueReport = ref<RevenueReport | null>(null)
const performanceReport = ref<PerformanceReport | null>(null)

// Current time display
const currentTime = ref(new Date())
const formattedTime = computed(() => {
  return currentTime.value.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
})

const formattedDate = computed(() => {
  return currentTime.value.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  })
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
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toFixed(0)
}

function formatPercent(num: number): string {
  return num.toFixed(1) + '%'
}

function formatCurrency(num: number): string {
  return '$' + num.toFixed(2)
}

function formatUptime(seconds: number): string {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  return `${d}d ${h}h`
}

// User Activity computed data
const userActivityMetrics = computed<MetricData[]>(() => {
  if (!userActivityReport.value) return []
  const report = userActivityReport.value
  return [
    {
      title: t('analytics.userActivity.dailyActiveUsers'),
      value: formatNumber(report.activeUsersDaily.slice(-1)[0]?.count || 0),
      trend: 'up',
      change: '+' + (report.activeUsersDaily.slice(-1)[0]?.count || 0),
    },
    {
      title: t('analytics.userActivity.retention1d'),
      value: formatPercent(report.userRetention.day1),
      trend: report.userRetention.day1 > 50 ? 'up' : 'down',
      change:
        report.userRetention.day1 > 50
          ? t('analytics.status.good')
          : t('analytics.status.needsWork'),
    },
    {
      title: t('analytics.userActivity.retention7d'),
      value: formatPercent(report.userRetention.day7),
      trend: report.userRetention.day7 > 30 ? 'up' : 'neutral',
      change:
        report.userRetention.day7 > 30 ? t('analytics.status.good') : t('analytics.status.average'),
    },
    {
      title: t('analytics.userActivity.retention30d'),
      value: formatPercent(report.userRetention.day30),
      trend: report.userRetention.day30 > 10 ? 'up' : 'neutral',
      change:
        report.userRetention.day30 > 10
          ? t('analytics.status.good')
          : t('analytics.status.average'),
    },
  ]
})

const userActivityChartData = computed<ChartDataPoint[]>(() => {
  if (!userActivityReport.value) return []
  return userActivityReport.value.activeUsersDaily.map((item) => ({
    date: new Date(item.date),
    users: item.count,
  }))
})

const userActivityBarItems = computed<BarListItem[]>(() => {
  if (!userActivityReport.value) return []
  return userActivityReport.value.topActiveUsers.map((user) => ({
    id: user.userId,
    label: user.username,
    value: user.loginCount,
    subtitle: t('analytics.userActivity.logins', { count: user.loginCount }),
  }))
})

const userActivityHeatmapData = computed(() => {
  if (!userActivityReport.value) return { cells: [], rows: [], columns: [] }
  const hours = userActivityReport.value.peakActiveHours
  const cells: HeatmapCell[] = hours.map((h) => ({
    x: h.hour % 6,
    y: Math.floor(h.hour / 6),
    value: h.count,
    label: `${h.hour}:00`,
  }))
  const rows: HeatmapRow[] = [0, 1, 2, 3].map((r) => ({
    label: `${r * 6}:00-${(r + 1) * 6}:00`,
  }))
  const columns: HeatmapColumn[] = [0, 1, 2, 3, 4, 5].map((c) => ({
    label: `${c}`,
  }))
  return { cells, rows, columns }
})

// Problem Completion computed data
const problemCompletionMetrics = computed<MetricData[]>(() => {
  if (!problemCompletionReport.value) return []
  const report = problemCompletionReport.value
  return [
    {
      title: t('analytics.problemCompletion.totalAttempts'),
      value: formatNumber(report.totalAttempts),
      trend: 'neutral',
    },
    {
      title: t('analytics.problemCompletion.successfulAttempts'),
      value: formatNumber(report.successfulAttempts),
      trend: 'up',
      change: '+' + formatNumber(report.successfulAttempts),
    },
    {
      title: t('analytics.problemCompletion.completionRate'),
      value: formatPercent(report.overallCompletionRate),
      trend: report.overallCompletionRate > 30 ? 'up' : 'down',
      change:
        report.overallCompletionRate > 30
          ? t('analytics.status.good')
          : t('analytics.status.needsWork'),
    },
    {
      title: t('analytics.problemCompletion.trendingProblems'),
      value: report.trendingProblems.length,
      trend: 'neutral',
    },
  ]
})

const problemDifficultyBarItems = computed<BarListItem[]>(() => {
  if (!problemCompletionReport.value) return []
  const difficultyColors: Record<string, string> = {
    EASY: 'var(--status-success)',
    MEDIUM: 'var(--status-warning)',
    HARD: 'var(--status-error)',
  }
  return problemCompletionReport.value.byDifficulty.map((item) => ({
    id: item.difficulty,
    label: item.difficulty,
    value: item.rate,
    color: difficultyColors[item.difficulty] || 'var(--accent-primary)',
    subtitle: `${item.completed}/${item.total} ${t('analytics.problemCompletion.completed')}`,
  }))
})

const problemHardestBarItems = computed<BarListItem[]>(() => {
  if (!problemCompletionReport.value) return []
  return problemCompletionReport.value.hardestProblems.map((problem) => ({
    id: problem.problemId,
    label: problem.title,
    value: problem.completionRate,
    color: 'var(--status-error)',
    subtitle: problem.difficulty,
  }))
})

const problemTagItems = computed<TagItem[]>(() => {
  if (!problemCompletionReport.value) return []
  return problemCompletionReport.value.byTag.map((tag) => ({
    id: tag.tagId,
    label: tag.label,
    value: tag.rate,
    count: tag.total,
  }))
})

// Contest Participation computed data
const contestParticipationMetrics = computed<MetricData[]>(() => {
  if (!contestParticipationReport.value) return []
  const report = contestParticipationReport.value
  return [
    {
      title: t('analytics.contestParticipation.totalContests'),
      value: report.totalContests,
      trend: 'neutral',
    },
    {
      title: t('analytics.contestParticipation.totalParticipants'),
      value: formatNumber(report.totalParticipants),
      trend: 'up',
      change: '+' + formatNumber(report.totalParticipants),
    },
    {
      title: t('analytics.contestParticipation.avgParticipants'),
      value: report.averageParticipantsPerContest.toFixed(1),
      trend: 'neutral',
      suffix: t('analytics.perContest'),
    },
    {
      title: t('analytics.contestParticipation.virtualParticipation'),
      value: report.virtualParticipation.total,
      trend: 'neutral',
    },
  ]
})

const contestTypeBarItems = computed<BarListItem[]>(() => {
  if (!contestParticipationReport.value) return []
  return contestParticipationReport.value.byType.map((item) => ({
    id: item.type,
    label: item.type,
    value: item.avgParticipants,
    subtitle: `${item.count} ${t('analytics.contestParticipation.contests')}`,
  }))
})

const contestTopBarItems = computed<BarListItem[]>(() => {
  if (!contestParticipationReport.value) return []
  return contestParticipationReport.value.topContests.map((contest) => ({
    id: contest.contestId,
    label: contest.title,
    value: contest.participants,
    subtitle: `${contest.participants} ${t('analytics.contestParticipants')}`,
  }))
})

// Revenue computed data
const revenueMetrics = computed<MetricData[]>(() => {
  if (!revenueReport.value) return []
  const report = revenueReport.value
  return [
    {
      title: t('analytics.revenue.mrr'),
      value: formatCurrency(report.mrr),
      trend: 'up',
      prefix: '$',
    },
    {
      title: t('analytics.revenue.arr'),
      value: formatCurrency(report.arr),
      trend: 'up',
      prefix: '$',
    },
    {
      title: t('analytics.revenue.subscribers'),
      value: report.subscriberCount,
      trend: 'neutral',
    },
    {
      title: t('analytics.revenue.conversionRate'),
      value: formatPercent(report.conversionRate),
      trend: report.conversionRate > 5 ? 'up' : 'neutral',
      change:
        report.conversionRate > 5 ? t('analytics.status.good') : t('analytics.status.average'),
    },
  ]
})

const revenuePlanBarItems = computed<BarListItem[]>(() => {
  if (!revenueReport.value) return []
  return revenueReport.value.byPlan.map((item) => ({
    id: item.plan,
    label: item.plan,
    value: item.revenue,
    subtitle: `${item.subscribers} ${t('analytics.revenue.subscribers')}`,
  }))
})

// Performance computed data
const performanceMetrics = computed<MetricData[]>(() => {
  if (!performanceReport.value) return []
  const report = performanceReport.value
  return [
    {
      title: t('analytics.performance.uptime'),
      value: formatUptime(report.systemUptime),
      trend: report.systemUptime > 86400 * 7 ? 'up' : 'neutral',
      change:
        report.systemUptime > 86400 * 7
          ? t('analytics.status.excellent')
          : t('analytics.status.good'),
    },
    {
      title: t('analytics.performance.throughput'),
      value: formatNumber(report.throughput),
      trend: 'neutral',
      suffix: '/24h',
    },
    {
      title: t('analytics.performance.errorRate'),
      value: formatPercent(report.errorRate),
      trend: report.errorRate > 1 ? 'down' : 'up',
      change:
        report.errorRate > 1
          ? t('analytics.status.needsAttention')
          : t('analytics.status.excellent'),
    },
    {
      title: t('analytics.performance.memoryUsage'),
      value: formatPercent(report.resourceUsage.memory),
      trend: report.resourceUsage.memory > 80 ? 'down' : 'neutral',
      change:
        report.resourceUsage.memory > 80
          ? t('analytics.status.high')
          : t('analytics.status.normal'),
    },
  ]
})

const performanceEndpointBarItems = computed<BarListItem[]>(() => {
  if (!performanceReport.value) return []
  return performanceReport.value.slowestEndpoints.map((endpoint) => ({
    id: endpoint.endpoint,
    label: endpoint.endpoint,
    value: endpoint.averageTime,
    subtitle: `${endpoint.requestCount} ${t('analytics.performance.requests')}`,
  }))
})

async function loadReport() {
  // Debug: Log auth state before role check
  if (import.meta.env.DEV) {
    console.log({
      isAuthenticated: authStore.isAuthenticated,
      userRole: authStore.userRole,
      user: authStore.user,
      hasAdminRole: authStore.hasRole('ADMIN'),
      hasSuperAdminRole: authStore.hasRole('SUPER_ADMIN'),
      hasAnyAdminRole: authStore.hasAnyRole(['ADMIN', 'SUPER_ADMIN']),
    })
  }

  // Check if user has required role before making API request
  if (!authStore.isAuthenticated) {
    toast.error(t('analytics.authRequired'))
    return
  }
  if (!authStore.hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    const currentRole = authStore.userRole || 'none'
    toast.error(t('analytics.adminRequiredWithRole', { role: currentRole }))
    // Show refresh session button
    showRefreshSession.value = true
    return
  }

  // Hide refresh session button if we passed the role check
  showRefreshSession.value = false

  // Only set loading to true after passing all checks
  loading.value = true
  try {
    switch (activeTab.value) {
      case 'user_activity':
        userActivityReport.value = await analyticsApi.getUserActivity({ days: days.value })
        break
      case 'problem_completion':
        problemCompletionReport.value = await analyticsApi.getProblemCompletion({
          days: days.value,
        })
        break
      case 'contest_participation':
        contestParticipationReport.value = await analyticsApi.getContestParticipation({
          days: days.value,
        })
        break
      case 'revenue':
        revenueReport.value = await analyticsApi.getRevenue({ days: days.value })
        break
      case 'performance':
        performanceReport.value = await analyticsApi.getPerformance()
        break
    }
  } catch (error) {
    console.error('Failed to load report:', error)
    // Skip toast for 401 errors - request interceptor already handles redirect to login
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
    // Re-fetch user data from backend
    await authStore.fetchUser()
    // Reload the report after session refresh
    await loadReport()
    toast.success(t('analytics.sessionRefreshed'))
  } catch (error) {
    console.error('Failed to refresh session:', error)
    toast.error(t('analytics.sessionRefreshFailed'))
  } finally {
    loading.value = false
  }
}

watch([activeTab, days], () => {
  loadReport()
})

onMounted(() => {
  loadReport()
})
</script>

<template>
  <div class="flex flex-col gap-6 py-6 px-4 lg:px-8 min-h-full bg-background">
    <!-- Precision Header -->
    <header
      class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between pb-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]"
    >
      <div class="space-y-1">
        <h1 class="text-2xl font-medium tracking-tight text-foreground">
          {{ t('analytics.title') }}
        </h1>
        <p class="text-sm text-[var(--silver-500)]">
          {{ t('analytics.description') }}
        </p>
      </div>

      <!-- Time display and controls -->
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="text-[var(--silver-400)]">{{ formattedDate }}</span>
          <span class="text-lg font-data tabular-nums text-foreground">{{ formattedTime }}</span>
        </div>

        <Select v-model="days">
          <SelectTrigger
            class="w-[130px] h-8 text-xs border-[var(--silver-200)] dark:border-[var(--silver-300)]"
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="7">{{ t('analytics.periods.7days') }}</SelectItem>
            <SelectItem value="30">{{ t('analytics.periods.30days') }}</SelectItem>
            <SelectItem value="90">{{ t('analytics.periods.90days') }}</SelectItem>
            <SelectItem value="365">{{ t('analytics.periods.1year') }}</SelectItem>
          </SelectContent>
        </Select>

        <Button
          variant="outline"
          size="sm"
          @click="loadReport"
          :disabled="loading"
          class="h-8 border-[var(--silver-200)] dark:border-[var(--silver-300)]"
        >
          <IconRefresh class="h-3.5 w-3.5 mr-1" :class="{ 'animate-spin': loading }" />
          {{ t('common.refresh') }}
        </Button>
      </div>
    </header>

    <!-- Main Layout: Sidebar + Content -->
    <div class="flex flex-col lg:flex-row gap-6">
      <!-- Sidebar Navigation -->
      <aside class="lg:w-56 shrink-0">
        <div class="sticky top-6">
          <AnalyticsNav v-model:active-item="activeTab" />
        </div>
      </aside>

      <!-- Content Area -->
      <main class="flex-1 min-w-0">
        <!-- Loading State -->
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="flex items-center gap-3 text-[var(--silver-400)]">
            <div
              class="h-4 w-4 border-2 border-[var(--silver-300)] border-t-foreground rounded-full animate-spin"
            ></div>
            <span>{{ t('common.loading') }}</span>
          </div>
        </div>

        <!-- Permission Denied - Show Refresh Session Button -->
        <div v-else-if="showRefreshSession" class="flex items-center justify-center py-12">
          <div class="text-center space-y-4">
            <p class="text-[var(--silver-400)]">{{ t('analytics.permissionDenied') }}</p>
            <Button variant="outline" size="sm" @click="refreshSession" :disabled="loading">
              <IconRefresh class="h-4 w-4 mr-2" :class="{ 'animate-spin': loading }" />
              {{ t('analytics.refreshSession') }}
            </Button>
          </div>
        </div>

        <!-- User Activity Report -->
        <div v-else-if="activeTab === 'user_activity' && userActivityReport" class="space-y-5">
          <!-- Metric Cards -->
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            <AnalyticsMetricCard
              v-for="(metric, index) in userActivityMetrics"
              :key="index"
              :metric="metric"
            />
          </div>

          <!-- Charts Row -->
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
            <!-- Main Chart -->
            <div class="lg:col-span-2">
              <AreaChart
                :title="t('analytics.userActivity.activeUsersTrend')"
                :description="t('analytics.userActivity.activeUsersTrendDesc')"
                :data="userActivityChartData"
                :series-keys="['users']"
                :config="{
                  users: {
                    label: t('analytics.userActivity.activeUsers'),
                    color: 'var(--accent-primary)',
                  },
                }"
              />
            </div>

            <!-- Heatmap -->
            <div class="lg:col-span-1">
              <AnalyticsHeatmap
                :title="t('analytics.userActivity.peakHours')"
                :description="t('analytics.userActivity.peakHoursDesc')"
                :data="userActivityHeatmapData.cells"
                :rows="userActivityHeatmapData.rows"
                :columns="userActivityHeatmapData.columns"
                :cell-size="24"
              />
            </div>
          </div>

          <!-- Top Users List -->
          <AnalyticsBarList
            :title="t('analytics.userActivity.topUsers')"
            :description="t('analytics.userActivity.topUsersDesc')"
            :items="userActivityBarItems"
            :limit="10"
          />
        </div>

        <!-- Problem Completion Report -->
        <div
          v-else-if="activeTab === 'problem_completion' && problemCompletionReport"
          class="space-y-5"
        >
          <!-- Metric Cards -->
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            <AnalyticsMetricCard
              v-for="(metric, index) in problemCompletionMetrics"
              :key="index"
              :metric="metric"
            />
          </div>

          <!-- Difficulty & Hardest Problems -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <AnalyticsBarList
              :title="t('analytics.problemCompletion.byDifficulty')"
              :description="t('analytics.problemCompletion.byDifficultyDesc')"
              :items="problemDifficultyBarItems"
              :show-percentage="true"
              :limit="5"
            />
            <AnalyticsBarList
              :title="t('analytics.problemCompletion.hardestProblems')"
              :description="t('analytics.problemCompletion.hardestProblemsDesc')"
              :items="problemHardestBarItems"
              :show-percentage="true"
              :limit="5"
            />
          </div>

          <!-- Tag Cloud -->
          <AnalyticsTagCloud
            :title="t('analytics.problemCompletion.topTags')"
            :description="t('analytics.problemCompletion.topTagsDesc')"
            :tags="problemTagItems"
            :value-format="'percent'"
            :limit="20"
          />
        </div>

        <!-- Contest Participation Report -->
        <div
          v-else-if="activeTab === 'contest_participation' && contestParticipationReport"
          class="space-y-5"
        >
          <!-- Metric Cards -->
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            <AnalyticsMetricCard
              v-for="(metric, index) in contestParticipationMetrics"
              :key="index"
              :metric="metric"
            />
          </div>

          <!-- Type & Top Contests -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <AnalyticsBarList
              :title="t('analytics.contestParticipation.byType')"
              :description="t('analytics.contestParticipation.byTypeDesc')"
              :items="contestTypeBarItems"
              :limit="10"
            />
            <AnalyticsBarList
              :title="t('analytics.contestParticipation.topContests')"
              :description="t('analytics.contestParticipation.topContestsDesc')"
              :items="contestTopBarItems"
              :limit="10"
            />
          </div>
        </div>

        <!-- Revenue Report -->
        <div v-else-if="activeTab === 'revenue' && revenueReport" class="space-y-5">
          <!-- Metric Cards -->
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            <AnalyticsMetricCard
              v-for="(metric, index) in revenueMetrics"
              :key="index"
              :metric="metric"
            />
          </div>

          <!-- Plan Distribution -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <AnalyticsBarList
              :title="t('analytics.revenue.byPlan')"
              :description="t('analytics.revenue.byPlanDesc')"
              :items="revenuePlanBarItems"
              :limit="10"
            />

            <!-- Key Metrics Card -->
            <div
              class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded-lg bg-card shadow-float p-5"
            >
              <h3 class="text-base font-medium tracking-tight mb-4">
                {{ t('analytics.revenue.metrics') }}
              </h3>
              <div class="space-y-4">
                <div
                  class="flex items-center justify-between py-2 border-b border-[var(--silver-100)] dark:border-[var(--silver-800)]"
                >
                  <span class="text-sm text-[var(--silver-500)]">{{
                    t('analytics.revenue.arpu')
                  }}</span>
                  <span class="font-data tabular-nums font-medium">{{
                    formatCurrency(revenueReport.arpu)
                  }}</span>
                </div>
                <div
                  class="flex items-center justify-between py-2 border-b border-[var(--silver-100)] dark:border-[var(--silver-800)]"
                >
                  <span class="text-sm text-[var(--silver-500)]">{{
                    t('analytics.revenue.churnRate')
                  }}</span>
                  <span
                    class="font-data tabular-nums font-medium"
                    :class="
                      revenueReport.churnRate > 5
                        ? 'text-[var(--status-error)]'
                        : 'text-[var(--status-success)]'
                    "
                  >
                    {{ formatPercent(revenueReport.churnRate) }}
                  </span>
                </div>
                <div class="flex items-center justify-between py-2">
                  <span class="text-sm text-[var(--silver-500)]">{{
                    t('analytics.revenue.totalRevenue')
                  }}</span>
                  <span class="font-data tabular-nums font-medium">{{
                    formatCurrency(revenueReport.totalRevenue)
                  }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Performance Report -->
        <div v-else-if="activeTab === 'performance' && performanceReport" class="space-y-5">
          <!-- Metric Cards -->
          <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            <AnalyticsMetricCard
              v-for="(metric, index) in performanceMetrics"
              :key="index"
              :metric="metric"
            />
          </div>

          <!-- Resource Usage -->
          <div
            class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded-lg bg-card shadow-float p-5"
          >
            <h3 class="text-base font-medium tracking-tight mb-4">
              {{ t('analytics.performance.resourceUsage') }}
            </h3>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
              <!-- CPU -->
              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-[var(--silver-500)]">{{
                    t('analytics.performance.cpu')
                  }}</span>
                  <span class="font-data tabular-nums font-medium">{{
                    formatPercent(performanceReport.resourceUsage.cpu)
                  }}</span>
                </div>
                <div
                  class="h-2 bg-[var(--silver-100)] dark:bg-[var(--silver-800)] rounded-full overflow-hidden"
                >
                  <div
                    class="h-full rounded-full transition-all duration-500"
                    :class="
                      performanceReport.resourceUsage.cpu > 80
                        ? 'bg-[var(--status-error)]'
                        : performanceReport.resourceUsage.cpu > 60
                          ? 'bg-[var(--status-warning)]'
                          : 'bg-[var(--accent-primary)]'
                    "
                    :style="{ width: performanceReport.resourceUsage.cpu + '%' }"
                  />
                </div>
              </div>

              <!-- Memory -->
              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-[var(--silver-500)]">{{
                    t('analytics.performance.memory')
                  }}</span>
                  <span class="font-data tabular-nums font-medium">{{
                    formatPercent(performanceReport.resourceUsage.memory)
                  }}</span>
                </div>
                <div
                  class="h-2 bg-[var(--silver-100)] dark:bg-[var(--silver-800)] rounded-full overflow-hidden"
                >
                  <div
                    class="h-full rounded-full transition-all duration-500"
                    :class="
                      performanceReport.resourceUsage.memory > 80
                        ? 'bg-[var(--status-error)]'
                        : performanceReport.resourceUsage.memory > 60
                          ? 'bg-[var(--status-warning)]'
                          : 'bg-[var(--status-success)]'
                    "
                    :style="{ width: performanceReport.resourceUsage.memory + '%' }"
                  />
                </div>
              </div>

              <!-- Disk -->
              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-[var(--silver-500)]">{{
                    t('analytics.performance.disk')
                  }}</span>
                  <span class="font-data tabular-nums font-medium">{{
                    formatPercent(performanceReport.resourceUsage.disk)
                  }}</span>
                </div>
                <div
                  class="h-2 bg-[var(--silver-100)] dark:bg-[var(--silver-800)] rounded-full overflow-hidden"
                >
                  <div
                    class="h-full rounded-full transition-all duration-500"
                    :class="
                      performanceReport.resourceUsage.disk > 80
                        ? 'bg-[var(--status-error)]'
                        : performanceReport.resourceUsage.disk > 60
                          ? 'bg-[var(--status-warning)]'
                          : 'bg-[var(--status-warning)]'
                    "
                    :style="{ width: performanceReport.resourceUsage.disk + '%' }"
                  />
                </div>
              </div>
            </div>
          </div>

          <!-- Slowest Endpoints -->
          <AnalyticsBarList
            :title="t('analytics.performance.slowestEndpoints')"
            :description="t('analytics.performance.slowestEndpointsDesc')"
            :items="performanceEndpointBarItems"
            :limit="10"
          />
        </div>

        <!-- No Data State -->
        <div v-else-if="!loading" class="flex items-center justify-center py-12">
          <div class="text-center">
            <p class="text-[var(--silver-400)]">{{ t('analytics.noData') }}</p>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>
