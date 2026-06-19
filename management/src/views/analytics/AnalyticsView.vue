<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { IconRefresh } from '@tabler/icons-vue'
import { useAnalyticsReports } from './composables/useAnalyticsReports'
import {
  AnalyticsHeatmap,
  AnalyticsTagCloud,
  AnalyticsBarList,
  AnalyticsTopUsersChart,
  AnalyticsOverviewPanel,
  AnalyticsResourceUsage,
} from '@/components/analytics'
import AreaChart from '@/components/dashboard/AreaChart.vue'
import type { TimePeriod } from '@/components/dashboard/areaChartData'
import { IconUsers, IconFileText, IconTrophy, IconServer } from '@tabler/icons-vue'

const { t } = useI18n()

const {
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
} = useAnalyticsReports()

// --- Computed properties for layout ---

const chartTimePeriod = computed<TimePeriod>(() => {
  if (days.value >= 365) return 'all'
  if (days.value >= 90) return '90d'
  if (days.value >= 30) return '30d'
  return '7d'
})

const metricGroups = computed(() => {
  if (
    !userActivityReport.value ||
    !problemCompletionReport.value ||
    !contestParticipationReport.value ||
    !performanceReport.value
  ) {
    return []
  }

  const u = userActivityReport.value
  const p = problemCompletionReport.value
  const c = contestParticipationReport.value
  const s = performanceReport.value

  const getTrendStyle = (trend?: string): Record<string, string> => {
    if (trend === 'up') {
      return {
        color: 'var(--status-success)',
        borderColor: 'color-mix(in srgb, var(--status-success) 25%, transparent)',
        backgroundColor: 'color-mix(in srgb, var(--status-success) 6%, transparent)',
      }
    }
    if (trend === 'down') {
      return {
        color: 'var(--status-error)',
        borderColor: 'color-mix(in srgb, var(--status-error) 25%, transparent)',
        backgroundColor: 'color-mix(in srgb, var(--status-error) 6%, transparent)',
      }
    }
    return {
      color: 'var(--silver-400)',
      borderColor: 'color-mix(in srgb, var(--silver-300) 25%, transparent)',
      backgroundColor: 'color-mix(in srgb, var(--silver-100) 6%, transparent)',
    }
  }

  return [
    {
      title: t('analytics.nav.userActivity'),
      icon: IconUsers,
      items: [
        {
          label: t('analytics.userActivity.dailyActiveUsers'),
          value: formatNumber(u.activeUsersDaily.slice(-1)[0]?.count || 0),
          change: '+' + (u.activeUsersDaily.slice(-1)[0]?.count || 0),
          changeStyle: getTrendStyle('up'),
        },
        {
          label: t('analytics.userActivity.retention1d'),
          value: formatPercent(u.userRetention.day1),
          change:
            u.userRetention.day1 > 50
              ? t('analytics.status.good')
              : t('analytics.status.needsWork'),
          changeStyle: getTrendStyle(u.userRetention.day1 > 50 ? 'up' : 'down'),
        },
        {
          label: t('analytics.userActivity.retention7d'),
          value: formatPercent(u.userRetention.day7),
          change:
            u.userRetention.day7 > 30 ? t('analytics.status.good') : t('analytics.status.average'),
          changeStyle: getTrendStyle(u.userRetention.day7 > 30 ? 'up' : 'neutral'),
        },
        {
          label: t('analytics.userActivity.retention30d'),
          value: formatPercent(u.userRetention.day30),
          change:
            u.userRetention.day30 > 10 ? t('analytics.status.good') : t('analytics.status.average'),
          changeStyle: getTrendStyle(u.userRetention.day30 > 10 ? 'up' : 'neutral'),
        },
      ],
    },
    {
      title: t('analytics.nav.problemCompletion'),
      icon: IconFileText,
      items: [
        {
          label: t('analytics.problemCompletion.totalAttempts'),
          value: formatNumber(p.totalAttempts),
        },
        {
          label: t('analytics.problemCompletion.successfulAttempts'),
          value: formatNumber(p.successfulAttempts),
          change: '+' + formatNumber(p.successfulAttempts),
          changeStyle: getTrendStyle('up'),
        },
        {
          label: t('analytics.problemCompletion.completionRate'),
          value: formatPercent(p.overallCompletionRate),
          change:
            p.overallCompletionRate > 30
              ? t('analytics.status.good')
              : t('analytics.status.needsWork'),
          changeStyle: getTrendStyle(p.overallCompletionRate > 30 ? 'up' : 'down'),
        },
        {
          label: t('analytics.problemCompletion.trendingProblems'),
          value: formatNumber(p.trendingProblems.length),
        },
      ],
    },
    {
      title: t('analytics.nav.contestParticipation'),
      icon: IconTrophy,
      items: [
        {
          label: t('analytics.contestParticipation.totalContests'),
          value: formatNumber(c.totalContests),
        },
        {
          label: t('analytics.contestParticipation.totalParticipants'),
          value: formatNumber(c.totalParticipants),
          change: '+' + formatNumber(c.totalParticipants),
          changeStyle: getTrendStyle('up'),
        },
        {
          label: t('analytics.contestParticipation.avgParticipants'),
          value: c.averageParticipantsPerContest.toFixed(1),
        },
        {
          label: t('analytics.contestParticipation.virtualParticipation'),
          value: formatNumber(c.virtualParticipation.total),
        },
      ],
    },
    {
      title: t('analytics.nav.performance'),
      icon: IconServer,
      items: [
        {
          label: t('analytics.performance.uptime'),
          value: formatUptime(s.systemUptime),
          change:
            s.systemUptime > 86400 * 7
              ? t('analytics.status.excellent')
              : t('analytics.status.good'),
          changeStyle: getTrendStyle(s.systemUptime > 86400 * 7 ? 'up' : 'neutral'),
        },
        {
          label: t('analytics.performance.throughput'),
          value: formatNumber(s.throughput),
        },
        {
          label: t('analytics.performance.errorRate'),
          value: formatPercent(s.errorRate),
          change:
            s.errorRate > 1
              ? t('analytics.status.needsAttention')
              : t('analytics.status.excellent'),
          changeStyle: getTrendStyle(s.errorRate > 1 ? 'down' : 'up'),
        },
        {
          label: t('analytics.performance.memoryUsage'),
          value: formatPercent(s.resourceUsage.memory),
          change:
            s.resourceUsage.memory > 80 ? t('analytics.status.high') : t('analytics.status.normal'),
          changeStyle: getTrendStyle(s.resourceUsage.memory > 80 ? 'down' : 'neutral'),
        },
      ],
    },
  ]
})

const chartData = computed(() => {
  if (!userActivityReport.value) return []
  return userActivityReport.value.activeUsersDaily.map((item) => ({
    date: new Date(item.date),
    users: item.count,
  }))
})

const heatmapData = computed(() => {
  if (!userActivityReport.value) return { cells: [], rows: [], columns: [] }
  const hours = userActivityReport.value.peakActiveHours
  const cells = hours.map((h) => ({
    x: h.hour % 6,
    y: Math.floor(h.hour / 6),
    value: h.count,
    label: `${h.hour}:00`,
  }))
  const rows = [0, 1, 2, 3].map((r) => ({ label: `${r * 6}:00-${(r + 1) * 6}:00` }))
  const columns = [0, 1, 2, 3, 4, 5].map((c) => ({ label: `${c}` }))
  return { cells, rows, columns }
})

const topUsers = computed(() => {
  if (!userActivityReport.value) return []
  return [...userActivityReport.value.topActiveUsers]
    .sort((a, b) => b.loginCount - a.loginCount)
    .slice(0, 10)
})

const difficultyBarItems = computed(() => {
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

const hardestBarItems = computed(() => {
  if (!problemCompletionReport.value) return []
  return problemCompletionReport.value.hardestProblems.map((problem) => ({
    id: problem.problemId,
    label: problem.title,
    value: problem.completionRate,
    color: 'var(--status-error)',
    subtitle: problem.difficulty,
  }))
})

const tagItems = computed(() => {
  if (!problemCompletionReport.value) return []
  return problemCompletionReport.value.byTag.map((tag) => ({
    id: tag.tagId,
    label: tag.label,
    value: tag.rate,
    count: tag.total,
  }))
})

const typeBarItems = computed(() => {
  if (!contestParticipationReport.value) return []
  return contestParticipationReport.value.byType.map((item) => ({
    id: item.type,
    label: item.type,
    value: item.avgParticipants,
    subtitle: `${item.count} ${t('analytics.contestParticipation.contests')}`,
  }))
})

const topContestBarItems = computed(() => {
  if (!contestParticipationReport.value) return []
  return contestParticipationReport.value.topContests.map((contest) => ({
    id: contest.contestId,
    label: contest.title,
    value: contest.participants,
    subtitle: `${contest.participants} ${t('analytics.contestParticipants')}`,
  }))
})

const endpointBarItems = computed(() => {
  if (!performanceReport.value) return []
  return performanceReport.value.slowestEndpoints.map((endpoint) => ({
    id: endpoint.endpoint,
    label: endpoint.endpoint,
    value: endpoint.averageTime,
    subtitle: `${endpoint.requestCount} ${t('analytics.performance.requests')}`,
  }))
})
</script>

<template>
  <div class="flex flex-col gap-5 px-4 py-5 lg:px-6 min-h-full bg-background">
    <!-- Precision Header -->
    <header
      class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between pb-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/60"
    >
      <div class="space-y-1">
        <h1 class="text-xl font-bold tracking-tight text-foreground font-mono uppercase">
          {{ t('analytics.title') }}
        </h1>
        <p class="text-xs text-[var(--silver-500)]">
          {{ t('analytics.description') }}
        </p>
      </div>

      <!-- Header Control & Info Bar -->
      <div
        class="grid w-full grid-cols-1 gap-2 bg-[var(--surface-sunken)] border border-[var(--border)] p-2 shadow-sm rounded-none sm:w-auto sm:grid-cols-3"
      >
        <!-- Monospace Status/Date Display -->
        <div
          class="flex h-8 w-full items-center justify-center gap-1.5 whitespace-nowrap bg-card px-2 border border-[var(--silver-200)] dark:border-[var(--silver-300)]/50 text-xxs font-mono text-[var(--silver-500)] rounded-none sm:w-36"
        >
          <span class="w-1.5 h-1.5 rounded-full bg-[var(--status-success)] animate-pulse"></span>
          <span class="text-[var(--silver-400)] font-data">{{ formattedDate }}</span>
          <span class="font-bold text-foreground font-data tabular-nums">{{ formattedTime }}</span>
        </div>

        <Select v-model="days">
          <SelectTrigger
            size="sm"
            class="h-8 w-full text-xs rounded-none border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-card sm:w-36"
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent class="rounded-none">
            <SelectItem :value="7">{{ t('analytics.periods.7days') }}</SelectItem>
            <SelectItem :value="30">{{ t('analytics.periods.30days') }}</SelectItem>
            <SelectItem :value="90">{{ t('analytics.periods.90days') }}</SelectItem>
            <SelectItem :value="365">{{ t('analytics.periods.1year') }}</SelectItem>
          </SelectContent>
        </Select>

        <Button
          variant="outline"
          size="sm"
          @click="loadReport"
          :disabled="loading"
          class="h-8 w-full rounded-none border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-card sm:w-36"
        >
          <IconRefresh class="h-3.5 w-3.5 mr-1" :class="{ 'animate-spin': loading }" />
          {{ t('common.refresh') }}
        </Button>
      </div>
    </header>

    <!-- Main Layout -->
    <div class="min-w-0 flex-1">
      <!-- Loading State -->
      <div v-if="loading" class="flex items-center justify-center py-16">
        <div class="flex items-center gap-3 text-[var(--silver-400)] font-mono">
          <div
            class="h-4 w-4 border-2 border-[var(--silver-300)] border-t-foreground rounded-full animate-spin"
          />
          <span>{{ t('common.loading') }}</span>
        </div>
      </div>

      <!-- Permission Denied -->
      <div v-else-if="showRefreshSession" class="flex items-center justify-center py-16">
        <div class="text-center space-y-4">
          <p class="text-[var(--silver-400)] font-mono">{{ t('analytics.permissionDenied') }}</p>
          <Button
            variant="outline"
            size="sm"
            @click="refreshSession"
            :disabled="loading"
            class="rounded-none"
          >
            <IconRefresh class="h-4 w-4 mr-2" :class="{ 'animate-spin': loading }" />
            {{ t('analytics.refreshSession') }}
          </Button>
        </div>
      </div>

      <!-- Unified Dashboard Content Grid -->
      <div
        v-else-if="
          userActivityReport &&
          problemCompletionReport &&
          contestParticipationReport &&
          performanceReport
        "
        class="space-y-5"
      >
        <!-- Row 1: Continuous overview panel -->
        <AnalyticsOverviewPanel :groups="metricGroups" />

        <!-- Row 2: User Activity Trends & Heatmap -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div class="lg:col-span-2">
            <AreaChart
              :title="t('analytics.userActivity.activeUsersTrend')"
              :description="t('analytics.userActivity.activeUsersTrendDesc')"
              :data="chartData"
              :period="chartTimePeriod"
              :show-period-selector="false"
              :series-keys="['users']"
              :config="{
                users: {
                  label: t('analytics.userActivity.activeUsers'),
                  color: 'var(--accent-primary)',
                },
              }"
            />
          </div>
          <div class="lg:col-span-1">
            <AnalyticsHeatmap
              :title="t('analytics.userActivity.peakHours')"
              :description="t('analytics.userActivity.peakHoursDesc')"
              :data="heatmapData.cells"
              :rows="heatmapData.rows"
              :columns="heatmapData.columns"
              :cell-size="32"
              :cell-gap="4"
            />
          </div>
        </div>

        <!-- Row 3: User and problem insights -->
        <div class="grid grid-cols-1 gap-4 lg:grid-cols-12">
          <div class="lg:col-span-5">
            <AnalyticsTopUsersChart :data="topUsers" />
          </div>
          <div class="grid gap-4 md:grid-cols-2 lg:col-span-7">
            <AnalyticsResourceUsage
              :usage="performanceReport.resourceUsage"
              :format-percent="formatPercent"
            />
            <AnalyticsBarList
              :title="t('analytics.problemCompletion.byDifficulty')"
              :description="t('analytics.problemCompletion.byDifficultyDesc')"
              :items="difficultyBarItems"
              :show-percentage="true"
              :limit="5"
              compact
            />
          </div>
        </div>

        <!-- Row 4: Contest intelligence — all three cards on the same row -->
        <div class="grid grid-cols-1 gap-4 lg:grid-cols-12">
          <div class="lg:col-span-6">
            <AnalyticsBarList
              :title="t('analytics.contestParticipation.topContests')"
              :description="t('analytics.contestParticipation.topContestsDesc')"
              :items="topContestBarItems"
              :limit="3"
              compact
            />
          </div>
          <div class="lg:col-span-3">
            <AnalyticsBarList
              :title="t('analytics.contestParticipation.byType')"
              :description="t('analytics.contestParticipation.byTypeDesc')"
              :items="typeBarItems"
              :limit="3"
              compact
            />
          </div>
          <div class="lg:col-span-3">
            <AnalyticsTagCloud
              :title="t('analytics.problemCompletion.topTags')"
              :description="t('analytics.problemCompletion.topTagsDesc')"
              :tags="tagItems"
              :value-format="'percent'"
              :limit="10"
              compact
            />
          </div>
        </div>

        <!-- Row 5: Diagnostics only when actionable data exists -->
        <div
          v-if="hardestBarItems.length > 0 || endpointBarItems.length > 0"
          class="grid grid-cols-1 gap-4 lg:grid-cols-2"
        >
          <AnalyticsBarList
            v-if="hardestBarItems.length > 0"
            :title="t('analytics.problemCompletion.hardestProblems')"
            :description="t('analytics.problemCompletion.hardestProblemsDesc')"
            :items="hardestBarItems"
            :show-percentage="true"
            :limit="5"
            compact
          />
          <AnalyticsBarList
            v-if="endpointBarItems.length > 0"
            :title="t('analytics.performance.slowestEndpoints')"
            :description="t('analytics.performance.slowestEndpointsDesc')"
            :items="endpointBarItems"
            :limit="10"
            compact
          />
        </div>
      </div>

      <!-- No Data State -->
      <div v-else-if="!loading" class="flex items-center justify-center py-16">
        <div class="text-center">
          <p class="text-[var(--silver-400)] font-mono">{{ t('analytics.noData') }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
