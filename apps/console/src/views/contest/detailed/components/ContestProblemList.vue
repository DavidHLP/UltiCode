<script setup lang="ts">
import { computed } from "vue";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import { Badge } from "@ulticode/design-system/ui/badge";
import { Button } from "@ulticode/design-system/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@ulticode/design-system/ui/table";
import { Lock, Target, Award, Check, Play } from "@lucide/vue";
import { useI18n } from "vue-i18n";
import type { ContestDetail, ContestProblemSummary } from "@/types/contest";
import {
  formatAcceptanceRate,
  getRowAction,
  type ProblemStatus,
  type RowAction,
} from "./contestProblemRow";

const props = defineProps<{
  contest: ContestDetail;
  problems: ContestProblemSummary[];
  contestId: string;
  isRegistered: boolean;
  registering: boolean;
  getDifficultyColor: (difficulty: string) => string;
  problemStatuses?: Record<number, ProblemStatus>;
  // Whether the current viewer has an active virtual contest session on
  // this contest. Needed in addition to `contest.status` because virtual
  // contests are per-user replays of contests whose `status` is typically
  // already `FINISHED`. Without this signal the Solutions tab would be
  // exposed mid-replay, defeating the anti-cheat invariant documented at
  // `useProblemLayout.ts:60-62`.
  isInVirtualSession?: boolean;
}>();

const emit = defineEmits<{
  (e: "register"): void;
}>();

const { t } = useI18n();

const getProblemStatus = (problemId: number): ProblemStatus => {
  return props.problemStatuses?.[problemId] || "todo";
};

// Only attach ?contestId=... while the contest is actually live. Once the
// contest is finished (FINISHED) or never started (UPCOMING is locked
// above), the problem-detail layout treats the URL query as an opaque
// "contest mode" signal and hides the Solutions tab to keep competitors
// from peeking at editorial write-ups. We must therefore drop the query
// once the contest ends so users can browse solutions normally.
//
// We additionally keep solutions hidden while the viewer has an active
// virtual contest session on THIS contest — virtual contests are
// per-user replays of contests whose underlying `status` is already
// `FINISHED`, so status alone would expose the Solutions tab mid-replay
// and bypass the anti-cheat invariant.
//
// R10.6 / anti-cheat fix: do NOT also require `props.contest.isVirtual`.
// That field is the static "is this a virtual-only contest" flag on the
// Contest entity (DB `contests.is_virtual`) and is *false* for any
// real, scheduled contest — including ones the user is virtually
// replaying. Conflating "user has a virtual session" with "the contest
// itself is virtual-only" was the H1 regression in d219bd8c6: every
// virtual replay of a real contest would drop `?contestId=...` and
// expose editorial write-ups. The `isInVirtualSession` prop is supplied
// by `ContestDetailView` and is already scoped to this contestId (it
// checks `virtualSession?.contestId === contestId` upstream), so
// trusting it alone is safe.
const contestIsLive = computed(
  () => props.contest.status === "RUNNING" || props.isInVirtualSession === true,
);

// Returned as a typed RouteLocationRaw-style object so vue-router picks up
// the absence of a query string when the contest is not live.
function problemLink(slug: string) {
  return {
    name: "problem-detail" as const,
    params: { slug },
    query: contestIsLive.value ? { contestId: props.contestId } : undefined,
  };
}

// Resolved per-row action (label + i18n key + button variant).
type RowVariant =
  "default" | "outline" | "destructive" | "secondary" | "ghost" | "link";

function rowAction(problemId: number): {
  key: RowAction;
  label: string;
  variant: RowVariant;
} {
  const action = getRowAction(
    props.contest.status,
    getProblemStatus(problemId),
  );
  // i18n key per action; "review" is a separate branch so post-game
  // users get a distinct CTA even when the personal status is "todo".
  const i18nKey = `contest.detail.row.${action}`;
  const label = t(i18nKey);
  // "locked" is the only disabled state; the rest are interactive
  // (the click navigates to the problem page just like the row
  // itself does).
  return {
    key: action,
    label,
    variant: action === "locked" ? "outline" : "default",
  };
}
</script>

<template>
  <Card id="contest-problems" class="overflow-hidden">
    <CardHeader class="pb-3">
      <CardTitle>{{ t("contest.detail.challenges") }}</CardTitle>
    </CardHeader>
    <CardContent class="p-0">
      <div
        v-if="contest.status === 'UPCOMING'"
        class="flex flex-col items-center justify-center gap-4 px-6 py-12 text-center"
      >
        <div
          class="flex h-16 w-16 items-center justify-center rounded-lg bg-accent dark:bg-background border border-border text-muted-foreground"
        >
          <Lock class="h-6 w-6" />
        </div>
        <div class="space-y-2">
          <h3 class="text-base font-bold text-foreground">
            {{ t("contest.detail.problemsLocked") }}
          </h3>
          <p class="text-xs text-muted-foreground max-w-sm">
            {{ t("contest.detail.problemsUnlockHint") }}
          </p>
        </div>
        <Button
          v-if="!isRegistered"
          variant="outline"
          class="px-6 cursor-pointer"
          :disabled="registering"
          @click="emit('register')"
        >
          {{ t("contest.detail.register") }}
        </Button>
      </div>

      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead class="w-20 pl-6 h-10">#</TableHead>
            <TableHead class="h-10">{{
              t("contest.detail.problemHeaders.title")
            }}</TableHead>
            <TableHead class="w-32 h-10">{{
              t("contest.detail.problemHeaders.difficulty")
            }}</TableHead>
            <TableHead class="w-24 text-center h-10">{{
              t("contest.detail.problemHeaders.score")
            }}</TableHead>
            <TableHead class="w-32 text-center h-10">{{
              t("contest.detail.problemHeaders.acceptance")
            }}</TableHead>
            <TableHead class="w-32 pr-6 text-right h-10">{{
              t("contest.detail.problemHeaders.action")
            }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="problem in problems"
            :key="problem.id"
            class="group cursor-pointer dark:hover:bg-background/30 last:border-b-0"
            @click="problem.slug && $router.push(problemLink(problem.slug))"
          >
            <TableCell class="pl-6 py-3">
              <!-- Solved Status Block Indicator -->
              <div
                v-if="getProblemStatus(problem.problemId) === 'solved'"
                class="flex h-9 w-9 items-center justify-center rounded-lg border font-mono text-xs font-black transition-all bg-status-success-mark/10 text-foreground border-status-success-mark/35 shadow-sm"
                :title="t('contest.detail.row.solved')"
              >
                <Check class="h-4.5 w-4.5 stroke-[3]" />
              </div>
              <div
                v-else-if="getProblemStatus(problem.problemId) === 'attempted'"
                class="flex h-9 w-9 items-center justify-center rounded-lg border font-mono text-xs font-black transition-all bg-status-warning-mark/10 text-foreground border-status-warning-mark/35 shadow-sm"
                :title="t('contest.detail.row.attempted', { n: 0 })"
              >
                {{ problem.problemIndex || "?" }}
              </div>
              <div
                v-else
                class="flex h-9 w-9 items-center justify-center rounded-lg border font-mono text-xs font-black transition-all bg-accent dark:bg-background text-muted-foreground border-border/40 group-hover:bg-background dark:group-hover:bg-accent"
                :title="t('contest.detail.row.notStarted')"
              >
                {{ problem.problemIndex || "#" }}
              </div>
            </TableCell>

            <TableCell class="py-3">
              <div class="space-y-0.5">
                <router-link
                  v-if="problem.slug"
                  :to="problemLink(problem.slug)"
                  class="text-base font-bold text-foreground dark:text-foreground group-hover:text-primary transition-colors"
                  @click.stop
                >
                  {{ problem.title }}
                </router-link>
                <span v-else class="text-base font-bold text-foreground">{{
                  problem.title
                }}</span>
                <div
                  class="flex items-center gap-3 text-xs font-bold text-muted-foreground uppercase tracking-wider"
                >
                  <span class="flex items-center gap-1 font-mono">
                    <Target class="h-3.5 w-3.5" />
                    <!--
                      "全场 X 人通过" / "全场 X 次提交" — the backend
                      returns counts of *other* contestants, not personal
                      attempts. We render them as 全场 (overall) so the
                      user understands this is field-wide data, not
                      their own.
                    -->
                    {{
                      t("contest.detail.row.solvedByAll", {
                        n: problem.solvedCount || 0,
                      })
                    }}
                  </span>
                  <span class="font-mono">{{
                    t("contest.detail.row.totalSubmissions", {
                      n: problem.submissionCount || 0,
                    })
                  }}</span>
                </div>
              </div>
            </TableCell>

            <TableCell class="py-3">
              <Badge
                variant="outline"
                :class="[
                  getDifficultyColor(problem.difficulty || 'Medium'),
                  'font-black text-xs uppercase tracking-wider h-5 px-2 rounded-lg border-current/25 bg-current/5',
                ]"
              >
                {{
                  t(
                    `problem.difficulty.${(problem.difficulty || "medium").toLowerCase()}`,
                  )
                }}
              </Badge>
            </TableCell>

            <TableCell class="text-center py-3">
              <span
                class="inline-flex items-center gap-1 font-black text-sm text-foreground font-mono"
              >
                <Award class="h-4.5 w-4.5" />
                {{ problem.score || 0 }}
              </span>
            </TableCell>

            <TableCell class="text-center py-3">
              <span
                class="text-xs font-bold text-muted-foreground dark:text-muted-foreground font-mono"
                :data-testid="`acceptance-rate-${problem.problemId}`"
              >
                {{ formatAcceptanceRate(problem.acceptanceRate) }}
              </span>
            </TableCell>

            <TableCell class="pr-6 py-3 text-right">
              <Button
                v-if="problem.slug"
                :variant="rowAction(problem.problemId).variant"
                :disabled="rowAction(problem.problemId).key === 'locked'"
                :class="[
                  'rounded-lg px-3 h-8 font-black text-xs uppercase tracking-widest cursor-pointer',
                  rowAction(problem.problemId).key === 'review'
                    ? 'border border-border bg-accent/60 hover:bg-accent text-foreground dark:text-foreground dark:bg-background/60'
                    : rowAction(problem.problemId).key === 'continue'
                      ? 'bg-status-warning-mark/15 border border-status-warning-mark/40 text-foreground hover:bg-status-warning-mark/25'
                      : rowAction(problem.problemId).key === 'locked'
                        ? 'border border-border bg-accent/40 text-muted-foreground cursor-not-allowed'
                        : 'bg-primary/15 border border-primary/40 text-primary hover:bg-primary/25',
                ]"
                :data-testid="`row-action-${problem.problemId}`"
                @click.stop="$router.push(problemLink(problem.slug!))"
              >
                <Play
                  v-if="rowAction(problem.problemId).key === 'start'"
                  class="h-3 w-3 mr-1"
                />
                {{ rowAction(problem.problemId).label }}
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
