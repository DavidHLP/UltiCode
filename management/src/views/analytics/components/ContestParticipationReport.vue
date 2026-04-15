<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnalyticsMetricCard, AnalyticsBarList } from '@/components/analytics'
import type { MetricData, BarListItem } from '@/components/analytics'
import type { ContestParticipationReport } from '@/api/admin/analytics'

const { t } = useI18n()

const props = defineProps<{
  report: ContestParticipationReport
  formatNumber: (n: number) => string
}>()

const metrics = computed<MetricData[]>(() => {
  const r = props.report
  return [
    {
      title: t('analytics.contestParticipation.totalContests'),
      value: r.totalContests,
      trend: 'neutral',
    },
    {
      title: t('analytics.contestParticipation.totalParticipants'),
      value: props.formatNumber(r.totalParticipants),
      trend: 'up',
      change: '+' + props.formatNumber(r.totalParticipants),
    },
    {
      title: t('analytics.contestParticipation.avgParticipants'),
      value: r.averageParticipantsPerContest.toFixed(1),
      trend: 'neutral',
      suffix: t('analytics.perContest'),
    },
    {
      title: t('analytics.contestParticipation.virtualParticipation'),
      value: r.virtualParticipation.total,
      trend: 'neutral',
    },
  ]
})

const typeBarItems = computed<BarListItem[]>(() =>
  props.report.byType.map((item) => ({
    id: item.type,
    label: item.type,
    value: item.avgParticipants,
    subtitle: `${item.count} ${t('analytics.contestParticipation.contests')}`,
  })),
)

const topBarItems = computed<BarListItem[]>(() =>
  props.report.topContests.map((contest) => ({
    id: contest.contestId,
    label: contest.title,
    value: contest.participants,
    subtitle: `${contest.participants} ${t('analytics.contestParticipants')}`,
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
        :title="t('analytics.contestParticipation.byType')"
        :description="t('analytics.contestParticipation.byTypeDesc')"
        :items="typeBarItems"
        :limit="10"
      />
      <AnalyticsBarList
        :title="t('analytics.contestParticipation.topContests')"
        :description="t('analytics.contestParticipation.topContestsDesc')"
        :items="topBarItems"
        :limit="10"
      />
    </div>
  </div>
</template>
