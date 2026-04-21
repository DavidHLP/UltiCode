---
gsd_state_version: 1.0
milestone: v1.7
milestone_name: Notifications
status: planning
last_updated: "2026-04-21"
last_activity: 2026-04-21 -- v1.7 roadmap created
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Roadmap: UltiCode

## Milestones

- ✅ **v1.0 Technical Debt** — Phases 1-4 (shipped 2026-04-16)
- ✅ **v1.1 Technical Debt II** — Phases 5-8 (shipped 2026-04-17)
- ✅ **v1.2 CI/CD Pipeline** — Phases 9-11 (shipped 2026-04-18)
- ✅ **v1.3 Core Features** — Phases 12-15 (shipped 2026-04-19)
- ✅ **v1.4 Seed Data** — Phases 16-18 (shipped 2026-04-19)
- ✅ **v1.5 Coverage** — Phases 19-25 (shipped 2026-04-20)
- ✅ **v1.6 User & Social** — Phases 26-29 (shipped 2026-04-21)
- 🚧 **v1.7 Notifications** — Phases 30-33 (in progress)

## Phase Progress

| Phase | Milestone | Plans | Status | Completed |
|-------|-----------|-------|--------|-----------|
| 1-4 | v1.0 | - | Complete | 2026-04-16 |
| 5-8 | v1.1 | - | Complete | 2026-04-17 |
| 9-11 | v1.2 | - | Complete | 2026-04-18 |
| 12-15 | v1.3 | - | Complete | 2026-04-19 |
| 16-18 | v1.4 | - | Complete | 2026-04-19 |
| 19-25 | v1.5 | - | Complete | 2026-04-20 |
| 26-29 | v1.6 | 5 | Complete | 2026-04-21 |
| 30-33 | v1.7 | 1 | Planning | - |

---
## Phases

- [ ] **Phase 30: WebSocket Push Wiring** - Wire NotificationServiceImpl and AchievementNotificationListener to RealtimeService
- [ ] **Phase 31: Follow Notification Trigger** - Add follow notification trigger to FollowServiceImpl
- [x] **Phase 32: Contest Reminder Trigger** - Wire ContestScheduler to notification service for 24h and 1h reminders (completed 2026-04-21)
- [x] **Phase 33: Submission Result Trigger** - Wire SubmissionServiceImpl to notification service (completed 2026-04-21)

---

## Phase Details

### Phase 30: WebSocket Push Wiring
**Goal**: All notification creations push via WebSocket to connected clients
**Depends on**: Nothing
**Requirements**: NOTIF-01, NOTIF-02
**Success Criteria** (what must be TRUE):
  1. User receives real-time WebSocket push when any notification is created via NotificationServiceImpl.createNotification()
  2. User receives real-time WebSocket push when an achievement is earned (AchievementNotificationListener fixed)
**Plans**: 1

Plans:
- [ ] 30-01-PLAN.md — Wire NotificationServiceImpl and AchievementNotificationListener to RealtimeService

### Phase 31: Follow Notification Trigger
**Goal**: Users receive notifications when someone follows them
**Depends on**: Phase 30
**Requirements**: NOTIF-03
**Success Criteria** (what must be TRUE):
  1. User receives in-app notification when another user follows them
  2. Notification is persisted to DB and pushed via WebSocket
**Plans**: 1

Plans:
- [x] 32-01-PLAN.md — Wire ContestScheduler to notification service for 24h and 1h reminders

### Phase 32: Contest Reminder Trigger
**Goal**: Users receive notifications before contests they registered for start
**Depends on**: Phase 30
**Requirements**: NOTIF-04
**Success Criteria** (what must be TRUE):
  1. User receives notification 24 hours before a registered contest starts
  2. User receives notification 1 hour before a registered contest starts
**Plans**: 1

Plans:
- [ ] 32-01-PLAN.md — Wire ContestScheduler to notification service for 24h and 1h reminders

### Phase 33: Submission Result Trigger
**Goal**: Users receive notifications when their submission is judged
**Depends on**: Phase 30
**Requirements**: NOTIF-05
**Success Criteria** (what must be TRUE):
  1. User receives notification when their submission is judged (AC/WA/TLE/etc.)
  2. Notification includes submission status and problem context
**Plans**: 1

Plans:
- [ ] 32-01-PLAN.md — Wire ContestScheduler to notification service for 24h and 1h reminders

---
_Archived milestones: `.planning/milestones/`
