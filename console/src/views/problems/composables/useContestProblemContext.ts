/**
 * useContestProblemContext
 *
 * Centralises "what contest am I in, and is the current problem part of it"
 * for the problem page. Consumed by LayoutHeaderLeft (to swap the
 * site-wide prev/next/random nav for a contest-scoped one), the
 * ContestProblemDock, and the announcement bell.
 *
 * Data sources (all from the contest detail store; no new endpoints):
 *   - `currentContest`            (loaded via loadContestDetail)
 *   - `userParticipation` map     (loaded via loadParticipationStatus)
 *   - `contestProblems` map       (loaded via loadProblems — hoisted
 *                                  into stores/contestDetail.ts from
 *                                  the local ref that used to live in
 *                                  ContestDetailView)
 *
 * URL contract: `route.query.contestId` is the DB id (backend
 * `resolveContestId` returns it in every response). `route.params.slug`
 * is the URL form. All matching is done on the DB id. See R10.6.1 in
 * ContestDetailView for the slug-vs-id drift history.
 */
import { computed, ref, watch, type ComputedRef, type Ref } from "vue";
import { storeToRefs } from "pinia";
import { useRoute } from "vue-router";
import { useContestDetailStore } from "@/stores/contestDetail";
import { useAuthStore } from "@/stores/auth";
import type {
  ContestDetail,
  ContestProblemSummary,
  ParticipationStatus,
} from "@/types/contest";
import type { ProblemDetail } from "@/types/problem-detail";

export interface ContestProblemNav {
  prev: ContestProblemSummary | null;
  next: ContestProblemSummary | null;
  current: ContestProblemSummary | null;
}

export interface UseContestProblemContext {
  contestId: Ref<string | null>;
  contest: Ref<ContestDetail | null>;
  participation: Ref<ParticipationStatus | null>;
  problems: Ref<ContestProblemSummary[]>;
  contestProblemNav: ComputedRef<ContestProblemNav>;
  isInContest: ComputedRef<boolean>;
  /**
   * `null` while loading or when the route has no `?contestId=`.
   * `true` once we confirm the current problem is in the contest's
   * problem list, `false` if we have proof it isn't.
   * The false-while-still-loading is `null` so the UI can show a
   * neutral state instead of flashing the "not in contest" error.
   */
  problemBelongsToContest: ComputedRef<boolean | null>;
  refreshParticipation: () => Promise<void>;
  refreshProblems: () => Promise<void>;
}

export function useContestProblemContext(
  problem: Ref<ProblemDetail | null>,
): UseContestProblemContext {
  const route = useRoute();
  const contestStore = useContestDetailStore();
  const authStore = useAuthStore();

  const { currentContest, userParticipation, contestProblems } =
    storeToRefs(contestStore);

  // The route is the source of truth for "are we in a contest problem";
  // it can be cleared by the contest-list page when the contest ends.
  const contestId = computed<string | null>(() => {
    const v = route.query.contestId;
    if (Array.isArray(v)) return v[0] ?? null;
    return typeof v === "string" && v.length > 0 ? v : null;
  });

  const isInContest = computed(() => contestId.value !== null);

  // Local mirror of `userParticipation.get(contestId)` so consumers
  // can read it as a `Ref<...|null>` instead of poking the map. The
  // store's map is keyed by the URL slug, matching `route.query.contestId`.
  //
  // Map mutations (`store.userParticipation.value.set(...)`) don't
  // trigger Vue's watch on `() => store.userParticipation.value`,
  // because the Map's *reference* is unchanged. We get around this
  // by re-reading the map *after* the store actions complete in
  // the loader watch below; the watch here only handles the
  // `contestId` change as a fallback.
  const participation = ref<ParticipationStatus | null>(null);
  watch(
    contestId,
    (id: string | null) => {
      if (!id) {
        participation.value = null;
        return;
      }
      participation.value = userParticipation.value.get(id) ?? null;
    },
    { immediate: true },
  );

  // Same mirror pattern for problems.
  const problems = ref<ContestProblemSummary[]>([]);
  watch(
    contestId,
    (id: string | null) => {
      problems.value = id ? (contestProblems.value.get(id) ?? []) : [];
    },
    { immediate: true },
  );

  // Drive the store actions whenever the route's contestId changes.
  // The store actions are idempotent (cached) so re-running them is
  // cheap; this keeps the problem page self-sufficient and means a
  // deep link to /problems/:slug?contestId=... still works even if
  // the user never opened the contest detail page.
  watch(
    contestId,
    async (id: string | null) => {
      if (!id) return;
      // Only refetch if the store doesn't already have the data — this
      // makes navigating from the contest detail to a problem page a
      // zero-network transition.
      //
      // We compare on the URL-stable `slug` (the same value the URL
      // `?contestId=` query carries) rather than `currentContest.value.id`
      // (the DB primary key), because the store's `loadContestDetail`
      // is called with the URL slug and the response contains the DB
      // id as `id`. See R10.6.1 notes in ContestDetailView.
      if (currentContest.value?.slug !== id) {
        try {
          await contestStore.loadContestDetail(id);
        } catch {
          // Surfaced as a generic error elsewhere; the page can still
          // render the "not in contest" guard based on the empty
          // problems list.
        }
      }
      try {
        await contestStore.loadProblems(id);
      } catch {
        // Same: failed load leaves `problems` empty and the
        // `problemBelongsToContest` check will return false.
      }
      if (authStore.isAuthenticated) {
        try {
          await contestStore.loadParticipationStatus(id);
        } catch {
          // Participation is best-effort; an anonymous or rate-limited
          // request shouldn't break the page.
        }
      }
      // Re-read the store maps *after* the awaited loads complete.
      // Map mutations don't propagate through Vue's watch system
      // (the Map reference is unchanged), so we have to push the
      // values into our local mirrors explicitly. This also makes
      // the local refs the source of truth for downstream consumers
      // like `problemBelongsToContest`.
      problems.value = contestProblems.value.get(id) ?? [];
      participation.value = userParticipation.value.get(id) ?? null;
    },
    { immediate: true },
  );

  // Contest-scoped prev/next. We sort by `problemIndex` lexically — the
  // backend returns A, B, C (single letter) for typical contests, so
  // string sort matches the intended order without a new endpoint.
  // If a contest has two-digit indices (AA, AB, ...) we'll need a
  // numeric `order` field; for now the problem page covers the common
  // case (≤ 26 problems) and a fallback `null` next/prev disables the
  // arrow without throwing.
  const contestProblemNav = computed<ContestProblemNav>(() => {
    const list = [...problems.value].sort((a, b) =>
      a.problemIndex.localeCompare(b.problemIndex),
    );
    const pid = problem.value?.id;
    const current =
      list.find((p: ContestProblemSummary) => p.problemId === pid) ?? null;
    if (!current) return { prev: null, next: null, current: null };
    const idx = list.indexOf(current);
    return {
      prev: idx > 0 ? (list[idx - 1] ?? null) : null,
      next: idx < list.length - 1 ? (list[idx + 1] ?? null) : null,
      current,
    };
  });

  // null  = still loading or no contest in URL
  // true  = loaded AND current problem is part of this contest
  // false = loaded AND current problem is NOT in this contest's list
  const problemBelongsToContest = computed<boolean | null>(() => {
    if (!isInContest.value) return null;
    if (contestId.value && !currentContest.value) return null; // still loading
    const pid = problem.value?.id;
    if (pid == null) return null;
    if (problems.value.length === 0) return null; // problems not loaded yet
    return problems.value.some(
      (p: ContestProblemSummary) => p.problemId === pid,
    );
  });

  async function refreshParticipation(): Promise<void> {
    if (!contestId.value) return;
    if (!authStore.isAuthenticated) return;
    await contestStore.loadParticipationStatus(contestId.value);
  }

  async function refreshProblems(): Promise<void> {
    if (!contestId.value) return;
    await contestStore.loadProblems(contestId.value);
  }

  return {
    contestId,
    contest: currentContest,
    participation,
    problems,
    contestProblemNav,
    isInContest,
    problemBelongsToContest,
    refreshParticipation,
    refreshProblems,
  };
}
