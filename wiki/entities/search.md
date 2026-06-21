---
title: Search
type: entity
tags: [search, platform, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/search/
  - backend-spring/src/main/java/com/ulticode/modules/search/controller/SearchController.java
  - backend-spring/src/main/java/com/ulticode/modules/search/config/MeiliSearchConfig.java
aliases: [全文检索]
---

# Search

Cross-index full-text search — single `/search` endpoint that fans out to
**MeiliSearch** indices (problems, users, posts, solutions). MeiliSearch is
**opt-in** at boot via `meilisearch.enabled=true`; when disabled the bean is
absent and the controller returns no hits (or the service short-circuits with
an empty result).

## Responsibility

A thin, read-only surface that delegates to a MeiliSearch client. The module
holds *no* persistence of its own — there is no MySQL `search_*` table. Indexed
content is owned by the source module ([[entities/problem]], [[entities/user]],
[[entities/forum]], [[entities/solution]]); this module only queries.

## Key tables

None. MeiliSearch is the source of truth at query time. Index contents are
maintained by the owning modules via sync jobs (out of scope for the read
path).

## Controllers

- `SearchController` → `/search` (single `GET /search` accepting
  `SearchQueryDTO` — query string, `index` filter, page, page size, optional
  filters).

## Configuration

`MeiliSearchConfig` is conditional on `meilisearch.enabled=true`. When the
property is missing/false, the `Client` bean is not created, and the search
service must fall back gracefully (no NPE).

```yaml
meilisearch:
  enabled: true
  host: http://localhost:7700
  api-key: ${MEILI_MASTER_KEY:}
```

## Flow

client `GET /search?q=...` → `SearchController` → `SearchService.search` →
`Client` (MeiliSearch) → typed `SearchResponseVO` (hits grouped by index type,
total counts, page metadata).

## Source files

- `backend-spring/.../modules/search/` (controller, service + impl, dto,
  `config/MeiliSearchConfig.java`).

## Cross-links

- [[entities/problem]] · [[entities/user]] · [[entities/forum]] · [[entities/solution]]
- [[overview/backend-modules-overview]]

## Gotchas

- The module is **conditionally wired** — when MeiliSearch is disabled, the
  bean is absent. Any code that injects the `Client` directly will fail
  application startup; always go through `SearchService`.
- There is no DB-side fallback. If MeiliSearch is unreachable, the search
  endpoint returns an empty result (or 5xx depending on the wrapper); the
  primary SQL DB is **not** queried as a fallback.
- `SearchIndexType` enum is the allow-list for which indices a query may
  target — adding a new index requires both a new enum value and a sync job
  in the owning module.
