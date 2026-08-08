<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";
import type { ECharts } from "echarts";
import { Clock, Microchip } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import {
  buildDistributionChartOption,
  DEFAULT_CHART_FALLBACKS,
  formatMemory,
  formatPercentile,
  formatRuntime,
  readChartCssColors,
  renderAvatarMarkPointDataUrl,
  type DistributionChartPoint,
} from "./submissionChartOptions";

type ActiveChart = "runtime" | "memory";

interface Props {
  runtimePoints: DistributionChartPoint[];
  totalRuntimeCount: number;
  runtimeHighlightIndex: number;
  memoryPoints: DistributionChartPoint[];
  totalMemoryCount: number;
  memoryHighlightIndex: number;
  runtime: number | undefined;
  memory: number | undefined;
  runtimePercentile: number | undefined;
  memoryPercentile: number | undefined;
  avatarUrl: string | undefined;
}

const props = defineProps<Props>();

const { t } = useI18n();

const activeChart = ref<ActiveChart>("runtime");
const showRuntimeDetail = computed(() => activeChart.value === "runtime");
const showMemoryDetail = computed(() => activeChart.value === "memory");

const runtimeChartRef = ref<HTMLDivElement | null>(null);
const memoryChartRef = ref<HTMLDivElement | null>(null);
let runtimeChart: ECharts | null = null;
let memoryChart: ECharts | null = null;

let runtimeResizeObserver: ResizeObserver | null = null;
let memoryResizeObserver: ResizeObserver | null = null;

let renderGeneration = 0;

function disposeChart(chart: ECharts | null) {
  if (!chart) return;
  chart.dispose();
}

function attachResizeObserver(
  el: HTMLDivElement | null,
  chart: ECharts | null,
): ResizeObserver | null {
  if (!el || !chart) return null;
  if (typeof ResizeObserver === "undefined") return null;
  const observer = new ResizeObserver(() => {
    chart.resize();
  });
  observer.observe(el);
  return observer;
}

async function applyAvatarMarkPoint(
  chart: ECharts,
  userIndex: number,
  paired: { count: number }[],
  avatarUrl: string,
  generation: number,
) {
  const colors = readChartCssColors(DEFAULT_CHART_FALLBACKS);
  let circularAvatar: string | null = null;
  try {
    circularAvatar = await renderAvatarMarkPointDataUrl(avatarUrl, colors.accent);
  } catch {
    return;
  }
  if (generation !== renderGeneration) return;
  if (!circularAvatar) return;
  chart.setOption({
    series: [
      {
        markPoint: {
          data: [
            {
              coord: [userIndex, paired[userIndex]?.count ?? 0],
              symbol: "image://" + circularAvatar,
              symbolSize: 32,
              symbolOffset: [0, -20],
            },
          ],
        },
      },
    ],
  });
}

function initRuntimeChart() {
  if (!runtimeChartRef.value) return;
  disposeChart(runtimeChart);
  if (runtimeResizeObserver) {
    runtimeResizeObserver.disconnect();
    runtimeResizeObserver = null;
  }
  runtimeChart = echarts.init(runtimeChartRef.value);
  runtimeResizeObserver = attachResizeObserver(runtimeChartRef.value, runtimeChart);
  runtimeChart.setOption(
    buildDistributionChartOption(
      props.runtimePoints,
      props.totalRuntimeCount,
      props.runtimeHighlightIndex,
      "ms",
      t,
    ),
  );
  if (
    props.runtimeHighlightIndex >= 0 &&
    props.runtimeHighlightIndex < props.runtimePoints.length &&
    props.avatarUrl
  ) {
    const generation = renderGeneration;
    void applyAvatarMarkPoint(
      runtimeChart,
      props.runtimeHighlightIndex,
      props.runtimePoints,
      props.avatarUrl,
      generation,
    );
  }
}

function initMemoryChart() {
  if (!memoryChartRef.value) return;
  disposeChart(memoryChart);
  if (memoryResizeObserver) {
    memoryResizeObserver.disconnect();
    memoryResizeObserver = null;
  }
  memoryChart = echarts.init(memoryChartRef.value);
  memoryResizeObserver = attachResizeObserver(memoryChartRef.value, memoryChart);
  memoryChart.setOption(
    buildDistributionChartOption(
      props.memoryPoints,
      props.totalMemoryCount,
      props.memoryHighlightIndex,
      "MB",
      t,
    ),
  );
  if (
    props.memoryHighlightIndex >= 0 &&
    props.memoryHighlightIndex < props.memoryPoints.length &&
    props.avatarUrl
  ) {
    const generation = renderGeneration;
    void applyAvatarMarkPoint(
      memoryChart,
      props.memoryHighlightIndex,
      props.memoryPoints,
      props.avatarUrl,
      generation,
    );
  }
}

function selectChart(next: ActiveChart) {
  if (activeChart.value === next) return;
  activeChart.value = next;
  void nextTick(() => {
    if (next === "runtime") initRuntimeChart();
    else initMemoryChart();
  });
}

onMounted(() => {
  void nextTick(() => {
    initRuntimeChart();
    initMemoryChart();
  });
});

watch(
  [
    () => props.runtimePoints,
    () => props.memoryPoints,
    () => props.totalRuntimeCount,
    () => props.totalMemoryCount,
    () => props.avatarUrl,
    () => props.runtimeHighlightIndex,
    () => props.memoryHighlightIndex,
  ],
  () => {
    renderGeneration++;
    void nextTick(() => {
      initRuntimeChart();
      initMemoryChart();
    });
  },
);

watch(activeChart, () => {
  renderGeneration++;
  void nextTick(() => {
    initRuntimeChart();
    initMemoryChart();
  });
});

onBeforeUnmount(() => {
  renderGeneration++;
  if (runtimeResizeObserver) {
    runtimeResizeObserver.disconnect();
    runtimeResizeObserver = null;
  }
  if (memoryResizeObserver) {
    memoryResizeObserver.disconnect();
    memoryResizeObserver = null;
  }
  disposeChart(runtimeChart);
  disposeChart(memoryChart);
  runtimeChart = null;
  memoryChart = null;
});
</script>

<template>
  <div class="space-y-4" data-e2e-locator="submission-performance-panel">
    <div
      class="flex w-full flex-col gap-1.5 rounded-none border border-border bg-[var(--surface-sunken)]/35 p-2"
    >
      <div class="flex items-center justify-between gap-1.5">
        <div class="flex w-full flex-wrap gap-2">
          <div
            role="button"
            tabindex="0"
            data-testid="runtime-tab"
            class="group flex min-w-[240px] flex-1 cursor-pointer flex-col rounded-none border px-3 py-2 text-xs transition-colors"
            :class="
              showRuntimeDetail
                ? 'border-[var(--accent-electric)] bg-[color-mix(in_oklch,var(--accent-electric)_10%,transparent)] shadow-[inset_2px_0_0_var(--accent-electric)]'
                : 'border-border bg-card hover:border-[color-mix(in_oklch,var(--accent-electric)_45%,var(--border))] hover:bg-[var(--surface-sunken)]/50'
            "
            @click="selectChart('runtime')"
            @keydown.enter.prevent="selectChart('runtime')"
            @keydown.space.prevent="selectChart('runtime')"
          >
            <div class="flex items-center justify-between gap-2">
              <div class="flex min-w-0 items-center gap-1.5 text-foreground">
                <Clock class="h-3 w-3" />
                <div class="min-w-0 flex-1 truncate text-xs font-medium">
                  {{ t("problem.submissions.runtimeDistribution") }}
                </div>
              </div>
              <span
                class="font-data text-2xs uppercase text-muted-foreground"
              >
                ms
              </span>
            </div>
            <div
              class="mt-2 flex flex-wrap items-baseline gap-x-2 gap-y-1 font-data tabular-nums"
            >
              <span class="font-semibold text-foreground">{{
                formatRuntime(props.runtime)
              }}</span>
              <span class="text-muted-foreground">{{
                t("problem.layout.beats", {
                  percent: formatPercentile(props.runtimePercentile),
                })
              }}</span>
            </div>
          </div>
          <div
            role="button"
            tabindex="0"
            data-testid="memory-tab"
            class="group flex min-w-[240px] flex-1 cursor-pointer flex-col rounded-none border px-3 py-2 text-xs transition-colors"
            :class="
              showMemoryDetail
                ? 'border-[var(--terminal-cyan)] bg-[color-mix(in_oklch,var(--terminal-cyan)_10%,transparent)] shadow-[inset_2px_0_0_var(--terminal-cyan)]'
                : 'border-border bg-card hover:border-[color-mix(in_oklch,var(--terminal-cyan)_45%,var(--border))] hover:bg-[var(--surface-sunken)]/50'
            "
            @click="selectChart('memory')"
            @keydown.enter.prevent="selectChart('memory')"
            @keydown.space.prevent="selectChart('memory')"
          >
            <div class="flex items-center justify-between gap-2">
              <div class="flex min-w-0 items-center gap-1.5 text-foreground">
                <Microchip class="h-3 w-3" />
                <div class="min-w-0 flex-1 truncate text-xs font-medium">
                  {{ t("problem.submissions.memoryDistribution") }}
                </div>
              </div>
              <span
                class="font-data text-2xs uppercase text-muted-foreground"
              >
                MB
              </span>
            </div>
            <div
              class="mt-2 flex flex-wrap items-baseline gap-x-2 gap-y-1 font-data tabular-nums"
            >
              <span class="font-medium text-foreground">{{
                formatMemory(props.memory)
              }}</span>
              <span class="text-muted-foreground">{{
                t("problem.layout.beats", {
                  percent: formatPercentile(props.memoryPercentile),
                })
              }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div
      v-show="showRuntimeDetail"
      class="rounded-none border border-border p-3"
    >
      <div
        class="h-48 w-full"
        ref="runtimeChartRef"
        data-testid="runtime-chart"
        style="touch-action: none; overflow: hidden; contain: content"
      ></div>
    </div>
    <div
      v-show="showMemoryDetail"
      class="rounded-none border border-border p-3"
    >
      <div
        class="h-48 w-full"
        ref="memoryChartRef"
        data-testid="memory-chart"
        style="touch-action: none; overflow: hidden; contain: content"
      ></div>
    </div>
  </div>
</template>