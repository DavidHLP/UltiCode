# Phase 28: Achievement Backend - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-21
**Phase:** 28-achievement-backend
**Areas discussed:** Async Event Wiring, Progress Endpoint Design, Missing Triggers, Category Filtering

---

## Async Event Wiring

| Option | Description | Selected |
|--------|-------------|----------|
| ApplicationEventPublisher + @Async @EventListener | Publish AchievementEarnedEvent → async listener dispatches to NotificationService | ✓ |

**Auto-selected:** Recommended default — decouples achievement awarding from WebSocket push.

---

## Progress Endpoint Design

| Option | Description | Selected |
|--------|-------------|----------|
| GET /users/me/achievements/progress | Returns unearned achievements with current count, percentage, next milestone | ✓ |

**Auto-selected:** Matches ACHV-02 requirement.

---

## Missing Achievement Triggers

| Option | Description | Selected |
|--------|-------------|----------|
| Add FIRST_PROBLEM, LANGUAGE_* types to enum | Extends AchievementType enum and trigger service | ✓ |

**Auto-selected:** Required for ACHV-01 completion.

---

## Category Filtering

| Option | Description | Selected |
|--------|-------------|----------|
| Validate against known categories: problems, contests, social, streaks, special | 400 on unknown category | ✓ |

**Auto-selected:** Standard REST validation pattern.

---

## Claude's Discretion

All decisions auto-resolved via --auto mode. No user choices required.

## Deferred Ideas

None — all ACHV requirements are in scope for Phase 28.
