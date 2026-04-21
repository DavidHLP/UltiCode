# Phase 32: Contest Reminder Trigger - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-21
**Phase:** 32-contest-reminder-trigger
**Areas discussed:** Scheduling strategy, Reminder notification title format, Duplicate reminder prevention strategy

---

## Scheduling Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| New @Scheduled method in ContestScheduler | Add @Scheduled method that polls for upcoming contests with registered users and sends reminders at T-24h and T-1h. Reuses existing scheduler infrastructure. | ✓ |
| Spring Event listener | Use Spring's @EventListener on ContestStatusEvent. ContestScheduler emits events when status changes — listen for UPCOMING contests and schedule reminder tasks. | |

**User's choice:** New @Scheduled method in ContestScheduler
**Notes:** --auto mode selected recommended option.

---

## Reminder Notification Title Format

| Option | Description | Selected |
|--------|-------------|----------|
| Time-specific titles | T-24h: "Contest '{title}' starts in 24 hours"; T-1h: "Contest '{title}' starts in 1 hour" — simple, clear, no body needed | ✓ |
| Natural language | 24h: "Reminder: {title} starts tomorrow"; 1h: "Starting soon: {title}" — more conversational | |

**User's choice:** Time-specific titles
**Notes:** --auto mode selected recommended option.

---

## Duplicate Reminder Prevention Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Deduplicate via NotificationService | Track sent reminders in notification metadata or DB — avoids duplicate reminders for same contest/user | ✓ |
| Participant status fields | Add fields to ContestParticipant: reminder_sent_24h, reminder_sent_1h — explicit and queryable | |

**User's choice:** Deduplicate via NotificationService
**Notes:** --auto mode selected recommended option.

---

## Claude's Discretion

All gray areas were auto-resolved with recommended defaults in --auto mode.

---

## Deferred Ideas

None — discussion stayed within phase scope.
