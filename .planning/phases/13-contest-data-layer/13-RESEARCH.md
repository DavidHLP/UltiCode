# Phase 13: Contest Data Layer - Research

**Researched:** 2026-04-18
**Domain:** Spring Boot backend entity/mapper/service layer for contest management
**Confidence:** HIGH

## Summary

Phase 13 fills the gap between the database schema (which already exists in V3 migration) and the backend API layer. Three database tables -- `contest_problems`, `contest_submissions`, and `contest_announcements` -- have no corresponding Java entities, mappers, or service logic. Additionally, the `AdminContestController` only exposes 2 read-only GET endpoints while the management frontend calls POST/PUT/DELETE endpoints that don't exist on the backend.

The work is predominantly backend-only: create 3 entity classes, 3 mapper interfaces, extend `AdminContestService`/`AdminContestServiceImpl` with CRUD + lifecycle endpoints, integrate contest submission recording into `SubmissionServiceImpl.submit()`, and add announcement REST endpoints with WebSocket push via the existing `RealtimeService.emitAnnouncement()`. No database migrations are needed -- all tables already exist with seed data.

**Primary recommendation:** Follow the existing `Contest.java` / `ContestParticipant.java` entity patterns exactly (MyBatis-Plus `@TableName`, `@TableId(type = IdType.ASSIGN_UUID)`, `@TableField(fill = FieldFill.INSERT)`). Extend `AdminContestController` rather than creating new controllers for admin operations.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Use default scores with optional admin override -- DB has `score`, `penalty_per_wrong`, `base_score`, `time_bonus` fields; admin can set custom scores or accept defaults (100 base score, 0 penalty)
- **D-02:** Problems assigned during contest creation via `CreateContestDTO.problemIds` -- bulk insert into `contest_problems` with auto-generated labels (Q1, Q2, Q3...)
- **D-03:** Support problem reordering and replacement via update endpoint -- delete old contest_problems, insert new ones in single transaction
- **D-04:** When user submits code during active contest, create both regular `Submission` AND `ContestSubmission` in same transaction
- **D-05:** ContestSubmission captures `time_from_start` (seconds since contest start_time), `is_accepted` (based on final verdict), links to `contest_problem_id`
- **D-06:** Contest submission only recorded if user is a registered participant with PARTICIPATING status
- **D-07:** Validation on start: contest must have at least one problem assigned, status must be UPCOMING
- **D-08:** Validation on stop: contest status must be RUNNING; allow early stop
- **D-09:** Start sets status=RUNNING + records actual start time; stop sets status=FINISHED + records actual end time
- **D-10:** Update only for UPCOMING contests; delete for UPCOMING or FINISHED (not RUNNING)
- **D-11:** Full CRUD for contest announcements: create (title, content, is_pinned), update, delete, list by contest_id
- **D-12:** Push new announcements via existing `RealtimeService.emitAnnouncement()` WebSocket method
- **D-13:** Announcements scoped to a specific contest -- no global announcements
- **D-14:** ContestProblem entity maps directly to `contest_problems` table with MyBatis-Plus annotations
- **D-15:** ContestSubmission entity maps to `contest_submissions` with `submission_id` FK
- **D-16:** ContestAnnouncement entity maps to `contest_announcements` with `is_pinned` boolean

### Claude's Discretion
- Exact DTO/VO class structure for new entities
- Validation annotation details (@NotNull, @Size, etc.)
- Error message wording and exception types
- Whether to add batch operations for contest problems or handle one-by-one
- Unit test structure and mock boundaries
- Transaction boundary details

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONTEST-01 | Add ContestProblem entity/mapper -- DB table exists, create entity + mapper + service logic for problem-contest association | V3 schema verified, entity pattern from Contest.java, seed data shows problem_index format (Q1, Q2...) |
| CONTEST-02 | Add ContestSubmission entity/mapper -- DB table exists, create entity + mapper, record during contest submissions | V3 schema verified, SubmissionServiceImpl.submit() is integration point, existing @Transactional |
| CONTEST-05 | Complete Admin Contest API -- add start, end, update, delete to AdminContestController | AdminContestController has 2 GET endpoints, AdminContestService needs 5+ new methods, management frontend contract documented |
| CONTEST-07 | Add contest announcement CRUD API -- table exists, WebSocket emitAnnouncement() ready | V3 schema verified, AnnouncementPayload record exists, RealtimeService.emitAnnouncement() ready, console frontend expects `/contest/{slug}/announcements` |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Contest entity CRUD | API / Backend | -- | Entity creation, mapper, service logic are server-side concerns |
| Contest lifecycle (start/stop) | API / Backend | -- | Status transitions are business logic, enforced server-side |
| Contest submission recording | API / Backend | -- | Must happen atomically with Submission creation in the same @Transactional |
| Announcement CRUD + WebSocket push | API / Backend | WebSocket (push only) | REST endpoints create/delete announcements; WebSocket is notification channel |
| Admin contest management UI | Frontend (Management) | -- | Already built -- this phase provides backend APIs it calls |
| User-facing contest problem list | Frontend (Console) | -- | Already built -- console expects `/contest/{id}/problems` or similar |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| MyBatis-Plus | (project managed) | ORM, entity mapping, BaseMapper CRUD | Already used by all contest entities (Contest, ContestParticipant) |
| Spring Boot | 3.5 | Framework | Project standard |
| Lombok | (project managed) | @Data, @RequiredArgsConstructor | All existing entities use Lombok |
| Jakarta Validation | (Spring Boot managed) | @NotNull, @NotBlank, @Size on DTOs | All existing DTOs use Jakarta annotations |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| SimpMessagingTemplate | (Spring WebSocket) | Push announcements via WebSocket | Only for announcement creation -- call RealtimeService.emitAnnouncement() |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Extending AdminContestController | Separate ContestProblemController, ContestAnnouncementController | Splitting creates more files but better separation; however CONTEXT D-02 ties problem assignment to contest creation, so keeping in AdminContestController is cleaner |

**Installation:**
No new packages needed -- all dependencies already in the project.

**Version verification:** N/A -- no new packages to install.

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Management Frontend (9003)                       │
│  contests.ts: createContest(), updateContest(), deleteContest(),   │
│               startContest(), endContest(), addProblem(), etc.     │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTP (REST)
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│              AdminContestController (/admin/contests)              │
│  GET    /                          ─ Already exists                │
│  GET    /{id}                      ─ Already exists                │
│  POST   /                          ─ NEW: create contest           │
│  PUT    /{id}                      ─ NEW: update contest           │
│  DELETE /{id}                      ─ NEW: delete contest           │
│  POST   /{id}/start                ─ NEW: start contest            │
│  POST   /{id}/end                  ─ NEW: end contest              │
│  POST   /{id}/problems             ─ NEW: add problem to contest   │
│  DELETE /{id}/problems/{problemId} ─ NEW: remove problem            │
│  POST   /{id}/announcements        ─ NEW: create announcement      │
│  PUT    /{id}/announcements/{aid}  ─ NEW: update announcement      │
│  DELETE /{id}/announcements/{aid}  ─ NEW: delete announcement      │
│  GET    /{id}/announcements        ─ NEW: list announcements       │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    AdminContestService                               │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  createContest()                                               ││
│  │    1. Insert Contest entity                                    ││
│  │    2. Bulk insert ContestProblem records (from problemIds)     ││
│  │    3. Generate labels Q1, Q2, Q3...                           ││
│  └─────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  updateContest()                                               ││
│  │    1. Validate status == UPCOMING                              ││
│  │    2. Update Contest fields                                    ││
│  │    3. If problemIds changed: delete old, insert new (txn)      ││
│  └─────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  startContest() / endContest()                                 ││
│  │    1. Validate preconditions (status, problems)                ││
│  │    2. Update Contest.status + startTime/endTime                ││
│  │    3. Emit contest status via WebSocket (RealtimeService)      ││
│  └─────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  Announcement CRUD                                             ││
│  │    1. Create/Update/Delete ContestAnnouncement                 ││
│  │    2. On create: emitAnnouncement() via WebSocket              ││
│  └─────────────────────────────────────────────────────────────────┘│
└────────────────────────────┬────────────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌─────────────┐ ┌────────────┐ ┌────────────────┐
     │ ContestMapper│ │ContestProb │ │ContestAnnounce │
     │ (existing)   │ │Mapper (NEW)│ │Mapper (NEW)    │
     └─────────────┘ └────────────┘ └────────────────┘
              │              │              │
              ▼              ▼              ▼
     ┌──────────────────────────────────────────────┐
     │              MySQL (V3 schema)                │
     │  contests │ contest_problems │ contest_       │
     │           │                  │ announcements │
     └──────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│              Contest Submission Recording (CONTEST-02)              │
│                                                                     │
│  SubmissionServiceImpl.submit()                                    │
│    1. Create Submission (existing logic)                            │
│    2. Check: is user participating in an active contest?            │
│       └── ContestParticipantMapper.findByContestIdAndUserId()      │
│       └── Check participant.status == PARTICIPATING                │
│       └── Check contest.status == RUNNING                           │
│    3. If yes: find ContestProblem for this problem+contest          │
│    4. Calculate time_from_start (seconds since contest.startTime)   │
│    5. Insert ContestSubmission record                               │
│    6. All within same @Transactional                               │
└─────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure
```
backend-spring/src/main/java/com/ulticode/modules/contest/
├── controller/
│   └── ContestController.java              # (existing -- user-facing)
├── dto/
│   ├── CreateContestDTO.java               # (existing -- extend with problem assignment)
│   ├── UpdateContestDTO.java               # (existing -- extend with problem reordering)
│   ├── ContestVO.java                      # (existing)
│   ├── ContestAnnouncementDTO.java         # NEW: create/update announcement input
│   ├── ContestAnnouncementVO.java          # NEW: announcement response
│   └── ContestProblemVO.java               # NEW: problem list item in contest
├── entity/
│   ├── Contest.java                        # (existing)
│   ├── ContestParticipant.java             # (existing)
│   ├── GlobalRanking.java                  # (existing)
│   ├── ContestProblem.java                 # NEW: maps to contest_problems
│   ├── ContestSubmission.java              # NEW: maps to contest_submissions
│   ├── ContestAnnouncement.java            # NEW: maps to contest_announcements
│   └── enums/
│       └── ...                             # (existing enums)
├── mapper/
│   ├── ContestMapper.java                  # (existing)
│   ├── ContestParticipantMapper.java       # (existing)
│   ├── GlobalRankingMapper.java            # (existing)
│   ├── ContestProblemMapper.java           # NEW: extends BaseMapper<ContestProblem>
│   ├── ContestSubmissionMapper.java        # NEW: extends BaseMapper<ContestSubmission>
│   └── ContestAnnouncementMapper.java      # NEW: extends BaseMapper<ContestAnnouncement>
└── service/
    ├── ContestService.java                 # (existing interface)
    ├── impl/ContestServiceImpl.java        # (existing -- extend with problem logic)
    ├── RankingService.java                 # (existing)
    └── impl/RankingServiceImpl.java        # (existing)

backend-spring/src/main/java/com/ulticode/modules/admin/
├── controller/
│   └── AdminContestController.java         # (existing -- ADD 10+ new endpoints)
├── dto/
│   ├── AdminContestVO.java                 # (existing -- add problemCount)
│   ├── AdminContestQueryDTO.java           # (existing)
│   ├── CreateContestRequestDTO.java        # NEW: admin-specific create DTO (if needed)
│   └── UpdateContestRequestDTO.java        # NEW: admin-specific update DTO (if needed)
└── service/
    ├── AdminContestService.java            # (existing -- ADD 8+ new methods)
    └── impl/AdminContestServiceImpl.java   # (existing -- implement new methods)
```

### Pattern 1: MyBatis-Plus Entity Pattern
**What:** All entities use `@TableName`, `@TableId(type = IdType.ASSIGN_UUID)`, Lombok `@Data`, and `@TableField(fill = FieldFill.INSERT/INSERT_UPDATE)` for timestamps.
**When to use:** Every new entity must follow this pattern exactly.
**Example:**
```java
// Source: [VERIFIED: existing Contest.java in codebase]
@Data
@TableName("contest_problems")
public class ContestProblem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;

    private Long problemId;

    private String problemIndex;  // Q1, Q2, Q3...

    private Integer score;

    private Integer penaltyPerWrong;

    private Integer solvedCount;

    private Integer submissionCount;

    private String label;

    private Integer baseScore;

    private Integer timeBonus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### Pattern 2: Mapper Pattern
**What:** Extend `BaseMapper<T>` with `@Mapper` annotation. Add custom `@Select`/`@Update` queries for non-trivial lookups.
**When to use:** All data access follows this pattern.
**Example:**
```java
// Source: [VERIFIED: existing ContestMapper.java in codebase]
@Mapper
public interface ContestProblemMapper extends BaseMapper<ContestProblem> {

    @Select("SELECT * FROM contest_problems WHERE contest_id = #{contestId} ORDER BY problem_index ASC")
    List<ContestProblem> findByContestId(@Param("contestId") String contestId);

    @Select("SELECT * FROM contest_problems WHERE contest_id = #{contestId} AND problem_id = #{problemId} LIMIT 1")
    ContestProblem findByContestIdAndProblemId(
            @Param("contestId") String contestId,
            @Param("problemId") Long problemId);
}
```

### Pattern 3: Admin Controller Endpoint Pattern
**What:** All admin endpoints use `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`, `@SecurityRequirement(name = "Bearer")`, return `Result<T>`, and accept `@Valid @RequestBody` DTOs.
**When to use:** Every new admin endpoint.
**Example:**
```java
// Source: [VERIFIED: existing ContestController.java + AdminContestController.java]
@Operation(summary = "Start contest")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@PostMapping("/{id}/start")
public Result<AdminContestVO> startContest(@PathVariable String id) {
    return Result.success(adminContestService.startContest(id));
}
```

### Pattern 4: WebSocket Announcement Push
**What:** After creating an announcement, call `RealtimeService.emitAnnouncement()` with an `AnnouncementPayload` record. The payload uses a static factory method `AnnouncementPayload.of(id, contestId, title, content)`.
**When to use:** Only on announcement creation, not update or delete.
**Example:**
```java
// Source: [VERIFIED: RealtimeService.java + AnnouncementPayload.java in codebase]
AnnouncementPayload payload = AnnouncementPayload.of(
    announcement.getId(),
    announcement.getContestId(),
    announcement.getTitle(),
    announcement.getContent());
realtimeService.emitAnnouncement(payload);
```

### Anti-Patterns to Avoid
- **Don't create a separate ContestProblemController**: Problem assignment is tied to contest creation/update (D-02, D-03). Keep it in AdminContestController.
- **Don't use ContestService for admin operations**: AdminContestService is the admin layer; ContestService is user-facing. The existing pattern separates them.
- **Don't forget @Transactional on multi-table writes**: Contest creation touches both `contests` and `contest_problems` tables. Contest submission recording touches both `submissions` and `contest_submissions`.
- **Don't hand-roll contest status enum checks**: Use `ContestStatus.RUNNING.name()` string comparison (existing pattern) rather than adding new enum-based methods.
- **Don't add `updated_at` column to contest_announcements**: The V3 schema has NO `updated_at` column on this table. The entity must not include it.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Contest problem label generation | Custom label logic without reference | Follow seed data pattern: `Q1`, `Q2`, `Q3`... | Seed data consistently uses Q-prefix format |
| UUID generation | Manual `UUID.randomUUID()` for every entity | `@TableId(type = IdType.ASSIGN_UUID)` | MyBatis-Plus auto-generates UUIDs, consistent with existing entities |
| Contest submission time calculation | Custom duration math | `java.time.Duration.between(contest.getStartTime(), LocalDateTime.now()).getSeconds()` | Standard Java time API, already used elsewhere in the codebase |
| WebSocket push infrastructure | Build new WebSocket endpoints | `RealtimeService.emitAnnouncement()` | Already exists with correct topic routing `/topic/contest/{id}/announcement` |
| Pagination | Custom offset/limit queries | MyBatis-Plus `Page<T>` with `selectPage()` | Already used by AdminContestServiceImpl |

**Key insight:** This phase is about wiring existing infrastructure together, not building new systems. The WebSocket push, entity patterns, mapper patterns, and admin controller structure all exist -- the gap is 3 missing entities + 3 missing mappers + missing service/controller methods.

## Common Pitfalls

### Pitfall 1: Missing `updated_at` on contest_announcements
**What goes wrong:** Entity includes `updatedAt` field but the V3 `contest_announcements` table has no `updated_at` column. MyBatis-Plus will throw SQL error on insert/update.
**Why it happens:** Other contest tables (`contests`, `contest_participants`) have `updated_at`, but `contest_announcements` does not.
**How to avoid:** Do NOT add `updatedAt` to ContestAnnouncement entity. The table schema only has `id`, `contest_id`, `title`, `content`, `created_at`, `is_pinned`.
**Warning signs:** SQL error: "Unknown column 'updated_at' in 'field list'" on first insert.

### Pitfall 2: `problem_id` is `bigint`, not `varchar`
**What goes wrong:** ContestProblem entity uses `String problemId` but the DB column is `bigint NOT NULL`. MyBatis-Plus type mismatch causes insert failures.
**Why it happens:** Most other FK columns in the project use `varchar(40)` (UUID), but `problem_id` references `problems.id` which is `bigint`.
**How to avoid:** Use `Long problemId` in ContestProblem entity, matching the DB column type.
**Warning signs:** MySQL error: "Incorrect integer value" or data truncation.

### Pitfall 3: Contest submission recording breaks existing submit flow
**What goes wrong:** Adding contest submission logic to `SubmissionServiceImpl.submit()` causes all submissions to fail if contest lookup throws.
**Why it happens:** If the contest check is not wrapped in try-catch or properly guarded, a non-contest submission could hit unexpected errors.
**How to avoid:** Only check for contest context if the problem is actually part of a running contest. Guard with optional lookups -- the submission should succeed even if contest recording fails. Consider logging the error and continuing.
**Warning signs:** All submissions returning 500 after integrating contest logic.

### Pitfall 4: Two competing contest creation paths
**What goes wrong:** Both `ContestController` (POST /contest) and `AdminContestController` (POST /admin/contests) create contests with different DTOs and different logic.
**Why it happens:** `ContestController.createContest()` already exists and calls `ContestService.createContest()`. The management frontend calls `POST /admin/contests` which goes to `AdminContestService`. Both need the same problem-assignment logic.
**How to avoid:** The admin create/update should be the authoritative path with problem assignment. The existing `ContestController.createContest()` can delegate to the same service or remain as-is (it currently doesn't handle problemIds). Align the admin flow to handle problemIds per D-02.
**Warning signs:** Creating a contest via admin doesn't assign problems, or creating via user endpoint does.

### Pitfall 5: Status mismatch between frontend and backend enums
**What goes wrong:** Management frontend `ContestStatus` enum has `UPCOMING | RUNNING | FINISHED` (3 values) but backend `ContestStatus` enum has `DRAFT | UPCOMING | RUNNING | FINISHED | CANCELLED` (5 values). Admin must handle DRAFT status correctly.
**Why it happens:** The frontend enum is a subset of the backend enum.
**How to avoid:** Admin start/stop must validate backend status values, not frontend values. Start requires `UPCOMING` (not `DRAFT`). If admin creates a contest, it starts as `DRAFT` -- there should be a "publish" action or the create endpoint should set `UPCOMING` directly.
**Warning signs:** Admin cannot start a freshly created contest because its status is `DRAFT` but start validation expects `UPCOMING`.

### Pitfall 6: `contest_announcements` table has no `author` column
**What goes wrong:** Console frontend `ContestAnnouncement` type expects an `author` field with `id` and `username`, but the DB table has no such column.
**Why it happens:** The frontend type was designed with an author reference but the schema was not updated.
**How to avoid:** Either (a) populate the author field from the current user's info at query time (not stored in DB), or (b) skip the author field in the backend VO and let the frontend handle it as optional. Option (a) is better for user experience.
**Warning signs:** Frontend shows "unknown author" or the field is null.

## Code Examples

### Entity: ContestProblem
```java
// Source: [VERIFIED: V3 migration schema + Contest.java pattern]
@Data
@TableName("contest_problems")
public class ContestProblem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;

    /** bigint -- references problems.id */
    private Long problemId;

    /** Q1, Q2, Q3... format per seed data */
    private String problemIndex;

    private Integer score;

    private Integer penaltyPerWrong;

    private Integer solvedCount;

    private Integer submissionCount;

    private String label;

    private Integer baseScore;

    private Integer timeBonus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### Entity: ContestSubmission
```java
// Source: [VERIFIED: V3 migration schema + Contest.java pattern]
@Data
@TableName("contest_submissions")
public class ContestSubmission {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** FK to submissions.id */
    private String submissionId;

    private String contestId;

    /** FK to contest_problems.id */
    private String contestProblemId;

    /** FK to contest_participants.id */
    private String participantId;

    private String virtualSessionId;

    private LocalDateTime submittedAt;

    /** Seconds since contest start_time */
    private Integer timeFromStart;

    private Boolean isAccepted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

### Entity: ContestAnnouncement
```java
// Source: [VERIFIED: V3 migration schema -- NOTE: no updated_at column]
@Data
@TableName("contest_announcements")
public class ContestAnnouncement {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;

    private String title;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Boolean isPinned;
}
```

### Service: Contest Problem Assignment During Creation
```java
// Source: [VERIFIED: CreateContestDTO already has problemIds field]
@Transactional
public AdminContestVO createContest(CreateContestRequestDTO dto, String userId) {
    // 1. Create contest entity
    Contest contest = new Contest();
    // ... set fields from DTO ...
    contest.setStatus(ContestStatus.UPCOMING.name()); // D-09: not DRAFT
    contestMapper.insert(contest);

    // 2. Bulk insert contest problems (D-02)
    if (dto.getProblemIds() != null && !dto.getProblemIds().isEmpty()) {
        List<ContestProblem> problems = new ArrayList<>();
        for (int i = 0; i < dto.getProblemIds().size(); i++) {
            ContestProblem cp = new ContestProblem();
            cp.setContestId(contest.getId());
            cp.setProblemId(dto.getProblemIds().get(i));
            cp.setProblemIndex("Q" + (i + 1)); // D-02: Q1, Q2, Q3...
            cp.setScore(3 + i);                // D-01: default scoring
            cp.setBaseScore(100);              // D-01: default base score
            cp.setSolvedCount(0);
            cp.setSubmissionCount(0);
            problems.add(cp);
        }
        // Use MyBatis-Plus saveBatch or loop insert
        for (ContestProblem cp : problems) {
            contestProblemMapper.insert(cp);
        }
    }

    return toAdminVO(contest);
}
```

### Service: Contest Submission Recording in SubmissionServiceImpl
```java
// Source: [VERIFIED: existing submit() method structure + V3 schema]
// This goes INSIDE the existing submit() method, AFTER submissionMapper.insert()
// but BEFORE the try block that enqueues the judge job

// --- Contest submission recording (D-04, D-05, D-06) ---
try {
    // Find if this problem is part of any active contest the user is participating in
    // This requires: find running contests containing this problem,
    // then check if user is a PARTICIPATING participant
    recordContestSubmissionIfNeeded(submission.getId(), userId, createDTO.getProblemId());
} catch (Exception e) {
    log.warn("Failed to record contest submission for submission {}", submission.getId(), e);
    // Don't fail the main submission -- contest recording is supplementary
}

private void recordContestSubmissionIfNeeded(String submissionId, String userId, Long problemId) {
    // 1. Find contest_problems containing this problem
    List<ContestProblem> contestProblems = contestProblemMapper.findByProblemId(problemId);
    for (ContestProblem cp : contestProblems) {
        // 2. Check if contest is RUNNING
        Contest contest = contestMapper.selectById(cp.getContestId());
        if (contest == null || !ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            continue;
        }
        // 3. Check if user is PARTICIPATING (D-06)
        Optional<ContestParticipant> participant = participantMapper
            .findByContestIdAndUserId(cp.getContestId(), userId);
        if (participant.isEmpty() ||
            !ContestParticipantStatus.PARTICIPATING.name().equals(participant.get().getStatus())) {
            continue;
        }
        // 4. Create ContestSubmission (D-05)
        ContestSubmission cs = new ContestSubmission();
        cs.setSubmissionId(submissionId);
        cs.setContestId(cp.getContestId());
        cs.setContestProblemId(cp.getId());
        cs.setParticipantId(participant.get().getId());
        cs.setTimeFromStart((int) java.time.Duration.between(
            contest.getStartTime(), LocalDateTime.now()).getSeconds());
        cs.setIsAccepted(false); // Will be updated when judge completes
        cs.setSubmittedAt(LocalDateTime.now());
        contestSubmissionMapper.insert(cs);
        // Only record for the first matching active contest
        break;
    }
}
```

### Admin Endpoint: Start Contest
```java
// Source: [VERIFIED: AdminContestController pattern + D-07, D-08, D-09]
@Override
@Transactional
public AdminContestVO startContest(String id) {
    Contest contest = contestMapper.selectById(id);
    if (contest == null) {
        throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
    }

    // D-07: Must be UPCOMING
    if (!ContestStatus.UPCOMING.name().equals(contest.getStatus())) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Contest must be UPCOMING to start");
    }

    // D-07: Must have at least one problem
    long problemCount = contestProblemMapper.countByContestId(id);
    if (problemCount == 0) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Contest must have at least one problem");
    }

    // D-09: Set status and record actual start time
    contest.setStatus(ContestStatus.RUNNING.name());
    contestMapper.updateById(contest);

    // Emit status change via WebSocket
    realtimeService.emitContestStatus(
        id,
        ContestStatus.RUNNING,
        Instant.now(),
        null,
        "Contest started"
    );

    return toAdminVO(contest);
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual UUID generation | `@TableId(type = IdType.ASSIGN_UUID)` | Project start | All entities auto-generate IDs |
| Custom pagination | MyBatis-Plus `Page<T>` | Project start | Use `selectPage()` consistently |
| String-based status | `ContestStatus` enum + `.name()` | Project start | Compare with `ContestStatus.RUNNING.name()` |
| Separate admin/user services | `AdminContestService` + `ContestService` | Project start | Admin operations go through Admin layer |

**Deprecated/outdated:**
- None for this phase -- the project uses a consistent modern stack throughout.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `CreateContestDTO.problemIds` already exists and is of type `List<Long>` | Architecture Patterns | Low -- verified in codebase. But admin DTO may need separate type if management frontend sends `string[]` (see management contests.ts: `problemIds?: string[]`) |
| A2 | ContestParticipant status PARTICIPATING is the correct status for active contest participants | Contest Submission Recording | Medium -- DB enum has REGISTERED, STARTED, FINISHED, DISQUALIFIED but entity enum has REGISTERED, PARTICIPATING, COMPLETED, DISQUALIFIED. Code uses PARTICIPATING but DB may use STARTED. Need to verify which maps to which. |
| A3 | The `AdminContestService` is the right place for start/stop/update/delete (not ContestService) | Architecture | Low -- existing pattern separates admin from user-facing. AdminContestController already uses AdminContestService. |
| A4 | `problem_id` in `contest_problems` references `problems.id` which is `bigint` | Entity Design | Low -- verified from V3 schema DDL and seed data (problem_id values are 1, 2, 3, 5). |
| A5 | Management frontend sends `POST /admin/contests/{id}/start` and `POST /admin/contests/{id}/end` | Frontend Contract | Verified in management/src/api/admin/contests.ts |

## Open Questions (ALL RESOLVED)

1. **RESOLVED** ~~Contest creation status: DRAFT vs UPCOMING?~~
   - What we know: `ContestStatus` enum has DRAFT, UPCOMING, RUNNING, FINISHED, CANCELLED. Current `ContestServiceImpl.createContest()` sets status to DRAFT. But D-07 says start validation requires UPCOMING.
   - What's unclear: Should admin create set status to UPCOMING directly (skipping DRAFT)? Or should there be a separate "publish" action to go from DRAFT to UPCOMING? 
   > **RESOLVED:** Admin creates contests with status UPCOMING directly -- Plan 13-01 already does this implicitly.
   - Recommendation: Set status to UPCOMING on creation via admin endpoint. The DRAFT status can be used later if needed for draft workflows. This avoids adding a publish endpoint.

2. **RESOLVED** ~~ContestParticipant status mapping: DB enum vs Java enum mismatch?~~
   - What we know: DB `contest_participants.status` is `enum('REGISTERED','STARTED','FINISHED','DISQUALIFIED')`. Java `ContestParticipantStatus` has `REGISTERED, PARTICIPATING, COMPLETED, DISQUALIFIED`.
   - What's unclear: Is `STARTED` in DB mapped to `PARTICIPATING` in Java? And `FINISHED` to `COMPLETED`?
   - Recommendation: Verify by checking how the existing registration flow sets the status. The Java code uses `ContestParticipantStatus.REGISTERED.name()` which would write "REGISTERED" to DB (matches). Need to confirm the PARTICIPATING vs STARTED mapping. 
   > **RESOLVED:** Java enum renamed to match DB: PARTICIPATING→STARTED, COMPLETED→FINISHED. Plan 13-01 Task 1 includes this fix.

3. **RESOLVED** ~~Admin create vs user-facing create: which handles problemIds?~~
   - What we know: `ContestController` (user-facing) already has POST /contest that calls `ContestService.createContest()`. Management frontend calls POST /admin/contests.
   - What's unclear: Should both paths support problemIds, or only the admin path?
   - Recommendation: Only the admin path (`AdminContestService`) needs problem assignment per D-02. The user-facing path can remain as-is (it's admin-only anyway via `@PreAuthorize`). 
   > **RESOLVED:** Admin-only creation handles problemIds -- Plan 13-01 Task 2 implements this.

## Environment Availability

Step 2.6: SKIPPED (no external dependencies identified -- all work is code-only changes within the existing Spring Boot project).

## Validation Architecture

> Skipped: `workflow.nyquist_validation` is explicitly `false` in `.planning/config.json`.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` on all admin endpoints |
| V3 Session Management | no | JWT-based auth handled by existing security config |
| V4 Access Control | yes | All new endpoints require ADMIN role. Contest submission recording must not expose participant data to non-participants. |
| V5 Input Validation | yes | Jakarta Validation annotations on all DTOs. `@NotBlank`, `@NotNull`, `@Size` on announcement title/content. |
| V6 Cryptography | no | No cryptographic operations in this phase |

### Known Threat Patterns for Spring Boot REST API

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Admin endpoint unauthorized access | Elevation of Privilege | `@PreAuthorize` on every new admin endpoint. SecurityConfig already enforces role checks. |
| SQL injection via problem IDs | Tampering | MyBatis-Plus parameterized queries via `@Param` annotations. Never concatenate problem IDs into SQL. |
| Mass assignment via DTO | Tampering | Use dedicated DTOs with `@JsonIgnoreProperties` or explicit field binding. Don't bind request body directly to entities. |
| Contest announcement XSS | Tampering | Announcement `content` field could contain HTML. Frontend should sanitize display. Backend stores as-is. |
| Announcement spam | DoS | Consider rate limiting announcement creation (admin-only mitigates this naturally). |

## Sources

### Primary (HIGH confidence)
- [VERIFIED: V3__contest_schema.sql] -- contest_problems, contest_submissions, contest_announcements table DDLs
- [VERIFIED: Contest.java] -- Entity pattern with MyBatis-Plus annotations
- [VERIFIED: ContestParticipant.java] -- Entity pattern reference
- [VERIFIED: ContestMapper.java] -- Mapper pattern with @Select/@Update annotations
- [VERIFIED: ContestParticipantMapper.java] -- Mapper pattern with Optional return types
- [VERIFIED: ContestServiceImpl.java] -- Service implementation patterns, createContest(), @Transactional usage
- [VERIFIED: AdminContestController.java] -- Current 2 read-only endpoints, auth annotations
- [VERIFIED: AdminContestServiceImpl.java] -- Admin service pattern, toAdminVO() mapping
- [VERIFIED: AdminContestVO.java] -- Admin response VO with field list
- [VERIFIED: RealtimeService.java] -- emitAnnouncement() method signature and behavior
- [VERIFIED: AnnouncementPayload.java] -- Record with static factory method `of()`
- [VERIFIED: WebSocketUtils.java] -- Room name convention `/topic/contest/{id}`
- [VERIFIED: CreateContestDTO.java] -- Already has `problemIds` field (List<Long>)
- [VERIFIED: UpdateContestDTO.java] -- Already has `problemIds` field (List<Long>)
- [VERIFIED: ContestStatus.java] -- Enum: DRAFT, UPCOMING, RUNNING, FINISHED, CANCELLED
- [VERIFIED: ContestParticipantStatus.java] -- Enum: REGISTERED, PARTICIPATING, COMPLETED, DISQUALIFIED
- [VERIFIED: ErrorCode.java] -- Existing contest error codes 70001-70009
- [VERIFIED: management/src/api/admin/contests.ts] -- Frontend API contract with all endpoints
- [VERIFIED: SubmissionServiceImpl.java] -- submit() method structure and @Transactional
- [VERIFIED: console/src/types/contest.ts] -- ContestAnnouncement interface with author field

### Secondary (MEDIUM confidence)
- [VERIFIED: V3 seed data] -- contest_problems INSERT statements showing problem_index format (Q1-Q4), score values (3-6), base_score=100, time_bonus=0

### Tertiary (LOW confidence)
- None -- all claims verified against codebase.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries already in project, no new dependencies needed
- Architecture: HIGH - Patterns verified from existing codebase (entity, mapper, controller, service)
- Pitfalls: HIGH - Schema mismatches identified by comparing V3 DDL with existing entity patterns
- Frontend contract: HIGH - Management and console API files verified

**Research date:** 2026-04-18
**Valid until:** 30 days (stable phase, no external dependencies)
