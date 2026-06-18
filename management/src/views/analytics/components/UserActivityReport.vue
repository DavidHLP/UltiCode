<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnalyticsMetricCard, AnalyticsHeatmap } from '@/components/analytics'
import type {
  MetricData,
  HeatmapCell,
  HeatmapRow,
  HeatmapColumn,
} from '@/components/analytics'
import AreaChart from '@/components/dashboard/AreaChart.vue'
import type { ChartDataPoint } from '@/components/dashboard/AreaChart.vue'
import type { UserActivityReport } from '@/api/admin/analytics'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { IconChartBar } from '@tabler/icons-vue'

const { t } = useI18n()

const props = defineProps<{
  report: UserActivityReport
  formatNumber: (n: number) => string
  formatPercent: (n: number) => string
}>()

const metrics = computed<MetricData[]>(() => {
  const r = props.report
  return [
    {
      title: t('analytics.userActivity.dailyActiveUsers'),
      value: props.formatNumber(r.activeUsersDaily.slice(-1)[0]?.count || 0),
      trend: 'up',
      change: '+' + (r.activeUsersDaily.slice(-1)[0]?.count || 0),
    },
    {
      title: t('analytics.userActivity.retention1d'),
      value: props.formatPercent(r.userRetention.day1),
      trend: r.userRetention.day1 > 50 ? 'up' : 'down',
      change:
        r.userRetention.day1 > 50 ? t('analytics.status.good') : t('analytics.status.needsWork'),
    },
    {
      title: t('analytics.userActivity.retention7d'),
      value: props.formatPercent(r.userRetention.day7),
      trend: r.userRetention.day7 > 30 ? 'up' : 'neutral',
      change:
        r.userRetention.day7 > 30 ? t('analytics.status.good') : t('analytics.status.average'),
    },
    {
      title: t('analytics.userActivity.retention30d'),
      value: props.formatPercent(r.userRetention.day30),
      trend: r.userRetention.day30 > 10 ? 'up' : 'neutral',
      change:
        r.userRetention.day30 > 10 ? t('analytics.status.good') : t('analytics.status.average'),
    },
  ]
})

const chartData = computed<ChartDataPoint[]>(() =>
  props.report.activeUsersDaily.map((item) => ({
    date: new Date(item.date),
    users: item.count,
  })),
)

const heatmapData = computed(() => {
  const hours = props.report.peakActiveHours
  const cells: HeatmapCell[] = hours.map((h) => ({
    x: h.hour % 6,
    y: Math.floor(h.hour / 6),
    value: h.count,
    label: `${h.hour}:00`,
  }))
  const rows: HeatmapRow[] = [0, 1, 2, 3].map((r) => ({ label: `${r * 6}:00-${(r + 1) * 6}:00` }))
  const columns: HeatmapColumn[] = [0, 1, 2, 3, 4, 5].map((c) => ({ label: `${c}` }))
  return { cells, rows, columns }
})

const topUsers = computed(() => {
  return [...props.report.topActiveUsers]
    .sort((a, b) => b.loginCount - a.loginCount)
    .slice(0, 10)
})

const maxVal = computed(() => {
  const counts = props.report.topActiveUsers.map((u) => u.loginCount)
  return Math.max(...counts, 1)
})

function truncateUsername(username: string) {
  if (username.length > 7) {
    return username.slice(0, 6) + '..'
  }
  return username
}
</script>

<template>
  <div class="space-y-5">
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
      <AnalyticsMetricCard v-for="(metric, index) in metrics" :key="index" :metric="metric" />
    </div>

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

    <!-- Top Active Users Leaderboard - Monospace Terminal SVG Bar Chart -->
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
            <text x="35" y="203" class="text-2xs font-mono fill-[var(--silver-400)] text-right" text-anchor="end">0</text>
            <text x="35" y="163" class="text-2xs font-mono fill-[var(--silver-400)] text-right" text-anchor="end">{{ formatNumber(Math.round(maxVal * 0.25)) }}</text>
            <text x="35" y="123" class="text-2xs font-mono fill-[var(--silver-400)] text-right" text-anchor="end">{{ formatNumber(Math.round(maxVal * 0.5)) }}</text>
            <text x="35" y="83" class="text-2xs font-mono fill-[var(--silver-400)] text-right" text-anchor="end">{{ formatNumber(Math.round(maxVal * 0.75)) }}</text>
            <text x="35" y="43" class="text-2xs font-mono fill-[var(--silver-400)] text-right" text-anchor="end">{{ formatNumber(maxVal) }}</text>

            <!-- Bars loop -->
            <g v-for="(user, i) in topUsers" :key="user.userId" class="group/bar">
              <!-- Bar rect -->
              <rect
                :x="45 + i * 53"
                :y="200 - (user.loginCount / maxVal) * 160"
                width="28"
                :height="(user.loginCount / maxVal) * 160"
                class="fill-[color-mix(in oklch,var(--accent-primary)_35%,var(--silver-300))] group-hover/bar:fill-[var(--accent-primary)] stroke-[var(--silver-200)] dark:stroke-[var(--silver-700)] stroke-1 transition-all duration-200 cursor-pointer"
              />
              <!-- Label value on top of bar -->
              <text
                :x="45 + i * 53 + 14"
                :y="200 - (user.loginCount / maxVal) * 160 - 6"
                class="text-2xs font-mono font-bold fill-foreground text-center opacity-70 group-hover/bar:opacity-100 origin-bottom transition-all duration-150"
                text-anchor="middle"
              >
                {{ user.loginCount }}
              </text>
              <!-- Label username below bar -->
              <text
                :x="45 + i * 53 + 14"
                :y="216"
                class="text-2xs font-mono fill-[var(--silver-500)] group-hover/bar:fill-foreground text-center font-medium transition-colors"
                text-anchor="middle"
              >
                {{ truncateUsername(user.username) }}
              </text>
            </g>
          </svg>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
