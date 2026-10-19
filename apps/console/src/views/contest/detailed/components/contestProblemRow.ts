/**
 * Pure helpers for the ContestProblemList SFC.
 *
 * `<script setup>` doesn't allow `export`, so this file lives next
 * to the SFC and is imported by both the SFC (for the template) and
 * the unit tests (no Vue mount needed).
 *
 * The functions are deliberately pure and dependency-free so they
 * can be unit-tested in isolation and (later) re-used by other
 * surfaces (e.g. the "我的提交" list under a finished contest).
 */

export type RowAction = "start" | "continue" | "view" | "locked" | "review";

export type ProblemStatus = "solved" | "attempted" | "todo";

/**
 * Decide the per-row primary action label.
 *
 *   contest.status  problem.status   → action
 *   --------------------------------------------
 *   UPCOMING        *                → "locked"   (any problem pre-start)
 *   FINISHED        *                → "review"   (post-game: every
 *                                              problem is reviewable,
 *                                              regardless of personal
 *                                              state — see the product
 *                                              decision in
 *                                              PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md
 *                                              §P0-3)
 *   *               "solved"         → "view"     (already AC'd; show
 *                                              the existing solution
 *                                              + your history)
 *   *               "attempted"      → "continue" (you've submitted but
 *                                              not AC'd; pick up where
 *                                              you left off)
 *   *               "todo"           → "start"    (untouched)
 */
export function getRowAction(
  contestStatus: string,
  problemStatus: ProblemStatus,
): RowAction {
  if (contestStatus === "UPCOMING") return "locked";
  if (contestStatus === "FINISHED") return "review";
  if (problemStatus === "solved") return "view";
  if (problemStatus === "attempted") return "continue";
  return "start";
}

/**
 * The problem API supplies a 0..100 percentage; keep its unit and
 * render one decimal. Missing or non-finite values fall back to 0%.
 */
export function formatAcceptanceRate(rate: number | null | undefined): string {
  const r = typeof rate === "number" && Number.isFinite(rate) ? rate : 0;
  return `${r.toFixed(1)}%`;
}
