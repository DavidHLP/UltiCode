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

function formatRuntime(value: number | undefined): string {
  if (!Number.isFinite(value)) return "-- ms";
  return `${Math.round(value as number)} ms`;
}

function formatMemory(value: number | undefined): string {
  if (!Number.isFinite(value)) return "-- MB";
  const memoryMb = value as number;
  return `${memoryMb >= 100 ? Math.round(memoryMb) : memoryMb.toFixed(1)} MB`;
}

function formatPercentile(value: number | undefined): string {
  return Number.isFinite(value) ? (value as number).toFixed(1) : "0.0";
}

function readCssColor(name: string, fallback: string): string {
  if (typeof window === "undefined") return fallback;
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  return value || fallback;
}

function chartColors() {
  const foreground = readCssColor("--muted-foreground", "#839496");
  const border = readCssColor("--border", "#174652");
  const accent = readCssColor("--chart-series-1", "#268bd2");
  return {
    foreground,
    border,
    accent,
    mutedBar: echarts.color.modifyAlpha(foreground, 0.32),
  };
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
  statusLabel,
  statusDescription,
  statusSuggestion,
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
  pairedDist,
  totalCount,
  highlightIndex,
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
  paired: { i: number; count: number; bin: number }[],
  total: number,
  userIndex: number,
  unit: string,
) {
  const colors = chartColors();

  return {
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: unknown) => {
        const dataArray = params as TooltipCallbackDataParams[];
        const data = dataArray[0];
        if (!data) return "";
        const point = paired[data.dataIndex];
        if (!point || !Number.isFinite(point.bin)) return "";
        const count = point.count;
        const percentage = total ? ((count / total) * 100).toFixed(2) : "0";
        const isUserPosition = data.dataIndex === userIndex;
        return `${point.bin}${unit}<br/>${t("problem.layout.count")}: ${count}<br/>${t("problem.layout.percentage")}: ${percentage}%${isUserPosition ? `<br/><span style="color: var(--chart-series-1);">${t("problem.layout.userPosition")}</span>` : ""}`;
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
      data: paired.map((point) => `${point.bin}${unit}`),
      axisLabel: {
        interval: paired.length <= 8 ? 0 : Math.ceil(paired.length / 8),
        rotate: 0,
        fontSize: 10,
        color: colors.foreground,
        fontFamily:
          "JetBrains Mono, SF Mono, Roboto Mono, ui-monospace, monospace",
      },
      axisLine: { lineStyle: { color: colors.border } },
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: colors.border } },
      axisLabel: {
        fontSize: 10,
        color: colors.foreground,
        fontFamily:
          "JetBrains Mono, SF Mono, Roboto Mono, ui-monospace, monospace",
      },
    },
    series: [
      {
        type: "bar",
        data: paired.map((d, i) => ({
          value: d.count,
          itemStyle: {
            color: i === userIndex ? colors.accent : colors.mutedBar,
            borderRadius: 0,
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
    ctx.strokeStyle =
      getComputedStyle(document.documentElement)
        .getPropertyValue("--chart-series-1")
        .trim() || "var(--chart-series-1)";
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
      pairedDist.value,
      totalCount.value,
      highlightIndex.value,
      "ms",
    ),
  );
  if (
    highlightIndex.value >= 0 &&
    highlightIndex.value < pairedDist.value.length
  ) {
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
      pairedMemoryDist.value,
      totalMemoryCount.value,
      memoryHighlightIndex.value,
      "MB",
    ),
  );
  if (
    memoryHighlightIndex.value >= 0 &&
    memoryHighlightIndex.value < pairedMemoryDist.value.length
  ) {
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

watch(
  [activeChart, pairedDist, pairedMemoryDist, () => props.submission?.id],
  ([newVal]) => {
    nextTick(() => {
      if (newVal === "runtime") initRuntimeChart();
      else initMemoryChart();
    });
  },
);

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
            class="h-8 w-8 rounded-none hover:bg-muted"
            @click="emit('back')"
          >
            <ArrowLeft class="h-4 w-4" />
          </Button>
          <div
            class="flex flex-1 items-center gap-1.5 text-lg font-data font-semibold uppercase leading-tight tracking-wider"
            :class="statusToneClass"
          >
            <Loader2 v-if="isPending" class="h-4 w-4 animate-spin" />
            <span data-e2e-locator="submission-result">{{ statusLabel }}</span>
            <span
              v-if="isPending && pendingSeconds > 30"
              class="text-xs font-data text-muted-foreground tabular-nums"
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
          <span v-else class="font-data tabular-nums">
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
            <Avatar class="h-4 w-4 rounded-none">
              <AvatarImage
                class="rounded-none"
                :src="
                  props.submission.user?.avatar ||
                  'https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png'
                "
              />
              <AvatarFallback class="rounded-none">U</AvatarFallback>
            </Avatar>
            <span class="font-data font-medium text-foreground">{{
              props.submission.user?.name ||
              props.submission.user?.username ||
              "User"
            }}</span>
            <span class="text-muted-foreground/60">{{
              t("problem.submissions.submittedAt")
            }}</span>
            <span class="font-data tabular-nums">{{
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
          class="h-7 text-xs rounded-none bg-[var(--terminal-green)] hover:bg-[var(--terminal-green)] text-[var(--background)]"
          @click="handleWriteSolution"
        >
          {{ t("problem.solutions.writeSolution") }}
        </Button>
        <Button
          v-if="isStuck"
          variant="outline"
          size="sm"
          class="h-7 text-xs rounded-none"
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
      <div v-if="statusDescription" class="mt-2 text-sm text-foreground">
        {{ statusDescription }}
      </div>
      <div
        v-if="verdictDetail"
        class="mt-2 rounded-none bg-muted px-3 py-2 font-data text-xs text-foreground"
      >
        {{ verdictDetail }}
      </div>
      <div v-if="statusSuggestion" class="mt-2 text-xs text-muted-foreground">
        {{ t("problem.submissions.suggestion") }}: {{ statusSuggestion }}
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
        class="whitespace-pre-wrap text-sm font-data text-[var(--terminal-red)] bg-transparent p-0"
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
        class="flex w-full flex-col gap-1.5 rounded-none border border-border bg-[var(--surface-sunken)]/35 p-2"
      >
        <div class="flex items-center justify-between gap-1.5">
          <div class="flex w-full flex-wrap gap-2">
            <div
              role="button"
              tabindex="0"
              class="group flex min-w-[240px] flex-1 cursor-pointer flex-col rounded-none border px-3 py-2 text-xs transition-colors"
              :class="
                showRuntimeDetail
                  ? 'border-[var(--accent-electric)] bg-[color-mix(in_oklch,var(--accent-electric)_10%,transparent)] shadow-[inset_2px_0_0_var(--accent-electric)]'
                  : 'border-border bg-card hover:border-[color-mix(in_oklch,var(--accent-electric)_45%,var(--border))] hover:bg-[var(--surface-sunken)]/50'
              "
              @click="activeChart = 'runtime'"
              @keydown.enter.prevent="activeChart = 'runtime'"
              @keydown.space.prevent="activeChart = 'runtime'"
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
                  formatRuntime(props.submission?.runtime)
                }}</span>
                <span class="text-muted-foreground">{{
                  t("problem.layout.beats", {
                    percent: formatPercentile(
                      props.submission?.runtimePercentile,
                    ),
                  })
                }}</span>
              </div>
            </div>
            <div
              role="button"
              tabindex="0"
              class="group flex min-w-[240px] flex-1 cursor-pointer flex-col rounded-none border px-3 py-2 text-xs transition-colors"
              :class="
                showMemoryDetail
                  ? 'border-[var(--terminal-cyan)] bg-[color-mix(in_oklch,var(--terminal-cyan)_10%,transparent)] shadow-[inset_2px_0_0_var(--terminal-cyan)]'
                  : 'border-border bg-card hover:border-[color-mix(in_oklch,var(--terminal-cyan)_45%,var(--border))] hover:bg-[var(--surface-sunken)]/50'
              "
              @click="activeChart = 'memory'"
              @keydown.enter.prevent="activeChart = 'memory'"
              @keydown.space.prevent="activeChart = 'memory'"
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
                  formatMemory(props.submission?.memory)
                }}</span>
                <span class="text-muted-foreground">{{
                  t("problem.layout.beats", {
                    percent: formatPercentile(
                      props.submission?.memoryPercentile,
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
