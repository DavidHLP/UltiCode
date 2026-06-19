<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { IconServer } from '@tabler/icons-vue'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface ResourceUsage {
  cpu: number
  memory: number
  disk: number
}

const props = defineProps<{
  usage: ResourceUsage
  formatPercent: (value: number) => string
}>()

const { t } = useI18n()

const metrics = [
  { key: 'cpu', labelKey: 'analytics.performance.cpu', normalColor: 'var(--accent-primary)' },
  { key: 'memory', labelKey: 'analytics.performance.memory', normalColor: 'var(--status-success)' },
  { key: 'disk', labelKey: 'analytics.performance.disk', normalColor: 'var(--status-success)' },
] as const

function metricColor(value: number, normalColor: string): string {
  if (value > 80) return 'var(--status-error)'
  if (value > 60) return 'var(--status-warning)'
  return normalColor
}
</script>

<template>
  <Card
    class="h-full gap-0 py-0 overflow-hidden rounded-none border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 shadow-float flex flex-col"
  >
    <CardHeader
      class="flex flex-row items-center gap-2 px-4 py-3.5 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/50 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/10"
    >
      <IconServer class="size-4 text-[var(--accent-primary)]" />
      <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">
        {{ t('analytics.performance.resourceUsage') }}
      </CardTitle>
    </CardHeader>
    <CardContent class="grid gap-3 p-4">
      <div v-for="metric in metrics" :key="metric.key" class="space-y-1.5">
        <div class="flex items-center justify-between gap-3">
          <span class="text-xs font-mono text-[var(--silver-500)]">
            {{ t(metric.labelKey) }}
          </span>
          <span class="text-xs font-data font-bold tabular-nums text-foreground">
            {{ props.formatPercent(props.usage[metric.key]) }}
          </span>
        </div>
        <div class="h-1.5 overflow-hidden bg-[var(--silver-100)] dark:bg-[var(--silver-800)]">
          <div
            class="h-full transition-[width] duration-500"
            :style="{
              width: Math.min(Math.max(props.usage[metric.key], 0), 100) + '%',
              backgroundColor: metricColor(props.usage[metric.key], metric.normalColor),
            }"
          />
        </div>
      </div>
    </CardContent>
  </Card>
</template>
