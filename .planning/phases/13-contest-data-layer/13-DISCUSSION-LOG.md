# Phase 13: Contest Data Layer - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 13-contest-data-layer
**Mode:** Auto (--auto flag)
**Areas discussed:** Contest Problem Scoring, Contest Submission Sync, Admin Lifecycle Validation, Announcement Real-time

---

## Contest Problem Scoring

| Option | Description | Selected |
|--------|-------------|----------|
| Default scores with optional override | Admin can accept defaults (100 base, 0 penalty) or set custom per problem | ✓ |
| Required admin input per problem | Force admin to specify score/penalty for every problem | |
| Uniform scoring | All problems get same score, no customization | |

**Auto-selected:** Default scores with optional override — flexible without forcing admin input. DB has score, penalty_per_wrong, base_score, time_bonus fields available for customization.

---

## Contest Submission Sync

| Option | Description | Selected |
|--------|-------------|----------|
| Same transaction as submission | Create both Submission and ContestSubmission in one @Transactional call | ✓ |
| Async via event | Publish event after submission, listener creates ContestSubmission | |
| Separate service call | Frontend calls contest submission endpoint separately | |

**Auto-selected:** Same transaction — ensures data consistency. ContestSubmission is a thin record linking submission to contest context.

---

## Admin Lifecycle Validation

| Option | Description | Selected |
|--------|-------------|----------|
| Validate — require problems to start, allow early stop | Must have ≥1 problem to start; allow stop before end_time | ✓ |
| No validation | Start/stop anytime regardless of state | |
| Strict validation | Full state machine with transition guards | |

**Auto-selected:** Validate — require problems to start, allow early stop. Safety without over-constraining admin workflow.

---

## Announcement Real-time

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, via existing emitAnnouncement() | Use existing WebSocket infrastructure to push new announcements | ✓ |
| No, polling only | Frontend polls for announcements on interval | |

**Auto-selected:** Yes — RealtimeService.emitAnnouncement() already exists, just needs REST endpoint to trigger it.

---

## Claude's Discretion

- Exact DTO/VO class structure for new entities
- Validation annotation details
- Error message wording and exception types
- Batch vs one-by-one operations for contest problems
- Unit test structure and mock boundaries
- Transaction boundary details

## Deferred Ideas

None — discussion stayed within phase scope.
