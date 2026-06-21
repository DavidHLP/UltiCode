---
title: SCHEMA — Wiki Convention
type: schema
tags: [meta, convention]
status: living
updated: 2026-06-21
sources:
  - AGENTS.md
  - CLAUDE.md
---

# SCHEMA — How this wiki is structured and maintained

This is the **single source of truth** for how the UltiCode wiki is organized.
Every page in `docs/` follows the rules below. When the LLM ingests a new source,
answers a query, or runs a lint pass, it consults this file first.

> Read [`README.md`](README.md) for the 30-second orientation. This file is the
> operating manual.

## 1. The three layers

| Layer | What it is | Who writes it |
|-------|-----------|---------------|
| **Raw sources** | The codebase and authoritative instruction docs — `backend-spring/`, `console/`, `management/`, `shared/`, `init-db/migrations/`, `docker/`, `scripts/`, `infrastructure/`, `.github/workflows/`, plus `AGENTS.md`, `CLAUDE.md`, `.claude/rules/`. **Immutable.** The source of truth. | Humans |
| **The wiki** | This `docs/` tree — LLM-generated markdown (entities, concepts, overviews, index, log). Explains, synthesizes, and cross-references the raw sources. | **LLM** (you) |
| **The schema** | This file (`SCHEMA.md`) + [`README.md`](README.md). Tells the LLM how the wiki is structured and what workflows to follow. Co-evolved by human + LLM. | Human + LLM |

The wiki **never duplicates** `AGENTS.md` / `CLAUDE.md` directives. Those are the
*command layer* (what to do); the wiki is the *knowledge layer* (how things work
and why). When a page needs an operational fact that lives in `AGENTS.md`, it
links out: "see `AGENTS.md` § Development Startup" — it does not copy the steps.

Raw sources are **implicit**: there is no `docs/.raw/` mirror. Pages cite sources
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

## 3. Directory layout

```
docs/
├── README.md          # landing / orientation
├── SCHEMA.md          # this file
├── index.md           # content catalog (one line per page, by type)
├── log.md             # append-only maintenance timeline
├── overview/          # synthesis — whole-system viewpoint (architecture, pipelines)
├── entities/          # domain objects / backend modules
└── concepts/          # cross-cutting design ideas, decisions, patterns
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
type: entity                   # entity | concept | overview | index | log | schema
tags: [judging, core]          # lowercase, broad→specific
status: living                 # living | stub | deprecated
updated: 2026-06-21            # ISO date of last material edit
sources:                       # repo-relative paths this page distills
  - backend-spring/src/main/java/com/ulticode/modules/submission/
  - init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql
aliases: []                    # alternate names or 中文别名 for search
---
```

- `status: stub` = page exists as a placeholder, not yet distilled. Lint flags stubs.
- `sources:` must point at **real** paths. Cite a directory for a module, a file
  for a specific decision/migration.
- Keep `tags` small (2–4). They power future Dataview queries.

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
grep -rhoE '\[\[[^]|]+' docs/ | sed 's/\[\[//' | sort -u
# Pages with no inbound links (orphans) — diff outbound targets against file list
comm -23 <(find docs -name '*.md' | sed 's#docs/##; s#\.md##' | sort) \
        <(grep -rhoE '\[\[[^]|]+' docs/ | sed 's/\[\[//' | sort -u)
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
