<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useDashboardStore } from '@/stores/admin/dashboard'
import { useAuditStore } from '@/stores/admin/audit'
import StatCards, { type StatItem } from '@/components/dashboard/StatCards.vue'
import AreaChart from '@/components/dashboard/AreaChart.vue'
import DashboardTimeline, {
  type TimelineActivity,
} from '@/components/dashboard/DashboardTimeline.vue'
import type { ChartDataPoint } from '@/components/dashboard/AreaChart.vue'
import { IconUsers, IconFileText, IconTrophy, IconFlag } from '@tabler/icons-vue'
import { ChartMetric, ChartPeriod } from '@/api/admin/dashboard'
import { formatTime24, formatWeekdayShortDate } from '@/lib/format/date'

const { t } = useI18n()
const authStore = useAuthStore()
const dashboardStore = useDashboardStore()
const auditStore = useAuditStore()

const loading = ref(true)

// Current time display
const currentTime = ref(new Date())
const formattedTime = computed(() => formatTime24(currentTime.value))

const formattedDate = computed(() => formatWeekdayShortDate(currentTime.value))

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

// Computed stats from API data
const stats = computed<StatItem[]>(() => {
  // Note: request.ts unwraps Result<T>, so stats is directly DashboardStatsVO
  const data = dashboardStore.stats
  if (!data) return []

  // Calculate flagged content count
  const flaggedCount =
    (data.solutions?.flagged || 0) +
    (data.forum?.flaggedPosts || 0) +
    (data.forum?.flaggedComments || 0)

  return [
    {
      title: t('dashboard.stats.totalUsers'),
      value: data.users?.total?.toLocaleString() || '0',
      change: data.users?.activeToday ? `+${data.users.activeToday}` : '+0',
      trend: data.users?.activeToday > 0 ? 'up' : 'neutral',
      description: `${data.users?.activeWeek || 0} ${t('dashboard.stats.activeThisWeek')}`,
      icon: IconUsers,
      href: '/users',
    },
    {
      title: t('dashboard.stats.totalProblems'),
      value: data.problems?.total?.toLocaleString() || '0',
      change: `${data.problems?.published || 0} ${t('dashboard.stats.published')}`,
      trend: 'neutral',
      description: `${data.problems?.unpublished || 0} ${t('dashboard.stats.unpublished')}`,
      icon: IconFileText,
      href: '/problems',
    },
    {
      title: t('dashboard.stats.activeContests'),
      value: data.contests?.running?.toString() || '0',
      change: `${data.contests?.upcoming || 0} ${t('dashboard.stats.upcoming')}`,
      trend: 'neutral',
      description: `${data.contests?.finished || 0} ${t('dashboard.stats.finished')}`,
      icon: IconTrophy,
      href: '/contests',
    },
    {
      title: t('dashboard.stats.flaggedContent'),
      value: flaggedCount.toString(),
      change: flaggedCount > 0 ? t('dashboard.stats.actionNeeded') : t('dashboard.stats.allClear'),
      trend: flaggedCount > 0 ? 'down' : 'neutral',
      description: t('dashboard.stats.pendingModeration'),
      icon: IconFlag,
      href: '/moderation',
    },
  ]
})

const timelineActivities = computed<TimelineActivity[]>(() => {
  return (auditStore.logs || []).slice(0, 5).map((log) => ({
    id: log.id,
    action: log.action,
    user: log.performer?.username || 'System',
    target: log.user?.username || log.entityType || 'N/A',
    time: formatRelativeTime(log.createdAt),
  }))
})

// Transform backend chart data to AreaChart format
// Backend now returns ChartDataPoint[] with { date, count } format
const chartData = computed<ChartDataPoint[]>(() => {
  const rawData = dashboardStore.chartData?.data || []
  return rawData.map((item) => ({
    date: new Date(item.date),
    users: item.count,
  }))
})

function formatRelativeTime(date: Date | string): string {
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return t('dashboard.timeAgo.justNow')
  if (minutes < 60) {
    return t('dashboard.timeAgo.minuteAgo', { count: minutes })
  }
  if (hours < 24) {
    return t('dashboard.timeAgo.hourAgo', { count: hours })
  }
  return t('dashboard.timeAgo.dayAgo', { count: days })
}

async function loadData() {
  loading.value = true
  try {
    await Promise.all([
      dashboardStore.fetchStats(),
      dashboardStore.fetchChartStats({
        metric: ChartMetric.USERS,
        period: ChartPeriod.DAY,
        days: 90,
      }),
      auditStore.fetchLogs({ limit: 10 }),
    ])
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())
</script>

<template>
  <div class="flex flex-col gap-6 py-6 px-4 lg:px-8 bg-background">
    <!-- Precision Header -->
    <header
      class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between pb-4 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]"
    >
      <div class="space-y-1">
        <h1 class="text-2xl font-medium tracking-tight text-foreground">
          {{ t('dashboard.title') }}
        </h1>
        <div class="flex items-center gap-3 text-sm">
          <span class="text-[var(--foreground-muted)]">
            {{ t('dashboard.welcome') }},
            <span class="font-medium text-foreground">{{ authStore.userName }}</span>
          </span>
          <!-- Role badge with silver border -->
          <span
            class="inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-medium rounded-none border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)]"
          >
            {{
              authStore.userRole
                ? t(`users.filters.role.${authStore.userRole}`, authStore.userRole)
                : ''
            }}
          </span>
        </div>
      </div>

      <!-- Time display with monospace font -->
      <div class="flex items-center gap-4 text-sm">
        <div class="flex items-center gap-2">
          <span class="text-[var(--foreground-muted)]">{{ formattedDate }}</span>
          <span class="text-lg font-data tabular-nums text-foreground">{{ formattedTime }}</span>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="flex items-center gap-3 text-[var(--foreground-muted)]">
        <div
          class="h-4 w-4 border-2 border-[var(--border-subtle)] border-t-foreground rounded-full animate-spin"
        ></div>
        <span>{{ t('dashboard.loading') }}</span>
      </div>
    </div>

    <!-- Bento Grid Layout -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-4 gap-5">
      <!-- Stat Cards Row - Full width -->
      <div class="col-span-full">
        <StatCards :stats="stats" />
      </div>

      <!-- Main Chart - 3 columns (75%) -->
      <div class="lg:col-span-3">
        <AreaChart
          :title="t('dashboard.chart.userRegistrationTrend')"
          :description="t('dashboard.chart.dailyRegistrations')"
          :data="chartData"
          :series-keys="['users']"
          :config="{ users: { label: 'Users', color: 'var(--accent-primary)' } }"
        />
      </div>

      <!-- Activity Timeline - 1 column (25%) -->
      <div class="lg:col-span-1">
        <DashboardTimeline :activities="timelineActivities" />
      </div>
    </div>
  </div>
</template>
