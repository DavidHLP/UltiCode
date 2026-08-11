export { default as ChartCrosshair } from "./ChartCrosshair.vue";
export { default as ChartLegend } from "./ChartLegend.vue";
export { default as ChartSingleTooltip } from "./ChartSingleTooltip.vue";
export { default as ChartTooltip } from "./ChartTooltip.vue";

export function defaultColors(count: number = 3) {
  return Array.from(
    { length: count },
    (_, index) => `var(--chart-series-${(index % 8) + 1})`,
  );
}

export * from "./interface";
