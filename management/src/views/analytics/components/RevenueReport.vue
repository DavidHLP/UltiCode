<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnalyticsMetricCard, AnalyticsBarList } from '@/components/analytics'
import type { MetricData, BarListItem } from '@/components/analytics'
import type { RevenueReport } from '@/api/admin/analytics'

const { t } = useI18n()

const props = defineProps<{
  report: RevenueReport
  formatNumber: (n: number) => string
  formatPercent: (n: number) => string
  formatCurrency: (n: number) => string
}>()

const metrics = computed<MetricData[]>(() => {
  const r = props.report
  return [
    {
      title: t('analytics.revenue.mrr'),
      value: props.formatCurrency(r.mrr),
      trend: 'up',
      prefix: '$',
    },
    {
      title: t('analytics.revenue.arr'),
      value: props.formatCurrency(r.arr),
      trend: 'up',
      prefix: '$',
    },
    {
      title: t('analytics.revenue.subscribers'),
      value: r.subscriberCount,
      trend: 'neutral',
    },
    {
      title: t('analytics.revenue.conversionRate'),
      value: props.formatPercent(r.conversionRate),
      trend: r.conversionRate > 5 ? 'up' : 'neutral',
      change: r.conversionRate > 5 ? t('analytics.status.good') : t('analytics.status.average'),
    },
  ]
})

const planBarItems = computed<BarListItem[]>(() =>
  props.report.byPlan.map((item) => ({
    id: item.plan,
    label: item.plan,
    value: item.revenue,
    subtitle: `${item.subscribers} ${t('analytics.revenue.subscribers')}`,
  })),
)
</script>

<template>
  <div class="space-y-5">
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
      <AnalyticsMetricCard v-for="(metric, index) in metrics" :key="index" :metric="metric" />
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <AnalyticsBarList
        :title="t('analytics.revenue.byPlan')"
        :description="t('analytics.revenue.byPlanDesc')"
        :items="planBarItems"
        :limit="10"
      />

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
            <span class="text-sm text-[var(--silver-500)]">{{ t('analytics.revenue.arpu') }}</span>
            <span class="font-data tabular-nums font-medium">{{
              formatCurrency(report.arpu)
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
                report.churnRate > 5 ? 'text-[var(--status-error)]' : 'text-[var(--status-success)]'
              "
            >
              {{ formatPercent(report.churnRate) }}
            </span>
          </div>
          <div class="flex items-center justify-between py-2">
            <span class="text-sm text-[var(--silver-500)]">{{
              t('analytics.revenue.totalRevenue')
            }}</span>
            <span class="font-data tabular-nums font-medium">{{
              formatCurrency(report.totalRevenue)
            }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
