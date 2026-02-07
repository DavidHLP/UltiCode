<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
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
import { Badge } from '@/components/ui/badge'
import { IconShieldCheck, IconUsers, IconFileText, IconTrophy, IconFlag } from '@tabler/icons-vue'
import { ChartMetric, ChartPeriod } from '@/api/admin/dashboard'

const { t } = useI18n()
const authStore = useAuthStore()
const dashboardStore = useDashboardStore()
const auditStore = useAuditStore()

const loading = ref(true)

// Computed stats from API data
const stats = computed<StatItem[]>(() => {
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
      href: '/admin/users',
    },
    {
      title: t('dashboard.stats.totalProblems'),
      value: data.problems?.total?.toLocaleString() || '0',
      change: `${data.problems?.published || 0} ${t('dashboard.stats.published')}`,
      trend: 'neutral',
      description: `${data.problems?.unpublished || 0} ${t('dashboard.stats.unpublished')}`,
      icon: IconFileText,
      href: '/admin/problems',
    },
    {
      title: t('dashboard.stats.activeContests'),
      value: data.contests?.running?.toString() || '0',
      change: `${data.contests?.upcoming || 0} ${t('dashboard.stats.upcoming')}`,
      trend: 'neutral',
      description: `${data.contests?.finished || 0} ${t('dashboard.stats.finished')}`,
      icon: IconTrophy,
      href: '/admin/contests',
    },
    {
      title: t('dashboard.stats.flaggedContent'),
      value: flaggedCount.toString(),
      change: flaggedCount > 0 ? t('dashboard.stats.actionNeeded') : t('dashboard.stats.allClear'),
      trend: flaggedCount > 0 ? 'down' : 'neutral',
      description: t('dashboard.stats.pendingModeration'),
      icon: IconFlag,
      href: '/admin/moderation',
    },
  ]
})

const timelineActivities = computed<TimelineActivity[]>(() => {
  return auditStore.logs.slice(0, 5).map((log) => ({
    id: log.id,
    action: log.action,
    user: log.performer?.username || 'System',
    target: log.user?.username || log.entity_type || 'N/A',
    time: formatRelativeTime(log.created_at),
  }))
})

// Transform backend chart data to AreaChart format
const chartData = computed<ChartDataPoint[]>(() => {
  const data = dashboardStore.chartData?.data || []
  // Backend returns Prisma groupBy results: { joined_at: Date, _count: number }
  // AreaChart expects: { date: Date, [key: string]: number }
  return data.map((item) => {
    // Find the date field (joined_at, created_at, published_at, etc.)
    const dateKey = Object.keys(item).find((key) => key.endsWith('_at') || key === 'date')
    const dateValue = dateKey ? (item[dateKey] as string | Date) : new Date()
    // Get the count value
    const countValue = (item._count as number) || 0
    return {
      date: new Date(dateValue),
      users: countValue,
    }
  })
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
  <div class="flex flex-col gap-6 py-6 px-4 lg:px-6 min-h-screen">
    <!-- Header Section -->
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div class="space-y-1">
        <h1 class="text-3xl font-bold tracking-tight">{{ t('dashboard.title') }}</h1>
        <div class="flex items-center gap-2 text-sm">
          <span class="text-muted-foreground">
            {{ t('dashboard.welcome') }},
            <span class="font-medium text-foreground">{{ authStore.userName }}</span>
          </span>
          <Badge
            variant="secondary"
            class="gap-1.5 py-0.5 px-2 text-xs font-medium border-primary/20 bg-primary/5 text-primary"
          >
            <IconShieldCheck class="h-3 w-3" />
            {{ authStore.userRole }}
          </Badge>
        </div>
      </div>

      <div class="flex items-center gap-3 text-sm text-muted-foreground">
        <span>{{ t('dashboard.lastUpdated', { time: t('dashboard.timeAgo.justNow') }) }}</span>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-muted-foreground">{{ t('dashboard.loading') }}</div>
    </div>

    <!-- Bento Grid Layout -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-4 gap-6">
      <!-- Header spans full width -->
      <!-- (Already handled by flex container above) -->

      <!-- Stat Cards - each takes 1 column on large screens -->
      <div class="col-span-full">
        <StatCards :stats="stats" />
      </div>

      <!-- Chart - takes 3 columns (66%) -->
      <div class="lg:col-span-3">
        <AreaChart
          :title="t('dashboard.chart.userRegistrationTrend')"
          :description="t('dashboard.chart.dailyRegistrations')"
          :data="chartData"
          :series-keys="['users']"
          :config="{ users: { label: 'Users', color: 'var(--primary)' } }"
        />
      </div>

      <!-- Action Center - takes 1 column (33%) -->
      <div class="lg:col-span-1">
        <DashboardTimeline :activities="timelineActivities" />
      </div>

      <!-- Timeline could also go here as full width if needed -->
    </div>
  </div>
</template>
