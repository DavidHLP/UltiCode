import { computed, ref, watch, onUnmounted } from "vue";
import { useI18n } from "vue-i18n";
import type {
  DistributionBin,
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import { getStatusLabelI18nKey } from "@/shared/submission-status/src";

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

export function buildDistributionDisplayPoints(
  points: NormalizedDistributionPoint[],
  currentValue: number | null | undefined,
): NormalizedDistributionPoint[] {
  const hasVisibleCounts = points.some((point) => point.count > 0);
  if (hasVisibleCounts) return points;

  const value = toFiniteNumber(currentValue);
  if (value == null) return points;

  if (points.length === 0) {
    return [{ i: 0, bin: value, count: 1 }];
  }

  let closestIndex = 0;
  let closestDistance = Math.abs(points[0].bin - value);
  for (let i = 1; i < points.length; i++) {
    const distance = Math.abs(points[i].bin - value);
    if (distance < closestDistance) {
      closestDistance = distance;
      closestIndex = i;
    }
  }

  return points.map((point, index) => ({
    ...point,
    count: index === closestIndex ? 1 : point.count,
  }));
}

export function useSubmissionDetail(
  submission: () => SubmissionRecord | undefined,
  statusMetaByKey: () => Record<string, SubmissionStatusMeta>,
) {
  const { t } = useI18n();

  const parseMs = (value: string | number) => {
    if (typeof value === "number") {
      return Number.isFinite(value) && value >= 0 ? value : null;
    }
    const m = /([0-9]+)\s*ms/.exec(value);
    if (!m) return null;
    const parsed = Number(m[1]);
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
  };

  const runtimeMs = computed(() =>
    submission() ? parseMs(submission()!.runtime) : null,
  );

  const statusMeta = computed(() =>
    submission() ? statusMetaByKey()[submission()!.status] : null,
  );

  const statusLabel = computed(() => {
    const status = submission()?.status ?? "";
    const key = getStatusLabelI18nKey(status);
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
    // `created_at` is the canonical snake_case field on `SubmissionRecord`;
    // `submittedAt` is an optional alias some payloads include. We prefer
    // the primary field and only fall back to the alias when it is missing.
    const createdAtRaw =
      submission()?.created_at ?? submission()?.submittedAt ?? "";
    const created = new Date(createdAtRaw).getTime();
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
  const displayPairedDist = computed(() =>
    buildDistributionDisplayPoints(pairedDist.value, runtimeMs.value),
  );
  const distBins = computed<number[]>(() =>
    displayPairedDist.value.map((point) => point.bin),
  );
  const distCounts = computed<number[]>(() =>
    displayPairedDist.value.map((point) => point.count),
  );
  const totalCount = computed(() =>
    displayPairedDist.value.reduce(
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
  const displayPairedMemoryDist = computed(() =>
    buildDistributionDisplayPoints(
      pairedMemoryDist.value,
      submission()?.memory,
    ),
  );
  const memoryDistBins = computed<number[]>(() =>
    displayPairedMemoryDist.value.map((point) => point.bin),
  );
  const memoryDistCounts = computed<number[]>(() =>
    displayPairedMemoryDist.value.map((point) => point.count),
  );
  const totalMemoryCount = computed(() =>
    displayPairedMemoryDist.value.reduce(
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
    pairedDist: displayPairedDist,
    totalCount,
    highlightIndex,
    memoryDistBins,
    memoryDistCounts,
    pairedMemoryDist: displayPairedMemoryDist,
    totalMemoryCount,
    memoryHighlightIndex,
  };
}
