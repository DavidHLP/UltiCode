# Phase 14: Contest Engine - Research

**Researched:** 2026-04-19
**Domain:** Spring Boot scheduling, Codeforces rating algorithm, WebSocket real-time push, contest lifecycle automation
**Confidence:** HIGH

## Summary

Phase 14 implements the contest automation engine on top of Phase 13's data layer. The three core capabilities are: (1) a `@Scheduled` job that polls every 10 seconds to auto-transition contests from UPCOMING to RUNNING (when `start_time` is reached) and from RUNNING to FINISHED (when `end_time` is reached or admin stops early), (2) a Codeforces-style Elo rating engine that batch-updates `global_rankings` and `contest_participants.final_rank` when a contest ends, and (3) a real-time ranking push pipeline that emits throttled ranking updates to WebSocket subscribers after each submission verdict, completing the JUDGE-04 WebSocket requirement. All infrastructure already exists in `RealtimeService` (throttle tracking, pending updates map, `@Scheduled` flush) and the existing `BackupScheduler` pattern shows exactly how to structure the new `ContestScheduler`.

**Primary recommendation:** Follow the BackupScheduler pattern for the scheduler component, implement the CF Elo formula as a dedicated `RatingCalculationEngine` service, and wire the submission verdict callback to `RealtimeService.markDirty(contestId)` to leverage the existing throttle/flush infrastructure.

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Fixed-rate @Scheduled polling -- run every 10 seconds, check all contests with status=UPCOMING or status=RUNNING
- **D-02:** UPCOMING→RUNNING trigger: current time >= start_time -- set status=RUNNING and record actual_start_time
- **D-03:** RUNNING→FINISHED trigger: current time >= end_time OR admin stopped early -- set status=FINISHED and record actual_end_time, then trigger rating calculation
- **D-04:** Idempotent transitions -- re-checked each poll, skip if already in target state
- **D-05:** Emit contest status change via RealtimeService.emitContestStatus() on each transition
- **D-06:** Codeforces-style Elo variant -- use established CF rating formulas
- **D-07:** Rating range: 0-3500, default 1500 for new users -- global_ranking.rating defaults to 1500
- **D-08:** Title system (10 levels): Newbie < 1200, Pupil < 1400, Specialist < 1600, Expert < 1900, Candidate Master < 2100, Master < 2300, International Master < 2400, Grandmaster < 2600, International Grandmaster < 3000, Legend < 3500
- **D-09:** Contest ranking stored in contest_participants.final_rank after contest ends
- **D-10:** Batch calculation on contest finish: fetch all PARTICIPATING participants, compute new ratings, update global_rankings in batch
- **D-11:** Rating changes only applied to users who have global_ranking records -- new users get record created at rating=1500
- **D-12:** Throttled to max once per second per contest -- RealtimeService already has this infrastructure with RANKING_THROTTLE_MS=1000
- **D-13:** Ranking recalculated on every submission: fetch all participants, sort by score desc, then penalty asc
- **D-14:** Ranking payload: userId, username, rank, score, penalty, solved count -- sent to /contest/{contestId}/ranking room
- **D-15:** JudgeWorkerProcessor already has WebSocket push in Phase 12 -- verify it's wired to the right user destination
- **D-16:** Submission result pushed to /user/{userId}/submission topic -- frontend subscribes to receive verdict without polling
- **D-17:** Payload: submissionId, status, score, timeUsed, memoryUsed, judgedAt -- matches SubmissionResultPayload record
- **D-18:** ContestScheduler triggers rating calculation after RUNNING→FINISHED transition -- call RatingCalculationService.calculateAndUpdate(contestId)
- **D-19:** SubmissionService.submit() already checks active contest in Phase 13 -- Judge Worker verdict WebSocket push added here
- **D-20:** RealtimeService ranking throttle: pendingRankingUpdates map tracks dirty contests, @Scheduled flushes dirty contests at most once per second

### Claude's Discretion

- Exact rating formula coefficients (K-factor, volatility)
- Specific penalty calculation formula (CF uses 10min penalty per wrong submission)
- Whether scheduler should process all UPCOMING/RUNNING contests or paginate
- Unit test structure for scheduler (time-based testing with Clock)
- Whether to emit ranking update on contest finish or only during contest

### Deferred Ideas (OUT OF SCOPE)

- Contest freeze time (during last hour, rankings locked) -- future phase
- Problem difficulty weight in rating calculation -- future enhancement

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONTEST-03 | Contest lifecycle auto-transition (UPCOMING→RUNNING→FINISHED) | ContestScheduler polls every 10s using existing `findByStatus()` mapper method; idempotent transitions prevent double-processing |
| CONTEST-04 | Real-time ranking updates via WebSocket during contest | RealtimeService throttle/flush infrastructure already built; submission callback marks dirty, scheduled flush emits |
| CONTEST-06 | Contest rating calculation after contest ends | Codeforces Elo variant with K-factor based on contest count; batch updates global_rankings and contest_participants.final_rank |
| JUDGE-04 | Judge verdict pushed to browser via WebSocket | SubmissionResultPayload already defined; JudgeWorkerProcessor.pushResult() already calls realtimeService.emitSubmissionResult(); minor fix to wire contestId |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| ContestScheduler | API/Backend | -- | Spring @Scheduled runs on backend; transitions Contest.status in DB |
| RatingCalculationEngine | API/Backend | -- | Pure Java computation, updates global_rankings and contest_participants tables |
| RealtimeService.emitRankingUpdate | API/Backend | -- | SimpMessagingTemplate pushes to WebSocket topics; throttle logic in service |
| Submission result WebSocket | API/Backend | -- | JudgeWorkerProcessor calls realtimeService after verdict; SimpMessagingTemplate delivers |
| Ranking recalculation | API/Backend | -- | RankingService.getLiveRanking() already sorts by score/penalty; used by scheduler flush |
| Global ranking update | API/Backend | -- | GlobalRankingMapper batch update; recalculate global_rank column after all contest ratings |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot 3.5 | (from project) | @Scheduled, @EnableScheduling | Already enabled in UlticodeBackendApplication.java |
| MyBatis-Plus | (from project) | DB queries for scheduler and rating | Already used throughout contest module |
| SimpMessagingTemplate | (Spring WebSocket) | STOMP WebSocket push | Already used by RealtimeService |
| ConcurrentHashMap | (JDK) | Throttle tracking | Already used in RealtimeService.pendingRankingUpdates |
| Java `Clock` | (JDK) | Time-based testing for scheduler | Best practice for testable time dependencies |

**Installation:** No new dependencies -- all required infrastructure already in the project.

## Architecture Patterns

### System Architecture Diagram

```
[Judge Worker verdict callback]
         │
         ▼
[SubmissionService.submit()]  ──────────────────────────────────────────┐
         │                                                         │
         ▼                                                         │
[RealtimeService.markDirty(contestId)]                               │
   (adds contestId to pendingRankingUpdates)                         │
                                                                    │
[RealtimeService.flushPendingRankings()]  ◄──── @Scheduled (1s) ────┘
   (every second, for each dirty contest)                             │
         │                                                           │
         ▼                                                           │
[RankingService.getLiveRanking(contestId)]                            │
   (sort: totalScore DESC, totalPenalty ASC)                         │
         │                                                           │
         ▼                                                           │
[realtimeService.emitRankingUpdate(contestId, rankings)]              │
   (STOMP → /topic/contest/{contestId}/ranking)                     │
                                                                    │
[JudgeWorkerProcessor.pushResult()]                                   │
   (after verdict written)                                           │
         │                                                           │
         ▼                                                           │
[realtimeService.emitSubmissionResult(userId, payload)]              │
   (STOMP → /user/{userId}/queue/submission)                         │
                                                                    │
[ContestScheduler.run()]  ◄──── @Scheduled (10s) ────────────────────┘
   │
   ├── [findByStatus("UPCOMING")] ──► if (now >= startTime)
   │       └── update status=RUNNING, actualStartTime
   │       └── emitContestStatus(RUNNING)
   │       └── markDirty(contestId)
   │
   ├── [findByStatus("RUNNING")] ──► if (now >= endTime)
   │       └── update status=FINISHED, actualEndTime
   │       └── emitContestStatus(FINISHED)
   │       └── [RatingCalculationEngine.calculateAndUpdate(contestId)]
   │               ├── fetch all STARTED participants
   │               ├── compute new ratings (CF Elo)
   │               ├── batch-update global_rankings
   │               ├── batch-update contest_participants.final_rank
   │               └── recalculate global_rank column
   │
   └── (repeat on next 10s tick)
```

### Recommended Project Structure

```
backend-spring/src/main/java/com/ulticode/modules/contest/
├── scheduler/
│   └── ContestScheduler.java          # @Scheduled job, 10s interval
├── service/
│   ├── RatingCalculationService.java  # Interface for rating engine
│   └── impl/
│       └── RatingCalculationServiceImpl.java  # CF Elo implementation
```

### Pattern 1: @Scheduled Polling (BackupScheduler pattern)
**What:** Fixed-rate `@Scheduled` job that polls DB and performs state transitions.
**When to use:** Contest lifecycle automation, any time-based state machine.
**Source:** `backend-spring/src/main/java/com/ulticode/modules/backup/scheduler/BackupScheduler.java`
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestScheduler {

    private final ContestMapper contestMapper;
    private final ContestService contestService;
    private final RatingCalculationService ratingService;
    private final RealtimeService realtimeService;

    @Scheduled(fixedRate = 10_000)  // 10 seconds
    public void run() {
        // 1. Find UPCOMING contests whose start_time has passed
        List<Contest> upcoming = contestMapper.findByStatus(ContestStatus.UPCOMING.name());
        for (Contest contest : upcoming) {
            if (!contest.getStartTime().isAfter(LocalDateTime.now())) {
                transitionToRunning(contest);
            }
        }

        // 2. Find RUNNING contests whose end_time has passed
        List<Contest> running = contestMapper.findByStatus(ContestStatus.RUNNING.name());
        for (Contest contest : running) {
            if (contest.getEndTime() != null && !contest.getEndTime().isAfter(LocalDateTime.now())) {
                transitionToFinished(contest);
            }
        }
    }

    @Transactional
    void transitionToRunning(Contest contest) { ... }

    @Transactional
    void transitionToFinished(Contest contest) { ... }
}
```

### Pattern 2: Codeforces Rating Calculation
**What:** CF Elo variant with K-factor based on contest count, expected score = 1 / (1 + 10^((opponentRating - myRating) / 400)).
**When to use:** After contest ends, for all STARTED participants.
**Source:** [CITED: codeforces.com/blog/entry/102] -- CF rating system documentation

```java
public record RatingResult(String oderId, int oldRating, int newRating, int change) {

    /**
     * Calculate new rating using Codeforces Elo variant.
     *
     * <p>Expected score = 1 / (1 + 10^((opponentRating - myRating) / 400))
     * Rating change = round(K * (actualScore - expectedScore))
     * K = 32 for new players, decreases with more contests
     */
    public static RatingResult calculate(int myRating, int opponentRating, boolean won) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentRating - myRating) / 400.0));
        double actual = won ? 1.0 : 0.0;
        int k = determineKFactor(myRating);
        int change = (int) Math.round(k * (actual - expected));
        return new RatingResult(myRating + change, change);
    }

    static int determineKFactor(int rating) {
        if (rating < 2100) return 32;
        if (rating < 2400) return 24;
        return 16;
    }
}
```

### Anti-Patterns to Avoid

- **Timezone mismatch:** Always use `LocalDateTime.now()` vs `Instant.now()` -- check which the DB stores and be consistent. DB `start_time` is `datetime(3)` without zone; Java uses system default. Document this clearly.
- **Double transition:** If scheduler polls at t=0 and t=10s, and transition takes 5s, second poll may re-trigger. D-04 (idempotent) prevents this, but the implementation must re-check status before writing.
- **Rating for unregistered participants:** D-11 requires global_ranking record to exist. Users who never attended a rated contest should not get a rating. The system must create the record at rating=1500 if missing.
- **Concurrent rating updates:** If contest ends and admin manually triggers a re-rating, both could write simultaneously. Use `@Transactional` with appropriate isolation level.

## Common Pitfalls

### Pitfall 1: Null end_time causing contests to never finish
**What goes wrong:** Some contests in the DB have `end_time = NULL` (visible in seed data). The scheduler condition `if (now >= end_time)` will never trigger for these.
**Why it happens:** The `contests.end_time` column is nullable in the schema. The `end_time` is computed as `start_time + duration_minutes` but may not always be stored.
**How to avoid:** Compute effective end time as `contest.getStartTime().plusMinutes(contest.getDurationMinutes())` when `end_time` is null. Alternatively, the scheduler should always compute end_time from start_time + duration when the DB column is null.
**Warning signs:** Contests stuck in RUNNING status after duration expires.

### Pitfall 2: Rating title mismatch between enum and rating thresholds
**What goes wrong:** RatingTitle enum values (e.g., `CANDIDATE_MASTER`) don't match the D-08 thresholds exactly.
**Why it happens:** The enum was defined independently from the rating boundary decisions.
**How to avoid:** Define a static method in `RatingTitle` that maps rating integer to enum value. Never rely on enum ordinal or name matching the threshold. Use a lookup table instead.
**Warning signs:** Users showing `CANDIDATE_MASTER` at 2000 rating instead of 2100+.

### Pitfall 3: Judge verdict push missing contestId in payload
**What goes wrong:** `JudgeWorkerProcessor.pushResult()` creates `SubmissionResultPayload.of(submissionId, null, problemId, ...)` with null contestId. The frontend contest page needs contestId to correlate the result.
**Why it happens:** Phase 12 built the method without contest context.
**How to avoid:** Modify `JudgeJob` to carry contestId. When verdict is written, check if submission is part of an active contest (via ContestSubmission record) and include it in the WebSocket payload. Fall back to null for non-contest submissions.
**Warning signs:** Frontend receives verdict but can't update contest ranking board.

### Pitfall 4: Infinite flush loop on contest finish
**What goes wrong:** After contest ends, the scheduler marks it dirty one final time and `flushPendingRankings()` emits a last ranking update. If `markDirty` is called from multiple places (submission + scheduler), it may flush multiple times unnecessarily.
**Why it happens:** The pendingRankingUpdates map tracks dirty contests but does not distinguish "during contest" vs "final update after contest".
**How to avoid:** Skip `markDirty` for FINISHED contests in the submission callback. The scheduler's transitionToFinished() should directly emit the final ranking (not go through the throttle path), then not add to pendingUpdates.

## Code Examples

### Flushing pending ranking updates (existing RealtimeService pattern)
**Source:** `backend-spring/src/main/java/com/ulticode/modules/websocket/service/RealtimeService.java`

```java
@Scheduled(fixedRate = 1000)  // 1 second flush
public void flushPendingRankings() {
    Set<String> dirty = Set.copyOf(pendingRankingUpdates.keySet());
    pendingRankingUpdates.clear();

    for (String contestId : dirty) {
        Long lastPush = lastRankingPushTime.get(contestId);
        long elapsed = System.currentTimeMillis() - lastPush;

        if (elapsed >= RANKING_THROTTLE_MS) {
            List<RankingItem> rankings = rankingService.getLiveRanking(contestId, 200);
            emitRankingUpdate(contestId, rankings);
            lastRankingPushTime.put(contestId, System.currentTimeMillis());
        } else {
            // Re-mark as dirty for next flush
            pendingRankingUpdates.putIfAbsent(contestId, true);
        }
    }
}

public void markDirty(String contestId) {
    pendingRankingUpdates.putIfAbsent(contestId, true);
}
```

### Mapping rating to title
**Source:** [CITED: codeforces.com/blog/entry/102]

```java
public static RatingTitle fromRating(int rating) {
    if (rating < 1200) return RatingTitle.NEWBIE;
    if (rating < 1400) return RatingTitle.PUPIL;
    if (rating < 1600) return RatingTitle.SPECIALIST;
    if (rating < 1900) return RatingTitle.EXPERT;
    if (rating < 2100) return RatingTitle.CANDIDATE_MASTER;
    if (rating < 2300) return RatingTitle.MASTER;
    if (rating < 2400) return RatingTitle.INTERNATIONAL_MASTER;
    if (rating < 2600) return RatingTitle.GRANDMASTER;
    if (rating < 3000) return RatingTitle.INTERNATIONAL_GRANDMASTER;
    return RatingTitle.LEGENDARY_GRANDMASTER;
}
```

### Transitioning participant status on contest start
The scheduler should NOT directly change participant status from REGISTERED to STARTED. Instead, the first submission by a registered participant sets their status to STARTED (handled in Phase 13's SubmissionService). The scheduler only transitions the contest's own status field.

However, after RUNNING→FINISHED, the scheduler should transition all STARTED participants to FINISHED:

```java
@Transactional
public void finishContest(String contestId) {
    // Update contest status
    Contest contest = contestMapper.selectById(contestId);
    contest.setStatus(ContestStatus.FINISHED.name());
    contest.setActualEndTime(LocalDateTime.now());
    contestMapper.updateById(contest);

    // Transition all STARTED participants to FINISHED
    List<ContestParticipant> active = participantMapper.findByContestIdAndStatus(
        contestId, ContestParticipantStatus.STARTED.name());
    for (ContestParticipant p : active) {
        p.setStatus(ContestParticipantStatus.FINISHED.name());
        p.setFinishedAt(LocalDateTime.now());
        participantMapper.updateById(p);
    }

    // Calculate ratings
    ratingCalculationService.calculateAndUpdate(contestId);
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual contest lifecycle | @Scheduled polling (10s) | Phase 14 | Contests auto-start/stop without admin |
| No rating system | CF Elo variant | Phase 14 | Participants earn persistent ratings |
| Polling-based ranking | WebSocket push (throttled 1s) | Phase 14 | Real-time leaderboard, reduced server load |
| Poll for verdict | WebSocket push on verdict | Phase 14 (JUDGE-04) | Instant browser notification |

**Deprecated/outdated:**
- Direct WebSocket endpoint push (without throttle): replaced by flush-based batching to prevent spam
- Rating without volatility: current CF system uses K-factor only, no volatility parameter (simpler)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `contests.end_time` may be NULL; effective end time = start_time + duration_minutes | Common Pitfalls | Contests could get stuck RUNNING if end_time is null |
| A2 | Participant status REGISTERED→STARTED is triggered by first submission, not by scheduler | Architecture | If Phase 13 didn't implement this, participants will show as REGISTERED even after contest starts |
| A3 | CF K-factor thresholds: <2100 = 32, <2400 = 24, >=2400 = 16 | Rating Engine | Standard CF values, but D-08 didn't specify -- flag for confirmation |
| A4 | Rating only for participants with global_ranking record (D-11) means skip users without one | Rating Engine | If a STARTED participant has no global_ranking record, they are skipped -- correct per D-11 |
| A5 | `markDirty` from submission callback and `flushPendingRankings` are the only ranking update paths | Architecture | If other code paths bypass the throttle, flooding is possible |

## Open Questions

1. **K-factor thresholds for rating calculation**
   - What we know: CF standard uses 32 (<2100), 24 (<2400), 16 (>=2400)
   - What's unclear: D-08 didn't specify K-factor values -- only title thresholds
   - Recommendation: Use standard CF K-factor values; if user wants different, they can override via `contest_scoring_rules`

2. **Should the scheduler also transition REGISTERED participants to STARTED?**
   - What we know: D-06 mentions STARTED participants for rating calculation, but no explicit decision on who sets this status
   - What's unclear: Phase 13's SubmissionService may have implemented participant STARTED transition on first submit -- verify before planning
   - Recommendation: Check Phase 13 implementation; if not done, scheduler should do it during UPCOMING→RUNNING

3. **SCORE vs ICPC scoring mode impact on ranking**
   - What we know: `contests.scoring_mode` can be SCORE or ICPC
   - What's unclear: Does ranking sort by totalScore (SCORE) or solved_count+penalty (ICPC)?
   - Recommendation: RankingService.getLiveRanking() should respect scoring_mode: ICPC = sort by solved_count DESC, penalty ASC; SCORE = sort by totalScore DESC, penalty ASC

4. **Global rank recalculation after batch rating update**
   - What we know: After updating all global_rankings.rating, the global_rank column needs to be recomputed (rank = 1, 2, 3... by rating DESC)
   - What's unclear: Should this be done in the same transaction or as a separate batch?
   - Recommendation: Separate batch update after rating calculation completes; use a single UPDATE with row_number or fetch-then-update approach

## Environment Availability

Step 2.6: SKIPPED (no external dependencies -- all required tools (Java 17, Maven, MySQL, Redis) are already available from project setup).

## Sources

### Primary (HIGH confidence)
- `backend-spring/src/main/java/com/ulticode/UlticodeBackendApplication.java` -- @EnableScheduling confirmed
- `backend-spring/src/main/java/com/ulticode/modules/backup/scheduler/BackupScheduler.java` -- @Scheduled pattern reference
- `backend-spring/src/main/java/com/ulticode/modules/websocket/service/RealtimeService.java` -- Throttle infrastructure, emitRankingUpdate, emitSubmissionResult
- `backend-spring/src/main/java/com/ulticode/modules/websocket/constants/WebSocketConstants.java` -- USER_QUEUE_SUBMISSION constant
- `backend-spring/src/main/java/com/ulticode/modules/websocket/util/WebSocketUtils.java` -- getContestRoomName returns /topic/contest/{id}
- `backend-spring/src/main/java/com/ulticode/modules/websocket/contest/dto/RankingUpdatePayload.java` -- RankingItem record with rank, userId, username, score, solvedCount, penalty
- `backend-spring/src/main/java/com/ulticode/modules/websocket/contest/dto/SubmissionResultPayload.java` -- SubmissionResultPayload record
- `backend-spring/src/main/java/com/ulticode/modules/websocket/event/ContestStatusEvent.java` -- ContestStatus enum (UPCOMING, REGISTRATION, RUNNING, ENDED)
- `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java` -- pushResult() calls realtimeService.emitSubmissionResult(); line 291-296
- `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestMapper.java` -- findByStatus() method already exists
- `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java` -- findByContestIdAndStatus() with status filter
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/GlobalRanking.java` -- rating, maxRating, ratingTitle, maxRatingTitle fields
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/Contest.java` -- startTime, endTime, durationMinutes, status, actualStartTime, actualEndTime
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/ContestParticipant.java` -- finalRank, totalPenalty, totalScore, totalAttempts, status
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/enums/RatingTitle.java` -- 10 title levels
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/enums/ContestParticipantStatus.java` -- REGISTERED, STARTED, FINISHED, DISQUALIFIED
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RankingServiceImpl.java` -- getLiveRanking() with score/penalty sort
- `db-manager/migrations/V3__contest_schema.sql` -- contests.end_time nullable, contest_participants.status, scoring_mode

### Secondary (MEDIUM confidence)
- [codeforces.com/blog/entry/102] -- CF rating formula (K-factor, expected score, rating change)
- [Assumed] K-factor values: <2100 = 32, <2400 = 24, >=2400 = 16 -- standard CF values not verified against project documentation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all infrastructure exists, @Scheduled already enabled
- Architecture: HIGH -- patterns confirmed from existing code
- Pitfalls: MEDIUM -- null end_time and judge verdict push are based on code inspection but haven't been validated by running the system

**Research date:** 2026-04-19
**Valid until:** 2026-05-19 (30 days -- contest system patterns are stable)
