<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from "vue";
import * as echarts from "echarts";
import type { UserSkill } from "@/types/userStats";
import { useI18n } from "vue-i18n";
import { readCssColor, SOLARIZED_PALETTE } from "@ulticode/design-system";
import { withSafeChartAnimation } from "./chartOptions";

const { t } = useI18n();

interface Props {
  skills: UserSkill[];
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const chartRef = ref<HTMLDivElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
let themeObserver: MutationObserver | null = null;

const hasSkills = computed(() => props.skills && props.skills.length > 0);

const initChart = () => {
  if (!chartRef.value || !hasSkills.value) return;

  if (chartInstance) {
    chartInstance.dispose();
  }

  chartInstance = echarts.init(chartRef.value, undefined, {
    renderer: "canvas",
  });

  const colors = {
    series: readCssColor("--chart-series-1", SOLARIZED_PALETTE.blue),
    background: readCssColor(
      "--chart-tooltip-background",
      SOLARIZED_PALETTE.base3,
    ),
    surface: readCssColor("--surface-highlight", SOLARIZED_PALETTE.base2),
    foreground: readCssColor("--foreground-strong", SOLARIZED_PALETTE.base01),
    muted: readCssColor("--foreground", SOLARIZED_PALETTE.base0),
    border: readCssColor("--chart-grid-color", SOLARIZED_PALETTE.base1),
    tooltipBorder: readCssColor(
      "--chart-tooltip-border",
      SOLARIZED_PALETTE.base0,
    ),
  };

  const indicators = props.skills.map((skill) => ({
    name: skill.tagName,
    max: Math.max(...props.skills.map((s) => s.count)) * 1.2,
  }));

  const values = props.skills.map((skill) => skill.count);

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: "item",
      backgroundColor: colors.background,
      borderColor: colors.tooltipBorder,
      borderRadius: 0,
      textStyle: {
        color: colors.foreground,
      },
      formatter: (params: unknown) => {
        const data = params as {
          value: number[];
          indicator: { name: string }[];
        };
        if (!data.value || !data.indicator) return "";
        return data.indicator
          .map((ind, idx) => `${ind.name}: ${data.value[idx]}`)
          .join("<br/>");
      },
    },
    radar: {
      indicator: indicators,
      shape: "polygon",
      splitNumber: 4,
      axisName: {
        color: colors.muted,
        fontSize: 11,
        fontWeight: 500,
      },
      splitLine: {
        lineStyle: {
          color: colors.border,
        },
      },
      splitArea: {
        areaStyle: {
          color: [colors.background, colors.surface],
        },
      },
      axisLine: {
        lineStyle: {
          color: colors.border,
        },
      },
    },
    series: [
      {
        type: "radar",
        data: [
          {
            value: values,
            name: t("personal.skills.solvedByTag"),
            symbol: "circle",
            symbolSize: 6,
            lineStyle: {
              width: 2,
              color: colors.series,
            },
            areaStyle: {
              color: colors.series,
              opacity: 0.2,
            },
            itemStyle: {
              color: colors.series,
              borderColor: colors.background,
              borderWidth: 2,
            },
          },
        ],
      },
    ],
  };

  chartInstance.setOption(withSafeChartAnimation(option));
};

const handleResize = () => {
  chartInstance?.resize();
};

onMounted(() => {
  if (hasSkills.value) {
    initChart();
  }
  window.addEventListener("resize", handleResize);
  themeObserver = new MutationObserver(() => initChart());
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class"],
  });
});

watch(
  () => props.skills,
  () => {
    if (hasSkills.value) {
      initChart();
    } else {
      chartInstance?.dispose();
      chartInstance = null;
    }
  },
  { deep: true, flush: "post" },
);

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
  themeObserver?.disconnect();
  themeObserver = null;
  chartInstance?.dispose();
  chartInstance = null;
});
</script>

<template>
  <div class="relative">
    <div v-if="loading" class="flex items-center justify-center h-[220px]">
      <div
        class="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"
      ></div>
    </div>
    <div
      v-else-if="!hasSkills"
      class="flex flex-col items-center justify-center h-[220px] text-center"
    >
      <p class="text-sm text-muted-foreground">
        {{ t("personal.skills.noSkills") }}
      </p>
    </div>
    <div v-else ref="chartRef" class="h-[220px] w-full"></div>
  </div>
</template>
