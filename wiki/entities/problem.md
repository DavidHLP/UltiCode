---
title: Problem
type: entity
tags: [problem, core]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/problem/
  - init-db/migrations/V20260610120000__Create_Test_Cases_Table.sql
  - init-db/migrations/V20260616120000__Add_Problem_Resource_Limits.sql
aliases: [题目]
---

# Problem

The unit of content users solve. A problem is more than a statement — it carries
multi-language metadata, examples, tags, version history, per-user notes, and the
test cases the judge runs against. Supports **versioning** with diffs.

> Judged by: [[entities/submission]]. Listed in: [[entities/problemlist]].

## Responsibility

Owns problem CRUD (public + admin), test-case management, tag taxonomy, language
allow-list, **version history with diffs**, and per-user notes.

## Key tables

| Table | Purpose |
|-------|---------|
| `problems` | title, difficulty, status, resource limits |
| `problem_details` | statement (Markdown), I/O format |
| `problem_examples` | sample I/O shown to users |
| `problem_languages` | allowed submission languages |
| `problem_tags` / `problem_tag_relations` | tag taxonomy + join |
| `problem_versions` | versioned snapshots for diff/restore |
| `problem_notes` | per-user private notes |
| `test_cases` | judge cases (sample/full, `is_deleted`) |

## Key flows

- **Public read**: `ProblemController` → `/problems` (list/detail, filtered),
  returns `ProblemDetailPublicVO`.
- **Admin authoring**: `/admin/problems` (create/update), `ProblemDetailAdminVO`;
  test cases at `/admin/problems/{id}/test-cases`.
- **Versioning**: `ProblemVersionServiceImpl` snapshots a problem; `VersionDiffVO`
  / `VersionWithDiffVO` render diffs; `AdminProblemVersionController` exposes
  `/admin/problems` version routes.
- **Notes**: `ProblemNoteController` → `/problems/{id}/note` (per-user).

## Controllers

- `ProblemController` → `/problems`
- `AdminProblemVersionController` → `/admin/problems` (version surface)
- `ProblemNoteController` → `/problems/{problemId}/note`

## Migrations

- `V20260610120000__Create_Test_Cases_Table` + `V…Add_Test_Cases_Is_Deleted` —
  test cases split out, soft-deletable.
- `V20260616120000__Add_Problem_Resource_Limits` — time/memory limits per problem.
- `V20260615140000__Seed_Problem_Category_Tags` — tag seed.

## Source files

- `backend-spring/.../modules/problem/` (controller, service/impl, entity, dto, vo, mapper).
- `backend-spring/.../modules/problem/service/impl/ProblemVersionServiceImpl.java`.

## Cross-links

- [[entities/submission]] · [[entities/problemlist]] · [[entities/solution]]
- [[concepts/module-layering]]
- [[overview/backend-modules-overview]]

## Gotchas

- Test cases are soft-deleted (`is_deleted`); judge query must exclude them.
- `problem_versions` diffs are computed server-side (`VersionDiffVO`) — don't
  reconstruct from raw rows on the client.
- Resource limits (`time_limit`/`memory_limit`) feed the [[entities/sandbox]];
  changing them doesn't retroactively re-judge past submissions.
