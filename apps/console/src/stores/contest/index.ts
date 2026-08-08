// console/src/stores/contest/index.ts
//
// Contest stores organization (architecture-review 2026-07-21, C3 RETAIN):
//
// Six contest-related Pinia stores exist across two locations:
//   - stores/contestBrowse.ts        — contest list / browse view
//   - stores/contestDetail.ts        — single contest detail view
//   - stores/contestRanking.ts       — GLOBAL rankings + user contest history
//   - stores/virtualContest.ts       — virtual contest timer / session
//   - stores/contestProblemShell.ts  — cross-component signal bus for the
//                                      contest problem page (LayoutHeaderCenter
//                                      submit -> ContestProblemDock refresh +
//                                      toast; contest-scoped announcement
//                                      unread counter). No network state; the
//                                      authoritative responsibility docstring
//                                      lives in that file's header.
//   - stores/contest/rankingStore.ts — PER-CONTEST live ranking (frozen state)
//
// The split between stores/ root and stores/contest/ subdir is intentional:
// the five root-level stores back top-level routes and cross-component
// signalling fans; rankingStore is consumed only inside the contest detail
// view's live-ranking subsurface. The naming collision (contestRanking vs
// rankingStore) reflects two genuinely different responsibilities — global
// ranking history vs per-contest live ranking with a frozen scoreboard.
//
// Consolidation trigger: if contest-related stores grow to 9+, collect them
// all under stores/contest/ with a real barrel and rename to disambiguate
// the two ranking responsibilities. At six, the scatter is cheaper than a
// migration.
export { useRankingStore } from "./rankingStore";
