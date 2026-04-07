<script setup lang="ts">
import { computed, ref, onMounted, watch, nextTick } from "vue";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import MarkdownView from "@/components/markdown/MarkdownView.vue";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import { Clock, Microchip, ArrowLeft } from "lucide-vue-next";
import * as echarts from "echarts";
import type { ECharts } from "echarts";
import { useI18n } from "vue-i18n";

interface TooltipCallbackDataParams {
  dataIndex: number;
  value: number;
  [key: string]: unknown;
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

const parseMs = (value: string | number) => {
  if (typeof value === "number") return value;
  const m = /([0-9]+)\s*ms/.exec(value);
  return m ? Number(m[1]) : null;
};

const runtimeMs = computed(() =>
  props.submission ? parseMs(props.submission.runtime) : null,
);

const statusMeta = computed(() =>
  props.submission ? props.statusMetaByKey?.[props.submission.status] : null,
);

const statusLabel = computed(
  () => statusMeta.value?.label ?? props.submission?.status ?? "",
);

const statusToneClass = computed(() => {
  const severity = statusMeta.value?.severity ?? statusMeta.value?.category;
  switch (severity) {
    case "success":
      return "text-[var(--terminal-green)]";
    case "error":
      return "text-[var(--terminal-red)]";
    case "warning":
      return "text-[var(--terminal-amber)]";
    case "info":
      return "text-[var(--accent-electric)]";
    default:
      return props.submission?.status === "Accepted"
        ? "text-[var(--terminal-green)]"
        : "text-[var(--terminal-red)]";
  }
});

const isAccepted = computed(() => props.submission?.status === "Accepted");
const isCompileError = computed(
  () => props.submission?.status === "Compile Error",
);
const isPending = computed(() =>
  ["Pending", "Judging"].includes(props.submission?.status ?? ""),
);
const showCaseDetails = computed(
  () => !isAccepted.value && !isCompileError.value && !isPending.value,
);
const showVerdictMeta = computed(() => {
  if (isAccepted.value) return false;
  return (
    Boolean(statusMeta.value?.description) ||
    Boolean(statusMeta.value?.suggestion) ||
    Boolean(props.submission?.errorDetail)
  );
});

const verdictDetail = computed(() =>
  isCompileError.value ? null : props.submission?.errorDetail,
);

const distBins = computed<number[]>(
  () => props.submission?.runtimeDistBinsMs?.map((b) => b.min) ?? [],
);
const distCounts = computed<number[]>(
  () => props.submission?.runtimeDistBinsMs?.map((b) => b.count) ?? [],
);
const distLength = computed(() =>
  Math.min(distCounts.value.length, distBins.value.length),
);
const pairedDist = computed(() =>
  Array.from({ length: distLength.value }, (_, i) => ({
    i,
    count: distCounts.value[i]!,
    bin: distBins.value[i]!,
  })),
);
const totalCount = computed(() =>
  pairedDist.value.reduce(
    (acc, d) => acc + (Number.isFinite(d.count) ? d.count : 0),
    0,
  ),
);

const highlightIndex = computed(() => {
  const bins = distBins.value;
  const val = runtimeMs.value;
  if (!Array.isArray(bins) || bins.length === 0 || val == null) return -1;
  const v = val as number;
  let closest = 0;
  let best = Math.abs((bins[0] ?? v) - v);
  for (let i = 1; i < bins.length; i++) {
    const bi = bins[i] ?? v;
    const d = Math.abs(bi - v);
    if (d < best) {
      best = d;
      closest = i;
    }
  }
  return closest;
});

const activeChart = ref<"runtime" | "memory">("runtime");

const showRuntimeDetail = computed(() => activeChart.value === "runtime");
const showMemoryDetail = computed(() => activeChart.value === "memory");

const runtimeChartRef = ref<HTMLDivElement>();
const memoryChartRef = ref<HTMLDivElement>();
let runtimeChart: ECharts | null = null;
let memoryChart: ECharts | null = null;

const initRuntimeChart = () => {
  if (!runtimeChartRef.value) return;

  if (runtimeChart) {
    runtimeChart.dispose();
  }

  runtimeChart = echarts.init(runtimeChartRef.value);

  const userIndex = highlightIndex.value;
  // Determine avatar URL: use dynamic user avatar or fallback to default
  const userAvatar =
    props.submission?.user?.avatar ||
    "https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png";

  // 创建圆形头像 Image 对象
  const avatarImg = new Image();
  avatarImg.crossOrigin = "anonymous";
  avatarImg.src = userAvatar;

  const option = {
    tooltip: {
      trigger: "axis",
      axisPointer: {
        type: "shadow",
      },
      formatter: (params: unknown) => {
        const dataArray = params as TooltipCallbackDataParams[];
        const data = dataArray[0];
        if (!data) return "";
        const bin = distBins.value[data.dataIndex];
        const count = distCounts.value[data.dataIndex] ?? 0;
        const total = totalCount.value;
        const percentage = total ? ((count / total) * 100).toFixed(2) : "0";
        const isUserPosition = data.dataIndex === userIndex;
        return `${bin}ms<br/>${t("problem.layout.count")}: ${count}<br/>${t("problem.layout.percentage")}: ${percentage}%${isUserPosition ? `<br/><span style="color: hsl(var(--chart-series-1));">${t("problem.layout.userPosition")}</span>` : ""}`;
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
      data: distBins.value.map((bin) => `${bin}ms`),
      axisLabel: {
        interval: Math.ceil(distBins.value.length / 8),
        rotate: 0,
        fontSize: 10,
      },
      axisLine: {
        lineStyle: {
          color: "hsl(var(--border))",
        },
      },
    },
    yAxis: {
      type: "value",
      axisLine: {
        show: false,
      },
      axisTick: {
        show: false,
      },
      splitLine: {
        lineStyle: {
          color: "hsl(var(--border))",
        },
      },
      axisLabel: {
        fontSize: 10,
        color: "hsl(var(--muted-foreground))",
      },
    },
    series: [
      {
        type: "bar",
        data: pairedDist.value.map((d, i) => ({
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

  runtimeChart.setOption(option);

  // 图表渲染完成后添加圆形头像
  if (userIndex >= 0) {
    avatarImg.onload = () => {
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext("2d");
      if (!ctx) return;

      const size = 28;
      canvas.width = size;
      canvas.height = size;

      // 绘制圆形裁剪路径
      ctx.beginPath();
      ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
      ctx.closePath();
      ctx.clip();

      // 绘制头像
      ctx.drawImage(avatarImg, 0, 0, size, size);

      // 添加边框
      ctx.restore();
      ctx.beginPath();
      ctx.arc(size / 2, size / 2, size / 2 - 1, 0, Math.PI * 2);
      ctx.strokeStyle = "hsl(var(--chart-series-1))";
      ctx.lineWidth = 2;
      ctx.stroke();

      const circularAvatar = canvas.toDataURL();

      // 使用圆形头像更新图表
      runtimeChart?.setOption({
        series: [
          {
            markPoint: {
              data: [
                {
                  coord: [userIndex, pairedDist.value[userIndex]?.count ?? 0],
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
};

const initMemoryChart = () => {
  if (!memoryChartRef.value) return;

  if (memoryChart) {
    memoryChart.dispose();
  }

  memoryChart = echarts.init(memoryChartRef.value);

  // 模拟内存分布数据
  const memoryBins = Array.from({ length: 80 }, (_, i) => {
    const baseMemory = 43.42;
    const step = 0.048;
    return (baseMemory + i * step).toFixed(3);
  });

  const memoryCounts = Array.from({ length: 80 }, () =>
    Math.floor(Math.random() * 100),
  );
  const userMemoryIndex = 40; // 用户位置索引
  const userAvatar =
    props.submission?.user?.avatar ||
    "https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png";

  // 创建圆形头像 Image 对象
  const avatarImg = new Image();
  avatarImg.crossOrigin = "anonymous";
  avatarImg.src = userAvatar;

  const option = {
    tooltip: {
      trigger: "axis",
      axisPointer: {
        type: "shadow",
      },
      formatter: (params: unknown) => {
        const dataArray = params as TooltipCallbackDataParams[];
        const data = dataArray[0];
        if (!data) return "";
        const isUserPosition = data.dataIndex === userMemoryIndex;
        return `${memoryBins[data.dataIndex]}MB<br/>${t("problem.layout.count")}: ${data.value}${isUserPosition ? `<br/><span style="color: hsl(var(--chart-series-1));">${t("problem.layout.userPosition")}</span>` : ""}`;
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
      data: memoryBins,
      axisLabel: {
        interval: Math.ceil(memoryBins.length / 8),
        rotate: 0,
        fontSize: 10,
        formatter: (value: string) => `${value}mb`,
      },
      axisLine: {
        lineStyle: {
          color: "hsl(var(--border))",
        },
      },
    },
    yAxis: {
      type: "value",
      axisLine: {
        show: false,
      },
      axisTick: {
        show: false,
      },
      splitLine: {
        lineStyle: {
          color: "hsl(var(--border))",
        },
      },
      axisLabel: {
        fontSize: 10,
        color: "hsl(var(--muted-foreground))",
      },
    },
    series: [
      {
        type: "bar",
        data: memoryCounts.map((count, i) => ({
          value: count,
          itemStyle: {
            color:
              i === userMemoryIndex
                ? "hsl(var(--chart-series-1))"
                : "hsl(var(--muted-foreground) / 0.3)",
            borderRadius: [4, 4, 0, 0],
          },
        })),
        barMaxWidth: 40,
      },
    ],
  };

  memoryChart.setOption(option);

  // 图表渲染完成后添加圆形头像
  avatarImg.onload = () => {
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const size = 28;
    canvas.width = size;
    canvas.height = size;

    // 绘制圆形裁剪路径
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
    ctx.closePath();
    ctx.clip();

    // 绘制头像
    ctx.drawImage(avatarImg, 0, 0, size, size);

    // 添加边框
    ctx.restore();
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2 - 1, 0, Math.PI * 2);
    ctx.strokeStyle = "hsl(var(--chart-series-1))";
    ctx.lineWidth = 2;
    ctx.stroke();

    const circularAvatar = canvas.toDataURL();

    // 使用圆形头像更新图表
    memoryChart?.setOption({
      series: [
        {
          markPoint: {
            data: [
              {
                coord: [userMemoryIndex, memoryCounts[userMemoryIndex] ?? 0],
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
};

onMounted(() => {
  if (showRuntimeDetail.value) {
    nextTick(() => initRuntimeChart());
  }
  if (showMemoryDetail.value) {
    nextTick(() => initMemoryChart());
  }
});

watch(activeChart, (newVal) => {
  if (newVal === "runtime") {
    nextTick(() => initRuntimeChart());
  } else if (newVal === "memory") {
    nextTick(() => initMemoryChart());
  }
});

const toggleRuntimeChart = () => {
  activeChart.value = "runtime";
};

const toggleMemoryChart = () => {
  activeChart.value = "memory";
};

const codeMarkdown = computed(() => {
  if (!props.submission) return "";
  const lang = props.submission.language.toLowerCase();
  const code = props.submission.code;
  return "```" + lang + "\n" + code + "\n" + "```";
});

import { useRouter } from "vue-router";
const router = useRouter();

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
            <span data-e2e-locator="submission-result">{{ statusLabel }}</span>
          </div>
        </div>

        <!-- Test cases info (only if not compile error) -->
        <div
          v-if="!isCompileError && !isPending"
          class="text-xs font-normal text-muted-foreground"
        >
          <span v-if="isAccepted">
            {{ t("problem.submissions.allTestsPassed") }}
          </span>
          <span v-else>
            {{
              t("problem.submissions.testsPassed", {
                count:
                  props.submission?.tests?.filter(
                    (t) => t.status === "Accepted",
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
      </div>
    </div>

    <!-- Content based on Status -->
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

    <!-- 1. Compile Error -->
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

    <!-- 2. Failure details -->
    <div v-else-if="showCaseDetails" class="space-y-4">
      <div v-if="props.submission.input" class="space-y-1.5">
        <div class="text-xs font-medium text-muted-foreground">
          {{ t("problem.layout.input") }}
        </div>
        <div
          class="rounded-none bg-muted px-3 py-2 text-sm font-mono text-foreground"
        >
          {{ props.submission.input }}
        </div>
      </div>

      <div v-if="props.submission.output" class="space-y-1.5">
        <div class="text-xs font-medium text-muted-foreground">
          {{ t("problem.layout.output") }}
        </div>
        <div
          class="rounded-none bg-muted px-3 py-2 text-sm font-mono text-foreground"
        >
          {{ props.submission.output }}
        </div>
      </div>

      <div v-if="props.submission.expected_output" class="space-y-1.5">
        <div class="text-xs font-medium text-muted-foreground">
          {{ t("problem.layout.expected") }}
        </div>
        <div
          class="rounded-none bg-muted px-3 py-2 text-sm font-mono text-foreground"
        >
          {{ props.submission.expected_output }}
        </div>
      </div>
    </div>

    <!-- 3. Accepted (Charts) -->
    <div v-else-if="isAccepted" class="space-y-4">
      <!-- 分布统计卡片 -->
      <div
        class="flex w-full flex-col gap-1.5 rounded-none border border-border p-2"
      >
        <div class="flex items-center justify-between gap-1.5">
          <div class="flex w-full flex-wrap gap-2">
            <!-- Runtime Trigger -->
            <div
              class="rounded-none group flex min-w-[240px] flex-1 cursor-pointer flex-col px-3 py-2 text-xs transition hover:opacity-100"
              :class="showRuntimeDetail ? 'bg-accent' : 'opacity-40'"
              @click="toggleRuntimeChart"
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
                <span class="font-medium text-foreground">
                  {{ props.submission?.runtime.toString().replace("ms", "") }}
                  ms
                </span>
                <span class="text-muted-foreground">
                  {{
                    t("problem.layout.beats", {
                      percent: (
                        props.submission?.runtimePercentile ?? 0
                      ).toFixed(1),
                    })
                  }}
                </span>
              </div>
            </div>

            <!-- Memory Trigger -->
            <div
              class="rounded-none group flex min-w-[240px] flex-1 cursor-pointer flex-col px-3 py-2 text-xs transition hover:opacity-100"
              :class="showMemoryDetail ? 'bg-accent' : 'opacity-40'"
              @click="toggleMemoryChart"
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
                <span class="font-medium text-foreground">
                  {{ props.submission?.memory.toString().replace("MB", "") }} MB
                </span>
                <span class="text-muted-foreground">
                  {{
                    t("problem.layout.beats", {
                      percent: (
                        props.submission?.memoryPercentile ?? 0
                      ).toFixed(1),
                    })
                  }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Charts -->
      <div v-if="showRuntimeDetail" class="rounded-none border border-border p-3">
        <div
          class="h-48 w-full"
          ref="runtimeChartRef"
          style="touch-action: none; overflow: hidden; contain: content"
        ></div>
      </div>
      <div v-if="showMemoryDetail" class="rounded-none border border-border p-3">
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
    <div class="space-y-2 mt-2">
      <div class="text-xs font-medium text-muted-foreground">
        {{ t("problem.submissions.code") }}
      </div>
      <MarkdownView
        :content="codeMarkdown"
        editor-id="submission-code-preview"
      />
    </div>
  </div>
</template>
