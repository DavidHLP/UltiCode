---
title: Solution
type: entity
tags: [solution, community]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/solution/
  - init-db/migrations/V20260611140000__Create_Solution_Topics_Table.sql
aliases: [题解]
---

# Solution

Community-authored writeups for a problem — Markdown solutions, threaded comments,
and topic-based grouping. Like [[entities/forum]], content is UGC and must be
sanitized before render; reports route to [[entities/moderation]].

## Key tables

- `solutions` — the writeup (problem ref, author, body, status).
- `solution_comments` — threaded discussion.
- `solution_topics` — topic grouping (`V20260611140000`).

## Controllers

- `SolutionController` → `/solutions`; `AdminSolutionController` → `/admin/solutions`.

## Flow

Author writes → (optional moderation gate) → published → readers comment →
tagged by topic. Soft-delete + audit like other UGC modules.

## Source files

- `backend-spring/.../modules/solution/` (controller, service/impl, entity, dto, mapper).

## Cross-links

- [[entities/problem]] · [[entities/forum]] · [[entities/moderation]]
- [[concepts/security-invariants]]

## Gotchas

- Solution Markdown → sanitize before any `v-html` (XSS invariant).
- A solution may be tied to a problem version; verify the ref still resolves.
