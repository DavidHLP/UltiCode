<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from "vue";
import * as echarts from "echarts";
import type { UserSkill } from "@/types/userStats";
import { useI18n } from "vue-i18n";
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

const hasSkills = computed(() => props.skills && props.skills.length > 0);

const initChart = () => {
  if (!chartRef.value || !hasSkills.value) return;

  if (chartInstance) {
    chartInstance.dispose();
  }

  chartInstance = echarts.init(chartRef.value, undefined, {
    renderer: "canvas",
  });

  const indicators = props.skills.map((skill) => ({
    name: skill.tagName,
    max: Math.max(...props.skills.map((s) => s.count)) * 1.2,
  }));

  const values = props.skills.map((skill) => skill.count);

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: "item",
      backgroundColor: "oklch(0 0 0 / 0.8)",
      borderColor: "transparent",
      borderRadius: 0,
      textStyle: {
        color: "#fff",
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
        color: "var(--muted-foreground)",
        fontSize: 11,
        fontWeight: 500,
      },
      splitLine: {
        lineStyle: {
          color: "var(--border)",
        },
      },
      splitArea: {
        areaStyle: {
          color: ["oklch(0.5 0 0 / 0.3)", "oklch(0.5 0 0 / 0.1)"],
        },
      },
      axisLine: {
        lineStyle: {
          color: "var(--border)",
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
              color: "oklch(0.6149 0.1394 244.9)",
            },
            areaStyle: {
              color: "oklch(0.6149 0.1394 244.9 / 0.2)",
            },
            itemStyle: {
              color: "oklch(0.6149 0.1394 244.9)",
              borderColor: "var(--background)",
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
