<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import {
  BookPlus,
  CheckCircle2,
  ChevronDown,
  Clock,
  Lightbulb,
  RefreshCw,
  Target,
  Timer,
  Trophy,
} from "lucide-vue-next";
import { toast } from "vue-sonner";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import ContestStatusBadge from "@/views/contest/components/ContestStatusBadge.vue";
import ContestTimer from "@/views/contest/components/ContestTimer.vue";
import ContestAnnouncementBell from "./ContestAnnouncementBell.vue";
import { fetchContestProblemSubmissions } from "@/api/contest";
import { useAuthStore } from "@/stores/auth";
import { useContestStore } from "@/stores/contest";
import { useContestProblemShellStore } from "@/stores/contestProblemShell";
import { ContestProblemContextKey, ToggleNotesKey } from "../problem-context";
import type { ContestProblemSummary } from "@/types/contest";
import type { SubmissionRecord } from "@/types/submission";

const router = useRouter();
const route = useRoute();
const { t } = useI18n();
const ctx = inject(ContestProblemContextKey, null);
const toggleNotes = inject(ToggleNotesKey, () => {});
const authStore = useAuthStore();
const contestStore = useContestStore();
const shellStore = useContestProblemShellStore();

type ProblemPill = {
  problemId: number;
  problemIndex: string;
  slug: string | null;
};

interface VerdictBreakdown {
  total: number;
  accepted: number;
  wrongAnswer: number;
  timeLimit: number;
  runtime: number;
  compileError: number;
  other: number;
}

const isAuthed = computed(() => authStore.isAuthenticated);
const isVisible = computed(
  () => ctx?.isInContest.value === true && ctx.contest.value != null,
);
const score = computed(() => ctx?.participation.value?.score ?? 0);
const rank = computed(() => ctx?.participation.value?.ranking ?? null);
const solvedCount = computed(
  () => ctx?.participation.value?.problemsSolved ?? 0,
);
const totalProblems = computed(
  () =>
    ctx?.participation.value?.totalProblems ?? ctx?.problems.value.length ?? 0,
);

const timerTargetIso = computed<string | null>(() => {
  if (!ctx?.contest.value) return null;
  const status = ctx.contest.value.status;
  if (status === "UPCOMING") return ctx.contest.value.startTime ?? null;
  if (status === "RUNNING") return ctx.contest.value.endTime ?? null;
  if (contestStore.isInVirtualContest && contestStore.virtualSession?.endsAt) {
    return contestStore.virtualSession.endsAt;
  }
  return null;
});
const timerIsCountdownToStart = computed(
  () => ctx?.contest.value?.status === "UPCOMING",
);

const showSolutionsHiddenHint = computed(() => {
  if (!ctx?.contest.value) return false;
  return (
    ctx.contest.value.status === "RUNNING" || contestStore.isInVirtualContest
  );
});

const pills = computed<ProblemPill[]>(() => {
  if (!ctx?.problems.value) return [];
  return [...ctx.problems.value]
    .sort((a, b) => a.problemIndex.localeCompare(b.problemIndex))
    .map((p: ContestProblemSummary) => ({
      problemId: p.problemId,
      problemIndex: p.problemIndex,
      slug: p.slug,
    }));
});

function isActivePill(p: ProblemPill): boolean {
  return (
    ctx?.problemBelongsToContest.value === true &&
    ctx.contestProblemNav.value.current?.problemId === p.problemId
  );
}

function goToPill(p: ProblemPill): void {
  if (!p.slug) return;
  router.push({
    name: "problem-detail",
    params: { slug: p.slug },
    query: { contestId: ctx?.contest.value?.slug ?? "" },
  });
}

let pendingToast: ReturnType<typeof setTimeout> | null = null;
let inFlightToken = 0;

watch(
  () => shellStore.lastSubmitResult,
  (res) => {
    if (!res || !ctx) return;
    if (pendingToast) {
      clearTimeout(pendingToast);
      pendingToast = null;
    }

    const myToken = ++inFlightToken;
    const prevScore = ctx.participation.value?.score ?? 0;
    const prevSolved = ctx.participation.value?.problemsSolved ?? 0;

    pendingToast = setTimeout(async () => {
      pendingToast = null;
      try {
        await ctx.refreshParticipation();
      } catch {
        // The toast can still report the verdict if the score refresh fails.
      }
      if (myToken !== inFlightToken) return;

      const now = ctx.participation.value;
      const nowScore = now?.score ?? prevScore;
      const nowSolved = now?.problemsSolved ?? prevSolved;
      const total = now?.totalProblems ?? totalProblems.value;
      const delta = Math.max(0, nowScore - prevScore);
      const verdict = (res.status ?? "").toString();

      if (verdict === "Accepted") {
        toast.success(
          t("contest.detail.submit.accepted", {
            delta,
            total: nowScore,
            solved: nowSolved,
            totalProblems: total,
          }) as string,
        );
      } else if (/wrong[ _-]?answer/i.test(verdict)) {
        const penalty = ctx.contest.value?.penaltyPerWrong ?? 0;
        toast.error(
          t("contest.detail.submit.wrongAnswer", { penalty }) as string,
        );
      } else if (/compile[ _-]?error/i.test(verdict)) {
        toast.error(t("contest.detail.submit.compileError") as string);
      } else {
        toast.info(
          t("contest.detail.submit.judging", { status: verdict }) as string,
        );
      }

      shellStore.clearLastSubmit();
    }, 1500);
  },
  { immediate: false },
);

onBeforeUnmount(() => {
  if (pendingToast) {
    clearTimeout(pendingToast);
    pendingToast = null;
  }
});

onMounted(() => {
  if (!ctx?.isInContest.value || !isAuthed.value) return;
  void ctx.refreshParticipation();
});

const submissions = ref<SubmissionRecord[]>([]);
const loadingReview = ref(false);

const slug = computed(() => {
  const s = route.params.slug;
  return Array.isArray(s) ? (s[0] ?? null) : (s ?? null);
});
const problemId = computed(
  () => ctx?.contestProblemNav.value.current?.problemId ?? null,
);
const contestIdForFetch = computed(() => ctx?.contestId.value ?? null);

const firstAccepted = computed(() => {
  const accepted = submissions.value
    .filter((s) => s.status === "Accepted")
    .sort(
      (a, b) =>
        new Date(a.created_at).getTime() - new Date(b.created_at).getTime(),
    );
  return accepted[0] ?? null;
});

const breakdown = computed<VerdictBreakdown>(() => {
  const out: VerdictBreakdown = {
    total: submissions.value.length,
    accepted: 0,
    wrongAnswer: 0,
    timeLimit: 0,
    runtime: 0,
    compileError: 0,
    other: 0,
  };

  for (const s of submissions.value) {
    const v = (s.status ?? "").toString();
    if (v === "Accepted" || v === "ACCEPTED") out.accepted++;
    else if (/wrong[ _-]?answer/i.test(v)) out.wrongAnswer++;
    else if (/time[ _-]?limit/i.test(v) || /^tle$/i.test(v)) out.timeLimit++;
    else if (/runtime[ _-]?error/i.test(v) || /^re$/i.test(v)) out.runtime++;
    else if (/compile[ _-]?error/i.test(v) || /^ce$/i.test(v))
      out.compileError++;
    else out.other++;
  }

  return out;
});

const finalScore = computed(() => ctx?.participation.value?.score ?? null);

async function loadReview(): Promise<void> {
  if (ctx?.contest.value?.status !== "FINISHED") {
    submissions.value = [];
    return;
  }

  const cid = contestIdForFetch.value;
  const pid = problemId.value;
  if (cid == null || pid == null) return;

  loadingReview.value = true;
  try {
    submissions.value = await fetchContestProblemSubmissions(cid, pid);
  } catch {
    submissions.value = [];
  } finally {
    loadingReview.value = false;
  }
}

watch(
  [contestIdForFetch, problemId, () => ctx?.contest.value?.status],
  () => {
    void loadReview();
  },
  { immediate: true },
);

function formatTime(iso: string | undefined | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (!Number.isFinite(d.getTime())) return "";
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function handleRetake(): void {
  if (!slug.value) return;
  router.push({
    name: "problem-detail",
    params: { slug: slug.value, tab: route.params.tab },
    query: {
      ...route.query,
      contestId: ctx?.contest.value?.slug ?? route.query.contestId,
    },
  });
}

function handleNotebook(): void {
  toggleNotes();
}
</script>

<template>
  <Popover v-if="isVisible">
    <PopoverTrigger as-child>
      <Button
        type="button"
        class="h-8 max-w-[210px] rounded-none border border-border bg-transparent px-2.5 font-mono text-[11px] font-black uppercase tracking-wider text-muted-foreground shadow-none transition-colors hover:bg-[var(--silver-100)]/70 hover:text-[var(--solarized-base01)] data-[state=open]:bg-[var(--silver-100)] dark:hover:bg-[var(--solarized-base03)]/70 dark:hover:text-[var(--solarized-base1)]"
        :aria-label="ctx?.contest.value?.title"
        :title="ctx?.contest.value?.title"
        data-testid="contest-problem-dock-trigger"
      >
        <Trophy class="h-3.5 w-3.5 shrink-0 text-[var(--terminal-amber)]" />
        <span class="hidden max-w-[120px] truncate xl:inline">
          {{ ctx?.contest.value?.title }}
        </span>
        <span class="font-black text-[var(--terminal-amber)]">
          {{ isAuthed ? score : "—" }}
        </span>
        <ChevronDown class="h-3 w-3 shrink-0 opacity-60" />
      </Button>
    </PopoverTrigger>

    <PopoverContent
      align="end"
      :side-offset="8"
      class="w-[min(92vw,440px)] rounded-none border border-border bg-[var(--solarized-base3)] p-0 font-mono shadow-lg dark:bg-[var(--solarized-base02)]"
    >
      <header
        class="border-b border-border bg-[var(--silver-100)]/50 px-3 py-2 dark:bg-[var(--solarized-base03)]/50"
      >
        <div class="flex min-w-0 items-center justify-between gap-3">
          <div class="min-w-0">
            <p class="truncate text-xs font-black text-foreground">
              {{ ctx?.contest.value?.title }}
            </p>
            <div
              v-if="timerTargetIso"
              class="mt-1 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-muted-foreground"
            >
              <Timer class="h-3 w-3" />
              <span>{{
                timerIsCountdownToStart
                  ? t("contest.detail.shell.startsIn")
                  : t("contest.detail.shell.endsIn")
              }}</span>
              <ContestTimer
                :target-time="timerTargetIso"
                :is-countdown-to-start="timerIsCountdownToStart"
                :show-icon="false"
                :compact="true"
                size="sm"
              />
            </div>
          </div>
          <ContestStatusBadge
            v-if="ctx?.contest.value"
            :status="ctx.contest.value.status"
            size="sm"
          />
        </div>
      </header>

      <div class="space-y-3 p-3">
        <div class="grid grid-cols-3 gap-2">
          <div class="border border-border/60 bg-background/30 px-2 py-2">
            <div
              class="flex items-center gap-1 text-[10px] text-muted-foreground"
            >
              <Trophy class="h-3 w-3 text-[var(--terminal-amber)]" />
              {{ t("contest.detail.shell.score") }}
            </div>
            <p class="mt-1 text-sm font-black text-foreground">
              {{ isAuthed ? score : "—" }}
            </p>
          </div>
          <div class="border border-border/60 bg-background/30 px-2 py-2">
            <div
              class="flex items-center gap-1 text-[10px] text-muted-foreground"
            >
              <Target class="h-3 w-3 text-[var(--accent-electric)]" />
              {{ t("contest.detail.shell.rank") }}
            </div>
            <p class="mt-1 text-sm font-black text-foreground">
              {{ isAuthed && rank != null ? `#${rank}` : "—" }}
            </p>
          </div>
          <div class="border border-border/60 bg-background/30 px-2 py-2">
            <div class="text-[10px] text-muted-foreground">
              {{ t("contest.detail.shell.solved") }}
            </div>
            <p class="mt-1 text-sm font-black text-[var(--terminal-green)]">
              {{ isAuthed ? solvedCount : "—" }}
              <span class="text-xs text-muted-foreground"
                >/ {{ totalProblems }}</span
              >
            </p>
          </div>
        </div>

        <section v-if="pills.length > 0" class="space-y-2">
          <div
            class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
          >
            {{ t("contest.detail.shell.problemNav") }}
          </div>
          <div
            class="grid grid-cols-[repeat(auto-fit,minmax(36px,1fr))] border border-border"
          >
            <button
              v-for="p in pills"
              :key="p.problemId"
              type="button"
              :class="[
                'h-8 border-r border-border text-[11px] font-black uppercase tracking-wider last:border-r-0 transition-colors',
                isActivePill(p)
                  ? 'bg-[var(--accent-electric)]/15 text-[var(--accent-electric)]'
                  : 'bg-transparent text-muted-foreground hover:bg-[var(--silver-100)]/50 dark:hover:bg-[var(--solarized-base03)]/50',
                !p.slug ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
              ]"
              :disabled="!p.slug"
              :data-testid="`dock-pill-${p.problemIndex}`"
              @click="goToPill(p)"
            >
              {{ p.problemIndex }}
            </button>
          </div>
        </section>

        <div
          class="flex items-center justify-between gap-3 border border-border/60 px-2 py-2"
        >
          <span
            class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
          >
            {{ t("contest.detail.announcements.title") }}
          </span>
          <ContestAnnouncementBell />
        </div>

        <div
          v-if="showSolutionsHiddenHint"
          class="flex items-start gap-1.5 border border-border/60 bg-[var(--silver-100)]/40 px-2 py-2 text-[10px] leading-snug text-muted-foreground dark:bg-[var(--solarized-base03)]/40"
        >
          <Lightbulb class="mt-0.5 h-3 w-3 shrink-0" />
          <span>{{ t("contest.detail.solutionsHiddenHint") }}</span>
        </div>

        <section
          v-if="ctx?.contest.value?.status === 'FINISHED'"
          class="space-y-2 border-t border-border/70 pt-3"
        >
          <div class="flex items-center justify-between gap-3">
            <h3
              class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
            >
              {{ t("contest.review.title") }}
            </h3>
            <span
              v-if="loadingReview"
              class="text-[10px] text-muted-foreground"
            >
              ...
            </span>
          </div>

          <div class="grid grid-cols-3 gap-2">
            <div class="min-w-0">
              <div
                class="flex items-center gap-1 text-[10px] text-muted-foreground"
              >
                <CheckCircle2
                  v-if="firstAccepted"
                  class="h-3 w-3 text-[var(--terminal-green)]"
                />
                <Clock v-else class="h-3 w-3" />
                {{ t("contest.review.firstACLabel") }}
              </div>
              <p class="mt-1 truncate text-[11px] font-bold text-foreground">
                {{ firstAccepted ? formatTime(firstAccepted.created_at) : "—" }}
              </p>
            </div>
            <div class="min-w-0">
              <p class="text-[10px] text-muted-foreground">
                {{ t("contest.review.breakdownLabel") }}
              </p>
              <p
                class="mt-1 truncate text-[11px] font-bold text-[var(--terminal-green)]"
              >
                {{ breakdown.accepted }} / {{ breakdown.total }} AC
              </p>
            </div>
            <div class="min-w-0">
              <p class="text-[10px] text-muted-foreground">
                {{ t("contest.review.finalScoreLabel") }}
              </p>
              <p class="mt-1 truncate text-[11px] font-bold text-foreground">
                {{ finalScore != null ? finalScore : "—" }}
              </p>
            </div>
          </div>

          <ul
            v-if="submissions.length > 0"
            class="max-h-28 overflow-y-auto border border-border/50"
          >
            <li
              v-for="s in submissions"
              :key="s.id"
              class="grid grid-cols-[1fr_auto_auto] items-center gap-2 border-b border-border/30 px-2 py-1.5 text-[10px] last:border-b-0"
            >
              <span class="truncate text-muted-foreground">
                {{ formatTime(s.created_at) }}
              </span>
              <span class="font-black text-foreground">{{ s.status }}</span>
              <span class="font-black text-[var(--terminal-amber)]">
                {{
                  s.contest_info?.score != null
                    ? `+${s.contest_info.score}`
                    : "—"
                }}
              </span>
            </li>
          </ul>
          <p
            v-else-if="!loadingReview"
            class="border border-border/50 px-2 py-3 text-center text-[11px] text-muted-foreground"
          >
            {{ t("contest.review.empty") }}
          </p>

          <div class="flex flex-wrap items-center gap-2">
            <Button
              variant="default"
              size="sm"
              class="h-8 rounded-none border border-[var(--accent-electric)]/40 bg-[var(--accent-electric)]/15 px-3 font-black text-[10px] uppercase tracking-widest text-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/25 cursor-pointer"
              :disabled="!slug"
              data-testid="contest-review-retake"
              @click="handleRetake"
            >
              <RefreshCw class="mr-1 h-3 w-3" />
              {{ t("contest.review.retake") }}
            </Button>
            <Button
              variant="outline"
              size="sm"
              class="h-8 rounded-none border border-border bg-transparent px-3 font-black text-[10px] uppercase tracking-widest text-muted-foreground hover:bg-[var(--silver-100)] hover:text-foreground dark:hover:bg-[var(--solarized-base03)] cursor-pointer"
              data-testid="contest-review-notebook"
              @click="handleNotebook"
            >
              <BookPlus class="mr-1 h-3 w-3" />
              {{ t("contest.review.addToNotebook") }}
            </Button>
          </div>
        </section>
      </div>
    </PopoverContent>
  </Popover>
</template>
