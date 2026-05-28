## Implementation Plan: Problem Detail Tabs Frontend-Backend Alignment

### Task Type
- [x] Fullstack (Frontend + Backend)

### Technical Solution
Synthesize Codex backend analysis + Gemini frontend analysis. Fix contract-boundary problems first (P0), then DTO lightweighting (P1), then architectural DTO separation (P2). Each sprint is independently shippable.

### Implementation Steps

#### Sprint 0: Contract Smoke Tests (Foundation)
1. **Add backend contract smoke tests** in `ProblemDetailTabContractIT`:
   - Anonymous `GET /problems/slug/two-sum` -> 200
   - Anonymous `GET /api/problems/1/solutions` -> 200 (will fail until Sprint 1 fix)
   - Anonymous `GET /problems/1/submissions` -> 401
   - Anonymous `GET /submissions/statuses` -> 200
2. **Add frontend API unit tests**:
   - `mapProblemDetail()` prefers `detail.content`
   - `fetchSolutionFeed()` parses `PageResult`
   - `mapSubmission()` handles camel/snake
3. **Run tests to confirm RED state** before implementation.

#### Sprint 1: P0 Contract Break Fixes (Immediate Ship)
4. **Backend: SecurityConfig GET-only matcher for solutions list**
   - File: `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java`
   - Add `requestMatchers(HttpMethod.GET, "/api/problems/*/solutions").permitAll()`
   - Keep existing `PUBLIC_ENDPOINTS` unchanged; use method-specific matcher to avoid over-permissioning POST/DELETE
5. **Backend + Frontend: Align solution update method**
   - Frontend: Add `apiPut()` to `console/src/utils/request.ts`, change `updateSolution()` in `console/src/api/solution.ts` to use `apiPut`
   - Backend: Add `@PatchMapping("/api/solutions/{id}")` alias to `SolutionController.update()` to future-proof partial updates
   - This makes both PUT and PATCH work; frontend uses PUT (aligned with current backend primary)
6. **Frontend: MobileProblemLayout route sync**
   - File: `console/src/views/problems/components/MobileProblemLayout.vue`
   - Read `useRoute().params.tab` to initialize `activeTab`
   - Watch `route.params.tab` -> update `activeTab`
   - Watch `activeTab` -> `router.push` with tab param
   - Use guard flags (isUpdatingFromRoute / isUpdatingFromStore) to prevent infinite loops, mirroring `useProblemLayout.ts`
7. **Frontend: Fix SubmissionsListView inverted caption**
   - File: `console/src/views/problems/submissions/SubmissionsListView.vue`
   - Change `TableCaption` to show `noSubmissionsDesc` only when `decoratedSubmissions.length === 0`
8. **Frontend: Run button auth guard**
   - File: `console/src/views/problems/headers/LayoutHeaderCenter.vue`
   - Wrap `handleRun()` with `useAuthStore().isAuthenticated` check
   - If not authenticated, toast `t("problem.messages.loginRequired")` and return early
   - Backend remains unchanged (still requires auth)

#### Sprint 2: Solution List Refactor (Lightweight + Server-Side Filter)
9. **Backend: Create `SolutionListItemVO`**
   - Fields: id, title, summary, language, tags, author{id,name,avatar}, counts{views,comments,likes,dislikes}, score, viewerVote, publishedAt, isPinned
   - Exclude `content`
10. **Backend: New canonical endpoint + keep compatibility**
    - Add `GET /problems/{problemId}/solutions?page&pageSize&sort&language&search` to `ProblemController` or keep in `SolutionController`
    - Keep `GET /api/problems/{problemId}/solutions` as compatibility alias (deprecated)
    - Implement server-side filtering/sorting/pagination in `SolutionServiceImpl`
11. **Backend: Batch query for solution list**
    - Replace per-row `toVO()` enrichment with:
      - Single batch user query by author IDs
      - Batch edge count query for likes/dislikes
      - Batch comment count query
      - Batch viewer vote query from SecurityContext
    - Target: bounded SQL count regardless of page size
12. **Backend: Remove userId query param dependency**
    - `findByProblemId()` no longer accepts/reads `userId` param
    - Viewer fields populated from `SecurityContext` (null for anonymous)
13. **Frontend: Adapt to lightweight solution list**
    - Update `SolutionFeedItem` type to match `SolutionListItemVO`
    - Move local content search to server-side; send `search` param
    - `ProblemSolutionsView`: on select, call `fetchSolutionDetail(id)` for full content
    - Update `fetchSolutionFeed()` to drop `userId` param

#### Sprint 3: Submission List/Detail Refactor
14. **Backend: Create `SubmissionListItemVO`**
    - Fields: id, status, language, runtime, memory, createdAt, notes, problem summary
    - Exclude: code, tests, compilerError, errorDetail, input/output/expected, distributions
15. **Backend: Create `SubmissionDetailVO`**
    - Fields: all `SubmissionListItemVO` fields + code, tests, compilerError, errorDetail, failed input/output/expected, runtimePercentile, memoryPercentile, runtimeDistBinsMs, memoryDistBinsMb
16. **Backend: Wire new DTOs**
    - `GET /problems/{problemId}/submissions` returns `PageResult<SubmissionListItemVO>`
    - `GET /submissions/{submissionId}` returns `SubmissionDetailVO`
    - Add `runtimeDistBinsMs` to entity mapper and detail VO
17. **Frontend: Adapt submission types and flow**
    - Split `SubmissionRecord` into `SubmissionListItem` and `SubmissionDetail`
    - `SubmissionsView` loads list only
    - On select, call `fetchSubmissionDetail(submissionId)`
    - Update `useSubmissionDetail.ts` to handle missing `runtimeDistBinsMs` gracefully

#### Sprint 4: Public/Admin Problem Detail DTO Separation
18. **Backend: Create `ProblemDetailPublicVO`**
    - Allowlist fields: id, slug, title, difficulty, status, isPremium, stats, detail, tags, examples, languages, interactions.counts
    - Exclude: isDeleted, isFlagged, publishedBy, flagReason, flaggedAt, reviewedBy, reviewedAt
19. **Backend: Create `ProblemDetailAdminVO`**
    - Extends/inherits public fields + all moderation/management fields
20. **Backend: Update `ProblemServiceImpl.buildDetailResponse()`**
    - Return `ProblemDetailPublicVO` for console endpoint
    - Admin controller uses `ProblemDetailAdminVO`
21. **Frontend: Update console types**
    - `ProblemDetailResponse` type removes admin fields

### Key Files

| File | Operation | Description |
|------|-----------|-------------|
| `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java` | Modify | Add GET-only permitAll matcher for `/api/problems/*/solutions` |
| `backend-spring/src/main/java/com/ulticode/modules/solution/controller/SolutionController.java` | Modify | Add `@PatchMapping` alias; keep `@PutMapping` |
| `console/src/utils/request.ts` | Modify | Add `apiPut()` utility |
| `console/src/api/solution.ts` | Modify | Change `updateSolution()` to `apiPut`; drop `userId` param from `fetchSolutionFeed()` |
| `console/src/views/problems/components/MobileProblemLayout.vue` | Modify | Bidirectional route<->tab sync |
| `console/src/views/problems/submissions/SubmissionsListView.vue` | Modify | Fix inverted TableCaption conditional |
| `console/src/views/problems/headers/LayoutHeaderCenter.vue` | Modify | Add auth guard to `handleRun()` |
| `backend-spring/src/main/java/com/ulticode/modules/solution/dto/SolutionListItemVO.java` | Create | Lightweight list item DTO |
| `backend-spring/src/main/java/com/ulticode/modules/solution/dto/SolutionDetailVO.java` | Create | Full detail DTO (rename from current VO or extract) |
| `backend-spring/src/main/java/com/ulticode/modules/solution/service/impl/SolutionServiceImpl.java` | Modify | Batch query list mapper; server-side filter/sort/page |
| `backend-spring/src/main/java/com/ulticode/modules/submission/dto/SubmissionListItemVO.java` | Create | Lightweight submission list DTO |
| `backend-spring/src/main/java/com/ulticode/modules/submission/dto/SubmissionDetailVO.java` | Create | Full submission detail DTO |
| `backend-spring/src/main/java/com/ulticode/modules/submission/dto/SubmissionVO.java` | Modify or Deprecate | Add `runtimeDistBinsMs`; migrate to new DTOs |
| `backend-spring/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java` | Modify | Split list/detail return types |
| `console/src/views/problems/solutions/ProblemSolutionsView.vue` | Modify | Detail-on-demand fetch |
| `console/src/views/problems/submissions/SubmissionsView.vue` | Modify | Detail-on-demand fetch |
| `backend-spring/src/main/java/com/ulticode/modules/problem/dto/ProblemDetailPublicVO.java` | Create | Public-safe problem detail response |
| `backend-spring/src/main/java/com/ulticode/modules/problem/dto/ProblemDetailAdminVO.java` | Create | Admin problem detail response |
| `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` | Modify | Build public vs admin DTO |

### Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Adding public GET matcher accidentally exposes POST/DELETE | Use `HttpMethod.GET` specific matcher, not generic `PUBLIC_ENDPOINTS` array entry |
| PATCH/PUT dual support causes confusion | Document `@PatchMapping` as transitional; frontend uses PUT in Sprint 1; deprecate PUT in future sprint |
| Solution list batch query breaks existing list view | Keep old endpoint as alias with `@Deprecated`; verify frontend E2E before removal |
| DTO split breaks management frontend types | Management uses admin DTOs; verify management build after changes |
| Mobile route sync causes infinite loops | Copy guard pattern exactly from `useProblemLayout.ts` (isUpdatingFromRoute / isUpdatingFromStore) |
| runtimeDistBinsMs missing in DB for historical submissions | Frontend handles null gracefully; backend returns null without error |
| Anonymous run decision blocks Sprint 1 | Decision made: require auth + frontend guard (short term). Anonymous run is a separate future feature requiring rate limits and sandbox hardening. |

### SESSION_ID (for /ccg:execute use)
- CODEX_SESSION: 019e6ec3-f393-7ec2-bdb4-8b658d875846
- GEMINI_SESSION: 27073854-03d4-4c8f-8719-4926e889eccf

### Testing Checklist (Per Sprint)

**Sprint 0:**
- [ ] Backend contract tests compile and fail (RED)
- [ ] Frontend API unit tests compile and fail (RED)

**Sprint 1:**
- [ ] MockMvc: anonymous GET `/api/problems/{id}/solutions` returns 200
- [ ] MockMvc: anonymous POST `/api/problems/{id}/solutions` returns 401
- [ ] Controller test: PATCH `/api/solutions/{id}` works when authenticated
- [ ] Frontend Vitest: `updateSolution` uses `apiPut`
- [ ] Browser: mobile viewport `/problems/two-sum/solutions` shows solutions tab
- [ ] Browser: submission list caption correct with/without data
- [ ] Browser: Run button shows login toast when anonymous

**Sprint 2:**
- [ ] Service test: solution list SQL query count is bounded (not N+1)
- [ ] Controller test: list response excludes `content`
- [ ] E2E: solution search filters across all results, not just current page

**Sprint 3:**
- [ ] Controller test: submission list excludes `code`, `tests`, failure IO
- [ ] Controller test: detail includes `runtimeDistBinsMs`
- [ ] Frontend: chart shows data or graceful empty state

**Sprint 4:**
- [ ] Serialization test: public response has no `isDeleted`, `publishedBy`, `flag_*`
- [ ] Management build: admin endpoints still return full fields
