# Phase 47: Frontend i18n - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Consistent internationalization across Console and Management frontends with unified useLocale composable, lazy-loaded translation files, and Console header language switcher.

**This phase delivers:**
- Management vue-i18n upgrade (10.0.8 → 11.3.2) matching Console
- Unified useLocale composable with localStorage persistence
- Lazy loading of non-active locale files (dynamic import)
- Console header language switcher (zh-CN / en-US)
- missingWarn enabled in development mode

</domain>

<decisions>
## Implementation Decisions

### vue-i18n Upgrade (I18N-01)
- **D-01:** Upgrade Management vue-i18n from 10.0.8 to 11.3.2 to match Console

### useLocale Composable (I18N-02)
- **D-02:** Management adopts Console's `storage.ts` pattern — robust localStorage with graceful fallback to sessionStorage and in-memory storage (already proven in Console, no need to reinvent)
- **D-03:** Storage key: `ulticode-locale` (already established in Console's storage.ts)
- **D-04:** Backend sync — locale preference stored in user profile via backend API call when user changes language (exact endpoint TBD by researcher/planner)
- **D-05:** `document.documentElement.lang` updated on locale change for accessibility

### Lazy Loading (I18N-03)
- **D-06:** Non-active locale files load via dynamic `import()` — only the active locale is eagerly loaded at startup
- **D-07:** Translation files remain as `.ts` modules (not JSON) to support the existing `import()` pattern in Management's `index.ts`

### Language Switcher UI (I18N-04)
- **D-08:** Language switcher placed in Console header — likely in the user menu area or as a standalone icon button next to user avatar
- **D-09:** Switcher toggles between zh-CN and en-US — no third option in v3.0 scope
- **D-10:** UI component: Dropdown or segmented control (planner decides based on header space)

### missingWarn (I18N-05)
- **D-11:** `missingWarn: true` in development mode only — `false` in production
- **D-12:** Both Console and Management should have missingWarn enabled when `NODE_ENV === 'development'`

### Composables Unification
- **D-13:** Management useLocale should expose the same interface as Console: `{ locale, setLocale, toggleLocale, ... }` — both frontends can share the same composable logic pattern

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §I18N-01, I18N-02, I18N-03, I18N-04, I18N-05 — Phase 47 acceptance criteria
- `.planning/ROADMAP.md` §Phase 47 — Phase goal and success criteria

### Frontend Code
- `console/package.json` — Current vue-i18n version (11.3.2) — target for Management upgrade
- `management/package.json` — Current vue-i18n version (10.0.8) — source of upgrade
- `console/src/i18n/index.ts` — Existing i18n setup with eager loading (baseline for lazy loading)
- `console/src/i18n/utils/storage.ts` — Robust localStorage with graceful fallback (reference for Management)
- `console/src/composables/useLocale.ts` — Existing useLocale composable (reference for API design)
- `management/src/composables/useLocale.ts` — Current Management useLocale (to be updated)
- `management/src/i18n/index.ts` — Current Management i18n setup

### Prior Phase Context
- `.planning/phases/46-sandbox-hardening/46-CONTEXT.md` — Most recent phase context
- `.planning/STATE.md` §Phase 46 Summary — Notes vue-i18n upgrade aligns Management with Console

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- **console/src/i18n/utils/storage.ts**: Production-ready locale storage with localStorage → sessionStorage → memory fallback and toast notifications. Management should adopt this directly instead of building a new solution.
- **console/src/composables/useLocale.ts**: Complete composable with `setLocale`, `toggleLocale`, locale config, i18n utilities. Management should align its composable API to this.
- **console/src/i18n/types.ts**: Type definitions for `SupportedLocale`, `LocaleConfig`, `MessageSchema` — can be shared or copied to Management

### Established Patterns
- **Storage key**: `ulticode-locale` — established, should not change
- **Supported locales**: `zh-CN` and `en-US` — only two in scope for v3.0
- **Eager loading**: Both frontends currently load all locale messages at startup — lazy loading requires dynamic import refactor

### Integration Points
- **Header component**: Console header (likely `Header.vue` or `AppHeader.vue`) — where language switcher will be added
- **Backend user profile API**: Need to confirm if locale preference is stored per-user in backend — researcher to verify

</codebase_context>

<specifics>
## Specific Ideas

No specific UI references yet — switcher placement in header (D-08) is the main visual decision, planner can choose appropriate component based on header layout.

</specifics>

<deferred>
## Deferred Ideas

None — all 5 requirements are well-scoped within Phase 47.

</deferred>

---

*Phase: 47-frontend-i18n*
*Context gathered: 2026-04-22*
