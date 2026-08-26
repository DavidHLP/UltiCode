# @ulticode/sidebar-menu

Shared sidebar **visual contract** + components for the UltiCode `console` and
`management` frontends. Activation visuals come from a single CSS source
(`.uc-sidebar-*` + `[data-active]` attributes) so the two apps never hand-write
`border-l-4 border-[--accent-electric]` again.

## Consumption

Source-alias import (workspace internal — no build step):

```ts
import {
  SidebarMenuItem as SharedSidebarMenuItem,      // alias to avoid clashing with
  SidebarMenuSubItem as SharedSidebarMenuSubItem, // the local shadcn ui/sidebar
  SidebarGroupCollapsible,
  SidebarParentItem,
} from '@/shared/sidebar-menu/src'
```

> The `as SharedSidebarMenuItem` alias is **required** when a local shadcn
> `SidebarMenuItem` exists in the same file — the two same-named systems coexist
> by aliasing.

## CSS import (per app)

Each app imports the contract **after** design-system/tailwind in its
`src/style.css`, so Tailwind utilities cannot override the activation bar:

```css
@import "../../../packages/design-system/style.css";
@import "../../../packages/sidebar-menu/src/styles/sidebar-menu.css"; /* AFTER tailwind */
```

Removing the explicit per-app import re-introduces cascade fragility —
`.uc-sidebar-item[data-active='true']` would load before Tailwind and lose to
utility classes on equal specificity. (Do **not** re-add the import to
`packages/design-system/style.css` — it was removed because it loaded before
tailwind and caused the contract to appear twice.)

## Components

| Component | Status | Notes |
|---|---|---|
| `SidebarMenuItem` | used by console | top-level row; `as='link'\|'button'\|'a'`, `badge`, `showChevron` |
| `SidebarMenuSubItem` | used by console + management | nested row; renders router-link when `:to` is passed |
| `SidebarGroupCollapsible` | used by console | collapsible section; forwards `open`/`defaultOpen`/`disabled`; `active` drives the label via `[data-active]` |
| `SidebarParentItem` | used by console | parent = link + collapsible children; **uncontrolled only** (no `v-model:open` — see component JSDoc) |
| `SidebarNavUser` | **@beta — not yet wired** | reserved for future adoption |
| `SidebarIconButton` | **@beta — not yet wired** | hover-reveal action; needs a `.group` or named `group/*` ancestor |

## Activation contract

`.uc-sidebar-item` / `.uc-sidebar-sub-item` read `[data-active="true|false"]`.
Top-level rows also share their geometry (padding, gap, height, radius and
focus outline) here, so source-aliased consumers do not depend on Tailwind
scanning the shared package. Nested rows retain their component-level geometry
utilities, allowing management's terminal density to remain intentional.
Consumers only choose semantic size/text utilities and content; navigation,
action and list rows therefore keep one visual shape.
Tinted backgrounds use `color-mix()` wrapped in `@supports` — browsers without
`color-mix` (Chrome <111 / Safari <16.2 / Firefox <113) still render the
activation bar + color + font-weight; only the tint is dropped.

The console shell also uses the layout contract classes
`.uc-sidebar-shell`, `.uc-sidebar-header`, `.uc-sidebar-content`,
`.uc-sidebar-nav`, `.uc-sidebar-section-trigger`, `.uc-sidebar-list-section`,
`.uc-sidebar-user-trigger`, and `.uc-sidebar-dropdown`. They keep user rows,
navigation rows, actions, groups, list rows, sub-items, and menus on the same
Garden spacing, radius, surface, and focus treatment. Consumers should pass
content and state only; do not add per-section width, radius, or color overrides.

## Tests

```bash
pnpm test        # jsdom, 30+ specs
pnpm type-check  # vue-tsc --build
```

A global `RouterLink` stub is registered in `src/__tests__/setup.ts` so the
`as='link'` / `:to` / `SidebarParentItem` Mode-A production paths are covered
even though this package has no vue-router dependency.
