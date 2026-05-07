
## ProblemListServiceTest (Wave 2)
- File: `backend-spring/src/test/java/com/ulticode/modules/problemlist/service/ProblemListServiceTest.java`
- Pattern: `@ExtendWith(MockitoExtension.class)` + manual constructor injection in `@BeforeEach`
  - `@InjectMocks` does NOT work with Lombok `@RequiredArgsConstructor` — must instantiate manually
  - Reference: `ProblemVersionServiceTest` uses same pattern
- Mockito setup: `@Mock` for all 6 dependencies, then `new ProblemListServiceImpl(...)` in setUp
- Test structure: `@Nested` classes per method (`UpdateBasicInfoTests`, `UpdateVisibilityTests`, `UpdateBannerTests`)
- Test cases:
  - `updateBasicInfo_Success` — verifies name/description updated, ArgumentCaptor checks entity state
  - `updateBasicInfo_NotOwner` — verifies `BusinessException` with `PROBLEM_LIST_CANNOT_EDIT`, no updateById called
  - `updateBasicInfo_NotFound` — verifies `BusinessException` with `PROBLEM_LIST_NOT_FOUND`
  - `updateVisibility_Success` — verifies isPublic/isFeatured updated, other fields unchanged
  - `updateVisibility_OnlyPublic` — null isFeatured leaves field untouched (conditional update)
  - `updateBanner_Success` — verifies all 3 banner fields updated
  - `updateBanner_PartialUpdate` — null fields left unchanged (conditional update)
  - `updateBanner_NotOwner` — ownership check
- Helper methods: `createProblemList()` (sets all fields including version=1), `mockOwnerUser()` (stubs userMapper)
- ArgumentCaptor usage: `ArgumentCaptor.forClass(ProblemList.class)` to verify updateById received correct entity
- `verify(problemListMapper, never()).updateById(any(ProblemList.class))` — must use typed `any()` to avoid ambiguous method reference (BaseMapper has `updateById(T)` and `updateById(Collection<T>)`)
- Test results: 9 tests, 0 failures, BUILD SUCCESS
- Note: `toSummaryVOWithSavedStatus` calls `problemListProblemMapper.countByListId` and `userMapper.selectById` — both must be mocked in success cases
- Note: `toSummaryVOWithSavedStatus` also calls `problemListBookmarkMapper.existsByUserIdAndListId` — must be mocked

## useAutoSave Composable (Wave 3)
- File: `management/src/composables/useAutoSave.ts`
- Pattern: Generic composable wrapping user-provided save function with debounce + AbortController cancellation
- VueUse integration: `useDebounceFn` from `@vueuse/core` for delayed saves (default 1000ms)
- AbortController pattern:
  - Each `performSave` creates new AbortController, passes signal to `saveFn`
  - New save aborts previous in-flight request before starting
  - `cancel()` exposes manual abort to consumers
  - AbortError is caught and treated as non-error (reverts to idle)
- Blur trigger: `blurTriggers` option (default true) controls debounce bypass
  - When true: uses `debouncedSave` (debounced)
  - When false: calls `performSave` directly (immediate)
  - Note: blur event handling is consumer's responsibility — composable only provides the mechanism
- State tracking: `saveStatus` ('idle' | 'saving' | 'saved' | 'error'), `lastSavedAt` (Date | null), `error` (Error | null)
- Type signature: `saveFn: (data: T, signal: AbortSignal) => Promise<void>` — consumer must accept and respect AbortSignal
- Test file: `management/src/composables/useAutoSave.test.ts`
  - 10 tests covering: initialization, debounce, cancellation, error handling, lastSavedAt, cancel(), blurTriggers=false, AbortError handling, default timing, signal passing
  - Uses `vi.useFakeTimers()` + `vi.advanceTimersByTime()` for debounce testing
  - Mock saveFn uses `signal.addEventListener('abort', ...)` to simulate real cancellation behavior
  - Test results: 10 tests, 0 failures, 525ms

## useProblemListPermissions Composable (Wave 3)
- File: `management/src/composables/useProblemListPermissions.ts`
- Pattern: Simple permission wrapper using auth store's `hasPermission` method
- Permission constants from `@/constants/permissions`:
  - `PERM.PROBLEM_LIST_UPDATE.action` = 'UPDATE', `PERM.PROBLEM_LIST_UPDATE.resource` = 'PROBLEM_LIST'
  - `PERM.PROBLEM_LIST_MANAGE_PROBLEMS.action` = 'MANAGE_PROBLEMS', `PERM.PROBLEM_LIST_MANAGE_PROBLEMS.resource` = 'PROBLEM_LIST'
- Functions return computed refs for reactivity
- Test file: `management/src/composables/useProblemListPermissions.test.ts`
  - 8 tests covering all permission checks (true/false for each of 4 functions)
  - Uses `createPinia()` + `setActivePinia()` in beforeEach
  - Mock: `vi.mocked(useAuthStore).mockReturnValue({ hasPermission: mockFn } as unknown as ReturnType<typeof useAuthStore>)`
- Note: Must use `as unknown as` casting to satisfy TypeScript when mocking Pinia store
- Test results: 8 tests, 0 failures, PASS

## VisibilitySection Component (Wave 3)
- File: `management/src/views/problem-lists/components/VisibilitySection.vue`
- Props: `modelValue` (ProblemList | null), `disabled` (boolean, optional)
- Emits: `update:modelValue` with updated ProblemList
- Fields: isPublic (Switch, green when checked), isFeatured (Switch, amber when checked)
- Auto-save implementation:
  - Uses `useAutoSave` composable from `@/composables/useAutoSave`
  - Debounce 1000ms, blurTriggers: true
  - API call: `adminProblemListsApi.updateVisibility(id, { isPublic, isFeatured })`
- Tooltip: Shows `[?]` trigger with TooltipContent when isFeatured=true explaining implications
  - Uses `TooltipProvider`, `Tooltip`, `TooltipTrigger`, `TooltipContent` from `@/components/ui/tooltip`
- Save status indicator: 'idle'/'saving'/'saved'/'error' with color coding (gray/green/red)
  - Displayed below the switch grid
- Test file: `management/src/views/problem-lists/components/VisibilitySection.test.ts`
  - 10 tests covering: rendering, auto-save, disabled state, props
  - Uses `vi.hoisted()` for stubs that are referenced in `vi.mock()` factories
  - Issue: `vi.mock` is hoisted but stubs are runtime - `vi.hoisted()` solves this
  - Issue: `SwitchStub` referenced in `vi.mock('@/components/ui/switch', ...)` caused "Cannot access before initialization"
  - Fix: Use `vi.hoisted(() => ({ SwitchStub: {...}, ... }))` to define stubs at hoisting time
  - Test results: 10 tests, 0 failures, PASS

## BasicInfoSection Component (Wave 3)
- File: `management/src/views/problem-lists/components/BasicInfoSection.vue`
- Props: `modelValue` (ProblemListDetail | null), `disabled` (boolean, optional)
- Emits: `update:modelValue` with updated ProblemListDetail
- Auto-save implementation:
  - Uses `watchDebounced` from `@vueuse/core` (1s debounce, 2s maxWait)
  - Also saves on blur via `handleBlur` function
  - Skips save if values haven't changed (compares with `lastSavedValues`)
  - API call: `adminProblemListsApi.updateBasicInfo(id, { name, description })`
- Save status indicator: 'idle'/'saving'/'saved'/'error'
  - Displayed as small text next to name label with color coding
- Validation: vee-validate + Zod schema requiring name min 1, max 100 chars
- Exposes `saveStatus` via `defineExpose` for testing
- Test file: `management/src/views/problem-lists/components/BasicInfoSection.test.ts`
  - 8 tests covering: rendering, props handling, auto-save behavior, validation
  - Uses stubs for FormField, FormItem, FormLabel, FormControl, FormMessage, Input, Textarea
  - Key issue: Stubs must use `h()` from vue, not `mount()` from test-utils
  - Key issue: `saveChanges` skips if values haven't changed — must modify form values before calling
  - Test results: 8 tests, 0 failures, PASS

## BannerSection Component (Wave 3)
- File: `management/src/views/problem-lists/components/BannerSection.vue`
- Props: `modelValue` (ProblemList | null), `disabled` (boolean, optional)
- Emits: `update:modelValue` with updated ProblemList
- Fields: bannerTag (Input, max 50 chars), bannerTheme (Select: blue/green/purple/orange/red), bannerOrder (Number)
- Auto-save implementation:
  - Uses `useDebounceFn` from `@vueuse/core` (1s debounce)
  - Saves on blur via `handleBlur` function
  - Skips save if values haven't changed (compares with `lastSavedValues`)
  - API call: `adminProblemListsApi.updateBanner(id, { bannerTag, bannerTheme, bannerOrder })`
- Save status indicator: 'idle'/'saving'/'saved'/'error'
  - Displayed below the form fields with color coding (gray/green/red)
- TypeScript issue: `debouncedSave.cancel()` — UseDebounceFnReturn has cancel but TypeScript doesn't infer it
  - Fix: Cast to `{ cancel?: () => void }` before calling
- Test file: `management/src/views/problem-lists/components/BannerSection.test.ts`
  - 5 tests covering: rendering, props handling
  - Uses simple stubs for FormField, FormItem, etc. with `stubs: { FormField: true, ... }`
  - Issue: `vi.mock` hoisting - mock variables defined after vi.mock calls cause "Cannot access before initialization"
  - Fix: Use simple stubs without complex factory functions, rely on `stubs` option in mount
  - Test results: 5 tests, 0 failures, PASS

## ProblemListDetailView Refactor (Wave 3)
- File: `management/src/views/problem-lists/ProblemListDetailView.vue`
- Change: Removed tabbed layout (`activeTab`, `tabs`, `v-show`) + `GeneralInfo` component
- New layout: 3 sections stacked vertically (`space-y-8`) + `ProblemsManager` below
  - `BasicInfoSection` — name/description with auto-save
  - `VisibilitySection` — isPublic/isFeatured with auto-save
  - `BannerSection` — bannerTag/bannerTheme/bannerOrder with auto-save
  - `ProblemsManager` — problem list management (manual save)
- `list` ref changed from `computed(() => store.currentList)` to writable computed:
  - `get: () => store.currentList`, `set: (val) => store.currentList = val`
  - This allows `v-model` pattern via `@update:modelValue="handleListUpdate"`
- `handleListUpdate(updatedList)` — syncs section-emitted updates back to store
- Each section receives `:model-value="list"` and emits `update:modelValue` with partial updates
- `isCreate` flag disables sections during creation (no list ID to save against)
- Type-check: Clean — only pre-existing TS5101 deprecation warning (baseUrl), no errors in modified file
- Removed unused: `GeneralInfo` import, `activeTab` ref, `tabs` computed, tab navigation HTML

## BasicInfoSection Create Mode (Wave 3)
- File: `management/src/views/problem-lists/components/BasicInfoSection.vue`
- Added `isCreate` prop (boolean, default false) to toggle between create and edit behavior
- Added `success` emit `(id: string)` — fired after successful create, parent redirects to edit page
- Create mode behavior:
  - No auto-save (watchDebounced and blur handler gated with `!props.isCreate`)
  - No save status indicator next to name field
  - Shows "Create" button at bottom of form instead
  - On click: validates form, calls `adminProblemListsApi.createList({ name, description })`
  - On success: emits `success` with new list ID, parent (`ProblemListDetailView`) calls `handleCreateSuccess` → `router.replace({ name: 'problem-list-edit', params: { id } })`
  - On error: shows toast with network/server error message
- Edit mode behavior: unchanged (auto-save with debounce, blur trigger, status indicator)
- ProblemListDetailView changes:
  - Passes `:is-create="isCreate"` to BasicInfoSection
  - Listens to `@success="handleCreateSuccess"` on BasicInfoSection
  - Other sections (Visibility, Banner) still receive `:disabled="isCreate"` — disabled during creation
- Type-check: Clean — no errors in modified files

## E2E Tests for Problem List Edit Flow (Wave 3)
- File: `management/src/e2e/problem-list-edit.spec.ts`
- Playwright version issues encountered:
  - Multiple versions of @playwright/test in pnpm store caused conflicts
  - Error: "Playwright Test did not expect test.describe() to be called here"
  - Tried versions 1.38.0, 1.40.0, 1.59.1 — all had same issue
  - Root cause: Playwright requires tests to be run through its CLI, not directly via node
  - Tests must be executed with `npx playwright test` command
- Test structure created (8 tests across 4 describe blocks):
  1. Create Flow:
     - Create new list → redirect to edit page
     - Validation error when name is empty
  2. Edit Flow with Auto-Save:
     - Auto-save after debounce (1.5s wait)
     - Save on blur immediately
  3. Module Sections Visibility:
     - All 3 sections visible on edit page (Basic Info, Visibility, Banner)
     - Create button visible in create mode, auto-save status hidden
  4. Error Handling:
     - Network error during auto-save
     - 409 conflict on create
- API mocking pattern: `page.route(url, handler)` with `route.fulfill()`
- Environment variables: VITE_ADMIN_BASE_URL (default 9003), VITE_API_BASE_URL (default 9001)
- Note: Playwright package removed from devDependencies due to version conflicts
  - Test file is ready but requires proper Playwright setup to run
  - Recommendation: Add playwright.config.ts and ensure single version in lockfile
