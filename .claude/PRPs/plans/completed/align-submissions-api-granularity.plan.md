# Plan: Align Submissions Admin Page Frontend-Backend API Granularity

## Summary
Fix 4 identified granularity mismatches between the management frontend (`management/src/views/submissions/`) and the admin backend (`AdminSubmissionController`). The backend behavior is already correct; changes are limited to frontend types, column definitions, and documentation. No backend files need modification.

## User Story
As a maintainer of the UltiCode admin panel, I want the frontend submissions API types and column definitions to match the backend contract exactly, so that type safety is preserved and future server-side sorting can be enabled without breakage.

## Problem → Solution
Current state: 4 minor mismatches exist (`sortBy` enum value, date type mismatch, unused `codeLength` field, overly broad `unknown` types) → Desired state: all types and columns align cleanly with the backend contract.

## Metadata
- **Complexity**: Small
- **Source PRD**: `docs/submissions-api-granularity-analysis.md`
- **PRD Phase**: standalone
- **Estimated Files**: 3-5 frontend files

---

## UX Design

### Before
- Table shows columns: ID, Problem, User, Language, Status, Runtime, Memory, Submitted At, Actions
- `codeLength` data is fetched but never displayed
- Sorting is client-side only; server-side sort parameter is misaligned (`created_at` vs `createdAt`)

### After
- Table shows columns: ID, Problem, User, Language, Status, Runtime, Memory, Code Length, Submitted At, Actions
- Server-side sort parameter is aligned (`createdAt`) for future use

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Submissions table | 9 columns | 10 columns | Adds `codeLength` column |
| Sort param (internal) | `sortBy: 'created_at'` | `sortBy: 'createdAt'` | Aligns with backend switch statement |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `management/src/api/admin/submissions.ts` | 1-125 | Core types to modify |
| P0 | `management/src/views/submissions/columns.ts` | 1-175 | Column definitions to extend |
| P1 | `management/src/views/submissions/SubmissionsView.vue` | 1-505 | Verify column data usage |
| P1 | `management/src/stores/admin/submissions.ts` | 1-134 | Store that consumes API types |
| P2 | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSubmissionVO.java` | 1-77 | Backend contract reference |
| P2 | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSubmissionQueryDTO.java` | 1-49 | Backend query param reference |

## External Documentation

No external research needed — feature uses established internal patterns only.

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: management/src/api/admin/submissions.ts:21-37
```ts
export interface SubmissionListItem {
  id: string
  problemId: number
  problemTitle: string
  // ... camelCase fields
}
```

### COLUMN_DEFINITION
// SOURCE: management/src/views/submissions/columns.ts:60-70
```ts
{
  accessorKey: 'id',
  header: () => t('submissions.id'),
  cell: ({ row }) =>
    h('span', { class: 'font-data text-xs text-[var(--terminal-cyan)]' }, row.original.id.slice(0, 8)),
  enableHiding: false,
}
```

### FORMATTER_FUNCTION
// SOURCE: management/src/views/submissions/columns.ts:19-35
```ts
export function formatRuntime(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}
```

### I18N_MODULE_STRUCTURE
// SOURCE: management/src/i18n/locales/en-US/modules/submissions.ts
```ts
export default {
  id: 'ID',
  problem: 'Problem',
  // keys used by columns.ts header: () => t('submissions.xxx')
}
```

### TABLE_I18N_KEY
// SOURCE: management/src/components/table/DataTable.vue:85-92
```ts
const name = t(`table.columnNames.${columnId}`, columnId)
if (import.meta.env.DEV && name === columnId) {
  console.error(
    `[i18n] Missing translation key: table.columnNames.${columnId}. ` +
      `Add it to management/src/i18n/locales/*/modules/table.ts`,
  )
}
```
> **GOTCHA**: Every `accessorKey` used in columns.ts must have a matching `table.columnNames.${key}` entry in both `en-US/modules/table.ts` and `zh-CN/modules/table.ts`, in both camelCase and snake_case forms.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `management/src/api/admin/submissions.ts` | UPDATE | Fix `sortBy` enum; add date format comment |
| `management/src/views/submissions/columns.ts` | UPDATE | Add `codeLength` column definition |
| `management/src/i18n/locales/en-US/modules/submissions.ts` | UPDATE | Add `codeLength` key |
| `management/src/i18n/locales/zh-CN/modules/submissions.ts` | UPDATE | Add `codeLength` key |
| `management/src/i18n/locales/en-US/modules/table.ts` | UPDATE | Add `table.columnNames.codeLength` (camelCase + snake_case) |
| `management/src/i18n/locales/zh-CN/modules/table.ts` | UPDATE | Add `table.columnNames.codeLength` (camelCase + snake_case) |

## NOT Building
- No backend changes (backend contract is already correct)
- No server-side sorting wiring (out of scope; only fixing the type alignment)
- No changes to `testDetails` / `memoryDistBinsMb` / `runtimeDistBinsMs` types (they are unused in UI and `unknown` is safe)
- No changes to `PageResult`, `SubmissionStatistics`, `StatusOption`, or rejudge DTOs (already aligned)

---

## Step-by-Step Tasks

### Task 1: Fix `sortBy` enum value
- **ACTION**: Update `SubmissionQueryParams.sortBy` type in API types file
- **IMPLEMENT**: Change `'created_at'` to `'createdAt'` in the union type
- **MIRROR**: `management/src/api/admin/submissions.ts:13`
- **IMPORTS**: None
- **GOTCHA**: Ensure the single-quote style matches existing code (single quotes, no semicolons)
- **VALIDATE**: Run `pnpm type-check` in `management/` — expect zero errors

### Task 2: Add `codeLength` column to submissions table
- **ACTION**: Insert a new column definition in `columns.ts` between `memory` and `createdAt`
- **IMPLEMENT**: Add a column with `accessorKey: 'codeLength'`, header `t('submissions.codeLength')`, and a cell renderer that shows the value with a `font-data` class. Use `'-''` for null values.
- **MIRROR**: Follow the `runtime` / `memory` column patterns in `columns.ts:112-130`
- **IMPORTS**: None (uses existing `h` from vue)
- **GOTCHA**: The column must have `accessorKey: 'codeLength'` which matches the API field name; DataTable.vue will look up `table.columnNames.codeLength` for the header.
- **VALIDATE**: Browser devtools console should show no i18n missing-key errors

### Task 3: Add i18n keys for `codeLength`
- **ACTION**: Add translation keys in 4 i18n files
- **IMPLEMENT**:
  - `management/src/i18n/locales/en-US/modules/submissions.ts`: add `codeLength: 'Code Length'`
  - `management/src/i18n/locales/zh-CN/modules/submissions.ts`: add `codeLength: '代码长度'`
  - `management/src/i18n/locales/en-US/modules/table.ts`: add `codeLength: 'Code Length'` and `code_length: 'Code Length'` under `columnNames`
  - `management/src/i18n/locales/zh-CN/modules/table.ts`: add `codeLength: '代码长度'` and `code_length: '代码长度'` under `columnNames`
- **MIRROR**: Follow existing entries in each file
- **IMPORTS**: None
- **GOTCHA**: Both camelCase and snake_case keys are required under `columnNames` per project convention
- **VALIDATE**: Browser console should not emit `[i18n] Missing translation key` for `codeLength`

### Task 4: Add date format comment to API types
- **ACTION**: Add JSDoc comment to `startDate` and `endDate` fields
- **IMPLEMENT**: Add `/** @format ISO-8601 (YYYY-MM-DDTHH:mm:ss) */` above both fields in `SubmissionQueryParams`
- **MIRROR**: Existing JSDoc style in the same file (e.g., lines 30-31)
- **IMPORTS**: None
- **GOTCHA**: Keep comments concise, no multi-paragraph blocks
- **VALIDATE**: Visual inspection — comments should be present and formatted correctly

---

## Testing Strategy

### Type Checking
| Test | Command | Expected |
|---|---|---|
| Type check | `cd management && pnpm type-check` | Zero errors |

### Lint Check
| Test | Command | Expected |
|---|---|---|
| ESLint | `cd management && pnpm lint` | No new warnings |

### Browser Validation
| Test | Step | Expected |
|---|---|---|
| Column visible | Open `/submissions` | `Code Length` column appears between Memory and Submitted At |
| Data populated | Verify rows have values | Non-null code lengths display as numbers; null shows `-` |
| i18n | Switch language (EN/ZH) | Header label translates correctly |
| No console errors | Check browser devtools | No `[i18n] Missing translation key` errors |

### Edge Cases Checklist
- [ ] `codeLength` is null → shows `-`
- [ ] `codeLength` is 0 → shows `0`
- [ ] Table sorting on Code Length works (client-side, TanStack default)

---

## Validation Commands

### Static Analysis
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management && pnpm type-check
```
EXPECT: Zero type errors

### Lint
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management && pnpm lint
```
EXPECT: No new warnings

### Browser Validation
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management && pnpm dev
```
EXPECT: Navigate to `http://localhost:9003/submissions`, verify `Code Length` column is visible and populated

---

## Acceptance Criteria
- [ ] `sortBy` type uses `'createdAt'` instead of `'created_at'`
- [ ] `codeLength` column is visible in the submissions table
- [ ] All 4 i18n files updated with `codeLength` keys (including `table.columnNames`)
- [ ] Date fields have format comments
- [ ] `pnpm type-check` passes
- [ ] `pnpm lint` passes
- [ ] Browser console shows no i18n missing-key errors

## Completion Checklist
- [ ] Code follows discovered patterns (camelCase, no semicolons, single quotes)
- [ ] Column styling matches existing terminal-table aesthetic (`font-data`, `tabular-nums`)
- [ ] i18n keys added to both EN and ZH locales
- [ ] `table.columnNames` keys added in both camelCase and snake_case
- [ ] No backend files modified
- [ ] No unnecessary scope additions

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| i18n key missing in one locale | Low | Medium (console error) | Checklist validates all 4 files |
| Column ordering breaks visual rhythm | Low | Low | Insert between Memory and Submitted At |
| Build fails due to type import issue | Very Low | Low | Run `pnpm type-check` before commit |

## Notes
- This is a **frontend-only** change. The backend `AdminSubmissionVO` already returns `codeLength` and expects `sortBy: 'createdAt'`.
- The `useDataTable` composable currently does not wire sorting state to API params, so the `sortBy` fix is preparatory (enables future server-side sorting without breakage).
- `testDetails`, `memoryDistBinsMb`, `runtimeDistBinsMs` remain `unknown` because they are unused in the current UI. A future feature that renders test case details would need to define their shapes.
