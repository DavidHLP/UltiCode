# G-5 Worklog

- 2026-08-26: Recovered existing Garden task ledger and protected unrelated dirty worktree changes.
- 2026-08-26: Audited AppSidebar, SidebarNav, Calendars, NavUser, SidebarListSections, and shared sidebar-menu contract.
- 2026-08-26: Implemented shared Garden shell/row/group/list/user/dropdown styles and removed per-section width/color overrides.
- 2026-08-26: Validated `/problemset`, `/forum`, `/contest`, console 70 test files / 590 tests, type-check, lint, format, build, and graph synchronization.
- 2026-08-27: Recovered clean `main` at `6b35ea1dc`; confirmed i18n key parity (console 1908/1908, management validator pass) and theme/typography guards pass.
- 2026-08-27: Browser evidence identified a real locale lifecycle split: landing copy switched to English while `html lang` remained `zh-CN` because `LandingHeader` mutated vue-i18n directly.
- 2026-08-27: Planned shared `html[lang]`-selected zh/en Garden profiles, unified initial locale resolution, pre-bundle locale seeding, and cross-app validation.
