---
phase: "47"
plan: "02"
subsystem: "frontend-i18n"
tags:
  - "vue-i18n"
  - "i18n"
  - "frontend"
  - "management"
  - "internationalization"
requires:
  - "I18N-02"
  - "I18N-03"
provides:
  - "unified useLocale composable in management"
affects:
  - "management/src/composables/useLocale.ts"
tech-stack:
  added:
    - "apiPatch for backend locale sync"
  patterns:
    - "useLocale composable matching Console API exactly"
    - "localStorage persistence via setStoredLocale()"
    - "backend sync on locale change"
key-files:
  modified:
    - "management/src/composables/useLocale.ts"
key-decisions:
  - "setLocale calls setStoredLocale() for robust localStorage with fallback chain"
  - "Backend sync via PATCH /users/me with { locale } - fire and forget, silent on error"
  - "API surface matches Console useLocale: setLocale, toggleLocale, isCurrentLocale, locale, localeConfig, availableLocales, t, te, tm, rt, n, d"
requirements-completed:
  - "I18N-02"
  - "I18N-03"
duration: "1 min"
completed: "2026-04-22T15:11:00Z"
---

# Phase 47 Plan 02: Frontend i18n - Management useLocale Unification Summary

**Wave 2 complete.** Management useLocale composable now matches Console's API surface exactly.

## Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1 | `aff2fd56c` | `management/src/composables/useLocale.ts` |
| Task 2 | N/A (no changes needed) | N/A - types already consistent |

## One-liner

Management useLocale composable unified with Console - same setLocale, toggleLocale, isCurrentLocale API, localStorage persistence via robust storage.ts fallback chain, and backend sync via PATCH /users/me.

## Deviations from Plan

None - plan executed exactly as written.

## Task Details

### Task 1: Rewrite Management useLocale composable to match Console API
- **Status:** DONE
- **Commit:** `aff2fd56c`
- Rewrote `management/src/composables/useLocale.ts` (69 lines added, 57 removed)
- API now matches Console exactly:
  - `setLocale(newLocale)` - sets i18n locale, calls `setStoredLocale()`, updates `document.documentElement.lang`, syncs to backend via `apiPatch("/users/me", { locale })`
  - `toggleLocale()` - cycles through SUPPORTED_LOCALES array
  - `isCurrentLocale(localeCode)` - checks if given locale is active
  - `locale` - computed SupportedLocale
  - `localeConfig` - computed from LOCALE_CONFIGS
  - `availableLocales` - mapped from LOCALE_CONFIGS
  - All i18n utilities: `t`, `te`, `tm`, `rt`, `n`, `d`
- Backend sync: `apiPatch("/users/me", { locale })` with silent error handling

### Task 2: Verify Management i18n types consistency with Console
- **Status:** DONE (no changes needed)
- Management `types.ts` already exports `SUPPORTED_LOCALES`, `LOCALE_CONFIGS`, `SupportedLocale` matching Console's structure
- `LocaleConfig` interface has `name`, `nativeName`, `flag`, `dir` - already consistent with Console

## Verification Results

| Check | Result |
|-------|--------|
| `setLocale`, `toggleLocale`, `isCurrentLocale` in useLocale.ts | PASS (7 occurrences) |
| `setStoredLocale()` import and call | PASS |
| `document.documentElement.lang` update | PASS |
| Backend sync via `apiPatch` | PASS |
| Management types.ts exports consistency | PASS (6 pattern matches) |
| `pnpm type-check` (pre-existing utils.ts:131 error) | FAIL (pre-existing, unrelated to this plan) |

## Known Issues

**Pre-existing type error (out of scope):** `management/src/i18n/utils.ts:131` has `Type instantiation is excessively deep` error. This was already present before this plan and is unrelated to the useLocale changes.

## Wave Readiness

Wave 2 complete. Both I18N-02 (unified useLocale) and I18N-03 (lazy loading, storage persistence) requirements are satisfied. Next: Wave 3 (Console language switcher UI).
