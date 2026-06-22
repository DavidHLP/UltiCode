<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from "vue";
import * as echarts from "echarts";
import { useI18n } from "vue-i18n";
import { fetchSubmissionHistory } from "@/api/submission";
import type { SubmissionHistory } from "@/api/submission";
import {
  createAcceptedAreaGradient,
  withSafeChartAnimation,
} from "./chartOptions";

const { t } = useI18n();

interface Props {
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const chartRef = ref<HTMLDivElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
const historyData = ref<SubmissionHistory | null>(null);
const dataLoading = ref(true);

const hasData = computed(
  () =>
    historyData.value &&
    (historyData.value.monthly.length > 0 ||
      historyData.value.languages.length > 0),
);

const initChart = () => {
  if (!chartRef.value || !hasData.value) return;

  if (chartInstance) {
    chartInstance.dispose();
  }

  chartInstance = echarts.init(chartRef.value, undefined, {
    renderer: "canvas",
  });

  const months = historyData.value?.monthly.map((m) => m.month) || [];
  const submissionCounts = historyData.value?.monthly.map((m) => m.count) || [];
  const acceptedCounts =
    historyData.value?.monthly.map((m) => m.accepted) || [];

  const languages = historyData.value?.languages.map((l) => l.language) || [];
  const languageCounts = historyData.value?.languages.map((l) => l.count) || [];

  // Build series array
  const series: echarts.SeriesOption[] = [
    {
      name: t("personal.history.totalSubmissions"),
      type: "bar",
      data: submissionCounts,
      itemStyle: {
        color: "oklch(0.6149 0.1394 244.9 / 0.6)",
        borderRadius: 0,
      },
      emphasis: {
        itemStyle: {
          color: "oklch(0.6149 0.1394 244.9)",
        },
      },
    },
    {
      name: t("personal.history.accepted"),
      type: "line",
      data: acceptedCounts,
      smooth: true,
      symbol: "circle",
      symbolSize: 6,
      lineStyle: {
        width: 2,
        color: "oklch(0.6444 0.1508 118.6)",
      },
      itemStyle: {
        color: "oklch(0.6444 0.1508 118.6)",
      },
      areaStyle: {
        color: createAcceptedAreaGradient(),
      },
    },
  ];

  // Append a horizontal bar series for language distribution so the
  // submission-history card uses the same bar-chart visual language as
  // the rest of the dashboard (LearningProgress, etc.) rather than a
  // pie/donut chart that visually breaks the row.
  if (languages.length > 0) {
    series.push({
      name: t("personal.history.languageDistribution"),
      type: "bar",
      xAxisIndex: 1,
      yAxisIndex: 1,
      data: languageCounts,
      itemStyle: {
        color: "oklch(0.6545 0.1340 85.7 / 0.7)",
        borderRadius: 0,
      },
      emphasis: {
        itemStyle: {
          color: "oklch(0.6545 0.1340 85.7)",
        },
      },
      barCategoryGap: "40%",
    });
  }

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: "axis",
      backgroundColor: "oklch(0 0 0 / 0.8)",
      borderColor: "transparent",
      borderRadius: 0,
      textStyle: {
        color: "#fff",
      },
    },
    legend: {
      data: [
        t("personal.history.totalSubmissions"),
        t("personal.history.accepted"),
        ...(languages.length > 0
          ? [t("personal.history.languageDistribution")]
          : []),
      ],
      bottom: 0,
      textStyle: {
        color: "var(--muted-foreground)",
        fontSize: 11,
      },
    },
    grid: [
      {
        left: "3%",
        right: "55%",
        bottom: "15%",
        top: "10%",
        containLabel: true,
      },
      {
        left: "55%",
        right: "3%",
        bottom: "15%",
        top: "10%",
        containLabel: true,
      },
    ],
    xAxis: [
      {
        type: "category",
        data: months,
        axisLine: {
          lineStyle: {
            color: "var(--border)",
          },
        },
        axisLabel: {
          color: "var(--muted-foreground)",
          fontSize: 10,
          rotate: 45,
        },
      },
      {
        gridIndex: 1,
        type: "value",
        axisLine: {
          lineStyle: {
            color: "var(--border)",
          },
        },
        axisLabel: {
          color: "var(--muted-foreground)",
          fontSize: 10,
        },
        splitLine: {
          lineStyle: {
            color: "var(--border)",
            type: "dashed",
          },
        },
      },
    ],
    yAxis: [
      {
        type: "value",
        axisLine: {
          lineStyle: {
            color: "var(--border)",
          },
        },
        axisLabel: {
          color: "var(--muted-foreground)",
          fontSize: 10,
        },
        splitLine: {
          lineStyle: {
            color: "var(--border)",
            type: "dashed",
          },
        },
      },
      {
        gridIndex: 1,
        type: "category",
        data: languages,
        axisLine: {
          lineStyle: {
            color: "var(--border)",
          },
        },
        axisLabel: {
          color: "var(--muted-foreground)",
          fontSize: 10,
        },
      },
    ],
    series,
  };

  chartInstance.setOption(withSafeChartAnimation(option));
};

const handleResize = () => {
  chartInstance?.resize();
};

const loadData = async () => {
  dataLoading.value = true;
  try {
    historyData.value = await fetchSubmissionHistory();
  } catch (e) {
    console.error("Failed to load submission history", e);
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

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
  chartInstance?.dispose();
  chartInstance = null;
});
</script>

<template>
  <div class="relative">
    <div
      v-if="props.loading || dataLoading"
      class="flex items-center justify-center h-[230px]"
    >
      <div
        class="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"
      ></div>
    </div>
    <div
      v-else-if="!hasData"
      class="flex flex-col items-center justify-center h-[230px] text-center"
    >
      <p class="text-sm text-muted-foreground">
        {{ t("personal.history.noHistory") }}
      </p>
    </div>
    <div v-else ref="chartRef" class="h-[230px] w-full"></div>
  </div>
</template>
