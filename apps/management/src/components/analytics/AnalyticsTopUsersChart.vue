<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import { IconChartBar } from '@tabler/icons-vue'
import { readCssColor, SOLARIZED_PALETTE } from '@ulticode/design-system'
import { useColorTheme } from '@/shared/theme/src'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  createTopUsersChartOption,
  type TopUserChartColors,
  type TopUserChartDatum,
} from './topUsersChart'

use([BarChart, GridComponent, TooltipComponent, SVGRenderer])

const props = defineProps<{
  data: TopUserChartDatum[]
}>()

const { t, locale } = useI18n()
const { theme } = useColorTheme()
const themeRevision = ref(0)

let themeObserver: MutationObserver | undefined

const colors = computed<TopUserChartColors>(() => {
  void theme.value
  void themeRevision.value

  return {
    accent: readCssColor('--chart-series-1', SOLARIZED_PALETTE.blue),
    accentMuted: readCssColor('--chart-series-2', SOLARIZED_PALETTE.cyan),
    axis: readCssColor('--foreground', SOLARIZED_PALETTE.base0),
    border: readCssColor('--chart-tooltip-border', SOLARIZED_PALETTE.base01),
    card: readCssColor('--chart-tooltip-background', SOLARIZED_PALETTE.base02),
    foreground: readCssColor('--foreground-strong', SOLARIZED_PALETTE.base1),
  }
})

const option = computed(() => {
  void locale.value
  return createTopUsersChartOption(props.data, colors.value, (count) =>
    t('analytics.userActivity.logins', { count }),
  )
})

onMounted(() => {
  themeObserver = new MutationObserver(() => {
    themeRevision.value += 1
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class'],
  })
})

onUnmounted(() => {
  themeObserver?.disconnect()
})
</script>

<template>
  <Card
    class="border border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/60 bg-card shadow-float overflow-hidden flex flex-col gap-0 py-0 rounded-none h-full"
  >
    <CardHeader
      class="px-4 py-4 bg-[var(--surface)] dark:bg-[var(--surface-highlight)]/10 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/50"
    >
      <div class="flex items-center gap-2">
        <IconChartBar class="h-4 w-4 text-[var(--accent-primary)]" />
        <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">
          {{ t('analytics.userActivity.topUsers') }}
        </CardTitle>
      </div>
      <CardDescription class="text-xs text-[var(--foreground-muted)] mt-1">
        {{ t('analytics.userActivity.topUsersDesc') }}
      </CardDescription>
    </CardHeader>
    <CardContent class="p-4">
      <div
        v-if="data.length === 0"
        class="h-[200px] flex items-center justify-center text-sm text-[var(--foreground-muted)]"
      >
        {{ t('analytics.noData') }}
      </div>
      <VChart
        v-else
        :option="option"
        autoresize
        class="w-full"
        style="height: 200px; min-height: 200px"
        :aria-label="t('analytics.userActivity.topUsers')"
      />
    </CardContent>
  </Card>
</template>
