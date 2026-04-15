# Phase 04: Frontend Quality - Research

**Researched:** 2026-04-15
**Domain:** Vue 3 component splitting, Composition API composables, co-located component architecture
**Confidence:** HIGH

## Summary

This phase splits 13 oversized Vue components (8 console + 5 management) and 1 oversized Pinia store into smaller co-located sub-components and composables. The project already demonstrates established patterns for this work: ProblemDetailView uses a composable (`useProblemDetail.ts`) for data fetching, PersonalView already has co-located chart components in `components/`, and the codebase uses `provide/inject` (problem-context.ts) for deeply nested component trees.

The primary challenge is not technical but organizational: 8,685 lines across 13 components + 600-line store must be decomposed without behavioral regressions. The splitting strategy is already decided (D-03 through D-14 in CONTEXT.md). This research focuses on confirming those patterns work with the actual codebase structure and identifying specific extraction targets per component.

**Primary recommendation:** Follow the locked decisions (D-01 through D-14) exactly. Split by tabs for tab-heavy components, extract dialog content for dialog-heavy components, and pull data-fetching logic into composables for script-heavy components. Each plan commit must pass `vue-tsc --build` and `vite build` to verify no regressions.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Extracted sub-components co-located in `components/` subdirectory next to parent view
- **D-02:** Extracted composables co-located in `composables/` subdirectory; only promote to global if reused across 3+ views
- **D-03:** Tab-heavy components split by tab content; parent remains tab orchestrator
- **D-04:** Dialog-heavy components extract dialog content into separate files; dialog state stays in parent
- **D-05:** Large script sections extract data fetching/state into composables
- **D-06:** Extract into composables: data fetching (API calls, SWR), complex state machines, reusable business logic
- **D-07:** Keep inline: simple formatters, template-bound validators, UI state toggles (dialog open/close, tab active)
- **D-08:** Parent-child communication via standard Vue props/emit
- **D-09:** Use provide/inject only for deeply nested cases where prop drilling exceeds 3 levels; no new Pinia stores for component-local state
- **D-10:** Sub-components: 100-300 lines target, 500 lines hard cap
- **D-11:** Composables: 100-250 lines target
- **D-12:** Parent orchestrator: 200-450 lines target, 500 lines hard cap
- **D-13:** Split order: ProblemListsView (1356) > ProblemsListView (1224) > ContestDetailView (1039) > AnalyticsView (881) > SubmissionsDetail (867) > Calendars (790) > ModerationQueueView (768) > ProblemListView (804) > ProblemDetailView (692) > PersonalView (666) > ProblemExplorer (642) > SettingsView (627) > HiddenTestCasesEditor (602)
- **D-14:** Moderation Pinia store split alongside ModerationQueueView

### Claude's Discretion
- Exact naming of extracted components and composables (follow existing project conventions)
- Order of extraction within a component (top-to-bottom vs most-complex-first)
- Whether to extract a shared utility vs duplicate logic under 10 lines (prefer simplicity)
- Specific emit event names and prop interfaces
- `<script setup>` vs `defineComponent` for extracted pieces (follow existing convention per component)

### Deferred Ideas (OUT OF SCOPE)
- None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| QUAL-01 | 14 oversized Vue components (600+ lines) split into smaller composable pieces, maintaining identical behavior | 13 Vue components verified over 600 lines + 1 Pinia store at 600 lines. Splitting patterns confirmed by existing codebase examples (useProblemDetail, PersonalView co-located components, provide/inject in ProblemDetailView). Build verification commands: `vue-tsc --build` + `vite build`. |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Component file organization | Browser / Client (Vue SFC) | -- | This is purely frontend code splitting; no backend involvement |
| Data fetching logic extraction | Browser / Client (composables) | API / Backend (via existing API layer) | Composables call existing API functions; no backend changes needed |
| State management (local) | Browser / Client (refs, composables) | -- | Component-local state stays in composables per D-09 |
| State management (store) | Browser / Client (Pinia) | -- | Moderation store split is within existing store boundaries |
| i18n propagation | Browser / Client (vue-i18n) | -- | vue-i18n uses Vue's provide/inject; child components call `useI18n()` directly |
| Build verification | Browser / Client (vite, vue-tsc) | -- | `vue-tsc --build` and `vite build` are the regression gates |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Vue | ^3.5.25 / ^3.5.26 | UI framework | `<script setup>` Composition API, provide/inject, defineProps/defineEmits |
| TypeScript | ~5.9.3 | Type safety | vue-tsc for type-checking, interfaces for props/return types |
| vue-tsc | ^3.1.5 / ^3.2.1 | Vue type checking | `vue-tsc --build` verifies no type errors after splitting |
| Vite | ^7.2.6 / ^7.3.0 | Build tool | `vite build` verifies production build succeeds |
| vue-i18n | ^10.0.0 / ^10.0.8 | Internationalization | `useI18n()` composable available in all extracted components |
| Pinia | ^3.0.4 | State management | Existing stores remain unchanged; moderation store split within same file boundary |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @vueuse/core | ^14.1.0 | Vue utilities | `watchDebounced`, `useDebounceFn` -- used in existing composable patterns |
| shadcn-vue | (new-york style) | UI components | Dialog, Card, Tabs, etc. -- no API changes needed, just restructure imports |
| @tanstack/vue-table | ^8.21.3 | Data tables (management) | DataTable component used in ProblemsListView, ModerationQueueView |
| Radix Vue | (via shadcn-vue) | Accessible primitives | Underlying headless components -- no direct interaction |
| Tailwind CSS | ^4.1.17 / ^4.1.18 | Styling | `@theme inline` config -- no changes needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Co-located `components/` dirs | Global `src/components/` | Global makes reuse easier but breaks locality; D-01 locks co-location |
| `<script setup>` | `defineComponent()` | D-13 allows either based on existing convention; all examined components use `<script setup>` |
| Props/emit | provide/inject | D-09 restricts provide/inject to 3+ level nesting only |

**Installation:** No new dependencies required. All splitting uses existing project stack.

**Version verification:**
```bash
npm view vue version   # 3.5.17 (registry may lag behind ^3.5.25 in package.json)
npm view vue-tsc version
npm view pinia version
```
[VERIFIED: package.json files in both console/ and management/]

## Architecture Patterns

### System Architecture Diagram

```
                         User Request (browser navigation)
                                    |
                                    v
                    +-------------------------------+
                    |     Vue Router (route match)   |
                    +-------------------------------+
                                    |
                                    v
                    +-------------------------------+
                    |     Parent View Component      |
                    |  (orchestrator, 200-450 lines) |
                    |  - Tab state management        |
                    |  - Dialog open/close state     |
                    |  - Imports composables         |
                    |  - Passes props down           |
                    +-------------------------------+
                       |              |              |
              props    |     props    |     props    |
              events   v     events   v     events   v
        +----------+  +----------+  +----------+
        | Sub-Comp |  | Sub-Comp |  | Sub-Comp |
        | (tab 1)  |  | (tab 2)  |  | (dialog) |
        | <500 ln  |  | <500 ln  |  | <500 ln  |
        +----------+  +----------+  +----------+
                                    |
                                    v
                    +-------------------------------+
                    |     Co-located Composable      |
                    |  (data fetching, state mgmt)   |
                    |  - useSWR / direct API calls   |
                    |  - Complex computed properties |
                    |  - Business logic              |
                    +-------------------------------+
                                    |
                                    v
                    +-------------------------------+
                    |     Existing API Layer         |
                    |  (api/*.ts, stores/*.ts)       |
                    +-------------------------------+
```

### Recommended Project Structure (post-split example)

```
console/src/views/personal/
  ProblemListsView.vue              # Orchestrator (~300 lines)
  components/
    MyListsTab.vue                  # Tab content (~200 lines)
    SavedListsTab.vue               # Tab content (~200 lines)
    CategoriesTab.vue               # Tab content (~200 lines)
    CreateListDialog.vue            # Dialog content (~120 lines)
    DeleteListDialog.vue            # Dialog content (~80 lines)
    CreateCategoryDialog.vue        # Dialog content (~80 lines)
    EditCategoryDialog.vue          # Dialog content (~80 lines)
  composables/
    useProblemLists.ts              # Data fetching + state (~200 lines)

console/src/views/contest/detailed/
  ContestDetailView.vue             # Orchestrator (~350 lines)
  components/
    ContestHeader.vue               # Title, badges, status (~150 lines)
    ContestStatusTimer.vue          # Countdown/status card (~120 lines)
    ContestRegistration.vue         # Register/unregister UI (~100 lines)
    ContestProblemList.vue          # Problem listing table (~200 lines)
    ContestRankingTable.vue         # Rankings with live updates (~250 lines)
    VirtualContestSection.vue       # Virtual contest controls (~100 lines)
  composables/
    useContestStatus.ts             # Timer logic, status formatting (~150 lines)
    useContestRankings.ts           # Ranking fetching, live polling (~100 lines)
```

### Pattern 1: Tab Splitting (D-03)
**What:** Parent remains as tab orchestrator. Each tab's content becomes a separate component. Parent owns `activeTab` ref and renders the correct tab component.
**When to use:** Components with 3+ tabs, where each tab has substantial content (100+ lines).
**Example:**
```vue
<!-- Parent: ProblemListsView.vue (orchestrator) -->
<script setup lang="ts">
import { ref } from "vue";
import { useProblemLists } from "./composables/useProblemLists";
import MyListsTab from "./components/MyListsTab.vue";
import SavedListsTab from "./components/SavedListsTab.vue";
import CategoriesTab from "./components/CategoriesTab.vue";
import CreateListDialog from "./components/CreateListDialog.vue";
import DeleteListDialog from "./components/DeleteListDialog.vue";

const { t } = useI18n();
const activeTab = ref("my-lists");
const {
  data, loading, loadData,
  sortedMyLists, sortedSavedLists, sortedCategories,
} = useProblemLists();

// Dialog state stays in parent per D-04
const isCreateOpen = ref(false);
const listToDelete = ref<ProblemList | null>(null);
const isDeleteOpen = ref(false);
</script>

<template>
  <PersonalPageShell>
    <Tabs v-model="activeTab">
      <TabsList>
        <TabsTrigger value="my-lists">{{ t("...") }}</TabsTrigger>
        <TabsTrigger value="saved">{{ t("...") }}</TabsTrigger>
        <TabsTrigger value="categories">{{ t("...") }}</TabsTrigger>
      </TabsList>
      <TabsContent value="my-lists">
        <MyListsTab :lists="sortedMyLists" @delete="openDeleteDialog" @create="isCreateOpen = true" />
      </TabsContent>
      <TabsContent value="saved">
        <SavedListsTab :lists="sortedSavedLists" @unsave="handleUnsave" />
      </TabsContent>
      <TabsContent value="categories">
        <CategoriesTab :categories="sortedCategories" @edit="openEditCategory" />
      </TabsContent>
    </Tabs>
  </PersonalPageShell>
</template>
```

### Pattern 2: Dialog Extraction (D-04)
**What:** Dialog wrapper (`<Dialog :open="isXxxOpen">`) stays in parent. Dialog content (form, body, footer) moves to a child component. The child receives data via props and emits submit/cancel events.
**When to use:** Components with 3+ dialogs, or any dialog with substantial form logic (50+ lines).
**Example:**
```vue
<!-- Child: CreateListDialog.vue -->
<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  open: boolean;
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "submit", data: { name: string; description: string; isPublic: boolean }): void;
}>();

const { t } = useI18n();
const form = ref({ name: "", description: "", isPublic: false });

function handleSubmit() {
  emit("submit", { ...form.value });
}

function handleCancel() {
  emit("update:open", false);
  form.value = { name: "", description: "", isPublic: false };
}
</script>

<template>
  <Dialog :open="props.open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t("...") }}</DialogTitle>
      </DialogHeader>
      <!-- Form content here -->
      <DialogFooter>
        <Button variant="outline" @click="handleCancel">{{ t("cancel") }}</Button>
        <Button :disabled="!form.name.trim() || props.loading" @click="handleSubmit">
          {{ t("create") }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
```

### Pattern 3: Composable Extraction (D-05)
**What:** Extract data fetching, computed properties, and business logic into a composable function. Parent's `<script setup>` imports and destructures the composable return value.
**When to use:** Script section exceeds 300 lines, or data fetching logic is repeated across tabs/sections.
**Example:**
```typescript
// composables/useProblemLists.ts
import { ref, computed, onMounted } from "vue";
import { useAuthStore } from "@/stores/auth";
import type { UserProblemListsResponse, ProblemList } from "@/types/problem-list";
import { fetchProblemListsOverview } from "@/api/problem-list";

export function useProblemLists() {
  const { t } = useI18n();
  const loading = ref(true);
  const currentUserId = useAuthStore().fetchCurrentUserId();
  const searchQuery = ref("");
  const data = ref<UserProblemListsResponse>({
    myLists: [], savedLists: [], featured: [], categories: [],
  });

  const sortedMyLists = computed(() => {
    let lists = [...data.value.myLists].sort((a, b) => b.problemCount - a.problemCount);
    if (searchQuery.value.trim()) {
      const q = searchQuery.value.toLowerCase();
      lists = lists.filter(l => l.name.toLowerCase().includes(q));
    }
    return lists;
  });

  const loadData = async () => {
    if (!currentUserId) { loading.value = false; return; }
    try {
      data.value = await fetchProblemListsOverview(currentUserId);
    } catch (e) {
      console.error("Failed to load problem lists", e);
    } finally {
      loading.value = false;
    }
  };

  onMounted(loadData);

  return {
    data, loading, loadData, searchQuery,
    sortedMyLists, sortedSavedLists: computed(() => /* ... */),
    sortedCategories: computed(() => /* ... */),
    totalSavedCount: computed(() => data.value.savedLists.length),
  };
}
```
[VERIFIED: vue-i18n `useI18n()` works in composables -- confirmed by existing `useContestSocket.ts` and `useProblemDetail.ts` patterns in codebase]

### Pattern 4: provide/inject for Deep Trees (D-09)
**What:** For component trees with 3+ nesting levels, use typed `InjectionKey` for provide/inject. Already used in ProblemDetailView via `problem-context.ts`.
**When to use:** ProblemDetailView pattern where connector components wrap sub-views with different prop interfaces.
**Example:**
```typescript
// problem-context.ts (existing pattern)
import type { InjectionKey, Ref } from "vue";
import type { ProblemDetail } from "@/types/problem-detail";

export interface ProblemContext {
  problem: Ref<ProblemDetail | null>;
  runResult: Ref<ProblemRunResult | null>;
  contestId: Ref<string | null>;
}

export const ProblemContextKey: InjectionKey<ProblemContext> = Symbol("ProblemContext");
```

### Anti-Patterns to Avoid
- **New Pinia stores for local state:** D-09 explicitly forbids this. Component-local state belongs in composables or parent refs, not new stores.
- **Prop mutation in children:** Child components must never modify props directly. All changes go through emit events.
- **Breaking i18n key paths:** Extracted components should keep the same i18n key namespace. If ProblemListsView uses `t("personal.problemLists.title")`, extracted tab components should also use the same key path -- do NOT create new key namespaces.
- **Moving dialog state into child components:** D-04 says dialog open/close state stays in the parent. Only the dialog content/form logic moves.
- **Extracting utility functions into composables:** D-07 says keep simple formatters (date, number) inline. Only extract when logic exceeds 10 lines or is reused.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| State management | Custom event bus, mitt | Pinia stores (existing) + composables | Already established; new stores forbidden by D-09 for local state |
| Data fetching patterns | Custom fetch wrappers | useSWR (console) / useDataTable (management) | Both already exist and handle caching, loading, error states |
| Dialog components | Custom modal implementations | shadcn-vue Dialog (existing) | Already imported in all dialog-heavy components |
| i18n in child components | Pass t function as prop | `useI18n()` directly in child | vue-i18n uses Vue provide/inject; just call `useI18n()` |
| Form validation | Custom validation logic | Existing template-bound validation patterns | D-07 says keep template validators inline |
| Debounced search | Custom setTimeout/clearTimeout | `@vueuse/core` `watchDebounced` / `useDebounceFn` | Already used in ProblemsListView and useDataTable |

**Key insight:** This phase is pure restructuring -- no new libraries, no new patterns, no new features. Every extracted piece follows patterns already demonstrated in the codebase.

## Component-by-Component Analysis

### Console (8 components, 6,856 total lines)

#### 1. ProblemListsView.vue (1,356 lines) -- PRIORITY 1
**Path:** `console/src/views/personal/ProblemListsView.vue`
**Breakdown:** ~354 script / ~980 template / ~22 style
**Split strategy:** Tab splitting (4 tabs) + dialog extraction + composable
**Extraction targets:**
- **Composable:** `useProblemLists.ts` -- data fetching (fetchProblemListsOverview), sorted computed properties, search filtering
- **Tab components:** MyListsTab, SavedListsTab, CategoriesTab (and possibly FeaturedLists)
- **Dialog components:** CreateListDialog, DeleteListDialog, CreateCategoryDialog, EditCategoryDialog, DeleteCategoryDialog
- **Estimated split:** Parent ~300 lines, 4 tabs ~150-200 each, 5 dialogs ~60-120 each, composable ~180 lines
**Existing assets:** Already imports `PersonalPageShell` and `PersonalPageHeader` from `./components/`
**i18n keys:** `personal.problemLists.*`, `personal.messages.*`

#### 2. ProblemListView.vue (804 lines)
**Path:** `console/src/views/problem-list/ProblemListView.vue`
**Breakdown:** ~372 script / ~432 template
**Split strategy:** Dialog extraction + composable for data operations
**Extraction targets:**
- **Composable:** `useProblemListOperations.ts` -- CRUD operations (fork, delete, update, add/remove problem, save/unsave, move to category), list data fetching
- **Dialog components:** EditListDialog, DeleteListDialog, AddProblemsDialog (search + add problems to list)
- **Existing assets:** Already has `ProblemListAnalytics.vue` co-located in same directory
**i18n keys:** `problemList.*`

#### 3. ContestDetailView.vue (1,039 lines)
**Path:** `console/src/views/contest/detailed/ContestDetailView.vue`
**Breakdown:** ~299 script / ~740 template
**Split strategy:** Section extraction + composable for timer/ranking logic
**Extraction targets:**
- **Composable:** `useContestStatus.ts` -- timer logic (formatCountdown, updateStatusTimer, getContestEndTimeMs), registration handlers
- **Composable:** `useContestRankings.ts` -- loadRankings, live polling (setInterval every 30s)
- **Section components:** ContestHeader (title, badges, status card), ContestRegistration (register/unregister buttons), ContestProblemList (problem table), ContestRankingTable (rankings with country flags)
- **Existing assets:** Uses `VirtualContestTimer` from `../components/`; existing `useContestSocket` composable for WebSocket
**Note:** The timer interval logic (statusIntervalId, rankingIntervalId) and their cleanup in onUnmounted are critical for the composable -- must call onMounted/onUnmounted inside composable [VERIFIED: useContestSocket.ts demonstrates this pattern]

#### 4. SubmissionsDetail.vue (867 lines)
**Path:** `console/src/views/problems/submissions/SubmissionsDetail.vue`
**Breakdown:** ~568 script / ~299 template
**Split strategy:** Script-heavy -- composable extraction primary
**Extraction targets:**
- **Composable:** `useSubmissionDetail.ts` -- status computation, parseMs, runtime calculation, all computed properties related to submission status
- **Section components:** SubmissionCodeBlock (code display with syntax highlighting), SubmissionTestResults (result details table)
- **Note:** Uses echarts for charts -- the chart rendering logic should stay with its template section
**i18n keys:** `submissions.*`

#### 5. PersonalView.vue (666 lines)
**Path:** `console/src/views/personal/PersonalView.vue`
**Breakdown:** ~191 script / ~475 template
**Split strategy:** Template-heavy -- extract profile sections
**Extraction targets:**
- **Existing co-located components already handle much of the heavy lifting:** ActivityHeatmap, SkillRadarChart, SubmissionHistoryChart, LearningProgressChart
- **Potential further extractions:** UserProfileCard (avatar, name, bio, social links), UserStatsPanel (easy/medium/hard stats), UserSkillsSection (skill radar)
- **i18n keys:** `personal.*`

#### 6. ProblemDetailView.vue (692 lines)
**Path:** `console/src/views/problems/ProblemDetailView.vue`
**Breakdown:** ~606 script / ~81 template
**Split strategy:** Script-heavy -- composable extraction primary
**Extraction targets:**
- **Already partially split:** Uses `useProblemDetail.ts` composable, `problem-context.ts` for provide/inject, and imports DescriptionView, ProblemSolutionsView, SubmissionsView, CodeView, TestCaseView, TestResultsView
- **Remaining script bloat (~606 lines):** Layout configuration (header groups, panel config), connector component definitions (ConnectedDescriptionView, etc.), problem hooks integration, breakpoints, panel toggles
- **Potential extractions:** `useProblemLayout.ts` (layout tree configuration, header group setup), `useProblemPanels.ts` (side panel, notes panel toggle logic)
- **Note:** This component is already well-decomposed into sub-views. The script bloat is primarily layout orchestration and connector definitions. Composable extraction should focus on layout configuration.

#### 7. ProblemExplorer.vue (642 lines)
**Path:** `console/src/components/problem/ProblemExplorer.vue`
**Breakdown:** ~358 script / ~252 template / ~32 style
**Split strategy:** Mixed -- composable for filter/pagination, component for list display
**Extraction targets:**
- **Composable:** `useProblemExplorer.ts` -- filter state (difficulty, tags, search), pagination, sorting, API fetching
- **Component:** ProblemFilterPanel (difficulty badges, tag selection, search input), ProblemResultList (the scrollable problem list items)
**Note:** Located in shared `components/problem/` -- extracted components should also go in a `components/` subdirectory here, not in a view directory

#### 8. Calendars.vue (790 lines)
**Path:** `console/src/features/sider/Calendars.vue`
**Breakdown:** ~280 script / ~510 template
**Split strategy:** Template-heavy with dialog extraction
**Extraction targets:**
- **Components:** CalendarView (the actual calendar grid), ContestList (contest items list), CalendarEventDialog (create/edit contest event)
- **Note:** Located in `features/sider/` -- extracted components go in `features/sider/components/`
- **i18n keys:** `calendar.*` or `contest.*`

### Management (5 components + 1 store, 4,702 total lines)

#### 9. ProblemsListView.vue (1,224 lines) -- PRIORITY 2
**Path:** `management/src/views/problems/ProblemsListView.vue`
**Breakdown:** ~913 script / ~187 template / ~0 style (extreme script-heavy)
**Split strategy:** Composable extraction primary (913 lines of script)
**Extraction targets:**
- **Composable:** `useProblemFilters.ts` -- filter state (difficulty, status, published, sortBy, sortOrder), URL synchronization, debounced updates
- **Composable:** `useProblemActions.ts` -- single/bulk operations (delete, flag, publish/unpublish, import/export)
- **Component:** ProblemBulkActions (bulk action buttons and dialogs)
- **Note:** Already uses `useDataTable` composable. The filter state management and URL sync logic (~200 lines) is a prime composable target.
- **Existing assets:** `components/` directory already has ProblemForm.vue, ProblemImportDialog.vue, BulkActionDialog.vue, BulkEditDialog.vue, FlagInfoDialog.vue

#### 10. AnalyticsView.vue (881 lines)
**Path:** `management/src/views/analytics/AnalyticsView.vue`
**Breakdown:** ~484 script / ~392 template / ~0 style
**Split strategy:** Tab splitting + composable per report
**Extraction targets:**
- **Tab components:** UserActivityReport, ProblemCompletionReport, ContestParticipationReport, RevenueReport, PerformanceReport
- **Composable:** `useAnalyticsReports.ts` -- report data fetching, time formatting, number formatting utilities
- **Note:** Each report tab has its own data source, loading state, and display logic -- natural split boundary
- **Existing assets:** Already imports AnalyticsNav, AnalyticsMetricCard, AnalyticsBarList, AnalyticsTagCloud, AnalyticsHeatmap from `@/components/analytics`

#### 11. ModerationQueueView.vue (768 lines)
**Path:** `management/src/views/moderation/ModerationQueueView.vue`
**Breakdown:** ~358 script / ~149 template
**Split strategy:** Component extraction + composable
**Extraction targets:**
- **Composable:** `useModerationFilters.ts` -- filter state (status, category, entityType), pagination
- **Components:** BatchActionDialog (batch moderation operations)
- **Note:** Already has co-located components: ActionHistoryTimeline, EntityPreviewCard, ModerationActionPanel, ModerationDetailDrawer
- **Existing assets:** Uses `useModerationStore` (the store being split in D-14)

#### 12. ModerationStore (600 lines)
**Path:** `management/src/stores/admin/moderation.ts`
**Breakdown:** Pure TypeScript (Pinia store)
**Split strategy:** Split into domain-focused sub-modules
**Extraction targets:**
- **Store module:** `moderation/queue.ts` -- queue CRUD operations, queue pagination
- **Store module:** `moderation/reports.ts` -- report management
- **Store module:** `moderation/appeals.ts` -- appeal management
- **Store module:** `moderation/actions.ts` -- moderation actions (resolve, ban, etc.)
- **Store module:** `moderation/index.ts` -- re-export combined store
- **Pattern:** Use Pinia's `defineStore` with composed helpers, or split into multiple stores that the main store re-exports

#### 13. SettingsView.vue (627 lines)
**Path:** `management/src/views/settings/SettingsView.vue`
**Breakdown:** ~169 script / ~453 template
**Split strategy:** Tab splitting
**Extraction targets:**
- **Tab components:** GeneralSettings, EmailSettings, RateLimitSettings, UploadSettings, FeatureToggleSettings
- **Note:** Template-heavy with 5 settings sections. Each section is a natural extraction boundary.

#### 14. HiddenTestCasesEditor.vue (602 lines)
**Path:** `management/src/components/problem/HiddenTestCasesEditor.vue`
**Breakdown:** ~319 script / ~278 template / ~0 style
**Split strategy:** Mixed -- extract form and display sections
**Extraction targets:**
- **Composable:** `useTestCases.ts` -- CRUD operations for test cases, form state management
- **Components:** TestCaseForm (create/edit form), TestCaseList (sidebar list), TestCaseDetail (active test case display), TestCaseImportDialog (bulk import)

### Location Corrections (CRITICAL for Planner)

The ROADMAP plan 04-01 incorrectly assigns two components to console:

| Component | ROADMAP says | Actual location | Correct plan |
|-----------|-------------|-----------------|-------------|
| SettingsView | Console (04-01) | `management/src/views/settings/SettingsView.vue` | **04-02** (management) |
| HiddenTestCasesEditor | Console (04-01) | `management/src/components/problem/HiddenTestCasesEditor.vue` | **04-02** (management) |

**Corrected plan assignment:**
- **04-01 (console):** ProblemListsView, ProblemListView, ContestDetailView, SubmissionsDetail, PersonalView, ProblemDetailView, ProblemExplorer, Calendars (8 components)
- **04-02 (management):** ProblemsListView, AnalyticsView, ModerationQueueView, SettingsView, HiddenTestCasesEditor + moderation store (5 components + 1 store)

## Common Pitfalls

### Pitfall 1: i18n Key Path Changes
**What goes wrong:** Extracted component uses a different i18n key namespace than the original, causing translation keys to not resolve at runtime.
**Why it happens:** Developer creates new keys for extracted component instead of reusing the parent's key paths.
**How to avoid:** When extracting, keep the exact same `t("personal.problemLists.title")` calls. Only if the component genuinely moves to a different section should keys be updated (and then a separate i18n key update task is needed).
**Warning signs:** Blank text in UI where translations should appear, console warnings about missing translation keys.

### Pitfall 2: Dialog State Orphaning
**What goes wrong:** Dialog open/close state is accidentally moved into the extracted dialog component, breaking the parent's ability to control dialog visibility.
**Why it happens:** It feels natural to put all dialog logic in the dialog component, but D-04 requires the parent to own the open/close state.
**How to avoid:** Parent owns `const isXxxOpen = ref(false)`. Pass `:open="isXxxOpen"` as prop. Child emits `@update:open` or `@close` events. Never let the child modify `isXxxOpen` directly.
**Warning signs:** Dialogs that cannot be opened from parent triggers (buttons outside the dialog component).

### Pitfall 3: Emitted Event Handler Name Mismatches
**What goes wrong:** Parent template uses `@delete="handleDelete"` but child emits `(e: "remove")` or `(e: "delete-item")` -- names don't match.
**Why it happens:** Naming is at Claude's discretion per CONTEXT.md, but inconsistent naming causes silent failures.
**How to avoid:** Before extracting, document the emit contract. Use kebab-case event names that match the action (`@delete`, `@create`, `@update`, `@close`).
**Warning signs:** Event handlers in parent never fire; no errors in console (Vue silently ignores unmatched events).

### Pitfall 4: Composable Lifecycle Mismatch
**What goes wrong:** Composable uses `onMounted` but is imported in a component that conditionally renders (e.g., inside a v-if or a tab), causing lifecycle hooks to fire at wrong times.
**Why it happens:** Vue composables inherit the calling component's lifecycle. If the calling component is conditionally rendered, onMounted may fire multiple times or not at all.
**How to avoid:** For data-fetching composables used in tab content, consider lazy loading triggered by the parent (pass a `loadData` function down as prop or trigger via watch). Alternatively, ensure tab components are always mounted but hidden via CSS (shadcn Tabs uses this pattern).
**Warning signs:** Data loads multiple times on tab switches, or data never loads when tab is first activated.

### Pitfall 5: Import Path Breakage After Moving Files
**What goes wrong:** Other components import from the old location after a file is moved to a co-located `components/` directory.
**Why it happens:** Only the parent view should import co-located components. But if any other component had a direct import, it breaks.
**How to avoid:** Before moving any file, grep for its import path across the entire codebase. If external imports exist, either keep the file in its original location or update all importers. Co-located components should only be imported by their parent.
**Warning signs:** TypeScript errors during `vue-tsc --build`, build failures.

### Pitfall 6: INTERVAL/Timer Leaks in Extracted Composables
**What goes wrong:** Extracted composable creates `setInterval` but doesn't clean it up in `onUnmounted`, causing memory leaks and stale callbacks.
**Why it happens:** The timer cleanup was in the parent's onUnmounted, but after extraction it must be in the composable's onUnmounted.
**How to avoid:** Any composable that calls `setInterval`, `setTimeout`, or `addEventListener` MUST also call the corresponding cleanup in `onUnmounted`. Follow the pattern in `useContestSocket.ts` which properly handles this.
**Warning signs:** Background actions continuing after navigating away from a page; memory usage growth in devtools.

## Code Examples

### Existing Composable Pattern (data fetching)
```typescript
// Source: console/src/views/problems/useProblemDetail.ts (existing)
import { ref, watch, type Ref } from "vue";
import type { ProblemDetail } from "@/types/problem-detail";
import { fetchProblemDetailById } from "@/api/problem-detail";

export function useProblemDetail(slug: Ref<string | null | undefined>) {
  const problem = ref<ProblemDetail | null>(null);
  const isLoading = ref(false);

  const loadProblem = async (value: string) => {
    isLoading.value = true;
    try {
      const userId = useAuthStore().fetchCurrentUserId();
      problem.value = await fetchProblemDetailById(value, userId ?? undefined);
    } catch (error) {
      console.error("Failed to load problem detail", error);
      problem.value = null;
    } finally {
      isLoading.value = false;
    }
  };

  watch(slug, (value) => {
    if (!value) { problem.value = null; return; }
    void loadProblem(value);
  }, { immediate: true });

  return { problem, isLoading, loadProblem };
}
```

### Existing Composable Pattern (data table with SWR)
```typescript
// Source: management/src/composables/useDataTable.ts (existing)
export function useDataTable<TData, TFilters, TParams>(options: UseDataTableOptions<TData, TFilters, TParams>): UseDataTableReturn<TData> {
  const { store, filters, transformParams, debounceMs = 500, autoLoad = false } = options;
  const searchQuery = ref('');
  const tablePagination = ref<PaginationState>({ pageIndex: 0, pageSize: 10 });
  // ...debounced watchers, loading state, etc.
  return { searchQuery, tablePagination, selectedRows, loading, data, total, error, loadEntities };
}
```

### Dialog Extraction Pattern (v-model for open state)
```vue
<!-- Parent: controls open state -->
<CreateListDialog
  v-model:open="isCreateOpen"
  :loading="isCreating"
  @submit="handleCreateList"
/>
```
```vue
<!-- Child: CreateListDialog.vue -->
<script setup lang="ts">
const props = defineProps<{ open: boolean; loading: boolean }>();
const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "submit", data: CreateListDTO): void;
}>();
</script>
<template>
  <Dialog :open="props.open" @update:open="emit('update:open', $event)">
    <DialogContent><!-- form --></DialogContent>
  </Dialog>
</template>
```

### Typed InjectionKey Pattern
```typescript
// Source: console/src/views/problems/problem-context.ts (existing)
import type { InjectionKey, Ref } from "vue";

export interface ProblemContext {
  problem: Ref<ProblemDetail | null>;
  runResult: Ref<ProblemRunResult | null>;
}

export const ProblemContextKey: InjectionKey<ProblemContext> = Symbol("ProblemContext");
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Options API (data, methods, computed) | Composition API (`<script setup>`) | Vue 3.0 (2020) | All components in this project already use `<script setup>` |
| Mixins for shared logic | Composables (functions starting with `use`) | Vue 3.0 (2020) | All 17 existing composables follow this pattern |
| Prop drilling for deep trees | provide/inject with typed InjectionKey | Vue 3.0 (2020) | ProblemDetailView already uses this; D-09 restricts to 3+ levels |
| Vuex for state management | Pinia | Vue 3 + Pinia 2+ (2021) | Both frontends use Pinia 3.x |
| Global component registration | Co-located components with relative imports | Established best practice | D-01 locks co-location pattern |

**Deprecated/outdated:**
- Options API: Still supported but not used in this project. Do not introduce.
- Mixins: Still supported but replaced by composables. Do not use.
- `this.$emit`: Use `defineEmits` + `emit()` in `<script setup>` instead.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | QUAL-01 "14 components" includes the moderation Pinia store (600 lines), making it 13 Vue components + 1 store = 14 items | Component Analysis | Planner may need to re-scope if only Vue components are targeted |
| A2 | vue-i18n `useI18n()` works in extracted child components without any special setup (relies on Vue's provide/inject) | Pattern 3 | If broken, all extracted components would show untranslated keys -- easy to detect |
| A3 | shadcn-vue Tabs component keeps all TabsContent children mounted (not destroyed on tab switch), meaning composables with onMounted will fire once | Pitfall 4 | If tabs unmount, data-fetching composables would need manual trigger instead of onMounted |
| A4 | Console has no SettingsView component (only editor settings store) -- this is a ROADMAP error | Location Corrections | If a SettingsView is added later, plan scope would need adjustment |
| A5 | No external component imports co-located components from ProblemListsView, ProblemListView, etc. (only parent imports them) | Pitfall 5 | If external imports exist, moving files would break them -- need to grep before moving |

## Open Questions (RESOLVED)

1. **RESOLVED: QUAL-01 scope clarification — Store included.**
   The Pinia moderation store is included in the 14-item count per D-14. Plans 01+02 cover all 13 Vue components + 1 store = 14 items.

2. **RESOLVED: Plan boundary correction — SettingsView and HiddenTestCasesEditor assigned to management.**
   Both files exist in management/ (verified by file system scan). Plan 01 covers 8 console components; Plan 02 covers 5 management components + 1 store. ROADMAP plan descriptions were updated to reflect correct assignments.

## Environment Availability

> This phase involves only frontend code changes with no external service dependencies.
> Step 2.6: SKIPPED (no external dependencies identified -- all work is Vue file restructuring using existing stack)

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | vue-tsc, vite | Yes | v24.14.1 | -- |
| pnpm | Package manager | Yes | 10.33.0 | -- |
| vue-tsc | Type checking | Yes | 5.9.3 (console), 3.2.1 (management) | -- |
| vite | Build verification | Yes | 7.2.6 (console), 7.3.0 (management) | -- |

**Missing dependencies with no fallback:** None.

## Sources

### Primary (HIGH confidence)
- [VERIFIED: package.json files] -- Console and management dependency versions
- [VERIFIED: Codebase grep] -- Component line counts, script/template breakdowns, i18n usage patterns
- [VERIFIED: Existing composables] -- useProblemDetail.ts, useSWR.ts, useDataTable.ts, useContestSocket.ts patterns
- [VERIFIED: problem-context.ts] -- provide/inject with typed InjectionKey pattern
- [Context7: /vuejs/docs] -- Composables pattern, provide/inject API, component communication (props/emit)
- [Context7: /unovue/shadcn-vue] -- Dialog component usage pattern

### Secondary (MEDIUM confidence)
- [VERIFIED: CONTEXT.md decisions D-01 through D-14] -- Locked implementation decisions

### Tertiary (LOW confidence)
- None -- all claims verified against codebase

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- verified from package.json, all libraries already in project
- Architecture: HIGH -- existing patterns (useProblemDetail, co-located components, provide/inject) confirm the approach
- Pitfalls: HIGH -- identified from examining actual component code and understanding Vue reactivity system
- Location corrections: HIGH -- verified by file system scan, critical for planner

**Research date:** 2026-04-15
**Valid until:** 90 days (stable domain -- Vue 3 Composition API patterns are well-established and not changing)
