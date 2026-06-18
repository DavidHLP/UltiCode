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
 * Render a 0..1 acceptance fraction as a 1-decimal percent string.
 *
 *   formatAcceptanceRate(0.732)  → "73.2%"
 *   formatAcceptanceRate(1)      → "100.0%"
 *   formatAcceptanceRate(null)   → "0.0%"    (defensive fallback)
 *
 * The backend returns a fraction per the ContestProblemSummary type
 * contract; we render with one decimal place so the table doesn't
 * flicker "73.2357%" with full precision. Non-finite or nullish
 * values fall back to 0% so the table never shows "NaN%".
 */
export function formatAcceptanceRate(
  rate: number | null | undefined,
): string {
  const r = typeof rate === "number" && Number.isFinite(rate) ? rate : 0;
  return `${(r * 100).toFixed(1)}%`;
}
