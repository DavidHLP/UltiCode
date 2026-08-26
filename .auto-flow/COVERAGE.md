# G-5 Coverage

| Requirement | Evidence |
| --- | --- |
| One Garden contract for shell, user row, nav rows, groups, list rows, actions, sub-items, and menus | `packages/sidebar-menu/src/styles/sidebar-menu.css`; console `features/sider` callers; `/problemset` browser snapshot |
| Preserve routes, permissions, activation, collapse, dropdown, and focus behavior | `SidebarNav.vue` logic unchanged; `AppSidebar.vue` context selection unchanged; console 70 files / 590 tests passed |
| Cover problemset, forum, contest, and personal context selection | `AppSidebar.vue` selects existing context data; `/problemset`, `/forum`, and `/contest` browser QA passed |
| Keep docs aligned | `packages/sidebar-menu/README.md`; `PROJECT_DOCUMENTATION.md` §9 |

Scope intentionally excludes the separate right-column `ProblemSetSidebar` and management visual redesign; management type-check/build smoke passed because the new selectors are console-class scoped.
