---
title: Forum
type: entity
tags: [forum, community, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/forum/
  - init-db/migrations/V202603_120500__Fix_Forum_User_References.sql
aliases: [论坛]
---

# Forum

Community discussion: posts, comments, communities (subforum groups), tags, and a
denormalized forum-user profile. All user-generated content flows through
[[entities/moderation]] and must be sanitized before any `v-html` render.

## Responsibility

Owns threaded discussion — posts, comments, communities + membership, tag
taxonomy, and the forum-specific user profile row.

## Key tables

| Table | Purpose |
|-------|---------|
| `forum_posts` | top-level posts (Markdown body, `autoResultMap`) |
| `forum_comments` | replies |
| `forum_communities` | subforum groups |
| `forum_community_members` | membership |
| `forum_tags` | tag taxonomy |
| `forum_users` | denormalized forum-side user profile |

## Key flows

- **Post**: create `forum_posts` (Markdown) → tags via `forum_tags` → visible
  after moderation rules.
- **Comment**: nested under a post.
- **Community**: joinable groups with membership.
- **UGC safety**: body is Markdown/KaTeX — must be sanitized before render (see
  `AGENTS.md` § Security Invariants); reports flow to [[entities/moderation]].

## Controllers

- `ForumController` → `/forum`.
- `AdminForumController` → `/admin/forum` (admin-side management).

## Migrations

- `V202603_120500__Fix_Forum_User_References` — corrected user FK references.
- `V202603_120700__Seed_Forum_Posts_Per_User`, `V202603_120800__Seed_Comments_And_Interactions` — seed.

## Source files

- `backend-spring/.../modules/forum/` (controller, service/impl, entity, dto, mapper).

## Cross-links

- [[entities/moderation]] · [[entities/user]]
- [[overview/backend-modules-overview]]

## Gotchas

- `forum_posts` uses `autoResultMap = true` (JSON column handling) — mapper
  config must preserve it.
- Likes/saves on forum content go through `edge_operations`, not a forum-local
  table — see [[overview/backend-modules-overview]] (vote/edgeoperations row).
- Never render forum Markdown via `v-html` without sanitization — XSS invariant.
