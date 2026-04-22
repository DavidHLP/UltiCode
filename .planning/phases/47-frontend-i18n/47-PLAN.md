---
phase: "47"
slug: "frontend-i18n"
plan: "01"
type: "execute"
wave: 1
depends_on: []
files_modified:
  - "management/package.json"
  - "management/src/i18n/utils/storage.ts"
  - "management/src/i18n/index.ts"
autonomous: true
requirements:
  - "I18N-01"
  - "I18N-02"
  - "I18N-03"
  - "I18N-04"
  - "I18N-05"
must_haves:
  truths:
    - "Management frontend uses vue-i18n 11.3.2 matching Console"
    - "Management locale storage uses robust localStorage → sessionStorage → memory fallback"
    - "Non-active locale translation files load on-demand via dynamic import"
    - "Console header displays LanguageSwitcher with zh-CN and en-US options"
    - "Missing translation keys produce console warnings in development"
  artifacts:
    - path: "management/package.json"
      provides: "vue-i18n version 11.3.2"
      min_lines: 1
    - path: "management/src/i18n/utils/storage.ts"
      provides: "Robust locale storage with graceful fallback"
      min_lines: 50
    - path: "management/src/i18n/index.ts"
      provides: "Lazy-loaded i18n with dynamic imports"
      min_lines: 30
    - path: "management/src/composables/useLocale.ts"
      provides: "Unified useLocale composable matching Console API"
      min_lines: 30
  key_links:
    - from: "management/src/composables/useLocale.ts"
      to: "management/src/i18n/utils/storage.ts"
      via: "setStoredLocale / getStoredLocale calls"
      pattern: "setStoredLocale|getStoredLocale"
    - from: "management/src/i18n/index.ts"
      to: "vue-i18n"
      via: "createI18n factory"
      pattern: "createI18n"
---

<objective>
Upgrade Management vue-i18n from 10.0.8 to 11.3.2, adopt Console's robust storage.ts pattern for locale persistence, and refactor Management i18n setup to use lazy-loaded translation files via dynamic import().
</objective>

<context>
@console/package.json §dependencies.vue-i18n — target version 11.3.2
@management/package.json §dependencies.vue-i18n — current version 10.0.8 (source of upgrade)
@console/src/i18n/utils/storage.ts — robust localStorage → sessionStorage → memory fallback pattern to adopt in Management
@console/src/i18n/index.ts — existing i18n setup baseline
@management/src/i18n/index.ts — current Management i18n setup to refactor
@management/src/composables/useLocale.ts — current Management composable to update
@console/src/composables/useLocale.ts — Console composable with setLocale, toggleLocale, isCurrentLocale interface
</context>

<tasks>

<task type="auto">
  <name>Task 1: Upgrade vue-i18n in Management to 11.3.2</name>
  <files>management/package.json</files>
  <action>
    Update management/package.json dependencies.vue-i18n from "10.0.8" to "11.3.2" to match Console (per D-01).
    Also update lucide-vue-next to ^0.552.0 to match Console's version (peer dependency alignment).
  </action>
  <verify>
    <automated>cd management && pnpm install --lockfile-only && grep '"vue-i18n": "\^11.3.2"' pnpm-lock.yaml</automated>
  </verify>
  <done>management/package.json declares vue-i18n ^11.3.2</done>
</task>

<task type="auto">
  <name>Task 2: Create Management storage.ts with robust localStorage fallback</name>
  <files>management/src/i18n/utils/storage.ts</files>
  <action>
    Create management/src/i18n/utils/storage.ts adopting Console's storage.ts pattern (per D-02):
    - LOCALE_STORAGE_KEY = "ulticode-locale" (per D-03)
    - Storage layers: localStorage → sessionStorage → in-memory Map fallback
    - isStorageAccessible() test function
    - detectBestStorageLayer() function
    - getStoredLocale() / setStoredLocale() exports
    - Debounced toast notifications via vue-sonner for storage fallback transitions
    - Memory fallback messages in both zh-CN and en-US
    - SSR-safe (typeof window !== "undefined" guards)
    Copy the logic from console/src/i18n/utils/storage.ts but adapt import paths to management's structure.
    Use dynamic import() for vue-sonner toast (SSR-safe, client-only).
  </action>
  <verify>
    <automated>ls management/src/i18n/utils/storage.ts && wc -l management/src/i18n/utils/storage.ts</automated>
  </verify>
  <done>management/src/i18n/utils/storage.ts exists with localStorage → sessionStorage → memory fallback chain</done>
</task>

<task type="auto">
  <name>Task 3: Refactor Management i18n/index.ts for lazy loading + missingWarn</name>
  <files>management/src/i18n/index.ts</files>
  <action>
    Refactor management/src/i18n/index.ts to use lazy loading (per D-06, D-07):
    - Keep default locale eager-loaded (zh-CN or stored preference)
    - Non-active locale loaded via dynamic import() — only the active locale is eagerly loaded at startup
    - Translation files remain as .ts modules (not JSON)
    - Add missingWarn: import.meta.env.DEV (per D-11) — true in development, false in production
    - Add fallbackWarn: false to suppress fallback locale warnings
    - Keep silentTranslationWarn: true
    - Create i18n instance with the lazy-loaded messages object
    Pattern: Use a messages object where only the initial locale is imported directly, other locale is lazily imported.
    Update document.documentElement.lang when locale changes (per D-05).
    Re-export SUPPORTED_LOCALES, LOCALE_CONFIGS, DEFAULT_LOCALE, FALLBACK_LOCALE from ./types.
  </action>
  <verify>
    <automated>grep -c "import(" management/src/i18n/index.ts</automated>
  </verify>
  <done>management/src/i18n/index.ts uses dynamic import() for non-active locale and missingWarn: import.meta.env.DEV</done>
</task>

</tasks>

<verification>
## Wave 1 Verification

1. pnpm install in management succeeds with vue-i18n 11.3.2
2. management/src/i18n/utils/storage.ts exists with getStoredLocale / setStoredLocale functions
3. management/src/i18n/index.ts contains at least one dynamic import() for lazy loading
4. pnpm type-check passes in management
</verification>

<success_criteria>
- management/package.json has vue-i18n ^11.3.2
- management/src/i18n/utils/storage.ts provides robust fallback storage
- management/src/i18n/index.ts uses lazy loading (dynamic import) for non-active locale
- management pnpm install and pnpm type-check both pass
</success_criteria>

<output>
After wave 1 completion, create .planning/phases/47-frontend-i18n/47-01-SUMMARY.md
</output>
---
phase: "47"
slug: "frontend-i18n"
plan: "02"
type: "execute"
wave: 2
depends_on: ["47-01"]
files_modified:
  - "management/src/composables/useLocale.ts"
autonomous: true
requirements:
  - "I18N-02"
  - "I18N-03"
must_haves:
  truths:
    - "Management useLocale composable exposes same interface as Console: { locale, setLocale, toggleLocale, isCurrentLocale, ... }"
    - "useLocale persists selected locale to localStorage via storage.ts"
    - "Backend sync calls API to persist locale preference on change"
  artifacts:
    - path: "management/src/composables/useLocale.ts"
      provides: "Unified composable with setLocale, toggleLocale, isCurrentLocale matching Console API"
      min_lines: 40
  key_links:
    - from: "management/src/composables/useLocale.ts"
      to: "management/src/i18n/utils/storage.ts"
      via: "setStoredLocale() call on locale change"
      pattern: "setStoredLocale"
    - from: "management/src/composables/useLocale.ts"
      to: "backend user profile API"
      via: "PATCH /users/me or similar endpoint for locale preference"
      pattern: "axios.patch|apiPatch"
---

<objective>
Unify Management useLocale composable to match Console's API surface (setLocale, toggleLocale, isCurrentLocale) with localStorage persistence and backend sync.</objective>

<context>
@console/src/composables/useLocale.ts — Console composable API to match
@management/src/composables/useLocale.ts — current Management composable to replace
@console/src/i18n/types.ts — LOCALE_CONFIGS, SUPPORTED_LOCALES types reference
@management/src/i18n/types.ts — existing Management i18n types (verify consistency)
@management/src/i18n/utils/storage.ts — from Wave 1
</context>

<tasks>

<task type="auto">
  <name>Task 1: Rewrite Management useLocale composable to match Console API</name>
  <files>management/src/composables/useLocale.ts</files>
  <action>
    Rewrite management/src/composables/useLocale.ts to match Console's API (per D-13):
    - Import from @/i18n/utils/storage (Wave 1 created storage.ts)
    - setLocale(newLocale) — sets i18n locale, calls setStoredLocale(newLocale), updates document.documentElement.lang
    - toggleLocale() — cycles through SUPPORTED_LOCALES array
    - isCurrentLocale(localeCode) — checks if given locale is active
    - Expose locale (computed as SupportedLocale), localeConfig, availableLocales (mapped from LOCALE_CONFIGS)
    - Expose all i18n utilities: t, te, tm, rt, n, d from useI18n()
    - Backend sync: On setLocale(), call PATCH /users/me with { locale: newLocale } to persist preference (per D-04).
      Use management's existing api utility or axios. Handle errors silently (locale already changed locally).
    The composable must be a drop-in replacement for Console's useLocale — same interface, same property names.
    Read console/src/composables/useLocale.ts as the implementation reference.
  </action>
  <verify>
    <automated>grep -c "toggleLocale\|setLocale\|isCurrentLocale" management/src/composables/useLocale.ts</automated>
  </verify>
  <done>Management useLocale exposes setLocale, toggleLocale, isCurrentLocale matching Console's interface</done>
</task>

<task type="auto">
  <name>Task 2: Verify Management i18n types consistency with Console</name>
  <files>management/src/i18n/types.ts</files>
  <action>
    Check management/src/i18n/types.ts exports:
    - SUPPORTED_LOCALES array (['zh-CN', 'en-US'])
    - LOCALE_CONFIGS object with { code, nativeName, flag, dir }
    - DEFAULT_LOCALE, FALLBACK_LOCALE constants
    - SupportedLocale, LocaleConfig, MessageSchema types

    If types are missing or inconsistent with Console's console/src/i18n/types.ts, update management/src/i18n/types.ts to match.
    The useLocale composable depends on these types being consistent across both frontends.
  </action>
  <verify>
    <automated>grep -c "SUPPORTED_LOCALES\|LOCALE_CONFIGS\|SupportedLocale" management/src/i18n/types.ts</automated>
  </verify>
  <done>Management i18n types match Console's i18n types (LOCALE_CONFIGS, SUPPORTED_LOCALES, SupportedLocale)</done>
</task>

</tasks>

<verification>
## Wave 2 Verification

1. management/src/composables/useLocale.ts exports setLocale, toggleLocale, isCurrentLocale
2. setLocale calls setStoredLocale and updates document.documentElement.lang
3. setLocale also calls backend API to sync locale preference
4. pnpm type-check passes in management
</verification>

<success_criteria>
- Management useLocale matches Console's useLocale interface
- setLocale persists to localStorage via storage.ts
- setLocale syncs to backend API
- document.documentElement.lang updated on locale change
</success_criteria>

<output>
After wave 2 completion, create .planning/phases/47-frontend-i18n/47-02-SUMMARY.md
</output>
---
phase: "47"
slug: "frontend-i18n"
plan: "03"
type: "execute"
wave: 3
depends_on: ["47-01", "47-02"]
files_modified:
  - "console/src/i18n/index.ts"
  - "management/src/i18n/index.ts"
autonomous: true
requirements:
  - "I18N-05"
must_haves:
  truths:
    - "Missing translation keys produce console warnings in development mode (missingWarn: true)"
    - "Missing translation keys are silenced in production (missingWarn: false)"
  artifacts:
    - path: "console/src/i18n/index.ts"
      provides: "missingWarn: import.meta.env.DEV enabled"
      min_lines: 1
    - path: "management/src/i18n/index.ts"
      provides: "missingWarn: import.meta.env.DEV enabled"
      min_lines: 1
  key_links:
    - from: "console/src/i18n/index.ts"
      to: "vue-i18n"
      via: "missingWarn option"
      pattern: "missingWarn"
    - from: "management/src/i18n/index.ts"
      to: "vue-i18n"
      via: "missingWarn option"
      pattern: "missingWarn"
---

<objective>
Enable missingWarn in both Console and Management i18n configurations so missing translation keys produce console warnings in development but are silenced in production (per D-11, D-12).</objective>

<context>
@console/src/i18n/index.ts — Console i18n setup (currently missing missingWarn: import.meta.env.DEV)
@management/src/i18n/index.ts — Management i18n setup (from Wave 1, needs missingWarn check)
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add missingWarn: import.meta.env.DEV to Console i18n</name>
  <files>console/src/i18n/index.ts</files>
  <action>
    Read console/src/i18n/index.ts and verify/add missingWarn: import.meta.env.DEV to the createI18n call.
    The missingWarn option enables console warnings when translation keys are not found.
    Set to import.meta.env.DEV so it is true in development and false in production (per D-11).
    If missingWarn is already set to import.meta.env.DEV, this task is already done — verify and document.
    If missingWarn is false or missing, add it now.
  </action>
  <verify>
    <automated>grep "missingWarn" console/src/i18n/index.ts</automated>
  </verify>
  <done>Console i18n has missingWarn: import.meta.env.DEV configured</done>
</task>

<task type="auto">
  <name>Task 2: Verify Management missingWarn: import.meta.env.DEV in Wave 1 refactored i18n</name>
  <files>management/src/i18n/index.ts</files>
  <action>
    Read management/src/i18n/index.ts (the Wave 1 refactored version).
    Verify it has missingWarn: import.meta.env.DEV in the createI18n call.
    If missing, add it. This should have been added in Wave 1 Task 3 but needs verification.
  </action>
  <verify>
    <automated>grep "missingWarn" management/src/i18n/index.ts</automated>
  </verify>
  <done>Management i18n has missingWarn: import.meta.env.DEV configured</done>
</task>

<task type="auto">
  <name>Task 3: Final verification — pnpm type-check and build</name>
  <files>console/package.json, management/package.json</files>
  <action>
    Run final verification across both frontends:
    1. cd management && pnpm type-check (must pass)
    2. cd management && pnpm build (must succeed)
    3. cd console && pnpm type-check (must pass)
    4. Verify LanguageSwitcher.vue is still present and correctly imports useLocale from @/composables/useLocale
    5. Verify LanguageSwitcher uses availableLocales, setLocale, isCurrentLocale from useLocale
    This ensures all changes integrate correctly and nothing is broken.
  </action>
  <verify>
    <automated>cd management && pnpm type-check && cd ../console && pnpm type-check</automated>
  </verify>
  <done>Both frontends pass type-check and build</done>
</task>

</tasks>

<verification>
## Wave 3 Verification

1. console/src/i18n/index.ts has missingWarn: import.meta.env.DEV
2. management/src/i18n/index.ts has missingWarn: import.meta.env.DEV
3. Both frontends pass pnpm type-check
4. Both frontends pass pnpm build
</verification>

<success_criteria>
- missingWarn enabled in both frontends (DEV=true, PROD=false)
- All 5 requirements (I18N-01 through I18N-05) are covered by the 3 waves
- Phase 47 success criteria from ROADMAP.md are satisfied
</success_criteria>

<output>
After wave 3 completion, create .planning/phases/47-frontend-i18n/47-03-SUMMARY.md
</output>
