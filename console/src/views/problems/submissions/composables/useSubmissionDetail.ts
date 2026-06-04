import { computed, ref, watch, onUnmounted } from "vue";
import { useI18n } from "vue-i18n";
import type {
  DistributionBin,
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";

export interface NormalizedDistributionPoint {
  i: number;
  bin: number;
  count: number;
}

function toFiniteNumber(value: unknown): number | null {
  const numericValue =
    typeof value === "number"
      ? value
      : typeof value === "string" && value.trim() !== ""
        ? Number(value)
        : Number.NaN;
  return Number.isFinite(numericValue) ? numericValue : null;
}

export function normalizeDistributionBins(
  bins: DistributionBin[] | undefined,
): NormalizedDistributionPoint[] {
  if (!Array.isArray(bins)) return [];

  return bins.flatMap((bin, i) => {
    if (Array.isArray(bin)) {
      const label = toFiniteNumber(bin[0]);
      if (label == null) return [];
      return [{ i, bin: label, count: toFiniteNumber(bin[1]) ?? 0 }];
    }

    if (bin && typeof bin === "object") {
      const label = toFiniteNumber(bin.min ?? bin.value ?? bin.bin ?? bin.max);
      if (label == null) return [];
      return [{ i, bin: label, count: toFiniteNumber(bin.count) ?? 0 }];
    }

    const label = toFiniteNumber(bin);
    if (label == null) return [];
    return [{ i, bin: label, count: 0 }];
  });
}

export function useSubmissionDetail(
  submission: () => SubmissionRecord | undefined,
  statusMetaByKey: () => Record<string, SubmissionStatusMeta>,
) {
  const { t } = useI18n();

  const parseMs = (value: string | number) => {
    if (typeof value === "number") return value;
    const m = /([0-9]+)\s*ms/.exec(value);
    return m ? Number(m[1]) : null;
  };

  const runtimeMs = computed(() =>
    submission() ? parseMs(submission()!.runtime) : null,
  );

  const statusMeta = computed(() =>
    submission() ? statusMetaByKey()[submission()!.status] : null,
  );

  const statusLabel = computed(() => {
    const status = submission()?.status ?? "";
    const normalized = status.toUpperCase().replace(/\s+/g, "_");
    const map: Record<string, string> = {
      ACCEPTED: "submission.status.accepted",
      WRONG_ANSWER: "submission.status.wrongAnswer",
      TIME_LIMIT_EXCEEDED: "submission.status.timeLimitExceeded",
      MEMORY_LIMIT_EXCEEDED: "submission.status.memoryLimitExceeded",
      OUTPUT_LIMIT_EXCEEDED: "submission.status.outputLimitExceeded",
      RUNTIME_ERROR: "submission.status.runtimeError",
      COMPILE_ERROR: "submission.status.compileError",
      PRESENTATION_ERROR: "submission.status.presentationError",
      SYSTEM_ERROR: "submission.status.systemError",
      JUDGING: "submission.status.judging",
      PENDING: "submission.status.pending",
    };
    const key = map[normalized];
    return key ? t(key) : (statusMeta.value?.label ?? status);
  });

  const statusDescription = computed(() => {
    const status = submission()?.status ?? "";
    const normalized = status.toUpperCase().replace(/\s+/g, "_");
    const map: Record<string, string> = {
      ACCEPTED: "submission.statusDescriptions.accepted",
      WRONG_ANSWER: "submission.statusDescriptions.wrongAnswer",
      TIME_LIMIT_EXCEEDED: "submission.statusDescriptions.timeLimitExceeded",
      MEMORY_LIMIT_EXCEEDED:
        "submission.statusDescriptions.memoryLimitExceeded",
      OUTPUT_LIMIT_EXCEEDED:
        "submission.statusDescriptions.outputLimitExceeded",
      RUNTIME_ERROR: "submission.statusDescriptions.runtimeError",
      COMPILE_ERROR: "submission.statusDescriptions.compileError",
      PRESENTATION_ERROR: "submission.statusDescriptions.presentationError",
      SYSTEM_ERROR: "submission.statusDescriptions.systemError",
      JUDGING: "submission.statusDescriptions.judging",
      PENDING: "submission.statusDescriptions.pending",
    };
    const key = map[normalized];
    return key ? t(key) : (statusMeta.value?.description ?? "");
  });

  const statusSuggestion = computed(() => {
    const status = submission()?.status ?? "";
    const normalized = status.toUpperCase().replace(/\s+/g, "_");
    const map: Record<string, string> = {
      ACCEPTED: "submission.statusSuggestions.accepted",
      WRONG_ANSWER: "submission.statusSuggestions.wrongAnswer",
      TIME_LIMIT_EXCEEDED: "submission.statusSuggestions.timeLimitExceeded",
      MEMORY_LIMIT_EXCEEDED: "submission.statusSuggestions.memoryLimitExceeded",
      RUNTIME_ERROR: "submission.statusSuggestions.runtimeError",
      COMPILE_ERROR: "submission.statusSuggestions.compileError",
    };
    const key = map[normalized];
    return key ? t(key) : (statusMeta.value?.suggestion ?? "");
  });

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
        return submission()?.status === "Accepted"
          ? "text-[var(--terminal-green)]"
          : "text-[var(--terminal-red)]";
    }
  });

  const isAccepted = computed(() => submission()?.status === "Accepted");
  const isCompileError = computed(
    () => submission()?.status === "Compile Error",
  );
  const isPending = computed(() =>
    ["Pending", "Judging"].includes(submission()?.status ?? ""),
  );
  const isStuck = computed(() => submission()?.status === "System Error");

  // Pending timer
  const pendingSeconds = ref(0);
  let pendingTimer: ReturnType<typeof setInterval> | null = null;

  const startPendingTimer = () => {
    if (pendingTimer) return;
    const created = new Date(
      submission()?.submittedAt ?? submission()?.created_at ?? "",
    ).getTime();
    if (Number.isNaN(created)) return;
    pendingTimer = setInterval(() => {
      pendingSeconds.value = Math.floor((Date.now() - created) / 1000);
    }, 1000);
  };

  const stopPendingTimer = () => {
    if (pendingTimer) {
      clearInterval(pendingTimer);
      pendingTimer = null;
    }
    pendingSeconds.value = 0;
  };

  watch(
    isPending,
    (pending) => {
      if (pending) startPendingTimer();
      else stopPendingTimer();
    },
    { immediate: true },
  );

  onUnmounted(() => stopPendingTimer());

  // Computed display flags
  const showCaseDetails = computed(
    () => !isAccepted.value && !isCompileError.value && !isPending.value,
  );

  const showVerdictMeta = computed(() => {
    if (isAccepted.value) return false;
    return (
      Boolean(statusMeta.value?.description) ||
      Boolean(statusMeta.value?.suggestion) ||
      Boolean(submission()?.errorDetail)
    );
  });

  const verdictDetail = computed(() =>
    isCompileError.value ? null : submission()?.errorDetail,
  );

  const codeMarkdown = computed(() => {
    const sub = submission();
    if (!sub) return "";
    const lang = sub.language.toLowerCase();
    const code = sub.code;
    return "```" + lang + "\n" + code + "\n" + "```";
  });

  // Runtime distribution data
  const pairedDist = computed(() =>
    normalizeDistributionBins(submission()?.runtimeDistBinsMs),
  );
  const distBins = computed<number[]>(() =>
    pairedDist.value.map((point) => point.bin),
  );
  const distCounts = computed<number[]>(() =>
    pairedDist.value.map((point) => point.count),
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

  // Memory distribution data
  const pairedMemoryDist = computed(() =>
    normalizeDistributionBins(submission()?.memoryDistBinsMb),
  );
  const memoryDistBins = computed<number[]>(() =>
    pairedMemoryDist.value.map((point) => point.bin),
  );
  const memoryDistCounts = computed<number[]>(() =>
    pairedMemoryDist.value.map((point) => point.count),
  );
  const totalMemoryCount = computed(() =>
    pairedMemoryDist.value.reduce(
      (acc, d) => acc + (Number.isFinite(d.count) ? d.count : 0),
      0,
    ),
  );

  const memoryHighlightIndex = computed(() => {
    const bins = memoryDistBins.value;
    const val = submission()?.memory;
    if (!Array.isArray(bins) || bins.length === 0 || val == null) return -1;
    let closest = 0;
    let best = Math.abs((bins[0] ?? val) - val);
    for (let i = 1; i < bins.length; i++) {
      const bi = bins[i] ?? val;
      const d = Math.abs(bi - val);
      if (d < best) {
        best = d;
        closest = i;
      }
    }
    return closest;
  });

  return {
    runtimeMs,
    statusMeta,
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
  };
}
