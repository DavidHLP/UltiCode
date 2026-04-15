<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  AnalyticsMetricCard,
  AnalyticsBarList,
  AnalyticsHeatmap,
} from '@/components/analytics'
import type { MetricData, BarListItem, HeatmapCell, HeatmapRow, HeatmapColumn } from '@/components/analytics'
import AreaChart from '@/components/dashboard/AreaChart.vue'
import type { ChartDataPoint } from '@/components/dashboard/AreaChart.vue'
import type { UserActivityReport } from '@/api/admin/analytics'

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
      change: r.userRetention.day1 > 50 ? t('analytics.status.good') : t('analytics.status.needsWork'),
    },
    {
      title: t('analytics.userActivity.retention7d'),
      value: props.formatPercent(r.userRetention.day7),
      trend: r.userRetention.day7 > 30 ? 'up' : 'neutral',
      change: r.userRetention.day7 > 30 ? t('analytics.status.good') : t('analytics.status.average'),
    },
    {
      title: t('analytics.userActivity.retention30d'),
      value: props.formatPercent(r.userRetention.day30),
      trend: r.userRetention.day30 > 10 ? 'up' : 'neutral',
      change: r.userRetention.day30 > 10 ? t('analytics.status.good') : t('analytics.status.average'),
    },
  ]
})

const chartData = computed<ChartDataPoint[]>(() =>
  props.report.activeUsersDaily.map((item) => ({
    date: new Date(item.date),
    users: item.count,
  })),
)

const barItems = computed<BarListItem[]>(() =>
  props.report.topActiveUsers.map((user) => ({
    id: user.userId,
    label: user.username,
    value: user.loginCount,
    subtitle: t('analytics.userActivity.logins', { count: user.loginCount }),
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
          :config="{ users: { label: t('analytics.userActivity.activeUsers'), color: 'var(--accent-primary)' } }"
        />
      </div>
      <div class="lg:col-span-1">
        <AnalyticsHeatmap
          :title="t('analytics.userActivity.peakHours')"
          :description="t('analytics.userActivity.peakHoursDesc')"
          :data="heatmapData.cells"
          :rows="heatmapData.rows"
          :columns="heatmapData.columns"
          :cell-size="24"
        />
      </div>
    </div>

    <AnalyticsBarList
      :title="t('analytics.userActivity.topUsers')"
      :description="t('analytics.userActivity.topUsersDesc')"
      :items="barItems"
      :limit="10"
    />
  </div>
</template>
