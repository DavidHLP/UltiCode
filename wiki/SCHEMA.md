---
title: SCHEMA — Wiki Convention
type: schema
tags: [meta, convention, type/schema]
status: living
updated: 2026-06-23
sources:
  - AGENTS.md
  - CLAUDE.md
---

# SCHEMA — How this wiki is structured and maintained

This is the **single source of truth** for how the UltiCode wiki is organized.
Every page in `wiki/` follows the rules below. When the LLM ingests a new source,
answers a query, or runs a lint pass, it consults this file first.

> Read [`README.md`](README.md) for the 30-second orientation. This file is the
> operating manual.

## 1. The three layers

| Layer | What it is | Who writes it |
|-------|-----------|---------------|
| **Raw sources** | The codebase and authoritative instruction docs — `backend-spring/`, `console/`, `management/`, `shared/`, `init-db/migrations/`, `docker/`, `scripts/`, `infrastructure/`, `.github/workflows/`, plus `AGENTS.md`, `CLAUDE.md`, `.claude/rules/`. **Immutable.** The source of truth. | Humans |
| **The wiki** | This `wiki/` tree — LLM-generated markdown (entities, concepts, overviews, index, log). Explains, synthesizes, and cross-references the raw sources. | **LLM** (you) |
| **The schema** | This file (`SCHEMA.md`) + [`README.md`](README.md). Tells the LLM how the wiki is structured and what workflows to follow. Co-evolved by human + LLM. | Human + LLM |

The wiki **never duplicates** `AGENTS.md` / `CLAUDE.md` directives. Those are the
*command layer* (what to do); the wiki is the *knowledge layer* (how things work
and why). When a page needs an operational fact that lives in `AGENTS.md`, it
links out: "see `AGENTS.md` § Development Startup" — it does not copy the steps.

Raw sources are **implicit**: there is no `wiki/.raw/` mirror. Pages cite sources
by repo-relative path literal (e.g. ``backend-spring/.../modules/submission/``).

## 2. The three operations

### Ingest
A new source arrives (a new module, a migration, an ops runbook, a design doc).
1. Read the source and discuss key takeaways.
2. Create or update the relevant `entities/`, `concepts/`, `overview/` pages.
3. Add a `sources:` entry pointing at the real repo path on every page touched.
4. Update [`index.md`](index.md) — one line per page.
5. Append an entry to [`log.md`](log.md) with the greppable prefix.

A single ingest routinely touches 10–15 pages (the new entity + every concept and
overview that should now mention it).

### Query
A question is asked against the wiki.
1. Read [`index.md`](index.md) to locate relevant pages.
2. Drill into those pages; follow `[[wikilinks]]` as needed.
3. Synthesize an answer **with citations** (page name + source path).
4. If the answer is valuable and reusable, **file it back** as a new concept or
   overview page — explorations should compound, not vanish into chat history.

### Lint
A periodic health check. Look for:
- **Orphan pages** — no inbound `[[wikilinks]]` (run the grep in §7).
- **Dead links** — `[[x]]` targets that resolve to no file.
- **Stale claims** — statements a newer source has superseded.
- **Missing cross-references** — two pages that should link but don't.
- **Missing pages** — an important concept mentioned everywhere but with no page.
- **Source drift** — a `sources:` path that no longer exists in the repo.
- **Stale frontmatter dates** — a page whose `updated:` predates its last real edit; run `scripts/dev/wiki-manifest.sh --check` (see §12).

## 3. Directory layout

```
wiki/
├── README.md          # landing / orientation
├── SCHEMA.md          # this file
├── index.md           # content catalog (one line per page, by type)
├── log.md             # append-only maintenance timeline
├── overview/          # synthesis — whole-system viewpoint (architecture, pipelines)
├── entities/          # domain objects / backend modules
├── concepts/          # cross-cutting design ideas, decisions, patterns
├── templates/         # Obsidian templates (entity / concept / overview / daily-note)
├── daily-notes/       # one-file-per-day ingest journal (auto-created by Daily Notes core plugin)
└── .meta/             # generated provenance manifest (see §12); NOT a content layer
```

Only three content types: `overview`, `entity`, `concept`. There is deliberately
**no** `decisions/`, `codemap/`, `ops/`, or `theme/` directory — an ADR folds into
`concepts/`, an architecture mirror or ops deep-dive folds into `overview/`.

## 4. Page types

| Type | Folder | Answers | Length |
|------|--------|---------|--------|
| **entity** | `entities/` | "What is X and how does the X module work?" | ~80–150 lines |
| **concept** | `concepts/` | "Why did we decide X / what pattern is X?" | ~60–120 lines |
| **overview** | `overview/` | "How do these pieces fit together end-to-end?" | ~120–200 lines |

### entity page shape
- One-line essence.
- **Responsibility** — what the module owns.
- **Key tables / entities** — table name → purpose (from `@TableName`).
- **Key flows** — the main request paths, step by step.
- **Source files** — the repo paths that define it.
- **Cross-links** — `[[...]]` to related entities, concepts, overviews.
- **Gotchas** — non-obvious traps.

### concept page shape
- **The problem** — what tension this resolves.
- **The decision** — what was chosen.
- **Why** — the reasoning.
- **Where it lives** — source paths (code + migration).
- **Trade-offs** — what was rejected and why.
- **Related** — entities/concepts it governs.

### overview page shape
- **Scope map** — ASCII diagram or table of the territory.
- **How pieces connect** — the end-to-end flow.
- **Entry points** — where to start reading the code.
- **Links out** — into every relevant entity/concept.

## 5. Frontmatter (every content page)

```yaml
---
title: Submission              # human title
type: entity                   # entity | concept | overview | index | log | schema | daily
tags: [judging, core, type/entity]   # lowercase, broad→specific; FIRST slot = type-tag
status: living                 # living | stub | deprecated
updated: 2026-06-21            # ISO date of last material edit
sources:                       # repo-relative paths this page distills
  - backend-spring/src/main/java/com/ulticode/modules/submission/
  - init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql
aliases: []                    # alternate names or 中文别名 for search
---
```

- `type` — one of `entity | concept | overview | index | log | schema | daily`.
- `tags` MUST include exactly one `type/<x>` tag mirroring `type:` —
  `type/entity`, `type/concept`, `type/overview`, `type/daily`.
  The tag is what Obsidian Bases, Dataview, and tag-pane filters key off.
  The Templates core plugin auto-injects it for new pages that copy
  `templates/entity.md`, `templates/concept.md`, etc.
- `status: stub` = page exists as a placeholder, not yet distilled. Lint flags stubs.
- `sources:` must point at **real** paths. Cite a directory for a module, a file
  for a specific decision/migration.
- Keep topic `tags` small (2–4). They power future Dataview queries.

## 6. Naming & linking

- **Filenames**: kebab-case, the page slug. `entities/judge-queue.md`.
- **Wikilinks for in-wiki navigation**: ``[[entities/submission|Submission]]``
  (path `|` display text). Path-less ``[[Submission]]`` is allowed but ambiguous
  when slugs differ from titles — prefer the explicit path form. These power the
  Obsidian graph view. Link **liberally**; a dangling ``[[x]]`` is a stub-marker,
  not an error.
- **Repo paths are literals, never links**: ``backend-spring/.../modules/submission/``
  in backticks. They must not become `[...](...)` links.
- **External/doc references**: prose like "see `AGENTS.md` § Development Startup".

## 7. index.md & log.md

**`index.md`** — content catalog, grouped by type, one line per page:
```
- [Submission](entities/submission.md) — judging state machine, generation fence, lease
```

**`log.md`** — append-only timeline. Every entry starts with a greppable prefix:
```
## [2026-06-21] bootstrap | Initial wiki scaffold (39 pages)
```
`grep "^## \[" log.md | tail -5` → last 5 events. Entry types: `bootstrap`,
`ingest`, `query-filed`, `lint`, `fix`.

## 8. Lint commands (read-only)

```bash
# All wikilink targets
grep -rhoE '\[\[[^]|]+' wiki/ | sed 's/\[\[//' | sort -u
# Pages with no inbound links (orphans) — diff outbound targets against file list
comm -23 <(find docs -name '*.md' | sed 's#wiki/##; s#\.md##' | sort) \
        <(grep -rhoE '\[\[[^]|]+' wiki/ | sed 's/\[\[//' | sort -u)
# Stale source paths — every sources: entry must exist
# (spot-check with: ls <path>)
```

## 9. Writing rules

- **English** for all prose. Code identifiers, paths, commands, and `@Annotations`
  stay verbatim.
- **No filler**. Every section earns its place. A 60-line page that is dense beats
  a 200-line page that is padded.
- **Cite sources** on every non-trivial claim. If a page states "submissions use a
  generation fence", `sources:` must include the migration that introduced it.
- **No duplication of the command layer**. `pm2`/`mvnw`/`pnpm dev` commands belong
  in `AGENTS.md`/`CLAUDE.md`; the wiki may *illustrate* with one but must not host
  the authoritative runbook.
- **Flag uncertainty**. If a claim could not be verified against source, mark it
  `(unverified)` rather than asserting.
- **Keep it current**. When a migration or refactor changes behavior, update the
  page the same turn and append to `log.md`.

## 10. Daily notes — human ingest journal

The Daily Notes core plugin auto-creates one file per day at
`daily-notes/YYYY-MM-DD.md`, hydrated from `templates/daily-note.md`. Use it
as the **human-scale companion** to `log.md`:

| Surface | Granularity | Audience | Format |
|---------|-------------|----------|--------|
| `log.md` | per operation (1 line) | grep, audits | `## [YYYY-MM-DD] ingest | ...` |
| `daily-notes/YYYY-MM-DD.md` | per day (paragraphs) | human reflection | template: Ingested / Created / Queries / Lint / Notes |

A daily note is **not** a wiki page — it carries `type: daily` and
`type/daily` tag so Bases / Dataview can filter it out of "real" content lists.

**When to write**:
- After every `claude-obsidian:wiki-ingest` run (one bullet per source under "Ingested").
- After every `claude-obsidian:wiki-lint` run (findings under "Lint findings").
- After every query that gets filed back as a page (under "Queries filed").

**Why both?** `log.md` is greppable and survives compaction; the daily note
preserves *why* a decision was made, not just *what* changed.

## 11. Graph view coloring — manual UI setup (REQUIRED ONCE PER MACHINE)

### Why this section exists

Graph node colors come from Obsidian's `.obsidian/graph.json` →
`colorGroups[]`. **Each Obsidian instance owns its own copy of that file** —
the app rewrites it on open, normalizing anything an external tool wrote.
A LLM (or any non-Obsidian editor) can write the perfect 3 rules into
`graph.json`, and the next time the user opens Obsidian the file is reverted
to whatever the local UI last had (often empty). This is why this section
is **a procedure, not a snippet to paste**.

### Palette (canonical)

Three color groups keyed off the `type/<x>` tag from § 5:

| Tag | Hex | Visual | Meaning |
|-----|-----|--------|---------|
| `tag:#type/entity` | `#7c3aed` | violet | a single module / domain object |
| `tag:#type/concept` | `#0ea5e9` | blue | a decision, pattern, or invariant |
| `tag:#type/overview` | `#10b981` | green | a whole-system synthesis |

Pages without a `type/<x>` tag render in Obsidian's default gray — that's
the **lint signal** that a page was added without following the template.

### One-time setup (per machine that opens this vault)

The first time you open this vault on a new machine, the Graph view's
**Color groups** (颜色组) panel will show **only** a **新建颜色组**
(Create color group) button — no existing rules. That is **expected**.
The button is the only entry point; there is no JSON to copy, no plugin
to install, no CLI command. You add the rules one at a time:

1. Open the vault in Obsidian.
2. Click the **Graph view** icon in the left ribbon.
3. Click the ⚙️ gear in the top-right of the graph panel.
4. Scroll to **Color groups** (颜色组) → click the **新建颜色组**
   (Create color group) button. A row appears with a search box + a
   color swatch.
5. In the row, type `tag:#type/entity` in the search box and press Enter.
   Then click the color swatch → paste `7c3aed` → press Enter.
6. Click **新建颜色组** again for the second rule:
   `tag:#type/concept` → color `0ea5e9`.
7. Click **新建颜色组** once more for the third rule:
   `tag:#type/overview` → color `10b981`.
8. Close the settings panel. Obsidian writes the rules to
   `.obsidian/graph.json` automatically. **Do not edit that file
   externally**; Obsidian will normalize it on next open.

Summary of the three rules to enter:

| Query (type into the search box) | Color (paste into swatch) |
|---|---|
| `tag:#type/entity` | `7c3aed` (violet) |
| `tag:#type/concept` | `0ea5e9` (blue) |
| `tag:#type/overview` | `10b981` (green) |

### Verification

After setup, the graph should show:
- **25 violet nodes** (entity modules)
- **12 blue nodes** (concepts)
- **7 green nodes** (overviews)
- **4 gray meta nodes** (README, SCHEMA, index, log — intentionally untagged
  for the wiki core, see § 5)
- **1 blue-gray daily node** (`daily-notes/2026-06-21.md`, tagged
  `type/daily` but not in the 3 color groups — falls through to gray)

If colors don't appear: Settings → Community plugins → make sure the
**Graph** core plugin is enabled, then repeat the setup. The
**Dataview** community plugin (already installed) does not affect graph
coloring.

### Alternative: programmatic coloring without `graph.json`

If a fully scriptable pipeline is preferred (e.g., for CI / headless
generation), use one of:

- **Obsidian Bases** — create a `.base` file filtering by `type-tag` and
  render with the Graph view's "open in Bases" action (Obsidian 1.7+).
- **Juggl** community plugin — replaces Graph view with a Cytoscape-based
  renderer that supports node shapes per type and per-property color
  mapping via CSS-like rules.
- **Excalibrain** community plugin — tree-style hierarchical view colored
  by frontmatter property.

All three read the same `#type/<x>` tag convention from § 5, so adopting
this section now keeps the door open for any of them later.

## 12. Page versioning & manifest (`wiki/.meta/manifest.json`)

Every content page carries a hand-maintained `updated:` date. That date is a
**human semantic signal** ("when the meaning last changed") and it drifts: a
page gets a typo fix or a cross-link in a later commit and no one bumps the
date. `wiki/.meta/manifest.json` is the **machine-traceable** companion — for
every page it records the last git commit that touched it, so audits and lint
can answer *"which commit last modified this page, and by whom?"*.

### What it stores

```jsonc
{
  "$schema": "wiki-manifest-v1",
  "generated_with_head": "<full HEAD sha>",              // determinism anchor (no timestamp)
  "stats": { "pages": 54, "by_type": { "entity": 26, "concept": 14, "overview": 8, ... } },
  "pages": [
    {
      "path": "wiki/entities/submission.md",
      "type": "entity", "title": "Submission", "status": "living",
      "frontmatter_updated": "2026-06-21",               // echoed from frontmatter
      "last_commit": {                                    // `git log -1 -- <path>`
        "sha": "0dc3c0e2d...", "short": "0dc3c0e2d",
        "committed_at": "2026-06-21T17:47:08+08:00",
        "author": "DavidHLP",
        "subject": "docs: restructure wiki ..."
      },
      "body_sha256": "<sha256 of body, LF-normalized>"
    }
  ]
}
```

`last_commit.sha` is the page's **version identifier** — the git provenance this
manifest exists to surface. `body_sha256` (over the body, frontmatter stripped,
line endings normalized) detects content change independent of commit history.

### Generate / lint

```bash
scripts/dev/wiki-manifest.sh             # regenerate manifest from git history
scripts/dev/wiki-manifest.sh --check     # lint; exit 1 on any finding
```

The manifest is a **derived, deterministic** artifact: at a given HEAD it
reproduces byte-for-byte. Commit it alongside content changes so the whole
team shares one provenance view.

### Lint signals (`--check`)

| Signal | Meaning |
|--------|---------|
| `[stale-fm]` | `frontmatter_updated` is behind the page's `last_commit` date — bump the frontmatter date (§ 9 "keep it current"). |
| `[unregistered]` | a wiki `.md` exists that the manifest doesn't list — regenerate. |
| `[stale-entry]` | the manifest lists a file that no longer exists — regenerate. |
| `[drift]` | a page's body hash or last-commit changed since the manifest was recorded — regenerate. |
| `[head]` | HEAD moved since the manifest's anchor — regenerate. |

### Workflow

After editing any wiki page, **regenerate the manifest in the same change**:
edit pages → `scripts/dev/wiki-manifest.sh` → `git add wiki/.meta/manifest.json`
→ commit. A `--check` gate flags any content change shipped without a matching
manifest refresh.
