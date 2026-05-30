# Plan: Contest API Phase 6 — 管理端对齐 (Management Console Alignment)

## Summary
Align the Management frontend Contest API types with the backend DTOs and the Console frontend enum conventions. Convert `ContestFormat` and `ContestStatus` from string-union type aliases to TypeScript enums (matching Console), rename the UI-facing `ContestStatus` to `ContestUiStatus` to eliminate semantic collision, and fix `problemIds` type from `string[]` to `number[]` to match backend `List<Long>`.

## User Story
As a maintainer of the Management frontend,
I want Contest types to use proper enums and align with backend DTO field types,
So that type safety is consistent across frontends, backend contract changes are caught at compile time, and there is no ambiguity between API state values and UI presentation states.

## Problem → Solution
**Current state**:
- Management uses `type ContestFormat = 'ICPC' | 'IOI' | 'CUSTOM'` — a string union that offers no enum-style reverse mapping and diverges from Console's `ContestType` enum.
- Management uses `type ContestStatus = 'DRAFT' | 'UPCOMING' | ...` — same issue; Console already uses `enum ContestStatus`.
- `ContestStatusBadge.vue` defines its own `ContestStatus` type with UI states (`draft`, `published`, `ongoing`...) that collides semantically with the API `ContestStatus`.
- `problemIds` is typed as `string[]` in Management but backend sends/receives `List<Long>` (JSON numbers); Console already types it as `number[]`.

**Desired state**:
- Management defines `ContestType` and `ContestStatus` as TS enums identical to Console.
- UI presentation status in `ContestStatusBadge.vue` is renamed to `ContestUiStatus`, eliminating naming collision.
- `problemIds` is `number[]` across all Management Contest interfaces.

## Metadata
- **Complexity**: Small
- **Source PRD**: `docs/contest-api-alignment-analysis.md`
- **PRD Phase**: Phase 6 — 管理端对齐
- **Estimated Files**: 6

---

## UX Design

Internal change — no user-facing UX transformation. Only type-level changes; runtime behavior is identical.

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `management/src/api/admin/contests.ts` | 1-159 | Source of truth for API types being changed |
| P0 (critical) | `console/src/types/contest.ts` | 1-17 | Target enum patterns to mirror |
| P1 (important) | `management/src/views/contest/components/ContestStatusBadge.vue` | 1-125 | UI status type to rename |
| P1 (important) | `management/src/views/contest/components/ContestCard.vue` | 1-30 | Imports UI status type |
| P1 (important) | `management/src/views/contest/components/index.ts` | 1-5 | Re-exports UI status type |
| P2 (reference) | `management/src/api/admin/__tests__/contests.spec.ts` | 1-217 | Tests to update for enum values |

---

## External Documentation

No external research needed — feature uses established internal patterns (Console already has the target enum definitions).

---

## Patterns to Mirror

### CONSOLE_ENUM_PATTERN
// SOURCE: `console/src/types/contest.ts:1-17`
```typescript
export enum ContestType {
  ICPC = "ICPC",
  IOI = "IOI",
  CUSTOM = "CUSTOM",
}

export enum ContestStatus {
  DRAFT = "DRAFT",
  UPCOMING = "UPCOMING",
  RUNNING = "RUNNING",
  FINISHED = "FINISHED",
  CANCELLED = "CANCELLED",
}
```

### MANAGEMENT_API_PATTERN
// SOURCE: `management/src/api/admin/contests.ts:1-40`
```typescript
export type ContestFormat = 'ICPC' | 'IOI' | 'CUSTOM'
export type ContestStatus = 'DRAFT' | 'UPCOMING' | 'RUNNING' | 'FINISHED' | 'CANCELLED'

export interface Contest {
  id: string
  contestType: ContestFormat
  status: ContestStatus
  // ...
}
```

### UI_STATUS_PATTERN
// SOURCE: `management/src/views/contest/components/ContestStatusBadge.vue:12-21`
```typescript
export type ContestStatus =
  | 'draft'
  | 'published'
  | 'registering'
  | 'upcoming'
  | 'ongoing'
  | 'freezing'
  | 'finished'
  | 'archived'
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `management/src/api/admin/contests.ts` | UPDATE | Convert `ContestFormat` → `ContestType` enum, `ContestStatus` → `ContestStatus` enum, `problemIds` → `number[]` |
| `management/src/views/contest/components/ContestStatusBadge.vue` | UPDATE | Rename exported type `ContestStatus` → `ContestUiStatus` |
| `management/src/views/contest/components/ContestCard.vue` | UPDATE | Import `ContestUiStatus` instead of `ContestStatus` from badge |
| `management/src/views/contest/components/index.ts` | UPDATE | Re-export `ContestUiStatus` instead of `ContestStatus` |
| `management/src/api/admin/__tests__/contests.spec.ts` | UPDATE | Replace `ContestFormat` imports/usage with `ContestType` enum |
| `management/src/views/contests/wizard/StepBasicInfo.vue` | UPDATE | Import `ContestType` instead of `ContestFormat`; update `CONTEST_FORMATS` array |
| `management/src/views/contests/wizard/ContestWizard.vue` | UPDATE | Import `ContestType` instead of `ContestFormat`; update default value |
| `management/src/views/contests/ContestsListView.vue` | UPDATE | Import `ContestType` instead of `ContestFormat` |

## NOT Building

- No backend Java changes (backend DTOs already have correct enums and `List<Long>`)
- No changes to `ContestServiceImpl` slug generation behavior
- No Console frontend changes (Console is already the reference pattern)
- No database schema changes
- No new API endpoints
- No changes to the UI status badge colors, labels, or logic — only the type name changes

---

## Step-by-Step Tasks

### Task 1: Replace `ContestFormat` with `ContestType` enum
- **ACTION**: Update `management/src/api/admin/contests.ts` to define and use `ContestType` enum
- **IMPLEMENT**:
  1. Replace `export type ContestFormat = 'ICPC' | 'IOI' | 'CUSTOM'` with:
     ```typescript
     export enum ContestType {
       ICPC = 'ICPC',
       IOI = 'IOI',
       CUSTOM = 'CUSTOM',
     }
     ```
  2. In `Contest` interface: change `contestType: ContestFormat` → `contestType: ContestType`
  3. In `CreateContestDto`: change `contestType?: ContestFormat` → `contestType?: ContestType`
  4. In `UpdateContestDto`: change `contestType?: ContestFormat` → `contestType?: ContestType`
  5. In `ContestQueryParams`: change `contestType?: ContestFormat` → `contestType?: ContestType`
- **MIRROR**: `console/src/types/contest.ts:1-9` (`ContestType` enum)
- **IMPORTS**: No new imports needed
- **GOTCHA**: `as ContestFormat` type assertions in test files and wizard components must be updated to use `ContestType.ICPC` etc.
- **VALIDATE**: `pnpm type-check` in `management/`

### Task 2: Replace API-facing `ContestStatus` with `ContestStatus` enum
- **ACTION**: Update `management/src/api/admin/contests.ts` to define and use `ContestStatus` enum
- **IMPLEMENT**:
  1. Replace `export type ContestStatus = 'DRAFT' | 'UPCOMING' | 'RUNNING' | 'FINISHED' | 'CANCELLED'` with:
     ```typescript
     export enum ContestStatus {
       DRAFT = 'DRAFT',
       UPCOMING = 'UPCOMING',
       RUNNING = 'RUNNING',
       FINISHED = 'FINISHED',
       CANCELLED = 'CANCELLED',
     }
     ```
  2. In `Contest` interface: change `status: ContestStatus` → `status: ContestStatus` (name stays, meaning changes from alias to enum)
- **MIRROR**: `console/src/types/contest.ts:11-17` (`ContestStatus` enum)
- **IMPORTS**: No new imports needed
- **GOTCHA**: Files that import `ContestStatus` from `contests.ts` will now get the enum. Ensure no file imports both the enum from `contests.ts` AND the UI type from `ContestStatusBadge.vue`.
- **VALIDATE**: `pnpm type-check` in `management/`

### Task 3: Rename UI `ContestStatus` to `ContestUiStatus`
- **ACTION**: Rename the UI presentation type in `ContestStatusBadge.vue` and all its consumers
- **IMPLEMENT**:
  1. In `ContestStatusBadge.vue` line 12: rename `export type ContestStatus` → `export type ContestUiStatus`
  2. In `ContestStatusBadge.vue` line 24: update `status: ContestStatus` → `status: ContestUiStatus`
  3. In `ContestStatusBadge.vue` line 35: update `ContestStatus,` → `ContestUiStatus,`
  4. In `ContestStatusBadge.vue` line 88: update `statusConfig[props.status]` reference — no change needed since local variable name is unchanged
  5. In `ContestCard.vue` line 15: change `import ContestStatusBadge, { type ContestStatus }` → `import ContestStatusBadge, { type ContestUiStatus }`
  6. In `ContestCard.vue` line 26: change `status: ContestStatus` → `status: ContestUiStatus`
  7. In `ContestCard.vue` line 52: `props.contest.status === 'upcoming'` — no runtime change
  8. In `index.ts` line 5: change `export type { ContestStatus } from './ContestStatusBadge.vue'` → `export type { ContestUiStatus } from './ContestStatusBadge.vue'`
- **MIRROR**: `ContestStatusBadge.vue:12-21` (type definition pattern)
- **IMPORTS**: No new imports needed
- **GOTCHA**: Do NOT rename the `ContestStatusBadge` component itself — only the exported type. The component name stays.
- **VALIDATE**: `pnpm type-check` in `management/`

### Task 4: Align `problemIds` from `string[]` to `number[]`
- **ACTION**: Fix the `problemIds` type in `Contest`, `CreateContestDto`, and `UpdateContestDto`
- **IMPLEMENT**:
  1. In `contests.ts` `Contest` interface line 26: change `problemIds?: string[]` → `problemIds?: number[]`
  2. In `contests.ts` `CreateContestDto` line 91: change `problemIds?: string[]` → `problemIds?: number[]`
  3. In `contests.ts` `UpdateContestDto` line 106: change `problemIds?: string[]` → `problemIds?: number[]`
- **MIRROR**: `console/src/types/contest.ts:141` (`problemIds: number[]`)
- **IMPORTS**: None
- **GOTCHA**: Test file `contests.spec.ts` line 108 already uses `[1, 2, 3]` (numbers), so no test change needed for this field. Verify no other file passes string IDs.
- **VALIDATE**: `pnpm type-check` in `management/`

### Task 5: Update wizard and list view imports
- **ACTION**: Update all files that import `ContestFormat` to import `ContestType` instead
- **IMPLEMENT**:
  1. `StepBasicInfo.vue` line 11: `import type { ContestFormat }` → `import type { ContestType }`
  2. `StepBasicInfo.vue` line 14: `const CONTEST_FORMATS: ContestFormat[] = ['ICPC', 'IOI', 'CUSTOM']` → `const CONTEST_FORMATS: ContestType[] = [ContestType.ICPC, ContestType.IOI, ContestType.CUSTOM]`
  3. `StepBasicInfo.vue` line 21: `contestType: ContestFormat` → `contestType: ContestType`
  4. `StepBasicInfo.vue` line 32: `updateField(field: string, value: string | number | bigint | ContestFormat | null)` → `updateField(field: string, value: string | number | bigint | ContestType | null)`
  5. `StepBasicInfo.vue` line 78: `@update:model-value="updateField('contestType', $event as ContestFormat)"` → `@update:model-value="updateField('contestType', $event as ContestType)"`
  6. `ContestWizard.vue` line 17: `import type { ContestFormat }` → `import type { ContestType }`
  7. `ContestWizard.vue` line 51: `contestType: 'ICPC' as ContestFormat` → `contestType: ContestType.ICPC`
  8. `ContestWizard.vue` line 144: `contestType: 'ICPC' as ContestFormat` → `contestType: ContestType.ICPC`
  9. `ContestsListView.vue` line 11: `import type { Contest, ContestFormat }` → `import type { Contest, ContestType }`
  10. `ContestsListView.vue` line 113: `filters.typeFilter === 'all' ? undefined : (filters.typeFilter as ContestFormat | undefined)` → `filters.typeFilter === 'all' ? undefined : (filters.typeFilter as ContestType | undefined)`
- **MIRROR**: `ContestWizard.vue:51` (default value pattern)
- **IMPORTS**: `ContestType` from `@/api/admin/contests`
- **GOTCHA**: In `StepBasicInfo.vue`, the `updateField` function signature uses a union; adding `ContestType` to the union is correct because the enum is a subtype of `string` at runtime but TypeScript treats it as a distinct type in the union.
- **VALIDATE**: `pnpm type-check` in `management/`

### Task 6: Update unit tests for enum values
- **ACTION**: Replace `ContestFormat` usage with `ContestType` in `contests.spec.ts`
- **IMPLEMENT**:
  1. Line 2: `import { contestsApi, CreateContestDto, ContestFormat }` → `import { contestsApi, CreateContestDto, ContestType }`
  2. Line 24: `contestType: 'ICPC' as ContestFormat` → `contestType: ContestType.ICPC`
  3. Line 39: `contestType: 'ICPC'` → `contestType: ContestType.ICPC`
  4. Line 52: `expect(result.contestType).toBe('ICPC')` → `expect(result.contestType).toBe(ContestType.ICPC)`
  5. Line 60: `contestType: 'CUSTOM' as ContestFormat` → `contestType: ContestType.CUSTOM`
  6. Line 74: `contestType: 'CUSTOM'` → `contestType: ContestType.CUSTOM`
  7. Line 83: `expect(result.contestType).toBe('CUSTOM')` → `expect(result.contestType).toBe(ContestType.CUSTOM)`
  8. Line 91: `contestType: 'ICPC' as ContestFormat` → `contestType: ContestType.ICPC`
  9. Line 105: `contestType: 'ICPC'` → `contestType: ContestType.ICPC`
  10. Line 125: `contestType: 'ICPC'` → `contestType: ContestType.ICPC`
  11. Line 160: `contestType: 'ICPC' as ContestFormat` → `contestType: ContestType.ICPC`
  12. Line 184: `contestType: 'ICPC' as ContestFormat` → `contestType: ContestType.ICPC`
- **MIRROR**: `contests.spec.ts:24` (test mock pattern)
- **IMPORTS**: `ContestType` from `@/api/admin/contests`
- **GOTCHA**: The mock objects in tests don't need to be full `Contest` objects; they just need the fields used in assertions. Keep them as plain objects — TypeScript will accept enum values.
- **VALIDATE**: `pnpm test` in `management/`

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `createContest` with `ContestType.ICPC` | `CreateContestDto` with enum value | API called with `"ICPC"` string | No |
| `createContest` with `ContestType.CUSTOM` | `CreateContestDto` with enum value | API called with `"CUSTOM"` string | No |
| `problemIds` as `number[]` | `[1, 2, 3]` | Compiles without type error | No |
| `ContestUiStatus` badge renders | `'ongoing'` | Shows red animated badge | No |
| `ContestCard` accepts `ContestUiStatus` | `'draft'` | Shows gray badge | No |

### Edge Cases Checklist
- [ ] Enum values serialize correctly to strings in JSON payloads (TS enums with string values do this by default)
- [ ] No file imports both `ContestStatus` enum and `ContestUiStatus` type with confusion
- [ ] `ContestWizard` default values compile with enum instead of string literal
- [ ] `StepBasicInfo` `CONTEST_FORMATS` array uses enum values in template options

---

## Validation Commands

### Static Analysis (Management Frontend)
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management
pnpm type-check
```
EXPECT: Zero type errors

### Unit Tests (Management Frontend)
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management
pnpm test
```
EXPECT: All tests pass (including updated `contests.spec.ts`)

### Lint Check (Management Frontend)
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management
pnpm lint
```
EXPECT: No lint errors

### Type Check (Console Frontend — regression check)
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console
pnpm type-check
```
EXPECT: Zero type errors (Console should be unaffected)

---

## Acceptance Criteria
- [ ] `management/src/api/admin/contests.ts` defines `ContestType` enum (not `ContestFormat` alias)
- [ ] `management/src/api/admin/contests.ts` defines `ContestStatus` enum (not type alias)
- [ ] `ContestStatusBadge.vue` exports `ContestUiStatus` (not `ContestStatus`)
- [ ] `ContestCard.vue` uses `ContestUiStatus` import
- [ ] `problemIds` is `number[]` in all Management Contest interfaces
- [ ] All wizard and list view files import `ContestType` instead of `ContestFormat`
- [ ] `contests.spec.ts` passes with updated enum imports and values
- [ ] `pnpm type-check` passes in `management/`
- [ ] `pnpm test` passes in `management/`

## Completion Checklist
- [ ] Code follows discovered patterns (Console enum definitions)
- [ ] No naming collisions between API and UI status types
- [ ] Tests follow test patterns (Vitest, mock API responses)
- [ ] No hardcoded values (use enum members instead of string literals)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `ContestStatus` enum name collision with UI status in some file | Low | Medium | `pnpm type-check` will catch any ambiguous import; grep for `import.*ContestStatus` to verify |
| Wizard template options bound to string literals instead of enum values | Medium | Medium | Check `StepBasicInfo.vue` template — ensure `CONTEST_FORMATS` array is used for options and emits enum values |
| Test mocks using old `as ContestFormat` assertion | Low | Low | `pnpm test` will fail with clear TS errors; grep for `ContestFormat` in `__tests__` |

## Notes
- The backend `CreateContestDTO` and `UpdateContestDTO` already contain a `slug` field with validation annotations (`@Pattern`, `@Size`). The analysis document incorrectly states it is missing — it was added after the analysis was written. However, `ContestServiceImpl` ignores `dto.getSlug()` during both create and update, always regenerating from title. Changing service behavior is out of scope for Phase 6; the DTO field alignment is already correct.
- Console frontend is the reference pattern for enums. Management should mirror it exactly so both frontends can share type definitions in the future if desired.
- `ContestUiStatus` is intentionally kept separate from `ContestStatus` because they represent different concerns: API state vs. UI presentation state. A future mapping layer (e.g., `mapApiStatusToUiStatus`) could bridge them if needed.
