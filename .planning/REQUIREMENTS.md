# Requirements: UltiCode v1.7 Notifications

**Defined:** 2026-04-21
**Core Value:** 平台安全性、功能完整性和交付自动化

## v1 Requirements

### Notification Core

- [ ] **NOTIF-01**: User receives real-time in-app notification via WebSocket when a notification is created
- [ ] **NOTIF-02**: User receives real-time WebSocket push when an achievement is earned (fix AchievementNotificationListener)

### Notification Triggers

- [ ] **NOTIF-03**: User receives notification when someone follows them (follow trigger wired to notification service)
- [ ] **NOTIF-04**: User receives notification when a contest they registered for starts soon (24h and 1h reminder)
- [ ] **NOTIF-05**: User receives notification when their submission is judged (AC/WA/TLE/etc.)

## v2 Requirements

### Notification Preferences

- **NOTIF-06**: User can configure notification preferences per category (followers, achievements, contests, submissions)
- **NOTIF-07**: User can receive email notifications for important categories

### Advanced Features

- **NOTIF-08**: User can view notification history with pagination
- **NOTIF-09**: User can mark individual notifications as read
- **NOTIF-10**: User can mark all notifications as read

## Out of Scope

| Feature | Reason |
|---------|--------|
| Push notification preferences UI | Competitive differentiator — defer to v1.x |
| Email notification integration | High complexity — needs template design and unsubscribe flow |
| Notification grouping/aggregation | UI polish — not table stakes |
| PWA push notifications | Web-first — not needed for v1 |
| Notification deduplication keys | Database schema change — address when scale demands |
| Redis-cached unread counts | Premature optimization — MySQL count is fine for current scale |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| NOTIF-01 | Phase 30 | Pending |
| NOTIF-02 | Phase 30 | Pending |
| NOTIF-03 | Phase 31 | Pending |
| NOTIF-04 | Phase 32 | Pending |
| NOTIF-05 | Phase 33 | Pending |

**Coverage:**
- v1 requirements: 5 total
- Mapped to phases: 5
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-21*
*Last updated: 2026-04-21 after roadmap creation*
