<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnalyticsMetricCard, AnalyticsBarList } from '@/components/analytics'
import type { MetricData, BarListItem } from '@/components/analytics'
import type { PerformanceReport } from '@/api/admin/analytics'

const { t } = useI18n()

const props = defineProps<{
  report: PerformanceReport
  formatNumber: (n: number) => string
  formatPercent: (n: number) => string
}>()

const metrics = computed<MetricData[]>(() => {
  const r = props.report
  return [
    {
      title: t('analytics.performance.uptime'),
      value: (() => {
        const d = Math.floor(r.systemUptime / 86400)
        const h = Math.floor((r.systemUptime % 86400) / 3600)
        return `${d}d ${h}h`
      })(),
      trend: r.systemUptime > 86400 * 7 ? 'up' : 'neutral',
      change:
        r.systemUptime > 86400 * 7 ? t('analytics.status.excellent') : t('analytics.status.good'),
    },
    {
      title: t('analytics.performance.throughput'),
      value: props.formatNumber(r.throughput),
      trend: 'neutral',
      suffix: '/24h',
    },
    {
      title: t('analytics.performance.errorRate'),
      value: props.formatPercent(r.errorRate),
      trend: r.errorRate > 1 ? 'down' : 'up',
      change:
        r.errorRate > 1 ? t('analytics.status.needsAttention') : t('analytics.status.excellent'),
    },
    {
      title: t('analytics.performance.memoryUsage'),
      value: props.formatPercent(r.resourceUsage.memory),
      trend: r.resourceUsage.memory > 80 ? 'down' : 'neutral',
      change:
        r.resourceUsage.memory > 80 ? t('analytics.status.high') : t('analytics.status.normal'),
    },
  ]
})

const endpointBarItems = computed<BarListItem[]>(() =>
  props.report.slowestEndpoints.map((endpoint) => ({
    id: endpoint.endpoint,
    label: endpoint.endpoint,
    value: endpoint.averageTime,
    subtitle: `${endpoint.requestCount} ${t('analytics.performance.requests')}`,
  })),
)
</script>

<template>
  <div class="space-y-5">
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
      <AnalyticsMetricCard v-for="(metric, index) in metrics" :key="index" :metric="metric" />
    </div>

    <div
      class="border border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/60 bg-card shadow-float overflow-hidden flex flex-col rounded-none"
    >
      <div
        class="pb-4 px-5 pt-5 bg-[var(--surface)] dark:bg-[var(--surface-highlight)]/10 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/50"
      >
        <h3 class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">
          {{ t('analytics.performance.resourceUsage') }}
        </h3>
      </div>
      <div class="p-5 flex-1 grid grid-cols-1 md:grid-cols-3 gap-6">
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-sm text-[var(--foreground-muted)]">{{
              t('analytics.performance.cpu')
            }}</span>
            <span class="font-data tabular-nums font-medium">{{
              formatPercent(report.resourceUsage.cpu)
            }}</span>
          </div>
          <div
            class="h-2 bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)] rounded-full overflow-hidden"
          >
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="
                report.resourceUsage.cpu > 80
                  ? 'bg-[var(--status-error-mark)]'
                  : report.resourceUsage.cpu > 60
                    ? 'bg-[var(--status-warning-mark)]'
                    : 'bg-[var(--accent-primary)]'
              "
              :style="{ width: report.resourceUsage.cpu + '%' }"
            />
          </div>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-sm text-[var(--foreground-muted)]">{{
              t('analytics.performance.memory')
            }}</span>
            <span class="font-data tabular-nums font-medium">{{
              formatPercent(report.resourceUsage.memory)
            }}</span>
          </div>
          <div
            class="h-2 bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)] rounded-full overflow-hidden"
          >
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="
                report.resourceUsage.memory > 80
                  ? 'bg-[var(--status-error-mark)]'
                  : report.resourceUsage.memory > 60
                    ? 'bg-[var(--status-warning-mark)]'
                    : 'bg-[var(--status-success-mark)]'
              "
              :style="{ width: report.resourceUsage.memory + '%' }"
            />
          </div>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-sm text-[var(--foreground-muted)]">{{
              t('analytics.performance.disk')
            }}</span>
            <span class="font-data tabular-nums font-medium">{{
              formatPercent(report.resourceUsage.disk)
            }}</span>
          </div>
          <div
            class="h-2 bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)] rounded-full overflow-hidden"
          >
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="
                report.resourceUsage.disk > 80
                  ? 'bg-[var(--status-error-mark)]'
                  : report.resourceUsage.disk > 60
                    ? 'bg-[var(--status-warning-mark)]'
                    : 'bg-[var(--status-success-mark)]'
              "
              :style="{ width: report.resourceUsage.disk + '%' }"
            />
          </div>
        </div>
      </div>
    </div>

    <AnalyticsBarList
      :title="t('analytics.performance.slowestEndpoints')"
      :description="t('analytics.performance.slowestEndpointsDesc')"
      :items="endpointBarItems"
      :limit="10"
    />
  </div>
</template>
