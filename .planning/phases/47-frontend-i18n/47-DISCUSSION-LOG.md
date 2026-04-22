# Phase 47: Frontend i18n - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 47-frontend-i18n
**Areas discussed:** All

---

## vue-i18n Upgrade (I18N-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Upgrade to 11.3.2 | Match Console version | ✓ |

**User's choice:** Upgrade Management from 10.0.8 to 11.3.2
**Notes:** Console already on 11.3.2 — alignment is the goal

---

## useLocale Composable (I18N-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Adopt Console's storage.ts | Proven pattern with graceful fallback | ✓ |
| Build new solution | Management builds its own | |

**User's choice:** Adopt Console's `storage.ts` pattern — robust localStorage with sessionStorage and in-memory fallback
**Notes:** No need to reinvent; Console's storage is production-ready

---

## Lazy Loading (I18N-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Dynamic import() | Non-active locale loaded on-demand | ✓ |
| JSON + import() | Change from .ts modules to JSON files | |

**User's choice:** Dynamic import() — keep translation files as .ts modules
**Notes:** Management already uses .ts module imports; dynamic import() works with .ts

---

## Language Switcher UI (I18N-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Header area | User menu or icon button | ✓ |
| Separate component | Standalone component elsewhere | |

**User's choice:** Console header area (user menu or icon button)
**Notes:** Placed near user avatar for easy access

---

## missingWarn (I18N-05)

| Option | Description | Selected |
|--------|-------------|----------|
| Development only | true in dev, false in prod | ✓ |
| Always on | Warn in all environments | |

**User's choice:** Development only
**Notes:** Production should not flood console with missing key warnings

---

## Claude's Discretion

- Switcher UI component (dropdown vs segmented control) — planner decides based on header layout
- Backend sync exact endpoint — researcher verifies if user locale preference already stored in backend

