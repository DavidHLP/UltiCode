---
title: Maintenance Log
type: log
tags: [meta, type/log]
status: living
updated: 2026-06-21
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

