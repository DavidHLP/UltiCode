# G-5 Worklog

- 2026-08-26: Recovered existing Garden task ledger and protected unrelated dirty worktree changes.
- 2026-08-26: Audited AppSidebar, SidebarNav, Calendars, NavUser, SidebarListSections, and shared sidebar-menu contract.
- 2026-08-26: Implemented shared Garden shell/row/group/list/user/dropdown styles and removed per-section width/color overrides.
- 2026-08-26: Validated `/problemset`, `/forum`, `/contest`, console 70 test files / 590 tests, type-check, lint, format, build, and graph synchronization.
- 2026-08-27: Recovered clean `main` at `6b35ea1dc`; confirmed i18n key parity (console 1908/1908, management validator pass) and theme/typography guards pass.
- 2026-08-27: Browser evidence identified a real locale lifecycle split: landing copy switched to English while `html lang` remained `zh-CN` because `LandingHeader` mutated vue-i18n directly.
- 2026-08-27: Planned shared `html[lang]`-selected zh/en Garden profiles, unified initial locale resolution, pre-bundle locale seeding, and cross-app validation.
- 2026-08-27: Implemented shared initial locale resolution, corrected the landing switcher to use `useLocale`, and seeded `html lang` in the canonical pre-bundle bootstrap plus both generated copies.
- 2026-08-27: Added explicit zh-CN/en-US typography and layout metric profiles; console/management shells and landing/problem consumers now read shared locale-aware tokens.
- 2026-08-27: Focused checks passed for locale-preference (4/4), theme (31/31), design-system (15/15), and console locale consumers (29/29); browser QA confirmed cross-route profile switching.
- 2026-08-27: Review found and fixed the console storage-notifier import regression and an existing sidebar shadow color literal; no Confirmed Findings remain.
- 2026-08-27: Final Garden gate passed with Console 70/590 and Management 53/423 tests, both builds/type-checks, i18n validators, theme guards, and legacy color sweep.
