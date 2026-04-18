# Phase 14: Contest Engine - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement the contest automation engine: automatic lifecycle transitions (UPCOMING→RUNNING→FINISHED), Codeforces-style rating calculation, real-time ranking WebSocket pushes, and submission result WebSocket push to browsers. Phase 13 provides entities and admin API — Phase 14 wires up the runtime automation.

**Scope:**
- ContestScheduler: @Scheduled job that transitions contest status based on start_time/end_time
- RatingCalculationEngine: Codeforces Elo variant that updates global_rankings after contest ends
- RealtimeService.emitRankingUpdate(): Push ranking updates to contest WebSocket room on each submission
- JUDGE-04 WebSocket: Judge Worker pushes verdict to browser via existing RealtimeService submission result path
- Contest ranking recalculation after each contest ends (all participants' final ranks + ratings)

**Out of scope:**
- Phase 13 entities (ContestProblem, ContestSubmission, ContestAnnouncement) — already handled
- New database migrations — tables already in V3
- Frontend changes — WebSocket composables already built in Phase 13 frontend
- Contest freeze time logic — deferred

</domain>

<decisions>
## Implementation Decisions

### Contest Scheduler
- **D-01:** Fixed-rate @Scheduled polling — run every 10 seconds, check all contests with status=UPCOMING or status=RUNNING
- **D-02:** UPCOMING→RUNNING trigger: current time >= start_time — set status=RUNNING and record actual_start_time
- **D-03:** RUNNING→FINISHED trigger: current time >= end_time OR admin stopped early — set status=FINISHED and record actual_end_time, then trigger rating calculation
- **D-04:** Idempotent transitions — re-checked each poll, skip if already in target state
- **D-05:** Emit contest status change via RealtimeService.emitContestStatus() on each transition (Phase 13 has this infrastructure)

### Rating Calculation Engine
- **D-06:** Codeforces-style Elo variant — use established CF rating formulas (not inventing new math)
- **D-07:** Rating range: 0-3500, default 1500 for new users — global_ranking.rating defaults to 1500
- **D-08:** Title system (10 levels): Newbie < 1200, Pupil < 1400, Specialist < 1600, Expert < 1900, Candidate Master < 2100, Master < 2300, International Master < 2400, Grandmaster < 2600, International Grandmaster < 3000, Legend < 3500
- **D-09:** Contest ranking stored in contest_participants.final_rank after contest ends
- **D-10:** Batch calculation on contest finish: fetch all PARTICIPATING participants, compute new ratings, update global_rankings in batch
- **D-11:** Rating changes only applied to users who have global_ranking records — new users get record created at rating=1500

### Real-Time Ranking Updates
- **D-12:** Throttled to max once per second per contest — RealtimeService already has this infrastructure with RANKING_THROTTLE_MS=1000
- **D-13:** Ranking recalculated on every submission: fetch all participants, sort by score desc, then penalty asc (lower penalty = better rank)
- **D-14:** Ranking payload: userId, username, rank, score, penalty, solved count — sent to /contest/{contestId}/ranking room

### Submission Result WebSocket (JUDGE-04)
- **D-15:** JudgeWorkerProcessor already has WebSocket push in Phase 12 — verify it's wired to the right user destination
- **D-16:** Submission result pushed to /user/{userId}/submission topic — frontend subscribes to receive verdict without polling
- **D-17:** Payload: submissionId, status, score, timeUsed, memoryUsed, judgedAt — matches SubmissionResultPayload record

### Integration Points
- **D-18:** ContestScheduler triggers rating calculation after RUNNING→FINISHED transition — call RatingCalculationService.calculateAndUpdate(contestId)
- **D-19:** SubmissionService.submit() already checks active contest in Phase 13 — Judge Worker verdict WebSocket push added here
- **D-20:** RealtimeService ranking throttle: pendingRankingUpdates map tracks dirty contests, @Scheduled flushes dirty contests at most once per second

### Claude's Discretion
- Exact rating formula coefficients (K-factor, volatility)
- Specific penalty calculation formula (CF uses 10min penalty per wrong submission)
- Whether scheduler should process all UPCOMING/RUNNING contests or paginate
- Unit test structure for scheduler (time-based testing with Clock)
- Whether to emit ranking update on contest finish or only during contest

### Folded Todos
None — no pending todos matched this phase.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema
- `db-manager/migrations/V3__contest_schema.sql` — Contest tables: contests, contest_problems, contest_submissions, contest_announcements, contest_participants, contest_rankings, global_rankings

### Phase 13 Context (Critical — Phase 14 depends on it)
- `.planning/phases/13-contest-data-layer/13-CONTEXT.md` — Entity patterns, service interfaces, WebSocket emitAnnouncement() infrastructure

### Phase 12 Context
- `.planning/phases/12-judge-worker/12-CONTEXT.md` — Judge Worker architecture, language whitelist, WebSocket push pattern, retry logic

### Existing Backend
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/Contest.java` — Contest entity with status, startTime, duration, actualStartTime, actualEndTime
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/GlobalRanking.java` — rating, maxRating, ratingTitle, maxRatingTitle, contestsAttended, contestsRated
- `backend-spring/src/main/java/com/ulticode/modules/contest/entity/ContestParticipant.java` — participant status tracking, finalRank
- `backend-spring/src/main/java/com/ulticode/modules/contest/enums/ContestStatus.java` — UPCOMING, RUNNING, FINISHED
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/RankingService.java` — getContestRanking() method
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RankingServiceImpl.java` — Current ranking query (needs update for real-time)
- `backend-spring/src/main/java/com/ulticode/modules/websocket/service/RealtimeService.java` — emitRankingUpdate(), emitContestStatus(), throttle infrastructure (RANKING_THROTTLE_MS=1000)
- `backend-spring/src/main/java/com/ulticode/modules/websocket/contest/dto/SubmissionResultPayload.java` — WebSocket payload for submission results
- `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java` — Judge Worker with verdict priority and WebSocket push (Phase 12)

### Frontend (Already Built — Reference Only)
- `console/src/views/contest/ContestDetailView.vue` — User contest page (currently polling, needs WebSocket upgrade — Phase 14 scope)
- `console/src/composables/useContestSocket.ts` — WebSocket composable with STOMP + SockJS + auto-reconnect (Phase 13)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **RealtimeService**: Already has emitRankingUpdate(), emitContestStatus(), emitAnnouncement() with throttling built-in — just call it from scheduler
- **SubmissionResultPayload**: Record already defined with all fields — Judge Worker uses it in Phase 12
- **ContestParticipant**: Already has finalRank field — ranking calculation writes to it
- **GlobalRanking**: Already has rating, maxRating, ratingTitle, maxRatingTitle — rating engine updates these

### Established Patterns
- **@Scheduled pattern**: BackupScheduler.java in contest module shows how to structure a scheduled job
- **Throttle pattern**: RealtimeService uses Map<String, Long> + pendingRankingUpdates to track dirty contests
- **Rating pattern**: CF Elo is well-documented standard — implement against public CF rating formula spec
- **Codeforces rating**: Known formula — 32 K-factor for new players, 10-32 for established, rating change = K * (actual - expected)

### Integration Points
- **Scheduler → Rating**: ContestScheduler.run() calls RatingCalculationService.calculateAndUpdate(contestId) on FINISHED transition
- **Submission → Ranking**: After each submission verdict, update contest_participants score and trigger ranking recalc
- **Judge Worker → WebSocket**: After verdict written to Submission, push SubmissionResultPayload to /user/{userId}/submission
- **Rating → GlobalRanking**: RatingEngine updates global_ranking.rating and recalculates ratingTitle based on new rating

### Critical Gaps
- **No ContestScheduler**: No @Scheduled job exists — contests never auto-transition
- **No RatingCalculationEngine**: No service for CF-style rating computation
- **RankingServiceImpl.getContestRanking()**: Only reads final ranks from finished contests — needs update for real-time during contest
- **RealtimeService.emitRankingUpdate()**: Infrastructure exists but no caller triggers it on submissions
- **Judge Worker verdict push**: Phase 12 built the method but may not be wired to correct user destination

</code_context>

<specifics>
## Specific Ideas

- CF rating formula: new_rating = old_rating + K * (score - expected_score), where score is 0 or 1 (win/loss equivalent), expected_score = 1 / (1 + 10^((opponent_rating - old_rating)/400))
- CF uses rank list based on solved count + penalty (10 min per wrong submission)
- Penalty calculation: sum of accepted-submission-time + 10 * wrong_submissions for each problem

</specifics>

<deferred>
## Deferred Ideas

- Contest freeze time (during last hour, rankings locked) — future phase
- Problem difficulty weight in rating calculation — future enhancement

---

*Phase: 14-contest-engine*
*Context gathered: 2026-04-18*
