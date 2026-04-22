---
phase: "47"
plan: "01"
subsystem: "frontend-i18n"
tags:
  - "vue-i18n"
  - "i18n"
  - "frontend"
  - "management"
  - "internationalization"
requires:
  - "I18N-01"
  - "I18N-02"
  - "I18N-03"
provides:
  - "vue-i18n 11.3.2 in management"
  - "robust locale storage with fallback chain"
  - "lazy-loaded locale messages"
affects:
  - "management/package.json"
  - "management/src/i18n/"
tech-stack:
  added:
    - "vue-i18n@11.3.2"
  patterns:
    - "lazy loading via dynamic import()"
    - "localStorage → sessionStorage → memory fallback"
    - "SSR-safe storage access"
key-files:
  created:
    - "management/src/i18n/utils/storage.ts"
  modified:
    - "management/package.json"
    - "management/pnpm-lock.yaml"
    - "management/src/i18n/index.ts"
key-decisions:
  - "Adopted Console's storage.ts pattern for Management locale persistence"
  - "Storage key ulticode-locale per D-03"
  - "missingWarn: import.meta.env.DEV (true in dev, false in prod) per D-11"
  - "Non-active locale loaded via dynamic import() per D-06"
requirements-completed:
  - "I18N-01"
  - "I18N-02"
  - "I18N-03"
duration: "4 min"
completed: "2026-04-22T15:09:43Z"
---

# Phase 47 Plan 01: Frontend i18n - Management Upgrade Summary

**Wave 1 complete.** Upgraded Management vue-i18n from 10.0.8 to 11.3.2, adopted robust storage fallback pattern, and refactored i18n setup for lazy loading.

## Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1 | `f6e2300b6` | `management/package.json`, `management/pnpm-lock.yaml` |
| Task 2 | `2c2ce7120` | `management/src/i18n/utils/storage.ts` |
| Task 3 | `effd8f611` | `management/src/i18n/index.ts` |

## One-liner

Management vue-i18n upgraded to 11.3.2 with robust localStorage fallback chain and lazy-loaded non-active locale via dynamic import().

## Deviations from Plan

None - plan executed exactly as written.

## Task Details

### Task 1: Upgrade vue-i18n in Management to 11.3.2
- **Status:** DONE
- **Commit:** `f6e2300b6`
- Updated `management/package.json` `vue-i18n` from `^10.0.8` to `^11.3.2`
- Verified with `pnpm install` and lockfile inspection confirming `vue-i18n@11.3.2`

### Task 2: Create Management storage.ts with robust localStorage fallback
- **Status:** DONE
- **Commit:** `2c2ce7120`
- Created `management/src/i18n/utils/storage.ts` (315 lines)
- Storage chain: `localStorage → sessionStorage → in-memory Map`
- Toast notifications via `vue-sonner` for storage transitions
- SSR-safe with `typeof window !== "undefined"` guards
- Debounced notifications (5 second cooldown)

### Task 3: Refactor Management i18n/index.ts for lazy loading + missingWarn
- **Status:** DONE
- **Commit:** `effd8f611`
- `missingWarn: import.meta.env.DEV` (true in development, false in production)
- `fallbackWarn: false` to suppress fallback locale warnings
- `silentTranslationWarn: true` kept
- `zh-CN` eagerly loaded as default; `en-US` lazily loaded via dynamic `import()`
- Exported `loadLocale()` function for composable use

## Verification Results

| Check | Result |
|-------|--------|
| `pnpm install` with vue-i18n 11.3.2 | PASS |
| `management/src/i18n/utils/storage.ts` exists | PASS (315 lines) |
| `management/src/i18n/index.ts` contains dynamic import() | PASS (4 occurrences) |
| `pnpm type-check` (i18n/index.ts only) | PASS (0 errors) |

## Known Issues

**Pre-existing type error (out of scope):** `management/src/i18n/utils.ts` line 131 has a type error (`Type instantiation is excessively deep`) related to `i18n.global.te(key)`. This is a pre-existing issue unrelated to the plan's scope.

## Wave Readiness

Wave 1 complete. Next: Wave 2 (Console language switcher UI, unified useLocale composable for Management).
