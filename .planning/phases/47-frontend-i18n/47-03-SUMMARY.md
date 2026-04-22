---
phase: "47"
plan: "03"
subsystem: "frontend-i18n"
tags:
  - "vue-i18n"
  - "i18n"
  - "frontend"
  - "console"
  - "internationalization"
requires:
  - "I18N-05"
provides:
  - "missingWarn enabled in Console and Management"
affects:
  - "console/src/i18n/index.ts"
tech-stack:
  added:
    - "missingWarn: import.meta.env.DEV"
  patterns:
    - "missingWarn true in development, false in production"
key-files:
  modified:
    - "console/src/i18n/index.ts"
key-decisions:
  - "missingWarn: import.meta.env.DEV per D-11 (true in dev, false in prod)"
requirements-completed:
  - "I18N-05"
duration: "1 min"
completed: "2026-04-22T15:12:00Z"
---

# Phase 47 Plan 03: Frontend i18n - missingWarn Summary

**Wave 3 complete.** Added missingWarn: import.meta.env.DEV to Console i18n, verified Management already had it.

## Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1 | `b12dabb2b` | `console/src/i18n/index.ts` |

## One-liner

missingWarn: import.meta.env.DEV added to Console i18n (Management already had it from Wave 1).

## Deviations from Plan

None - plan executed exactly as written.

## Task Details

### Task 1: Add missingWarn: import.meta.env.DEV to Console i18n
- **Status:** DONE
- **Commit:** `b12dabb2b`
- Added `missingWarn: import.meta.env.DEV` to `console/src/i18n/index.ts`
- Console type-check passes

### Task 2: Verify Management missingWarn: import.meta.env.DEV
- **Status:** DONE
- Already present in `management/src/i18n/index.ts` line 75 (added in Wave 1)

### Task 3: Final verification
- **Status:** DONE
- Console type-check: PASS
- Management build: PASS (vite built in 1.58s)
- Pre-existing type error in `management/src/i18n/utils.ts:131` (TypeScript TS2589) documented in 47-02-SUMMARY.md - unrelated to this plan

## Verification Results

| Check | Result |
|-------|--------|
| `missingWarn` in Console i18n | PASS (line 25) |
| `missingWarn` in Management i18n | PASS (line 75) |
| Console `pnpm type-check` | PASS |
| Management `pnpm build` | PASS |

## Requirements Coverage

All 5 requirements now complete:
- I18N-01 (vue-i18n upgrade): Phase 47-01
- I18N-02 (useLocale unification): Phase 47-02
- I18N-03 (lazy loading): Phase 47-01/47-02
- I18N-04 (LanguageSwitcher): Phase 47-02
- I18N-05 (missingWarn): Phase 47-03 (this plan)

## Self-Check: PASSED
