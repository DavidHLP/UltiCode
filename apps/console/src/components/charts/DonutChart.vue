<script setup lang="ts" generic="T extends Record<string, unknown>">
import { computed } from "vue";
import { Donut } from "@unovis/ts";
import { VisDonut, VisSingleContainer } from "@unovis/vue";
import {
  ChartContainer,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  componentToString,
  type ChartConfig,
} from "@ulticode/design-system/ui/chart";
const props = withDefaults(
  defineProps<{
    data: T[];
    index: keyof T & string;
    category: keyof T & string;
    colors?: string[];
    showLegend?: boolean;
    showTooltip?: boolean;
    valueFormatter?: (value: number) => string;
  }>(),
  {
    showLegend: true,
    showTooltip: true,
    valueFormatter: (value: number) => String(value),
  },
);
const data = computed(() =>
  props.data.map((item, index) => ({
    key: `segment-${index}`,
    label: String(item[props.index] ?? ""),
    count: Number.isFinite(Number(item[props.category]))
      ? Math.max(0, Number(item[props.category]))
      : 0,
    color: props.colors?.[index] ?? `var(--chart-${Math.min(index + 1, 5)})`,
  })),
);
const config = computed<ChartConfig>(() =>
  Object.fromEntries(
    data.value.map((item) => [
      item.key,
      { label: item.label, color: item.color },
    ]),
  ),
);
const tooltip = componentToString(
  { count: { label: "" } },
  ChartTooltipContent,
  { labelKey: "label", hideIndicator: true },
);
</script>
<template>
  <figure class="flex w-full flex-col gap-3">
    <ChartContainer :config="config" class="h-40">
      <VisSingleContainer :data="data">
        <VisDonut
          :value="(item: (typeof data)[number]) => item.count"
          :color="(item: (typeof data)[number]) => item.color"
          :arc-width="20"
          :pad-angle="0.03"
        />
        <ChartTooltip
          v-if="showTooltip"
          :triggers="{ [Donut.selectors.segment]: tooltip }"
        />
      </VisSingleContainer>
      <ChartLegendContent v-if="showLegend" />
    </ChartContainer>
    <figcaption
      class="flex flex-wrap justify-center gap-3 text-sm tabular-nums"
    >
      <span v-for="item in data" :key="item.key"
        >{{ item.label }}: {{ valueFormatter(item.count) }}</span
      >
    </figcaption>
  </figure>
</template>
