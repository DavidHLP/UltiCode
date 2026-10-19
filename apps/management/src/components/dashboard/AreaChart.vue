<script setup lang="ts">
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@ulticode/design-system/ui/accordion'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@ulticode/design-system/ui/table'

import { ref, computed, watch } from 'vue'
import type { ChartConfig } from '@ulticode/design-system/ui/chart'
import {
  VisAxis,
  VisLine,
  VisXYContainer,
  VisScatter,
  VisArea,
  VisCrosshair,
  VisTooltip,
} from '@unovis/vue'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@ulticode/design-system/ui/card'
import { ChartContainer } from '@ulticode/design-system/ui/chart'
import { Button } from '@ulticode/design-system/ui/button'
import { useI18n } from 'vue-i18n'
import { ChartBar as IconChartBar } from '@lucide/vue'

import { filterChartDataByPeriod, formatChartDateTick, type TimePeriod } from './areaChartData'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@ulticode/design-system/ui/empty'

export interface ChartDataPoint {
  date: Date
  [key: string]: string | number | Date
}

const props = withDefaults(
  defineProps<{
    title?: string
    description?: string
    data?: ChartDataPoint[]
    seriesKeys?: string[]
    config?: ChartConfig
    period?: TimePeriod
    showPeriodSelector?: boolean
  }>(),
  {
    title: 'Area Chart - Interactive',
    description: 'Showing total visitors for the last 3 months',
    seriesKeys: () => ['mobile', 'desktop'],
    showPeriodSelector: true,
  },
)

const { t, locale } = useI18n()

const emit = defineEmits<{
  'update:period': [period: TimePeriod]
}>()

// Default chart data if not provided
const defaultData: ChartDataPoint[] = [
  { date: new Date('2024-04-01'), desktop: 222, mobile: 150 },
  { date: new Date('2024-04-02'), desktop: 97, mobile: 180 },
  { date: new Date('2024-04-03'), desktop: 167, mobile: 120 },
  { date: new Date('2024-04-04'), desktop: 242, mobile: 260 },
  { date: new Date('2024-04-05'), desktop: 373, mobile: 290 },
  { date: new Date('2024-04-06'), desktop: 301, mobile: 340 },
  { date: new Date('2024-04-07'), desktop: 245, mobile: 180 },
  { date: new Date('2024-04-08'), desktop: 409, mobile: 320 },
  { date: new Date('2024-04-09'), desktop: 59, mobile: 110 },
  { date: new Date('2024-04-10'), desktop: 261, mobile: 190 },
  { date: new Date('2024-04-11'), desktop: 327, mobile: 350 },
  { date: new Date('2024-04-12'), desktop: 292, mobile: 210 },
  { date: new Date('2024-04-13'), desktop: 342, mobile: 380 },
  { date: new Date('2024-04-14'), desktop: 137, mobile: 220 },
  { date: new Date('2024-04-15'), desktop: 120, mobile: 170 },
  { date: new Date('2024-04-16'), desktop: 138, mobile: 190 },
  { date: new Date('2024-04-17'), desktop: 446, mobile: 360 },
  { date: new Date('2024-04-18'), desktop: 364, mobile: 410 },
  { date: new Date('2024-04-19'), desktop: 243, mobile: 180 },
  { date: new Date('2024-04-20'), desktop: 89, mobile: 150 },
  { date: new Date('2024-04-21'), desktop: 137, mobile: 200 },
  { date: new Date('2024-04-22'), desktop: 224, mobile: 170 },
  { date: new Date('2024-04-23'), desktop: 138, mobile: 230 },
  { date: new Date('2024-04-24'), desktop: 387, mobile: 290 },
  { date: new Date('2024-04-25'), desktop: 215, mobile: 250 },
  { date: new Date('2024-04-26'), desktop: 75, mobile: 130 },
  { date: new Date('2024-04-27'), desktop: 383, mobile: 420 },
  { date: new Date('2024-04-28'), desktop: 122, mobile: 180 },
  { date: new Date('2024-04-29'), desktop: 315, mobile: 240 },
  { date: new Date('2024-04-30'), desktop: 454, mobile: 380 },
  { date: new Date('2024-05-01'), desktop: 165, mobile: 220 },
  { date: new Date('2024-05-02'), desktop: 293, mobile: 310 },
  { date: new Date('2024-05-03'), desktop: 247, mobile: 190 },
  { date: new Date('2024-05-04'), desktop: 385, mobile: 420 },
  { date: new Date('2024-05-05'), desktop: 481, mobile: 390 },
  { date: new Date('2024-05-06'), desktop: 498, mobile: 520 },
  { date: new Date('2024-05-07'), desktop: 388, mobile: 300 },
  { date: new Date('2024-05-08'), desktop: 149, mobile: 210 },
  { date: new Date('2024-05-09'), desktop: 227, mobile: 180 },
  { date: new Date('2024-05-10'), desktop: 293, mobile: 330 },
  { date: new Date('2024-05-11'), desktop: 335, mobile: 270 },
  { date: new Date('2024-05-12'), desktop: 197, mobile: 240 },
  { date: new Date('2024-05-13'), desktop: 197, mobile: 160 },
  { date: new Date('2024-05-14'), desktop: 448, mobile: 490 },
  { date: new Date('2024-05-15'), desktop: 473, mobile: 380 },
  { date: new Date('2024-05-16'), desktop: 338, mobile: 400 },
  { date: new Date('2024-05-17'), desktop: 499, mobile: 420 },
  { date: new Date('2024-05-18'), desktop: 315, mobile: 350 },
  { date: new Date('2024-05-19'), desktop: 235, mobile: 180 },
  { date: new Date('2024-05-20'), desktop: 177, mobile: 230 },
  { date: new Date('2024-05-21'), desktop: 82, mobile: 140 },
  { date: new Date('2024-05-22'), desktop: 81, mobile: 120 },
  { date: new Date('2024-05-23'), desktop: 252, mobile: 290 },
  { date: new Date('2024-05-24'), desktop: 294, mobile: 220 },
  { date: new Date('2024-05-25'), desktop: 201, mobile: 250 },
  { date: new Date('2024-05-26'), desktop: 213, mobile: 170 },
  { date: new Date('2024-05-27'), desktop: 420, mobile: 460 },
  { date: new Date('2024-05-28'), desktop: 233, mobile: 190 },
  { date: new Date('2024-05-29'), desktop: 78, mobile: 130 },
  { date: new Date('2024-05-30'), desktop: 340, mobile: 280 },
  { date: new Date('2024-05-31'), desktop: 178, mobile: 230 },
  { date: new Date('2024-06-01'), desktop: 178, mobile: 200 },
  { date: new Date('2024-06-02'), desktop: 470, mobile: 410 },
  { date: new Date('2024-06-03'), desktop: 103, mobile: 160 },
  { date: new Date('2024-06-04'), desktop: 439, mobile: 380 },
  { date: new Date('2024-06-05'), desktop: 88, mobile: 140 },
  { date: new Date('2024-06-06'), desktop: 294, mobile: 250 },
  { date: new Date('2024-06-07'), desktop: 323, mobile: 370 },
  { date: new Date('2024-06-08'), desktop: 385, mobile: 320 },
  { date: new Date('2024-06-09'), desktop: 438, mobile: 480 },
  { date: new Date('2024-06-10'), desktop: 155, mobile: 200 },
  { date: new Date('2024-06-11'), desktop: 92, mobile: 150 },
  { date: new Date('2024-06-12'), desktop: 492, mobile: 420 },
  { date: new Date('2024-06-13'), desktop: 81, mobile: 130 },
  { date: new Date('2024-06-14'), desktop: 426, mobile: 380 },
  { date: new Date('2024-06-15'), desktop: 307, mobile: 350 },
  { date: new Date('2024-06-16'), desktop: 371, mobile: 310 },
  { date: new Date('2024-06-17'), desktop: 475, mobile: 520 },
  { date: new Date('2024-06-18'), desktop: 107, mobile: 170 },
  { date: new Date('2024-06-19'), desktop: 341, mobile: 290 },
  { date: new Date('2024-06-20'), desktop: 408, mobile: 450 },
  { date: new Date('2024-06-21'), desktop: 169, mobile: 210 },
  { date: new Date('2024-06-22'), desktop: 317, mobile: 270 },
  { date: new Date('2024-06-23'), desktop: 480, mobile: 530 },
  { date: new Date('2024-06-24'), desktop: 132, mobile: 180 },
  { date: new Date('2024-06-25'), desktop: 141, mobile: 190 },
  { date: new Date('2024-06-26'), desktop: 434, mobile: 380 },
  { date: new Date('2024-06-27'), desktop: 448, mobile: 490 },
  { date: new Date('2024-06-28'), desktop: 149, mobile: 200 },
  { date: new Date('2024-06-29'), desktop: 103, mobile: 160 },
  { date: new Date('2024-06-30'), desktop: 446, mobile: 400 },
]

const chartData = computed(() => props.data || defaultData)

const defaultConfig: ChartConfig = {
  mobile: {
    label: 'Mobile',
    color: 'var(--chart-1)',
  },
  desktop: {
    label: 'Desktop',
    color: 'var(--chart-2)',
  },
}

const chartConfig = computed(() => props.config || defaultConfig)

const timePeriod = ref<TimePeriod>(props.period ?? '90d')

watch(
  () => props.period,
  (period) => {
    if (period) timePeriod.value = period
  },
)

function selectTimePeriod(period: TimePeriod) {
  timePeriod.value = period
  emit('update:period', period)
}

const timePeriods: { value: TimePeriod; label: string }[] = [
  { value: '7d', label: 'dashboard.timePeriod.last7Days' },
  { value: '30d', label: 'dashboard.timePeriod.last30Days' },
  { value: '90d', label: 'dashboard.timePeriod.last90Days' },
  { value: 'all', label: 'dashboard.timePeriod.allTime' },
]

const filterRange = computed(() => filterChartDataByPeriod(chartData.value, timePeriod.value))

const seriesKeys = computed(() => props.seriesKeys || ['mobile', 'desktop'])

type Data = (typeof chartData.value)[number]

const yAccessors = computed(() =>
  seriesKeys.value.map((key) => (d: Data) => (d[key] as number) || 0),
)

const getColor = (_: unknown, index: number) => {
  const key = seriesKeys.value[index]
  return key ? chartConfig.value[key]?.color || 'var(--chart-1)' : 'var(--chart-1)'
}

// Calculate dynamic yDomain to prevent line squishing
const yDomain = computed(() => {
  const data = filterRange.value
  if (!data || data.length === 0) return [0, 10]
  let max = 0
  for (const item of data) {
    for (const key of seriesKeys.value) {
      const val = Number(item[key])
      if (val > max) max = val
    }
  }
  // add a 20% margin to the top, and ensure at least [0, 5]
  const upper = Math.max(5, Math.ceil(max * 1.25))
  return [0, upper]
})

/**
 * Crosshair tooltip template. Renders a compact,  card
 * showing the hovered date and one row per series. Returning an empty
 * string hides the tooltip.
 */
function crosshairTemplate(d: Data) {
  if (!d || !(d.date instanceof Date) || Number.isNaN(d.date.getTime())) return ''
  const dateStr = d.date.toLocaleDateString(locale.value, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  })
  const rows = seriesKeys.value
    .map((key) => {
      const cfg = chartConfig.value[key]
      const label = typeof cfg?.label === 'string' ? cfg.label : key
      const color = cfg?.color || 'var(--chart-1)'
      const value = Number(d[key]) || 0
      return (
        `<div class="uc-chart-tooltip__row">` +
        `<span class="uc-chart-tooltip__dot" style="background:${color}"></span>` +
        `<span class="uc-chart-tooltip__label">${escapeHtml(String(label))}</span>` +
        `<span class="uc-chart-tooltip__value">${value.toLocaleString()}</span>` +
        `</div>`
      )
    })
    .join('')
  return (
    `<div class="uc-chart-tooltip">` +
    `<div class="uc-chart-tooltip__date">${escapeHtml(dateStr)}</div>` +
    `<div class="uc-chart-tooltip__divider"></div>` +
    rows +
    `</div>`
  )
}

/** Minimal HTML escape for tooltip strings (labels, dates). */
function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>

<template>
  <Card class="overflow-hidden gap-0 py-0 h-full flex flex-col">
    <CardHeader class="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
      <div class="space-y-1">
        <CardTitle>{{ props.title }}</CardTitle>
        <CardDescription>{{ props.description }}</CardDescription>
      </div>

      <!-- Time period selector - Segmented Control style -->
      <div
        v-if="props.showPeriodSelector"
        class="flex flex-wrap items-center gap-0.5 rounded-lg border border-border dark:border-border bg-accent p-0.5"
      >
        <Button
          v-for="period in timePeriods"
          :key="period.value"
          variant="ghost"
          :size="'sm'"
          class="px-3.5 cursor-pointer"
          :class="
            timePeriod === period.value
              ? 'bg-card text-primary font-bold shadow-sm border border-border dark:border-border'
              : 'text-muted-foreground hover:text-foreground hover:bg-border/50'
          "
          @click="selectTimePeriod(period.value)"
        >
          {{ t(period.label) }}
        </Button>
      </div>
    </CardHeader>

    <CardContent class="px-2 py-4 sm:px-5 flex-1 min-h-0">
      <!-- Empty State -->
      <div
        v-if="filterRange.length === 0"
        class="flex flex-col items-center justify-center h-[280px] w-full border border-dashed border-border dark:border-border bg-background/30 rounded-lg"
      >
        <Empty>
          <EmptyContent>
            <EmptyMedia variant="icon">
              <IconChartBar class="size-6" />
            </EmptyMedia>
            <EmptyHeader class="text-center mt-2">
              <EmptyTitle>
                {{ t('dashboard.chart.noDataTitle') }}
              </EmptyTitle>
              <EmptyDescription class="mt-1 max-w-xs mx-auto">
                {{ t('dashboard.chart.noDataDesc') }}
              </EmptyDescription>
            </EmptyHeader>
          </EmptyContent>
        </Empty>
      </div>

      <!-- Chart View -->
      <ChartContainer
        v-else
        :config="chartConfig"
        class="aspect-auto h-[260px] w-full"
        :cursor="true"
      >
        <VisXYContainer
          :data="filterRange"
          :margin="{ left: 0, right: 16, top: 10, bottom: 20 }"
          :y-domain="yDomain"
        >
          <!-- Semi-transparent area fill -->
          <VisArea
            :x="(d: Data) => d.date.getTime()"
            :y="yAccessors"
            :color="getColor"
            :opacity="0.14"
          />
          <!-- Precision thin lines -->
          <VisLine
            :x="(d: Data) => d.date.getTime()"
            :y="yAccessors"
            :color="getColor"
            :line-width="2"
          />
          <!-- Small data points -->
          <VisScatter
            v-for="(accessor, index) in yAccessors"
            :key="seriesKeys[index]"
            :x="(d: Data) => d.date.getTime()"
            :y="accessor"
            :color="getColor(undefined, index)"
            :size="8"
            stroke-color="var(--card)"
            :stroke-width="2"
          />
          <VisAxis
            type="x"
            :x="(d: Data) => d.date.getTime()"
            :tick-line="false"
            :domain-line="false"
            :grid-line="false"
            :num-ticks="6"
            :tick-text-adaptive-sets="true"
            :tick-format="(d: number, index: number) => formatChartDateTick(d, locale)"
          />
          <VisAxis
            type="y"
            :num-ticks="4"
            :tick-line="false"
            :domain-line="false"
            :grid-line="true"
            :grid-line-color="'color-mix(in oklch, var(--border) 45%, transparent)'"
            :tick-format="(d: number) => (Number.isInteger(d) ? d.toString() : d.toFixed(1))"
          />
          <VisTooltip />
          <!-- Vertical crosshair + tooltip showing the hovered data point -->
          <VisCrosshair
            :x="(d: Data) => d.date.getTime()"
            :y="yAccessors"
            :color="getColor"
            :template="crosshairTemplate"
            :hide-when-far-from-pointer="true"
            :hide-when-far-from-pointer-distance="80"
          />
        </VisXYContainer>
      </ChartContainer>
      <ul
        v-if="seriesKeys.length > 1"
        class="mt-4 flex flex-wrap gap-4 text-sm"
        :aria-label="props.title"
      >
        <li v-for="key in seriesKeys" :key="key" class="flex items-center gap-2">
          <span
            class="size-2 shrink-0 rounded-full"
            :style="{ background: chartConfig[key]?.color }"
            aria-hidden="true"
          />
          <span>{{ chartConfig[key]?.label ?? key }}: {{ filterRange.at(-1)?.[key] ?? 0 }}</span>
        </li>
      </ul>
      <Accordion v-if="filterRange.length" type="single" collapsible class="mt-4">
        <AccordionItem value="data">
          <AccordionTrigger>{{ t('common.dataTable') }}</AccordionTrigger>
          <AccordionContent>
            <Table>
              <TableHeader
                ><TableRow
                  ><TableHead>{{ t('common.date') }}</TableHead
                  ><TableHead v-for="key in seriesKeys" :key="key">{{
                    chartConfig[key]?.label ?? key
                  }}</TableHead></TableRow
                ></TableHeader
              >
              <TableBody
                ><TableRow v-for="row in filterRange" :key="row.date.toISOString()"
                  ><TableCell>{{ row.date.toLocaleDateString(locale) }}</TableCell
                  ><TableCell v-for="key in seriesKeys" :key="key">{{
                    row[key]
                  }}</TableCell></TableRow
                ></TableBody
              >
            </Table>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </CardContent>
  </Card>
</template>

<style scoped>
/* Unovis crosshair tooltip styling. The crosshair tooltip is rendered
   into the document body by the VisTooltip that VisCrosshair manages
   internally, so we use :deep to pierce the scoped boundary. */
:deep(.uc-chart-tooltip) {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  min-width: 12rem;
  padding: 0.5rem 0.625rem;
  font-family: var(--uc-font-ui);
  font-size: var(--uc-text-xs);
  color: var(--foreground);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-float);
  pointer-events: none;
}

:deep(.uc-chart-tooltip__date) {
  font-weight: var(--uc-font-weight-semibold);
  font-size: var(--uc-text-2xs);
  letter-spacing: 0.05em;
  color: var(--muted-foreground);
  text-transform: uppercase;
}

:deep(.uc-chart-tooltip__divider) {
  height: 1px;
  background: var(--border);
  margin: 0.125rem 0;
}

:deep(.uc-chart-tooltip__row) {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

:deep(.uc-chart-tooltip__dot) {
  display: inline-block;
  width: 0.5rem;
  height: 0.5rem;
  flex-shrink: 0;
  border-radius: 50%;
}

:deep(.uc-chart-tooltip__label) {
  flex: 1;
  color: var(--muted-foreground);
  font-size: var(--uc-text-xs);
}

:deep(.uc-chart-tooltip__value) {
  color: var(--foreground);
  font-weight: var(--uc-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}
</style>
