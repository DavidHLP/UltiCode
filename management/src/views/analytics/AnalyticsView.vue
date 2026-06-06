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
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  AnalyticsHeatmap,
  AnalyticsTagCloud,
  AnalyticsBarList,
} from '@/components/analytics'
import AreaChart from '@/components/dashboard/AreaChart.vue'
import {
  IconUsers,
  IconFileText,
  IconTrophy,
  IconServer,
  IconChartBar,
  IconClock,
} from '@tabler/icons-vue'

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
          change: u.userRetention.day1 > 50 ? t('analytics.status.good') : t('analytics.status.needsWork'),
          changeStyle: getTrendStyle(u.userRetention.day1 > 50 ? 'up' : 'down'),
        },
        {
          label: t('analytics.userActivity.retention7d'),
          value: formatPercent(u.userRetention.day7),
          change: u.userRetention.day7 > 30 ? t('analytics.status.good') : t('analytics.status.average'),
          changeStyle: getTrendStyle(u.userRetention.day7 > 30 ? 'up' : 'neutral'),
        },
        {
          label: t('analytics.userActivity.retention30d'),
          value: formatPercent(u.userRetention.day30),
          change: u.userRetention.day30 > 10 ? t('analytics.status.good') : t('analytics.status.average'),
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
          change: p.overallCompletionRate > 30 ? t('analytics.status.good') : t('analytics.status.needsWork'),
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
          change: s.systemUptime > 86400 * 7 ? t('analytics.status.excellent') : t('analytics.status.good'),
          changeStyle: getTrendStyle(s.systemUptime > 86400 * 7 ? 'up' : 'neutral'),
        },
        {
          label: t('analytics.performance.throughput'),
          value: formatNumber(s.throughput),
        },
        {
          label: t('analytics.performance.errorRate'),
          value: formatPercent(s.errorRate),
          change: s.errorRate > 1 ? t('analytics.status.needsAttention') : t('analytics.status.excellent'),
          changeStyle: getTrendStyle(s.errorRate > 1 ? 'down' : 'up'),
        },
        {
          label: t('analytics.performance.memoryUsage'),
          value: formatPercent(s.resourceUsage.memory),
          change: s.resourceUsage.memory > 80 ? t('analytics.status.high') : t('analytics.status.normal'),
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

const maxVal = computed(() => {
  if (!userActivityReport.value) return 1
  const counts = userActivityReport.value.topActiveUsers.map((u) => u.loginCount)
  return Math.max(...counts, 1)
})

function truncateUsername(username: string) {
  if (username.length > 7) {
    return username.slice(0, 6) + '..'
  }
  return username
}

function formatLastActive(dateStr?: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

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
  <div class="flex flex-col gap-6 py-6 px-4 lg:px-8 min-h-full bg-background">
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
      <div class="flex flex-wrap items-center gap-3 bg-[var(--surface-sunken)] border border-[var(--border)] p-2 shadow-sm rounded-none">
        <!-- Monospace Status/Date Display -->
        <div class="flex items-center gap-2 px-2.5 py-1 bg-card border border-[var(--silver-200)] dark:border-[var(--silver-300)]/50 text-[11px] font-mono text-[var(--silver-500)] rounded-none">
          <span class="w-1.5 h-1.5 rounded-full bg-[var(--status-success)] animate-pulse"></span>
          <span class="text-[var(--silver-400)] font-data">{{ formattedDate }}</span>
          <span class="font-bold text-foreground font-data tabular-nums">{{ formattedTime }}</span>
        </div>

        <div class="flex items-center gap-2">
          <Select v-model="days">
            <SelectTrigger
              class="w-[130px] h-8 text-xs rounded-none border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-card"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent class="rounded-none">
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
            class="h-8 rounded-none border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-card"
          >
            <IconRefresh class="h-3.5 w-3.5 mr-1" :class="{ 'animate-spin': loading }" />
            {{ t('common.refresh') }}
          </Button>
        </div>
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
          <Button variant="outline" size="sm" @click="refreshSession" :disabled="loading" class="rounded-none">
            <IconRefresh class="h-4 w-4 mr-2" :class="{ 'animate-spin': loading }" />
            {{ t('analytics.refreshSession') }}
          </Button>
        </div>
      </div>

      <!-- Unified Dashboard Content Grid -->
      <div v-else-if="userActivityReport && problemCompletionReport && contestParticipationReport && performanceReport" class="space-y-6">
        
        <!-- Row 1: Metrics Command Panel (Overview Grid) -->
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-5">
          <Card v-for="panel in metricGroups" :key="panel.title" class="border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card overflow-hidden flex flex-col rounded-none shadow-sm">
            <CardHeader class="pb-3 pt-4 px-4 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/10 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/50">
              <div class="flex items-center gap-2">
                <component :is="panel.icon" class="h-4 w-4 text-[var(--accent-primary)]" />
                <CardTitle class="text-[11px] font-bold font-mono uppercase tracking-wide text-foreground">
                  {{ panel.title }}
                </CardTitle>
              </div>
            </CardHeader>
            <CardContent class="p-4 space-y-2.5">
              <div v-for="item in panel.items" :key="item.label" class="flex items-center justify-between text-xs border-b border-[var(--silver-100)]/50 dark:border-[var(--silver-200)]/20 pb-1.5 last:border-b-0 last:pb-0">
                <span class="text-[var(--silver-500)] font-mono">{{ item.label }}</span>
                <div class="flex items-center gap-1.5 font-data tabular-nums">
                  <span class="font-bold text-foreground">{{ item.value }}</span>
                  <span v-if="item.change" class="text-[9px] font-bold px-1.5 py-0.5 rounded-none border border-transparent font-mono" :style="item.changeStyle">
                    {{ item.change }}
                  </span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <!-- Row 2: User Activity Trends & Heatmap -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
          <div class="lg:col-span-2">
            <AreaChart
              :title="t('analytics.userActivity.activeUsersTrend')"
              :description="t('analytics.userActivity.activeUsersTrendDesc')"
              :data="chartData"
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

        <!-- Row 3: Top Users, Resource Usage, Slowest Endpoints -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          <!-- Top Active Users SVG Chart -->
          <Card
            class="border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card shadow-float overflow-hidden flex flex-col rounded-none"
          >
            <CardHeader class="pb-4 pt-5 px-5 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/10 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/50">
              <div class="flex items-center gap-2">
                <IconChartBar class="h-4 w-4 text-[var(--accent-primary)]" />
                <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">
                  {{ t('analytics.userActivity.topUsers') }}
                </CardTitle>
              </div>
              <CardDescription class="text-xs text-[var(--silver-400)] mt-1">
                {{ t('analytics.userActivity.topUsersDesc') }}
              </CardDescription>
            </CardHeader>
            <CardContent class="p-6">
              <div class="w-full overflow-hidden">
                <svg viewBox="0 0 600 240" class="w-full h-auto text-foreground select-none overflow-visible">
                  <!-- Horizontal grid lines -->
                  <line x1="45" y1="200" x2="580" y2="200" stroke="color-mix(in oklch, var(--border) 60%, transparent)" stroke-width="1" />
                  <line x1="45" y1="160" x2="580" y2="160" stroke="color-mix(in oklch, var(--border) 25%, transparent)" stroke-width="1" stroke-dasharray="2 2" />
                  <line x1="45" y1="120" x2="580" y2="120" stroke="color-mix(in oklch, var(--border) 25%, transparent)" stroke-width="1" stroke-dasharray="2 2" />
                  <line x1="45" y1="80" x2="580" y2="80" stroke="color-mix(in oklch, var(--border) 25%, transparent)" stroke-width="1" stroke-dasharray="2 2" />
                  <line x1="45" y1="40" x2="580" y2="40" stroke="color-mix(in oklch, var(--border) 25%, transparent)" stroke-width="1" stroke-dasharray="2 2" />

                  <!-- Y-Axis labels (ticks) -->
                  <text x="35" y="203" class="text-[9px] font-mono fill-[var(--silver-400)] text-right" text-anchor="end">0</text>
                  <text x="35" y="163" class="text-[9px] font-mono fill-[var(--silver-400)] text-right" text-anchor="end">{{ formatNumber(Math.round(maxVal * 0.25)) }}</text>
                  <text x="35" y="123" class="text-[9px] font-mono fill-[var(--silver-400)] text-right" text-anchor="end">{{ formatNumber(Math.round(maxVal * 0.5)) }}</text>
                  <text x="35" y="83" class="text-[9px] font-mono fill-[var(--silver-400)] text-right" text-anchor="end">{{ formatNumber(Math.round(maxVal * 0.75)) }}</text>
                  <text x="35" y="43" class="text-[9px] font-mono fill-[var(--silver-400)] text-right" text-anchor="end">{{ formatNumber(maxVal) }}</text>

                  <!-- Bars loop -->
                  <g v-for="(user, i) in topUsers" :key="user.userId" class="group/bar">
                    <rect
                      :x="45 + i * 53"
                      :y="200 - (user.loginCount / maxVal) * 160"
                      width="28"
                      :height="(user.loginCount / maxVal) * 160"
                      class="fill-[color-mix(in oklch,var(--accent-primary)_35%,var(--silver-300))] group-hover/bar:fill-[var(--accent-primary)] stroke-[var(--silver-200)] dark:stroke-[var(--silver-700)] stroke-1 transition-all duration-200 cursor-pointer"
                    />
                    <text
                      :x="45 + i * 53 + 14"
                      :y="200 - (user.loginCount / maxVal) * 160 - 6"
                      class="text-[9px] font-mono font-bold fill-foreground text-center opacity-70 group-hover/bar:opacity-100 origin-bottom transition-all duration-150"
                      text-anchor="middle"
                    >
                      {{ user.loginCount }}
                    </text>
                    <text
                      :x="45 + i * 53 + 14"
                      :y="216"
                      class="text-[9px] font-mono fill-[var(--silver-500)] group-hover/bar:fill-foreground text-center font-medium transition-colors"
                      text-anchor="middle"
                    >
                      {{ truncateUsername(user.username) }}
                    </text>
                  </g>
                </svg>
              </div>
            </CardContent>
          </Card>

          <!-- Resource Usage Card -->
          <Card class="border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card shadow-float overflow-hidden flex flex-col rounded-none">
            <CardHeader class="pb-4 pt-5 px-5 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/10 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/50">
              <div class="flex items-center gap-2">
                <IconServer class="h-4 w-4 text-[var(--accent-primary)]" />
                <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">
                  {{ t('analytics.performance.resourceUsage') }}
                </CardTitle>
              </div>
            </CardHeader>
            <CardContent class="p-5 space-y-4">
              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-[var(--silver-500)]">{{ t('analytics.performance.cpu') }}</span>
                  <span class="font-data tabular-nums font-medium">{{ formatPercent(performanceReport.resourceUsage.cpu) }}</span>
                </div>
                <div class="h-2 bg-[var(--silver-100)] dark:bg-[var(--silver-200)] rounded-full overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-500"
                    :class="performanceReport.resourceUsage.cpu > 80 ? 'bg-[var(--status-error)]' : performanceReport.resourceUsage.cpu > 60 ? 'bg-[var(--status-warning)]' : 'bg-[var(--accent-primary)]'"
                    :style="{ width: performanceReport.resourceUsage.cpu + '%' }"
                  />
                </div>
              </div>
              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-[var(--silver-500)]">{{ t('analytics.performance.memory') }}</span>
                  <span class="font-data tabular-nums font-medium">{{ formatPercent(performanceReport.resourceUsage.memory) }}</span>
                </div>
                <div class="h-2 bg-[var(--silver-100)] dark:bg-[var(--silver-200)] rounded-full overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-500"
                    :class="performanceReport.resourceUsage.memory > 80 ? 'bg-[var(--status-error)]' : performanceReport.resourceUsage.memory > 60 ? 'bg-[var(--status-warning)]' : 'bg-[var(--status-success)]'"
                    :style="{ width: performanceReport.resourceUsage.memory + '%' }"
                  />
                </div>
              </div>
              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-[var(--silver-500)]">{{ t('analytics.performance.disk') }}</span>
                  <span class="font-data tabular-nums font-medium">{{ formatPercent(performanceReport.resourceUsage.disk) }}</span>
                </div>
                <div class="h-2 bg-[var(--silver-100)] dark:bg-[var(--silver-200)] rounded-full overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-500"
                    :class="performanceReport.resourceUsage.disk > 80 ? 'bg-[var(--status-error)]' : performanceReport.resourceUsage.disk > 60 ? 'bg-[var(--status-warning)]' : 'bg-[var(--status-success)]'"
                    :style="{ width: performanceReport.resourceUsage.disk + '%' }"
                  />
                </div>
              </div>
            </CardContent>
          </Card>

          <!-- Slowest Endpoints -->
          <AnalyticsBarList
            :title="t('analytics.performance.slowestEndpoints')"
            :description="t('analytics.performance.slowestEndpointsDesc')"
            :items="endpointBarItems"
            :limit="10"
          />
        </div>

        <!-- Row 4: Problem Completion Details -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <AnalyticsBarList
            :title="t('analytics.problemCompletion.byDifficulty')"
            :description="t('analytics.problemCompletion.byDifficultyDesc')"
            :items="difficultyBarItems"
            :show-percentage="true"
            :limit="5"
          />
          <AnalyticsBarList
            :title="t('analytics.problemCompletion.hardestProblems')"
            :description="t('analytics.problemCompletion.hardestProblemsDesc')"
            :items="hardestBarItems"
            :show-percentage="true"
            :limit="5"
          />
        </div>

        <!-- Row 5: Tags & Contests -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
          <div class="lg:col-span-2">
            <AnalyticsTagCloud
              :title="t('analytics.problemCompletion.topTags')"
              :description="t('analytics.problemCompletion.topTagsDesc')"
              :tags="tagItems"
              :value-format="'percent'"
              :limit="20"
            />
          </div>
          <div class="lg:col-span-1 flex flex-col gap-5">
            <AnalyticsBarList
              :title="t('analytics.contestParticipation.byType')"
              :description="t('analytics.contestParticipation.byTypeDesc')"
              :items="typeBarItems"
              :limit="5"
            />
            <AnalyticsBarList
              :title="t('analytics.contestParticipation.topContests')"
              :description="t('analytics.contestParticipation.topContestsDesc')"
              :items="topContestBarItems"
              :limit="5"
            />
          </div>
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
