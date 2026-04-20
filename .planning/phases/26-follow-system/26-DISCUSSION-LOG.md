# Phase 26: Follow System - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-21
**Phase:** 26-follow-system
**Areas discussed:** Follow relationship storage, Pagination approach, Achievement trigger mechanism, Duplicate follow handling

---

## Follow relationship storage

| Option | Description | Selected |
|--------|-------------|----------|
| New `user_follows` table | Separate table with composite key (follower_id, following_id) | ✓ |
| Extend existing user table | Add follower_count column to users table | |

**User's choice:** New `user_follows` table (auto-selected: recommended default)
**Notes:** Most flexible, cleanest separation of concerns. MyBatis-Plus pattern follows existing entities.

---

## Pagination approach

| Option | Description | Selected |
|--------|-------------|----------|
| MyBatis-Plus `Page<T>` offset | page + size params, returns total count | ✓ |
| Cursor-based pagination | cursor + limit, more efficient for large datasets | |

**User's choice:** MyBatis-Plus Page offset pagination (auto-selected: recommended default)
**Notes:** Consistent with existing codebase patterns.

---

## Achievement trigger mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Async via `@Async` | Non-blocking, follows the requirement of calling onFollowCountUpdated() | ✓ |
| Synchronous | Blocks follow response until achievement check completes | |

**User's choice:** Async via @Async (auto-selected: recommended default)
**Notes:** Prevents blocking the follow response while achievement check runs.

---

## Duplicate follow handling

| Option | Description | Selected |
|--------|-------------|----------|
| Idempotent | Return success without error if already following | ✓ |
| Error on duplicate | Throw exception if user tries to follow again | |

**User's choice:** Idempotent (auto-selected: recommended default)
**Notes:** Per REQUIREMENTS.md: "Duplicate follows are idempotent (no error)".

---

## Claude's Discretion

All four areas auto-resolved with recommended defaults in --auto mode.

## Deferred Ideas

- FOLLOW-03: Follow Button State — Phase 29 (frontend)
- Profile view/edit — Phase 27
- Achievement display — Phase 28
