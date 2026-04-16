# Phase 8: Testing - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Add key-path tests to frontend Console, frontend Management, and backend controllers. Scope is testing existing functionality, not building new features.

Specifically:
- Console frontend: API layer tests, auth store (login/refresh), problem store (data fetching)
- Management frontend: admin API layer tests, at least one admin store with CRUD
- Backend: AuthController + ProblemController @WebMvcTest integration tests

NOT in scope: E2E tests, performance tests, new features, full coverage of all controllers/stores.

</domain>

<decisions>
## Implementation Decisions

### Console Frontend Testing
- **D-01:** Test the API layer by mocking `axios` via vitest's `vi.mock` — verify correct HTTP methods, paths, and parameter passing
- **D-02:** Test auth store login/refresh flow — verify token storage state transitions and API call sequencing
- **D-03:** Test problem store data fetching — verify loading states, error handling, and data transformation
- **D-04:** Follow existing test patterns from `__tests__/` directories — co-located with source files

### Management Frontend Testing
- **D-05:** Test admin API layer (`management/src/api/admin/`) — verify CRUD endpoints for at least one admin resource
- **D-06:** Test at least one admin store with CRUD operations — verify state management patterns
- **D-07:** Follow existing vitest patterns — only 1 existing test (`moderation.spec.ts`) establishes the pattern

### Backend Controller Testing
- **D-08:** Use `@WebMvcTest` for controller integration tests — loads only the web layer, not the full context
- **D-09:** Mock service dependencies with `@MockBean` — test request/response contracts in isolation
- **D-10:** Test AuthController: login endpoint authentication, token response format, validation errors
- **D-11:** Test ProblemController: problem listing, single problem retrieval, authentication requirements
- **D-12:** Use Testcontainers pattern established in Phase 3 — existing test infrastructure at `backend-spring/src/test/`

### Claude's Discretion
- Exact test case details — planner can decide based on API endpoints
- Mock data specifics — follow existing patterns
- Test file naming conventions — follow project conventions

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Test Infrastructure
- `console/src/stores/__tests__/editorSettings.spec.ts` — Existing console store test pattern
- `console/src/composables/__tests__/useRetry.spec.ts` — Existing console composable test pattern
- `management/src/stores/admin/__tests__/moderation.spec.ts` — Existing management test (only one)
- `backend-spring/src/test/java/com/ulticode/modules/` — Existing backend test patterns

### API Layers to Test
- `console/src/utils/request.ts` — Frontend request utility (axios wrapper with CSRF)
- `console/src/api/auth.ts` — Console auth API
- `console/src/api/problem-detail.ts` — Console problem API
- `console/src/stores/auth.ts` — Console auth store
- `management/src/api/admin/` — Management admin API directory
- `management/src/api/auth.ts` — Management auth API

### Controllers to Test
- `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java`
- `backend-spring/src/main/java/com/ulticode/modules/problem/controller/ProblemController.java`

### Project Context
- `CLAUDE.md` — Project overview, test commands
- `.planning/PROJECT.md` — Requirements (TEST-02, TEST-03, TEST-04)
- `.planning/phases/07-code-quality-dependencies/07-CONTEXT.md` — Prior phase context

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Vitest configured in both console and management — `vitest ^4.0.15`
- Backend has Testcontainers BOM + existing 31 test files
- Console has 15 existing tests covering stores, composables, and components
- Management has 1 existing test covering moderation store

### Established Patterns
- Frontend: `__tests__/` directories co-located with source files, vitest + spec files
- Backend: `@Testcontainers`, `@ExtendWith(MockitoExtension.class)`, service-level unit tests
- API layer: `apiGet<T>()` / `apiPost<T>()` utility functions wrapping axios

### Integration Points
- Frontend API layer uses `request.ts` which auto-unwraps `Result<T>` responses
- Backend controllers return `Result<T>` wrapper with code/message/data
- CSRF token management via `csrfManager` for state-changing requests

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follow existing test patterns and cover the key paths identified in success criteria.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 08-testing*
*Context gathered: 2026-04-16*
