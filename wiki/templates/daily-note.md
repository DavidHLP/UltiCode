---
title: "{{date:YYYY-MM-DD}}"
type: daily
tags: [daily, ingest]
status: living
updated: {{date:YYYY-MM-DD}}
sources: []
---

# {{date:YYYY-MM-DD}} — Ingest journal

> One log per day. Used by `claude-obsidian:wiki-ingest` and `claude-obsidian:wiki-lint`
> to record what was added / changed / queried on this date.

## Ingested

<!-- For each source ingested today: file → pages touched → greppable log summary -->

- `path/to/source` → [[new-page]] · updated: [[existing-page]]
- _none_

## Created / updated pages

<!-- Mirror of log.md but at human scale: what changed in the vault -->

- `entities/foo.md` (created) — one-line gist
- `concepts/bar.md` (updated) — what changed
- _none_

## Queries filed

<!-- Reusable answers that should become a concept / overview page -->

- Q: ... → filed as `concepts/...`
- _none_

## Lint findings

<!-- Output of `claude-obsidian:wiki-lint`: orphans, dead links, stale sources -->

- _none_

## Notes

<!-- Free-form: design questions, traps, "should we ...", half-thoughts -->

-
