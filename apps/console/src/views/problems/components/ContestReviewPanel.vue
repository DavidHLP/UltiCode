<script setup lang="ts">
/**
 * ContestReviewPanel
 *
 * Rendered below the ContestProblemShell on the problem page when
 * the contest is FINISHED (and the URL still carries ?contestId=...).
 * Per PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md §P2-1, this is the
 * "post-game review mode" — the user's submission history for the
 * problem, with quick paths to retake or open the notebook.
 *
 * What it shows:
 *  - Summary row: 首次 AC 时间, 错因分布 (WA/TLE/RE/CE counts),
 *    最终得分 (from participation).
 *  - Submission timeline (latest first), each row: createdAt,
 *    status, runtime/memory, score (when present).
 *  - Two CTAs:
 *      "重新练习" → /problems/{slug} (drops ?contestId=...)
 *      "加入错题本" → opens the existing ProblemNotesDrawer
 *
 * Submissions come from `fetchContestProblemSubmissions(contestId,
 * problemId)` which already exists; no new endpoint.
 */
import { computed, inject, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import { RefreshCw, BookPlus, CheckCircle2, Clock } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { fetchContestProblemSubmissions } from "@/api/contest";
import { ContestProblemContextKey, ToggleNotesKey } from "../problem-context";
import type { SubmissionRecord } from "@/types/submission";

const { t } = useI18n();
const ctx = inject(ContestProblemContextKey, null);
const route = useRoute();
// Use the existing `toggleNotes` provided by ProblemDetailView via
// `ToggleNotesKey`. We expose the drawer from the page; this
// component just calls its toggle.
const toggleNotes = inject(ToggleNotesKey, () => {});

interface VerdictBreakdown {
  total: number;
  accepted: number;
  wrongAnswer: number;
  timeLimit: number;
  runtime: number;
  compileError: number;
  other: number;
}

const submissions = ref<SubmissionRecord[]>([]);
const loading = ref(false);

const slug = computed(() => {
  const s = route.params.slug;
  return Array.isArray(s) ? s[0] ?? null : s ?? null;
});
// Use `contestProblemNav.current.problemId` (already computed by
// the composable) instead of reaching into a non-existent
// `ctx.problem` field — the new contest context only exposes the
// list and the nav, not a single problem.
const problemId = computed(
  () => ctx?.contestProblemNav.value.current?.problemId ?? null,
);
const contestIdForFetch = computed(() => ctx?.contestId.value ?? null);

const firstAccepted = computed(() => {
  // SubmissionStatusKey does not include "ACCEPTED" — only "Accepted".
  // (Vue-i18n style guides use the lowercase canonical form here.)
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
    else if (/compile[ _-]?error/i.test(v) || /^ce$/i.test(v)) out.compileError++;
    else out.other++;
  }
  return out;
});

const finalScore = computed(
  () => ctx?.participation.value?.score ?? null,
);

async function load(): Promise<void> {
  const cid = contestIdForFetch.value;
  const pid = problemId.value;
  if (cid == null || pid == null) return;
  loading.value = true;
  try {
    submissions.value = await fetchContestProblemSubmissions(cid, pid);
  } catch {
    submissions.value = [];
  } finally {
    loading.value = false;
  }
}

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

function retakeHref(): string | null {
  if (!slug.value) return null;
  return `/problems/${slug.value}`;
}

function handleRetake(): void {
  const href = retakeHref();
  if (!href) return;
  window.location.assign(href);
}

function handleNotebook(): void {
  toggleNotes();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div
    v-if="ctx?.isInContest.value && ctx.contest.value?.status === 'FINISHED'"
    class="border-b border-border bg-[var(--surface-highlight)]/40 dark:bg-background/40"
    data-testid="contest-review-panel"
  >
    <div class="mx-auto max-w-7xl px-4 py-3 font-mono">
      <!-- Summary row -->
      <div
        class="grid grid-cols-1 gap-3 sm:grid-cols-3"
        data-testid="contest-review-summary"
      >
        <!-- First AC time -->
        <div class="flex items-start gap-2">
          <CheckCircle2
            v-if="firstAccepted"
            class="mt-0.5 h-4 w-4 shrink-0 text-[var(--status-success-mark)]"
          />
          <Clock v-else class="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
          <div>
            <p
              class="text-2xs font-black uppercase tracking-widest text-muted-foreground"
            >
              {{ t("contest.review.firstACLabel") }}
            </p>
            <p class="text-xs font-bold">
              {{
                firstAccepted
                  ? formatTime(firstAccepted.created_at)
                  : "—"
              }}
            </p>
          </div>
        </div>

        <!-- Verdict breakdown -->
        <div class="flex items-start gap-2">
          <Clock class="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
          <div>
            <p
              class="text-2xs font-black uppercase tracking-widest text-muted-foreground"
            >
              {{ t("contest.review.verdictBreakdown", {
                n: breakdown.total,
                wa: breakdown.wrongAnswer,
                tle: breakdown.timeLimit,
                re: breakdown.runtime,
                ce: breakdown.compileError,
              }) }}
            </p>
            <p class="text-xs font-bold text-[var(--foreground-strong)]">
              {{ breakdown.accepted }} / {{ breakdown.total }}
              <span class="text-2xs text-muted-foreground">AC</span>
            </p>
          </div>
        </div>

        <!-- Final score -->
        <div class="flex items-start gap-2">
          <CheckCircle2 class="mt-0.5 h-4 w-4 shrink-0 text-[var(--status-warning-mark)]" />
          <div>
            <p
              class="text-2xs font-black uppercase tracking-widest text-muted-foreground"
            >
              {{ t("contest.review.finalScoreLabel") }}
            </p>
            <p class="text-xs font-bold">
              {{ finalScore != null ? finalScore : "—" }}
            </p>
          </div>
        </div>
      </div>

      <!-- Submission timeline -->
      <ul
        v-if="submissions.length > 0"
        class="mt-3 max-h-48 overflow-y-auto border border-border/40 bg-surface dark:bg-surface-highlight"
        data-testid="contest-review-timeline"
      >
        <li
          v-for="(s, idx) in submissions"
          :key="idx"
          class="grid grid-cols-12 items-center gap-2 border-b border-border/30 px-3 py-1.5 text-xxs last:border-b-0"
        >
          <span class="col-span-3 font-mono text-muted-foreground">
            {{ formatTime(s.created_at) }}
          </span>
          <span class="col-span-3 font-black">
            {{ s.status }}
          </span>
          <span class="col-span-2 font-mono text-muted-foreground">
            {{ s.runtime ?? 0 }}ms
          </span>
          <span class="col-span-2 font-mono text-muted-foreground">
            {{ s.memory ?? 0 }}KB
          </span>
          <span class="col-span-2 text-right font-black text-[var(--foreground-strong)]">
            <!-- Score lives on the nested `contest_info` for contest
                 submissions; non-contest submissions don't have a
                 score at all. -->
            {{ s.contest_info?.score != null ? `+${s.contest_info.score}` : "—" }}
          </span>
        </li>
      </ul>

      <!-- CTAs -->
      <div class="mt-3 flex flex-wrap items-center gap-2">
        <Button
          variant="default"
          size="sm"
          class="h-8 rounded-none border border-[var(--primary)]/40 bg-[var(--primary)]/15 px-3 font-black text-2xs uppercase tracking-widest text-[var(--primary)] hover:bg-[var(--primary)]/25 cursor-pointer"
          :disabled="!retakeHref()"
          data-testid="contest-review-retake"
          @click="handleRetake"
        >
          <RefreshCw class="mr-1 h-3 w-3" />
          {{ t("contest.review.retake") }}
        </Button>
        <Button
          variant="outline"
          size="sm"
          class="h-8 rounded-none border border-border bg-transparent px-3 font-black text-2xs uppercase tracking-widest text-muted-foreground hover:bg-[var(--surface-highlight)] hover:text-foreground dark:hover:bg-background cursor-pointer"
          data-testid="contest-review-notebook"
          @click="handleNotebook"
        >
          <BookPlus class="mr-1 h-3 w-3" />
          {{ t("contest.review.addToNotebook") }}
        </Button>
      </div>
    </div>
  </div>
</template>