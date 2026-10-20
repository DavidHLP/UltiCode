<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Server as IconServer } from '@lucide/vue'

import { Card, CardContent, CardHeader, CardTitle } from '@ulticode/design-system/ui/card'

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
  { key: 'cpu', labelKey: 'analytics.performance.cpu', normalColor: 'var(--primary)' },
  {
    key: 'memory',
    labelKey: 'analytics.performance.memory',
    normalColor: 'var(--status-success-mark)',
  },
  {
    key: 'disk',
    labelKey: 'analytics.performance.disk',
    normalColor: 'var(--status-success-mark)',
  },
] as const

function metricColor(value: number, normalColor: string): string {
  if (value > 80) return 'var(--status-error-mark)'
  if (value > 60) return 'var(--status-warning-mark)'
  return normalColor
}
</script>

<template>
  <Card class="h-full gap-0 py-0 overflow-hidden flex flex-col">
    <CardHeader class="flex flex-row items-center gap-2 px-4 py-3.5">
      <IconServer class="size-4 text-primary" />
      <CardTitle>
        {{ t('analytics.performance.resourceUsage') }}
      </CardTitle>
    </CardHeader>
    <CardContent class="grid gap-3 p-4">
      <div v-for="metric in metrics" :key="metric.key" class="space-y-1.5">
        <div class="flex items-center justify-between gap-3">
          <span class="text-xs font-mono text-muted-foreground">
            {{ t(metric.labelKey) }}
          </span>
          <span class="text-xs font-mono font-bold tabular-nums text-foreground">
            {{ props.formatPercent(props.usage[metric.key]) }}
          </span>
        </div>
        <div class="h-1.5 overflow-hidden bg-accent dark:bg-foreground">
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
