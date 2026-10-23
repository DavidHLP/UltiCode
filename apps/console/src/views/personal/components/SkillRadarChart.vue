<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from "vue";
import * as echarts from "echarts";
import { useResizeObserver } from "@vueuse/core";
import type { UserSkill } from "@/types/userStats";
import { useI18n } from "vue-i18n";
import { useChartPalette } from "@ulticode/design-system";
import { problemTagLabelKey } from "@/i18n/utils/problemTagLabel";
import { withSafeChartAnimation } from "./chartOptions";

const { t, locale } = useI18n();

const skillLabel = (skill: UserSkill) => {
  const key = problemTagLabelKey(skill.tagSlug);
  return key ? t(key) : skill.tagName;
};

interface Props {
  skills: UserSkill[];
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const chartRef = ref<HTMLDivElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
const chartPalette = useChartPalette();

const hasSkills = computed(() => props.skills && props.skills.length > 0);

const initChart = () => {
  if (!chartRef.value || !hasSkills.value) return;

  if (chartInstance) {
    chartInstance.dispose();
  }

  chartInstance = echarts.init(chartRef.value, undefined, {
    renderer: "canvas",
  });

  const colors = chartPalette.value;

  const indicators = props.skills.map((skill) => ({
    name: skillLabel(skill),
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
      confine: true,
      formatter: () => {
        const tooltip = document.createElement("div");
        for (const skill of props.skills) {
          const row = document.createElement("div");
          row.textContent = `${skillLabel(skill)}: ${skill.count}`;
          tooltip.append(row);
        }
        return tooltip;
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
              color: colors.series1,
            },
            areaStyle: {
              color: colors.series1,
              opacity: 0.2,
            },
            itemStyle: {
              color: colors.series1,
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

useResizeObserver(chartRef, handleResize);

onMounted(() => {
  if (hasSkills.value) {
    initChart();
  }
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

watch(chartPalette, () => initChart(), { flush: "post" });
watch(locale, () => initChart(), { flush: "post" });
watch(
  () => props.loading,
  () => initChart(),
  { flush: "post" },
);

onUnmounted(() => {
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
