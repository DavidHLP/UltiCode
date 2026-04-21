---
gsd_state_version: 1.0
milestone: v1.6
milestone_name: User & Social
status: planning
last_updated: "2026-04-21T00:00:00.000Z"
last_activity: 2026-04-21 -- v1.6 roadmap created
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# ROADMAP.md

**Milestone:** v1.6 User & Social
**Granularity:** coarse
**Phases:** 4 (26-29)
**Coverage:** 11/11 requirements mapped

## Phases

- [x] **Phase 26: Follow System** - Follow/unfollow, follower/following lists, achievement integration
- [ ] **Phase 27: Profile Backend** - User profile endpoint, profile edit, avatar upload
- [ ] **Phase 28: Achievement Backend** - Achievement triggers, progress indicators, categories, WebSocket notifications
- [ ] **Phase 29: Social Frontend** - Profile page, follow button, achievements display

---

## Phase Details

### Phase 26: Follow System
**Goal:** Users can follow/unfollow each other and view follower/following lists
**Depends on:** None (first phase)
**Requirements:** FOLLOW-01, FOLLOW-02, FOLLOW-04
**Success Criteria** (what must be TRUE):
  1. User can follow another user via POST /users/{id}/follow (idempotent, no self-follow)
  2. User can unfollow another user via DELETE /users/{id}/follow
  3. User can view paginated follower list via GET /users/{id}/followers
  4. User can view paginated following list via GET /users/{id}/following
  5. Follower milestone achievements trigger automatically when follow count thresholds are reached
**Plans:** 1 plan
- [ ] 26-01-PLAN.md - Follow system implementation (entity, mapper, service, controller)

### Phase 27: Profile Backend
**Goal:** Users can view and edit user profiles with social stats
**Depends on:** Phase 26 (follow system for social counts)
**Requirements:** PROFILE-01, PROFILE-03
**Success Criteria** (what must be TRUE):
  1. User can view any user's public profile via GET /users/{id}/profile returning username, avatar, bio, social stats, and achievements
  2. User can edit their own bio, company, location, website via PUT /users/me/profile
  3. User can upload avatar via MultipartFile upload endpoint
  4. Profile includes aggregated stats: problems solved count, submissions count, contest rating, follower/following counts
**Plans:** 1 plan
- [ ] 26-01-PLAN.md - Follow system implementation (entity, mapper, service, controller)

### Phase 28: Achievement Backend
**Goal:** Achievement system is complete with triggers, progress tracking, categories, and real-time notifications
**Depends on:** Phase 26, Phase 27
**Requirements:** ACHV-01, ACHV-02, ACHV-03, ACHV-04
**Success Criteria** (what must be TRUE):
  1. System automatically awards achievements when criteria are met (first problem, language milestones, follower milestones, streaks, contests)
  2. User can view progress toward unearned achievements via GET /users/me/achievements/progress with current count, percentage, and next milestone
  3. User can browse achievements by category via GET /achievements?category={category}
  4. User receives WebSocket notification immediately upon earning an achievement with name, badge, and rarity
**Plans:** 1 plan
- [ ] 26-01-PLAN.md - Follow system implementation (entity, mapper, service, controller)
**UI hint:** yes

### Phase 29: Social Frontend
**Goal:** Profile page and social features visible and interactive in Console
**Depends on:** Phase 26, Phase 27, Phase 28
**Requirements:** PROFILE-02, FOLLOW-03
**Success Criteria** (what must be TRUE):
  1. User can view profile page at /profile/{username} with header, stats cards, achievements section
  2. Follow button shows "Follow" state when not following, "Following" with hover-to-unfollow when following
  3. Profile displays earned achievements as cards with badge icons and rarity tiers
  4. Follow button is visible only when viewing other users' profiles (not own profile)
**Plans:** 1 plan
- [ ] 26-01-PLAN.md - Follow system implementation (entity, mapper, service, controller)
**UI hint:** yes

---

## Progress Table

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 26. Follow System | 0/3 | Not started | - |
| 27. Profile Backend | 1/1 | ✓ Complete | 2026-04-21 |
| 28. Achievement Backend | 0/4 | Not started | - |
| 29. Social Frontend | 0/4 | Not started | - |

---

## Coverage Map

| Requirement | Phase | Description |
|-------------|-------|-------------|
| FOLLOW-01 | Phase 26 | Follow/Unfollow endpoints |
| FOLLOW-02 | Phase 26 | Follower/Following lists |
| FOLLOW-03 | Phase 29 | Follow button state (UI) |
| FOLLOW-04 | Phase 26 | Achievement integration |
| PROFILE-01 | Phase 27 | User profile endpoint |
| PROFILE-02 | Phase 29 | Profile page frontend |
| PROFILE-03 | Phase 27 | Profile edit + avatar |
| ACHV-01 | Phase 28 | Achievement triggers |
| ACHV-02 | Phase 28 | Progress indicators |
| ACHV-03 | Phase 28 | Achievement categories |
| ACHV-04 | Phase 28 | WebSocket notification |

---

*Generated: 2026-04-21*
