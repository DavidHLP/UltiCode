import type { InjectionKey, Ref } from "vue";
import type { ProblemDetail } from "@/types/problem-detail";
import type { ProblemRunResult } from "@/types/test-results";

export interface ProblemContext {
  problem: Ref<ProblemDetail | null>;
  runResult: Ref<ProblemRunResult | null>;
  contestId: Ref<string | null>;
}

export const ProblemContextKey: InjectionKey<ProblemContext> =
  Symbol("ProblemContext");

export const ToggleSidePanelKey: InjectionKey<() => void> =
  Symbol("ToggleSidePanel");

export const ToggleNotesKey: InjectionKey<() => void> = Symbol("ToggleNotes");

/**
 * Provided by ProblemDetailView when `route.query.contestId` is set.
 * Consumed by LayoutHeaderLeft (to swap site-wide nav for contest
 * nav), ContestProblemDock (toolbar contest summary, problem nav,
 * review actions) and the announcement bell.
 *
 * Provided alongside the existing `ProblemContextKey`; consumers
 * should treat absence as "not in a contest" and fall back to
 * non-contest behaviour. See `useContestProblemContext.ts` for the
 * full type definition.
 */
export type ContestProblemContextValue =
  import("@/views/problems/composables/useContestProblemContext").UseContestProblemContext;

export const ContestProblemContextKey: InjectionKey<ContestProblemContextValue> =
  Symbol("ContestProblemContext");
