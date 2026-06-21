---
title: Interactions
type: entity
tags: [bookmark, vote, edgeoperations, interactions]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/bookmark/
  - backend-spring/src/main/java/com/ulticode/modules/vote/
  - backend-spring/src/main/java/com/ulticode/modules/edgeoperations/
  - backend-spring/src/main/java/com/ulticode/modules/bookmark/controller/BookmarkController.java
  - backend-spring/src/main/java/com/ulticode/modules/edgeoperations/controller/EdgeOperationsController.java
  - backend-spring/src/main/java/com/ulticode/modules/follow/controller/FollowController.java
aliases: [收藏, 点赞]
---

# Interactions

User-to-content lightweight actions: **bookmarks** (folders + items) and
**votes** (likes/dislikes/favorites via the edge-operations table). Both modules
are thin CRUD layers over a single MySQL table each; together they cover "the
things a user can do to a problem/solution/post besides submitting a
solution."

This page merges three Java modules — `bookmark`, `vote`, and
`edgeoperations` — because their responsibilities are tightly coupled at the
API level (a problem's "interactions" widget hits all three).

## bookmark module

Folders of saved items. Curated, named, reorderable; "quick favorite" is a
shortcut that puts the item in the default folder.

### Key tables

| Table | Purpose |
|-------|---------|
| `collections` | user-owned folders (`BookmarkFolder` entity) |
| `collection_items` | items inside a folder (`Bookmark` entity — note: the entity name is `Bookmark` but the table is `collection_items`, mapped via `@TableField("collection_id")` etc.) |

### Controllers

`BookmarkController` → `/bookmarks`: `/quick`, `/folders`, `/folders/{id}`,
`/folders/{folderId}/items/{bookmarkId}` (CRUD + reorder). Most write
endpoints are `@RateLimit(20/60s)`.

## vote / edgeoperations modules

Likes, dislikes, and "favorites" (a vote-style save, distinct from
`Bookmark`). The HTTP surface lives in `edgeoperations`; the `vote` module
owns the data + enums. A vote operation is **toggle-style** — repeat calls
flip the state rather than inserting duplicates.

### Key tables

| Table | Purpose |
|-------|---------|
| `edge_operations` | one row per (user, target, operation); operation enum covers `VOTE_UP` / `VOTE_DOWN` / `FAVORITE` / `VIEW` / `ANALYZE` |

`EdgeOperationTargetType` is the allow-list of target kinds
(problem, solution, post, …); the same enum is reused by the bookmark target
type, so the two modules agree on what a "thing" is.

### Controllers

`EdgeOperationsController` → `/edge-operations`:
- `POST /edge-operations` — perform an op (rate-limited, 20/60s). For
  `VOTE_UP` / `VOTE_DOWN`, delegates to the vote service for toggle logic.
  For other ops, creates or deletes idempotently.
- `GET /edge-operations/interactions?targetId=...&targetType=...` — like /
  dislike / favorite counts and the current user's state. Works for
  anonymous users.
- `GET /edge-operations/{targetType}/{targetId}` — same data, path-style.

## Flow

client renders a problem page → `GET /edge-operations/PROBLEM/{id}` →
counts + caller's vote state → user clicks vote → `POST /edge-operations` →
`EdgeOperationsService.performOperation` → toggle / create / delete →
returned counts refresh the widget.

For bookmarking, the flow is `POST /bookmarks/quick` (default folder) or
`POST /bookmarks/folders/{folderId}/items` (named folder).

## Why these are merged

Each module is small, the same widget hits all three, and the data model
shares `targetType` semantics. Splitting them across three entity pages
would repeat the same flow narrative three times.

## Source files

- `backend-spring/.../modules/bookmark/` (controller, service + impl,
  entity, dto, mapper, enums).
- `backend-spring/.../modules/edgeoperations/` (controller, service + impl,
  dto — reuses `vote.entity.enums`).
- `backend-spring/.../modules/vote/` (entity, enums, mapper — used by
  `EdgeOperationsService` for vote logic).

## Cross-links

- [[entities/problem]] · [[entities/solution]] · [[entities/forum]]
- [[overview/backend-modules-overview]]

## Gotchas

- `Bookmark` entity maps to the `collection_items` table via `@TableName`
  override + `@TableField("collection_id")` etc. The Java name is **not** the
  table name — beware when writing raw SQL.
- Votes are **toggle-style**: re-clicking the same op deletes the row.
  Counting "likes" therefore requires a `WHERE operation = VOTE_UP` filter, not
  a row count.
- Anonymous reads are supported on `GET /edge-operations/...` — the user
  identity is read but treated as null; do not require auth on that endpoint.
- The `bookmark` and `vote` modules both use `targetType` enums but they are
  **distinct** enums (`bookmark.entity.enums.BookmarkType` vs
  `vote.entity.enums.EdgeOperationTargetType`). Adding a new target kind
  needs both updated.
- `EdgeOperationsService.performOperation` is rate-limited at 20/60s — this
  applies to the *endpoint*, not per-target, so a flood of distinct targets
  still hits the limit.
