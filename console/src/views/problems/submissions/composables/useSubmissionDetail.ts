import { computed, ref, watch, onUnmounted } from "vue";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";

export function useSubmissionDetail(
  submission: () => SubmissionRecord | undefined,
  statusMetaByKey: () => Record<string, SubmissionStatusMeta>,
) {
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

  const statusLabel = computed(
    () => statusMeta.value?.label ?? submission()?.status ?? "",
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
        return submission()?.status === "Accepted"
          ? "text-[var(--terminal-green)]"
          : "text-[var(--terminal-red)]";
    }
  });

  const isAccepted = computed(() => submission()?.status === "Accepted");
  const isCompileError = computed(() => submission()?.status === "Compile Error");
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

  watch(isPending, (pending) => {
    if (pending) startPendingTimer();
    else stopPendingTimer();
  }, { immediate: true });

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
  const distBins = computed<number[]>(
    () => submission()?.runtimeDistBinsMs?.map((b) => b.min) ?? [],
  );
  const distCounts = computed<number[]>(
    () => submission()?.runtimeDistBinsMs?.map((b) => b.count) ?? [],
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

  // Memory distribution data
  const memoryDistBins = computed<number[]>(
    () => submission()?.memoryDistBinsMb?.map((b) => b.min) ?? [],
  );
  const memoryDistCounts = computed<number[]>(
    () => submission()?.memoryDistBinsMb?.map((b) => b.count) ?? [],
  );
  const memoryDistLength = computed(() =>
    Math.min(memoryDistCounts.value.length, memoryDistBins.value.length),
  );
  const pairedMemoryDist = computed(() =>
    Array.from({ length: memoryDistLength.value }, (_, i) => ({
      i,
      count: memoryDistCounts.value[i]!,
      bin: memoryDistBins.value[i]!,
    })),
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
