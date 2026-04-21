# Phase 31: Follow Notification Trigger - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-21
**Phase:** 31-follow-notification-trigger
**Areas discussed:** Trigger location, Notification content, Idempotency & async

---

## Trigger Location

| Option | Description | Selected |
|--------|-------------|----------|
| FollowServiceImpl.follow() | Add notification call inside follow method, after idempotent insert | ✓ |
| @Async listener on follow event | Publish domain event, async listener creates notification | |
| Inside triggerFollowerAchievement | Reuse existing @Async achievement trigger chain | |

**User's choice:** FollowServiceImpl.follow() — synchronous, inside the follow method
**Notes:** Keep follow action responsive; async achievement trigger already handles achievement notifications separately

---

## Notification Content

| Option | Description | Selected |
|--------|-------------|----------|
| type=FOLLOW, category=social | FOLLOW enum + social category | ✓ |
| type=FOLLOW, category=user | Alternative category | |
| type=SYSTEM, category=social | Use SYSTEM type instead | |

**User's choice:** type=FOLLOW, category=social
**Notes:** Consistent with NotificationType enum; social category matches Phase 28 achievement categories

---

## Notification Title & Body

| Option | Description | Selected |
|--------|-------------|----------|
| title="{username} followed you", body="" | Simple, self-explanatory | ✓ |
| title="New follower", body="{username}" | More explicit body | |
| title="{username} started following you" | Longer, more complete | |

**User's choice:** title="{username} followed you", body=""
**Notes:** Concise; body empty as title is self-explanatory

---

## Idempotency

| Option | Description | Selected |
|--------|-------------|----------|
| Notify only on first follow | Check if insert actually happened | ✓ |
| Notify every follow action | Send notification each time | |

**User's choice:** Notify only on first follow
**Notes:** Re-following should not spam notifications

---

## Async vs Sync Notification

| Option | Description | Selected |
|--------|-------------|----------|
| Sync inside follow() | Notification persisted before response returned | ✓ |
| @Async listener | Fire-and-forget async | |

**User's choice:** Sync inside follow()
**Notes:** Consistent with Phase 30's pattern where createNotification() handles both persist and push

---

## Error Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Log and continue | Follow succeeds even if notification fails | ✓ |
| Throw and roll back | Follow fails if notification fails | |

**User's choice:** Log and continue
**Notes:** Consistent with Phase 30 D-11; notification is secondary to core follow action

---

## Claude's Discretion

- Exact metadata fields (followerUsername, followerAvatar confirmed)
- Notification link format (/profile/{followerUsername} confirmed)

## Deferred Ideas

None — discussion stayed within phase scope.
