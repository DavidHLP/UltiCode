---
phase: 13-contest-data-layer
plan: 01
subsystem: database, api
tags: [mybatis-plus, spring-boot, contest, entity, mapper, crud, lifecycle]

# Dependency graph
requires: []
provides:
  - ContestProblem, ContestSubmission, ContestAnnouncement entities mapping V3 contest tables
  - ContestProblemMapper, ContestSubmissionMapper, ContestAnnouncementMapper with custom queries
  - ContestParticipantStatus enum matching DB schema (REGISTERED, STARTED, FINISHED, DISQUALIFIED)
  - AdminContestService with full CRUD and lifecycle (create, update, delete, start, end)
  - 5 new admin REST endpoints matching management frontend API contract
affects: [13-contest-data-layer, 14-contest-participation, 15-contest-ranking]

# Tech tracking
tech-stack:
  added: []
  patterns: [contest-lifecycle-validation, problem-bulk-insert, slug-generation]

key-files:
  created:
    - backend-spring/src/main/java/com/ulticode/modules/contest/entity/ContestProblem.java
    - backend-spring/src/main/java/com/ulticode/modules/contest/entity/ContestSubmission.java
    - backend-spring/src/main/java/com/ulticode/modules/contest/entity/ContestAnnouncement.java
    - backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestProblemMapper.java
    - backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestSubmissionMapper.java
    - backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestAnnouncementMapper.java
  modified:
    - backend-spring/src/main/java/com/ulticode/modules/contest/entity/enums/ContestParticipantStatus.java
    - backend-spring/src/main/java/com/ulticode/modules/contest/entity/ContestParticipant.java
    - backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminContestService.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminContestServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminContestController.java

key-decisions:
  - "Admin-created contests go directly to UPCOMING status (not DRAFT) to allow immediate start"
  - "Contest creation bulk-inserts problems with Q1/Q2/Q3 labels and baseScore=100"
  - "Update replaces all contest_problems (delete + re-insert) rather than diffing"

patterns-established:
  - "Contest lifecycle: UPCOMING->RUNNING->FINISHED with strict state validation"
  - "Problem assignment: bulk-insert ContestProblem records with auto-generated problem_index"

requirements-completed: [CONTEST-01, CONTEST-05]

# Metrics
duration: 7min
completed: 2026-04-18
---

# Phase 13 Plan 01: Contest Entities, Mappers, and Admin CRUD Summary

**Fixed ContestParticipantStatus enum to match DB schema, created 3 entity classes and 3 mapper interfaces for V3 contest tables, implemented admin contest CRUD with lifecycle validation and problem assignment.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-04-18T15:17:56Z
- **Completed:** 2026-04-18T15:25:28Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments
- Fixed ContestParticipantStatus enum (PARTICIPATING->STARTED, COMPLETED->FINISHED) matching DB enum column exactly
- Created ContestProblem, ContestSubmission, ContestAnnouncement entities with correct field types per V3 DDL
- Created three mapper interfaces with custom query methods for service layer consumption
- Implemented admin contest CRUD (create with problem assignment, update with problem replacement, soft-delete)
- Added contest lifecycle endpoints (start: UPCOMING->RUNNING, end: RUNNING->FINISHED) with state validation

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix ContestParticipantStatus enum, create contest entities and mappers** - `950f393fb` (feat)
2. **Task 2: Implement admin contest CRUD and lifecycle endpoints** - `c23c32f76` (feat)

## Files Created/Modified
- `backend-spring/.../contest/entity/ContestProblem.java` - Entity mapping contest_problems table (Long problemId for bigint)
- `backend-spring/.../contest/entity/ContestSubmission.java` - Entity mapping contest_submissions table (no updatedAt)
- `backend-spring/.../contest/entity/ContestAnnouncement.java` - Entity mapping contest_announcements table (no updatedAt)
- `backend-spring/.../contest/mapper/ContestProblemMapper.java` - Mapper with findByContestId, countByContestId, deleteByContestId, findByProblemId
- `backend-spring/.../contest/mapper/ContestSubmissionMapper.java` - Mapper with findByContestIdAndParticipantId, countByContestId
- `backend-spring/.../contest/mapper/ContestAnnouncementMapper.java` - Mapper with pinned-first ordering query
- `backend-spring/.../contest/entity/enums/ContestParticipantStatus.java` - Fixed enum values to match DB
- `backend-spring/.../contest/entity/ContestParticipant.java` - Updated Javadoc to reflect correct enum values
- `backend-spring/.../contest/service/impl/ContestServiceImpl.java` - Updated 8 enum references
- `backend-spring/.../contest/mapper/ContestParticipantMapper.java` - Fixed SQL literal COMPLETED->FINISHED
- `backend-spring/.../admin/service/AdminContestService.java` - Added 5 method signatures
- `backend-spring/.../admin/service/impl/AdminContestServiceImpl.java` - Full CRUD + lifecycle implementation
- `backend-spring/.../admin/controller/AdminContestController.java` - 5 new REST endpoints

## Decisions Made
- Admin-created contests go directly to UPCOMING status (not DRAFT) to allow immediate start without a separate publish step
- Problem assignment uses Q1/Q2/Q3 auto-generated labels with baseScore=100 default
- Update operation replaces all contest_problems (delete + re-insert) for simplicity rather than diffing

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All V3 contest tables now have Java entities and mappers
- Admin can manage full contest lifecycle via REST API
- Management frontend's existing API calls (POST, PATCH, DELETE, start, end) are now backed by real endpoints
- ContestProblemMapper.deleteByContestId available for problem management in future plans
- ContestSubmissionMapper ready for submission recording in future phases

---
*Phase: 13-contest-data-layer*
*Completed: 2026-04-18*
