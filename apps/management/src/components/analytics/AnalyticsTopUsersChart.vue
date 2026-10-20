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

import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import { ChartBar as IconChartBar } from '@lucide/vue'

import { useChartPalette } from '@ulticode/design-system'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@ulticode/design-system/ui/card'
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
const chartPalette = useChartPalette()

const colors = computed<TopUserChartColors>(() => {
  const palette = chartPalette.value

  return {
    fontFamily: palette.fontFamily,
    accent: palette.series1,
    accentMuted: palette.series2,
    axis: palette.axis,
    border: palette.border,
    card: palette.background,
    foreground: palette.foreground,
  }
})

const option = computed(() => {
  void locale.value
  return createTopUsersChartOption(props.data, colors.value, (count) =>
    t('analytics.userActivity.logins', { count }),
  )
})
</script>

<template>
  <Card class="overflow-hidden flex flex-col gap-0 py-0 h-full">
    <CardHeader class="px-4 py-4">
      <div class="flex items-center gap-2">
        <IconChartBar class="h-4 w-4 text-primary" />
        <CardTitle>
          {{ t('analytics.userActivity.topUsers') }}
        </CardTitle>
      </div>
      <CardDescription class="mt-1">
        {{ t('analytics.userActivity.topUsersDesc') }}
      </CardDescription>
    </CardHeader>
    <CardContent class="p-4">
      <div
        v-if="data.length === 0"
        class="h-[200px] flex items-center justify-center text-sm text-muted-foreground"
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
      <Accordion v-if="data.length" type="single" collapsible class="mt-4">
        <AccordionItem value="data">
          <AccordionTrigger>{{ t('common.dataTable') }}</AccordionTrigger>
          <AccordionContent>
            <Table>
              <TableHeader
                ><TableRow
                  ><TableHead>{{ t('common.name') }}</TableHead
                  ><TableHead>{{ t('common.count') }}</TableHead></TableRow
                ></TableHeader
              >
              <TableBody
                ><TableRow v-for="user in data" :key="user.username"
                  ><TableCell>{{ user.username }}</TableCell
                  ><TableCell>{{ user.loginCount }}</TableCell></TableRow
                ></TableBody
              >
            </Table>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </CardContent>
  </Card>
</template>
