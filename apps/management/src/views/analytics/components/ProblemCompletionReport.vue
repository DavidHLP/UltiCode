<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AnalyticsMetricCard, AnalyticsBarList, AnalyticsTagCloud } from '@/components/analytics'
import type { MetricData, BarListItem, TagItem } from '@/components/analytics'
import type { ProblemCompletionReport } from '@/api/admin/analytics'

const { t } = useI18n()

const props = defineProps<{
  report: ProblemCompletionReport
  formatNumber: (n: number) => string
  formatPercent: (n: number) => string
}>()

const metrics = computed<MetricData[]>(() => {
  const r = props.report
  return [
    {
      title: t('analytics.problemCompletion.totalAttempts'),
      value: props.formatNumber(r.totalAttempts),
      trend: 'neutral',
    },
    {
      title: t('analytics.problemCompletion.successfulAttempts'),
      value: props.formatNumber(r.successfulAttempts),
      trend: 'up',
      change: '+' + props.formatNumber(r.successfulAttempts),
    },
    {
      title: t('analytics.problemCompletion.completionRate'),
      value: props.formatPercent(r.overallCompletionRate),
      trend: r.overallCompletionRate > 30 ? 'up' : 'down',
      change:
        r.overallCompletionRate > 30 ? t('analytics.status.good') : t('analytics.status.needsWork'),
    },
    {
      title: t('analytics.problemCompletion.trendingProblems'),
      value: r.trendingProblems.length,
      trend: 'neutral',
    },
  ]
})

const difficultyBarItems = computed<BarListItem[]>(() => {
  const difficultyColors: Record<string, string> = {
    EASY: 'var(--status-success-mark)',
    MEDIUM: 'var(--status-warning-mark)',
    HARD: 'var(--status-error-mark)',
  }
  return props.report.byDifficulty.map((item) => ({
    id: item.difficulty,
    label: item.difficulty,
    value: item.rate,
    color: difficultyColors[item.difficulty] || 'var(--accent-primary)',
    subtitle: `${item.completed}/${item.total} ${t('analytics.problemCompletion.completed')}`,
  }))
})

const hardestBarItems = computed<BarListItem[]>(() =>
  props.report.hardestProblems.map((problem) => ({
    id: problem.problemId,
    label: problem.title,
    value: problem.completionRate,
    color: 'var(--status-error-mark)',
    subtitle: problem.difficulty,
  })),
)

const tagItems = computed<TagItem[]>(() =>
  props.report.byTag.map((tag) => ({
    id: tag.tagId,
    label: tag.label,
    value: tag.rate,
    count: tag.total,
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

    <AnalyticsTagCloud
      :title="t('analytics.problemCompletion.topTags')"
      :description="t('analytics.problemCompletion.topTagsDesc')"
      :tags="tagItems"
      :value-format="'percent'"
      :limit="20"
    />
  </div>
</template>
