---
title: Maintenance Log
type: log
tags: [meta, type/log]
status: living
updated: 2026-07-05
sources: []
---

# Maintenance Log

> [!info] Format
> Append-only timeline of wiki evolution. Greppable: `grep "^## \[" log.md | tail -5`.
>
> Entry types: `bootstrap` · `ingest` · `query-filed` · `lint` · `fix` · `format` · `docs` · `refactor`.
>
> Format: `## [YYYY-MM-DD] <type> | <summary>`

---

## [2026-07-05] refactor | Skeleton slim-down — MOC decoupling & dedup

Distilled the four skeleton files (avg 227 lines, 2.3× the wiki mean) back to
their contracts. Leaf pages (entity/concept/overview) were already inside the
length envelopes in SCHEMA §4 and are untouched.

**Decoupling (MOC)** — `SCHEMA.md §11 Graph view coloring` (89 lines of
Obsidian UI procedure) was the single biggest non-contract block in the schema.
Extracted to its own concept card
[`concepts/obsidian-graph-coloring.md`](concepts/obsidian-graph-coloring.md);
SCHEMA §11 now reads as a 5-line pointer + the load-bearing invariant
("the `type/<x>` tag is the single source of truth"). The concept page inherits
the full palette, setup recipe, verification, and Bases/Juggl/Excalibrain
alternatives. Zero external anchors referenced §11 (verified by grep), so no
breakage.

**Dedup (ruthless cut)** — `README.md` was repeating `SCHEMA.md`'s three-layers
/ three-operations / layout exposition verbatim. Compressed to a navigation MOC:
one-line essence, start-here table, layout sketch, and pointers. Stale bootstrap
counts (39→48, since superseded) replaced with pointers to `log.md` and the
manifest. README 82→49 lines.

**Skeleton log compression** — the five 2026-06-21 entries below were first-day
narrative (original bootstrap + the four polish passes that followed). Folded
into one canonical `bootstrap` entry; per-step detail is recoverable via
`git log --since=2026-06-21 --until=2026-06-22 -- wiki/`. log.md 364→~150 lines.

**Stale-reference fix** — `index.md`'s info callout cited "SCHEMA §9 for the
Obsidian callout (color-group) palette", but the palette section was removed
in an earlier revision (§9 is now Writing rules). Corrected to point at the
new concept card; counts row now defers to the manifest for the authoritative
number.

**Files touched (6)**: `wiki/SCHEMA.md` (§11 → pointer, frontmatter date),
`wiki/README.md` (full rewrite), `wiki/log.md` (this entry + compression),
`wiki/index.md` (stale-ref fix + register new concept),
`wiki/concepts/obsidian-graph-coloring.md` (new). Manifest regenerated.

## [2026-07-05] ingest | Dev Environment — code-review generalization follow-up

Code review of the 4 unpushed cold-start commits found the prior section's
"fix in `application.yml`" advice had become a prod-metrics hazard: the three
metric autoconfig excludes lived in base `application.yml` (loaded by every
profile), so prod silently lost all system/jvm/tomcat micrometer binders.
Commit `f175a17` moved the excludes + `management.metrics.enable.*` into
`application-dev.yml` (dev profile only — restating `ErrorMvcAutoConfiguration`
because Spring Boot profile list-replace drops the base list), so prod keeps
full metrics. Same commit aligned the pnpm toolchain (CI pnpm 10→11;
`console`/`management` `pnpm-workspace.yaml` unified on the `allowBuilds` map;
management 补 `msw:true` lost in the original migration) and corrected the
`ecosystem.config.cjs` `JAVA_TOOL_OPTIONS` comment. The dev-environment
overview's "Fix" subsection rewritten to point at `application-dev.yml`.

## [2026-07-05] ingest | Dev Environment — WSL2+Docker Desktop cold-start pitfalls

New H2 section in `wiki/overview/dev-environment-overview.md` for the three
traps hit during cold-start: (1) openssl missing on Fedora 44 WSL with
/usr/sbin off PATH, (2) MySQL 9.1 IPv6-only binding colliding with Docker
Desktop's broken IPv4→IPv6 port forwarding (fix: container fixed IPs + JDBC to
docker bridge IPs), (3) JDK 17.0.2 + WSL2 cgroup v2 NPE in actuator metric
binders (fix: autoconfig exclude three metric auto-configs). All three fixes
committed same session; the wiki section is the re-debug-suppression note.
Frontmatter `updated:` bumped to 2026-07-05; `sources:` adds `application.yml`.

## [2026-06-24] docs | wiki cross-links — back-link 9 orphan / weakly-linked pages

Full-link graph pass over `wiki/` (basename-resolved `[[...]]`; content inbound
`ci` excludes index/log/SCHEMA/templates/daily). Baseline was healthy — 99%
resolve, 4 "dead" links all template/SCHEMA placeholders — but 9 content pages
were under-linked: 2 orphans (`concepts/notification-dispatch-and-preferences`,
`concepts/sidebar-menu`, ci=0) and 7 weak (ci=1: `entities/{achievement, backup,
follow, i18n, monitoring, search, subscription}`). Root cause was mostly
missing back-links. Added one semantically-justified inbound link per page
across 9 files (page-tail `Cross-links` / `Related` / `Links out` only).
**Verified**: orphans 2→0, weak 7→0, all targets ci≥2; resolve-rate 98.9%.

## [2026-06-24] fix | sidebar-menu — cross-CR fixes (uncontrolled collapse, router-link coverage, CSS)

Closed all findings from three code-level CRs (claude/glm-5.2, codex/MiniMax-M3,
opencode/deepseek) on the sidebar-menu unification commits `a47423c4d..fc266ce10`.
`SidebarGroupCollapsible` + `SidebarParentItem` made purely uncontrolled;
`SidebarMenuSubItem` / `SidebarMenuItem` got `inheritAttrs:false` + dead
`attrClass` removed; CSS contract tightened (`color-mix` tinted backgrounds in
`@supports`, named `group/*` selectors); global `RouterLink` stub in
`src/__tests__/setup.ts` unlocked `as='link'` / Mode-A / `:to` coverage (41
specs, +14). `docs/architecture/` spec + 6 CRs retired (reviews recoverable via
`git show 66cd1be64`).

## [2026-06-24] ingest | Notification Dispatch & Preferences (ADR-004) concept page

Consolidated ADR-004 (previously Javadoc across 10+ notification files) into
one concept page: typed dispatcher, preference gate, 4 categories, idempotency
ledger, dual-path migration via `FeatureFlags.useNotificationIntent`. Filed
with the admin-broadcast preference-bypass fix (MARKETING/COMMUNICATION now
filtered; SECURITY/SYSTEM force-delivered).

## [2026-06-23] ingest | wiki manifest v1 — per-page git provenance

Added `wiki/.meta/manifest.json` — a derived, deterministic manifest binding
every wiki content page to its last git commit (`last_commit.sha` + author +
date + subject) plus `body_sha256`. Machine-traceable companion to the
hand-maintained frontmatter `updated:` date. New tooling
`scripts/dev/wiki-manifest.sh` generates and lints (`--check`):
`[stale-fm]` / `[unregistered]` / `[stale-entry]` / `[drift]` / `[head]`.

## [2026-06-22] ingest | Process Management concept page — PM2 vs Preview port mutex

New `wiki/concepts/process-management.md` codifying the two modes (PM2
long-running / Preview per-session) and the rule that you pick one, don't mix
on the same port. Three operational rules: which mode → which command;
`pnpm exec vite --strictPort` (not `pnpm dev`) for Preview; one
`scripts/dev/doctor.sh` as the read-only inspector. New file in sources:
`scripts/dev/doctor.sh` (4-port occupancy + PM2 health with `↺ ≥ 10` warning +
Docker container health; pure `bash` + `node`).

## [2026-06-21] bootstrap | Initial wiki scaffold + same-day polish (compressed)

First-day foundation. The original five entries (bootstrap → 9-module ingest →
callout formatting → Obsidian design polish → SCHEMA §11 procedure fix) are
folded here; per-step detail is recoverable via
`git log --since=2026-06-21 --until=2026-06-22 -- wiki/`.

**Scaffold (39→48 pages)** — created `README.md` / `SCHEMA.md` / `index.md` /
`log.md` + 7 overviews + 25 entities + 12 concepts + 4 templates, distilled
from `AGENTS.md`, `CLAUDE.md`, `.claude/rules/`, 26 backend modules, 35
migrations, `docker/sandbox/`, `scripts/dev/`, `ecosystem.config.cjs`, `shared/`.
Same day promoted 9 low-density modules to their own entity pages (admin,
search, i18n, monitoring, backup, achievement, follow, subscription; bookmark +
vote + edgeoperations merged into `interactions`).

**Obsidian vault wiring** — `type/<x>` tag added to every page's `tags:`
(convention codified in SCHEMA §5); Daily Notes + Templates core plugins
enabled; `templates/daily-note.md` + first `daily-notes/2026-06-21.md` created;
graph-view coloring keyed off `#type/<x>` (procedure now at
`concepts/obsidian-graph-coloring.md`).

**Callout palette** — semantic `[!xxx]` callouts applied across 44 content
pages (concept / entity / overview shapes). Each H2 section opens with the
appropriate callout (`[!question]` on "The problem", `[!success]` on "The
decision", `[!warning]` on "Gotchas", etc.).
