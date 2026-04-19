# Phase 13: Contest Data Layer - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Create the missing backend entities (ContestProblem, ContestSubmission, ContestAnnouncement), implement their mappers and service-layer logic, and add admin CRUD endpoints for contest management (start, stop, update, delete) and announcements. The management frontend already has extensive contest UI — this phase provides the backend APIs it calls.

**Scope:**
- ContestProblem entity + mapper + service logic (problem-contest association during create/update)
- ContestSubmission entity + mapper + service logic (sync recording when users submit during contests)
- ContestAnnouncement entity + mapper + CRUD REST endpoints
- AdminContestController: add start, end, update, delete endpoints
- ContestService: fill in missing implementation for problem assignment and lifecycle operations

**Out of scope:**
- Contest scheduler (automatic UPCOMING→RUNNING→FINISHED transitions) — Phase 14
- Rating calculation engine — Phase 14
- WebSocket real-time ranking — Phase 14
- Frontend changes (management and console frontends already built)
- New database migrations (tables already exist in V3)

</domain>

<decisions>
## Implementation Decisions

### Contest Problem Assignment
- **D-01:** Use default scores with optional admin override — DB has `score`, `penalty_per_wrong`, `base_score`, `time_bonus` fields; admin can set custom scores or accept defaults (100 base score, 0 penalty)
- **D-02:** Problems are assigned during contest creation via `CreateContestDTO.problemIds` (or similar list field) — bulk insert into `contest_problems` with auto-generated labels (Q1, Q2, Q3...)
- **D-03:** Support problem reordering and replacement via update endpoint — delete old contest_problems, insert new ones in single transaction

### Contest Submission Recording
- **D-04:** When a user submits code during an active contest, create both the regular `Submission` AND the `ContestSubmission` record in the same transaction — ensures data consistency
- **D-05:** ContestSubmission captures `time_from_start` (seconds since contest start_time), `is_accepted` (based on final verdict), and links to `contest_problem_id`
- **D-06:** Contest submission is only recorded if the user is a registered participant with PARTICIPATING status

### Admin Contest Lifecycle
- **D-07:** Validation on start: contest must have at least one problem assigned, status must be UPCOMING
- **D-08:** Validation on stop: contest status must be RUNNING; allow early stop (before end_time) for admin convenience
- **D-09:** Start action sets status to RUNNING and records actual start time; stop action sets status to FINISHED and records actual end time
- **D-10:** Update and delete follow standard patterns — update only allowed for UPCOMING contests, delete allowed for UPCOMING or FINISHED (not RUNNING)

### Announcement CRUD
- **D-11:** Full CRUD for contest announcements: create (with title, content, is_pinned), update, delete, list by contest_id
- **D-12:** Push new announcements via existing `RealtimeService.emitAnnouncement()` WebSocket method — infrastructure already exists, just needs REST endpoint to trigger it
- **D-13:** Announcements are scoped to a specific contest — no global announcements in this phase

### Entity Design
- **D-14:** ContestProblem entity maps directly to `contest_problems` table (V3 migration) — use MyBatis-Plus annotations
- **D-15:** ContestSubmission entity maps to `contest_submissions` table — includes `submission_id` FK to link back to main Submission
- **D-16:** ContestAnnouncement entity maps to `contest_announcements` table — standard fields with `is_pinned` boolean

### Claude's Discretion
- Exact DTO/VO class structure for new entities
- Validation annotation details (@NotNull, @Size, etc.)
- Error message wording and exception types
- Whether to add batch operations for contest problems or handle one-by-one
- Unit test structure and mock boundaries
- Transaction boundary details

### Folded Todos
None — no pending todos matched this phase.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema
- `db-manager/migrations/V3__contest_schema.sql` — Contest tables: contests, contest_problems, contest_submissions, contest_announcements, contest_participants, contest_rankings

### Existing Backend Contest Module
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/Contest.java` — Existing contest entity with status, startTime, duration fields
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/ContestParticipant.java` — Participant entity with status tracking
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/ContestService.java` — Service interface (has getContestProblems, createContest, updateContest, deleteContest methods declared)
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/ContestServiceImpl.java` — Current implementation (needs entity backing)
- `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestMapper.java` — Existing mapper
- `backend-spring/src/main/java/com/ulticode/modules/contest/enums/ContestStatus.java` — UPCOMING, RUNNING, FINISHED
- `backend-spring/src/main/java/com/ulticode/modules/contest/dto/CreateContestDTO.java` — Create DTO with validation annotations

### Admin Controller
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminContestController.java` — Only 2 read-only endpoints currently; add start/stop/update/delete here

### WebSocket (Announcement Push)
- `backend-spring/src/main/java/com/ulticode/modules/websocket/notification/NotificationWebSocketHandler.java` — Has emitAnnouncement() method ready to use
- `backend-spring/src/main/java/com/ulticode/modules/websocket/constants/WebSocketConstants.java` — Event constants

### Phase 12 Context (Prior Decisions)
- `.planning/phases/12-judge-worker/12-CONTEXT.md` — Judge Worker architecture, language whitelist (5 languages), WebSocket push pattern, retry logic

### Frontend (Already Built — Reference Only)
- `management/src/api/admin/contests.ts` — API calls the backend will serve
- `management/src/stores/admin/contests.ts` — Pinia store showing expected API contract
- `management/src/views/contests/ContestDetailView.vue` — Admin contest management page
- `console/src/api/contest.ts` — User-facing contest API client
- `console/src/types/contest.ts` — TypeScript types for contest data

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Contest entity + mapper**: Already exists with all fields matching the DB schema — new entities follow the same pattern
- **ContestService interface**: Already declares getContestProblems(), createContest(), updateContest() — implementation just needs entity backing
- **CreateContestDTO / UpdateContestDTO**: Already defined with validation — extend for problem assignment
- **AdminContestController**: Already has auth and base structure — add new endpoints here
- **RealtimeService.emitAnnouncement()**: WebSocket push infrastructure already built — just call it from the new announcement endpoints
- **Result<T> wrapper**: All API responses use the standard envelope pattern

### Established Patterns
- **Entity pattern**: MyBatis-Plus `@TableName`, `@TableId(type = IdType.ASSIGN_UUID)`, `@TableField` — follow Contest.java as template
- **Mapper pattern**: Extends `BaseMapper<T>` with `@Mapper` annotation — follow ContestMapper.java
- **Service pattern**: Interface + `@Service` impl, injected via constructor — follow ContestServiceImpl
- **Controller pattern**: `@RestController` with `@RequestMapping("/admin/contests")`, return `Result<T>` — follow AdminContestController
- **DTO/VO pattern**: DTOs for input validation, VOs for output — follow existing CreateContestDTO.java
- **Transaction pattern**: `@Transactional` on service methods that modify multiple tables

### Integration Points
- **Contest creation**: AdminContestController → ContestService.createContest() → insert Contest + bulk insert ContestProblem records
- **Contest submission recording**: SubmissionServiceImpl.submit() needs to check if user is in active contest → if yes, also insert ContestSubmission in same transaction
- **Admin lifecycle**: AdminContestController.start()/stop() → ContestService → update Contest.status
- **Announcement CRUD**: New controller methods → ContestAnnouncementService → insert/update/delete + WebSocket push on create

### Critical Gaps
- **No ContestProblem entity**: DB table exists but no Java entity, mapper, or service logic
- **No ContestSubmission entity**: DB table exists but no Java entity — contest submission recording is completely missing
- **No ContestAnnouncement entity**: DB table exists but no Java entity — announcement REST endpoints don't exist
- **Admin API incomplete**: Only 2 read-only endpoints, missing start/stop/update/delete
- **ContestService methods declared but unbacked**: getContestProblems() etc. exist in interface but can't work without entities

</code_context>

<specifics>
## Specific Ideas

No specific requirements — standard contest management backend implementation matching existing patterns.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 13-contest-data-layer*
*Context gathered: 2026-04-18*
