# Phase 04: Frontend Quality - Context

**Gathered:** 2026-04-15
**Status:** Ready for planning (auto-mode)

<domain>
## Phase Boundary

Split 13 oversized Vue components and 1 Pinia store (moderation) into smaller co-located sub-components and composables. No component may exceed 500 lines after splitting. Zero behavioral regressions — every split component must render and function identically to the original. No new features, no new pages, no styling changes.

**In scope:**
- Console (8): ProblemListsView (1356 lines), ProblemListView (804), ContestDetailView (1039), SubmissionsDetail (867), PersonalView (666), ProblemDetailView (692), ProblemExplorer (642), Calendars (790)
- Management (5 + store): ProblemsListView (1224), AnalyticsView (881), ModerationQueueView (768), SettingsView (627), HiddenTestCasesEditor (602), moderation Pinia store

**Out of scope:**
- New features or UI changes
- New dependencies or libraries
- Backend changes
- Test additions (Phase 3 covers backend tests; frontend tests are deferred to TEST-02/TEST-03)
- Performance optimization

</domain>

<decisions>
## Implementation Decisions

### Co-location Pattern
- **D-01:** Extracted sub-components are co-located in a `components/` subdirectory next to the parent view. Example: `views/personal/ProblemListsView.vue` + `views/personal/components/MyListsTab.vue`
- **D-02:** Extracted composables follow the same co-location pattern: `views/personal/composables/useProblemLists.ts` — only promote to global `composables/` if reused across 3+ views

### Split Strategy
- **D-03:** Tab-heavy components split by tab: each tab content becomes its own component, parent remains as tab orchestrator (handles active tab state, renders tab navigation + selected tab component)
- **D-04:** Dialog-heavy components extract each dialog into its own component file. Dialog state (open/close) stays in parent; dialog content and form logic live in the dialog component
- **D-05:** Large script sections (500+ lines of logic) extract data fetching and state management into composables. The parent's `<script setup>` should primarily orchestrate: import composables, wire props/events to child components

### Composable Extraction Scope
- **D-06:** Extract into composables: data fetching (API calls, SWR patterns), complex state machines (multi-step forms, filters), reusable business logic (pagination, search)
- **D-07:** Keep inline: simple formatters (date, number), template-bound validators, UI state toggles (dialog open/close, tab active). These are tightly coupled to the template and rarely worth abstracting

### Communication Pattern
- **D-08:** Parent-child communication via standard Vue props/emit pattern. Props pass data down, emit sends events up. Explicit and traceable.
- **D-09:** Use provide/inject only for deeply nested cases where prop drilling exceeds 3 levels. Do NOT create new Pinia stores for component-local state — that defeats the purpose of splitting

### Target Sizes
- **D-10:** Sub-components: 100-300 lines target, 500 lines hard cap
- **D-11:** Composables: 100-250 lines target
- **D-12:** Parent orchestrator components: 200-450 lines target, 500 lines hard cap

### Split Priority by Component
- **D-13:** Largest components split first (highest ROI): ProblemListsView (1356) → ProblemsListView (1224) → ContestDetailView (1039) → AnalyticsView (881) → SubmissionsDetail (867) → Calendars (790) → ModerationQueueView (768) → ProblemListView (804) → ProblemDetailView (692) → PersonalView (666) → ProblemExplorer (642) → SettingsView (627) → HiddenTestCasesEditor (602)
- **D-14:** Moderation Pinia store split alongside ModerationQueueView since they share domain logic

### Claude's Discretion
- Exact naming of extracted components and composables (follow existing project naming conventions)
- Order of extraction within a component (top-to-bottom vs most-complex-first)
- Whether to extract a shared utility vs duplicate a few lines (prefer simplicity when logic is <10 lines)
- Specific emit event names and prop interfaces
- Whether to use `<script setup>` or `defineComponent` for extracted pieces (follow existing convention in each component)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Configuration
- `CLAUDE.md` — Frontend Design System section (OKLCH, --radius: 0, Tailwind v4, shadcn-vue, Radix Vue)
- `.planning/REQUIREMENTS.md` — QUAL-01 acceptance criteria
- `.planning/ROADMAP.md` — Phase 4 definition and success criteria

### Console Frontend
- `console/src/views/personal/ProblemListsView.vue` — 1356 lines, 4-tab interface
- `console/src/views/problem-list/ProblemListView.vue` — 804 lines, dialog-heavy
- `console/src/views/contest/detailed/ContestDetailView.vue` — 1039 lines, contest info + registration
- `console/src/views/problems/submissions/SubmissionsDetail.vue` — 867 lines, code display + results
- `console/src/views/personal/PersonalView.vue` — 666 lines, user profile + stats
- `console/src/views/problems/ProblemDetailView.vue` — 692 lines, problem solving view
- `console/src/components/problem/ProblemExplorer.vue` — 642 lines, filter + problem list
- `console/src/features/sider/Calendars.vue` — 790 lines, calendar + list management
- `console/src/views/settings/SettingsView.vue` — Console settings (verify location)
- `console/src/composables/` — Existing 15 composables (useAuthStore, useSWR, useBreakpoints, etc.)

### Management Frontend
- `management/src/views/problems/ProblemsListView.vue` — 1224 lines, admin problem management
- `management/src/views/analytics/AnalyticsView.vue` — 881 lines, multi-report interface
- `management/src/views/moderation/ModerationQueueView.vue` — 768 lines, queue management
- `management/src/components/problem/HiddenTestCasesEditor.vue` — 602 lines, test case editor
- `management/src/views/settings/SettingsView.vue` — 627 lines, multi-tab settings
- `management/src/composables/` — Existing 2 composables (useDataTable, useLocale)

### Design System
- `console/src/assets/css/` — Solarized color tokens, Tailwind v4 @theme inline
- `management/src/assets/css/` — Management-specific styles

### Prior Phase Context
- `.planning/phases/02-core-functionality/02-CONTEXT.md` — Established patterns (Result<T>, API client pattern)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Console composables (15)**: useAuthStore, useBreakpoints, useCodeCache, useCodeTemplates, useEditorThemes, useErrorHandler, useGlobalShortcuts, useLoading, useLocale, useNetworkStatus, usePWA, useRetry, useSearch, useSocket, useSWR — established patterns for data fetching (useSWR), error handling, loading states
- **Management composables (2)**: useDataTable, useLocale — minimal; useDataTable pattern likely reusable for ProblemsListView and ModerationQueueView table extraction
- **Contest composable directory** (`console/src/composables/contest/`) — existing contest-related composables that ContestDetailView may already use or should reference

### Established Patterns
- **Script-heavy components**: ProblemDetailView (609 script / 82 template), SubmissionsDetail (571/295), ProblemsListView mgmt (916/307) — these need composable extraction most urgently
- **Template-heavy components**: ProblemListsView (357 script / 998 template), Calendars (284/505), ContestDetailView (302/736) — these need component extraction for template sections
- **Dialog-heavy components**: ProblemListsView (21 dialogs), Calendars (95 dialogs), HiddenTestCasesEditor (43 dialogs) — extract dialog content into separate components
- **Tab-heavy components**: ProblemListsView (4 tabs), AnalyticsView (multiple reports), SettingsView (5 settings sections) — split by tab content

### Integration Points
- Console views import from `@/composables/` and `@/components/`
- Management views import from `@/composables/` and Pinia stores
- Both frontends use shadcn-vue components (Dialog, Card, Form, Tabs, etc.)
- Both use `useI18n` extensively — composables should not break i18n context
- Both use Pinia stores for global state — component-local state stays in composables/refs

### Component Location Verification
- ROADMAP lists SettingsView and HiddenTestCasesEditor as console components in plan 04-01
- Explorer found SettingsView at `management/src/views/settings/` and HiddenTestCasesEditor at `management/src/components/problem/`
- Planner must verify actual file locations and assign to correct plan (04-01 vs 04-02)

</code_context>

<specifics>
## Specific Ideas

- ProblemListsView (1356 lines, largest): Split into 4 tab components (MyListsTab, SavedTab, CategoriesTab, SettingsTab) + composable for API calls + extracted dialog components for CRUD operations
- ContestDetailView (1039 lines): Split contest header, registration section, problem listing, timeline into separate components
- ProblemsListView mgmt (1224 lines): Extract bulk actions, problem table, filter panel, and action dialogs. Heaviest script (916 lines) needs composable extraction
- AnalyticsView (881 lines): Each report tab becomes a component. Data fetching per report goes into a composable

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---
*Phase: 04-frontend-quality*
*Context gathered: 2026-04-15*
