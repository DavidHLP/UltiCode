---
title: Problem List
type: entity
tags: [problemlist, community, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/problemlist/
aliases: [题单]
---

# Problem List

User-curated collections of [[entities/problem|problems]] — a "playlist" with
ordering, categories, sharing, and bookmarking. Distinct from a contest (no
scoring/window).

## Key tables

- `problem_lists` — the list (owner, title, visibility).
- `problem_list_problem_relations` — ordered problem membership.
- `problem_list_categories` — taxonomy.
- `problem_list_bookmarks` — who saved it.

## Controllers

- `ProblemListController` → `/problem-lists`;
  `AdminProblemListController` → `/admin/problem-lists`.

## Flow

Author creates list → adds problems (ordered) → tags by category → sets
visibility → others bookmark/follow.

## Source files

- `backend-spring/.../modules/problemlist/` (controller, service/impl, entity, dto, mapper).

## Cross-links

- [[entities/problem]] · [bookmark module](../overview/backend-modules-overview.md) (table coverage)
- [[overview/backend-modules-overview]]

## Gotchas

- Ordering lives in the relation table; reordering updates position, not membership.
- Visibility is enforced server-side — never trust a client-hidden list as private.
