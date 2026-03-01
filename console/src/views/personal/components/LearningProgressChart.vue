<script setup lang="ts">
import { ref, onMounted, watch, computed } from "vue";
import * as echarts from "echarts";
import { useI18n } from "vue-i18n";
import { fetchLearningProgress } from "@/api/submission";
import type { LearningProgress } from "@/api/submission";

const { t } = useI18n();

interface Props {
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const chartRef = ref<HTMLDivElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
const progressData = ref<LearningProgress | null>(null);
const dataLoading = ref(true);

const hasData = computed(
  () =>
    progressData.value &&
    (progressData.value.weeklyProgress.length > 0 ||
      progressData.value.difficultyProgress.length > 0),
);

const initChart = () => {
  if (!chartRef.value || !hasData.value) return;

  if (chartInstance) {
    chartInstance.dispose();
  }

  chartInstance = echarts.init(chartRef.value, undefined, {
    renderer: "canvas",
  });

  const weeks = progressData.value?.weeklyProgress.map((w) => w.week) || [];
  const problemsSolved =
    progressData.value?.weeklyProgress.map((w) => w.solved) || [];
  const timeSpent =
    progressData.value?.weeklyProgress.map((w) => w.timeSpent) || [];

  // Build series array
  const series: echarts.SeriesOption[] = [
    {
      name: t("personal.learning.problemsSolved"),
      type: "bar",
      data: problemsSolved,
      itemStyle: {
        color: "hsl(var(--primary) / 0.7)",
        borderRadius: [4, 4, 0, 0],
      },
      emphasis: {
        itemStyle: {
          color: "hsl(var(--primary))",
        },
      },
    },
    {
      name: t("personal.learning.timeSpent"),
      type: "line",
      yAxisIndex: 1,
      data: timeSpent,
      smooth: true,
      symbol: "circle",
      symbolSize: 6,
      lineStyle: {
        width: 2,
        color: "hsl(var(--chart-3))",
      },
      itemStyle: {
        color: "hsl(var(--chart-3))",
      },
    },
  ];

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: "axis",
      backgroundColor: "rgba(0, 0, 0, 0.8)",
      borderColor: "transparent",
      textStyle: {
        color: "#fff",
      },
      axisPointer: {
        type: "cross",
      },
    },
    legend: {
      data: [
        t("personal.learning.problemsSolved"),
        t("personal.learning.timeSpent"),
      ],
      bottom: 0,
      textStyle: {
        color: "hsl(var(--muted-foreground))",
        fontSize: 11,
      },
    },
    grid: {
      left: "3%",
      right: "5%",
      bottom: "15%",
      top: "10%",
      containLabel: true,
    },
    xAxis: {
      type: "category",
      data: weeks,
      axisLine: {
        lineStyle: {
          color: "hsl(var(--border))",
        },
      },
      axisLabel: {
        color: "hsl(var(--muted-foreground))",
        fontSize: 10,
        rotate: 45,
      },
    },
    yAxis: [
      {
        type: "value",
        name: t("personal.learning.problemsSolved"),
        nameTextStyle: {
          color: "hsl(var(--muted-foreground))",
          fontSize: 10,
        },
        axisLine: {
          lineStyle: {
            color: "hsl(var(--border))",
          },
        },
        axisLabel: {
          color: "hsl(var(--muted-foreground))",
          fontSize: 10,
        },
        splitLine: {
          lineStyle: {
            color: "hsl(var(--border))",
            type: "dashed",
          },
        },
      },
      {
        type: "value",
        name: t("personal.learning.hours"),
        nameTextStyle: {
          color: "hsl(var(--muted-foreground))",
          fontSize: 10,
        },
        axisLine: {
          lineStyle: {
            color: "hsl(var(--border))",
          },
        },
        axisLabel: {
          color: "hsl(var(--muted-foreground))",
          fontSize: 10,
        },
        splitLine: {
          show: false,
        },
      },
    ],
    series,
  };

  chartInstance.setOption(option);
};

const handleResize = () => {
  chartInstance?.resize();
};

const loadData = async () => {
  dataLoading.value = true;
  try {
    progressData.value = await fetchLearningProgress();
  } catch (e) {
    console.error("Failed to load learning progress", e);
  } finally {
    dataLoading.value = false;
  }
};

onMounted(() => {
  loadData();
  window.addEventListener("resize", handleResize);
});

watch([hasData, dataLoading], () => {
  if (hasData.value && !dataLoading.value) {
    setTimeout(() => initChart(), 0);
  }
});
</script>

<template>
  <div class="relative">
    <div
      v-if="props.loading || dataLoading"
      class="flex items-center justify-center h-[280px]"
    >
      <div
        class="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"
      ></div>
    </div>
    <div
      v-else-if="!hasData"
      class="flex flex-col items-center justify-center h-[280px] text-center"
    >
      <p class="text-sm text-muted-foreground">
        {{ t("personal.learning.noProgress") }}
      </p>
    </div>
    <div v-else ref="chartRef" class="h-[280px] w-full"></div>
  </div>
</template>
