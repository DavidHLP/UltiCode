---
title: Maintenance Log
type: log
tags: [meta, type/log]
status: living
updated: 2026-06-24
sources: []
---

# Maintenance Log

> [!info] Format
> Append-only timeline of wiki evolution. Greppable: `grep "^## \[" log.md | tail -5`.
>
> Entry types: `bootstrap` · `ingest` · `query-filed` · `lint` · `fix` · `format`.
>
> Format: `## [YYYY-MM-DD] <type> | <summary>`

---

## [2026-06-21] fix | SCHEMA § 11 rewritten — graph view coloring is now a manual UI procedure

The earlier `fix` entry claimed `graph.json` `colorGroups` could be set
externally; in practice Obsidian rewrites that file on open, normalizing
external edits back to whatever the local UI last had. After two revert
cycles during this session, the right home for this knowledge is
**a procedure**, not a file edit.

**`SCHEMA.md § 11` rewritten** — now reads as a one-time setup recipe:

- **Why this section exists** — explains the Obsidian-normalizes-`graph.json`
  problem so future agents stop trying to write the file from outside.
- **Palette** — canonical hex table (`#7c3aed` / `#0ea5e9` / `#10b981`).
- **One-time setup (per machine)** — 6-step UI procedure: open Graph view
  → ⚙️ → Color groups → add 3 rules with `tag:#type/<x>` queries →
  close → Obsidian writes the rules itself.
- **Verification** — expected node counts (25 violet entities, 12 blue
  concepts, 7 green overviews, 4 gray meta, 1 daily node).
- **Alternative paths** — Bases / Juggl / Excalibrain for scriptable
  coloring, all reusing the same `#type/<x>` tag convention.

**Why this matters** — the `#type/<x>` tag convention in § 5 is now the
single source of truth for "what is this page". Whether the user picks
graph.json colorGroups (manual), Juggl CSS rules, or Bases filtering,
all three read the same tag. The graph coloring feature is no longer
gated on getting a file edit to stick.

**Files touched (1)**: `wiki/SCHEMA.md` § 11 (full rewrite).

**Note for future ingest agents**: do **not** re-attempt to write
`.obsidian/graph.json` colorGroups externally — it's a known no-op.
Document any new visual rule in this section instead.

## [2026-06-21] fix | Obsidian design polish — type tags, daily-notes workflow, graph view coloring

Wired up the Obsidian vault so it earns its keep as a daily-use tool, not
just a static catalog.

**Type tags (every page)** — added `type/entity`, `type/concept`,
`type/overview` (and meta variants `type/index`, `type/log`, `type/schema`,
`type/landing`) to the `tags:` array of all 48 content pages plus the 4
meta pages. Convention now codified in `SCHEMA.md § 5`: every page MUST
carry exactly one `type/<x>` tag mirroring its `type:` field. The tag is
what Obsidian Bases, Dataview, and tag-pane filters key off.

**Graph view coloring** — `graph.json` `colorGroups` configured with three
groups keyed off `tag:#type/<x>` (entity → violet `#7c3aed`,
concept → blue `#0ea5e9`, overview → green `#10b981`). Pages without a
type-tag fall through to default gray — that's the lint signal that a
page was added without following the template. *(If `graph.json` is
reverted on a given machine, the type-tag convention still powers
Dataview/Bases regardless.)*

**Daily Notes + Templates core plugins enabled** —
`.obsidian/core-plugins.json` flipped `templates: true` and
`daily-notes: true`. New config files:
- `.obsidian/daily-notes.json` (folder `daily-notes/`, format `YYYY-MM-DD`,
  template `templates/daily-note`)
- `templates/daily-note.md` (new template: Ingested / Created / Queries
  filed / Lint findings / Notes)
- `daily-notes/2026-06-21.md` (first journal entry under the new workflow)

**SCHEMA additions** — two new sections:
- § 10 *Daily notes — human ingest journal* (how `daily-notes/` complements
  `log.md`: per-day paragraphs vs per-operation lines)
- § 11 *Graph view coloring* (where the colors come from + how to lint them)

**CLAUDE.md sync** — line 17's wiki description now mentions the new
`daily-notes/` folder and `templates/daily-note` template. No stale
`wiki/theme|ops|adr|CODEMAPS` paths remain anywhere in `CLAUDE.md` /
`AGENTS.md` (verified by `grep -nE "wiki/(theme|ops|adr|CODEMAPS)"` → no matches).

**Files touched (5 + 1)**:
- `wiki/.obsidian/{core-plugins,daily-notes,graph}.json`
- `wiki/templates/daily-note.md` (new)
- `wiki/daily-notes/2026-06-21.md` (new)
- `wiki/SCHEMA.md` § 3 + § 5 + new § 10/11
- `wiki/README.md` Layout + Status + frontmatter
- All 48 entity/concept/overview frontmatters (one-line tag addition)
- 4 meta pages frontmatters
- `CLAUDE.md` line 17

**Total wiki files now**: 7 overviews + 25 entities + 12 concepts + 4 meta
+ 1 daily + 4 templates = **53 files** (+5 vs prior 48).

## [2026-06-21] format | Obsidian color-group callouts applied across wiki (51 pages)

Formatted the wiki to Obsidian's standard pattern per the user request
"有 tag 有 颜色组". Every content page now uses semantic callouts (color groups)
as documented in `SCHEMA.md § 9`.

**Conventions added (`SCHEMA.md § 9`)**: a 13-entry palette mapping each
`[!xxx]` to a color and a use case (`[!question]` purple, `[!success]` green,
`[!info]` blue, `[!example]` purple, `[!warning]` orange, `[!danger]` red,
`[!tip]` cyan, `[!note]` blue, `[!quote]` gray, `[!abstract]` gray,
`[!bug]` red, `[!link]` link-blue, `[!tldr]` gray). Per-page-type shapes
for `concept` / `entity` / `overview`.

**Templates updated** (`templates/{concept,entity,overview}.md`): now ship
with example callout blocks so future pages follow the convention by default.

**Pages reformatted** (44 content pages): 12 concepts + 25 entities + 7 overviews.
Each H2 section now opens with the appropriate callout (`[!question]` on
"The problem", `[!success]` on "The decision", `[!example]` on "Key tables",
`[!warning]` on "Gotchas", `[!link]` on "Cross-links", etc.).

**Meta pages**: `README.md`, `index.md`, `log.md` each gained an orientation
callout. This `log.md` entry is itself the canonical example of the
"format" entry type.

**Notes**:
- A linter pass subsequently simplified some entity pages to a minimal
  H2 structure without callouts; this was preserved as the intentional
  per-page style for those entries. Concept and overview pages retain the
  full callout palette.

## [2026-06-21] ingest | Promoted 9 low-density modules to entity pages (admin/search/i18n/monitoring/backup/achievement/follow/subscription + interactions merge)

Closed the 10 follow-up module page gaps from the initial bootstrap. Eight
modules got their own page; `bookmark` + `vote` + `edgeoperations` were
**merged** into a single `interactions` page because the three modules share
the same widget surface (the "interactions" UI hits all three) and the data
model shares `targetType` semantics. The `backend-modules-overview` table was
rewritten to point at the new pages (the previous `(table only)` markers
across `admin` / `search` / `i18n` / `monitoring` / `backup` /
`achievement` / `follow` / `bookmark` / `vote` / `subscription` rows are
gone). `index.md` updated to 25 entities; total now **48 pages**.

**New entity pages (8)**: `admin`, `search`, `i18n`, `monitoring`, `backup`,
`achievement`, `follow`, `subscription`.

**New merged entity page (1)**: `interactions` (`bookmark` + `vote` +
`edgeoperations`).

**Updated**: `overview/backend-modules-overview.md` (10 row links + the
"16/10" intro), `index.md` (10 new lines + counts), `log.md` (this entry).

**Design notes**:
- `search` is **MeiliSearch**, not Elasticsearch — corrected from the
  original `(Elasticsearch-shaped)` table marker.
- `interactions` deliberately keeps three Java modules' detail in one
  page; splitting them would repeat the same widget flow three times.
- `subscription` has **two** controllers (`/admin/subscriptions` +
  `/subscription`); only the user-facing one is non-admin.

## [2026-06-21] bootstrap | Initial wiki scaffold (39 pages)

Created the wiki from a clean `wiki/` directory, following the LLM Wiki pattern
and [`SCHEMA.md`](SCHEMA.md). Decisions locked: full first cut (~39 pages),
pure-idea taxonomy (`overview/` + `entities/` + `concepts/`), English prose.

**Skeleton (4)**
- `README.md`, `SCHEMA.md`, `index.md`, `log.md`

**`overview/` (7)** — architecture, backend-modules (26-row map), frontend-apps,
judging-pipeline, auth-flow, database-schema, dev-environment.

**`entities/` (16)** — submission, contest, problem, notification, user, auth,
forum, moderation, judge-queue, sandbox, solution, problemlist, websocket,
permission, refreshtoken, email.

**`concepts/` (12)** — exactly-once-judging, notification-idempotency,
refresh-token-hash-only-storage, virtual-contest, security-invariants,
csrf-mechanism, sandbox-security-contract, theme-system,
result-envelope-and-case-mapping, module-layering, flyway-migration-discipline,
arthas-diagnostics.

**Distilled from**: `AGENTS.md`, `CLAUDE.md`, `.claude/rules/`,
`backend-spring/.../modules/` (26 modules), `init-db/migrations/` (35 migrations),
`docker/sandbox/`, `scripts/dev/`, `ecosystem.config.cjs`, `shared/`.

**Deliberately not done this pass** (follow-up): low-density modules (admin,
bookmark, vote, edgeoperations, i18n, monitoring, backup, search, achievement,
follow, subscription) are covered by the `backend-modules-overview` table rather
than their own entity page; `CLAUDE.md`/`AGENTS.md` still reference the old
`wiki/theme|ops|adr|CODEMAPS` paths and need updating separately; the wiki is the
knowledge layer and does not duplicate the command layer.

## [2026-06-22] ingest | Process Management concept page — PM2 vs Preview port mutex

A recurring 9001/9002/9003 outage pattern motivated this page: Claude Code
Preview (`pnpm exec vite` in `.claude/launch.json`) and PM2 (`node vite` in
`ecosystem.config.cjs`) both want the same ports, the Preview tool's
`autoPort` doesn't work with vite's `--port 0` (the panel points at 9002
while vite silently slips to 5173), and `pm2 list` shows `ulticode-9001`
as `online` with `↺=45` when it isn't actually serving — a state no
existing page warned about.

**New page** — `wiki/concepts/process-management.md` codifies:
- The two **modes** (PM2 long-running / Preview per-session) and the rule
  that you pick one, don't mix on the same port.
- The three operational rules: which mode → which command;
  `pnpm exec vite --strictPort` (not `pnpm dev`) for Preview; one
  `scripts/dev/doctor.sh` as the read-only inspector and recommender.
- Trade-offs — `--strictPort` is loud-on-conflict, doctor is read-only
  by design, `autoPort: true` is decorative for vite today.

**Cross-links added** — `wiki/index.md` (catalog, 12 → 13 concepts; 48 →
49 pages) and a forward link from `wiki/overview/dev-environment-overview`
to the new page (so the existing "PM2 + docker + Arthas" map points
readers at the new "which mode am I in" question).

**New file in sources** — `scripts/dev/doctor.sh`: 4-port occupancy +
PM2 health (with `↺ ≥ 10` warning) + Docker container health +
recommendation. Pure `bash` + `node` (already required by the frontends);
no `python3` or `jq` dependency. Cross-platform: `lsof` first, `netstat
-ano` on Windows, `/proc/<pid>/cmdline` on Linux. Exits 0 when all
listeners are accounted for, 1 if a port is held by an unknown owner.

**Files touched (3 new / edited)**:
- `wiki/concepts/process-management.md` (new, ~150 lines)
- `wiki/index.md` (1 line added, count updated)
- `wiki/overview/dev-environment-overview.md` (1 forward link)

## [2026-06-23] ingest | wiki manifest v1 — per-page git provenance

Added `wiki/.meta/manifest.json`, a **derived, deterministic** manifest that
binds every wiki content page to the last git commit that modified it
(`last_commit.sha` + author + date + subject) plus a `body_sha256`. It is the
machine-traceable companion to the hand-maintained frontmatter `updated:` date
— see [SCHEMA §12](SCHEMA.md).

**Why** — frontmatter `updated:` is a human semantic signal and it drifts: the
first `--check` run flagged pages whose `updated:` still read 2026-06-21 but
were materially edited on 2026-06-22 (`index.md`, `log.md`,
`dev-environment-overview.md`). The manifest surfaces that gap for audits.

**New tooling** — `scripts/dev/wiki-manifest.sh` generates the manifest from
git history and lints it (`--check`): `[stale-fm]` (frontmatter date behind
last commit), `[unregistered]`, `[stale-entry]`, `[drift]`, `[head]`. Pure
`node` (no extra deps); reads scalar frontmatter fields via regex.

**Frontmatter dates bumped to 2026-06-23** on the three pages materially edited
in this change (`SCHEMA.md`, `index.md`, `log.md`).
`dev-environment-overview.md` is intentionally left at its 2026-06-21 date —
its real last edit was the 2026-06-22 forward-link ingest; correcting that date
is deferred to its next material edit (kept as a standing `[stale-fm]` reminder
rather than back-dating).

**Files touched**:
- `scripts/dev/wiki-manifest.sh` (new)
- `wiki/.meta/manifest.json` (new, 54 pages)
- `wiki/SCHEMA.md` (§3 layout, §2 lint, new §12; frontmatter date)
- `wiki/index.md` (Meta line; frontmatter date)
- `wiki/log.md` (this entry; frontmatter date)


## [2026-06-24] ingest | Notification Dispatch & Preferences (ADR-004) concept page

Consolidated ADR-004 (previously Javadoc across 10+ notification files: §2.1
sealed intent, §2.3 fan-out, M4a-M4d migration, F9 statelessness, finding #7
CONTEST removal) into one concept page: typed dispatcher, preference gate, 4
categories, idempotency ledger, dual-path migration via
FeatureFlags.useNotificationIntent. Filed with the admin-broadcast
preference-bypass fix (MARKETING/COMMUNICATION now filtered; SECURITY/SYSTEM
force-delivered).

Pages: new `concepts/notification-dispatch-and-preferences.md`; `index.md`
(concept row + counts 13->14, 49->50).


## [2026-06-24] fix | sidebar-menu — cross-CR fixes (uncontrolled collapse, router-link coverage, CSS)

Closed all findings from three code-level CRs (claude/glm-5.2, codex/MiniMax-M3,
opencode/deepseek) on the sidebar-menu unification commits `a47423c4d..fc266ce10`.

**Correctness** — `SidebarGroupCollapsible` + `SidebarParentItem` made purely
uncontrolled (the `fc266ce10` `:open=undefined` → controlled-closed class of bug
is now structurally impossible; a new `defaultOpen=false` spec guard catches
what the pre-fix test couldn't); `SidebarMenuSubItem` / `SidebarMenuItem` got
`inheritAttrs:false` + dead `attrClass` removed; `SidebarParentItem` chevron
hit-area enlarged for touch; `SidebarNavUser` `@error` avatar fallback;
`SidebarGroupCollapsible.active` now drives `[data-active]` (not a class).

**CSS contract** — `color-mix` tinted backgrounds wrapped in `@supports`;
`.group:hover` extended to named groups (`group/collapsible`, `group/item`);
`.uc-sidebar-group-label[data-active]` added; the duplicate `@import` removed
from `shared/design-system/style.css` (it loaded before tailwind AND twice).

**Tests** — global `RouterLink` stub in `src/__tests__/setup.ts` unlocked
`as='link'` / Mode-A / `:to` coverage (41 specs, +14 vs prior 27).

**Docs** — `shared/sidebar-menu/README.md` created; `wiki/concepts/sidebar-menu.md`
Trade-offs now record the cascade / uncontrolled / @beta decisions; the
`docs/architecture/` spec + 6 CRs were retired (this wiki is the single source;
reviews recoverable via `git show 66cd1be64`).

**Verified**: shared/sidebar-menu 41/41 + `vue-tsc` 0; console + management
`vue-tsc` 0. Manifest regenerated.

## [2026-06-24] docs | wiki cross-links — back-link 9 orphan / weakly-linked pages

Full-link graph pass over `wiki/` (basename-resolved `[[...]]`; content inbound
`ci` excludes index/log/SCHEMA/templates/daily). Baseline was healthy — 99%
resolve, 4 "dead" links all template/SCHEMA placeholders — but 9 content pages
were under-linked: 2 orphans (`concepts/notification-dispatch-and-preferences`,
`concepts/sidebar-menu`, ci=0) and 7 weak (ci=1: `entities/{achievement, backup,
follow, i18n, monitoring, search, subscription}`). Root cause was mostly
**missing back-links** — the cited side named its neighbour in `Cross-links`
but the neighbour never returned it (`backup`→admin, `monitoring`→admin), plus
a sibling-concept gap (notification-idempotency ↔ dispatch) and `user.md`
using a markdown link where a wikilink belonged.

Added one semantically-justified inbound link per page across 9 files
(page-tail `Cross-links` / `Related` / `Links out` only; no prose changes):
`entities/{notification, user, interactions, problem, admin}` ·
`concepts/{notification-idempotency, theme-system, arthas-diagnostics}` ·
`overview/frontend-apps-overview`.

**Verified**: re-ran the graph — orphans 2→0, weak 7→0, all 9 targets now
ci≥2 (follow/monitoring=3, rest=2); dead links unchanged at 4 placeholders;
resolve-rate 98.9% (345/349); `git diff --check` clean. sidebar-menu's
annotated list-style `## Related` left as-is (its refactor-archive note is
load-bearing; SCHEMA format treated as guideline). Manifest regenerated.

## [2026-07-05] ingest | Dev Environment — WSL2+Docker Desktop cold-start pitfalls

New H2 section in `wiki/overview/dev-environment-overview.md` documenting the
three traps hit during 2026-07-05 cold-start: (1) openssl missing on Fedora
44 WSL with /usr/sbin off PATH, (2) MySQL 9.1 IPv6-only binding colliding
with Docker Desktop's broken IPv4→IPv6 port forwarding (fix: container
fixed IPs + JDBC to docker bridge IPs), (3) JDK 17.0.2 + WSL2 cgroup v2
NPE in actuator metric binders (fix: autoconfig exclude three metric
auto-configs). All three fixes are committed in the same session; the wiki
section is the re-debug-suppression note for the next session. Frontmatter
`updated:` bumped to 2026-07-05; `sources:` adds `application.yml`.

## [2026-07-05] ingest | Dev Environment — code-review generalization follow-up

Code review of the 4 unpushed cold-start commits found the section above's
"fix in `application.yml`" advice had become a prod-metrics hazard: the three
metric autoconfig excludes lived in base `application.yml` (loaded by every
profile), so prod silently lost all system/jvm/tomcat micrometer binders. The
"harmless no-op on newer JDKs" comment was factually wrong (a real disable on
every JDK). Commit `f175a17` moved the excludes + `management.metrics.enable.*`
into `application-dev.yml` (dev profile only — restating `ErrorMvcAutoConfiguration`
because Spring Boot profile list-replace drops the base list), so prod keeps
full metrics. Same commit also aligned the pnpm toolchain (CI pnpm 10→11;
`console`/`management` `pnpm-workspace.yaml` unified on the `allowBuilds` map;
management补 `msw:true` lost in the original migration), and corrected the
`ecosystem.config.cjs` `JAVA_TOOL_OPTIONS` comment. This wiki section's "Fix"
subsection rewritten to point at `application-dev.yml` and explain the dev-only
rationale. Frontmatter `updated:` stays 2026-07-05 (same-day revision).
