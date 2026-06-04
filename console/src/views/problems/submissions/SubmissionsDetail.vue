<script setup lang="ts">
import { computed, ref, onMounted, watch, nextTick } from "vue";
import { useRouter } from "vue-router";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import { Clock, Microchip, ArrowLeft, Loader2 } from "lucide-vue-next";
import * as echarts from "echarts";
import type { ECharts } from "echarts";
import { useI18n } from "vue-i18n";
import SubmissionTestResults from "./components/SubmissionTestResults.vue";
import SubmissionCodeBlock from "./components/SubmissionCodeBlock.vue";
import { useSubmissionDetail } from "./composables/useSubmissionDetail";

interface TooltipCallbackDataParams {
  dataIndex: number;
  value: number;
  [key: string]: unknown;
}

function formatMemory(bytes: number): string {
  if (bytes < 1024) return `${bytes} KB`;
  return `${(bytes / 1024).toFixed(1)} MB`;
}

const props = defineProps({
  submission: {
    type: Object as () => SubmissionRecord,
  } as const,
  statusMetaByKey: {
    type: Object as () => Record<string, SubmissionStatusMeta>,
    default: () => ({}),
  },
});

const emit = defineEmits<{
  (e: "back"): void;
}>();

const { t } = useI18n();
const router = useRouter();

const {
  statusMeta,
  statusLabel,
  statusToneClass,
  isAccepted,
  isCompileError,
  isPending,
  isStuck,
  pendingSeconds,
  showCaseDetails,
  showVerdictMeta,
  verdictDetail,
  codeMarkdown,
  distBins,
  distCounts,
  pairedDist,
  totalCount,
  highlightIndex,
  memoryDistBins,
  memoryDistCounts,
  pairedMemoryDist,
  totalMemoryCount,
  memoryHighlightIndex,
} = useSubmissionDetail(
  () => props.submission,
  () => props.statusMetaByKey,
);

// Chart state
const activeChart = ref<"runtime" | "memory">("runtime");
const showRuntimeDetail = computed(() => activeChart.value === "runtime");
const showMemoryDetail = computed(() => activeChart.value === "memory");

const runtimeChartRef = ref<HTMLDivElement>();
const memoryChartRef = ref<HTMLDivElement>();
let runtimeChart: ECharts | null = null;
let memoryChart: ECharts | null = null;

function buildChartOption(
  bins: number[],
  counts: number[],
  paired: { i: number; count: number; bin: number }[],
  total: number,
  userIndex: number,
  unit: string,
) {
  // userAvatar was unused
  "https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png";

  return {
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: unknown) => {
        const dataArray = params as TooltipCallbackDataParams[];
        const data = dataArray[0];
        if (!data) return "";
        const bin = bins[data.dataIndex];
        const count = counts[data.dataIndex] ?? 0;
        const percentage = total ? ((count / total) * 100).toFixed(2) : "0";
        const isUserPosition = data.dataIndex === userIndex;
        return `${bin}${unit}<br/>${t("problem.layout.count")}: ${count}<br/>${t("problem.layout.percentage")}: ${percentage}%${isUserPosition ? `<br/><span style="color: hsl(var(--chart-series-1));">${t("problem.layout.userPosition")}</span>` : ""}`;
      },
    },
    grid: {
      left: "3%",
      right: "4%",
      bottom: "15%",
      top: "15%",
      containLabel: true,
    },
    xAxis: {
      type: "category",
      data: bins.map((bin) => `${bin}${unit}`),
      axisLabel: {
        interval: Math.ceil(bins.length / 8),
        rotate: 0,
        fontSize: 10,
      },
      axisLine: { lineStyle: { color: "hsl(var(--border))" } },
    },
    yAxis: {
      type: "value",
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: "hsl(var(--border))" } },
      axisLabel: { fontSize: 10, color: "hsl(var(--muted-foreground))" },
    },
    series: [
      {
        type: "bar",
        data: paired.map((d, i) => ({
          value: d.count,
          itemStyle: {
            color:
              i === userIndex
                ? "hsl(var(--chart-series-1))"
                : "hsl(var(--muted-foreground) / 0.3)",
            borderRadius: [4, 4, 0, 0],
          },
        })),
        barMaxWidth: 40,
      },
    ],
  };
}

function addAvatarMarkPoint(
  chart: ECharts,
  userIndex: number,
  paired: { count: number }[],
  userAvatar: string,
) {
  const avatarImg = new Image();
  avatarImg.crossOrigin = "anonymous";
  avatarImg.src = userAvatar;
  avatarImg.onload = () => {
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const size = 28;
    canvas.width = size;
    canvas.height = size;
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
    ctx.closePath();
    ctx.clip();
    ctx.drawImage(avatarImg, 0, 0, size, size);
    ctx.restore();
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2 - 1, 0, Math.PI * 2);
    ctx.strokeStyle = "hsl(var(--chart-series-1))";
    ctx.lineWidth = 2;
    ctx.stroke();
    const circularAvatar = canvas.toDataURL();
    chart?.setOption({
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
  };
}

const initRuntimeChart = () => {
  if (!runtimeChartRef.value) return;
  if (runtimeChart) runtimeChart.dispose();
  runtimeChart = echarts.init(runtimeChartRef.value);
  runtimeChart.setOption(
    buildChartOption(
      distBins.value,
      distCounts.value,
      pairedDist.value,
      totalCount.value,
      highlightIndex.value,
      "ms",
    ),
  );
  if (highlightIndex.value >= 0) {
    addAvatarMarkPoint(
      runtimeChart,
      highlightIndex.value,
      pairedDist.value,
      props.submission?.user?.avatar ||
        "https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png",
    );
  }
};

const initMemoryChart = () => {
  if (!memoryChartRef.value) return;
  if (memoryChart) memoryChart.dispose();
  memoryChart = echarts.init(memoryChartRef.value);
  memoryChart.setOption(
    buildChartOption(
      memoryDistBins.value,
      memoryDistCounts.value,
      pairedMemoryDist.value,
      totalMemoryCount.value,
      memoryHighlightIndex.value,
      "MB",
    ),
  );
  if (memoryHighlightIndex.value >= 0) {
    addAvatarMarkPoint(
      memoryChart,
      memoryHighlightIndex.value,
      pairedMemoryDist.value,
      props.submission?.user?.avatar ||
        "https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png",
    );
  }
};

onMounted(() => {
  if (showRuntimeDetail.value) nextTick(() => initRuntimeChart());
  if (showMemoryDetail.value) nextTick(() => initMemoryChart());
});

watch(activeChart, (newVal) => {
  nextTick(() => {
    if (newVal === "runtime") initRuntimeChart();
    else initMemoryChart();
  });
});

const handleResubmit = () => {
  if (props.submission?.problem_id) {
    router.push({
      name: "problem-detail",
      params: { id: props.submission.problem_id },
      query: { resubmit: "true" },
    });
  }
};

const handleWriteSolution = () => {
  if (props.submission?.id) {
    router.push({
      name: "solution-create-from-submission",
      query: { submissionId: props.submission.id },
    });
  }
};
</script>

<template>
  <div
    v-if="props.submission"
    class="mx-auto flex w-full max-w-[700px] flex-col gap-4 px-3 py-2"
  >
    <!-- Header -->
    <div class="flex w-full items-center justify-between gap-3">
      <div class="flex flex-1 flex-col items-start gap-0.5 overflow-hidden">
        <div class="flex items-center gap-2 mb-1">
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-full hover:bg-muted"
            @click="emit('back')"
          >
            <ArrowLeft class="h-4 w-4" />
          </Button>
          <div
            class="flex flex-1 items-center gap-1.5 text-lg font-medium leading-tight"
            :class="statusToneClass"
          >
            <Loader2 v-if="isPending" class="h-4 w-4 animate-spin" />
            <span data-e2e-locator="submission-result">{{ statusLabel }}</span>
            <span
              v-if="isPending && pendingSeconds > 30"
              class="text-xs text-muted-foreground"
            >
              ({{ pendingSeconds }}s)
            </span>
          </div>
        </div>
        <div
          v-if="!isCompileError && !isPending"
          class="text-xs font-normal text-muted-foreground"
        >
          <span v-if="isAccepted">{{
            t("problem.submissions.allTestsPassed")
          }}</span>
          <span v-else>
            {{
              t("problem.submissions.testsPassed", {
                count:
                  props.submission?.tests?.filter(
                    (tc) => tc.status === "Accepted",
                  ).length ?? 0,
                total: props.submission?.tests?.length ?? 0,
              })
            }}
          </span>
        </div>
        <div class="flex items-center gap-2 mt-2 text-xs text-muted-foreground">
          <div class="flex items-center gap-1">
            <Avatar class="h-4 w-4">
              <AvatarImage
                :src="
                  props.submission.user?.avatar ||
                  'https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png'
                "
              />
              <AvatarFallback>U</AvatarFallback>
            </Avatar>
            <span class="font-medium text-foreground">{{
              props.submission.user?.username || "User"
            }}</span>
            <span class="text-muted-foreground/60">{{
              t("problem.submissions.submittedAt")
            }}</span>
            <span>{{
              new Date(
                props.submission.submittedAt ?? props.submission.created_at,
              ).toLocaleString()
            }}</span>
          </div>
        </div>
      </div>
      <div class="flex flex-none gap-2">
        <Button
          v-if="isAccepted"
          variant="default"
          size="sm"
          class="h-7 text-xs bg-[var(--terminal-green)] hover:bg-[var(--terminal-green)] text-[var(--background)]"
          @click="handleWriteSolution"
        >
          {{ t("problem.solutions.writeSolution") }}
        </Button>
        <Button
          v-if="isStuck"
          variant="outline"
          size="sm"
          class="h-7 text-xs"
          @click="handleResubmit"
        >
          {{ t("problem.submissions.resubmit") }}
        </Button>
      </div>
    </div>

    <!-- Stuck pending warning -->
    <div
      v-if="isPending && pendingSeconds > 120"
      class="rounded-none border border-[var(--terminal-amber)]/30 bg-[var(--terminal-amber)]/5 px-4 py-3 text-xs text-[var(--terminal-amber)]"
    >
      {{ t("problem.submissions.stuckWarning") }}
    </div>

    <!-- Verdict info -->
    <div
      v-if="showVerdictMeta"
      class="rounded-none border border-border bg-muted/40 px-4 py-3 text-xs"
    >
      <div class="text-xs font-medium text-muted-foreground">
        {{ t("problem.submissions.verdictInfo") }}
      </div>
      <div v-if="statusMeta?.description" class="mt-2 text-sm text-foreground">
        {{ statusMeta.description }}
      </div>
      <div
        v-if="verdictDetail"
        class="mt-2 rounded-none bg-muted px-3 py-2 font-mono text-xs text-foreground"
      >
        {{ verdictDetail }}
      </div>
      <div
        v-if="statusMeta?.suggestion"
        class="mt-2 text-xs text-muted-foreground"
      >
        {{ t("problem.submissions.suggestion") }}: {{ statusMeta.suggestion }}
      </div>
    </div>

    <!-- Compile Error -->
    <div
      v-if="isCompileError"
      class="rounded-none bg-[var(--terminal-red)]/10 border border-[var(--terminal-red)]/30 p-4"
    >
      <h3 class="font-medium text-[var(--terminal-red)] text-sm mb-2">
        {{ t("problem.submissions.compileError") }}
      </h3>
      <pre
        class="whitespace-pre-wrap text-sm font-mono text-[var(--terminal-red)] bg-transparent p-0"
        >{{
          props.submission.compiler_error ||
          t("problem.submissions.noErrorMessage")
        }}</pre
      >
    </div>

    <!-- Failure details -->
    <SubmissionTestResults
      v-else-if="showCaseDetails"
      :submission="props.submission"
    />

    <!-- Accepted (Charts) -->
    <div v-else-if="isAccepted" class="space-y-4">
      <div
        class="flex w-full flex-col gap-1.5 rounded-none border border-border p-2"
      >
        <div class="flex items-center justify-between gap-1.5">
          <div class="flex w-full flex-wrap gap-2">
            <div
              class="rounded-none group flex min-w-[240px] flex-1 cursor-pointer flex-col px-3 py-2 text-xs transition hover:opacity-100"
              :class="showRuntimeDetail ? 'bg-accent' : 'opacity-40'"
              @click="activeChart = 'runtime'"
            >
              <div class="flex justify-between gap-1.5">
                <div class="flex items-center gap-1 text-foreground">
                  <Clock class="h-3 w-3" />
                  <div class="flex-1 text-xs">
                    {{ t("problem.submissions.runtimeDistribution") }}
                  </div>
                </div>
              </div>
              <div class="mt-1.5 flex items-center gap-1">
                <span class="font-medium text-foreground"
                  >{{
                    props.submission?.runtime.toString().replace("ms", "")
                  }}
                  ms</span
                >
                <span class="text-muted-foreground">{{
                  t("problem.layout.beats", {
                    percent: (props.submission?.runtimePercentile ?? 0).toFixed(
                      1,
                    ),
                  })
                }}</span>
              </div>
            </div>
            <div
              class="rounded-none group flex min-w-[240px] flex-1 cursor-pointer flex-col px-3 py-2 text-xs transition hover:opacity-100"
              :class="showMemoryDetail ? 'bg-accent' : 'opacity-40'"
              @click="activeChart = 'memory'"
            >
              <div class="flex justify-between gap-1.5">
                <div class="flex items-center gap-1 text-foreground">
                  <Microchip class="h-3 w-3" />
                  <div class="flex-1 text-xs">
                    {{ t("problem.submissions.memoryDistribution") }}
                  </div>
                </div>
              </div>
              <div class="mt-1.5 flex items-center gap-1">
                <span class="font-medium text-foreground">{{
                  formatMemory(props.submission?.memory)
                }}</span>
                <span class="text-muted-foreground">{{
                  t("problem.layout.beats", {
                    percent: (props.submission?.memoryPercentile ?? 0).toFixed(
                      1,
                    ),
                  })
                }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div
        v-if="showRuntimeDetail"
        class="rounded-none border border-border p-3"
      >
        <div
          class="h-48 w-full"
          ref="runtimeChartRef"
          style="touch-action: none; overflow: hidden; contain: content"
        ></div>
      </div>
      <div
        v-if="showMemoryDetail"
        class="rounded-none border border-border p-3"
      >
        <div
          class="h-48 w-full"
          ref="memoryChartRef"
          style="touch-action: none; overflow: hidden; contain: content"
        ></div>
      </div>
    </div>

    <div
      v-else-if="!showVerdictMeta"
      class="rounded-none border border-dashed border-border bg-muted/30 px-4 py-3 text-xs text-muted-foreground"
    >
      {{ t("problem.submissions.detailsNotAvailable") }}
    </div>

    <!-- Code Section -->
    <SubmissionCodeBlock :code-markdown="codeMarkdown" />
  </div>
</template>
