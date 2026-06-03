<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ChartConfig } from '@/components/ui/chart'
import { VisAxis, VisLine, VisXYContainer, VisScatter } from '@unovis/vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer, ChartTooltip } from '@/components/ui/chart'
import { Button } from '@/components/ui/button'
import { useI18n } from 'vue-i18n'

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
  }>(),
  {
    title: 'Area Chart - Interactive',
    description: 'Showing total visitors for the last 3 months',
    seriesKeys: () => ['mobile', 'desktop'],
  },
)

const { t } = useI18n()

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
    color: 'var(--accent-primary)',
  },
  desktop: {
    label: 'Desktop',
    color: 'var(--silver-400)',
  },
}

const chartConfig = computed(() => props.config || defaultConfig)

// Time period selector (visual only initially)
type TimePeriod = '7d' | '30d' | '90d' | 'all'
const timePeriod = ref<TimePeriod>('90d')

const timePeriods: { value: TimePeriod; label: string }[] = [
  { value: '7d', label: 'dashboard.timePeriod.last7Days' },
  { value: '30d', label: 'dashboard.timePeriod.last30Days' },
  { value: '90d', label: 'dashboard.timePeriod.last90Days' },
  { value: 'all', label: 'dashboard.timePeriod.allTime' },
]

const filterRange = computed(() => {
  const data = chartData.value
  if (timePeriod.value === 'all') return data

  const referenceDate = new Date('2024-06-30')
  let daysToSubtract = 90
  if (timePeriod.value === '30d') {
    daysToSubtract = 30
  } else if (timePeriod.value === '7d') {
    daysToSubtract = 7
  }
  const startDate = new Date(referenceDate)
  startDate.setDate(startDate.getDate() - daysToSubtract)
  return data.filter((item) => {
    const date = new Date(item.date)
    return date >= startDate
  })
})

const seriesKeys = computed(() => props.seriesKeys || ['mobile', 'desktop'])

type Data = (typeof chartData.value)[number]

const getYValues = (d: Data) => {
  return seriesKeys.value.map((key) => (d[key] as number) || 0)
}

const getColors = () => {
  return seriesKeys.value.map((key) => {
    const config = chartConfig.value[key as keyof typeof chartConfig.value]
    return config?.color || 'var(--accent-primary)'
  })
}
</script>

<template>
  <Card
    class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] overflow-hidden shadow-float"
  >
    <CardHeader class="flex flex-row items-center justify-between space-y-0 pb-4 px-6 pt-6">
      <div class="space-y-1">
        <CardTitle class="text-lg font-medium tracking-tight">{{ props.title }}</CardTitle>
        <CardDescription class="text-xs text-[var(--silver-400)]">{{
          props.description
        }}</CardDescription>
      </div>

      <!-- Time period selector - precision style -->
      <div
        class="flex items-center gap-0.5 rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-transparent p-0.5"
      >
        <Button
          v-for="period in timePeriods"
          :key="period.value"
          :variant="timePeriod === period.value ? 'default' : 'ghost'"
          :size="'sm'"
          class="h-6 px-2.5 text-xs rounded-none font-data"
          :class="
            timePeriod === period.value
              ? 'bg-foreground text-background'
              : 'text-[var(--silver-400)] hover:text-foreground'
          "
          @click="timePeriod = period.value"
        >
          {{ t(period.label) }}
        </Button>
      </div>
    </CardHeader>

    <CardContent class="px-2 pb-6 pt-0 sm:px-6">
      <ChartContainer :config="chartConfig" class="aspect-auto h-[280px] w-full" :cursor="false">
        <VisXYContainer
          :data="filterRange"
          :margin="{ left: -10, right: 10, top: 10, bottom: 20 }"
          :y-domain="[0, 600]"
        >
          <!-- Precision thin lines only, no fill -->
          <VisLine :x="(d: Data) => d.date" :y="getYValues" :color="getColors" :line-width="1.5" />
          <!-- Small data points -->
          <VisScatter
            :x="(d: Data) => d.date"
            :y="getYValues"
            :color="getColors"
            :radius="3"
            :stroke-width="1"
          />
          <VisAxis
            type="x"
            :x="(d: Data) => d.date"
            :tick-line="false"
            :domain-line="false"
            :grid-line="false"
            :num-ticks="6"
            :tick-format="
              (d: number, index: number) => {
                const date = new Date(d)
                return date.toLocaleDateString('en-US', {
                  month: 'short',
                  day: 'numeric',
                })
              }
            "
          />
          <VisAxis
            type="y"
            :num-ticks="3"
            :tick-line="false"
            :domain-line="false"
            :grid-line="true"
            :grid-line-color="'var(--silver-200)'"
            :tick-format="(d: number) => d.toString()"
          />
          <ChartTooltip />
        </VisXYContainer>
      </ChartContainer>
    </CardContent>
  </Card>
</template>
