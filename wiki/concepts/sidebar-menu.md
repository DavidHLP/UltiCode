---
title: Sidebar Menu Visual Contract
type: concept
tags: [sidebar, frontend, design-system, type/concept]
status: living
updated: 2026-06-24
sources:
  - shared/sidebar-menu/src/
  - shared/sidebar-menu/src/styles/sidebar-menu.css
  - console/src/features/sider/
  - management/src/components/layout/
aliases: [ADR-005, Sidebar Menu Unification, uc-sidebar]
---

# Sidebar Menu Visual Contract

> [!note] Landed record of the sidebar-menu unification (retired spec at `docs/architecture/sidebar-menu-unification.md`, recoverable via `git show 66cd1be64`). The two frontends used to hand-write the same activation bar in four+ places; it now lives once in `shared/sidebar-menu`.

## The problem

console (`features/sider/`) and management (`components/layout/`) each hand-wrote the same sidebar visual contract — a 4px accent activation bar, tinted background, 2xs uppercase group label, collapse chevron, hover-revealed row actions, and a user bar. The activation class string alone (`border-l-4 border-[var(--accent-electric)] bg-.../8 text-... font-bold`) was duplicated across `SidebarNav.vue` / `NavMain.vue` and the collapsed branches, so a token change meant editing every copy.

## The decision

Lift the contract into `shared/sidebar-menu` (`@ulticode/sidebar-menu`):

- **CSS contract** (`styles/sidebar-menu.css`, `.uc-sidebar-*`) — the single source for the activation bar, group label, and hover-button reveal. It is driven by `[data-active="true|false"]` attributes, not hand-written class triples.
- **Components** — `SidebarMenuItem` / `SidebarMenuSubItem` / `SidebarGroupCollapsible` (existing, enhanced) plus `SidebarParentItem` / `SidebarNavUser` / `SidebarIconButton` (new). All emit `data-active`; none import an icon library — icons arrive via prop/slot, so console (lucide) and management (tabler) coexist.
- **Two-tier naming** — local `@/components/ui/sidebar` is the shadcn structural primitives (provider, collapsed tooltip); `@/shared/sidebar-menu/src` is the visual-contract layer. Same-named imports disambiguate via aliases (`SidebarMenuItem as SharedSidebarMenuItem`).

## Why

A token or spacing tweak should land in one file, not four. The shared package already follows the `shared/auth-core` split; sidebar visuals are the same kind of cross-app debt. `[data-active]` + CSS keeps component templates clean (no `isActive ? 'border-...' : '...'` triples) and lets the contract be audited in a single stylesheet. Activation is now an attribute the component emits, not a class string each call site re-derives.

## Where it lives

- `shared/sidebar-menu/src/styles/sidebar-menu.css` — `.uc-sidebar-item` / `-sub-item` / `-group-label` / `-icon-button`.
- `shared/sidebar-menu/src/components/*.vue` — six components, exported from `src/index.ts`.
- Imported by `console/src/style.css` and `management/src/style.css`, **after** `shared/design-system` (which owns the tokens `--accent-electric`, `--silver-*`).
- console consumes it in `features/sider/SidebarNav.vue`; management consumes `SidebarMenuSubItem` inside `components/layout/NavMain.vue`.

## Trade-offs

- **Not pixel-identical across apps.** console is lucide + `text-sm font-medium`; management is tabler + `font-mono text-xs` (terminal style). The *contract* (bar, label, reveal) is shared; the *typography / icon family* is not. management therefore keeps `SidebarMenuButton` for parent/plain rows and only de-duplicates the class string (`itemRowClass`), rather than swapping in shared rows that would change its terminal look.
- **`SidebarGroupCollapsible` keeps its name.** A new shared `SidebarGroup` would collide with the local shadcn `ui/sidebar/SidebarGroup.vue`, so the group container was enhanced in place (`title` / `icon` / `active` props) instead.
- **Sidebar-collapsed (icon-only) mode is out of scope** for the shared components. Both apps keep shadcn `SidebarMenuButton` + tooltip for that branch, so the shared layer only covers the expanded sidebar. (console de-duplicates that branch via a local `itemRowClass(active)` helper rather than the shared CSS, since the popover/tooltip structure can't reuse `.uc-sidebar-item`.)
- **CSS cascade depends on import order.** `.uc-sidebar-*` must load **after** Tailwind, which is why each app's `style.css` imports it explicitly after `shared/design-system`. It must NOT also be imported from `shared/design-system/style.css` (it once was — that loaded it before tailwind AND duplicated it). Removing the per-app import lets Tailwind utilities override the activation bar on equal specificity.
- **`color-mix` is progressive enhancement.** Tinted activation backgrounds use `color-mix(in srgb, …)` wrapped in `@supports`; browsers without it still render the bar + color + font-weight (only the tint drops).
- **`SidebarParentItem` and `SidebarGroupCollapsible` are uncontrolled only.** Neither exposes `v-model:open` — binding `:open="undefined"` makes reka treat `CollapsibleRoot` as controlled-closed so `CollapsibleContent` never renders (the `fc266ce10` regression). Both seed state from `defaultOpen` at mount. Route-driven auto-expand would need a different pattern (e.g. a `key` to force remount on route change).
- **`SidebarNavUser` / `SidebarIconButton` are @beta.** Exported but not yet wired into either app. `SidebarIconButton`'s hover-reveal also requires a `.group` or named `group/*` ancestor (the CSS covers both via `[class*='group/']:hover`).

## Related

- [[theme-system]] — owns the `.uc-` token family this builds on.
- [[module-layering]] — the `shared/` split (`auth-core`, `auth-ui`, `sidebar-menu`, `theme`).
- The refactor's spec + six code reviews (3 plan-level, 3 code-level) were retired from `docs/architecture/` (the dir was already gone before this docs-merge; its content lives in this wiki page and in `git show 66cd1be64`).
