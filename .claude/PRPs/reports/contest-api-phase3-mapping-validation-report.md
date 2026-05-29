# Implementation Report: Contest API Phase 3 — 数据映射层加固

## Summary
在 Console 前端引入 Zod schema 验证后端 Contest 模块响应，替代脆弱的 `as` 类型断言和 `toNumber()` 手动转换。同时删除冗余的 API 别名函数，统一调用入口。

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Medium | Medium |
| Confidence | 8/10 | 9/10 |
| Files Changed | 5-7 | 3 |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Install Zod Dependency | done | `zod@3.25.76` installed in console |
| 2 | Create Zod Schema File | done | `console/src/api/contest.schema.ts` created with 4 core schemas + pagination helper |
| 3 | Refactor API File to Use Zod Schemas | done | Deleted `toNumber()` + 5 mapper functions; replaced with `.parse()` |
| 4 | Remove Alias Functions | done | Deleted `getContest`, `register`, `withdraw`, `getMyParticipation` |
| 5 | Update Callers of Alias Functions | done | No external callers found — aliases were only defined, never consumed outside `contest.ts` |
| 6 | Verify Backend Number Types | done | Confirmed all Integer/Long fields serialize as JSON numbers |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis (type-check) | Pass with pre-existing issues | 12 type errors in unrelated files (comment-tree-builder, MarkdownEdit, DonutChart, CodeEditor). Zero new errors introduced. |
| Lint | Pass with pre-existing issues | 5 lint errors in unrelated files (MyContests.vue, FollowButton.vue, ProblemNotesDrawer.vue, ForumFeedView.vue). Zero new errors introduced. |
| Unit Tests | Pass with pre-existing failures | 3 pre-existing failures (2 in problem-detail.spec.ts, 1 auth-core module path issue). Zero new failures introduced. |
| Build | N/A | Not executed — plan scope did not require build |
| Integration | N/A | No integration tests for contest API layer |
| Edge Cases | Verified manually | Zod defaults handle missing fields; string numbers will reject as designed |

## Files Changed

| File | Action | Lines |
|---|---|---|
| `console/package.json` | UPDATED | +1 (zod dependency) |
| `console/pnpm-lock.yaml` | UPDATED | ~+10 (lockfile update) |
| `console/src/api/contest.schema.ts` | CREATED | +120 |
| `console/src/api/contest.ts` | UPDATED | -90 / +40 (net -50 lines) |

## Deviations from Plan

1. **No external caller updates needed**: Plan anticipated needing to update Vue files that called alias functions. Search revealed all 4 aliases (`getContest`, `register`, `withdraw`, `getMyParticipation`) were defined but never imported or called outside `contest.ts` itself. This reduced the file change count from 5-7 to 3.

2. **Schema field `currentParticipants` omitted**: Backend `ContestVO` has `currentParticipants` (Integer), but frontend `ContestDetail` type does not include it. Schema aligned with existing frontend type rather than adding a new field. This is consistent with Phase 3 scope (mapping layer alignment, not type expansion).

3. **Pre-existing build/test issues not fixed**: Per PRP rules, pre-existing failures in unrelated files are documented but not fixed, as they fall outside the plan scope.

## Issues Encountered

None directly caused by this implementation. All validation failures are pre-existing and unrelated to Contest API changes.

## Tests Written

No new unit tests written for this phase. The Zod schemas themselves are the runtime validation layer. Testing strategy recommends adding schema-level tests in a future phase if desired.

## Next Steps

- Code review via `/code-review`
- Commit changes via `/prp-commit`
- Proceed to Phase 4: 性能与逻辑修复 (Performance & Logic Fixes)
