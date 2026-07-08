<script setup lang="ts">
/**
 * ContestProblemShell
 *
 * A sticky bar above the problem page's existing layout header that
 * surfaces the contest context the user needs to make in-contest
 * decisions: where am I, how much time is left, my score, my rank,
 * which problems are A/B/C. Renders ONLY when `?contestId=...` is
 * in the route (gated by the parent).
 *
 * Architecture:
 *  - Reads all state from ContestProblemContextKey (injected by
 *    ProblemDetailView), which delegates to the contest store
 *    + useContestProblemContext.
 *  - Pushes its submit-result toasts by watching
 *    useContestProblemShellStore().lastSubmitResult (set by
 *    LayoutHeaderCenter after a successful submit).
 *  - On mount in a running contest, subscribes to the announcement
 *    count via useContestProblemShellStore().announceUnreadCount.
 *
 * Phase 1 of the product fix (PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md
 * §P0-1). Phase 2/3 (announcement popover, contest-aware submit
 * toast) plug in via the shell store.
 */
import { computed, inject, onBeforeUnmount, onMounted, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { ArrowLeft, Target, Trophy, Timer, Lightbulb } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import ContestStatusBadge from "@/views/contest/components/ContestStatusBadge.vue";
import ContestTimer from "@/views/contest/components/ContestTimer.vue";
import ContestAnnouncementBell from "./ContestAnnouncementBell.vue";
import { ContestProblemContextKey } from "../problem-context";
import { useContestProblemShellStore } from "@/stores/contestProblemShell";
import { useVirtualContestStore } from "@/stores/virtualContest";
import { useAuthStore } from "@/stores/auth";
import { toast } from "vue-sonner";

const router = useRouter();
const { t } = useI18n();
const ctx = inject(ContestProblemContextKey, null);
const shellStore = useContestProblemShellStore();
const contestStore = useVirtualContestStore();
const authStore = useAuthStore();

// ---------------------------------------------------------------------------
// Local UI state
// ---------------------------------------------------------------------------
// We render the score as a number (not "—" while loading) so the user
// always sees a value. The contest store seeds `userScore: 0` for
// authenticated users and the participation refresh fills it in
// after the first submit.
const score = computed(() => ctx?.participation.value?.score ?? 0);
const rank = computed(
  () => ctx?.participation.value?.ranking ?? null,
);
const solvedCount = computed(
  () => ctx?.participation.value?.problemsSolved ?? 0,
);
const totalProblems = computed(
  () => ctx?.participation.value?.totalProblems ?? ctx?.problems.value.length ?? 0,
);
const isAuthed = computed(() => authStore.isAuthenticated);

// The "ends in" target depends on whether this is a real-time or
// virtual session. We use the underlying contest's endTime for
// RUNNING, and the virtual session's endsAt for VIRTUAL replays.
// UPCOMING shows the start time with a "starts in" label.
const timerTargetIso = computed<string | null>(() => {
  if (!ctx?.contest.value) return null;
  const status = ctx.contest.value.status;
  if (status === "UPCOMING") {
    return ctx.contest.value.startTime ?? null;
  }
  if (status === "RUNNING") {
    return ctx.contest.value.endTime ?? null;
  }
  // FINISHED or VIRTUAL: virtual session wins because the
  // underlying contest's endTime has long passed.
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
  // Show the hint during RUNNING contests and during virtual
  // replays of any contest. FINISHED is excluded because the
  // Solutions tab is restored post-game.
  return (
    ctx.contest.value.status === "RUNNING" || contestStore.isInVirtualContest
  );
});

// ---------------------------------------------------------------------------
// A/B/C problem nav
// ---------------------------------------------------------------------------
// We sort by problemIndex (A, B, C ...) and present each as a pill.
// Clicking pushes the corresponding problem's URL with the same
// `?contestId=...` query so the contest context is preserved.
type ProblemPill = {
  problemId: number;
  problemIndex: string;
  slug: string | null;
  isSolved: boolean;
  isAttempted: boolean;
};

const pills = computed<ProblemPill[]>(() => {
  if (!ctx?.problems.value) return [];
  // Reuse the same `contestProblemNav` ordering logic — sort by
  // problemIndex. For non-string indexes (numeric) we fall back to
  // declaration order.
  // We don't have per-problem status in the context today;
  // the `current` problem is the only one we know about. The
  // visual emphasis on the active pill (via `isActivePill`)
  // is enough for v1.
  return [...ctx.problems.value]
    .sort((a, b) => a.problemIndex.localeCompare(b.problemIndex))
    .map((p) => ({
      problemId: p.problemId,
      problemIndex: p.problemIndex,
      slug: p.slug,
      isSolved: false,
      isAttempted: false,
    }));
});

function isActivePill(p: ProblemPill): boolean {
  return (
    ctx?.problemBelongsToContest.value === true &&
    // The composable exposes `contestProblemNav.value.current` —
    // we re-derive the active id from the URL slug for a tighter
    // match. If neither matches, no pill is highlighted.
    ctx?.contestProblemNav.value.current?.problemId === p.problemId
  );
}

function goToPill(p: ProblemPill): void {
  if (!p.slug) return;
  // Preserve the ?contestId=... query. `problem-detail` route
  // accepts the slug param; contestId is the URL-stable form
  // (matching what ContestProblemList.problemLink puts in the URL).
  router.push({
    name: "problem-detail",
    params: { slug: p.slug },
    query: { contestId: ctx?.contest.value?.slug ?? "" },
  });
}

// ---------------------------------------------------------------------------
// Submit feedback (Chunk D's toast logic)
// ---------------------------------------------------------------------------
// Watch the shell store's `lastSubmitResult`. When it changes, wait
// 1.5s for the judge (typical in-house D-form is < 500ms but a
// busy queue can take longer), then refresh participation so the
// shell's score/rank/solved reflect the new state. After the
// refresh, render a contest-aware toast. The toast variant is
// driven by `res.status`:
//   - Accepted: "{delta}+ pts · {total} pts · {solved}/{total}"
//   - Wrong Answer: penalty hint
//   - Compile Error: "not scored"
//   - other / Judging: generic "scoring…" message
//
// Rapid submissions (the user retries within 1s of a previous
// submit) would otherwise trigger overlapping 1.5s waits, two
// refresh calls, and two toasts. We cancel the pending timer
// when a newer submit arrives and use an in-flight token so a
// stale refresh from a previous submit can't write over a newer
// verdict.
let pendingToast: ReturnType<typeof setTimeout> | null = null;
let inFlightToken = 0;

watch(
  () => shellStore.lastSubmitResult,
  (res) => {
    if (!res || !ctx) return;
    // Cancel any in-flight wait — the newest submit wins.
    if (pendingToast) {
      clearTimeout(pendingToast);
      pendingToast = null;
    }
    // Bump the token so a stale refresh from a previous submit
    // can no-op if it resolves after this one starts.
    const myToken = ++inFlightToken;

    const prevScore = ctx.participation.value?.score ?? 0;
    const prevSolved = ctx.participation.value?.problemsSolved ?? 0;

    pendingToast = setTimeout(async () => {
      pendingToast = null;
      try {
        await ctx.refreshParticipation();
      } catch {
        // Toast still shows the verdict; score just won't refresh.
      }
      // Drop this run if a newer submit has superseded us.
      if (myToken !== inFlightToken) return;
      const now = ctx.participation.value;
      const nowScore = now?.score ?? prevScore;
      const nowSolved = now?.problemsSolved ?? prevSolved;
      const total = now?.totalProblems ?? totalProblems.value;
      const delta = Math.max(0, nowScore - prevScore);
      // `res.status` is `SubmissionStatusKey`; only "Accepted" is
      // the canonical form. The wrong-answer / compile-error
      // branches below are case-insensitive regexes that handle
      // any legacy mixed-case variants.
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
      // Clear the trigger so a re-mount of the shell (or a future
      // identical verdict) re-fires the toast.
      shellStore.clearLastSubmit();
      void prevSolved;
    }, 1500);
  },
  { immediate: false },
);

// On unmount, cancel any pending toast so it doesn't fire after
// the user navigates away.
onBeforeUnmount(() => {
  if (pendingToast) {
    clearTimeout(pendingToast);
    pendingToast = null;
  }
});

// Initial participation refresh on mount so the score/rank reflect
// the latest data even when the user lands on a problem page
// directly via a deep link. Anonymous users (no participation
// data) are skipped silently.
onMounted(() => {
  if (!ctx?.isInContest.value) return;
  if (!isAuthed.value) return;
  void ctx.refreshParticipation();
});
</script>

<template>
  <div
    v-if="ctx?.isInContest.value && ctx.contest.value"
    class="border-b border-border bg-[var(--solarized-base3)] dark:bg-[var(--solarized-base02)] shadow-sm"
    data-testid="contest-problem-shell"
  >
    <!-- Top row: back link, title, status badge, timer, score/rank/solved -->
    <div
      class="mx-auto flex max-w-7xl items-center gap-4 px-4 py-2 font-mono"
    >
      <!-- Back link -->
      <router-link
        :to="`/contest/${ctx.contest.value.slug}`"
        class="flex items-center gap-1.5 text-2xs font-black uppercase tracking-widest text-muted-foreground transition-colors hover:text-[var(--accent-electric)]"
        :data-testid="'shell-back-to-contest'"
      >
        <ArrowLeft class="h-3.5 w-3.5" />
        <span class="hidden sm:inline">{{ t("contest.detail.backToContest") }}</span>
      </router-link>

      <div class="h-5 w-px flex-none bg-border/60" />

      <!-- Title + status badge -->
      <div class="flex min-w-0 items-center gap-2">
        <span
          class="truncate text-xs font-black text-[var(--solarized-base01)] dark:text-[var(--solarized-base1)]"
          :title="ctx.contest.value.title"
        >
          {{ ctx.contest.value.title }}
        </span>
        <ContestStatusBadge
          :status="ctx.contest.value.status"
          size="sm"
        />
      </div>

      <!-- Timer (only when there's a meaningful target) -->
      <div
        v-if="timerTargetIso"
        class="ml-auto flex items-center gap-1.5 text-2xs font-bold uppercase tracking-widest text-muted-foreground"
      >
        <Timer class="h-3.5 w-3.5" />
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

    <!-- Second row: my score / rank / solved + A/B/C nav + announcement bell -->
    <div
      class="mx-auto flex max-w-7xl items-center gap-3 border-t border-border/40 px-4 py-2 font-mono"
    >
      <!-- My score -->
      <div class="flex items-center gap-1.5 text-xxs" data-testid="shell-score">
        <Trophy class="h-3.5 w-3.5 text-[var(--terminal-amber)]" />
        <span class="text-muted-foreground">{{ t("contest.detail.shell.score") }}</span>
        <span class="font-black text-[var(--solarized-base01)] dark:text-[var(--solarized-base1)]">
          {{ isAuthed ? score : "—" }}
        </span>
      </div>

      <div class="h-4 w-px flex-none bg-border/40" />

      <!-- My rank -->
      <div class="flex items-center gap-1.5 text-xxs" data-testid="shell-rank">
        <Target class="h-3.5 w-3.5 text-[var(--accent-electric)]" />
        <span class="text-muted-foreground">{{ t("contest.detail.shell.rank") }}</span>
        <span class="font-black text-[var(--solarized-base01)] dark:text-[var(--solarized-base1)]">
          {{ isAuthed && rank != null ? `#${rank}` : "—" }}
        </span>
      </div>

      <div class="h-4 w-px flex-none bg-border/40" />

      <!-- Solved / total -->
      <div class="flex items-center gap-1.5 text-xxs" data-testid="shell-solved">
        <span class="text-muted-foreground">{{ t("contest.detail.shell.solved") }}</span>
        <span class="font-black text-[var(--terminal-green)]">
          {{ isAuthed ? solvedCount : "—" }}
        </span>
        <span class="text-muted-foreground">/ {{ totalProblems }}</span>
      </div>

      <!-- Spacer pushes the right-hand controls to the end -->
      <div class="ml-auto flex items-center gap-3">
        <!-- A/B/C problem nav (segmented control) -->
        <div
          v-if="pills.length > 0"
          class="flex items-center gap-1"
          :data-testid="'shell-problem-nav'"
        >
          <span class="text-2xs font-black uppercase tracking-widest text-muted-foreground">
            {{ t("contest.detail.shell.problemNav") }}
          </span>
          <div class="flex overflow-hidden rounded-none border border-border">
            <button
              v-for="p in pills"
              :key="p.problemId"
              type="button"
              :class="[
                'h-7 w-8 font-black text-xxs uppercase tracking-wider transition-colors cursor-pointer',
                isActivePill(p)
                  ? 'bg-[var(--accent-electric)]/15 text-[var(--accent-electric)] border-r border-border last:border-r-0'
                  : 'bg-transparent text-muted-foreground hover:bg-[var(--silver-100)]/50 dark:hover:bg-[var(--solarized-base03)]/50 border-r border-border last:border-r-0',
                !p.slug ? 'cursor-not-allowed opacity-50' : '',
              ]"
              :disabled="!p.slug"
              :data-testid="`shell-pill-${p.problemIndex}`"
              @click="goToPill(p)"
            >
              {{ p.problemIndex }}
            </button>
          </div>
        </div>

        <!-- Announcement bell — backed by ContestAnnouncementBell.
             Renders the badge from the shell store; click opens a
             popover with the contest's announcements. -->
        <ContestAnnouncementBell />
      </div>
    </div>

    <!-- Solutions-hidden hint (only when RUNNING / virtual) -->
    <div
      v-if="showSolutionsHiddenHint"
      class="border-t border-border/40 bg-[var(--silver-100)]/40 px-4 py-1.5 font-mono text-2xs text-muted-foreground dark:bg-[var(--solarized-base03)]/40"
      :data-testid="'shell-solutions-hidden-hint'"
    >
      <div class="mx-auto flex max-w-7xl items-center gap-1.5">
        <Lightbulb class="h-3 w-3 shrink-0" />
        <span>{{ t("contest.detail.solutionsHiddenHint") }}</span>
      </div>
    </div>

    <!-- Virtual-session badge — hidden when there's no active session
         (and the contest status is FINISHED). Useful for the user
         to confirm they're in a virtual replay vs the live contest. -->
    <Badge
      v-if="contestStore.isInVirtualContest && ctx.contest.value?.status === 'FINISHED'"
      variant="outline"
      class="absolute right-4 top-2 hidden"
    >
      VIRTUAL
    </Badge>
  </div>
</template>
