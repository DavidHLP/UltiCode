# G-5 Coverage

| Requirement | Evidence |
| --- | --- |
| One Garden contract for shell, user row, nav rows, groups, list rows, actions, sub-items, and menus | `packages/sidebar-menu/src/styles/sidebar-menu.css`; console `features/sider` callers; `/problemset` browser snapshot |
| Preserve routes, permissions, activation, collapse, dropdown, and focus behavior | `SidebarNav.vue` logic unchanged; `AppSidebar.vue` context selection unchanged; console 70 files / 590 tests passed |
| Cover problemset, forum, contest, and personal context selection | `AppSidebar.vue` selects existing context data; `/problemset`, `/forum`, and `/contest` browser QA passed |
| Keep docs aligned | `packages/sidebar-menu/README.md`; `PROJECT_DOCUMENTATION.md` §9 |

Scope intentionally excludes the separate right-column `ProblemSetSidebar` and management visual redesign; management type-check/build smoke passed because the new selectors are console-class scoped.

## Locale-aware Garden design profiles

| Requirement | Task / Evidence |
| --- | --- |
| zh-CN and en-US have explicit global design profiles for typography, spacing, layout, and component metrics | I18N-DESIGN-002; `packages/theme/src/typography.css`, `packages/design-system/style.css` |
| Locale switching updates i18n, persistence, DOM language, and the active design profile together | I18N-DESIGN-001; shared locale-preference, both app callers, pre-bundle bootstrap, landing switch regression |
| Console, management, shared primitives, and landing shell consume the same profile seam without duplicate page systems | I18N-DESIGN-003; app shell and landing alias consumers |
| Both locales preserve translation key coverage and responsive behavior | I18N-DESIGN-004; console/management validators, tests, builds, desktop and narrow browser QA |

Out of scope: rewriting the 1908-key translation trees solely to equalize string length; changing Garden color semantics; adding RTL; release, deploy, or remote mutations.
