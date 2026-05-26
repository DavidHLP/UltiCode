# Plan: Audit Module Frontend-Backend Granularity Alignment

## Summary
Fix 5 granularity misalignment issues in the audit module: (1) AuditLogsView.fetchStats omits 4 filter params, causing stats ticker to show unfiltered data; (2) AuditReportView lacks 4 filter fields; (3) AuditReportView doesn't render actionsByType; (4) Export filename inconsistency; (5) Frontend constants mirror backend without compile-time validation (long-term, out of scope for this plan).

## User Story
As an admin, I want the audit stats ticker and report page to reflect the same filters I apply to the logs table, so that I see consistent, filtered statistics — not misleading global totals.

## Problem → Solution
**Current state**: Stats ticker in AuditLogsView always shows global counts regardless of active filters; AuditReportView only has 3 of 7 supported filters and doesn't visualize actionsByType; export filename uses `audit-log` (singular).
**Desired state**: All stats calls pass the same filter params as logs calls; AuditReportView has all 7 filter fields and renders actionsByType as a card; export filename is consistent (`audit-logs`).

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A (from alignment report)
- **PRD Phase**: N/A
- **Estimated Files**: 8

---

## UX Design

### Before
```
AuditLogsView:
┌─ Stats Ticker ──────────────────────────────┐
│ Create: 120  Update: 340  Delete: 50  Other:90 │  ← ALWAYS global, ignores filters
├─ Filters ────────────────────────────────────┤
│ [Date] [Performer] [User] [Entity] [Action] [Search] │
├─ Data Table ─────────────────────────────────┤
│ (filtered rows)                              │
└──────────────────────────────────────────────┘

AuditReportView:
┌─ Filters ─────────────────┐
│ [Date] [Performer]        │  ← only 3 fields
├─ Cards ───────────────────┤
│ totalActions | actionsByEntity | topPerformers │
│                            │  ← missing actionsByType
└────────────────────────────┘
```

### After
```
AuditLogsView:
┌─ Stats Ticker ──────────────────────────────┐
│ Create: 12  Update: 34  Delete: 5  Other:9   │  ← reflects active filters
├─ Filters ────────────────────────────────────┤
│ [Date] [Performer] [User] [Entity] [Action] [Search] │
├─ Data Table ─────────────────────────────────┤
│ (filtered rows)                              │
└──────────────────────────────────────────────┘

AuditReportView:
┌─ Filters ─────────────────────────────────────┐
│ [Date] [Performer] [User] [Entity] [Action] [Search] │  ← all 7 fields
├─ Cards ───────────────────────────────────────┤
│ totalActions | actionsByEntity | actionsByType | topPerformers │  ← NEW card
└───────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| AuditLogsView stats ticker | Shows global stats | Shows filtered stats | Pass all params to fetchStats |
| AuditReportView filters | 3 fields | 7 fields | Add userId, entityType, action, search |
| AuditReportView cards | 3 cards | 4 cards | Add actionsByType card |
| Export filename | `audit-log` | `audit-logs` | Consistent plural |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `management/src/views/audit/AuditLogsView.vue` | 90-120 | fetchStats call site — core bug |
| P0 | `management/src/views/audit/AuditReportView.vue` | all | Report page to enhance |
| P1 | `management/src/stores/admin/audit.ts` | all | Store actions — fetchStats signature |
| P1 | `management/src/utils/audit/utils.ts` | all | Constants and helpers for filters |
| P2 | `management/src/i18n/locales/en-US/modules/audit-report.ts` | all | Need new i18n keys |
| P2 | `management/src/api/admin/audit.ts` | 125-140 | Export filename |

## External Documentation

No external research needed — feature uses established internal patterns.

---

## Patterns to Mirror

### FILTER_SELECT_PATTERN
// SOURCE: management/src/views/audit/AuditLogsView.vue:50-75
// Entity type and action selects use <Select> with AUDIT_ENTITY_TYPES / getActionsForEntityType
```vue
<Select v-model="entityTypeFilter" :options="entityTypeOptions" />
<Select v-model="actionFilter" :options="actionOptions" />
```
The entityType → action cascade pattern (selecting an entity type filters available actions) is already implemented in AuditLogsView and must be replicated in AuditReportView.

### STATS_CARD_PATTERN
// SOURCE: management/src/views/audit/AuditReportView.vue template
// Stats cards use a Card wrapper with header and content, rendering lists with v-for
```vue
<Card>
  <CardHeader>{{ t('auditReport.someTitle') }}</CardHeader>
  <CardContent>
    <div v-for="item in stats.someField" :key="item.key">
      {{ item.key }}: {{ item.count }}
    </div>
  </CardContent>
</Card>
```

### I18N_KEY_PATTERN
// SOURCE: management/src/i18n/locales/en-US/modules/audit-report.ts
// Keys are flat under `auditReport.*` namespace
```ts
actionsByEntity: 'Actions by Entity',
topPerformers: 'Top Performers',
```
New actionsByType keys follow same flat pattern.

### STORE_ACTION_PATTERN
// SOURCE: management/src/stores/admin/audit.ts
```ts
async fetchStats(params: AuditLogQueryParams) {
  const response = await auditApi.getAuditStats(params)
  this.stats = response
}
```
No store changes needed — fetchStats already accepts full AuditLogQueryParams.

### DATE_NORMALIZATION
// SOURCE: management/src/views/audit/AuditLogsView.vue
```ts
import { normalizeDateParams } from '@/utils/audit/utils'
// normalizeDateParams adds T00:00:00, advances endDate to next day as exclusive upper bound
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `management/src/views/audit/AuditLogsView.vue` | UPDATE | Pass full params to fetchStats (fix #2) |
| `management/src/views/audit/AuditReportView.vue` | UPDATE | Add 4 filter fields + actionsByType card (fix #3, #4) |
| `management/src/i18n/locales/en-US/modules/audit-report.ts` | UPDATE | Add actionsByType i18n keys + new filter labels |
| `management/src/i18n/locales/zh-CN/modules/audit-report.ts` | UPDATE | Add actionsByType i18n keys + new filter labels |
| `management/src/api/admin/audit.ts` | UPDATE | Fix export filename `audit-log` → `audit-logs` (fix #1) |

## NOT Building

- Backend `/admin/audit/meta` endpoint (problem #5 — long-term architectural change)
- i18n coverage tests for action/entityType constants (problem #5 — short-term mitigation)
- Any backend changes (all 5 issues are frontend-only fixes)

---

## Step-by-Step Tasks

### Task 1: Fix AuditLogsView.fetchStats to pass all filter params
- **ACTION**: In AuditLogsView.vue, change the fetchStats call to pass the complete `params` object instead of a subset
- **IMPLEMENT**: Replace the destructured params object with the full `params` variable from loadLogs()
- **MIRROR**: The fetchLogs call already passes the full params — just use the same object
- **GOTCHA**: The `params` object already has `normalizeDateParams` applied; do NOT double-normalize
- **VALIDATE**: Set entityType filter, verify stats ticker updates to match

Current code (line ~105):
```ts
await auditStore.fetchStats({
  startDate: params.startDate,
  endDate: params.endDate,
  performerId: params.performerId,
})
```

Target code:
```ts
await auditStore.fetchStats(params)
```

The `params` object already contains all 7+ fields (startDate, endDate, performerId, userId, entityType, action, search, page, limit). The store's `fetchStats` already accepts `AuditLogQueryParams` which matches. No store changes needed.

### Task 2: Fix export filename inconsistency
- **ACTION**: In `management/src/api/admin/audit.ts`, verify and fix the export filename
- **IMPLEMENT**: Ensure the filename uses `audit-logs` (plural) to match the backend Content-Disposition header
- **GOTCHA**: The report says line 131 — verify current code and only change if it uses singular form
- **VALIDATE**: Trigger export, check downloaded filename

### Task 3: Add missing filter fields to AuditReportView
- **ACTION**: Add userId, entityType, action, and search filter inputs to AuditReportView
- **IMPLEMENT**:
  1. Add reactive refs: `userIdFilter`, `entityTypeFilter`, `actionFilter`, `searchFilter`
  2. Add the entity type / action cascade (same as AuditLogsView):
     - Import `AUDIT_ENTITY_TYPES`, `getActionsForEntityType`, `actionToI18nKey`, `entityTypeToI18nKey` from utils
     - Compute `entityTypeOptions` and `actionOptions` the same way as AuditLogsView
  3. Add filter UI elements in the filters section:
     - User ID: `<Input>` with placeholder
     - Entity Type: `<Select>` with `entityTypeOptions`
     - Action: `<Select>` with `actionOptions` (cascading from entity type)
     - Search: `<Input>` with placeholder
  4. Update `loadStats()` to include the new params:
     ```ts
     const params = normalizeDateParams({
       startDate: startDate.value || undefined,
       endDate: endDate.value || undefined,
       performerId: performerFilter.value || undefined,
       userId: userIdFilter.value || undefined,
       entityType: entityTypeFilter.value || undefined,
       action: actionFilter.value || undefined,
       search: searchFilter.value || undefined,
     })
     ```
- **MIRROR**: Copy the Select + options pattern from AuditLogsView lines 50-75
- **GOTCHA**: The entityType → action cascade: when entityType changes, reset actionFilter to undefined. This is already handled in AuditLogsView via a `watch` on `entityTypeFilter`.
- **VALIDATE**: Set entityType filter to "PROBLEM", verify stats update; set action filter, verify further narrowing

### Task 4: Add actionsByType visualization to AuditReportView
- **ACTION**: Add a fourth stats card showing actionsByType distribution
- **IMPLEMENT**:
  1. In the template, add a new Card after the actionsByEntity card:
     ```vue
     <Card>
       <CardHeader>
         <CardTitle>{{ t('auditReport.actionsByType') }}</CardTitle>
       </CardHeader>
       <CardContent>
         <div v-for="item in stats?.actionsByType" :key="item.actionType" class="flex items-center justify-between py-2">
           <span>{{ t(`audit.actionTypeGroups.${item.actionType}`) }}</span>
           <Badge variant="secondary">{{ item.count }}</Badge>
         </div>
         <div v-if="!stats?.actionsByType?.length" class="text-muted-foreground text-sm">
           {{ t('auditReport.noData') }}
         </div>
       </CardContent>
     </Card>
     ```
  2. The `actionsByType` data uses semantic types (CREATE, UPDATE, DELETE, etc.) matching the `audit.actionTypeGroups` i18n keys — these already have translations for all 16 types
- **MIRROR**: Follow the exact Card pattern from the existing actionsByEntity card in AuditReportView
- **GOTCHA**: `actionsByType[i].actionType` contains semantic types like "CREATE", "UPDATE" etc., NOT raw action names like "CREATE_PROBLEM". The i18n key path is `audit.actionTypeGroups.${actionType}`, NOT `audit.actionTypes.${actionType}`. This matches the backend SQL CASE mapping.
- **VALIDATE**: Load report page, verify actionsByType card renders with translated type names and counts

### Task 5: Add i18n keys for new AuditReportView features
- **ACTION**: Add missing i18n keys for actionsByType card title and new filter labels
- **IMPLEMENT**: Add to both en-US and zh-CN audit-report.ts:
  ```ts
  // en-US additions:
  actionsByType: 'Actions by Type',

  // zh-CN additions:
  actionsByType: '按操作类型',
  ```
  Note: The filter labels (User, Entity Type, Action, Search) can reuse existing keys from `audit.filters.*` namespace since AuditReportView already imports `useI18n`. Verify that the AuditReportView component has access to the `audit` i18n namespace — if not, use `auditReport.filters.*` keys.
- **MIRROR**: Follow flat key structure in audit-report.ts
- **GOTCHA**: The `audit.actionTypeGroups.*` keys already exist in `audit.ts` for both locales (16 keys each). No need to duplicate them — just reference from the template.
- **VALIDATE**: Switch language, verify all labels appear correctly

---

## Testing Strategy

### Manual Testing (primary — frontend UI)

| Test | Steps | Expected |
|---|---|---|
| Stats ticker filter sync | Set entityType=PROBLEM in AuditLogsView | Ticker shows counts filtered to PROBLEM entity only |
| Report filters | Set userId + entityType in AuditReportView | Stats cards reflect combined filter |
| actionsByType card | Load AuditReportView with no filters | Card shows all 16 action type categories with counts |
| Export filename | Click export | Downloaded file named `audit-logs.xlsx` or `audit-logs.csv` |
| Language switch | Toggle en-US/zh-CN | All new labels translate correctly |

### Edge Cases Checklist
- [x] Empty actionsByType array → show "no data" message
- [x] Selecting entityType resets action filter (cascade)
- [x] Clearing all filters returns to global stats
- [x] Date range + entityType combination works
- [x] actionsByType with 0 count for some types (backend only returns non-zero)

---

## Validation Commands

### Static Analysis
```bash
cd management && pnpm type-check
```
EXPECT: Zero type errors

### Lint
```bash
cd management && pnpm lint
```
EXPECT: No new lint errors

### Format
```bash
cd management && pnpm format
```
EXPECT: Consistent formatting

### Unit Tests
```bash
cd management && pnpm test
```
EXPECT: All tests pass

### Browser Validation
```bash
pm2 restart ulticode-9003
```
Then navigate to:
1. `/admin/audit` — set filters, verify stats ticker matches
2. `/admin/audit/report` — verify 4 filter fields + 4 stats cards
3. Export — verify filename

---

## Acceptance Criteria
- [ ] AuditLogsView stats ticker reflects active filter params (not global)
- [ ] AuditReportView has 7 filter fields (startDate, endDate, performerId, userId, entityType, action, search)
- [ ] AuditReportView renders actionsByType card with translated type names
- [ ] Export filename uses `audit-logs` (plural)
- [ ] All i18n keys present in both en-US and zh-CN
- [ ] Type-check passes
- [ ] Lint passes
- [ ] No regressions in existing audit functionality

## Completion Checklist
- [ ] Code follows discovered patterns (Select cascade, Card layout, i18n namespace)
- [ ] Error handling matches codebase style (no new error paths needed)
- [ ] No hardcoded values
- [ ] No unnecessary scope additions

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| entityType→action cascade complexity in AuditReportView | Low | Low | Copy exact pattern from AuditLogsView |
| actionsByType i18n key path mismatch | Low | Medium | Verified: use `audit.actionTypeGroups.*` (16 keys, both locales) |
| Stats API performance with all 7 params | Low | Low | Backend already supports all params — no new load |

## Notes
- Problem #5 (frontend-backend constant mirroring) is intentionally excluded — it's a long-term architectural issue requiring a backend meta endpoint
- The store's `fetchStats` already accepts `AuditLogQueryParams` with all fields — no store changes needed for any task
- The `normalizeDateParams` utility must be applied once to the params object before passing to fetchStats
